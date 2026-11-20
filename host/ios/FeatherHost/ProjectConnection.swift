import Foundation

struct ProjectConnection {
    let baseURL: URL
    let socketURL: URL
    let publicKey: String?

    static func parse(_ input: String, allowProjectKey: Bool) -> ProjectConnection? {
        var address = input.trimmingCharacters(in: .whitespacesAndNewlines)
        var publicKey: String?
        if address.hasPrefix("feather:") {
            guard let pairing = URLComponents(string: address),
                  pairing.scheme == "feather", pairing.host == "open",
                  pairing.path.isEmpty, pairing.user == nil, pairing.password == nil,
                  pairing.port == nil, pairing.fragment == nil else { return nil }
            var values: [String: String] = [:]
            for item in pairing.queryItems ?? [] {
                guard let value = item.value, values[item.name] == nil else { return nil }
                values[item.name] = value
            }
            guard let projectURL = values["url"] else { return nil }
            address = projectURL
            if let key = values["key"], !key.isEmpty {
                guard allowProjectKey, let bytes = Data(base64Encoded: key),
                      bytes.count == 32, bytes.base64EncodedString() == key else { return nil }
                publicKey = key
            }
        }

        let implicitScheme = !address.contains("://")
        guard var components = URLComponents(string: implicitScheme ? "http://" + address : address),
              let scheme = components.scheme, scheme == "http" || scheme == "https",
              let host = components.host, !host.isEmpty,
              components.user == nil, components.password == nil,
              components.query == nil, components.fragment == nil,
              (1...65535).contains(components.port ?? 8080) else { return nil }
        for item in components.percentEncodedPath.split(separator: "/") {
            guard let part = item.removingPercentEncoding, part != ".", part != "..",
                  !part.contains("/"), !part.contains("\\") else { return nil }
        }
        if implicitScheme && components.port == nil { components.port = 8080 }
        while components.percentEncodedPath.hasSuffix("/") {
            components.percentEncodedPath.removeLast()
        }
        guard let base = components.url else { return nil }
        components.scheme = scheme == "https" ? "wss" : "ws"
        components.percentEncodedPath += "/ws"
        guard let socket = components.url else { return nil }
        return ProjectConnection(baseURL: base, socketURL: socket, publicKey: publicKey)
    }
}
