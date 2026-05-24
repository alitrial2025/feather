import Foundation
import UIKit

@objc(DeviceInfoModule)
class DeviceInfoModule: NSObject, FeatherPlugin {
    let name = "feather-device-info"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return false
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        return false
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        switch method {
        case "getDeviceInfo":
            UIDevice.current.isBatteryMonitoringEnabled = true
            let battery = Int(UIDevice.current.batteryLevel * 100)
            
            let bounds = UIScreen.main.bounds
            let scale = UIScreen.main.scale
            
            let result: [String: Any] = [
                "os": "ios",
                "version": UIDevice.current.systemVersion,
                "model": UIDevice.current.model,
                "scale": scale,
                "width": bounds.width,
                "height": bounds.height,
                "battery": battery
            ]
            
            if let data = try? JSONSerialization.data(withJSONObject: result),
               let str = String(data: data, encoding: .utf8) {
                context.sendStateAssignment("", str)
            }
            return true
        default:
            return false
        }
    }
}
