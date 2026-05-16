import SwiftUI

/// iOS-side implementation of the feather-camera module.
/// Register this with ModuleRegistry in the Host app:
///
/// ```swift
/// ModuleRegistry.shared.register(CameraModuleIOS())
/// ```
struct CameraModuleIOS: FeatherModule {
    let name = "CameraPreview"

    func render(dict: [String: Any], onAction: @escaping (String, String?) -> Void) -> AnyView {
        AnyView(CameraPreviewView(onAction: onAction))
    }
}

private struct CameraPreviewView: View {
    let onAction: (String, String?) -> Void
    @State private var showingCamera = false

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Rectangle()
                    .fill(Color(.darkGray))
                    .frame(height: 300)
                    .cornerRadius(12)

                if showingCamera {
                    Text("Camera Preview Active")
                        .foregroundColor(.white)
                        .font(.headline)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.white.opacity(0.6))
                        Button("Open Camera") {
                            showingCamera = true
                            onAction("cameraOpened", nil)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
    }
}
