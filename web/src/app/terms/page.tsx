import React from "react";
import Link from "next/link";
import { ArrowLeft, BookOpen, Scale, Award, ShieldAlert, Mail } from "lucide-react";
import { ThemeToggle } from "../../components/ThemeProvider";
import { CURRENT_APP_VERSION } from "../../lib/version";

export const metadata = {
  title: "Terms of Service · Art Wallpaper",
  description: "Terms of Service, license agreements, and public domain artwork attributions for Art Wallpaper.",
};

export default function TermsOfServicePage() {
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
          <Link href="/privacy" className="text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors">
            Privacy Policy
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
            <Scale className="w-4 h-4 text-[var(--gallery-gold)]" />
            <span className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold">
              Legal Agreement and License
            </span>
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl text-[var(--paper-white)] font-normal leading-tight mb-4">
            Terms of Service
          </h1>
          <p className="text-xs text-[var(--paper-muted)] tracking-wider uppercase font-mono">
            Effective Date: November 10, 2026 · Version {CURRENT_APP_VERSION}
          </p>
        </div>

        {/* Section 1 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            1. Acceptance of Terms
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            By downloading, installing, running, or building Art Wallpaper (hereafter referred to as the &quot;Software&quot;), you agree to be bound by these Terms of Service. If you do not agree with any provision of these terms, do not install or use the Software.
          </p>
        </section>

        {/* Section 2 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            2. Open Source License
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            The source code of Art Wallpaper is released under the{" "}
            <strong className="text-[var(--paper-white)]">
              Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)
            </strong>{" "}
            license. Under this license:
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
            <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)]">
              <h3 className="text-sm font-semibold text-[var(--gallery-gold)] mb-2">You Are Free To:</h3>
              <ul className="text-xs text-[var(--paper-muted)] space-y-1.5 list-disc list-inside">
                <li><strong className="text-[var(--paper-white)]">Share:</strong> Copy and redistribute the material in any medium.</li>
                <li><strong className="text-[var(--paper-white)]">Adapt:</strong> Remix, transform, and build upon the codebase.</li>
              </ul>
            </div>
            <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)]">
              <h3 className="text-sm font-semibold text-[var(--gallery-gold)] mb-2">Under These Terms:</h3>
              <ul className="text-xs text-[var(--paper-muted)] space-y-1.5 list-disc list-inside">
                <li><strong className="text-[var(--paper-white)]">Attribution:</strong> You must give appropriate credit to Prince and provide a link to the license.</li>
                <li><strong className="text-[var(--paper-white)]">NonCommercial:</strong> You may not use the material for commercial purposes or monetization.</li>
              </ul>
            </div>
          </div>
        </section>

        {/* Section 3 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            3. Museum Artwork & Public Domain Attributions
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            All historic artworks delivered to your desktop via Art Wallpaper are sourced from The Metropolitan Museum of Art Open Access collection and are in the{" "}
            <strong className="text-[var(--paper-white)]">Public Domain (Creative Commons Zero / CC0)</strong>:
          </p>
          <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)] text-xs text-[var(--paper-muted)] leading-relaxed space-y-2">
            <p>
              Under Creative Commons Zero, these historic fine art images may be freely viewed, downloaded, copied, modified, and enjoyed worldwide without copyright restrictions.
            </p>
            <p>
              Art Wallpaper provides curatorial attribution (title, artist, year, medium, dimensions, and curatorial notes) as historical documentation, not as a copyright restriction.
            </p>
          </div>
        </section>

        {/* Section 4 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            4. Disclaimer of Warranties
          </h2>
          <div className="p-5 rounded bg-[var(--surface-elevated)] border border-[var(--border-subtle)] text-xs text-[var(--paper-muted)] leading-relaxed space-y-3">
            <div className="flex items-center gap-2 text-[var(--gallery-gold)] font-semibold">
              <ShieldAlert className="w-4 h-4" />
              <span>THE SOFTWARE IS PROVIDED &quot;AS IS&quot;, WITHOUT WARRANTY OF ANY KIND</span>
            </div>
            <p>
              TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE MAINTAINER DISCLAIMS ALL WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT.
            </p>
            <p>
              IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
            </p>
          </div>
        </section>

        {/* Section 5 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            5. Governance and Contributions
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            Art Wallpaper operates under a Benevolent Dictator for Life (BDFL) governance model. All contributions submitted via GitHub pull requests are subject to the project&apos;s{" "}
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/blob/master/GOVERNANCE.md"
              target="_blank"
              rel="noopener noreferrer"
              className="text-[var(--gallery-gold)] hover:underline"
            >
              GOVERNANCE.md
            </a>{" "}
            and{" "}
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/blob/master/CONTRIBUTING.md"
              target="_blank"
              rel="noopener noreferrer"
              className="text-[var(--gallery-gold)] hover:underline"
            >
              CONTRIBUTING.md
            </a>{" "}
            guidelines. Merges into the master branch and release publishing are strictly governed by the repository owner.
          </p>
        </section>

        {/* Section 6 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[var(--paper-white)] font-normal">
            6. Contact & Legal Notices
          </h2>
          <p className="text-sm text-[var(--paper-muted)] leading-relaxed">
            For legal inquiries, copyright notices, or licensing clarification, please contact:
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
            <Link href="/privacy" className="hover:text-[var(--gallery-gold)] transition-colors">
              Privacy Policy
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
