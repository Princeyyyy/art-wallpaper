"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Compass,
  Play,
  Pause,
  ExternalLink,
  Sparkles,
  Info,
  Maximize2,
} from "lucide-react";
import { FluidAtmosphereCanvas } from "./FluidCanvas";

export interface Artwork {
  id: string;
  title: string;
  artist: string;
  year: string;
  medium: string;
  dimensions: string;
  wing: string;
  curatorNote: string;
  src: string;
  metUrl?: string;
}

export const ALL_MASTERPIECES: Artwork[] = [
  // ── Wing I: Impressionism & Post-Impressionism ──
  {
    id: "wheat-field",
    title: "Wheat Field with Cypresses",
    artist: "Vincent van Gogh",
    year: "1889",
    medium: "Oil on canvas",
    dimensions: "73 × 93.4 cm",
    wing: "Post-Impressionist Wing",
    curatorNote:
      "Painted in the late summer of 1889 at Saint-Rémy. Heavy impasto brushwork swirls the sunlit wheat against the sombre, flame-like vertical silhouette of the cypresses.",
    src: "artworks/wheat-field.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/436535",
  },
  {
    id: "irises",
    title: "Irises",
    artist: "Vincent van Gogh",
    year: "1890",
    medium: "Oil on canvas",
    dimensions: "73.7 × 92.1 cm",
    wing: "Post-Impressionist Wing",
    curatorNote:
      "Painted in the asylum garden of Saint-Paul. Van Gogh contrasted deep violet blossoms against soft chartreuse foliage, seeking harmonious, restful intensity.",
    src: "artworks/irises.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/436528",
  },
  {
    id: "cypresses",
    title: "Cypresses",
    artist: "Vincent van Gogh",
    year: "1889",
    medium: "Oil on canvas",
    dimensions: "93.4 × 74 cm",
    wing: "Post-Impressionist Wing",
    curatorNote:
      "Van Gogh regarded the cypress as an Egyptian obelisk of nature — monumental, dark vertical silhouettes touching the swirling summer sky of Provence.",
    src: "artworks/cypresses.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/437980",
  },
  {
    id: "cezanne-still-life",
    title: "Still Life with Apples and Primroses",
    artist: "Paul Cézanne",
    year: "ca. 1890",
    medium: "Oil on canvas",
    dimensions: "73 × 92.4 cm",
    wing: "Post-Impressionist Wing",
    curatorNote:
      "Cézanne constructs spatial depth through modulated patches of color and tilted planes, treating apples and crockery with monumental architectural gravity.",
    src: "artworks/cezanne-still-life.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/435882",
  },
  {
    id: "seurat-grande-jatte",
    title: "Gray Weather, Grande Jatte",
    artist: "Georges Seurat",
    year: "ca. 1886–88",
    medium: "Oil on canvas",
    dimensions: "70.5 × 86.4 cm",
    wing: "Post-Impressionist Wing",
    curatorNote:
      "A luminous, overcast study of the island on the Seine. Seurat's precise pointillist dot matrix vibrates with optical harmony and quiet Sunday contemplation.",
    src: "artworks/seurat-grande-jatte.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/438015",
  },
  {
    id: "renoir-seashore",
    title: "By the Seashore",
    artist: "Auguste Renoir",
    year: "1883",
    medium: "Oil on canvas",
    dimensions: "92.1 × 72.4 cm",
    wing: "French Impressionism",
    curatorNote:
      "Painted in Guernsey with delicate, feathery brushwork. Renoir captures the crisp sea breeze, iridescent ocean waves, and sunlit porcelain flesh tones.",
    src: "artworks/renoir-seashore.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/437430",
  },

  // ── Wing II: Dutch Golden Age ──
  {
    id: "vermeer-pitcher",
    title: "Young Woman with a Water Pitcher",
    artist: "Johannes Vermeer",
    year: "ca. 1662",
    medium: "Oil on canvas",
    dimensions: "45.7 × 40.6 cm",
    wing: "Dutch Golden Age",
    curatorNote:
      "A solitary, timeless moment of Dutch domestic tranquility. Vermeer captures the cool, diffused morning daylight streaming onto polished brass and starched linen.",
    src: "artworks/vermeer-water-pitcher.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/437881",
  },
  {
    id: "rembrandt-self",
    title: "Self-Portrait",
    artist: "Rembrandt van Rijn",
    year: "1660",
    medium: "Oil on canvas",
    dimensions: "80.3 × 67.3 cm",
    wing: "Dutch Golden Age",
    curatorNote:
      "Painted in his fifty-fourth year with unflinching honesty. Layered impasto and dramatic chiaroscuro capture a life of grief, bankruptcy, and enduring artistic mastery.",
    src: "artworks/rembrandt-self-portrait.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/437397",
  },

  // ── Wing III: Maritime, Romanticism & Japanese Masterworks ──
  {
    id: "homer-gulf-stream",
    title: "The Gulf Stream",
    artist: "Winslow Homer",
    year: "1899",
    medium: "Oil on canvas",
    dimensions: "71.4 × 124.8 cm",
    wing: "American Realism",
    curatorNote:
      "A lone sailor adrift in a dismasted sloop among churning cobalt waves and circling sharks. Homer's supreme statement on human resilience against nature's indifference.",
    src: "artworks/homer-gulf-stream.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/11122",
  },
  {
    id: "turner-venice",
    title: "Venice, from the Porch of Madonna della Salute",
    artist: "J. M. W. Turner",
    year: "ca. 1835",
    medium: "Oil on canvas",
    dimensions: "91.4 × 122.2 cm",
    wing: "Romanticism & Maritime",
    curatorNote:
      "An ethereal vision of the Grand Canal dissolved in golden mist and iridescent lagoon reflections. Turner unites Canaletto's topography with supreme atmospheric light.",
    src: "artworks/turner-venice.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/437853",
  },
  {
    id: "hokusai-great-wave",
    title: "Under the Wave off Kanagawa (The Great Wave)",
    artist: "Katsushika Hokusai",
    year: "ca. 1830–32",
    medium: "Woodblock print; ink and color on paper",
    dimensions: "25.7 × 37.9 cm",
    wing: "Edo Period Masterworks",
    curatorNote:
      "A towering claw of Prussian blue foam frames Mount Fuji in the distance. The most globally recognized masterpiece of Japanese woodblock printing.",
    src: "artworks/hokusai-great-wave.jpg",
    metUrl: "https://www.metmuseum.org/art/collection/search/45434",
  },
];

