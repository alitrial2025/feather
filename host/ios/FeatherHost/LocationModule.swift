import Foundation

class LocationModule: FeatherModule {
    let name = "feather-location"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { return false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { return false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "getCurrentLocation":
            // Return a mock/placeholder location for now
            let location: [String: Any] = [
                "latitude": 37.7749,
                "longitude": -122.4194,
                "accuracy": 10.0
            ]
            if let data = try? JSONSerialization.data(withJSONObject: location),
               let str = String(data: data, encoding: .utf8) {
                context.sendStateAssignment("", str)
            }
            return true
        default:
            return false
        }
    }
}
