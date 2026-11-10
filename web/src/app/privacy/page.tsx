import React from "react";
import Link from "next/link";
import { ArrowLeft, Shield, Lock, HardDrive, Globe, Trash2, Mail } from "lucide-react";
import { ThemeToggle } from "../../components/ThemeProvider";
import { CURRENT_APP_VERSION } from "../../lib/version";

export const metadata = {
  title: "Privacy Policy · Art Wallpaper",
  description: "Art Wallpaper is local-first, zero-telemetry desktop software. Learn how your data is protected.",
};

export default function PrivacyPolicyPage() {
  return (
    <div className="relative min-h-screen bg-[var(--canvas-bg)] text-[var(--paper-white)] selection:bg-[var(--gallery-gold)] selection:text-[var(--canvas-bg)] font-sans antialiased transition-colors duration-500">
      {/* Film grain texture */}
      <div className="fixed inset-0 film-grain pointer-events-none z-10" />

      {/* Header */}
      <header className="relative z-20 border-b border-[var(--border-subtle)] bg-[var(--surface-glass)] backdrop-blur-md px-6 sm:px-12 py-4 flex items-center justify-between transition-colors">
        <Link
          href="/"
          className="flex items-center gap-2 text-xs uppercase tracking-widest text-[var(--paper-muted)] hover:text-[var(--gallery-gold)] transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Return to Salon</span>
        </Link>

        <div className="flex items-center gap-4 sm:gap-6 text-xs">
          <Link href="/terms" className="text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors">
            Terms of Service
          </Link>
          <a
            href="https://github.com/Princeyyyy/art-wallpaper"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[var(--gallery-gold)] hover:underline"
          >
            GitHub
          </a>
          <ThemeToggle />
        </div>
      </header>

      {/* Main Content */}
      <main className="relative z-10 max-w-4xl mx-auto px-6 sm:px-12 py-16 sm:py-24">
        {/* Title Block */}
        <div className="mb-16 pb-8 border-b border-[var(--border-subtle)]">
          <div className="flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-[var(--gallery-gold)]" />
            <span className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold">
              Legal and Data Governance
            </span>
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl text-[var(--paper-white)] font-normal leading-tight mb-4">
            Privacy Policy
          </h1>
          <p className="text-xs text-[var(--paper-muted)] tracking-wider uppercase font-mono">
            Effective Date: November 10, 2026 · Version {CURRENT_APP_VERSION}
          </p>
        </div>

        {/* Executive Summary Callout */}
        <div className="p-6 sm:p-8 rounded-lg bg-[var(--surface-elevated)] border border-[var(--gallery-gold)]/30 mb-16 shadow-[0_20px_50px_rgba(0,0,0,0.2)]">
          <h2 className="font-serif italic text-xl text-[var(--paper-white)] mb-3">
            Executive Summary: Local-First and Zero Telemetry
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed mb-4">
            Art Wallpaper was designed with privacy as an uncompromised architectural principle. The application does not collect, track, profile, transmit, or sell any personal data, usage metrics, or system identifiers. Everything required to curate and render museum masterpieces runs entirely on your local machine.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 border-t border-[var(--border-subtle)] text-xs">
            <div className="flex items-center gap-2 text-[var(--paper-white)]">
              <Lock className="w-3.5 h-3.5 text-[var(--gallery-gold)]" />
              <span>0% Analytics & Tracking</span>
            </div>
            <div className="flex items-center gap-2 text-[var(--paper-white)]">
              <HardDrive className="w-3.5 h-3.5 text-[var(--gallery-gold)]" />
              <span>100% Local Storage</span>
            </div>
            <div className="flex items-center gap-2 text-[var(--paper-white)]">
              <Globe className="w-3.5 h-3.5 text-[var(--gallery-gold)]" />
              <span>Public APIs Only</span>
            </div>
          </div>
        </div>

        {/* Section 1 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            1. Information We Do Not Collect
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            We believe you should enjoy fine art on your desktop without corporate surveillance. Art Wallpaper does not collect or log:
          </p>
          <ul className="list-disc list-inside text-sm text-[var(--paper-muted)] space-y-2 pl-2">
            <li>Names, email addresses, phone numbers, or account credentials.</li>
            <li>IP addresses, MAC addresses, hardware serial numbers, or device fingerprints.</li>
            <li>Browsing history, desktop activity, or running process lists.</li>
            <li>Telemetry, error beacons, event tracking, crash analytics, or session replays.</li>
            <li>Location data or GPS coordinates.</li>
          </ul>
        </section>

        {/* Section 2 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            2. Network Operations & External Communications
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            Art Wallpaper communicates over the internet exclusively for two functions:
          </p>
          
          <div className="space-y-4 pt-2">
            <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)]">
              <h3 className="text-sm font-semibold text-[var(--gallery-gold)] mb-1">
                A. The Metropolitan Museum of Art Open Access API
              </h3>
              <p className="text-xs text-[var(--paper-muted)] leading-relaxed mb-2">
                Endpoint: <code className="text-[var(--gallery-gold)] bg-[var(--surface-bg)] px-1.5 py-0.5 rounded border border-[var(--border-subtle)]">collectionapi.metmuseum.org</code>
              </p>
              <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
                The application connects to The Met API to query public-domain artwork metadata (title, artist, date, medium) and download high-resolution public-domain image files. No authentication tokens or user identities are transmitted during these requests.
              </p>
            </div>

            <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)]">
              <h3 className="text-sm font-semibold text-[var(--gallery-gold)] mb-1">
                B. GitHub Releases API (Update Checks)
              </h3>
              <p className="text-xs text-[var(--paper-muted)] leading-relaxed mb-2">
                Endpoint: <code className="text-[var(--gallery-gold)] bg-[var(--surface-bg)] px-1.5 py-0.5 rounded border border-[var(--border-subtle)]">api.github.com/repos/Princeyyyy/art-wallpaper/releases/latest</code>
              </p>
              <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
                The application queries the public GitHub Releases endpoint to inspect the latest semantic version string. If a new version exists, it can download the official signed installer directly to your temporary updates folder. No user data is sent.
              </p>
            </div>
          </div>
        </section>

        {/* Section 3 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            3. Local Storage Architecture
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            All state is isolated locally on your computer in your user profile:
            <br />
            <code className="text-xs text-[var(--gallery-gold)] bg-[var(--surface-elevated)] px-2 py-1 rounded inline-block mt-2 border border-[var(--border-subtle)]">
              Windows: %USERPROFILE%\.artwallpaper\ &nbsp;|&nbsp; macOS: ~/.artwallpaper/
            </code>
          </p>

          <div className="overflow-x-auto pt-2">
            <table className="w-full text-left text-xs border border-[var(--border-subtle)] rounded">
              <thead className="bg-[var(--surface-elevated)] text-[var(--gallery-gold)] border-b border-[var(--border-subtle)]">
                <tr>
                  <th className="p-3">File / Folder</th>
                  <th className="p-3">Purpose</th>
                  <th className="p-3">Transmission</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border-subtle)] text-[var(--paper-muted)]">
                <tr>
                  <td className="p-3 font-mono text-[var(--paper-white)]">settings.json (.bak)</td>
                  <td className="p-3">Active preferences (rotation hour, theme mode, tray state)</td>
                  <td className="p-3 text-emerald-500 font-semibold">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[var(--paper-white)]">artworks/</td>
                  <td className="p-3">High-resolution cached artwork JPEGs (capped at 100 items)</td>
                  <td className="p-3 text-emerald-500 font-semibold">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[var(--paper-white)]">metadata/</td>
                  <td className="p-3">Artwork exhibition details (title, artist, year, medium)</td>
                  <td className="p-3 text-emerald-500 font-semibold">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[var(--paper-white)]">history.json</td>
                  <td className="p-3">Shown artwork IDs to prevent duplicate desktop wallpapers</td>
                  <td className="p-3 text-emerald-500 font-semibold">Never transmitted</td>
                </tr>
                <tr>
                  <td className="p-3 font-mono text-[var(--paper-white)]">logs/</td>
                  <td className="p-3">Local application logs (rotated, pruned at 2MB)</td>
                  <td className="p-3 text-emerald-500 font-semibold">Never transmitted</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        {/* Section 4 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            4. User Rights and Complete Data Erasure
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            Because we do not store your data on external servers, you have total autonomy. You can completely delete all stored artworks, preferences, and logs at any time by simply deleting the <code className="text-xs text-[var(--gallery-gold)] bg-[var(--surface-elevated)] px-1.5 py-0.5 rounded border border-[var(--border-subtle)]">.artwallpaper</code> directory from your home folder.
          </p>
          <div className="p-4 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)] flex items-start gap-3">
            <Trash2 className="w-4 h-4 text-rose-500 mt-0.5 flex-shrink-0" />
            <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
              Uninstalling Art Wallpaper via Windows Control Panel or macOS Trash will cease all background operations immediately. Deleting the user directory permanently erases all cached museum assets.
            </p>
          </div>
        </section>

        {/* Section 5 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            5. Contact and Responsible Disclosure
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            If you have security inquiries, audit verifications, or questions regarding our privacy architecture, submit an issue or private vulnerability report through GitHub:
          </p>
          <div className="flex flex-wrap items-center gap-4 pt-2 text-xs">
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/security"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-[var(--gallery-gold)] hover:underline"
            >
              <Shield className="w-3.5 h-3.5" />
              <span>Security Advisory Desk</span>
            </a>
            <span className="text-[var(--paper-muted)]">·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-[var(--gallery-gold)] hover:underline"
            >
              <Mail className="w-3.5 h-3.5" />
              <span>GitHub Public Tracker</span>
            </a>
          </div>
        </section>

        {/* Section 6 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            6. Inquiries and Contact
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            If you have questions regarding this Privacy Policy or wish to report a security inquiry, please contact the maintainer directly:
          </p>
          <div className="flex items-center gap-3 p-4 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)] w-fit">
            <Mail className="w-4 h-4 text-[var(--gallery-gold)]" />
            <a
              href="mailto:princemumo80@gmail.com"
              className="text-xs font-mono text-[var(--paper-white)] hover:text-[var(--gallery-gold)] transition-colors"
            >
              princemumo80@gmail.com
            </a>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="relative z-10 border-t border-[var(--border-subtle)] py-10 px-6 sm:px-12 bg-[var(--canvas-bg)] text-xs font-sans text-[var(--paper-muted)] transition-colors">
        <div className="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
          <p className="text-[11px] text-[var(--paper-muted)]">
            Art Wallpaper Manager · Maintained by Prince · Free and open source.
          </p>
          <div className="flex items-center gap-4 text-[11px]">
            <Link href="/" className="hover:text-[var(--gallery-gold)] transition-colors">
              Home
            </Link>
            <span>·</span>
            <Link href="/terms" className="hover:text-[var(--gallery-gold)] transition-colors">
              Terms of Service
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
