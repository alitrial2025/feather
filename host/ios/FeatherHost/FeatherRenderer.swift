import Foundation
import SwiftUI

// MARK: - Style

struct FeatherStyle {
    var color: Color?; var fontSize: CGFloat?; var fontWeight: Font.Weight?
    var fontStyle: String?; var textAlign: TextAlignment?; var background: Color?
    var padding: CGFloat?; var paddingHorizontal: CGFloat?; var paddingVertical: CGFloat?
    var paddingStart: CGFloat?; var paddingEnd: CGFloat?; var paddingTop: CGFloat?; var paddingBottom: CGFloat?
    var margin: CGFloat?; var cornerRadius: CGFloat?; var borderColor: Color?; var borderWidth: CGFloat?
    var width: String?; var height: String?; var minWidth: String?; var minHeight: String?
    var maxWidth: String?; var maxHeight: String?; var alignment: String?; var spacing: CGFloat?
    var opacity: Double?; var textColor: Color?; var placeholderColor: Color?
    var contentMode: String?; var maxLines: Int?; var elevation: Int?
    var fillMaxWidth: Bool?; var fillMaxHeight: Bool?; var aspectRatio: Float?
    var gradient: String?; var clip: Bool?; var shadow: String?
    var position: String?; var top: CGFloat?; var bottom: CGFloat?; var left: CGFloat?; var right: CGFloat?
    var zIndex: Double?

    static func parse(from dict: [String: Any]?) -> FeatherStyle? {
        guard let d = dict, !d.isEmpty else { return nil }
        let styleKeys: Set<String> = [
            "color", "fontSize", "fontWeight", "fontStyle", "textAlign", "background",
            "padding", "paddingHorizontal", "paddingVertical", "paddingStart", "paddingEnd",
            "paddingTop", "paddingBottom", "margin", "cornerRadius", "borderColor",
            "borderWidth", "width", "height", "minWidth", "minHeight", "maxWidth",
            "maxHeight", "alignment", "spacing", "opacity", "textColor", "placeholderColor",
            "contentMode", "maxLines", "elevation", "fillMaxWidth", "fillMaxHeight",
            "aspectRatio", "gradient", "clip", "shadow", "position", "top", "bottom",
            "left", "right", "zIndex"
        ]
        guard d.keys.contains(where: { styleKeys.contains($0) }) else { return nil }
        var s = FeatherStyle()
        s.color = parseColor(d["color"] as? String)
        s.fontSize = (d["fontSize"] as? NSNumber)?.cgFloatValue
        s.fontWeight = parseFontWeight(d["fontWeight"] as? String)
        s.fontStyle = d["fontStyle"] as? String
        s.textAlign = parseTextAlign(d["textAlign"] as? String)
        s.background = parseColor(d["background"] as? String)
        s.padding = (d["padding"] as? NSNumber)?.cgFloatValue
        s.paddingHorizontal = (d["paddingHorizontal"] as? NSNumber)?.cgFloatValue
        s.paddingVertical = (d["paddingVertical"] as? NSNumber)?.cgFloatValue
        s.paddingStart = (d["paddingStart"] as? NSNumber)?.cgFloatValue
        s.paddingEnd = (d["paddingEnd"] as? NSNumber)?.cgFloatValue
        s.paddingTop = (d["paddingTop"] as? NSNumber)?.cgFloatValue
        s.paddingBottom = (d["paddingBottom"] as? NSNumber)?.cgFloatValue
        s.margin = (d["margin"] as? NSNumber)?.cgFloatValue
        s.cornerRadius = (d["cornerRadius"] as? NSNumber)?.cgFloatValue
        s.borderColor = parseColor(d["borderColor"] as? String)
        s.borderWidth = (d["borderWidth"] as? NSNumber)?.cgFloatValue
        s.width = d["width"] as? String; s.height = d["height"] as? String
        s.minWidth = d["minWidth"] as? String; s.minHeight = d["minHeight"] as? String
        s.maxWidth = d["maxWidth"] as? String; s.maxHeight = d["maxHeight"] as? String
        s.alignment = d["alignment"] as? String
        s.spacing = (d["spacing"] as? NSNumber)?.cgFloatValue
        s.opacity = (d["opacity"] as? NSNumber)?.doubleValue
        s.textColor = parseColor(d["textColor"] as? String)
        s.placeholderColor = parseColor(d["placeholderColor"] as? String)
        s.contentMode = d["contentMode"] as? String
        s.maxLines = (d["maxLines"] as? NSNumber)?.intValue
        s.elevation = (d["elevation"] as? NSNumber)?.intValue
        s.fillMaxWidth = d["fillMaxWidth"] as? Bool
        s.fillMaxHeight = d["fillMaxHeight"] as? Bool
        s.aspectRatio = (d["aspectRatio"] as? NSNumber)?.floatValue
        s.gradient = d["gradient"] as? String; s.clip = d["clip"] as? Bool
        s.shadow = d["shadow"] as? String
        s.position = d["position"] as? String
        s.top = (d["top"] as? NSNumber)?.cgFloatValue
        s.bottom = (d["bottom"] as? NSNumber)?.cgFloatValue
        s.left = (d["left"] as? NSNumber)?.cgFloatValue
        s.right = (d["right"] as? NSNumber)?.cgFloatValue
        s.zIndex = (d["zIndex"] as? NSNumber)?.doubleValue
        return s
    }

