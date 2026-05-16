import SwiftUI
import MapKit

struct MapsModuleIOS: FeatherModule {
    let name = "feather-maps"
    let version = "1.0.0"

    func getWidgets() -> [String] {
        return ["MapView"]
    }

    func canRender(_ node: FeatherNode) -> Bool {
        if case .custom(_, let type, _) = node {
            return type == "MapView"
        }
        return false
    }

    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView {
        AnyView(FeatherMapView())
    }
}

private struct FeatherMapView: View {
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 37.334_900, longitude: -122.009_020),
        span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.2)
    )

    var body: some View {
        Map(coordinateRegion: $region)
            .frame(height: 300)
            .cornerRadius(12)
    }
}
