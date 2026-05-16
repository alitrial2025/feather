package dev.feather.host

import android.content.Context
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * ReflectionModule — The "Bridge Factory" for Android.
 * 
 * This module allows calling arbitrary Android APIs from Kotlin/Wasm
 * without writing manual bridge code.
 */
class ReflectionModule : FeatherModule {
    override val name = "feather-reflection"
    override val version = "1.0.0"

    private val allowList = setOf(
        "android.os.Build",
        "android.os.BatteryManager",
        "android.view.Display",
        "android.content.Context",
        "java.util.Locale",
        "java.util.TimeZone"
    )

    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        val className = payload.optString("className")
        val methodName = payload.optString("methodName")
        val argsArray = payload.optJSONArray("args")
        
        if (className.isEmpty() || methodName.isEmpty()) return false

        // Security Check: Only allow whitelisted classes
        if (!allowList.any { className.startsWith(it) }) {
            println("[ReflectionModule] Blocked call to unauthorized class: $className")
            return false
        }

        return try {
            val clazz = Class.forName(className)
            val args = mutableListOf<Any?>()
            val argTypes = mutableListOf<Class<*>>()

            if (argsArray != null) {
                for (i in 0 until argsArray.length()) {
                    val arg = argsArray.get(i)
                    args.add(arg)
                    argTypes.add(mapJsonTypeToClass(arg))
                }
            }

            val reflectMethod = findMethod(clazz, methodName, argTypes.toTypedArray())
            val instance = if (Modifier.isStatic(reflectMethod.modifiers)) {
                null
            } else {
                resolveInstance(clazz, context.appContext) ?: clazz.getDeclaredConstructor().newInstance()
            }

            val result = reflectMethod.invoke(instance, *args.toTypedArray())
            val resultKey = payload.optString("resultStateKey")
            if (resultKey.isNotEmpty()) {
                context.sendStateAssignment(resultKey, result?.toString() ?: "null")
            }
            
            true
        } catch (e: Exception) {
            println("[ReflectionModule] Error calling $className.$methodName: ${e.message}")
            false
        }
    }

    private fun mapJsonTypeToClass(value: Any?): Class<*> {
        return when (value) {
            is String -> String::class.java
            is Int -> Int::class.java
            is Long -> Long::class.java
            is Double -> Double::class.java
            is Boolean -> Boolean::class.java
            else -> Any::class.java
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, argTypes: Array<Class<*>>): Method {
        return try {
            clazz.getMethod(name, *argTypes)
        } catch (e: NoSuchMethodException) {
            clazz.methods.firstOrNull { it.name == name && it.parameterCount == argTypes.size }
                ?: throw e
        }
    }

    private fun resolveInstance(clazz: Class<*>, context: Context): Any? {
        if (clazz.isAssignableFrom(Context::class.java)) return context
        try {
            val serviceNameField = clazz.fields.firstOrNull { it.name.endsWith("_SERVICE") && it.type == String::class.java }
            if (serviceNameField != null) {
                val serviceName = serviceNameField.get(null) as String
                return context.getSystemService(serviceName)
            }
        } catch (e: Exception) { }
        return null
    }
}
