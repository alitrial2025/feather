import Foundation
import SwiftUI
import AVKit

class MediaModule: FeatherModule {
    let name = "feather-media"
    let version = "1.0.0"

    func canHandleLegacyCommand(_ command: String) -> Bool {
        return false
    }

    func handleLegacyCommand(_ command: String, context: FeatherModuleContext) -> Bool {
        return false
    }

    func canRender(_ node: FeatherNode) -> Bool {
        switch node {
        case .videoPlayer, .audioPlayer:
            return true
        default:
            return false
        }
    }

    func render(node: FeatherNode, onAction: @escaping (String, String?) -> Void) -> AnyView {
        switch node {
        case .videoPlayer(_, let src, let autoplay, let controls, let loop, _, let style):
            return AnyView(FeatherVideoPlayer(src: src, autoplay: autoplay, controls: controls, loop: loop, style: style))
        case .audioPlayer(_, let src, let autoplay, let controls, let loop, let style):
            return AnyView(FeatherAudioPlayer(src: src, autoplay: autoplay, controls: controls, loop: loop, style: style))
        default:
            return AnyView(EmptyView())
        }
    }
}

struct FeatherVideoPlayer: View {
    let src: String
    let autoplay: Bool
    let controls: Bool
    let loop: Bool
    let style: FeatherStyle?
    
    @State private var player: AVPlayer?

    var body: some View {
        Group {
            if let player = player {
                VideoPlayer(player: player)
                    .onAppear {
                        if autoplay {
                            player.play()
                        }
                        if loop {
                            NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: player.currentItem, queue: .main) { _ in
                                player.seek(to: .zero)
                                player.play()
                            }
                        }
                    }
                    .onDisappear {
                        player.pause()
                    }
            } else {
                Color.black
            }
        }
        .onAppear {
            if let url = URL(string: src) {
                self.player = AVPlayer(url: url)
            }
        }
        // TODO: Apply container style if possible, or just layout
    }
}

struct FeatherAudioPlayer: View {
    let src: String
    let autoplay: Bool
    let controls: Bool
    let loop: Bool
    let style: FeatherStyle?
    
    @State private var player: AVPlayer?

    var body: some View {
        VStack {
            if let player = player {
                if controls {
                    VideoPlayer(player: player) // Still uses VideoPlayer but for audio it works
                        .frame(height: 100)
                } else {
                    Text("Audio playing...")
                        .onAppear {
                            if autoplay {
                                player.play()
                            }
                        }
                }
            }
        }
        .onAppear {
            if let url = URL(string: src) {
                self.player = AVPlayer(url: url)
            }
        }
    }
}
