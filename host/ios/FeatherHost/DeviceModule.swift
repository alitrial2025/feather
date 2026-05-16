import Foundation
import UIKit

class DeviceModule: FeatherModule {
    let name = "feather-device"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return command == "Haptics/vibrate" || 
               command.hasPrefix("Clipboard/setText=") || 
               command.hasPrefix("Clipboard/getText=")
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        if command == "Haptics/vibrate" {
            #if os(iOS)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            #endif
            return true
        } else if command.hasPrefix("Clipboard/setText=") {
            let value = String(command.dropFirst("Clipboard/setText=".count))
                .trimmingCharacters(in: CharacterSet(charactersIn: "\""))
            #if os(iOS)
            UIPasteboard.general.string = value
            #endif
            return true
        } else if command.hasPrefix("Clipboard/getText=") {
            let stateKey = String(command.dropFirst("Clipboard/getText=".count))
            #if os(iOS)
            let text = UIPasteboard.general.string ?? ""
            let escapedText = text.replacingOccurrences(of: "\"", with: "\\\"")
            context.sendStateAssignment(stateKey, escapedText)
            #endif
            return true
        }
        return false
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "vibrate", "impact":
            #if os(iOS)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            #endif
            return true
        case "clipboardSet":
            if let text = payload["text"] as? String {
                #if os(iOS)
                UIPasteboard.general.string = text
                #endif
                return true
            }
        case "clipboardGet":
            #if os(iOS)
            let text = UIPasteboard.general.string ?? ""
            context.sendStateAssignment("", text)
            #endif
            return true
        case "getDimensions":
            let bounds = UIScreen.main.bounds
            let scale = UIScreen.main.scale
            let result: [String: Any] = [
                "width": bounds.width,
                "height": bounds.height,
                "scale": scale
            ]
            if let data = try? JSONSerialization.data(withJSONObject: result),
               let str = String(data: data, encoding: .utf8) {
                context.sendStateAssignment("", str)
            }
            return true
        case "getDeviceInfo":
            let result: [String: Any] = [
                "os": "ios",
                "version": UIDevice.current.systemVersion,
                "model": UIDevice.current.model
            ]
            if let data = try? JSONSerialization.data(withJSONObject: result),
               let str = String(data: data, encoding: .utf8) {
                context.sendStateAssignment("", str)
            }
            return true
        default:
            break
        }
        return false
    }
}
