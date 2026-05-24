package dev.feather.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.FileLocation
import java.io.File

class FeatherSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return FeatherSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}

private class FeatherSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) {
            return emptyList()
        }

        // Use the KSP Visitor to locate the FeatherApp class declaration
        val visitor = FeatherAppVisitor(logger)
        resolver.getAllFiles().forEach { file ->
            file.accept(visitor, Unit)
        }

        val renderFunc = visitor.renderFunction
        val appClass = visitor.appClass

        if (appClass == null || renderFunc == null) {
            logger.warn("Feather KSP did not find a FeatherApp class or abstract render() override.")
            generated = true
            return emptyList()
        }

        val location = renderFunc.location
        if (location !is FileLocation) {
            logger.error("Could not determine source file location for FeatherApp.")
            generated = true
            return emptyList()
        }

        val filePath = location.filePath
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            logger.error("Source file does not exist: $filePath")
            generated = true
            return emptyList()
        }

        val source = sourceFile.readText()
        
        // Find the render function's body inside the source code using the line number
        val lines = source.lines()
        val startLineIdx = (location.lineNumber - 1).coerceIn(0, lines.size - 1)
        
        // Construct the source starting from the render signature line to extract the body
        val renderStartOffset = lines.take(startLineIdx).joinToString("\n").length + 1
        val remainingSource = source.substring(renderStartOffset)
        
        // Find opening brace '{' and match the closing brace '}'
        val openBraceIdx = remainingSource.indexOf('{')
        if (openBraceIdx < 0) {
            logger.error("Could not find body block for render() function.")
            generated = true
            return emptyList()
        }
        
        var depth = 0
        var closeBraceIdx = -1
        for (idx in openBraceIdx until remainingSource.length) {
            when (remainingSource[idx]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        closeBraceIdx = idx
                        break
                    }
                }
            }
        }
        
        if (closeBraceIdx < 0) {
            logger.error("Malformed render() function body: missing matching '}'")
            generated = true
            return emptyList()
        }
        
        val renderBody = remainingSource.substring(openBraceIdx + 1, closeBraceIdx)
        val payload = buildRenderPayload(renderBody)
        
        val containingFile = renderFunc.containingFile
        if (containingFile == null) {
            logger.error("Could not resolve containing file for render function symbol.")
            generated = true
            return emptyList()
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, containingFile),
            packageName = "feather",
            fileName = "app.feather",
            extensionName = "json",
        ).bufferedWriter().use { writer ->
            writer.write(payload)
            writer.write("\n")
        }

        generated = true
        return emptyList()
    }
}

private class FeatherAppVisitor(private val logger: KSPLogger) : KSVisitorVoid() {
    var renderFunction: KSFunctionDeclaration? = null
    var appClass: KSClassDeclaration? = null

    override fun visitFile(file: com.google.devtools.ksp.symbol.KSFile, data: Unit) {
        file.declarations.forEach { it.accept(this, data) }
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        val isApp = classDeclaration.superTypes.any { typeRef ->
            val resolved = runCatching { typeRef.resolve() }.getOrNull()
            val name = resolved?.declaration?.qualifiedName?.asString()
            name == "dev.feather.sdk.FeatherApp" || name == "FeatherApp"
        }
        
        if (isApp) {
            appClass = classDeclaration
            classDeclaration.declarations.forEach { decl ->
                if (decl is KSFunctionDeclaration && decl.simpleName.asString() == "render") {
                    renderFunction = decl
                }
            }
        }
        classDeclaration.declarations.forEach { it.accept(this, data) }
    }
}

private fun buildRenderPayload(source: String): String {
    val cleaned = preprocessSource(source)
    val widgets = parseWidgetTree(cleaned)
    val children = if (widgets.isEmpty()) {
        """{"type":"Text","text":"No Feather widgets found."}"""
    } else {
        widgets.joinToString(",")
    }

    return """
        {
          "type": "render",
          "protocol_version": 1,
          "root": {
            "type": "Column",
            "children": [$children]
          },
          "diagnostics": []
        }
    """.trimIndent()
}

/**
 * Preprocess the source to remove comments and import lines
 * so the parser doesn't match false positives.
 */
private fun preprocessSource(source: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < source.length) {
        // Skip line comments
        if (i + 1 < source.length && source[i] == '/' && source[i + 1] == '/') {
            while (i < source.length && source[i] != '\n') i++
            continue
        }
        // Skip block comments
        if (i + 1 < source.length && source[i] == '/' && source[i + 1] == '*') {
            i += 2
            while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
            i += 2
            continue
        }
        sb.append(source[i])
        i++
    }

    // Remove import lines
    return sb.toString()
        .lines()
        .filter { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("import ") && !trimmed.startsWith("package ")
        }
        .joinToString("\n")
}

/**
 * Recursively parse widget calls from source, producing JSON strings.
 * Handles nested Column/Row children correctly.
 */
