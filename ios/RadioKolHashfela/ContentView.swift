import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var player = RadioPlayer()
    @State private var backgroundURL = HashfelaBackgrounds.randomURL()

    var body: some View {
        ZStack {
            AsyncImage(url: backgroundURL) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                default:
                    Color.black
                }
            }
            .ignoresSafeArea()

            Color.black.opacity(0.38)
                .ignoresSafeArea()

            VStack(spacing: 8) {
                Text("Radio Kol Hashfela")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(.white)
                    .shadow(radius: 8)
                    .multilineTextAlignment(.center)

                Text(player.status)
                    .font(.system(size: 16))
                    .foregroundStyle(.white.opacity(0.9))
                    .shadow(radius: 6)
            }
            .padding(.horizontal, 24)
            .frame(maxHeight: .infinity, alignment: .top)
            .padding(.top, 72)

            Button(action: player.toggle) {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundStyle(.black)
                    .frame(width: 76, height: 76)
                    .background(.white.opacity(0.9))
                    .clipShape(Circle())
                    .shadow(radius: 10)
            }
            .accessibilityLabel(player.isPlaying ? "Stop" : "Play")

            VStack {
                Spacer()
                HStack {
                    Button(action: openWhatsApp) {
                        Label("WhatsApp", systemImage: "message.fill")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color(red: 0.145, green: 0.827, blue: 0.4))
                            .clipShape(Capsule())
                    }
                    .accessibilityLabel("Send WhatsApp message to the station")

                    Spacer()
                }
                .padding(.leading, 20)
                .padding(.bottom, 40)
            }
        }
        .onAppear {
            player.play()
        }
    }

    private func openWhatsApp() {
        var components = URLComponents(string: "https://wa.me/972585851036")!
        components.queryItems = [
            URLQueryItem(name: "text", value: "שיר מעולה, אתם הכי טובים!")
        ]

        guard let url = components.url else { return }
        UIApplication.shared.open(url)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
