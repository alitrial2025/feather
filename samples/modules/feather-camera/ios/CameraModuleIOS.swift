import SwiftUI

/// iOS-side implementation of the feather-camera module.
/// Register this with ModuleRegistry in the Host app:
///
/// ```swift
/// ModuleRegistry.shared.register(CameraModuleIOS())
/// ```
import SwiftUI
import AVFoundation

class CameraModuleIOS: NSObject, FeatherModule, AVCapturePhotoCaptureDelegate {
    let name = "feather-camera"
    let version = "1.0.0"

    private let photoOutput = AVCapturePhotoOutput()
    private var context: FeatherModuleContext?

    func getWidgets() -> [String] {
        return ["CameraPreview"]
    }

    func call(method: String, payload: [String: Any], context: FeatherModuleContext) -> Bool {
        if method == "takePhoto" {
            self.context = context
            let settings = AVCapturePhotoSettings()
            photoOutput.capturePhoto(with: settings, delegate: self)
            return true
        }
        return false
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation() else { return }
        let base64 = data.base64EncodedString()
        context?.sendStateAssignment("lastPhoto", base64)
    }

    func canRender(_ node: FeatherNode) -> Bool {
        if case .custom(_, let type, _) = node {
            return type == "CameraPreview"
        }
        return false
    }

    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView {
        AnyView(CameraPreviewView(photoOutput: photoOutput, onAction: onAction))
    }
}

private struct CameraPreviewView: View {
    let photoOutput: AVCapturePhotoOutput
    let onAction: (String, String?) -> Void
    @State private var session: AVCaptureSession?
    @State private var permissionGranted = false

    var body: some View {
        VStack {
            if permissionGranted {
                CameraPreviewRepresentable(session: session)
                    .frame(height: 400)
                    .cornerRadius(12)
                    .onAppear {
                        startSession()
                    }
                    .onDisappear {
                        session?.stopRunning()
                    }
            } else {
                Button("Enable Camera") {
                    requestPermission()
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    private func requestPermission() {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                self.permissionGranted = granted
                if granted { startSession() }
            }
        }
    }

    private func startSession() {
        guard session == nil else {
            if !session!.isRunning { session?.startRunning() }
            return
        }
        
        let newSession = AVCaptureSession()
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else { return }
        
        if newSession.canAddInput(input) && newSession.canAddOutput(photoOutput) {
            newSession.addInput(input)
            newSession.addOutput(photoOutput)
            newSession.startRunning()
            self.session = newSession
        }
    }
}

private struct CameraPreviewRepresentable: UIViewRepresentable {
    let session: AVCaptureSession?

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        if let session = session {
            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            view.layer.addSublayer(layer)
            context.coordinator.layer = layer
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.layer?.frame = uiView.bounds
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator {
        var layer: AVCaptureVideoPreviewLayer?
    }
}
