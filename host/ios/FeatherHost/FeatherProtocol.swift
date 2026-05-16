import Foundation

// MARK: - Unified FeatherNode (single source of truth — 50+ widgets)

enum FeatherNode: Identifiable {
    // Basic
    case text(id: String, text: String, style: FeatherStyle? = nil)
    case button(id: String, text: String, action: String, enabled: Bool = true, style: FeatherStyle? = nil)
    case textInput(id: String, value: String, placeholder: String, action: String, label: String? = nil, style: FeatherStyle? = nil)
    case image(id: String, src: String, alt: String, style: FeatherStyle? = nil)
    case icon(id: String, name: String, size: Int = 24, color: String? = nil, action: String? = nil)
    case iconButton(id: String, icon: String, action: String, size: Int = 24, color: String? = nil, background: String? = nil)
    case spacer(id: String, flex: Int = 1)
    case divider(id: String, color: String? = nil, thickness: Int = 1, indent: Int = 0)

    // Layouts
    case column(id: String, children: [FeatherNode], style: FeatherStyle? = nil)
    case row(id: String, children: [FeatherNode], style: FeatherStyle? = nil)
    case box(id: String, children: [FeatherNode], style: FeatherStyle? = nil)
    case lazyColumn(id: String, items: [FeatherNode], style: FeatherStyle? = nil)
    case lazyRow(id: String, items: [FeatherNode], style: FeatherStyle? = nil)
    case lazyGrid(id: String, items: [FeatherNode], columns: Int = 2, style: FeatherStyle? = nil)
    case scrollView(id: String, children: [FeatherNode], direction: String = "vertical", style: FeatherStyle? = nil)
    case card(id: String, children: [FeatherNode], action: String? = nil, style: FeatherStyle? = nil)
    case surface(id: String, children: [FeatherNode], style: FeatherStyle? = nil)
    case aspectRatio(id: String, ratio: Float, child: FeatherNode)

    // Interactive
    case switchNode(id: String, checked: Bool, action: String, enabled: Bool = true, label: String? = nil, trackColor: String? = nil)
    case checkbox(id: String, checked: Bool, action: String, enabled: Bool = true, label: String? = nil, color: String? = nil)
    case radioGroup(id: String, selected: String, action: String, options: [RadioOptionData])
    case slider(id: String, value: Float, min: Float, max: Float, step: Float?, action: String, label: String? = nil)
    case searchBar(id: String, value: String, placeholder: String, action: String, style: FeatherStyle? = nil)
    case dropdownMenu(id: String, selected: String, options: [String], action: String, label: String? = nil, style: FeatherStyle? = nil)

    // Progress
    case linearProgress(id: String, progress: Float?, color: String?, trackColor: String?)
    case circularProgress(id: String, progress: Float?, color: String?, size: Int = 40)

    // Feedback
    case dialog(id: String, visible: Bool, title: String?, content: FeatherNode, confirmText: String, dismissText: String?, confirmAction: String, dismissAction: String?)
    case bottomSheet(id: String, visible: Bool, content: FeatherNode, dismissAction: String?, style: FeatherStyle? = nil)
    case snackbar(id: String, message: String, visible: Bool, actionText: String?, action: String?)
    case badge(id: String, count: Int, color: String?, child: FeatherNode?)
    case chip(id: String, label: String, selected: Bool?, action: String?, icon: String?, color: String?)
    case tooltip(id: String, message: String, child: FeatherNode)
    case floatingActionButton(id: String, icon: String, action: String, background: String?, iconColor: String?)
    case placeholder(id: String, width: String?, height: String?, cornerRadius: Int?)

    // Scaffold
    case scaffold(id: String, topBar: FeatherNode?, bottomBar: FeatherNode?, fab: FeatherNode?, drawer: FeatherNode?, body: FeatherNode, style: FeatherStyle? = nil)
    case topAppBar(id: String, title: String, subtitle: String?, navigationIcon: String?, navigationAction: String?, background: String?, contentColor: String?)
    case bottomAppBar(id: String, children: [FeatherNode], background: String?, contentColor: String?)
    case tabRow(id: String, tabs: [TabItemData], selected: Int, action: String, background: String?, contentColor: String?)
    case pager(id: String, pages: [FeatherNode], currentPage: Int?, action: String?, style: FeatherStyle? = nil)
    case drawer(id: String, header: FeatherNode?, items: [DrawerItemData], style: FeatherStyle? = nil)

