import Foundation
import SwiftUI

/// A protocol for native platform modules in the iOS host.
protocol FeatherModule {
    var name: String { get }
    var version: String { get }

    func getWidgets() -> [String]

    /// Returns true if this module can handle the legacy `host:` command string.
    func canHandleLegacyCommand(_ command: String) -> Bool
    
    /// Executes a legacy `host:` command.
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool
    
    /// Executes a structured method call.
    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool
    
    /// Returns true if this module can render the given node.
    func canRender(_ node: FeatherNode) -> Bool
    
    /// Lifecycle hooks
    func initialize(context: FeatherModuleContext)
    func start()
    func stop()
    func destroy()
    
    /// Renders a module-specific node.
    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView
}

class NativeModuleWrapper: NativeModule {
    private let module: FeatherModule
    private let context: FeatherModuleContext
    
    init(module: FeatherModule, context: FeatherModuleContext) {
        self.module = module
        self.context = context
    }
    
    func getInfo() -> ModuleInfo {
        return ModuleInfo(name: module.name, version: module.version)
    }

    func getWidgets() -> [String] {
        return module.getWidgets()
    }
    
    func callSync(action: String, payload: String) throws -> String {
        let data = payload.data(using: .utf8) ?? Data()
        let json = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        if module.call(method: action, payload: json, context: context) {
            return "{\"status\": \"success\"}"
        }
        throw ModuleError.ExecutionFailed(message: "Module call failed")
    }
    
    func callAsync(action: String, payload: String, callback: NativeCallback) {
        do {
            let result = try callSync(action: action, payload: payload)
            callback.onSuccess(result: result)
        } catch {
            callback.onError(error: error.localizedDescription)
        }
    }
}


/// Default implementations for optional methods.
extension FeatherModule {
    func getWidgets() -> [String] {
        return []
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        return false
    }
    
    func canRender(_ node: FeatherNode) -> Bool {
        return false
    }
    
    func initialize(context: FeatherModuleContext) {}
    func start() {}
    func stop() {}
    func destroy() {}
    
    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView {
        return AnyView(EmptyView())
    }
}

/// Context passed to module methods, providing access to host resources.
struct FeatherModuleContext {
    let urlSession: URLSession
    let sendStateAssignment: (String, String) -> Void
}

/// Registry for all available native modules.
class ModuleRegistry {
    private var modules: [String: FeatherModule] = [:]

    /// Registers a new module.
    func register(_ module: FeatherModule) {
        modules[module.name] = module
    }

    /// Returns all registered modules.
    func registeredModules() -> [FeatherModule] {
        return Array(modules.values)
    }

    /// Checks if a module with the given name is registered.
    func hasModule(_ name: String) -> Bool {
        return modules[name] != nil
    }

    /// Dispatches a legacy command to the first module that can handle it.
    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        for module in modules.values {
            if module.canHandleLegacyCommand(command) {
                if module.handleLegacyCommand(command, context: context) {
                    return true
                }
            }
        }
        return false
    }

    /// Dispatches a structured module call.
    func call(moduleName: String, method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        guard let module = modules[moduleName] else { return false }
        return module.call(method: method, payload: payload, context: context)
    }

    /// Returns the module capable of rendering the given node, if any.
    func rendererFor(_ node: FeatherNode) -> FeatherModule? {
        return modules.values.first { $0.canRender(node) }
    }
    
    /// Lifecycle broadcasts
    func initializeAll(context: FeatherModuleContext) {
        modules.values.forEach { $0.initialize(context: context) }
    }
    
    func onStart() {
        modules.values.forEach { $0.start() }
    }
    
    func onStop() {
        modules.values.forEach { $0.stop() }
    }
    
    func onDestroy() {
        modules.values.forEach { $0.destroy() }
    }
}