const WINGS = [
  "All Wings",
  "Post-Impressionist Wing",
  "French Impressionism",
  "Dutch Golden Age",
  "American Realism",
  "Romanticism & Maritime",
  "Edo Period Masterworks",
];

export function GalleryWalk({
  onSelectForMantlepiece,
}: {
  onSelectForMantlepiece?: (artwork: Artwork) => void;
}) {
  const [activeWing, setActiveWing] = useState("All Wings");
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isAutoWalking, setIsAutoWalking] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState(0);
  const [fluidVelocity, setFluidVelocity] = useState(0);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const dragStartX = useRef(0);
  const dragOffsetRef = useRef(0);
  const lastDragX = useRef(0);
  const lastDragTime = useRef(0);
  const velocityRef = useRef(0);
  const animationFrameRef = useRef<number | null>(null);

  const filteredArtworks =
    activeWing === "All Wings"
      ? ALL_MASTERPIECES
      : ALL_MASTERPIECES.filter((art) => art.wing === activeWing);

  // Clamp current index when filter changes
  useEffect(() => {
    if (currentIndex >= filteredArtworks.length) {
      setCurrentIndex(0);
    }
  }, [activeWing, filteredArtworks.length, currentIndex]);

  // Cancel any pending momentum frame on unmount
  useEffect(() => {
    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, []);

  // Fluid momentum loop for inertia drag
  const stepMomentum = useCallback(() => {
    if (Math.abs(velocityRef.current) > 0.05) {
      velocityRef.current *= 0.92; // fluid friction damping
      dragOffsetRef.current += velocityRef.current;
      setDragOffset(dragOffsetRef.current);
      setFluidVelocity(velocityRef.current);
      animationFrameRef.current = requestAnimationFrame(stepMomentum);
    } else {
      velocityRef.current = 0;
      animationFrameRef.current = null;
      setFluidVelocity(0);

      // Snap to nearest artwork card if dragged past threshold
      const finalOffset = dragOffsetRef.current;
      dragOffsetRef.current = 0;
      setDragOffset(0);

      const threshold = 80;
      if (finalOffset > threshold) {
        setCurrentIndex((idx) => (idx > 0 ? idx - 1 : filteredArtworks.length - 1));
      } else if (finalOffset < -threshold) {
        setCurrentIndex((idx) => (idx < filteredArtworks.length - 1 ? idx + 1 : 0));
      }
    }
  }, [filteredArtworks.length]);

  // Pointer drag start
  const handlePointerDown = (e: React.PointerEvent) => {
    setIsDragging(true);
    setIsAutoWalking(false);
    dragStartX.current = e.clientX;
    dragOffsetRef.current = 0;
    lastDragX.current = e.clientX;
    lastDragTime.current = performance.now();
    velocityRef.current = 0;

    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
  };

  // Pointer drag move
  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging) return;
    const now = performance.now();
    const dt = Math.max(now - lastDragTime.current, 1);
    const dx = e.clientX - lastDragX.current;

    // Fluid drag with elastic resistance
    const instantVelocity = (dx / dt) * 16;
    velocityRef.current = instantVelocity;
    setFluidVelocity(instantVelocity);

    lastDragX.current = e.clientX;
    lastDragTime.current = now;

    const nextOffset = e.clientX - dragStartX.current;
    dragOffsetRef.current = nextOffset;
    setDragOffset(nextOffset);
  };

  // Pointer drag end / release
  const handlePointerUp = () => {
    if (!isDragging) return;
    setIsDragging(false);
    animationFrameRef.current = requestAnimationFrame(stepMomentum);
  };

  // Auto-walk promenade loop
  useEffect(() => {
    if (!isAutoWalking) return;
    const interval = setInterval(() => {
      setCurrentIndex((prev) => (prev < filteredArtworks.length - 1 ? prev + 1 : 0));
    }, 4500);
    return () => clearInterval(interval);
  }, [isAutoWalking, filteredArtworks.length]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === "ArrowLeft" || e.key === "a" || e.key === "A") {
        setCurrentIndex((prev) => (prev > 0 ? prev - 1 : filteredArtworks.length - 1));
      } else if (e.key === "ArrowRight" || e.key === "d" || e.key === "D") {
        setCurrentIndex((prev) => (prev < filteredArtworks.length - 1 ? prev + 1 : 0));
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [filteredArtworks.length]);

  const activeArtwork = filteredArtworks[currentIndex] || filteredArtworks[0];

  return (
    <section
      id="gallery-promenade"
      className="relative z-10 py-24 px-4 sm:px-8 border-t border-[var(--border-subtle)] bg-[var(--surface-bg)] overflow-hidden transition-colors duration-500"
    >
      {/* Interactive Fluid Atmosphere Canvas */}
      <FluidAtmosphereCanvas externalVelocityX={fluidVelocity} />

      <div className="relative z-20 max-w-6xl mx-auto">
        {/* Section Curatorial Header */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-elevated)] mb-4">
            <Compass className="w-3.5 h-3.5 text-[var(--gallery-gold)] animate-spin-slow" />
            <span className="font-serif tracking-widest text-[var(--gallery-gold)] uppercase text-xs font-semibold">
              The Grand Salon Promenade
            </span>
          </div>

          <h2 className="font-serif text-3xl sm:text-5xl md:text-6xl text-[var(--paper-white)] font-normal leading-tight mb-4">
            Walk the Gallery Wings
          </h2>

          <p className="font-sans text-xs sm:text-sm text-[var(--paper-muted)] max-w-xl mx-auto leading-relaxed">
            Drag or swipe horizontally to stroll between masterpieces. Feel the spatial depth of each wing as natural momentum and gallery lighting respond to your pace.
          </p>
        </div>

        {/* Wing Category Filters */}
        <div className="flex items-center justify-center gap-2 flex-wrap mb-10">
          {WINGS.map((wing) => {
            const isActive = activeWing === wing;
            return (
              <button
                key={wing}
                onClick={() => {
                  setActiveWing(wing);
                  setCurrentIndex(0);
                }}
                className={`px-3.5 py-1.5 rounded-full text-xs font-sans tracking-wide transition-all duration-300 ${
                  isActive
                    ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-[0_4px_16px_var(--gallery-gold-glow)]"
                    : "border border-[var(--border-subtle)] bg-[var(--surface-elevated)] text-[var(--paper-muted)] hover:text-[var(--paper-white)] hover:border-[var(--gallery-gold)]/40"
                }`}
              >
                {wing}
              </button>
            );
          })}
        </div>

        {/* The 3D Spatial Gallery Promenade Stage */}
        <div
          ref={containerRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
          className="relative perspective-container select-none cursor-grab active:cursor-grabbing min-h-[440px] sm:min-h-[540px] flex items-center justify-center"
        >
          {/* Ceiling Track Spotlight falling on the active gallery corridor */}
          <div className="gallery-spotlight-cone" />

          {/* Strolling 3D Carousel of Framed Artworks */}
          <div className="relative w-full max-w-3xl flex items-center justify-center">
            {filteredArtworks.map((art, idx) => {
              const offset = idx - currentIndex;
              // Only render nearby items for peak performance
              if (Math.abs(offset) > 2) return null;

              // 3D physics positioning
              const translateX = offset * 360 + dragOffset;
              const rotateY = offset * -18;
              const scale = offset === 0 ? 1 : 0.82;
              const opacity = offset === 0 ? 1 : Math.abs(offset) === 1 ? 0.45 : 0.15;
              const zIndex = 10 - Math.abs(offset);

              return (
                <div
                  key={art.id}
                  style={{
                    transform: `translateX(${translateX}px) scale(${scale}) rotateY(${rotateY}deg)`,
                    opacity,
                    zIndex,
                    transition: isDragging ? "none" : "all 0.5s cubic-bezier(0.16, 1, 0.3, 1)",
                  }}
                  className="absolute max-w-md sm:max-w-xl w-[86vw] sm:w-[500px] pointer-events-auto"
                >
                  {/* Museum Hardwood Frame */}
                  <div className="gilded-frame gallery-stroll-card rounded">
                    <div className="canvas-recess aspect-[4/3] relative overflow-hidden bg-black/95 rounded-sm flex items-center justify-center p-1 sm:p-2">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={art.src}
                        alt={art.title}
                        draggable={false}
                        className="max-h-full max-w-full w-auto h-auto object-contain select-none transition-transform duration-500 hover:scale-[1.01]"
                      />
                      {/* Oil specular varnish sheen */}
                      <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.04] to-transparent pointer-events-none" />
                    </div>

                    {/* Museum Engraved Plaque */}
                    <div className="mt-4 pt-3 border-t border-[var(--border-subtle)] flex items-center justify-between gap-3 text-left">
                      <div>
                        <div className="text-[10px] tracking-[0.2em] uppercase font-semibold text-[var(--gallery-gold)]">
                          {art.wing} · {art.year}
                        </div>
                        <div className="font-serif italic text-lg text-[var(--paper-white)] line-clamp-1">
                          {art.title}
                        </div>
                        <div className="text-[11px] text-[var(--paper-muted)]">
                          {art.artist}
                        </div>
                      </div>

                      {offset === 0 && onSelectForMantlepiece && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectForMantlepiece(art);
                            const mantle = document.getElementById("exhibition");
                            if (mantle) {
                              mantle.scrollIntoView({ behavior: "smooth" });
                            }
                          }}
                          className="gilded-shimmer-btn flex items-center gap-1.5 px-3 py-1.5 rounded bg-[var(--surface-elevated)] border border-[var(--gallery-gold)]/40 hover:border-[var(--gallery-gold)] text-[10px] font-sans tracking-wider uppercase text-[var(--gallery-gold)] transition-all whitespace-nowrap shadow-sm"
                        >
                          <Maximize2 className="w-3 h-3" />
                          <span>View on Mantle</span>
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Stroll Controller Bar & Plaque Detail */}
        <div className="mt-8 max-w-2xl mx-auto flex flex-col items-center gap-6">
          {/* Promenade Pedals & Auto-Walk Toggle */}
          <div className="flex items-center gap-4">
            <button
              onClick={() =>
                setCurrentIndex((prev) => (prev > 0 ? prev - 1 : filteredArtworks.length - 1))
              }
              aria-label="Previous artwork"
              className="w-10 h-10 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-elevated)] hover:border-[var(--gallery-gold)] text-[var(--paper-white)] flex items-center justify-center transition-all hover:scale-105 active:scale-95 shadow-sm"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>

            <button
              onClick={() => setIsAutoWalking((prev) => !prev)}
              className={`flex items-center gap-2 px-5 py-2 rounded-full border text-xs font-sans tracking-wider uppercase transition-all shadow-sm ${
                isAutoWalking
                  ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] border-[var(--gallery-gold)] font-semibold shadow-[0_0_20px_var(--gallery-gold-glow)]"
                  : "border-[var(--border-subtle)] bg-[var(--surface-elevated)] text-[var(--paper-white)] hover:border-[var(--gallery-gold)]"
              }`}
            >
              {isAutoWalking ? (
                <>
                  <Pause className="w-3.5 h-3.5" />
                  <span>Pause Stroll</span>
                </>
              ) : (
                <>
                  <Play className="w-3.5 h-3.5" />
                  <span>Begin Stroll</span>
                </>
              )}
            </button>

            <button
              onClick={() =>
                setCurrentIndex((prev) => (prev < filteredArtworks.length - 1 ? prev + 1 : 0))
              }
              aria-label="Next artwork"
              className="w-10 h-10 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-elevated)] hover:border-[var(--gallery-gold)] text-[var(--paper-white)] flex items-center justify-center transition-all hover:scale-105 active:scale-95 shadow-sm"
            >
              <ChevronRight className="w-5 h-5" />
            </button>
          </div>

          {/* Curatorial Commentary Placard */}
          <div className="w-full brass-plaque rounded-md p-5 text-left border border-[var(--border-subtle)] transition-colors">
            <div className="flex items-start justify-between gap-4 mb-2">
              <div className="flex items-center gap-2">
                <Info className="w-4 h-4 text-[var(--gallery-gold)]" />
                <span className="font-serif italic text-base sm:text-lg text-[var(--paper-white)]">
                  {activeArtwork.title} ({activeArtwork.year})
                </span>
              </div>
              {activeArtwork.metUrl && (
                <a
                  href={activeArtwork.metUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1 text-[10px] uppercase tracking-wider text-[var(--gallery-gold)] hover:underline"
                >
                  <span>Met Collection</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              )}
            </div>
            <p className="font-sans text-xs text-[var(--paper-muted)] leading-relaxed mb-3">
              {activeArtwork.curatorNote}
            </p>
            <div className="flex items-center justify-between text-[10px] font-mono text-[var(--paper-dim)] border-t border-[var(--border-subtle)] pt-2.5">
              <span>{activeArtwork.medium}</span>
              <span>{activeArtwork.dimensions}</span>
              <span className="text-[var(--gallery-gold)] font-sans uppercase">
                Artwork {currentIndex + 1} of {filteredArtworks.length}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
