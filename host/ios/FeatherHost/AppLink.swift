import Foundation

/// Resolve a path against the declarations of the verified standalone bundle.
func appLinkPath(_ input: URL, links: [[String: Any]]) -> String? {
    guard input.absoluteString.hasPrefix("feather://app/"),
          let url = URLComponents(url: input, resolvingAgainstBaseURL: false),
          url.scheme == "feather", url.host == "app", url.port == nil,
          url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return nil }
    let path = url.percentEncodedPath
    let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.")
    guard path.hasPrefix("/"), path.utf8.count <= 1024,
          path.split(separator: "/", omittingEmptySubsequences: false).dropFirst().allSatisfy({ segment in
              !segment.isEmpty && segment != "." && segment != ".." && segment.unicodeScalars.allSatisfy(allowed.contains)
          }) else { return nil }
    for link in links where (link["scheme"] as? String) == "feather" && (link["host"] as? String) == "app" {
        guard var prefix = link["path_prefix"] as? String else { continue }
        while prefix.hasSuffix("/") { prefix.removeLast() }
        if !prefix.isEmpty && (path == prefix || path.hasPrefix(prefix + "/")) { return path }
    }
    return nil
}
