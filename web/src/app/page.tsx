"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import {
  Sparkles,
  Download,
  Shield,
  Monitor,
  Eye,
  ArrowRight,
  ExternalLink,
  Layers,
  RefreshCw,
  HardDrive,
} from "lucide-react";

interface Masterpiece {
  id: string;
  title: string;
  artist: string;
  year: string;
  medium: string;
  dimensions: string;
  curatorNote: string;
  src: string;
}

const MASTERPIECES: Masterpiece[] = [
  {
    id: "wheat-field",
    title: "Wheat Field with Cypresses",
    artist: "Vincent van Gogh",
    year: "1889",
    medium: "Oil on canvas",
    dimensions: "73 × 93.4 cm",
    curatorNote:
      "Painted in the late summer of 1889 at Saint-Rémy. Heavy impasto brushwork swirls the sunlit wheat against the sombre, flame-like vertical silhouette of the cypresses.",
    src: "artworks/wheat-field.jpg",
  },
  {
    id: "vermeer-pitcher",
    title: "Young Woman with a Water Pitcher",
    artist: "Johannes Vermeer",
    year: "ca. 1662",
    medium: "Oil on canvas",
    dimensions: "45.7 × 40.6 cm",
    curatorNote:
      "A solitary, timeless moment of Dutch domestic tranquility. Vermeer captures the cool, diffused morning daylight streaming onto polished brass and starched linen.",
    src: "artworks/vermeer-water-pitcher.jpg",
  },
  {
    id: "rembrandt-self",
    title: "Self-Portrait",
    artist: "Rembrandt van Rijn",
    year: "1660",
    medium: "Oil on canvas",
    dimensions: "80.3 × 67.3 cm",
    curatorNote:
      "Painted in his fifty-fourth year with unflinching honesty. Layered impasto and dramatic chiaroscuro capture a life of grief, bankruptcy, and enduring artistic mastery.",
    src: "artworks/rembrandt-self-portrait.jpg",
  },
  {
    id: "irises",
    title: "Irises",
    artist: "Vincent van Gogh",
    year: "1890",
    medium: "Oil on canvas",
    dimensions: "73.7 × 92.1 cm",
    curatorNote:
      "Painted in the asylum garden of Saint-Paul. Van Gogh contrasted deep violet blossoms against soft chartreuse foliage, seeking harmonious, restful intensity.",
    src: "artworks/irises.jpg",
  },
  {
    id: "cypresses",
    title: "Cypresses",
    artist: "Vincent van Gogh",
    year: "1889",
    medium: "Oil on canvas",
    dimensions: "93.4 × 74 cm",
    curatorNote:
      "Van Gogh regarded the cypress as an Egyptian obelisk of nature — monumental, dark vertical silhouettes touching the swirling summer sky of Provence.",
    src: "artworks/cypresses.jpg",
  },
];

