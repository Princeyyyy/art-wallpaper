"use client";

import React, { useState, useEffect } from "react";
import { Volume2, VolumeX, Download, ArrowRight, Eye, Shield, Sparkles } from "lucide-react";

interface Painting {
  id: string;
  title: string;
  artist: string;
  bio: string;
  date: string;
  medium: string;
  wing: string;
  accession: string;
  quote: string;
  src: string;
}

const GALLERY_PIECES: Painting[] = [
  {
    id: "wheat-field",
    title: "Wheat Field with Cypresses",
    artist: "Vincent van Gogh",
    bio: "Dutch, Zundert 1853–1890 Auvers-sur-Oise",
    date: "1889",
    medium: "Oil on canvas · 73 × 93.4 cm",
    wing: "European Paintings · Gallery 825",
    accession: "1993.400.1",
    quote: "Cypresses are always occupying my thoughts... It is a splash of black in a sunny landscape, but it is one of the most interesting black notes.",
    src: "/artworks/wheat-field.jpg",
  },
  {
    id: "vermeer-pitcher",
    title: "Young Woman with a Water Pitcher",
    artist: "Johannes Vermeer",
    bio: "Dutch, Delft 1632–1675 Delft",
    date: "ca. 1662",
    medium: "Oil on canvas · 45.7 × 40.6 cm",
    wing: "European Paintings · Gallery 630",
    accession: "89.15.21",
    quote: "Morning light filters through leaded glass onto polished brass and Dutch linen, capturing a solitary moment of quiet contemplation.",
    src: "/artworks/vermeer-water-pitcher.jpg",
  },
  {
    id: "rembrandt-self",
    title: "Self-Portrait",
    artist: "Rembrandt van Rijn",
    bio: "Dutch, Leiden 1606–1669 Amsterdam",
    date: "1660",
    medium: "Oil on canvas · 80.3 × 67.3 cm",
    wing: "European Paintings · Gallery 632",
    accession: "14.40.618",
    quote: "Painted in Rembrandt's late fifty-fourth year with unflinching honesty — sculptural impasto and luminous chiaroscuro.",
    src: "/artworks/rembrandt-self-portrait.jpg",
  },
  {
    id: "irises",
    title: "Irises",
    artist: "Vincent van Gogh",
    bio: "Dutch, Zundert 1853–1890 Auvers-sur-Oise",
    date: "1890",
    medium: "Oil on canvas · 73.7 × 92.1 cm",
    wing: "European Paintings · Gallery 825",
    accession: "58.187",
    quote: "Seeking an effect of harmonious and restful intensity, Van Gogh contrasted the rich violet blossoms against soft chartreuse foliage.",
    src: "/artworks/irises.jpg",
  },
  {
    id: "cypresses",
    title: "Cypresses",
    artist: "Vincent van Gogh",
    bio: "Dutch, Zundert 1853–1890 Auvers-sur-Oise",
    date: "1889",
    medium: "Oil on canvas · 93.4 × 74 cm",
    wing: "European Paintings · Gallery 825",
    accession: "49.30",
    quote: "Vertical, monumental silhouettes painted during Van Gogh's stay in Saint-Rémy — trees like Egyptian obelisks touching the summer sky.",
    src: "/artworks/cypresses.jpg",
  },
];

