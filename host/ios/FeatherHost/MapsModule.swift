import Foundation

class MapsModule: FeatherModule {
    let name = "feather-maps"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { return false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { return false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        return true
    }
}
