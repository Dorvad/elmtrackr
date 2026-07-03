# ElmTrackr iOS Port

This local repository is for converting the existing native Android ElmTrackr app into an iOS app.

The Android app remains useful as the reference implementation, but new iOS app code should live under `ios/`. Do not change Android files, Supabase migrations, or Xcode project files unless a task specifically asks for that.

## iOS Starter

The first iOS module is a local Swift Package:

```text
ios/ElmTrackrCore
```

It exposes one public SwiftUI entry point:

```swift
RootView()
```

A tiny Xcode iOS app shell can add this local package and show `RootView` from `ContentView.swift`.

For setup details, start here:

- [ios/README.md](./ios/README.md)
- [ios/DAD_XCODE_SETUP.md](./ios/DAD_XCODE_SETUP.md)

## Repository Layout

| Path | Role |
|------|------|
| [`ios/`](./ios/) | New iOS app package and setup notes |
| [`ios/ElmTrackrCore/`](./ios/ElmTrackrCore/) | Swift Package containing most future iOS app code |
| [`android/`](./android/) | Existing native Android app, used as the reference implementation |
| [`supabase/`](./supabase/) | Existing database schema and migrations |
| [`app/`](./app/) | Frozen legacy Next.js web UI |

## Current iOS Status

The iOS package is intentionally small. It currently has:

- A buildable Swift Package named `ElmTrackrCore`
- A public `RootView`
- A native SwiftUI `TabView`
- Placeholder tabs for Dashboard, Shifts, Reports, and Settings

Not added yet:

- Supabase
- Local persistence
- Authentication
- An `.xcodeproj`
