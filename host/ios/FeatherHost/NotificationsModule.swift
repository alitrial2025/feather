import Foundation

class NotificationsModule: FeatherModule {
    let name = "feather-notifications"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { return false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { return false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "schedule":
            return true
        case "cancel":
            return true
        default:
            return false
        }
    }
}
