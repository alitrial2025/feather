package dev.feather.host

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.ServiceLoader

interface FeatherPlugin : FeatherModule

object PluginLoader {
    private const val PLUGINS_JSON = "feather_plugins.json"

    fun loadPlugins(context: Context): List<FeatherPlugin> {
        val plugins = mutableListOf<FeatherPlugin>()

        // 1. ServiceLoader discovery
        try {
            val serviceLoader = ServiceLoader.load(FeatherPlugin::class.java)
            for (plugin in serviceLoader) {
                plugins.add(plugin)
            }
        } catch (e: Exception) {
            android.util.Log.e("FeatherPlugin", "Error loading plugins via ServiceLoader", e)
        }

        // 2. Reflection parsing of assets/feather_plugins.json
        try {
            val assetManager = context.assets
            if (assetManager.list("")?.contains(PLUGINS_JSON) == true) {
                assetManager.open(PLUGINS_JSON).use { inputStream ->
                    val reader = InputStreamReader(inputStream)
                    val jsonContent = reader.readText()
                    val jsonObject = JSONObject(jsonContent)
                    val pluginsArray = jsonObject.optJSONArray("plugins")
                    if (pluginsArray != null) {
                        for (i in 0 until pluginsArray.length()) {
                            val className = pluginsArray.getString(i)
                            try {
                                val clazz = Class.forName(className)
                                val instance = clazz.getDeclaredConstructor().newInstance()
                                if (instance is FeatherPlugin) {
                                    plugins.add(instance)
                                    android.util.Log.i("FeatherPlugin", "Successfully loaded plugin reflection: $className")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FeatherPlugin", "Failed to load plugin class: $className", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FeatherPlugin", "Error reading $PLUGINS_JSON", e)
        }

        return plugins
    }
}
