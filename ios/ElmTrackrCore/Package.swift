// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "ElmTrackrCore",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "ElmTrackrCore",
            targets: ["ElmTrackrCore"]
        )
    ],
    targets: [
        .target(
            name: "ElmTrackrCore"
        )
    ]
)