    // Navigation
    case navigator(id: String, initialRoute: String, screens: [String: FeatherNode])
    case bottomTabs(id: String, tabs: [FeatherTab], style: FeatherStyle? = nil)
    case tab(id: String, title: String, route: String, icon: String?, content: FeatherNode)

    // Logic
    case forEach(id: String, itemsKey: String, template: [String: Any])
    case condition(id: String, key: String, then: FeatherNode, otherwise: FeatherNode? = nil)

    // Media
    case videoPlayer(id: String, src: String, autoplay: Bool = false, controls: Bool = true, loop: Bool = false, poster: String? = nil, style: FeatherStyle? = nil)
    case audioPlayer(id: String, src: String, autoplay: Bool = false, controls: Bool = true, loop: Bool = false, style: FeatherStyle? = nil)
    case webView(id: String, url: String, style: FeatherStyle? = nil)

    // Gestures
    case clickable(id: String, action: String, child: FeatherNode, ripple: Bool = true)
    case longPressable(id: String, action: String, child: FeatherNode, duration: Int = 500)
    case pullToRefresh(id: String, action: String, child: FeatherNode, refreshing: Bool)

    // Extensibility
    case custom(id: String, type: String, dict: [String: Any])
    case unsupported(id: String, type: String)

    var id: String {
        switch self {
        case .text(let id, _, _), .button(let id, _, _, _, _), .textInput(let id, _, _, _, _, _),
             .image(let id, _, _, _), .icon(let id, _, _, _, _), .iconButton(let id, _, _, _, _, _),
             .spacer(let id, _), .divider(let id, _, _, _),
             .column(let id, _, _), .row(let id, _, _), .box(let id, _, _),
             .lazyColumn(let id, _, _), .lazyRow(let id, _, _), .lazyGrid(let id, _, _, _),
             .scrollView(let id, _, _, _), .card(let id, _, _, _), .surface(let id, _, _),
             .aspectRatio(let id, _, _),
             .switchNode(let id, _, _, _, _, _), .checkbox(let id, _, _, _, _, _),
             .radioGroup(let id, _, _, _), .slider(let id, _, _, _, _, _, _),
             .searchBar(let id, _, _, _, _), .dropdownMenu(let id, _, _, _, _, _),
             .linearProgress(let id, _, _, _), .circularProgress(let id, _, _, _),
             .dialog(let id, _, _, _, _, _, _, _), .bottomSheet(let id, _, _, _, _),
             .snackbar(let id, _, _, _, _), .badge(let id, _, _, _), .chip(let id, _, _, _, _, _),
             .tooltip(let id, _, _), .floatingActionButton(let id, _, _, _, _),
             .placeholder(let id, _, _, _),
             .scaffold(let id, _, _, _, _, _, _), .topAppBar(let id, _, _, _, _, _, _),
             .bottomAppBar(let id, _, _, _), .tabRow(let id, _, _, _, _, _),
             .pager(let id, _, _, _, _), .drawer(let id, _, _, _),
             .navigator(let id, _, _), .bottomTabs(let id, _, _), .tab(let id, _, _, _, _),
             .forEach(let id, _, _), .condition(let id, _, _, _),
             .videoPlayer(let id, _, _, _, _, _, _), .audioPlayer(let id, _, _, _, _, _),
             .webView(let id, _, _),
             .clickable(let id, _, _, _), .longPressable(let id, _, _, _), .pullToRefresh(let id, _, _, _),
             .custom(let id, _, _), .unsupported(let id, _):
            return id
        }
    }
}

// MARK: - Supporting Data Types

struct FeatherTab: Identifiable {
    let id = UUID().uuidString
    let title: String
    let icon: String?
    let route: String
    let content: FeatherNode
}

struct RadioOptionData {
    let value: String
    let label: String
    let color: String?
}

struct TabItemData {
    let title: String
    let icon: String?
}

struct DrawerItemData {
    let title: String
    let icon: String?
    let route: String?
    let action: String?
}

// MARK: - Bundle Manifest (Codable for HTTP fetch)

struct BundleManifest: Decodable {
    let sdkVersion: String
    let protocolVersion: Int
    let render: BundleArtifact
    let assets: [BundleArtifact]?
    let modules: [BundleModuleEntry]?
    let delta: BundleDelta?

