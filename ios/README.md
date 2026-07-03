# ElmTrackr iOS

This folder contains the iOS work for ElmTrackr.

## What Is Here

```text
ios/
├── README.md
├── DAD_XCODE_SETUP.md
└── ElmTrackrCore/
    ├── Package.swift
    └── Sources/
        └── ElmTrackrCore/
            ├── RootView.swift
            ├── App/
            ├── Features/
            ├── Domain/
            ├── Data/
            └── Shared/
```

## ElmTrackrCore

`ElmTrackrCore` is a Swift Package. It is meant to hold almost all of the iOS app code.

The package exposes this public SwiftUI view:

```swift
RootView()
```

The small Xcode app shell should import the package and show `RootView()`.

## Current Screens

The starter app has four native SwiftUI tabs:

- Dashboard
- Shifts
- Reports
- Settings

Each tab currently shows a simple placeholder screen.

## What Is Not Included Yet

This starter package intentionally does not include:

- Supabase
- Local persistence
- Authentication
- An Xcode project file

Those can be added later after the app shell is running.

## For Xcode Setup

Use [DAD_XCODE_SETUP.md](./DAD_XCODE_SETUP.md) for very detailed setup instructions.
