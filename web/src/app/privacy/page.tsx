import React from "react";
import Link from "next/link";
import { ArrowLeft, Shield, Lock, HardDrive, Globe, Trash2, Mail } from "lucide-react";

export const metadata = {
  title: "Privacy Policy · Art Wallpaper",
  description: "Art Wallpaper is local-first, zero-telemetry desktop software. Learn how your data is protected.",
};

export default function PrivacyPolicyPage() {
  return (
    <div className="relative min-h-screen bg-[#07080B] text-[#EFE9DF] selection:bg-[#C29F6C] selection:text-[#07080B] font-sans antialiased">
      {/* Film grain texture */}
      <div className="fixed inset-0 film-grain pointer-events-none z-10" />

      {/* Header */}
      <header className="relative z-20 border-b border-white/[0.06] bg-[#07080B]/90 backdrop-blur-md px-6 sm:px-12 py-5 flex items-center justify-between">
        <Link
          href="/"
          className="flex items-center gap-2 text-xs uppercase tracking-widest text-[#8E8B82] hover:text-[#C29F6C] transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Return to Salon</span>
        </Link>

        <div className="flex items-center gap-6 text-xs">
          <Link href="/terms" className="text-[#8E8B82] hover:text-[#EFE9DF] transition-colors">
            Terms of Service
          </Link>
          <a
            href="https://github.com/Princeyyyy/art-wallpaper"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[#C29F6C] hover:underline"
          >
            GitHub
          </a>
        </div>
      </header>

      {/* Main Content */}
      <main className="relative z-10 max-w-4xl mx-auto px-6 sm:px-12 py-16 sm:py-24">
        {/* Title Block */}
        <div className="mb-16 pb-8 border-b border-white/[0.08]">
          <div className="flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-[#C29F6C]" />
            <span className="font-serif tracking-widest text-[#C29F6C] uppercase text-xs font-semibold">
              Legal and Data Governance
            </span>
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl text-[#F5EFE6] font-normal leading-tight mb-4">
            Privacy Policy
          </h1>
          <p className="text-xs text-[#8E8B82] tracking-wider uppercase font-mono">
            Effective Date: November 10, 2026 · Version 4.1.5
          </p>
        </div>

        {/* Executive Summary Callout */}
        <div className="p-6 sm:p-8 rounded-lg bg-[#0D0E14] border border-[#C29F6C]/30 mb-16 shadow-[0_20px_50px_rgba(0,0,0,0.6)]">
          <h2 className="font-serif italic text-xl text-[#F5EFE6] mb-3">
            Executive Summary: Local-First and Zero Telemetry
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed mb-4">
            Art Wallpaper was designed with privacy as an uncompromised architectural principle. The application does not collect, track, profile, transmit, or sell any personal data, usage metrics, or system identifiers. Everything required to curate and render museum masterpieces runs entirely on your local machine.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 border-t border-white/[0.06] text-xs">
            <div className="flex items-center gap-2 text-[#EFE9DF]">
              <Lock className="w-3.5 h-3.5 text-[#C29F6C]" />
              <span>0% Analytics & Tracking</span>
            </div>
            <div className="flex items-center gap-2 text-[#EFE9DF]">
              <HardDrive className="w-3.5 h-3.5 text-[#C29F6C]" />
              <span>100% Local Storage</span>
            </div>
            <div className="flex items-center gap-2 text-[#EFE9DF]">
              <Globe className="w-3.5 h-3.5 text-[#C29F6C]" />
              <span>Public APIs Only</span>
            </div>
          </div>
        </div>

        {/* Section 1 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            1. Information We Do Not Collect
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            We believe you should enjoy fine art on your desktop without corporate surveillance. Art Wallpaper does not collect or log:
          </p>
          <ul className="list-disc list-inside text-sm text-[#8E8B82] space-y-2 pl-2">
            <li>Names, email addresses, phone numbers, or account credentials.</li>
            <li>IP addresses, MAC addresses, hardware serial numbers, or device fingerprints.</li>
            <li>Browsing history, desktop activity, or running process lists.</li>
            <li>Telemetry, error beacons, event tracking, crash analytics, or session replays.</li>
            <li>Location data or GPS coordinates.</li>
          </ul>
        </section>

        {/* Section 2 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            2. Network Operations & External Communications
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            Art Wallpaper communicates over the internet exclusively for two functions:
          </p>
          
          <div className="space-y-4 pt-2">
            <div className="p-5 rounded bg-[#0A0B10] border border-white/[0.06]">
              <h3 className="text-sm font-semibold text-[#C29F6C] mb-1">
                A. The Metropolitan Museum of Art Open Access API
              </h3>
              <p className="text-xs text-[#8E8B82] leading-relaxed mb-2">
                Endpoint: <code className="text-[#C29F6C] bg-white/[0.04] px-1.5 py-0.5 rounded">collectionapi.metmuseum.org</code>
              </p>
              <p className="text-xs text-[#8E8B82] leading-relaxed">
                The application connects to The Met API to query public-domain artwork metadata (title, artist, date, medium) and download high-resolution public-domain image files. No authentication tokens or user identities are transmitted during these requests.
              </p>
            </div>

            <div className="p-5 rounded bg-[#0A0B10] border border-white/[0.06]">
              <h3 className="text-sm font-semibold text-[#C29F6C] mb-1">
                B. GitHub Releases API (Update Checks)
              </h3>
              <p className="text-xs text-[#8E8B82] leading-relaxed mb-2">
                Endpoint: <code className="text-[#C29F6C] bg-white/[0.04] px-1.5 py-0.5 rounded">api.github.com/repos/Princeyyyy/art-wallpaper/releases/latest</code>
              </p>
              <p className="text-xs text-[#8E8B82] leading-relaxed">
                The application queries the public GitHub Releases endpoint to inspect the latest semantic version string. If a new version exists, it can download the official signed installer directly to your temporary updates folder. No user data is sent.
              </p>
            </div>
          </div>
        </section>

        {/* Section 3 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            3. Local Storage Architecture
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            All state is isolated locally on your computer in your user profile:
            <br />
            <code className="text-xs text-[#C29F6C] bg-white/[0.04] px-2 py-1 rounded inline-block mt-2">
              Windows: %USERPROFILE%\.artwallpaper\ &nbsp;|&nbsp; macOS: ~/.artwallpaper/
            </code>
          </p>

          <div className="overflow-x-auto pt-2">
            <table className="w-full text-left text-xs border border-white/[0.08] rounded">
              <thead className="bg-[#0E1017] text-[#C29F6C] border-b border-white/[0.08]">
                <tr>
                  <th className="p-3">File / Folder</th>
                  <th className="p-3">Purpose</th>
                  <th className="p-3">Transmission</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.06] text-[#8E8B82]">
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">settings.json (.bak)</td>
                  <td className="p-3">Active preferences (rotation hour, theme mode, tray state)</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">artworks/</td>
                  <td className="p-3">High-resolution cached artwork JPEGs (capped at 100 items)</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">metadata/</td>
                  <td className="p-3">Artwork exhibition details (title, artist, year, medium)</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">history.json</td>
                  <td className="p-3">Shown artwork IDs to prevent duplicate desktop wallpapers</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">logs/</td>
                  <td className="p-3">Diagnostic rolling logs (app.log 1-day, error.log 30-day)</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[#EFE9DF]">updates/</td>
                  <td className="p-3">Downloaded installers, purged automatically on startup</td>
                  <td className="p-3 text-green-400">Never transmitted</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        {/* Section 4 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            4. User Rights, Data Control & Complete Purge
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            You maintain complete dominion over all data stored on your machine. You can purge all application state, history, caches, and preferences at any time through three methods:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
            <div className="p-4 rounded bg-[#0A0B10] border border-white/[0.06]">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#EFE9DF] mb-2">
                <Trash2 className="w-3.5 h-3.5 text-[#C29F6C]" />
                <span>In-App Settings</span>
              </div>
              <p className="text-xs text-[#8E8B82] leading-relaxed">
                Open Settings &gt; Maintenance &gt; Click &quot;Reset All Data&quot; to wipe local files instantly.
              </p>
            </div>
            <div className="p-4 rounded bg-[#0A0B10] border border-white/[0.06]">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#EFE9DF] mb-2">
                <Trash2 className="w-3.5 h-3.5 text-[#C29F6C]" />
                <span>Command Line</span>
              </div>
              <p className="text-xs text-[#8E8B82] leading-relaxed">
                Launch with <code className="text-[#C29F6C]">--clear-data</code> to automatically purge caches before window creation.
              </p>
            </div>
            <div className="p-4 rounded bg-[#0A0B10] border border-white/[0.06]">
              <div className="flex items-center gap-2 text-xs font-semibold text-[#EFE9DF] mb-2">
                <Trash2 className="w-3.5 h-3.5 text-[#C29F6C]" />
                <span>Manual Removal</span>
              </div>
              <p className="text-xs text-[#8E8B82] leading-relaxed">
                Delete the <code className="text-[#C29F6C]">.artwallpaper</code> folder directly from your user directory.
              </p>
            </div>
          </div>
        </section>

        {/* Section 5 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            5. Third-Party Websites & Services
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            This website and the application may contain hyperlinks to external websites (such as GitHub or The Metropolitan Museum of Art). We do not control and are not responsible for the privacy practices of external third-party services. We encourage you to review their respective privacy policies.
          </p>
        </section>

        {/* Section 6 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            6. Inquiries and Contact
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            If you have questions regarding this Privacy Policy or wish to report a security inquiry, please contact the maintainer directly:
          </p>
          <div className="flex items-center gap-3 p-4 rounded bg-[#0D0E14] border border-white/[0.08] w-fit">
            <Mail className="w-4 h-4 text-[#C29F6C]" />
            <a
              href="mailto:princemumo80@gmail.com"
              className="text-xs font-mono text-[#EFE9DF] hover:text-[#C29F6C] transition-colors"
            >
              princemumo80@gmail.com
            </a>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="relative z-10 border-t border-white/[0.04] py-10 px-6 sm:px-12 bg-[#030406] text-xs font-sans text-[#8E8B82]/70">
        <div className="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
          <p className="text-[11px] text-[#8E8B82]">
            Art Wallpaper Manager · Maintained by Prince · Free and open source.
          </p>
          <div className="flex items-center gap-4 text-[11px]">
            <Link href="/" className="hover:text-[#C29F6C] transition-colors">
              Home
            </Link>
            <span>·</span>
            <Link href="/terms" className="hover:text-[#C29F6C] transition-colors">
              Terms of Service
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