    enum CodingKeys: String, CodingKey {
        case sdkVersion = "sdk_version"
        case protocolVersion = "protocol_version"
        case render
        case assets
        case modules
        case delta
    }
}

struct BundleArtifact: Decodable {
    let path: String
    let sha256: String
    let bytes: Int
}

struct BundleModuleEntry: Decodable {
    let name: String
    let version: String
}

struct BundleDelta: Decodable {
    let changed: [String]
    let removed: [String]
}

// MARK: - Render Payload Parser (50+ widgets)

enum FeatherRenderPayload {
    static func decode(_ data: Data) throws -> (FeatherNode, [String]) {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let root = json["root"] as? [String: Any] else {
            return (.text(id: UUID().uuidString, text: "Invalid Feather payload"), [])
        }
        let type = json["type"] as? String ?? "render"
        var errors: [String] = []
        if type == "render_error" {
            if let diagnostics = json["diagnostics"] as? [[String: Any]] {
                errors = diagnostics.compactMap { $0["message"] as? String }
            }
        }
        return (parseNode(root), errors)
    }

    static func parseNode(_ dict: [String: Any]) -> FeatherNode {
        let type = dict["type"] as? String ?? "Unknown"
        let id = UUID().uuidString
        let style = FeatherStyle.parse(from: dict["style"] as? [String: Any]) ?? FeatherStyle.parse(from: dict)

        switch type {
        // ── Basic ──
        case "Text":
            return .text(id: id, text: dict["text"] as? String ?? "", style: style)
        case "Button":
            return .button(id: id, text: dict["text"] as? String ?? "Button", action: dict["action"] as? String ?? "buttonPressed", enabled: dict["enabled"] as? Bool ?? true, style: style)
        case "TextInput":
            return .textInput(id: id, value: dict["value"] as? String ?? "", placeholder: dict["placeholder"] as? String ?? "", action: dict["action"] as? String ?? "textChanged", label: dict["label"] as? String, style: style)
        case "Image":
            return .image(id: id, src: dict["src"] as? String ?? "", alt: dict["alt"] as? String ?? "", style: style)
        case "Icon":
            return .icon(id: id, name: dict["name"] as? String ?? "star", size: dict["size"] as? Int ?? 24, color: dict["color"] as? String, action: dict["action"] as? String)
        case "IconButton":
            return .iconButton(id: id, icon: dict["icon"] as? String ?? "menu", action: dict["action"] as? String ?? "", size: dict["size"] as? Int ?? 24, color: dict["color"] as? String, background: dict["background"] as? String)
        case "Spacer":
            return .spacer(id: id, flex: dict["flex"] as? Int ?? 1)
        case "Divider":
            return .divider(id: id, color: dict["color"] as? String, thickness: dict["thickness"] as? Int ?? 1, indent: dict["indent"] as? Int ?? 0)

        // ── Layouts ──
        case "Column":
            return .column(id: id, children: parseChildren(dict), style: style)
        case "Row":
            return .row(id: id, children: parseChildren(dict), style: style)
        case "Box":
            return .box(id: id, children: parseChildren(dict), style: style)
        case "LazyColumn":
            return .lazyColumn(id: id, items: parseItems(dict), style: style)
        case "LazyRow":
            return .lazyRow(id: id, items: parseItems(dict), style: style)
        case "LazyGrid":
            return .lazyGrid(id: id, items: parseItems(dict), columns: dict["columns"] as? Int ?? 2, style: style)
        case "ScrollView":
            return .scrollView(id: id, children: parseChildren(dict), direction: dict["direction"] as? String ?? "vertical", style: style)
        case "Card":
            return .card(id: id, children: parseChildren(dict), action: dict["action"] as? String, style: style)
        case "Surface":
            return .surface(id: id, children: parseChildren(dict), style: style)
        case "AspectRatio":
            let child = (dict["child"] as? [String: Any]).map(parseNode) ?? .spacer(id: UUID().uuidString)
            return .aspectRatio(id: id, ratio: (dict["ratio"] as? NSNumber)?.floatValue ?? 1.0, child: child)

        // ── Interactive ──
        case "Switch":
            return .switchNode(id: id, checked: dict["checked"] as? Bool ?? false, action: dict["action"] as? String ?? "", enabled: dict["enabled"] as? Bool ?? true, label: dict["label"] as? String, trackColor: dict["trackColor"] as? String)
        case "Checkbox":
            return .checkbox(id: id, checked: dict["checked"] as? Bool ?? false, action: dict["action"] as? String ?? "", enabled: dict["enabled"] as? Bool ?? true, label: dict["label"] as? String, color: dict["color"] as? String)
        case "RadioGroup":
            return .radioGroup(id: id, selected: dict["selected"] as? String ?? "", action: dict["action"] as? String ?? "", options: [])
        case "Slider":
            return .slider(id: id, value: (dict["value"] as? NSNumber)?.floatValue ?? 0, min: (dict["min"] as? NSNumber)?.floatValue ?? 0, max: (dict["max"] as? NSNumber)?.floatValue ?? 1, step: (dict["step"] as? NSNumber)?.floatValue, action: dict["action"] as? String ?? "", label: dict["label"] as? String)
        case "SearchBar":
            return .searchBar(id: id, value: dict["value"] as? String ?? "", placeholder: dict["placeholder"] as? String ?? "Search...", action: dict["action"] as? String ?? "", style: style)
        case "DropdownMenu":
            return .dropdownMenu(id: id, selected: dict["selected"] as? String ?? "", options: dict["options"] as? [String] ?? [], action: dict["action"] as? String ?? "", label: dict["label"] as? String, style: style)

        // ── Progress ──
        case "LinearProgress":
            return .linearProgress(id: id, progress: (dict["progress"] as? NSNumber)?.floatValue, color: dict["color"] as? String, trackColor: dict["trackColor"] as? String)
        case "CircularProgress":
            return .circularProgress(id: id, progress: (dict["progress"] as? NSNumber)?.floatValue, color: dict["color"] as? String, size: dict["size"] as? Int ?? 40)

        // ── Feedback ──
        case "Dialog":
            let content = (dict["content"] as? [String: Any]).map(parseNode) ?? .text(id: UUID().uuidString, text: "")
            return .dialog(id: id, visible: dict["visible"] as? Bool ?? false, title: dict["title"] as? String, content: content, confirmText: dict["confirmText"] as? String ?? "OK", dismissText: dict["dismissText"] as? String, confirmAction: dict["confirmAction"] as? String ?? "", dismissAction: dict["dismissAction"] as? String)
        case "BottomSheet":
            let content = (dict["content"] as? [String: Any]).map(parseNode) ?? .text(id: UUID().uuidString, text: "")
            return .bottomSheet(id: id, visible: dict["visible"] as? Bool ?? false, content: content, dismissAction: dict["dismissAction"] as? String, style: style)
        case "Snackbar":
            return .snackbar(id: id, message: dict["message"] as? String ?? "", visible: dict["visible"] as? Bool ?? false, actionText: dict["actionText"] as? String, action: dict["action"] as? String)
        case "Badge":
            let child = (dict["child"] as? [String: Any]).map(parseNode)
            return .badge(id: id, count: dict["count"] as? Int ?? 0, color: dict["color"] as? String, child: child)
        case "Chip":
            return .chip(id: id, label: dict["label"] as? String ?? "", selected: dict["selected"] as? Bool, action: dict["action"] as? String, icon: dict["icon"] as? String, color: dict["color"] as? String)
        case "Tooltip":
            let child = (dict["child"] as? [String: Any]).map(parseNode) ?? .spacer(id: UUID().uuidString)
            return .tooltip(id: id, message: dict["message"] as? String ?? "", child: child)
        case "FloatingActionButton":
            return .floatingActionButton(id: id, icon: dict["icon"] as? String ?? "plus", action: dict["action"] as? String ?? "", background: dict["background"] as? String, iconColor: dict["iconColor"] as? String)
        case "Placeholder":
            return .placeholder(id: id, width: dict["width"] as? String, height: dict["height"] as? String, cornerRadius: dict["cornerRadius"] as? Int)

        // ── Scaffold ──
        case "Scaffold":
            let body = (dict["body"] as? [String: Any]).map(parseNode) ?? .text(id: UUID().uuidString, text: "Empty")
            return .scaffold(id: id, topBar: (dict["topBar"] as? [String: Any]).map(parseNode), bottomBar: (dict["bottomBar"] as? [String: Any]).map(parseNode), fab: (dict["floatingActionButton"] as? [String: Any]).map(parseNode), drawer: (dict["drawer"] as? [String: Any]).map(parseNode), body: body, style: style)
        case "TopAppBar":
            return .topAppBar(id: id, title: dict["title"] as? String ?? "", subtitle: dict["subtitle"] as? String, navigationIcon: dict["navigationIcon"] as? String, navigationAction: dict["navigationAction"] as? String, background: dict["background"] as? String, contentColor: dict["contentColor"] as? String)
        case "BottomAppBar":
            return .bottomAppBar(id: id, children: parseChildren(dict), background: dict["background"] as? String, contentColor: dict["contentColor"] as? String)
        case "TabRow":
            return .tabRow(id: id, tabs: [], selected: dict["selected"] as? Int ?? 0, action: dict["action"] as? String ?? "", background: dict["background"] as? String, contentColor: dict["contentColor"] as? String)
        case "Pager":
            let pages = (dict["pages"] as? [[String: Any]] ?? []).map(parseNode)
            return .pager(id: id, pages: pages, currentPage: dict["currentPage"] as? Int, action: dict["action"] as? String, style: style)
        case "Drawer":
            return .drawer(id: id, header: (dict["header"] as? [String: Any]).map(parseNode), items: [], style: style)

        // ── Navigation ──
        case "Navigator":
            let initial = dict["initialRoute"] as? String ?? "home"
            let screensDict = dict["screens"] as? [String: [String: Any]] ?? [:]
            var screens: [String: FeatherNode] = [:]
            for (key, value) in screensDict { screens[key] = parseNode(value) }
            return .navigator(id: id, initialRoute: initial, screens: screens)
        case "BottomTabs":
            let tabsDicts = dict["tabs"] as? [[String: Any]] ?? []
            let tabs = tabsDicts.map { d -> FeatherTab in
                FeatherTab(title: d["title"] as? String ?? "Tab", icon: d["icon"] as? String, route: d["route"] as? String ?? "home", content: parseNode(d["content"] as? [String: Any] ?? [:]))
            }
            return .bottomTabs(id: id, tabs: tabs, style: style)

        // ── Logic ──
        case "ForEach":
            return .forEach(id: id, itemsKey: dict["itemsKey"] as? String ?? "", template: dict["template"] as? [String: Any] ?? [:])
        case "Condition":
            let then = parseNode(dict["then"] as? [String: Any] ?? [:])
            let otherwise = (dict["otherwise"] as? [String: Any]).map(parseNode)
            return .condition(id: id, key: dict["key"] as? String ?? "", then: then, otherwise: otherwise)

        // ── Media ──
        case "VideoPlayer":
            return .videoPlayer(id: id, src: dict["src"] as? String ?? "", autoplay: dict["autoplay"] as? Bool ?? false, controls: dict["controls"] as? Bool ?? true, loop: dict["loop"] as? Bool ?? false, poster: dict["poster"] as? String, style: style)
        case "AudioPlayer":
            return .audioPlayer(id: id, src: dict["src"] as? String ?? "", autoplay: dict["autoplay"] as? Bool ?? false, controls: dict["controls"] as? Bool ?? true, loop: dict["loop"] as? Bool ?? false, style: style)
        case "WebView":
            return .webView(id: id, url: dict["url"] as? String ?? "", style: style)

        // ── Gestures ──
        case "Clickable":
            let child = (dict["child"] as? [String: Any]).map(parseNode) ?? .spacer(id: UUID().uuidString)
            return .clickable(id: id, action: dict["action"] as? String ?? "", child: child, ripple: dict["ripple"] as? Bool ?? true)
        case "LongPressable":
            let child = (dict["child"] as? [String: Any]).map(parseNode) ?? .spacer(id: UUID().uuidString)
            return .longPressable(id: id, action: dict["action"] as? String ?? "", child: child, duration: dict["duration"] as? Int ?? 500)
        case "PullToRefresh":
            let child = (dict["child"] as? [String: Any]).map(parseNode) ?? .spacer(id: UUID().uuidString)
            return .pullToRefresh(id: id, action: dict["action"] as? String ?? "", child: child, refreshing: dict["refreshing"] as? Bool ?? false)

        default:
            return .unsupported(id: id, type: type)
        }
    }

    // MARK: Helpers
    private static func parseChildren(_ dict: [String: Any]) -> [FeatherNode] {
        (dict["children"] as? [[String: Any]] ?? []).map(parseNode)
    }
    private static func parseItems(_ dict: [String: Any]) -> [FeatherNode] {
        (dict["items"] as? [[String: Any]] ?? []).map(parseNode)
    }
}
