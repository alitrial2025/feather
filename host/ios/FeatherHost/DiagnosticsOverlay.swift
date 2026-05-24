import SwiftUI

struct DiagnosticsOverlay: View {
    let errors: [String]
    var onRetry: (() -> Void)? = nil
    
    var body: some View {
        if errors.isEmpty {
            EmptyView()
        } else {
            ZStack {
                // Premium vertical dark crimson gradient
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.12, green: 0.02, blue: 0.02),
                        Color(red: 0.05, green: 0.01, blue: 0.01)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    // Header section
                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .stroke(Color(red: 0.9, green: 0.04, blue: 0.08), lineWidth: 2)
                                .frame(width: 64, height: 64)
                                .background(Circle().fill(Color(red: 0.9, green: 0.04, blue: 0.08).opacity(0.15)))
                            
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(Color(red: 0.9, green: 0.04, blue: 0.08))
                        }
                        .padding(.top, 24)
                        
                        Text("FEATHER RUNTIME EXCEPTION")
                            .font(.system(size: 18, weight: .bold, design: .default))
                            .foregroundColor(.white)
                            .tracking(1.5)
                            .multilineTextAlignment(.center)
                        
                        Text("A critical error occurred during WASM dynamic execution or bundle parsing.")
                            .font(.system(size: 13))
                            .foregroundColor(Color(red: 0.88, green: 0.69, blue: 0.69))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    
                    // Glassmorphic log panel
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(errors, id: \.self) { error in
                                HStack(alignment: .top, spacing: 6) {
                                    Text(">")
                                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                                        .foregroundColor(Color(red: 0.9, green: 0.04, blue: 0.08))
                                    
                                    Text(error)
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(Color(red: 1.0, green: 0.8, blue: 0.8))
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .background(Color.black.opacity(0.35))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.15), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                    
                    // Action button
                    if let onRetry = onRetry {
                        Button(action: onRetry) {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 14, weight: .bold))
                                Text("FORCE RELOAD BUNDLE")
                                    .font(.system(size: 14, weight: .bold))
                                    .tracking(1.0)
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color(red: 0.9, green: 0.04, blue: 0.08))
                            .cornerRadius(24)
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
                            )
                            .shadow(color: Color(red: 0.9, green: 0.04, blue: 0.08).opacity(0.4), radius: 8, x: 0, y: 4)
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
            }
            .transition(.opacity)
        }
    }
}