private fun parseWidgetTree(source: String): List<String> {
    val widgets = mutableListOf<Pair<Int, String>>() // (position, json)

    val keywords = listOf("Text", "StateText", "Button", "TextInput", "Image", "Spacer", "Row", "Column", "GestureDetector")
    var searchStart = 0

    while (searchStart < source.length) {
        var firstMatch: Triple<String, Int, Char>? = null

        for (kw in keywords) {
            val parenPattern = "$kw("
            val bracePattern = "$kw {"

            val parenIdx = source.indexOf(parenPattern, searchStart)
            if (parenIdx >= 0) {
                // Verify it's not part of a longer identifier (e.g., "TextNode(")
                val charBefore = if (parenIdx > 0) source[parenIdx - 1] else ' '
                if (!charBefore.isLetterOrDigit() && charBefore != '_') {
                    if (firstMatch == null || parenIdx < firstMatch.second) {
                        firstMatch = Triple(kw, parenIdx, '(')
                    }
                }
            }

            val braceIdx = source.indexOf(bracePattern, searchStart)
            if (braceIdx >= 0) {
                val charBefore = if (braceIdx > 0) source[braceIdx - 1] else ' '
                if (!charBefore.isLetterOrDigit() && charBefore != '_') {
                    if (firstMatch == null || braceIdx < firstMatch.second) {
                        firstMatch = Triple(kw, braceIdx, '{')
                    }
                }
            }
        }

        if (firstMatch == null) break

        val (kw, start, openChar) = firstMatch
        val closeChar = if (openChar == '(') ')' else '}'

        // Find the matching close bracket
        val bracketStart = source.indexOf(openChar, start)
        if (bracketStart < 0) break

        var depth = 0
        var end = -1
        for (idx in bracketStart until source.length) {
            when (source[idx]) {
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        end = idx + 1
                        break
                    }
                }
            }
        }

        if (end < 0) break

        val body = source.substring(start, end)
        val innerBody = source.substring(bracketStart + 1, end - 1)

        val json = when (kw) {
            "Text" -> {
                val text = firstStringArgument(body, "Text") ?: "Text"
                """{"type":"Text","text":"${jsonEscape(text)}"}"""
            }
            "StateText" -> {
                val key = namedStringArgument(body, "key") ?: ""
                val prefix = namedStringArgument(body, "prefix") ?: ""
                val fallback = namedStringArgument(body, "fallback") ?: ""
                """{"type":"StateText","key":"${jsonEscape(key)}","prefix":"${jsonEscape(prefix)}","fallback":"${jsonEscape(fallback)}"}"""
            }
            "Button" -> {
                val text = namedStringArgument(body, "text")
                    ?: firstStringArgument(body, "Button")
                    ?: "Button"
                val action = namedStringArgument(body, "action") ?: "buttonPressed"
                """{"type":"Button","text":"${jsonEscape(text)}","action":"${jsonEscape(action)}"}"""
            }
            "TextInput" -> {
                val value = namedStringArgument(body, "value") ?: ""
                val placeholder = namedStringArgument(body, "placeholder") ?: ""
                val action = namedStringArgument(body, "action") ?: "textChanged"
                """{"type":"TextInput","value":"${jsonEscape(value)}","placeholder":"${jsonEscape(placeholder)}","action":"${jsonEscape(action)}"}"""
            }
            "Image" -> {
                val src = namedStringArgument(body, "src")
                    ?: firstStringArgument(body, "Image") ?: ""
                val alt = namedStringArgument(body, "alt") ?: ""
                """{"type":"Image","src":"${jsonEscape(src)}","alt":"${jsonEscape(alt)}"}"""
            }
            "Spacer" -> {
                """{"type":"Spacer","flex":1}"""
            }
            "Row", "Column" -> {
                // Recursively parse children inside the layout container
                val children = parseWidgetTree(innerBody)
                val childrenJson = children.joinToString(",")
                """{"type":"$kw","children":[$childrenJson]}"""
            }
            "GestureDetector" -> {
                val onTap = namedStringArgument(body, "onTap") ?: ""
                val onPan = namedStringArgument(body, "onPan") ?: ""
                val children = parseWidgetTree(innerBody)
                val childJson = children.firstOrNull() ?: """{"type":"Spacer","flex":1}"""
                """{"type":"GestureDetector","onTap":"${jsonEscape(onTap)}","onPan":"${jsonEscape(onPan)}","child":$childJson}"""
            }
            else -> """{"type":"Text","text":"Unsupported widget"}"""
        }

        widgets += Pair(start, json)
        searchStart = end
    }

    return widgets.sortedBy { it.first }.map { it.second }
}

private fun firstStringArgument(body: String, functionName: String): String? {
    val prefix = "$functionName(\""
    val start = body.indexOf(prefix)
    if (start < 0) {
        return null
    }

    val valueStart = start + prefix.length
    val end = body.indexOf('"', valueStart)
    return if (end >= 0) body.substring(valueStart, end) else null
}

private fun namedStringArgument(body: String, name: String): String? {
    val prefix = "$name = \""
    val start = body.indexOf(prefix)
    if (start < 0) {
        return null
    }

    val valueStart = start + prefix.length
    val end = body.indexOf('"', valueStart)
    return if (end >= 0) body.substring(valueStart, end) else null
}

private fun jsonEscape(value: String): String {
    return buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
