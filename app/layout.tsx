import type { Metadata, Viewport } from "next";
import { Suspense } from "react";
import "./globals.css";
import { ToastProvider } from "@/components/ui/Toast";
import { SettingsProvider } from "@/contexts/SettingsContext";
import { OnboardingGate } from "@/components/onboarding/OnboardingGate";

export const metadata: Metadata = {
  title: "ElmTrackr",
  description: "Personal shift & hours tracker",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "ElmTrackr",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  themeColor: "#5B4DF2",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <head>
        <link rel="apple-touch-icon" href="/icon-192.png" />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400;12..96,500;12..96,600;12..96,700&family=Hanken+Grotesk:wght@400;500;600;700;800&display=swap"
          rel="stylesheet"
        />
        {/*
          Runs before React hydration to apply the saved theme class
          immediately, preventing a flash of the wrong theme on load.
        */}
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var t=localStorage.getItem('elmtrackr-theme');if(t==='dark'){document.documentElement.classList.add('dark')}}catch(e){}})();`,
          }}
        />
      </head>
      <body className="min-h-screen antialiased" style={{ fontFamily: "var(--au-font)", background: "var(--au-bg)", color: "var(--au-ink)" }}>
        <ToastProvider>
          <SettingsProvider>
            <Suspense fallback={null}>
              <OnboardingGate>{children}</OnboardingGate>
            </Suspense>
          </SettingsProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
