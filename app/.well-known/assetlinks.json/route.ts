import { NextResponse } from "next/server";

// Digital Asset Links for Android App Links verification.
//
// The SHA-256 fingerprint below is the Play App Signing key that Google uses
// to sign the released app. It must match the certificate shown in
// Play Console → Setup → App signing. Update it here whenever that key
// changes (e.g. a key reset during testing) and redeploy.
const assetLinks = [
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: "com.elmlaunch.myapp",
      sha256_cert_fingerprints: [
        "84:A6:A1:EB:1E:51:7B:34:95:F9:02:A0:6C:52:24:F9:49:E2:D2:F5:58:21:EB:B7:94:AC:3D:81:F9:0E:0E:FE",
      ],
    },
  },
];

// Served as a static asset; the file never changes between requests.
export const dynamic = "force-static";

export function GET() {
  // NextResponse.json sets `content-type: application/json`, which the
  // Digital Asset Links verifier requires.
  return NextResponse.json(assetLinks);
}