export default function GallerySalon() {
  const [curtainOpen, setCurtainOpen] = useState(false);
  const [activePiece, setActivePiece] = useState<Masterpiece>(MASTERPIECES[0]);
  const [displayMode, setDisplayMode] = useState<"mantle" | "desktop">("mantle");
  const [mousePos, setMousePos] = useState({ x: 50, y: 35 });

  // Mouse spotlight coordinates
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMousePos({
        x: (e.clientX / window.innerWidth) * 100,
        y: (e.clientY / window.innerHeight) * 100,
      });
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  return (
    <div className="relative min-h-screen bg-[#07080B] text-[#EFE9DF] selection:bg-[#C29F6C] selection:text-[#07080B] overflow-x-hidden font-sans">
      {/* Ambient Museum Cursor Spotlight */}
      <div
        className="gallery-spotlight"
        style={
          {
            "--mouse-x": `${mousePos.x}%`,
            "--mouse-y": `${mousePos.y}%`,
          } as React.CSSProperties
        }
      />

      {/* Film Grain Texture */}
      <div className="fixed inset-0 film-grain pointer-events-none z-10" />

      {/* Architectural Navigation Header */}
      <header className="relative z-20 border-b border-white/[0.06] bg-[#07080B]/90 backdrop-blur-md px-6 sm:px-12 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-1.5 h-1.5 rounded-full bg-[#C29F6C]" />
          <span className="font-serif tracking-widest text-[#C29F6C] uppercase text-xs font-semibold">
            The Private Salon · Masterpiece Installation
          </span>
          <span className="hidden sm:inline-block text-[10px] font-mono text-[#8E8B82] bg-white/[0.04] px-2 py-0.5 rounded border border-white/[0.06]">
            v4.1.5
          </span>
        </div>

        <nav className="flex items-center gap-6 text-xs tracking-wider">
          <a
            href="#exhibition"
            className="hidden md:inline-block text-[#8E8B82] hover:text-[#EFE9DF] transition-colors"
          >
            Mantlepiece
          </a>
          <a
            href="#the-segue"
            className="hidden md:inline-block text-[#8E8B82] hover:text-[#EFE9DF] transition-colors"
          >
            Wallpaper Engine
          </a>
          <Link
            href="/privacy"
            className="hidden sm:inline-block text-[#8E8B82] hover:text-[#EFE9DF] transition-colors"
          >
            Privacy
          </Link>
          <Link
            href="/terms"
            className="hidden sm:inline-block text-[#8E8B82] hover:text-[#EFE9DF] transition-colors"
          >
            Terms
          </Link>
          <a
            href="#the-invitation"
            className="text-[11px] uppercase tracking-wider text-[#C29F6C] border-b border-[#C29F6C]/40 hover:border-[#C29F6C] pb-0.5 transition-colors font-medium"
          >
            Download v4.1.5
          </a>
        </nav>
      </header>

      {/* Prologue: The Invitation */}
      <section className="relative z-10 pt-20 pb-12 px-6 text-center max-w-3xl mx-auto">
        <p className="font-serif italic text-lg sm:text-xl text-[#C29F6C] mb-3">
          Behind the velvet drapery lies a four-hundred-year-old centerpiece.
        </p>

        <h1 className="font-serif text-4xl sm:text-6xl md:text-7xl font-normal text-[#F5EFE6] leading-[1.08] mb-6">
          Pull the curtain.
        </h1>

        <p className="text-xs sm:text-sm text-[#8E8B82] font-light max-w-xl mx-auto leading-relaxed">
          In historical private salons, the most valuable oil paintings were veiled behind heavy velvet to protect them from daylight and reveal them only for deliberate contemplation.
        </p>
      </section>

      {/* The Velvet Curtain & Mantlepiece Theater Stage */}
      <section id="exhibition" className="relative z-10 py-6 px-4 sm:px-10 max-w-5xl mx-auto">
        {/* Brass Drapery Rod */}
        <div className="curtain-rod w-full mb-0 flex items-center justify-between px-4">
          <div className="w-5 h-5 rounded-full bg-gradient-to-br from-[#FFE58F] to-[#785B0B] -ml-2 shadow-lg" />
          <div className="w-5 h-5 rounded-full bg-gradient-to-br from-[#FFE58F] to-[#785B0B] -mr-2 shadow-lg" />
        </div>

        {/* The Classical Wall & Velvet Stage */}
        <div className="relative rounded-b-md bg-[#0D0E13] border-x border-b border-white/[0.06] overflow-hidden min-h-[520px] sm:min-h-[640px] flex flex-col justify-between p-6 sm:p-12 shadow-[0_40px_100px_rgba(0,0,0,0.95)]">
          {/* Ceiling Spotlight Cone falling down on the mantlepiece */}
          <div className="gallery-spotlight-cone" />

          {/* Velvet Curtains (Left and Right Panels) */}
          <div
            className={`absolute inset-0 z-30 pointer-events-none flex overflow-hidden transition-all duration-1000 ${
              curtainOpen ? "opacity-0 pointer-events-none" : "opacity-100"
            }`}
          >
            {/* Left Velvet Panel */}
            <div
              className={`w-1/2 h-full velvet-curtain border-r border-black/60 transition-transform duration-1000 ease-out origin-left ${
                curtainOpen ? "-translate-x-full" : "translate-x-0"
              }`}
            />
            {/* Right Velvet Panel */}
            <div
              className={`w-1/2 h-full velvet-curtain border-l border-black/60 transition-transform duration-1000 ease-out origin-right ${
                curtainOpen ? "translate-x-full" : "translate-x-0"
              }`}
            />
          </div>

          {/* Golden Cord & Pull Trigger (When curtain is closed) */}
          {!curtainOpen && (
            <div className="absolute inset-0 z-40 flex flex-col items-center justify-center p-6 text-center">
              <button
                onClick={() => setCurtainOpen(true)}
                className="group relative px-8 py-5 rounded bg-black/60 backdrop-blur-md border border-[#C29F6C]/40 hover:border-[#C29F6C] shadow-[0_20px_50px_rgba(0,0,0,0.9)] transition-all hover:scale-105"
              >
                <div className="flex items-center gap-3 mb-1">
                  <Sparkles className="w-4 h-4 text-[#C29F6C] animate-pulse" />
                  <span className="font-serif italic text-2xl text-[#F5EFE6] group-hover:text-[#C29F6C] transition-colors">
                    Draw the Velvet Curtain
                  </span>
                </div>
                <p className="text-[11px] text-[#8E8B82] tracking-wider uppercase">
                  Click to reveal the mantlepiece centerpiece
                </p>
              </button>
            </div>
          )}

          {/* Unveiled Mantlepiece Content (Behind the curtain) */}
          <div className="relative z-20 flex-1 flex flex-col justify-center items-center py-6">
            {/* The Masterpiece in Authentic Walnut Moulding Frame */}
            <div className="gilded-frame max-w-2xl w-full mb-8 group transition-transform duration-700">
              <div className="canvas-recess aspect-[16/11] relative overflow-hidden bg-black rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={activePiece.src}
                  alt={activePiece.title}
                  className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-[1.02]"
                />
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.03] to-transparent pointer-events-none" />
              </div>
            </div>

            {/* Classical Wooden Mantlepiece Shelf */}
            <div className="mantlepiece-shelf w-full max-w-3xl py-4 px-6 sm:px-8 rounded-sm flex flex-col sm:flex-row items-center justify-between gap-4">
              {/* Engraved Brass Nameplate on the Mantle */}
              <div className="text-center sm:text-left">
                <div className="text-[10px] tracking-[0.25em] text-[#C29F6C] uppercase font-semibold">
                  {activePiece.artist} · {activePiece.year}
                </div>
                <div className="font-serif italic text-xl sm:text-2xl text-[#F5EFE6]">
                  {activePiece.title}
                </div>
                <div className="text-[11px] text-[#8E8B82]">
                  {activePiece.medium} · {activePiece.dimensions}
                </div>
              </div>

              {/* Reset Curtain or Re-cover Button */}
              {curtainOpen && (
                <button
                  onClick={() => setCurtainOpen(false)}
                  className="text-[11px] tracking-wider uppercase text-[#8E8B82] hover:text-[#C29F6C] transition-colors border border-white/[0.1] hover:border-[#C29F6C]/40 px-3 py-1.5 rounded"
                >
                  Veil Again
                </button>
              )}
            </div>

            {/* Corbel supports under mantlepiece */}
            <div className="w-full max-w-3xl flex justify-between px-12 -mt-1">
              <div className="mantlepiece-corbel w-12 h-6 rounded-b" />
              <div className="mantlepiece-corbel w-12 h-6 rounded-b" />
            </div>
          </div>
        </div>

        {/* Masterpiece Gallery Selector Tabs */}
        <div className="mt-8 flex items-center justify-center gap-2 overflow-x-auto pb-4">
          {MASTERPIECES.map((piece) => {
            const isSelected = activePiece.id === piece.id;
            return (
              <button
                key={piece.id}
                onClick={() => setActivePiece(piece)}
                className={`px-4 py-2 rounded-full text-xs tracking-wide transition-all whitespace-nowrap ${
                  isSelected
                    ? "bg-[#C29F6C] text-[#07080B] font-semibold shadow-[0_0_20px_rgba(194,159,108,0.3)]"
                    : "border border-white/[0.08] text-[#8E8B82] hover:text-[#EFE9DF] hover:border-white/20"
                }`}
              >
                {piece.artist.split(" ").pop()} — <span className="font-serif italic">{piece.title}</span>
              </button>
            );
          })}
        </div>
      </section>

      {/* The Segue: How this mantlepiece becomes your morning desk */}
      <section
        id="the-segue"
        className="relative z-10 py-24 px-6 sm:px-12 border-t border-white/[0.06] bg-gradient-to-b from-[#07080B] via-[#0B0D12] to-[#07080B]"
      >
        <div className="max-w-4xl mx-auto text-center">
          <p className="font-serif italic text-lg sm:text-xl text-[#C29F6C] mb-3">
            The Segue
          </p>

          <h2 className="font-serif text-3xl sm:text-5xl md:text-6xl text-[#F5EFE6] font-normal leading-tight mb-6">
            Now, imagine this mantlepiece on your desk.
          </h2>

          <p className="font-serif text-base sm:text-lg text-[#8E8B82] font-light max-w-2xl mx-auto leading-relaxed mb-12">
            You spend hours each day in front of a glowing glass monitor. Usually, it is filled with cold digital noise.
            <br />
            <strong className="text-[#F5EFE6] font-normal">
              What if an authentic museum picture frame was painted on your screen every morning?
            </strong>
          </p>

          {/* Interactive Morph Slider */}
          <div className="inline-flex items-center bg-[#111319] p-1.5 rounded-full border border-white/[0.1] text-xs mb-12">
            <button
              onClick={() => setDisplayMode("mantle")}
              className={`flex items-center gap-2 px-5 py-2 rounded-full transition-all ${
                displayMode === "mantle"
                  ? "bg-[#C29F6C] text-[#07080B] font-semibold shadow-md"
                  : "text-[#8E8B82] hover:text-[#EFE9DF]"
              }`}
            >
              <Eye className="w-3.5 h-3.5" />
              <span>1. The Salon Exhibition</span>
            </button>
            <button
              onClick={() => setDisplayMode("desktop")}
              className={`flex items-center gap-2 px-5 py-2 rounded-full transition-all ${
                displayMode === "desktop"
                  ? "bg-[#C29F6C] text-[#07080B] font-semibold shadow-md"
                  : "text-[#8E8B82] hover:text-[#EFE9DF]"
              }`}
            >
              <Monitor className="w-3.5 h-3.5" />
              <span>2. Your Desktop Wallpaper (07:00 AM)</span>
            </button>
          </div>

          {/* Dynamic Manifestation Showcase */}
          <div className="relative max-w-4xl mx-auto rounded-xl border border-white/[0.12] bg-black p-4 shadow-[0_40px_120px_rgba(0,0,0,0.98)] overflow-hidden">
            {/* Screen Bezel Frame */}
            <div className="px-4 pb-3 flex items-center justify-between text-[11px] text-[#8E8B82] border-b border-white/[0.08]">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500/40" />
                <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/40" />
                <span className="w-2.5 h-2.5 rounded-full bg-green-500/40" />
              </div>
              <div className="font-serif text-[#C29F6C] text-xs">
                {displayMode === "mantle"
                  ? "The Physical Salon Wall View"
                  : "Physical Walnut Picture Frame with Ambient Bokeh Background"}
              </div>
              <div className="text-[10px] font-mono text-white/50">
                {displayMode === "mantle" ? "Museum Framing" : "Native OS Wallpaper Sync Active"}
              </div>
            </div>

            {/* Screen Canvas Surface */}
            <div className="aspect-[16/10] relative flex flex-col justify-between overflow-hidden">
              {/* If Desktop Mode: Ambient Gaussian Blur sampled from artwork */}
              {displayMode === "desktop" ? (
                <div
                  className="ambient-blur-bg"
                  style={{ backgroundImage: `url(${activePiece.src})` }}
                />
              ) : (
                <div className="absolute inset-0 bg-[#0F1117] flex items-center justify-center">
                  <div className="w-[600px] h-[350px] bg-gradient-to-b from-white/[0.03] to-transparent pointer-events-none" />
                </div>
              )}

              {/* Minimal Status Strip */}
              <div className="relative z-10 px-5 py-2.5 flex items-center justify-between text-[11px] text-white/80 bg-black/30 backdrop-blur-sm">
                <span>07:00 AM Daily Rotation</span>
                <span className="font-serif italic text-xs text-[#C29F6C]">
                  {activePiece.artist} — {activePiece.title}
                </span>
                <span className="text-[10px] text-white/50">The Met Collection</span>
              </div>

              {/* The Framed Masterpiece */}
              <div className="relative z-10 flex items-center justify-center p-6 flex-1">
                <div
                  className={`relative max-h-[85%] max-w-[85%] transition-all duration-700 ${
                    displayMode === "mantle"
                      ? "p-4 bg-[#1A1612] border-2 border-[#C29F6C]/60 shadow-[0_20px_50px_rgba(0,0,0,0.9)]"
                      : "shadow-[0_30px_80px_rgba(0,0,0,0.95)] border-2 border-[#4A2A1A] p-3 bg-[#1A0E08] rounded-sm"
                  }`}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={activePiece.src}
                    alt={activePiece.title}
                    className="max-h-[260px] sm:max-h-[360px] w-auto object-contain"
                  />
                </div>
              </div>

              {/* Desktop Workspace Dock */}
              <div className="relative z-10 h-10 px-6 flex items-center justify-between text-[10px] text-white/60 bg-black/50 backdrop-blur-md border-t border-white/[0.08]">
                <span>Art Wallpaper Client · Windows & macOS Native</span>
                <span className="text-[#C29F6C] font-mono">
                  Physical Walnut Frame · Silky Bokeh Background
                </span>
              </div>
            </div>
          </div>

          <p className="text-xs text-[#8E8B82] mt-6 max-w-lg mx-auto">
            {displayMode === "mantle"
              ? "In the museum, oil masterpieces hang against textured plaster walls."
              : "On your desktop, the painting floats framed in roasted walnut with ambient bokeh blur and zero taskbar obstruction."}
          </p>
        </div>
      </section>

      {/* Feature Architecture Matrix */}
      <section className="relative z-10 py-20 px-6 sm:px-12 border-t border-white/[0.06] bg-[#07080B]">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-16">
            <span className="font-serif tracking-widest text-[#C29F6C] uppercase text-xs font-semibold">
              Engine Specifications
            </span>
            <h2 className="font-serif text-3xl sm:text-4xl text-[#F5EFE6] font-normal mt-2">
              Architecture Built for High Performance
            </h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            <div className="p-6 rounded-lg bg-[#0B0D12] border border-white/[0.08] flex flex-col justify-between">
              <div>
                <div className="w-8 h-8 rounded-full bg-[#C29F6C]/10 flex items-center justify-center text-[#C29F6C] mb-4">
                  <Layers className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[#F5EFE6] mb-2">
                  Dual-Layer Picture Frame
                </h3>
                <p className="text-xs text-[#8E8B82] leading-relaxed">
                  Every wallpaper is generated at physical screen resolution with multi-level gallery drop shadows, roasted walnut wood moulding, gilded brass fillet, and archival sight-edge bevels.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[#C29F6C] mt-4 pt-4 border-t border-white/[0.06]">
                ImageProcessor Engine
              </div>
            </div>

            <div className="p-6 rounded-lg bg-[#0B0D12] border border-white/[0.08] flex flex-col justify-between">
              <div>
                <div className="w-8 h-8 rounded-full bg-[#C29F6C]/10 flex items-center justify-center text-[#C29F6C] mb-4">
                  <RefreshCw className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[#F5EFE6] mb-2">
                  Operating System Sync
                </h3>
                <p className="text-xs text-[#8E8B82] leading-relaxed">
                  Direct Windows 11 JNA registry and macOS AppleScript integration ensures in-memory UI and desktop wallpaper are synchronized immediately on startup and system wake.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[#C29F6C] mt-4 pt-4 border-t border-white/[0.06]">
                WallpaperService API
              </div>
            </div>

            <div className="p-6 rounded-lg bg-[#0B0D12] border border-white/[0.08] flex flex-col justify-between">
              <div>
                <div className="w-8 h-8 rounded-full bg-[#C29F6C]/10 flex items-center justify-center text-[#C29F6C] mb-4">
                  <HardDrive className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[#F5EFE6] mb-2">
                  Pruned Local Storage
                </h3>
                <p className="text-xs text-[#8E8B82] leading-relaxed">
                  Chronological descending storage with a strict 100-item cap, automatic orphan pruning, dedicated error logs, and startup installer cleanup.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[#C29F6C] mt-4 pt-4 border-t border-white/[0.06]">
                Zero Telemetry Architecture
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Epilogue: The Archival Invitation */}
      <section id="the-invitation" className="relative z-10 py-24 px-6 sm:px-12 border-t border-white/[0.06] bg-[#050608]">
        <div className="max-w-3xl mx-auto text-center">
          <p className="font-serif italic text-lg text-[#C29F6C] mb-2">
            The Permanent Collection
          </p>

          <h2 className="font-serif text-3xl sm:text-5xl text-[#F5EFE6] font-normal leading-tight mb-4">
            Four centuries of art. Free forever.
          </h2>

          <p className="text-xs font-mono text-[#C29F6C] uppercase tracking-widest mb-6">
            Release v4.1.5 · Production Ready
          </p>

          <p className="font-serif text-base text-[#8E8B82] leading-relaxed mb-12 max-w-xl mx-auto">
            Art Wallpaper is a non-commercial, open-source desktop companion. It collects no data, contains no advertisements, and quietly delivers authentic museum pieces to your screen.
          </p>

          {/* Download Editions */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-lg mx-auto mb-12">
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases/download/v4.1.5/ArtWallpaper-4.1.5-Windows.msi"
              className="p-5 rounded border border-white/[0.1] bg-[#0B0D12] hover:border-[#C29F6C] transition-all text-left group"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-serif text-lg text-[#F5EFE6] group-hover:text-[#C29F6C] transition-colors">
                  Windows Edition
                </span>
                <Download className="w-4 h-4 text-[#8E8B82] group-hover:text-[#C29F6C] transition-colors" />
              </div>
              <p className="text-[11px] text-[#8E8B82] leading-relaxed mb-3">
                Native Windows 10 & 11 (.msi) installer. Fluent Dark mode, JNA Win32 integration.
              </p>
              <div className="text-[10px] uppercase tracking-wider text-[#C29F6C] flex items-center gap-1 font-semibold">
                <span>Download MSI (v4.1.5)</span>
                <ArrowRight className="w-3 h-3 group-hover:translate-x-1 transition-transform" />
              </div>
            </a>

            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases/download/v4.1.5/ArtWallpaper-4.1.5-macOS.dmg"
              className="p-5 rounded border border-white/[0.1] bg-[#0B0D12] hover:border-[#C29F6C] transition-all text-left group"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-serif text-lg text-[#F5EFE6] group-hover:text-[#C29F6C] transition-colors">
                  macOS Edition
                </span>
                <Download className="w-4 h-4 text-[#8E8B82] group-hover:text-[#C29F6C] transition-colors" />
              </div>
              <p className="text-[11px] text-[#8E8B82] leading-relaxed mb-3">
                Native macOS DMG disk image. Apple Silicon & Intel support with LaunchAgents auto-start.
              </p>
              <div className="text-[10px] uppercase tracking-wider text-[#C29F6C] flex items-center gap-1 font-semibold">
                <span>Download DMG (v4.1.5)</span>
                <ArrowRight className="w-3 h-3 group-hover:translate-x-1 transition-transform" />
              </div>
            </a>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-5 text-xs text-[#8E8B82]">
            <span className="flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-[#C29F6C]" />
              Authentic Open Source
            </span>
            <span>·</span>
            <span>Zero Telemetry</span>
            <span>·</span>
            <span>Met Museum Open Access</span>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper"
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#C29F6C] hover:underline flex items-center gap-1"
            >
              <span>GitHub Repository</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </section>

      {/* Archival Colophon & Legal Footer */}
      <footer className="relative z-10 border-t border-white/[0.04] py-12 px-6 sm:px-12 bg-[#030406] text-xs text-[#8E8B82]/70">
        <div className="max-w-4xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6 text-center md:text-left">
          <div>
            <div className="font-serif tracking-widest text-[#C29F6C] uppercase text-xs font-semibold mb-1">
              Art Wallpaper Manager
            </div>
            <p className="text-[11px] text-[#8E8B82]">
              A non-commercial desktop gallery application. Maintained by Prince.
            </p>
            <p className="text-[10px] text-[#8E8B82]/60 mt-1">
              Public domain imagery courtesy of The Metropolitan Museum of Art Open Access Collection.
            </p>
          </div>

          <div className="flex flex-wrap items-center justify-center md:justify-end gap-5 text-[11px] text-[#8E8B82]">
            <Link href="/privacy" className="hover:text-[#C29F6C] transition-colors">
              Privacy Policy
            </Link>
            <span>·</span>
            <Link href="/terms" className="hover:text-[#C29F6C] transition-colors">
              Terms of Service
            </Link>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-[#C29F6C] transition-colors"
            >
              Releases
            </a>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/blob/master/CONTRIBUTING.md"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-[#C29F6C] transition-colors"
            >
              Contributing
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
