import Foundation

class NavigationModule: FeatherModule {
    let name = "feather-navigation"
    let version = "1.0.0"
    
    private let onNavigateAction: (String, String?) -> Void

    init(onNavigateAction: @escaping (String, String?) -> Void) {
        self.onNavigateAction = onNavigateAction
    }

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return command.hasPrefix("Navigation/")
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        if command.hasPrefix("Navigation/navigate=") {
            let route = String(command.dropFirst("Navigation/navigate=".count))
            onNavigateAction("__navigate", route)
            return true
        } else if command == "Navigation/goBack" {
            onNavigateAction("__goBack", nil)
            return true
        }
        return false
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "navigate":
            onNavigateAction("__navigate", payload["route"] as? String)
            return true
        case "goBack":
            onNavigateAction("__goBack", nil)
            return true
        case "replace":
            onNavigateAction("__replace", payload["route"] as? String)
            return true
        case "reset":
            onNavigateAction("__reset", payload["route"] as? String)
            return true
        default:
            break
        }
        return false
    }
}
