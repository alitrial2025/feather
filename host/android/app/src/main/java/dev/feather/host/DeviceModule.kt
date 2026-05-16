package dev.feather.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import org.json.JSONObject

class DeviceModule : FeatherModule {
    override val name: String = "feather-device"
    override val version: String = "1.0.0"

    override fun canHandleLegacyCommand(command: String): Boolean {
        return command.startsWith("Haptics/") || command.startsWith("Clipboard/")
    }

    override fun handleLegacyCommand(command: String, context: FeatherModuleContext): Boolean {
        return when {
            command == "Haptics/vibrate" -> {
                vibrate(context.appContext)
                true
            }
            command.startsWith("Haptics/impact=") -> {
                vibrate(context.appContext) // Basic impact
                true
            }
            command.startsWith("Clipboard/setText=") -> {
                val value = command.removePrefix("Clipboard/setText=").trim('"')
                setClipboard(context.appContext, value)
                true
            }
            command.startsWith("Clipboard/getText=") -> {
                val stateKey = command.removePrefix("Clipboard/getText=")
                val text = getClipboard(context.appContext)
                context.sendStateAssignment(stateKey, text)
                true
            }
            else -> false
        }
    }

    override fun call(method: String, payload: JSONObject, context: FeatherModuleContext): Boolean {
        return when (method) {
            "vibrate" -> {
                vibrate(context.appContext)
                true
            }
            "impact" -> {
                vibrate(context.appContext)
                true
            }
            "clipboardSet" -> {
                val text = payload.optString("text")
                setClipboard(context.appContext, text)
                true
            }
            "clipboardGet" -> {
                val text = getClipboard(context.appContext)
                context.sendStateAssignment("", text)
                true
            }
            else -> false
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    private fun setClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Feather", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun getClipboard(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }
}
