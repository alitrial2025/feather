package dev.feather.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
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

        val appFile = resolver.getAllFiles()
            .firstOrNull { file ->
                val source = runCatching { File(file.filePath).readText() }.getOrNull()
                source?.contains("FeatherApp") == true && source.contains("override fun render()")
            }

        if (appFile == null) {
            logger.warn("Feather KSP did not find a FeatherApp render function.")
            generated = true
            return emptyList()
        }

        val source = File(appFile.filePath).readText()
        val payload = buildRenderPayload(source)
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, appFile),
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
