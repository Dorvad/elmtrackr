# Dad Xcode Setup

These instructions are for getting the iPhone version of ElmTrackr running on a Mac.

You do not need to understand the code. The goal is only to open Xcode, connect the local package, press Run, and send screenshots if anything looks confusing.

## 1. Install Xcode

1. Open the **App Store** on the Mac.
2. Search for **Xcode**.
3. Click **Get** or **Install**.
4. Wait for it to finish. Xcode is very large, so this can take a while.
5. Open **Xcode** once after it installs.
6. If Xcode asks to install extra tools or components, click **Install**.
7. If the Mac asks for the computer password, enter it.

## 2. Download Or Clone The Repo

Use whichever option is easiest.

### Option A: Download ZIP

1. Open the GitHub page for the ElmTrackr iOS repo.
2. Click the green **Code** button.
3. Click **Download ZIP**.
4. Open the downloaded ZIP file.
5. Move the extracted folder somewhere easy, like **Desktop** or **Documents**.

### Option B: Clone With GitHub Desktop

1. Install **GitHub Desktop** if it is not already installed.
2. Open GitHub Desktop.
3. Sign in if it asks.
4. Choose **File > Clone Repository**.
5. Select the ElmTrackr iOS repo.
6. Choose a local folder you can find again, like **Documents**.
7. Click **Clone**.

## 3. Create A Small iPhone App In Xcode

If an Xcode app project already exists, you can skip this section and open that project.

1. Open **Xcode**.
2. Click **Create New Project**.
3. Select **iOS** at the top.
4. Select **App**.
5. Click **Next**.
6. Product Name: type `ElmTrackr`.
7. Team: choose your Apple account if one appears. If nothing appears, leave it alone for now.
8. Organization Identifier: type something simple like `com.elmtrackr`.
9. Interface: choose **SwiftUI**.
10. Language: choose **Swift**.
11. Storage: choose **None** if Xcode asks.
12. Click **Next**.
13. Save the project somewhere easy to find.

## 4. Add The Local ElmTrackrCore Package

This connects the small Xcode app to the real iOS app code.

1. In Xcode, look at the file list on the left.
2. Click the blue project icon at the very top of the file list.
3. In the main area, click the project name under **PROJECT**.
4. Click the **Package Dependencies** tab.
5. Click the **+** button.
6. In the window that opens, click **Add Local...**.
7. Find the repo folder you downloaded or cloned.
8. Open this folder inside it:

   ```text
   ios/ElmTrackrCore
   ```

9. Click **Add Package**.
10. If Xcode asks which app target should use it, make sure the ElmTrackr app is checked.
11. Click **Add Package** again if Xcode asks.

## 5. Replace ContentView.swift

Now tell the tiny Xcode app to show the ElmTrackr package screen.

1. In Xcode, find `ContentView.swift` in the file list on the left.
2. Click it once.
3. Select all the text in that file.
4. Delete it.
5. Paste this:

```swift
import SwiftUI
import ElmTrackrCore

struct ContentView: View {
    var body: some View {
        RootView()
    }
}
```

6. Press **Command + S** to save.

## 6. Choose An iPhone Simulator

1. At the top of Xcode, near the Run button, find the device selector.
2. It might say something like **Any iOS Device**, **My Mac**, or an iPhone name.
3. Click it.
4. Choose an iPhone simulator, for example:
   - iPhone 16
   - iPhone 16 Pro
   - iPhone 15

If no iPhone simulator appears:

1. Click **Xcode > Settings** in the menu bar.
2. Click **Platforms**.
3. Install an iOS simulator if Xcode offers one.
4. Go back and choose the simulator again.

## 7. Press Run

1. Click the triangular **Run** button in the top left of Xcode.
2. The first build may take a few minutes.
3. The iPhone simulator should open.
4. The app should show four tabs at the bottom:
   - Dashboard
   - Shifts
   - Reports
   - Settings

## 8. Send Screenshots

Please send screenshots in either case.

If it works, send:

1. A screenshot of the simulator showing the app.
2. A screenshot of Xcode showing the project.

If there is an error, send:

1. A screenshot of the red error message in Xcode.
2. A screenshot of the left file list in Xcode.
3. A screenshot of **Package Dependencies** if the error mentions the package.

## 9. What Success Looks Like

Success means the simulator opens and the ElmTrackr app shows a simple screen with tabs:

- Dashboard
- Shifts
- Reports
- Settings

The screens are only placeholders for now. That is expected.
