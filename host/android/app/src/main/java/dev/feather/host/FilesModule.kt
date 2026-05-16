package dev.feather.host

import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class FilesModule : FeatherModule {
    override val name: String = "feather-files"
    override val version: String = "1.0.0"
    override fun canHandleLegacyCommand(command: String): Boolean = false
    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean = false

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        val dir = context.appContext.filesDir
        val path = payload.optString("path")
        val file = File(dir, path)
        
        return when (method) {
            "write" -> {
                file.parentFile?.mkdirs()
                file.writeText(payload.optString("content"))
                true
            }
            "read" -> {
                val content = if (file.exists()) file.readText() else ""
                context.sendStateAssignment("", content)
                true
            }
            "delete" -> {
                if (file.exists()) file.delete()
                true
            }
            "list" -> {
                val files = file.listFiles()?.map { it.name } ?: emptyList()
                context.sendStateAssignment("", JSONArray(files).toString())
                true
            }
            else -> false
        }
    }
}
