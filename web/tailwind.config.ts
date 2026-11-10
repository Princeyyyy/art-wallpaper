import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        gallery: {
          obsidian: "var(--canvas-bg)",
          stone: "var(--surface-bg)",
          wall: "var(--surface-elevated)",
          border: "var(--border-subtle)",
          gold: "var(--gallery-gold)",
          goldLight: "var(--gallery-gold-light)",
          brass: "var(--gallery-gold-dim)",
          vellum: "var(--paper-white)",
          dim: "var(--paper-muted)",
          wood: "var(--frame-wood)",
        },
      },
      fontFamily: {
        serif: ["Cormorant Garamond", "Georgia", "serif"],
        display: ["Cinzel", "Trajan Pro", "serif"],
        sans: ["Plus Jakarta Sans", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
      },
      animation: {
        "shimmer-sweep": "shimmerSweep 2.5s infinite",
        "pulse-subtle": "pulseSubtle 3s ease-in-out infinite",
        "float-slow": "floatSlow 6s ease-in-out infinite",
      },
      keyframes: {
        shimmerSweep: {
          "0%": { transform: "translateX(-100%)" },
          "100%": { transform: "translateX(200%)" },
        },
        pulseSubtle: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0.6" },
        },
        floatSlow: {
          "0%, 100%": { transform: "translateY(0px)" },
          "50%": { transform: "translateY(-6px)" },
        },
      },
    },
  },
  plugins: [],
};
export default config;
