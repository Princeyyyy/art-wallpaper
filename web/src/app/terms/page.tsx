import React from "react";
import Link from "next/link";
import { ArrowLeft, BookOpen, Scale, Award, ShieldAlert, Mail } from "lucide-react";

export const metadata = {
  title: "Terms of Service · Art Wallpaper",
  description: "Terms of Service, license agreements, and public domain artwork attributions for Art Wallpaper.",
};

export default function TermsOfServicePage() {
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
          <Link href="/privacy" className="text-[#8E8B82] hover:text-[#EFE9DF] transition-colors">
            Privacy Policy
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
            <Scale className="w-4 h-4 text-[#C29F6C]" />
            <span className="font-serif tracking-widest text-[#C29F6C] uppercase text-xs font-semibold">
              Legal Agreement and License
            </span>
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl text-[#F5EFE6] font-normal leading-tight mb-4">
            Terms of Service
          </h1>
          <p className="text-xs text-[#8E8B82] tracking-wider uppercase font-mono">
            Effective Date: November 10, 2026 · Version 4.1.5
          </p>
        </div>

        {/* Section 1 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            1. Acceptance of Terms
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            By downloading, installing, launching, copying, or otherwise using **Art Wallpaper** (including its desktop binaries, source code, website, and documentation), you acknowledge that you have read, understood, and agree to be bound by these Terms of Service. If you do not agree to these terms, do not install or use the software.
          </p>
        </section>

        {/* Section 2 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            2. Software License (CC BY-NC 4.0)
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            Art Wallpaper is free software released under the **Creative Commons Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0)**.
          </p>
          <div className="p-5 rounded bg-[#0A0B10] border border-white/[0.06] space-y-3 text-xs text-[#8E8B82]">
            <p className="text-[#EFE9DF] font-medium">Under this license, you are free to:</p>
            <ul className="list-disc list-inside space-y-1.5 pl-2">
              <li><strong className="text-[#EFE9DF]">Share:</strong> Copy and redistribute the material in any medium or format.</li>
              <li><strong className="text-[#EFE9DF]">Adapt:</strong> Remix, transform, and build upon the material for personal and educational use.</li>
            </ul>
            <p className="text-[#EFE9DF] font-medium pt-2">Subject to the following terms:</p>
            <ul className="list-disc list-inside space-y-1.5 pl-2">
              <li><strong className="text-[#EFE9DF]">Attribution:</strong> You must give appropriate credit to the original creator (Prince) and provide a link to the original repository.</li>
              <li><strong className="text-[#EFE9DF]">NonCommercial:</strong> You may not use the software, binaries, or derivatives for commercial purposes, including selling the software, bundling it with paid products, or monetizing it with advertising.</li>
            </ul>
          </div>
        </section>

        {/* Section 3 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            3. Public Domain Masterpieces & Museum Attribution
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            All fine art masterpieces and historical curatorial records delivered by the application belong to the public domain collections of **The Metropolitan Museum of Art** under their **Open Access program** and are dedicated to the public domain under Creative Commons Zero (CC0).
          </p>
          <div className="p-5 rounded bg-[#0A0B10] border border-white/[0.06] space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-[#C29F6C]">
              <Award className="w-4 h-4" />
              <span>Independent Open Source Curation</span>
            </div>
            <p className="text-xs text-[#8E8B82] leading-relaxed">
              Art Wallpaper is an independent open-source desktop viewer and is not affiliated with, funded by, or officially endorsed by The Metropolitan Museum of Art. We express deep gratitude to The Met for their visionary leadership in digital open access to world culture.
            </p>
          </div>
        </section>

        {/* Section 4 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            4. Acceptable Use Policy
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            You agree to use Art Wallpaper only for lawful personal desktop customization. You agree not to:
          </p>
          <ul className="list-disc list-inside text-sm text-[#8E8B82] space-y-2 pl-2">
            <li>Modify the software to initiate distributed denial-of-service (DDoS) requests against museum API endpoints.</li>
            <li>Use the software to spread malware, spyware, trojans, or malicious code.</li>
            <li>Misrepresent the provenance, authorship, or historical context of public-domain cultural artifacts.</li>
            <li>Circumvent or tamper with open-source licensing notices or project copyright headers.</li>
          </ul>
        </section>

        {/* Section 5 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            5. Disclaimer of Warranties
          </h2>
          <div className="p-5 rounded bg-[#0D0E14] border border-white/[0.08] space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-yellow-500/90">
              <ShieldAlert className="w-4 h-4" />
              <span>AS IS Software Provision</span>
            </div>
            <p className="text-xs text-[#8E8B82] leading-relaxed uppercase tracking-wide">
              TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, ART WALLPAPER IS PROVIDED ON AN &quot;AS IS&quot; AND &quot;AS AVAILABLE&quot; BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR CONDITIONS OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY, OR FITNESS FOR A PARTICULAR PURPOSE.
            </p>
            <p className="text-xs text-[#8E8B82] leading-relaxed">
              We do not warrant that desktop wallpaper rotation will operate uninterrupted or error-free across all third-party desktop environments, window managers, virtual multi-monitor setups, or operating system updates.
            </p>
          </div>
        </section>

        {/* Section 6 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            6. Limitation of Liability
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            In no event shall the author, maintainers, or contributors be liable for any direct, indirect, incidental, special, exemplary, or consequential damages (including, but not limited to, loss of data, loss of use, or operating system configuration disruption) arising in any way out of the use or inability to use this software.
          </p>
        </section>

        {/* Section 7 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            7. Governance and Modifications to Terms
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            Project governance follows the Benevolent Dictator for Life (BDFL) model outlined in the repository&apos;s <code className="text-[#C29F6C]">GOVERNANCE.md</code>. We reserve the right to modify these Terms of Service at any time. Changes will be published with an updated effective date. Continued use of Art Wallpaper constitutes acceptance of the revised terms.
          </p>
        </section>

        {/* Section 8 */}
        <section className="mb-14 space-y-4">
          <h2 className="font-serif text-2xl text-[#F5EFE6] font-normal">
            8. Contact Information
          </h2>
          <p className="text-sm text-[#8E8B82] leading-relaxed">
            For questions or inquiries regarding these Terms of Service, contact the project maintainer:
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
            <Link href="/privacy" className="hover:text-[#C29F6C] transition-colors">
              Privacy Policy
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
