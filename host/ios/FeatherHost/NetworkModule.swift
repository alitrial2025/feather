import Foundation

class NetworkModule: FeatherModule {
    let name = "feather-network"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return command.hasPrefix("Network/")
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        let parts = command.split(separator: "=", maxSplits: 1)
        guard parts.count == 2 else { return false }
        
        let method: String
        let cmd = String(parts[0])
        if cmd == "Network/get" { method = "GET" }
        else if cmd == "Network/post" { method = "POST" }
        else if cmd == "Network/put" { method = "PUT" }
        else if cmd == "Network/patch" { method = "PATCH" }
        else if cmd == "Network/delete" { method = "DELETE" }
        else { return false }
        
        let payloadStr = String(parts[1])
        if let data = payloadStr.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            performRequest(method: method, payload: json, legacyStateKey: json["stateKey"] as? String, context: context)
            return true
        }
        return false
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        let httpMethod = method.uppercased()
        let allowedMethods = ["GET", "POST", "PUT", "PATCH", "DELETE"]
        guard allowedMethods.contains(httpMethod) else { return false }
        
        performRequest(method: httpMethod, payload: payload, legacyStateKey: nil, context: context)
        return true
    }

    private func performRequest(method: String, payload: [String: Any], legacyStateKey: String?, context: FeatherModuleContext) {
        guard let urlString = payload["url"] as? String, let requestURL = URL(string: urlString) else { return }
        
        var request = URLRequest(url: requestURL)
        request.httpMethod = method
        
        if let headers = payload["headers"] as? [String: String] {
            for (key, value) in headers {
                request.setValue(value, forHTTPHeaderField: key)
            }
        }
        
        let bodyType = payload["bodyType"] as? String
        
        if method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE" {
            let bodyObj = payload["body"]
            
            if bodyType == "form", let bodyDict = bodyObj as? [String: Any] {
                var components: [String] = []
                for (key, value) in bodyDict {
                    let keyEncoded = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
                    let valueEncoded = "\(value)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "\(value)"
                    components.append("\(keyEncoded)=\(valueEncoded)")
                }
                request.httpBody = components.joined(separator: "&").data(using: .utf8)
                if request.value(forHTTPHeaderField: "Content-Type") == nil {
                    request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
                }
            } else {
                if let bodyDict = bodyObj as? [String: Any], let bodyData = try? JSONSerialization.data(withJSONObject: bodyDict) {
                    request.httpBody = bodyData
                } else if let bodyArr = bodyObj as? [Any], let bodyData = try? JSONSerialization.data(withJSONObject: bodyArr) {
                    request.httpBody = bodyData
                } else if let bodyStr = bodyObj as? String, !bodyStr.isEmpty {
                    request.httpBody = bodyStr.data(using: .utf8)
                }
                
                if request.value(forHTTPHeaderField: "Content-Type") == nil && request.httpBody != nil {
                    request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
                }
            }
        }

        let task = context.urlSession.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    let errorObj: [String: Any] = [
                        "code": "network_error",
                        "message": error.localizedDescription,
                        "recoverable": true
                    ]
                    if let errData = try? JSONSerialization.data(withJSONObject: errorObj),
                       let errStr = String(data: errData, encoding: .utf8) {
                        let key = legacyStateKey != nil ? "\(legacyStateKey!)_error" : "error"
                        context.sendStateAssignment(key, errStr)
                    }
                    return
                }
                
                guard let httpResponse = response as? HTTPURLResponse else { return }
                
                var resultObj: [String: Any] = [:]
                resultObj["status"] = httpResponse.statusCode
                
                var responseHeaders: [String: String] = [:]
                for (key, value) in httpResponse.allHeaderFields {
                    if let keyStr = key as? String, let valStr = value as? String {
                        responseHeaders[keyStr] = valStr
                    }
                }
                resultObj["headers"] = responseHeaders
                
                if let data = data, let bodyStr = String(data: data, encoding: .utf8) {
                    if let jsonBody = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                        resultObj["body"] = jsonBody
                    } else if let jsonArr = try? JSONSerialization.jsonObject(with: data) as? [Any] {
                        resultObj["body"] = jsonArr
                    } else {
                        resultObj["body"] = bodyStr
                    }
                } else {
                    resultObj["body"] = ""
                }
                
                if let resData = try? JSONSerialization.data(withJSONObject: resultObj),
                   let resStr = String(data: resData, encoding: .utf8) {
                    if httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
                        context.sendStateAssignment(legacyStateKey ?? "", resStr)
                    } else {
                        let key = legacyStateKey != nil ? "\(legacyStateKey!)_error" : "error"
                        context.sendStateAssignment(key, resStr)
                    }
                }
            }
        }
        task.resume()
    }
}
