import SwiftUI

enum AppRoute: String, CaseIterable, Identifiable {
    case dashboard
    case shifts
    case reports
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .dashboard:
            return "Dashboard"
        case .shifts:
            return "Shifts"
        case .reports:
            return "Reports"
        case .settings:
            return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .dashboard:
            return "gauge"
        case .shifts:
            return "clock"
        case .reports:
            return "doc.text"
        case .settings:
            return "gearshape"
        }
    }

    @ViewBuilder
    var view: some View {
        switch self {
        case .dashboard:
            DashboardView()
        case .shifts:
            ShiftsView()
        case .reports:
            ReportsView()
        case .settings:
            SettingsView()
        }
    }
}
