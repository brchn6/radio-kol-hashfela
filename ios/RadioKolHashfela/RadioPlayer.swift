import AVFoundation
import MediaPlayer
import SwiftUI

@MainActor
final class RadioPlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    @Published private(set) var status = "Loading…"

    private let streamURL = URL(string: "https://radio.streamgates.net/stream/1036kh")!
    private var player: AVPlayer?

    init() {
        configureAudioSession()
        configureRemoteCommands()
        updateNowPlayingInfo()
    }

    func toggle() {
        isPlaying ? stop() : play()
    }

    func play() {
        if player == nil {
            player = AVPlayer(url: streamURL)
        }

        status = "Now Playing…"
        isPlaying = true
        player?.play()
        updateNowPlayingInfo()
    }

    func stop() {
        player?.pause()
        player = nil
        status = "Stopped"
        isPlaying = false
        updateNowPlayingInfo()
    }

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            status = "Audio setup failed"
        }
    }

    private func configureRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.play() }
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.stop() }
            return .success
        }

        commandCenter.stopCommand.isEnabled = true
        commandCenter.stopCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.stop() }
            return .success
        }
    }

    private func updateNowPlayingInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: "Kol Hashfela 103.6FM",
            MPMediaItemPropertyArtist: "Radio Kol Hashfela",
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
    }
}
