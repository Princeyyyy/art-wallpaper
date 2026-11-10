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
  Compass,
} from "lucide-react";
import { ThemeToggle } from "../components/ThemeProvider";
import { GalleryWalk, ALL_MASTERPIECES, Artwork } from "../components/GalleryWalk";
import { useLatestRelease } from "../hooks/useLatestRelease";

export default function GallerySalon() {
  const { tag, windowsMsiUrl, macDmgUrl } = useLatestRelease();
  const [curtainOpen, setCurtainOpen] = useState(false);
  const [activePiece, setActivePiece] = useState<Artwork>(ALL_MASTERPIECES[0]);
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
    <div className="relative min-h-screen bg-[var(--canvas-bg)] text-[var(--paper-white)] selection:bg-[var(--gallery-gold)] selection:text-[var(--canvas-bg)] overflow-x-hidden font-sans transition-colors duration-500">
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
      <header className="relative z-30 border-b border-[var(--border-subtle)] bg-[var(--surface-glass)] backdrop-blur-md px-6 sm:px-12 py-4 flex items-center justify-between transition-colors">
        <div className="flex items-center gap-3">
          <div className="w-2 h-2 rounded-full bg-[var(--gallery-gold)] animate-pulse-subtle" />
          <span className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold">
            The Private Salon · Masterpiece Installation
          </span>
          <span className="hidden sm:inline-block text-[10px] font-mono text-[var(--paper-muted)] bg-[var(--surface-elevated)] px-2 py-0.5 rounded border border-[var(--border-subtle)]">
            {tag}
          </span>
        </div>

        <nav className="flex items-center gap-4 sm:gap-6 text-xs tracking-wider">
          <a
            href="#exhibition"
            className="hidden md:inline-block text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors"
          >
            Mantlepiece
          </a>
          <a
            href="#gallery-promenade"
            className="hidden md:inline-block text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors"
          >
            Gallery Walk
          </a>
          <a
            href="#the-segue"
            className="hidden md:inline-block text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors"
          >
            Wallpaper Engine
          </a>
          <Link
            href="/privacy"
            className="hidden sm:inline-block text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors"
          >
            Privacy
          </Link>
          <Link
            href="/terms"
            className="hidden sm:inline-block text-[var(--paper-muted)] hover:text-[var(--paper-white)] transition-colors"
          >
            Terms
          </Link>

          {/* Three-Way Theme Switcher (Auto, Light, Dark) */}
          <ThemeToggle />

          <a
            href="#the-invitation"
            className="gilded-shimmer-btn text-[11px] uppercase tracking-wider text-[var(--gallery-gold)] border border-[var(--gallery-gold)]/40 hover:border-[var(--gallery-gold)] px-3 py-1.5 rounded transition-all font-medium"
          >
            Download {tag}
          </a>
        </nav>
      </header>

      {/* Prologue: The Invitation */}
      <section className="relative z-10 pt-20 pb-12 px-6 text-center max-w-3xl mx-auto">
        <p className="font-serif italic text-lg sm:text-xl text-[var(--gallery-gold)] mb-3">
          Behind the velvet drapery lies a four-hundred-year-old centerpiece.
        </p>

        <h1 className="font-serif text-4xl sm:text-6xl md:text-7xl font-normal text-[var(--paper-white)] leading-[1.08] mb-6">
          Pull the curtain.
        </h1>

        <p className="text-xs sm:text-sm text-[var(--paper-muted)] font-light max-w-xl mx-auto leading-relaxed">
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
        <div className="relative rounded-b-md bg-[var(--surface-bg)] border-x border-b border-[var(--border-subtle)] overflow-hidden min-h-[520px] sm:min-h-[640px] flex flex-col justify-between p-6 sm:p-12 shadow-[0_40px_100px_rgba(0,0,0,0.4)] transition-colors">
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
                className="group relative px-8 py-5 rounded bg-[var(--surface-bg)]/80 backdrop-blur-md border border-[var(--gallery-gold)]/50 hover:border-[var(--gallery-gold)] shadow-[0_20px_50px_rgba(0,0,0,0.6)] transition-all hover:scale-105 active:scale-95"
              >
                <div className="flex items-center gap-3 mb-1 justify-center">
                  <Sparkles className="w-4 h-4 text-[var(--gallery-gold)] animate-pulse" />
                  <span className="font-serif italic text-2xl text-[var(--paper-white)] group-hover:text-[var(--gallery-gold)] transition-colors">
                    Draw the Velvet Curtain
                  </span>
                </div>
                <p className="text-[11px] text-[var(--paper-muted)] tracking-wider uppercase">
                  Click to reveal the mantlepiece centerpiece
                </p>
              </button>
            </div>
          )}

          {/* Unveiled Mantlepiece Content (Behind the curtain) */}
          <div className="relative z-20 flex-1 flex flex-col justify-center items-center py-6">
            {/* The Masterpiece in Authentic Walnut Moulding Frame */}
            <div className="gilded-frame max-w-2xl w-full mb-8 group transition-transform duration-700">
              <div className="canvas-recess min-h-[260px] sm:min-h-[380px] max-h-[460px] relative overflow-hidden bg-black/95 rounded-sm flex items-center justify-center p-1 sm:p-2">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={activePiece.src}
                  alt={activePiece.title}
                  className="max-h-[250px] sm:max-h-[370px] md:max-h-[440px] w-auto max-w-full object-contain rounded-[1px] shadow-sm transition-transform duration-700 group-hover:scale-[1.01]"
                />
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.04] to-transparent pointer-events-none" />
              </div>
            </div>

            {/* Classical Wooden Mantlepiece Shelf */}
            <div className="mantlepiece-shelf w-full max-w-3xl py-4 px-6 sm:px-8 rounded-sm flex flex-col sm:flex-row items-center justify-between gap-4">
              {/* Engraved Brass Nameplate on the Mantle */}
              <div className="text-center sm:text-left">
                <div className="text-[10px] tracking-[0.25em] text-[var(--gallery-gold)] uppercase font-semibold">
                  {activePiece.wing} · {activePiece.artist} · {activePiece.year}
                </div>
                <div className="font-serif italic text-xl sm:text-2xl text-[var(--paper-white)]">
                  {activePiece.title}
                </div>
                <div className="text-[11px] text-[var(--paper-muted)]">
                  {activePiece.medium} · {activePiece.dimensions}
                </div>
              </div>

              {/* Action Buttons: Veil or Explore Gallery Walk */}
              <div className="flex items-center gap-3">
                <a
                  href="#gallery-promenade"
                  className="gilded-shimmer-btn flex items-center gap-1.5 text-[11px] tracking-wider uppercase text-[var(--gallery-gold)] border border-[var(--gallery-gold)]/40 hover:border-[var(--gallery-gold)] px-3 py-1.5 rounded transition-colors"
                >
                  <Compass className="w-3.5 h-3.5" />
                  <span>Stroll Gallery</span>
                </a>
                {curtainOpen && (
                  <button
                    onClick={() => setCurtainOpen(false)}
                    className="text-[11px] tracking-wider uppercase text-[var(--paper-muted)] hover:text-[var(--gallery-gold)] transition-colors border border-[var(--border-subtle)] hover:border-[var(--gallery-gold)]/40 px-3 py-1.5 rounded"
                  >
                    Veil Again
                  </button>
                )}
              </div>
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
          {ALL_MASTERPIECES.slice(0, 7).map((piece) => {
            const isSelected = activePiece.id === piece.id;
            return (
              <button
                key={piece.id}
                onClick={() => {
                  setActivePiece(piece);
                  setCurtainOpen(true);
                }}
                className={`px-4 py-2 rounded-full text-xs tracking-wide transition-all duration-300 whitespace-nowrap ${
                  isSelected
                    ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-[0_0_20px_var(--gallery-gold-glow)]"
                    : "border border-[var(--border-subtle)] bg-[var(--surface-elevated)] text-[var(--paper-muted)] hover:text-[var(--paper-white)] hover:border-[var(--gallery-gold)]/40"
                }`}
              >
                {piece.artist.split(" ").pop()} — <span className="font-serif italic">{piece.title}</span>
              </button>
            );
          })}
        </div>
      </section>

      {/* ── FLAGSHIP: Fluid Dynamics & 3D Spatial Gallery Walk Promenade ── */}
      <GalleryWalk onSelectForMantlepiece={(piece) => setActivePiece(piece)} />

      {/* The Segue: How this mantlepiece becomes your morning desk */}
      <section
        id="the-segue"
        className="relative z-10 py-24 px-6 sm:px-12 border-t border-[var(--border-subtle)] bg-[var(--canvas-bg)] transition-colors"
      >
        <div className="max-w-4xl mx-auto text-center">
          <p className="font-serif italic text-lg sm:text-xl text-[var(--gallery-gold)] mb-3">
            The Segue
          </p>

          <h2 className="font-serif text-3xl sm:text-5xl md:text-6xl text-[var(--paper-white)] font-normal leading-tight mb-6">
            Now, imagine this mantlepiece on your desk.
          </h2>

          <p className="font-serif text-base sm:text-lg text-[var(--paper-muted)] font-light max-w-2xl mx-auto leading-relaxed mb-12">
            You spend hours each day in front of a glowing glass monitor. Usually, it is filled with cold digital noise.
            <br />
            <strong className="text-[var(--paper-white)] font-normal">
              What if an authentic museum picture frame was painted on your screen every morning?
            </strong>
          </p>

          {/* Interactive Morph Slider */}
          <div className="inline-flex items-center bg-[var(--surface-elevated)] p-1.5 rounded-full border border-[var(--border-subtle)] text-xs mb-12 shadow-sm">
            <button
              onClick={() => setDisplayMode("mantle")}
              className={`flex items-center gap-2 px-5 py-2 rounded-full transition-all duration-300 ${
                displayMode === "mantle"
                  ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-md"
                  : "text-[var(--paper-muted)] hover:text-[var(--paper-white)]"
              }`}
            >
              <Eye className="w-3.5 h-3.5" />
              <span>1. The Salon Exhibition</span>
            </button>
            <button
              onClick={() => setDisplayMode("desktop")}
              className={`flex items-center gap-2 px-5 py-2 rounded-full transition-all duration-300 ${
                displayMode === "desktop"
                  ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-md"
                  : "text-[var(--paper-muted)] hover:text-[var(--paper-white)]"
              }`}
            >
              <Monitor className="w-3.5 h-3.5" />
              <span>2. Your Desktop Wallpaper (07:00 AM)</span>
            </button>
          </div>

          {/* Dynamic Manifestation Showcase */}
          <div className="relative max-w-4xl mx-auto rounded-xl border border-[var(--border-subtle)] bg-black p-4 shadow-[0_40px_120px_rgba(0,0,0,0.6)] overflow-hidden">
            {/* Screen Bezel Frame */}
            <div className="px-4 pb-3 flex items-center justify-between text-[11px] text-white/70 border-b border-white/[0.08]">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500/50" />
                <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/50" />
                <span className="w-2.5 h-2.5 rounded-full bg-green-500/50" />
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
              <div className="relative z-10 px-5 py-2.5 flex items-center justify-between text-[11px] text-white/80 bg-black/40 backdrop-blur-sm">
                <span>07:00 AM Daily Rotation</span>
                <span className="font-serif italic text-xs text-[#C29F6C]">
                  {activePiece.artist} — {activePiece.title}
                </span>
                <span className="text-[10px] text-white/50">The Met Collection</span>
              </div>

              {/* The Framed Masterpiece strictly inside its physical moulding */}
              <div className="relative z-10 flex-1 flex items-center justify-center p-4 sm:p-6 min-h-0">
                <div className="relative inline-flex items-center justify-center transition-transform duration-700 hover:scale-[1.01]">
                  {/* Multi-Level Museum Frame */}
                  <div
                    className={`relative transition-all duration-700 flex items-center justify-center ${
                      displayMode === "mantle"
                        ? "p-4 sm:p-5 rounded-[4px] bg-gradient-to-br from-[#382618] via-[#22170E] to-[#120C07] shadow-[0_25px_60px_rgba(0,0,0,0.95)] ring-1 ring-[#D4AF37]/50"
                        : "p-3 sm:p-4 rounded-[3px] bg-gradient-to-br from-[#4A2E1C] via-[#2C1A0F] to-[#160D07] shadow-[0_25px_65px_rgba(0,0,0,0.95)] ring-1 ring-[#C8A97E]/70"
                    }`}
                  >
                    {/* Gilded Brass Fillet Inlay Bead */}
                    <div className="relative p-1 rounded-[2px] bg-[#16120D] ring-1 ring-[#D4AF37]/70 shadow-[inset_0_1px_3px_rgba(0,0,0,0.9)]">
                      {/* Archival Sight-Edge Bevel Recess */}
                      <div className="relative overflow-hidden rounded-[1px] shadow-[inset_0_3px_12px_rgba(0,0,0,0.95)] bg-black/90 flex items-center justify-center">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={activePiece.src}
                          alt={activePiece.title}
                          className="block w-auto h-auto max-h-[170px] sm:max-h-[230px] md:max-h-[260px] max-w-[75vw] sm:max-w-[420px] object-contain select-none shadow-sm"
                        />
                        {/* Specular oil canvas varnish sheen */}
                        <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.04] to-transparent pointer-events-none" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Desktop Workspace Dock */}
              <div className="relative z-10 h-10 px-6 flex items-center justify-between text-[10px] text-white/60 bg-black/60 backdrop-blur-md border-t border-white/[0.08]">
                <span>Art Wallpaper Client · Windows & macOS Native</span>
                <span className="text-[#C29F6C] font-mono">
                  Physical Walnut Frame · Silky Bokeh Background
                </span>
              </div>
            </div>
          </div>

          <p className="text-xs text-[var(--paper-muted)] mt-6 max-w-lg mx-auto">
            {displayMode === "mantle"
              ? "In the museum, oil masterpieces hang against textured plaster walls."
              : "On your desktop, the painting floats framed in roasted walnut with ambient bokeh blur and zero taskbar obstruction."}
          </p>
        </div>
      </section>

      {/* Feature Architecture Matrix */}
      <section className="relative z-10 py-20 px-6 sm:px-12 border-t border-[var(--border-subtle)] bg-[var(--surface-bg)] transition-colors">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-16">
            <span className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold">
              Engine Specifications
            </span>
            <h2 className="font-serif text-3xl sm:text-4xl text-[var(--paper-white)] font-normal mt-2">
              Architecture Built for High Performance
            </h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            <div className="p-6 rounded-lg bg-[var(--surface-elevated)] border border-[var(--border-subtle)] flex flex-col justify-between hover:border-[var(--gallery-gold)]/40 transition-all duration-300 hover:-translate-y-1 shadow-sm">
              <div>
                <div className="w-8 h-8 rounded-full bg-[var(--gallery-gold)]/10 flex items-center justify-center text-[var(--gallery-gold)] mb-4">
                  <Layers className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[var(--paper-white)] mb-2">
                  Dual-Layer Picture Frame
                </h3>
                <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
                  Every wallpaper is generated at physical screen resolution with multi-level gallery drop shadows, roasted walnut wood moulding, gilded brass fillet, and archival sight-edge bevels.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[var(--gallery-gold)] mt-4 pt-4 border-t border-[var(--border-subtle)]">
                ImageProcessor Engine
              </div>
            </div>

            <div className="p-6 rounded-lg bg-[var(--surface-elevated)] border border-[var(--border-subtle)] flex flex-col justify-between hover:border-[var(--gallery-gold)]/40 transition-all duration-300 hover:-translate-y-1 shadow-sm">
              <div>
                <div className="w-8 h-8 rounded-full bg-[var(--gallery-gold)]/10 flex items-center justify-center text-[var(--gallery-gold)] mb-4">
                  <RefreshCw className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[var(--paper-white)] mb-2">
                  Operating System Sync
                </h3>
                <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
                  Direct Windows 11 JNA registry and macOS AppleScript integration ensures in-memory UI and desktop wallpaper are synchronized immediately on startup and system wake.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[var(--gallery-gold)] mt-4 pt-4 border-t border-[var(--border-subtle)]">
                WallpaperService API
              </div>
            </div>

            <div className="p-6 rounded-lg bg-[var(--surface-elevated)] border border-[var(--border-subtle)] flex flex-col justify-between hover:border-[var(--gallery-gold)]/40 transition-all duration-300 hover:-translate-y-1 shadow-sm">
              <div>
                <div className="w-8 h-8 rounded-full bg-[var(--gallery-gold)]/10 flex items-center justify-center text-[var(--gallery-gold)] mb-4">
                  <HardDrive className="w-4 h-4" />
                </div>
                <h3 className="font-serif text-lg text-[var(--paper-white)] mb-2">
                  Pruned Local Storage
                </h3>
                <p className="text-xs text-[var(--paper-muted)] leading-relaxed">
                  Chronological descending storage with a strict 100-item cap, automatic orphan pruning, dedicated error logs, and startup installer cleanup.
                </p>
              </div>
              <div className="text-[10px] font-mono text-[var(--gallery-gold)] mt-4 pt-4 border-t border-[var(--border-subtle)]">
                Zero Telemetry Architecture
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Epilogue: The Archival Invitation */}
      <section
        id="the-invitation"
        className="relative z-10 py-24 px-6 sm:px-12 border-t border-[var(--border-subtle)] bg-[var(--canvas-bg)] transition-colors"
      >
        <div className="max-w-3xl mx-auto text-center">
          <p className="font-serif italic text-lg text-[var(--gallery-gold)] mb-2">
            The Permanent Collection
          </p>

          <h2 className="font-serif text-3xl sm:text-5xl text-[var(--paper-white)] font-normal leading-tight mb-4">
            Four centuries of art. Free forever.
          </h2>

          <p className="text-xs font-mono text-[var(--gallery-gold)] uppercase tracking-widest mb-6">
            Release {tag} · Production Ready
          </p>

          <p className="font-serif text-base text-[var(--paper-muted)] leading-relaxed mb-12 max-w-xl mx-auto">
            Art Wallpaper is a non-commercial, open-source desktop companion. It collects no data, contains no advertisements, and quietly delivers authentic museum pieces to your screen.
          </p>

          {/* Download Editions */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-lg mx-auto mb-12">
            <a
              href={windowsMsiUrl}
              className="gilded-shimmer-btn p-5 rounded border border-[var(--border-subtle)] bg-[var(--surface-elevated)] hover:border-[var(--gallery-gold)] transition-all text-left group shadow-sm hover:-translate-y-1"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-serif text-lg text-[var(--paper-white)] group-hover:text-[var(--gallery-gold)] transition-colors">
                  Windows Edition
                </span>
                <Download className="w-4 h-4 text-[var(--paper-muted)] group-hover:text-[var(--gallery-gold)] transition-colors" />
              </div>
              <p className="text-[11px] text-[var(--paper-muted)] leading-relaxed mb-3">
                Native Windows 10 & 11 (.msi) installer. Fluent Dark & Light modes, JNA Win32 integration.
              </p>
              <div className="text-[10px] uppercase tracking-wider text-[var(--gallery-gold)] flex items-center gap-1 font-semibold">
                <span>Download MSI ({tag})</span>
                <ArrowRight className="w-3 h-3 group-hover:translate-x-1 transition-transform" />
              </div>
            </a>

            <a
              href={macDmgUrl}
              className="gilded-shimmer-btn p-5 rounded border border-[var(--border-subtle)] bg-[var(--surface-elevated)] hover:border-[var(--gallery-gold)] transition-all text-left group shadow-sm hover:-translate-y-1"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="font-serif text-lg text-[var(--paper-white)] group-hover:text-[var(--gallery-gold)] transition-colors">
                  macOS Edition
                </span>
                <Download className="w-4 h-4 text-[var(--paper-muted)] group-hover:text-[var(--gallery-gold)] transition-colors" />
              </div>
              <p className="text-[11px] text-[var(--paper-muted)] leading-relaxed mb-3">
                Native macOS DMG disk image. Apple Silicon & Intel support with LaunchAgents auto-start.
              </p>
              <div className="text-[10px] uppercase tracking-wider text-[var(--gallery-gold)] flex items-center gap-1 font-semibold">
                <span>Download DMG ({tag})</span>
                <ArrowRight className="w-3 h-3 group-hover:translate-x-1 transition-transform" />
              </div>
            </a>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-5 text-xs text-[var(--paper-muted)]">
            <span className="flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-[var(--gallery-gold)]" />
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
              className="text-[var(--gallery-gold)] hover:underline flex items-center gap-1"
            >
              <span>GitHub Repository</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </section>

      {/* Archival Colophon & Legal Footer */}
      <footer className="relative z-10 border-t border-[var(--border-subtle)] py-12 px-6 sm:px-12 bg-[var(--canvas-bg)] text-xs text-[var(--paper-muted)] transition-colors">
        <div className="max-w-4xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6 text-center md:text-left">
          <div>
            <div className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold mb-1">
              Art Wallpaper Manager
            </div>
            <p className="text-[11px] text-[var(--paper-muted)]">
              A non-commercial desktop gallery application. Maintained by Prince.
            </p>
            <p className="text-[10px] text-[var(--paper-dim)] mt-1">
              Public domain imagery courtesy of The Metropolitan Museum of Art Open Access Collection.
            </p>
          </div>

          <div className="flex flex-wrap items-center justify-center md:justify-end gap-5 text-[11px] text-[var(--paper-muted)]">
            <Link href="/privacy" className="hover:text-[var(--gallery-gold)] transition-colors">
              Privacy Policy
            </Link>
            <span>·</span>
            <Link href="/terms" className="hover:text-[var(--gallery-gold)] transition-colors">
              Terms of Service
            </Link>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-[var(--gallery-gold)] transition-colors"
            >
              Releases
            </a>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/blob/master/CONTRIBUTING.md"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-[var(--gallery-gold)] transition-colors"
            >
              Contributing
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
