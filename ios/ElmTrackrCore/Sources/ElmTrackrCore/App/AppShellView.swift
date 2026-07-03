import SwiftUI

struct AppShellView: View {
    let environment: AppEnvironment

    var body: some View {
        TabView {
            ForEach(AppRoute.allCases) { route in
                NavigationStack {
                    route.view
                }
                .tabItem {
                    Label(route.title, systemImage: route.systemImage)
                }
            }
        }
    }
}
