import Foundation

/// A protocol that extends FeatherModule to identify third-party plugins.
protocol FeatherPlugin: FeatherModule {}

/// A dynamic loader that searches for dynamic classes declared in a config file.
class FeatherPluginLoader {
    private static let pluginsJsonName = "feather_plugins"
    
    static func loadPlugins() -> [FeatherPlugin] {
        var plugins: [FeatherPlugin] = []
        
        guard let path = Bundle.main.path(forResource: pluginsJsonName, ofType: "json") else {
            print("FeatherPluginLoader: feather_plugins.json not found in main bundle")
            return plugins
        }
        
        do {
            let data = try Data(contentsOf: URL(fileURLWithPath: path))
            if let jsonObject = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
               let pluginNames = jsonObject["plugins"] as? [String] {
                for className in pluginNames {
                    // Try to instantiate class dynamically via reflection.
                    // Classes in Swift are namespaced by target/module.
                    // To handle both namespace-prefixed and raw names, we try both.
                    var pluginClass: AnyClass? = NSClassFromString(className)
                    
                    if pluginClass == nil {
                        // Try prefixing target namespace (usually the host app's name, e.g., "FeatherHost.ClassName")
                        let appName = Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String ?? ""
                        let sanitizedAppName = appName.replacingOccurrences(of: " ", with: "_")
                        let namespacedClassName = "\(sanitizedAppName).\(className)"
                        pluginClass = NSClassFromString(namespacedClassName)
                    }
                    
                    if let validClass = pluginClass as? NSObject.Type {
                        if let pluginInstance = validClass.init() as? FeatherPlugin {
                            plugins.append(pluginInstance)
                            print("FeatherPluginLoader: Successfully loaded plugin dynamic instance: \(className)")
                        } else {
                            print("FeatherPluginLoader: Class \(className) does not conform to FeatherPlugin")
                        }
                    } else {
                        print("FeatherPluginLoader: Could not find or cast class \(className)")
                    }
                }
            }
        } catch {
            print("FeatherPluginLoader: Failed to parse \(pluginsJsonName).json: \(error.localizedDescription)")
        }
        
        return plugins
    }
}
