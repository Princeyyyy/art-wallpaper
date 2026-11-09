import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        gallery: {
          obsidian: "#060709",
          stone: "#101217",
          wall: "#161920",
          border: "#242833",
          gold: "#C8A97E",
          goldLight: "#E0C8A2",
          brass: "#9E8056",
          vellum: "#F5EFE6",
          dim: "#888E9B",
          wood: "#1A1410",
        },
      },
      fontFamily: {
        serif: ["Cormorant Garamond", "Georgia", "serif"],
        display: ["Cinzel", "Trajan Pro", "serif"],
        sans: ["Plus Jakarta Sans", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
      },
    },
  },
  plugins: [],
};
export default config;
