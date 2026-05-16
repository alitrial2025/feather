import Foundation

class AuthModule: FeatherModule {
    let name = "feather-auth"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { return false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { return false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "biometricPrompt":
            context.sendStateAssignment("", "{\"success\": true}")
            return true
        case "storeToken":
            return true
        case "getToken":
            context.sendStateAssignment("", "{}")
            return true
        default:
            return false
        }
    }
}
