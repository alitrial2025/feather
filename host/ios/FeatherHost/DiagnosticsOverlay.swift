import SwiftUI

struct DiagnosticsOverlay: View {
    let errors: [String]
    
    var body: some View {
        if errors.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Feather Diagnostics")
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding(.bottom, 4)
                
                ForEach(errors, id: \.self) { error in
                    Text(error)
                        .font(.system(size: 14, weight: .medium, design: .monospaced))
                        .foregroundColor(.white)
                }
                Spacer()
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(Color.red.opacity(0.95))
            .transition(.opacity)
        }
    }
}
