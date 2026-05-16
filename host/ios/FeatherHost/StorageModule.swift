import Foundation

class StorageModule: FeatherModule {
    let name = "feather-storage"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return command.hasPrefix("Storage/")
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        let defaults = UserDefaults.standard
        
        if command.hasPrefix("Storage/set=") {
            let jsonStr = String(command.dropFirst("Storage/set=".count))
            if let data = jsonStr.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let key = json["key"] as? String {
                defaults.set(json["value"] as? String ?? "", forKey: key)
                return true
            }
        } else if command.hasPrefix("Storage/get=") {
            let jsonStr = String(command.dropFirst("Storage/get=".count))
            if let data = jsonStr.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let key = json["key"] as? String,
               let stateKey = json["stateKey"] as? String {
                let value = defaults.string(forKey: key) ?? ""
                context.sendStateAssignment(stateKey, value)
                return true
            }
        } else if command.hasPrefix("Storage/remove=") {
            let key = String(command.dropFirst("Storage/remove=".count))
            defaults.removeObject(forKey: key)
            return true
        } else if command == "Storage/clear" {
            let domain = Bundle.main.bundleIdentifier!
            defaults.removePersistentDomain(forName: domain)
            return true
        }
        
        return false
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        let defaults = UserDefaults.standard
        
        switch method.lowercased() {
        case "set":
            if let key = payload["key"] as? String {
                if let dict = payload["value"] as? [String: Any], let data = try? JSONSerialization.data(withJSONObject: dict) {
                    defaults.set(String(data: data, encoding: .utf8) ?? "", forKey: key)
                } else if let arr = payload["value"] as? [Any], let data = try? JSONSerialization.data(withJSONObject: arr) {
                    defaults.set(String(data: data, encoding: .utf8) ?? "", forKey: key)
                } else {
                    defaults.set(payload["value"] as? String ?? "", forKey: key)
                }
                return true
            }
        case "get":
            if let key = payload["key"] as? String {
                let value = defaults.string(forKey: key) ?? ""
                context.sendStateAssignment("", value)
                return true
            }
        case "remove":
            if let key = payload["key"] as? String {
                defaults.removeObject(forKey: key)
                return true
            }
        case "clear":
            let domain = Bundle.main.bundleIdentifier!
            defaults.removePersistentDomain(forName: domain)
            return true
        default:
            break
        }
        return false
    }
}