    static func parseColor(_ hex: String?) -> Color? {
        guard let hex = hex?.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: ""),
              hex.count == 6 || hex.count == 8 else { return nil }
        var int: UInt64 = 0; Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        if hex.count == 8 { a = (int >> 24) & 0xFF; r = (int >> 16) & 0xFF; g = (int >> 8) & 0xFF; b = int & 0xFF }
        else { a = 255; r = (int >> 16) & 0xFF; g = (int >> 8) & 0xFF; b = int & 0xFF }
        return Color(.sRGB, red: Double(r)/255, green: Double(g)/255, blue: Double(b)/255, opacity: Double(a)/255)
    }

    private static func parseFontWeight(_ w: String?) -> Font.Weight? {
        switch w {
        case "bold", "700": return .bold; case "100": return .ultraLight; case "200": return .thin
        case "300": return .light; case "400", "normal": return .regular; case "500": return .medium
        case "600": return .semibold; case "800": return .heavy; case "900": return .black
        default: return nil
        }
    }
    private static func parseTextAlign(_ a: String?) -> TextAlignment? {
        switch a { case "center": return .center; case "right", "end": return .trailing; case "left", "start": return .leading; default: return nil }
    }
}

private extension NSNumber { var cgFloatValue: CGFloat { CGFloat(doubleValue) } }

// MARK: - Style Modifier

struct FeatherStyleModifier: ViewModifier {
    let style: FeatherStyle?
    func body(content: Content) -> some View {
        content
            .ifLet(style?.margin) { v, m in v.padding(m) }
            .ifLet(style?.cornerRadius) { v, r in v.clipShape(RoundedRectangle(cornerRadius: r)) }
            .ifLet(style?.background) { v, bg in v.background(bg) }
            .ifLet(style?.borderWidth) { v, bw in v.overlay(RoundedRectangle(cornerRadius: style?.cornerRadius ?? 0).stroke(style?.borderColor ?? .gray, lineWidth: bw)) }
            .padding(.leading, style?.paddingStart ?? style?.paddingHorizontal ?? style?.padding ?? 0)
            .padding(.trailing, style?.paddingEnd ?? style?.paddingHorizontal ?? style?.padding ?? 0)
            .padding(.top, style?.paddingTop ?? style?.paddingVertical ?? style?.padding ?? 0)
            .padding(.bottom, style?.paddingBottom ?? style?.paddingVertical ?? style?.padding ?? 0)
            .ifLet(style?.opacity) { v, o in v.opacity(o) }
            .ifLet(style?.width) { v, w in w == "match" || w == "fill" ? AnyView(v.frame(maxWidth: .infinity)) : (Double(w).map { AnyView(v.frame(width: CGFloat($0))) } ?? AnyView(v)) }
            .ifLet(style?.height) { v, h in h == "match" || h == "fill" ? AnyView(v.frame(maxHeight: .infinity)) : (Double(h).map { AnyView(v.frame(height: CGFloat($0))) } ?? AnyView(v)) }
            .ifLet(style?.shadow) { v, _ in v.shadow(radius: 4) }
            .ifLet(style?.zIndex) { v, z in v.zIndex(z) }
            .if(style?.position == "absolute") { v in
                v.offset(x: style?.left ?? 0, y: style?.top ?? 0)
            }
    }
}

