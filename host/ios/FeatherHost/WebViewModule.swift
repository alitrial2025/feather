import Foundation
import SwiftUI
import WebKit

class WebViewModule: FeatherModule {
    let name = "feather-webview"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return false
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        return false
    }

    func canRender(_ node: FeatherNode) -> Bool {
        if case .webView = node {
            return true
        }
        return false
    }

    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView {
        if case .webView(_, let url, let style) = node {
            return AnyView(FeatherWebView(url: url, style: style))
        }
        return AnyView(EmptyView())
    }
}

struct FeatherWebView: UIViewRepresentable {
    let url: String
    let style: FeatherStyle?

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.configuration.preferences.javaScriptEnabled = true
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if let requestURL = URL(string: url) {
            let request = URLRequest(url: requestURL)
            uiView.load(request)
        }
    }
}
