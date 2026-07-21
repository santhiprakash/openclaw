import OpenClawKit
import SwiftUI

/// Control-hub Terminal destination: embeds the gateway-served terminal page
/// (`/?view=terminal`, the ghostty-web surface shared with the Control UI) in an
/// authenticated WKWebView.
struct TerminalHubScreen: View {
    @Environment(NodeAppModel.self) private var appModel
    let headerSidebarAction: OpenClawSidebarHeaderAction?
    let usesNativeNavigationChrome: Bool
    let gatewayAction: (() -> Void)?

    init(
        headerSidebarAction: OpenClawSidebarHeaderAction? = nil,
        usesNativeNavigationChrome: Bool = false,
        gatewayAction: (() -> Void)? = nil)
    {
        self.headerSidebarAction = headerSidebarAction
        self.usesNativeNavigationChrome = usesNativeNavigationChrome
        self.gatewayAction = gatewayAction
    }

    var body: some View {
        let config = self.appModel.activeGatewayConnectConfig
        let storedOperatorToken = AuthenticatedControlUI.storedOperatorToken(config: config)
        ZStack {
            OpenClawProBackground()
            if let url = Self.terminalURL(config: config) {
                AuthenticatedControlUIWebView(
                    url: url,
                    authScript: Self.terminalAuthUserScript(
                        config: config,
                        storedOperatorToken: storedOperatorToken))
                    // Recreate the web view only when the connection inputs
                    // change; SwiftUI update passes must not restart live shells.
                        .id(Self.webContentIdentity(
                            config: config,
                            storedOperatorToken: storedOperatorToken))
                        .ignoresSafeArea(.container, edges: .bottom)
            } else {
                self.unavailableCard
            }
        }
        .navigationTitle("Terminal")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(
            self.usesNativeNavigationChrome || self.headerSidebarAction != nil ? .visible : .hidden,
            for: .navigationBar)
        .toolbar {
            if self.usesNativeNavigationChrome, let gatewayAction {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: gatewayAction) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(OpenClawType.subheadSemiBold)
                    }
                    .accessibilityLabel("Gateway settings")
                }
            }
            if let headerSidebarAction {
                OpenClawSidebarToolbarItem(
                    action: headerSidebarAction,
                    placement: .topBarLeading)
            }
        }
    }

    private var unavailableCard: some View {
        VStack(spacing: 12) {
            ProIconBadge(systemName: "terminal", color: OpenClawBrand.accent)
            Text("Terminal needs a connected gateway")
                .font(OpenClawType.subheadSemiBold)
            Text("Connect to your gateway to open a shell in the agent workspace.")
                .font(OpenClawType.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if let gatewayAction {
                Button(action: gatewayAction) {
                    Text("Open Gateway Settings")
                        .font(OpenClawType.subheadSemiBold)
                }
                .buttonStyle(.borderedProminent)
                .tint(OpenClawBrand.accent)
            }
        }
        .padding(24)
    }

    /// Credentials never enter the URL; the shared host injects them at document start.
    static func terminalURL(config: GatewayConnectConfig?) -> URL? {
        AuthenticatedControlUI.pageURL(
            config: config,
            queryItems: [URLQueryItem(name: "view", value: "terminal")])
    }

    static func terminalAuthUserScript(config: GatewayConnectConfig?) -> String? {
        self.terminalAuthUserScript(
            config: config,
            storedOperatorToken: AuthenticatedControlUI.storedOperatorToken(config: config))
    }

    static func terminalAuthUserScript(
        config: GatewayConnectConfig?,
        storedOperatorToken: String?) -> String?
    {
        AuthenticatedControlUI.authUserScript(
            config: config,
            pageURL: self.terminalURL(config: config),
            storedOperatorToken: storedOperatorToken)
    }

    static func webContentIdentity(config: GatewayConnectConfig?, storedOperatorToken: String?) -> Int {
        AuthenticatedControlUI.webContentIdentity(
            config: config,
            storedOperatorToken: storedOperatorToken)
    }
}