extension View {
    func featherStyle(_ style: FeatherStyle?) -> some View { modifier(FeatherStyleModifier(style: style)) }
    @ViewBuilder func ifLet<T>(_ value: T?, transform: (Self, T) -> some View) -> some View {
        if let value = value { transform(self, value) } else { self }
    }
}

// MARK: - DynamicView (50+ widget renderer)

struct DynamicView: View {
    let node: FeatherNode
    let onAction: (String, String?) -> Void
    @State private var textInputValue = ""

    var body: some View {
        switch node {
        // ── Basic ──
        case .text(_, let text, let style):
            Text(text)
                .ifLet(style?.color ?? style?.textColor) { v, c in v.foregroundColor(c) }
                .ifLet(style?.fontSize) { v, s in v.font(.system(size: s)) }
                .ifLet(style?.fontWeight) { v, w in v.fontWeight(w) }
                .ifLet(style?.textAlign) { v, a in v.multilineTextAlignment(a) }
                .ifLet(style?.maxLines) { v, n in v.lineLimit(n) }
                .featherStyle(style)

        case .button(_, let text, let action, let enabled, let style):
            Button(action: { onAction(action, nil) }) {
                Text(text)
                    .ifLet(style?.textColor ?? style?.color) { v, c in v.foregroundColor(c) }
                    .ifLet(style?.fontSize) { v, s in v.font(.system(size: s)) }
            }
            .disabled(!enabled)
            .ifLet(style?.background) { v, bg in
                v.buttonStyle(.plain)
                    .padding(.horizontal, style?.paddingHorizontal ?? 16)
                    .padding(.vertical, style?.paddingVertical ?? 8)
                    .background(bg).clipShape(RoundedRectangle(cornerRadius: style?.cornerRadius ?? 8))
            }

        case .textInput(_, let value, let placeholder, let action, _, let style):
            TextField(placeholder, text: Binding(get: { textInputValue.isEmpty ? value : textInputValue }, set: { textInputValue = $0; onAction(action, $0) }))
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .featherStyle(style)
                .onAppear { textInputValue = value }

        case .image(_, _, let alt, let style):
            Rectangle().fill(Color.gray.opacity(0.3))
                .overlay(Text(alt.isEmpty ? "Image" : alt).foregroundColor(.secondary))
                .featherStyle(style)

        case .icon(_, let name, _, let color, let action):
            let img = Image(systemName: mapIconName(name))
            if let action = action {
                Button(action: { onAction(action, nil) }) { img.ifLet(FeatherStyle.parseColor(color)) { v, c in v.foregroundColor(c) } }
            } else {
                img.ifLet(FeatherStyle.parseColor(color)) { v, c in v.foregroundColor(c) }
            }

        case .iconButton(_, let icon, let action, _, let color, _):
            Button(action: { onAction(action, nil) }) {
                Image(systemName: mapIconName(icon)).ifLet(FeatherStyle.parseColor(color)) { v, c in v.foregroundColor(c) }
            }

        case .spacer(_, _): SwiftUI.Spacer()
        case .divider(_, let color, _, _):
            Divider().ifLet(FeatherStyle.parseColor(color)) { v, c in v.overlay(c) }

        // ── Layouts ──
        case .column(_, let children, let style):
            VStack(alignment: hAlign(style?.alignment), spacing: style?.spacing ?? 8) {
                ForEach(children) { c in DynamicView(node: c, onAction: onAction) }
            }.featherStyle(style)

        case .row(_, let children, let style):
            HStack(alignment: vAlign(style?.alignment), spacing: style?.spacing ?? 8) {
                ForEach(children) { c in DynamicView(node: c, onAction: onAction) }
            }.featherStyle(style)

        case .box(_, let children, let style):
            ZStack(alignment: zAlign(style?.alignment)) {
                ForEach(children) { c in DynamicView(node: c, onAction: onAction) }
            }.featherStyle(style)

        case .lazyColumn(_, let items, let style):
            List { ForEach(items) { i in DynamicView(node: i, onAction: onAction).listRowInsets(EdgeInsets()).listRowBackground(Color.clear) } }
                .listStyle(.plain).featherStyle(style)

        case .lazyRow(_, let items, let style):
            ScrollView(.horizontal) { LazyHStack(spacing: style?.spacing ?? 8) { ForEach(items) { i in DynamicView(node: i, onAction: onAction) } } }.featherStyle(style)

        case .lazyGrid(_, let items, let columns, let style):
            let cols = Array(repeating: GridItem(.flexible()), count: columns)
            ScrollView { LazyVGrid(columns: cols, spacing: style?.spacing ?? 8) { ForEach(items) { i in DynamicView(node: i, onAction: onAction) } } }.featherStyle(style)

        case .scrollView(_, let children, let direction, let style):
            ScrollView(direction == "horizontal" ? .horizontal : .vertical) {
                let stack = VStack(spacing: style?.spacing ?? 8) { ForEach(children) { c in DynamicView(node: c, onAction: onAction) } }
                stack.featherStyle(style)
            }

        case .card(_, let children, let action, let style):
            let content = VStack(alignment: .leading, spacing: style?.spacing ?? 8) { ForEach(children) { c in DynamicView(node: c, onAction: onAction) } }
                .padding(style?.padding ?? 12).background(style?.background ?? Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: style?.cornerRadius ?? 8)).shadow(radius: 2)
            if let action = action { Button(action: { onAction(action, nil) }) { content } } else { content }

        case .surface(_, let children, let style):
            VStack { ForEach(children) { c in DynamicView(node: c, onAction: onAction) } }.featherStyle(style)

        case .aspectRatio(_, let ratio, let child):
            DynamicView(node: child, onAction: onAction).aspectRatio(CGFloat(ratio), contentMode: .fit)

        // ── Interactive ──
        case .switchNode(_, let checked, let action, let enabled, let label, _):
            Toggle(label ?? "", isOn: Binding(get: { checked }, set: { onAction(action, "\($0)") })).disabled(!enabled).padding(8)

        case .checkbox(_, let checked, let action, _, let label, _):
            Button(action: { onAction(action, "\(!checked)") }) {
                HStack { Image(systemName: checked ? "checkmark.square.fill" : "square"); if let l = label { Text(l) } }
            }.padding(8)

        case .radioGroup(_, let selected, let action, let options):
            VStack(alignment: .leading) { ForEach(options, id: \.value) { o in
                Button(action: { onAction(action, o.value) }) { HStack { Image(systemName: o.value == selected ? "largecircle.fill.circle" : "circle"); Text(o.label) } }.padding(4)
            } }

        case .slider(_, let value, let min, let max, _, let action, let label, _):
            VStack { if let l = label { Text(l) }; SwiftUI.Slider(value: Binding(get: { value }, set: { onAction(action, "\($0)") }), in: min...max) }.padding(8)

        case .searchBar(_, let value, let placeholder, let action, let style):
            TextField(placeholder, text: Binding(get: { value }, set: { onAction(action, $0) }))
                .textFieldStyle(RoundedBorderTextFieldStyle()).featherStyle(style)

        case .dropdownMenu(_, let selected, _, _, let label, let style):
            VStack(alignment: .leading) { if let l = label { Text(l).font(.caption).foregroundColor(.gray) }; Text(selected.isEmpty ? "Select..." : selected).padding(8).overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.gray)) }.featherStyle(style)

        // ── Progress ──
        case .linearProgress(_, let progress, let color, _):
            ProgressView(value: Double(progress ?? 0)).ifLet(FeatherStyle.parseColor(color)) { v, c in v.tint(c) }
        case .circularProgress(_, let progress, let color, _):
            if let p = progress { ProgressView(value: Double(p)).progressViewStyle(.circular).ifLet(FeatherStyle.parseColor(color)) { v, c in v.tint(c) } }
            else { ProgressView().progressViewStyle(.circular) }

        // ── Feedback ──
        case .dialog(_, _, _, _, _, _, _, _): EmptyView() // Dialogs managed by state
        case .bottomSheet(_, let visible, let content, _, let style):
            if visible { VStack { DynamicView(node: content, onAction: onAction) }.featherStyle(style) }
        case .snackbar(_, let message, let visible, let actionText, let action):
            if visible { HStack { Text(message).foregroundColor(.white); SwiftUI.Spacer(); if let at = actionText { Button(at) { if let a = action { onAction(a, nil) } }.foregroundColor(.yellow) } }.padding().background(Color(.darkGray)).cornerRadius(8).padding() }
        case .badge(_, let count, let color, let child):
            ZStack(alignment: .topTrailing) { if let c = child { DynamicView(node: c, onAction: onAction) }; if count > 0 { Text(count > 99 ? "99+" : "\(count)").font(.caption2).foregroundColor(.white).padding(4).background(FeatherStyle.parseColor(color) ?? .red).clipShape(Circle()) } }
        case .chip(_, let label, let selected, let action, _, let color):
            Button(action: { if let a = action { onAction(a, nil) } }) { Text(label).font(.footnote).padding(.horizontal, 12).padding(.vertical, 6).background((selected == true ? FeatherStyle.parseColor(color) ?? .blue : Color(.systemGray5))).foregroundColor(selected == true ? .white : .primary).clipShape(Capsule()) }
        case .tooltip(_, _, let child): DynamicView(node: child, onAction: onAction)
        case .floatingActionButton(_, let icon, let action, let background, let iconColor):
            Button(action: { onAction(action, nil) }) { Image(systemName: mapIconName(icon)).font(.title2).foregroundColor(FeatherStyle.parseColor(iconColor) ?? .white).padding().background(FeatherStyle.parseColor(background) ?? .blue).clipShape(Circle()).shadow(radius: 4) }
        case .placeholder(_, let width, let height, let cr):
            RoundedRectangle(cornerRadius: CGFloat(cr ?? 4)).fill(Color.gray.opacity(0.2)).frame(width: CGFloat(Double(width ?? "100") ?? 100), height: CGFloat(Double(height ?? "20") ?? 20))

        // ── Scaffold ──
        case .scaffold(_, let topBar, _, _, _, let body, let style):
            VStack(spacing: 0) { if let tb = topBar { DynamicView(node: tb, onAction: onAction) }; DynamicView(node: body, onAction: onAction).frame(maxWidth: .infinity, maxHeight: .infinity) }.featherStyle(style)
        case .topAppBar(_, let title, _, let navIcon, let navAction, let bg, let contentColor):
            HStack { if let ni = navIcon { Button(action: { if let a = navAction { onAction(a, nil) } }) { Image(systemName: mapIconName(ni)) } }; Text(title).font(.headline); SwiftUI.Spacer() }
                .padding().background(FeatherStyle.parseColor(bg) ?? Color(.systemBackground)).foregroundColor(FeatherStyle.parseColor(contentColor) ?? .primary)
        case .bottomAppBar(_, let children, let bg, _):
            HStack { ForEach(children) { c in DynamicView(node: c, onAction: onAction) } }.padding().background(FeatherStyle.parseColor(bg) ?? Color(.systemBackground))
        case .tabRow(_, let tabs, let selected, let action, _, _):
            HStack { ForEach(Array(tabs.enumerated()), id: \.offset) { i, t in Button(action: { onAction(action, "\(i)") }) { Text(t.title).fontWeight(i == selected ? .bold : .regular).padding(8) } } }
        case .pager(_, let pages, let currentPage, _, let style):
            if let p = currentPage, p < pages.count { DynamicView(node: pages[p], onAction: onAction).featherStyle(style) }
        case .drawer(_, let header, _, let style):
            VStack(alignment: .leading) { if let h = header { DynamicView(node: h, onAction: onAction) } }.featherStyle(style)

        // ── Navigation ──
        case .navigator(_, let initialRoute, let screens):
            NavigatorView(initialRoute: initialRoute, screens: screens, onAction: onAction)
        case .bottomTabs(_, let tabs, let style):
            TabView { ForEach(tabs) { t in DynamicView(node: t.content, onAction: onAction).tabItem { Label(t.title, systemImage: t.icon ?? "house") } } }.featherStyle(style)
        case .tab(_, _, _, _, let content): DynamicView(node: content, onAction: onAction)

        // ── Logic ──
        case .forEach(_, let itemsKey, _): Text("ForEach: \(itemsKey)").foregroundColor(.gray)
        case .condition(_, _, let then, _): DynamicView(node: then, onAction: onAction)

        // ── Media ──
        case .videoPlayer(_, let src, _, _, _, _, let style):
            ZStack { Color.black; Image(systemName: "play.fill").font(.largeTitle).foregroundColor(.white); Text(src.components(separatedBy: "/").last ?? "Video").foregroundColor(.white).font(.caption).offset(y: 30) }.featherStyle(style)
        case .audioPlayer(_, let src, _, _, _, _):
            HStack { Image(systemName: "play.circle.fill").font(.title).foregroundColor(.blue); Text(src.components(separatedBy: "/").last ?? "Audio").lineLimit(1); SwiftUI.Spacer() }.padding().background(Color(.systemGray6)).cornerRadius(8)
        case .webView(_, let url, let style):
            VStack { Text("WebView").font(.caption).foregroundColor(.gray); Text(url).font(.caption2).foregroundColor(.blue) }.padding().featherStyle(style)

        // ── Gestures ──
        case .gestureDetector(_, let onTap, let onPan, let child):
            DynamicView(node: child, onAction: onAction)
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { val in
                            if let action = onPan {
                                let payload = "{\"action\": \"\(action)\", \"payload\": {\"x\": \(val.translation.width), \"y\": \(val.translation.height)}}"
                                onAction(payload, nil)
                            }
                        }
                )
                .simultaneousGesture(
                    TapGesture()
                        .onEnded { _ in
                            if let action = onTap { onAction(action, nil) }
                        }
                )
        case .clickable(_, let action, let child, _):
            Button(action: { onAction(action, nil) }) { DynamicView(node: child, onAction: onAction) }
        case .longPressable(_, let action, let child, _):
            DynamicView(node: child, onAction: onAction).onLongPressGesture { onAction(action, nil) }
        case .pullToRefresh(_, _, let child, _):
            DynamicView(node: child, onAction: onAction) // Real impl needs .refreshable

        // ── Fallback ──
        case .custom(_, let type, _): Text("Custom: \(type)").foregroundColor(.orange)
        case .unsupported(_, let type): Text("Unsupported: \(type)").foregroundColor(.red)
        }
    }

    // MARK: Helpers
    private func hAlign(_ a: String?) -> HorizontalAlignment { a == "center" ? .center : a == "end" ? .trailing : .leading }
    private func vAlign(_ a: String?) -> VerticalAlignment { a == "center" ? .center : (a == "end" || a == "bottom") ? .bottom : .top }
    private func zAlign(_ a: String?) -> SwiftUI.Alignment { a == "center" ? .center : a == "bottomEnd" ? .bottomTrailing : .topLeading }
}

