import Foundation
import UIKit

/**
 * ReflectionModule — The "Bridge Factory" for iOS.
 * 
 * Uses the Objective-C runtime to dynamically call native methods.
 */
class ReflectionModule: NSObject, FeatherModule {
    let name = "feather-reflection"
    let version = "1.0.0"

    private let allowList: Set<String> = [
        "UIDevice",
        "UIScreen",
        "UIApplication",
        "NSLocale",
        "NSTimeZone",
        "NSBundle"
    ]

    func canHandleLegacyCommand(_ command: String) -> Bool { false }
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool { false }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        // Payload: { "className": "...", "methodName": "...", "args": [...] }
        guard let className = payload["className"] as? String,
              let methodName = payload["methodName"] as? String else { return false }
        
        // Security Check
        if !allowList.contains(className) {
            print("[ReflectionModule] Blocked call to unauthorized class: \(className)")
            return false
        }

        // Resolve class
        guard let clazz = NSClassFromString(className) as? NSObject.Type else {
            print("[ReflectionModule] Class not found: \(className)")
            return false
        }
        
        let selector = NSSelectorFromString(methodName)
        let args = payload["args"] as? [Any] ?? []
        
        // Resolve target
        let target: Any = resolveTarget(clazz: clazz)
        
        guard (target as AnyObject).responds(to: selector) else {
            print("[ReflectionModule] Target does not respond to selector: \(methodName)")
            return false
        }
        
        // Perform invocation
        let result: Any?
        if args.isEmpty {
            result = (target as AnyObject).perform(selector)?.takeUnretainedValue()
        } else if args.count == 1 {
            result = (target as AnyObject).perform(selector, with: args[0])?.takeUnretainedValue()
        } else if args.count == 2 {
            result = (target as AnyObject).perform(selector, with: args[0], with: args[1])?.takeUnretainedValue()
        } else {
            print("[ReflectionModule] iOS reflection currently supports up to 2 arguments")
            return false
        }
        
        if let resultKey = payload["resultStateKey"] as? String {
            context.sendStateAssignment(resultKey, "\(result ?? "null")")
        }
        
        return true
    }
    
    private func resolveTarget(clazz: NSObject.Type) -> Any {
        if clazz == UIDevice.self { return UIDevice.current }
        if clazz == UIScreen.self { return UIScreen.main }
        if clazz == UIApplication.self { return UIApplication.shared }
        return clazz
    }
}
