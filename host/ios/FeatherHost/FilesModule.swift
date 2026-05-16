import Foundation
import SwiftUI

class FilesModule: FeatherModule {
    let name = "feather-files"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool { return false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { return false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        let fileManager = FileManager.default
        guard let docsUrl = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return false }
        
        let path = payload["path"] as? String ?? ""
        let fileUrl = docsUrl.appendingPathComponent(path)
        
        switch method {
        case "write":
            let content = payload["content"] as? String ?? ""
            try? fileManager.createDirectory(at: fileUrl.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? content.write(to: fileUrl, atomically: true, encoding: .utf8)
            return true
        case "read":
            let content = (try? String(contentsOf: fileUrl, encoding: .utf8)) ?? ""
            context.sendStateAssignment("", content)
            return true
        case "delete":
            try? fileManager.removeItem(at: fileUrl)
            return true
        case "list":
            let contents = (try? fileManager.contentsOfDirectory(atPath: fileUrl.path)) ?? []
            if let data = try? JSONSerialization.data(withJSONObject: contents),
               let str = String(data: data, encoding: .utf8) {
                context.sendStateAssignment("", str)
            }
            return true
        default:
            return false
        }
    }
}
