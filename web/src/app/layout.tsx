import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Art Wallpaper — An Art Museum for Your Desktop",
  description: "Walk into an art gallery every morning. Curated masterpieces from The Metropolitan Museum of Art delivered directly to your desktop.",
  icons: {
    icon: "/assets/icon-128.png",
  },
  openGraph: {
    title: "Art Wallpaper — Walk Into an Art Gallery",
    description: "Curated fine art from The Met delivered directly to your Windows & Mac desktop. Free and open source.",
    images: ["/assets/social-preview-1280x640.png"],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="scroll-smooth">
      <head>
        <meta name="referrer" content="no-referrer" />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Cinzel:wght@500;600;700;800&family=Cormorant+Garamond:ital,wght@0,400;0,600;0,700;1,400;1,600&family=Plus+Jakarta+Sans:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="bg-gallery-obsidian text-gallery-vellum antialiased selection:bg-gallery-gold selection:text-gallery-obsidian relative min-h-screen">
        {children}
      </body>
    </html>
  );
}
