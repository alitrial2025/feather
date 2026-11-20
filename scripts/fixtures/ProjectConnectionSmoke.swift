import Foundation

@main
struct ProjectConnectionSmoke {
    static func main() {
        let key = Data(repeating: 42, count: 32).base64EncodedString()
        let scoped = "https://demo.example:8443/session/test-token"
        var deepLink = URLComponents()
        deepLink.scheme = "feather"
        deepLink.host = "open"
        deepLink.queryItems = [
            URLQueryItem(name: "url", value: scoped + "/"),
            URLQueryItem(name: "key", value: key)
        ]
        let link = deepLink.string!
        let paired = ProjectConnection.parse(link, allowProjectKey: true)!
        precondition(paired.baseURL.absoluteString == scoped)
        precondition(paired.socketURL.absoluteString == "wss://demo.example:8443/session/test-token/ws")
        precondition(paired.publicKey == key)
        precondition(ProjectConnection.parse(link, allowProjectKey: false) == nil)

        precondition(ProjectConnection.parse("localhost:9000", allowProjectKey: true)?.socketURL.absoluteString == "ws://localhost:9000/ws")
        for invalid in ["file:///tmp/app", "https://user:pass@demo.example", "https://demo.example/%2e%2e/private",
                        "https://demo.example/session%2Ftoken", link + "&key=duplicate", "https://demo.example/path?query=x"] {
            precondition(ProjectConnection.parse(invalid, allowProjectKey: true) == nil, invalid)
        }
        print("PASS: iOS scoped QR URL, signing key, and release pairing policy")
        let links = [["scheme": "feather", "host": "app", "path_prefix": "/device-info"]]
        let appURL = URL(string: "feather://app/device-info/display")!
        precondition(appLinkPath(appURL, links: links) == "/device-info/display")
        precondition(appLinkPath(appURL, links: []) == nil)
        for invalid in ["feather://open/device-info", "feather://app/device-information",
                        "feather://app/device-info/%2fadmin", "feather://app/device-info?target=admin"] {
            precondition(appLinkPath(URL(string: invalid)!, links: links) == nil, invalid)
        }
        print("PASS: iOS app link routing matches declared path components")
    }
}
