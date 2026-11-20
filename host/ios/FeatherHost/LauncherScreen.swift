import SwiftUI

struct LauncherScreen: View {
    @State private var ip: String = UserDefaults.standard.string(forKey: "last_ip") ?? "127.0.0.1"
    @State private var showScanner = false
    var onConnect: (String) -> Void

    var body: some View {
        ZStack {
            Color(red: 18/255, green: 18/255, blue: 18/255)
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Image(systemName: "gear")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 80, height: 80)
                    .foregroundColor(Color(red: 229/255, green: 9/255, blue: 20/255))

                VStack(spacing: 8) {
                    Text("Welcome to Feather")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white)

                    Text("Enter your computer's local IP address or scan QR code.")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }

                TextField("CLI IP or URL", text: $ip)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .foregroundColor(.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color(red: 229/255, green: 9/255, blue: 20/255), lineWidth: 1)
                    )
                    .padding(.horizontal, 32)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                Button(action: {
                    onConnect(ip)
                }) {
                    Text("Connect to CLI")
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 229/255, green: 9/255, blue: 20/255))
                        .cornerRadius(8)
                }
                .padding(.horizontal, 32)

                Button(action: {
                    showScanner = true
                }) {
                    Text("Scan QR Code")
                        .foregroundColor(.gray)
                }
            }

            if showScanner {
                QRScannerView(onScan: { scanned in
                    showScanner = false
                    onConnect(scanned)
                }, onClose: {
                    showScanner = false
                })
                .ignoresSafeArea()
            }
        }
    }
}