private func mapIconName(_ name: String) -> String {
    switch name {
    case "home": return "house"; case "menu": return "line.3.horizontal"; case "add", "plus": return "plus"
    case "search": return "magnifyingglass"; case "settings": return "gearshape"; case "star": return "star.fill"
    case "close": return "xmark"; case "back", "arrow_back": return "chevron.left"; case "forward", "arrow_forward": return "chevron.right"
    case "delete": return "trash"; case "edit": return "pencil"; case "share": return "square.and.arrow.up"
    case "favorite": return "heart.fill"; case "person": return "person.fill"; case "mail": return "envelope"
    case "phone": return "phone"; case "camera": return "camera"; case "photo": return "photo"
    case "play": return "play.fill"; case "pause": return "pause.fill"; case "stop": return "stop.fill"
    case "check": return "checkmark"; case "info": return "info.circle"; case "warning": return "exclamationmark.triangle"
    case "error": return "xmark.circle"; case "refresh": return "arrow.clockwise"; case "download": return "arrow.down.circle"
    case "upload": return "arrow.up.circle"; case "lock": return "lock"; case "unlock": return "lock.open"
    default: return name
    }
}

// MARK: - Navigation Helper

struct NavigatorView: View {
    let initialRoute: String; let screens: [String: FeatherNode]; let onAction: (String, String?) -> Void
    @State private var currentRoute = ""
    var body: some View {
        let route = currentRoute.isEmpty ? initialRoute : currentRoute
        let screen = screens[route] ?? screens.values.first ?? .unsupported(id: "err", type: "No screen")
        DynamicView(node: screen) { action, payload in
            if action == "__navigate", let next = payload { currentRoute = next }
            else if action == "__goBack" { currentRoute = initialRoute }
            else { onAction(action, payload) }
        }.onAppear { if currentRoute.isEmpty { currentRoute = initialRoute } }
    }
}