export default function GalleryWalkthrough() {
  const [selectedPiece, setSelectedPiece] = useState<Painting>(GALLERY_PIECES[0]);
  const [isInspecting, setIsInspecting] = useState(false);
  const [mousePos, setMousePos] = useState({ x: 50, y: 35 });
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [audioCtx, setAudioCtx] = useState<AudioContext | null>(null);

  // Spotlight follows mouse cursor
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

  // Ambient procedural gallery acoustics
  const toggleAudio = () => {
    if (!audioCtx) {
      const ctx = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
      setAudioCtx(ctx);
      startDrone(ctx);
      setIsPlayingAudio(true);
    } else {
      if (isPlayingAudio) {
        audioCtx.suspend();
        setIsPlayingAudio(false);
      } else {
        audioCtx.resume();
        setIsPlayingAudio(true);
      }
    }
  };

  const startDrone = (ctx: AudioContext) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    const filter = ctx.createBiquadFilter();

    osc.type = "sine";
    osc.frequency.setValueAtTime(55, ctx.currentTime); // Low A1 resonance

    filter.type = "lowpass";
    filter.frequency.setValueAtTime(160, ctx.currentTime);

    gain.gain.setValueAtTime(0.035, ctx.currentTime);

    osc.connect(filter);
    filter.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
  };

  return (
    <main className="relative min-h-screen bg-gallery-obsidian text-gallery-vellum overflow-x-hidden selection:bg-gallery-gold selection:text-gallery-obsidian">
      
      {/* ── Cursor Spotlight Beam ────────────────────────────────────────── */}
      <div
        className="gallery-spotlight"
        style={{
          "--mouse-x": `${mousePos.x}%`,
          "--mouse-y": `${mousePos.y}%`,
        } as React.CSSProperties}
      />

      {/* ── Subtle Film Grain Layer ─────────────────────────────────────── */}
      <div className="fixed inset-0 film-grain pointer-events-none z-0" />

      {/* ── Top Museum Header (Pure Editorial, No SaaS Chrome) ──────────── */}
      <header className="relative z-20 border-b border-gallery-border/40 backdrop-blur-md bg-gallery-obsidian/75 sticky top-0 px-8 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-2.5 h-2.5 rounded-full bg-gallery-gold" />
          <span className="font-display text-xs tracking-[0.28em] uppercase text-gallery-gold font-semibold">
            The Metropolitan Museum · Desktop Gallery Wing
          </span>
        </div>

        <div className="flex items-center gap-4">
          <button
            onClick={toggleAudio}
            className="flex items-center gap-2 px-3 py-1.5 rounded-full border border-gallery-border/60 hover:border-gallery-gold/80 text-xs text-gallery-dim hover:text-gallery-gold transition-all"
            title="Toggle Gallery Acoustics"
          >
            {isPlayingAudio ? (
              <>
                <Volume2 className="w-3.5 h-3.5 text-gallery-gold" />
                <span className="text-[11px] font-mono tracking-wider">Acoustics: On</span>
              </>
            ) : (
              <>
                <VolumeX className="w-3.5 h-3.5" />
                <span className="text-[11px] font-mono tracking-wider">Acoustics: Off</span>
              </>
            )}
          </button>

          <a
            href="#the-desk"
            className="hidden sm:inline-flex items-center gap-2 text-xs font-mono tracking-widest text-gallery-gold hover:text-gallery-goldLight transition-colors uppercase border-b border-gallery-gold/40 hover:border-gallery-gold pb-0.5"
          >
            Bring Gallery Home
            <ArrowRight className="w-3 h-3" />
          </a>
        </div>
      </header>

      {/* ── Act I: Entrance to the Exhibition ───────────────────────────── */}
      <section className="relative z-10 pt-28 pb-20 px-6 sm:px-12 text-center max-w-4xl mx-auto">
        <span className="text-xs font-display tracking-[0.35em] uppercase text-gallery-gold/90 block mb-6">
          Exhibition Entry · Special Installation
        </span>

        <h1 className="font-serif italic text-4xl sm:text-6xl md:text-7xl font-normal leading-[1.1] text-gallery-vellum mb-8">
          Step softly into the gallery.
        </h1>

        <p className="font-sans text-sm sm:text-base text-gallery-dim font-light max-w-2xl mx-auto leading-relaxed">
          The heavy oak doors close behind you. The city fades. Ahead lie four centuries of oil, linen, light, and quiet contemplation.
        </p>

        <div className="w-12 h-[1px] bg-gallery-gold/40 mx-auto mt-12" />
      </section>

      {/* ── Act II: The Gallery Rooms (Walkthrough) ─────────────────────── */}
      <section className="relative z-10 py-16 px-6 sm:px-12 max-w-7xl mx-auto">
        
        {/* Gallery Corridor Navigation Pills */}
        <div className="flex items-center justify-center gap-2 sm:gap-4 overflow-x-auto pb-8 mb-12 scrollbar-none">
          {GALLERY_PIECES.map((piece) => {
            const isSelected = selectedPiece.id === piece.id;
            return (
              <button
                key={piece.id}
                onClick={() => setSelectedPiece(piece)}
                className={`whitespace-nowrap px-4 py-2 rounded-full text-xs tracking-wider transition-all font-sans ${
                  isSelected
                    ? "bg-gallery-gold text-gallery-obsidian font-semibold shadow-[0_0_20px_rgba(200,169,126,0.3)]"
                    : "border border-gallery-border/60 text-gallery-dim hover:text-gallery-gold hover:border-gallery-gold/60"
                }`}
              >
                {piece.artist.split(" ").pop()} — <span className="italic">{piece.title}</span>
              </button>
            );
          })}
        </div>

        {/* The Exhibition Wall & Gilded Frame */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          
          {/* Main Museum Wall Frame */}
          <div className="lg:col-span-8 flex justify-center">
            <div
              onClick={() => setIsInspecting(!isInspecting)}
              className="museum-frame group cursor-pointer transition-all duration-700 hover:scale-[1.01] max-w-3xl w-full"
            >
              <div className="museum-canvas aspect-[16/11] relative rounded-sm bg-black">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={selectedPiece.src}
                  alt={selectedPiece.title}
                  className="w-full h-full object-cover transition-all duration-700 group-hover:brightness-105"
                />

                {/* Ambient Oil Canvas Varnish Reflection */}
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.03] to-transparent pointer-events-none" />

                {/* Inspect Button Pill */}
                <div className="absolute bottom-4 right-4 px-3 py-1.5 rounded-full backdrop-blur-md bg-black/60 border border-white/20 text-[11px] text-white/80 flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
                  <Eye className="w-3 h-3 text-gallery-gold" />
                  <span>Click to contemplate</span>
                </div>
              </div>
            </div>
          </div>

          {/* Authentic Museum Wall Plaque */}
          <div className="lg:col-span-4">
            <div className="museum-placard p-8 rounded">
              <span className="text-[10px] uppercase font-mono tracking-widest text-gallery-gold block mb-4">
                {selectedPiece.wing}
              </span>

              <h2 className="font-display text-2xl text-gallery-vellum font-semibold mb-1">
                {selectedPiece.artist}
              </h2>
              <p className="text-xs text-gallery-dim mb-4 font-light">
                {selectedPiece.bio}
              </p>

              <h3 className="font-serif italic text-3xl text-gallery-gold mb-2 leading-tight">
                {selectedPiece.title}
              </h3>
              <p className="text-xs font-mono text-gallery-vellum/75 mb-6">
                {selectedPiece.date} · {selectedPiece.medium}
              </p>

              <blockquote className="text-xs text-gallery-dim font-light leading-relaxed border-t border-gallery-border/60 pt-4 mb-6 italic">
                &ldquo;{selectedPiece.quote}&rdquo;
              </blockquote>

              <div className="text-[10px] font-mono text-gallery-dim/60 space-y-1 border-t border-gallery-border/40 pt-4">
                <div>Permanent Collection · The Metropolitan Museum of Art</div>
                <div>Accession: {selectedPiece.accession} · Open Access</div>
              </div>
            </div>
          </div>

        </div>

      </section>

      {/* ── Act III: The Culmination / The Desktop Reveal ("BAM") ─────────── */}
      <section id="the-desk" className="relative z-10 py-32 border-t border-gallery-border/50 bg-gradient-to-b from-gallery-stone/30 via-gallery-obsidian to-gallery-obsidian">
        <div className="max-w-5xl mx-auto px-6 text-center">
          
          <span className="text-xs font-display tracking-[0.35em] uppercase text-gallery-gold/80 block mb-4">
            The Resolution
          </span>

          <h2 className="font-serif italic text-4xl sm:text-6xl md:text-7xl text-gallery-vellum font-normal leading-tight mb-8">
            Why must you leave the gallery behind?
          </h2>

          <p className="font-sans text-sm sm:text-base text-gallery-dim font-light max-w-2xl mx-auto leading-relaxed mb-16">
            You don&apos;t have to wait for your next museum trip. At your chosen hour every morning, Art Wallpaper quietly hangs a new authenticated masterpiece on your desk — perfectly matted, completely undistorted, and free forever.
          </p>

          {/* ── Floating Laptop Simulator with Ambient Blur Matting ─────────── */}
          <div className="relative max-w-4xl mx-auto rounded-xl border border-gallery-border/80 bg-[#0F1116] p-4 shadow-[0_40px_100px_rgba(0,0,0,0.9)] mb-16">
            
            {/* Screen Bezel Header */}
            <div className="flex items-center justify-between px-3 pb-3 text-[11px] text-gallery-dim font-mono border-b border-gallery-border/40">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500/60" />
                <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/60" />
                <span className="w-2.5 h-2.5 rounded-full bg-green-500/60" />
              </div>
              <div className="text-[10px] uppercase tracking-wider text-gallery-gold flex items-center gap-1.5">
                <Sparkles className="w-3 h-3 text-gallery-gold animate-pulse" />
                Your Desktop, Transformed by Art Wallpaper
              </div>
              <div className="text-[10px]">Ambient Blur Mode Active</div>
            </div>

            {/* Screen Surface */}
            <div className="ambient-blur-container aspect-[16/10] relative rounded-b flex flex-col justify-between overflow-hidden">
              
              {/* Gaussian Bokeh Background sampled from the painting */}
              <div
                className="ambient-blur-bg"
                style={{ backgroundImage: `url(${selectedPiece.src})` }}
              />

              {/* Status bar */}
              <div className="relative z-10 flex items-center justify-between px-4 py-2 text-[10px] text-white/70 font-sans backdrop-blur-sm bg-black/25">
                <span>9:41 AM · Monday</span>
                <span className="text-gallery-gold font-mono text-[9px] uppercase tracking-widest">
                  Art Wallpaper Active in Tray
                </span>
              </div>

              {/* Foreground Canvas with Museum Matting & Drop Shadow */}
              <div className="relative z-10 flex items-center justify-center p-6 flex-1">
                <div className="relative max-h-[82%] max-w-[80%] shadow-[0_25px_60px_rgba(0,0,0,0.95)] border border-white/20 rounded-sm overflow-hidden">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={selectedPiece.src}
                    alt={selectedPiece.title}
                    className="max-h-[260px] sm:max-h-[360px] w-auto object-contain"
                  />
                </div>
              </div>

              {/* Taskbar Clearance */}
              <div className="relative z-10 h-9 backdrop-blur-md bg-black/45 border-t border-white/10 flex items-center justify-between px-4 text-[10px] text-white/60">
                <div className="flex items-center gap-3">
                  <span className="text-white/80">Workspace</span>
                  <span>Terminal</span>
                  <span>Browser</span>
                </div>
                <div className="text-gallery-gold/80 font-mono text-[9px]">
                  0% Stretch · Authentic Aspect Ratio
                </div>
              </div>

            </div>

          </div>

          {/* ── Dual Pure Download Actions (No SaaS, No Pricing, Free) ───────── */}
          <div className="max-w-xl mx-auto flex flex-col sm:flex-row items-center justify-center gap-5 mb-12">
            
            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases/latest"
              className="w-full sm:w-1/2 py-4 px-6 rounded border border-gallery-gold bg-gallery-gold text-gallery-obsidian font-bold text-xs tracking-[0.2em] uppercase hover:bg-gallery-goldLight transition-all shadow-[0_0_30px_rgba(200,169,126,0.35)] flex items-center justify-center gap-2 group"
            >
              <Download className="w-4 h-4 group-hover:-translate-y-0.5 transition-transform" />
              <span>Download for Windows</span>
            </a>

            <a
              href="https://github.com/Princeyyyy/art-wallpaper/releases/latest"
              className="w-full sm:w-1/2 py-4 px-6 rounded border border-gallery-border bg-gallery-stone hover:border-gallery-gold/80 text-gallery-vellum hover:text-gallery-gold font-bold text-xs tracking-[0.2em] uppercase transition-all flex items-center justify-center gap-2 group"
            >
              <Download className="w-4 h-4 group-hover:-translate-y-0.5 transition-transform" />
              <span>Download for macOS</span>
            </a>

          </div>

          <div className="text-xs text-gallery-dim/70 flex flex-wrap items-center justify-center gap-6 font-light">
            <span className="flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-gallery-gold" />
              100% Free & Open Source
            </span>
            <span>·</span>
            <span>Zero Telemetry</span>
            <span>·</span>
            <span>The Met Open Access</span>
            <span>·</span>
            <a
              href="https://github.com/Princeyyyy/art-wallpaper"
              className="text-gallery-gold hover:underline"
            >
              GitHub Source Code
            </a>
          </div>

        </div>
      </section>

      {/* ── Act IV: Quiet Museum Colophon (Footer) ───────────────────────── */}
      <footer className="relative z-10 border-t border-gallery-border/40 py-12 px-6 sm:px-12 bg-[#040507] text-xs text-gallery-dim font-light">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-6 text-center sm:text-left">
          
          <div>
            <div className="font-display tracking-[0.2em] uppercase text-gallery-gold font-semibold mb-1">
              Art Wallpaper
            </div>
            <p className="text-[11px] text-gallery-dim/80">
              An open-source tribute to four centuries of fine art. Maintained by Prince.
            </p>
          </div>

          <div className="text-xs text-gallery-dim/60 space-y-1">
            <p>
              Artworks delivered by{" "}
              <a href="https://www.metmuseum.org" className="text-gallery-gold hover:underline">
                The Metropolitan Museum of Art Open Access Collection
              </a>
              .
            </p>
            <p>
              Code signing graciously supported by the{" "}
              <a href="https://signpath.org" className="text-gallery-gold hover:underline">
                SignPath Foundation
              </a>
              .
            </p>
          </div>

        </div>
      </footer>

    </main>
  );
}
