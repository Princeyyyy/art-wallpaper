"use client";

import React, { createContext, useContext, useEffect, useState } from "react";
import { Monitor, Sun, Moon } from "lucide-react";

export type Theme = "dark" | "light" | "system";

interface ThemeContextType {
  theme: Theme;
  resolvedTheme: "dark" | "light";
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType>({
  theme: "system",
  resolvedTheme: "dark",
  setTheme: () => {},
});

const STORAGE_KEY = "art_wallpaper_theme";

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>("system");
  const [resolvedTheme, setResolvedTheme] = useState<"dark" | "light">("dark");
  const [mounted, setMounted] = useState(false);

  // Initialize theme from localStorage on mount
  useEffect(() => {
    setMounted(true);
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
      if (stored && ["dark", "light", "system"].includes(stored)) {
        setThemeState(stored);
      }
    } catch {
      // Ignore localStorage read errors in restricted contexts
    }
  }, []);

  // Update document class whenever theme or system preference changes
  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");

    const applyTheme = () => {
      let active: "dark" | "light";
      if (theme === "system") {
        active = mediaQuery.matches ? "dark" : "light";
      } else {
        active = theme;
      }

      setResolvedTheme(active);

      const root = document.documentElement;
      if (active === "dark") {
        root.classList.add("dark");
        root.classList.remove("light");
      } else {
        root.classList.add("light");
        root.classList.remove("dark");
      }
    };

    applyTheme();

    const handleChange = () => {
      if (theme === "system") {
        applyTheme();
      }
    };

    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [theme]);

  const setTheme = (nextTheme: Theme) => {
    setThemeState(nextTheme);
    try {
      localStorage.setItem(STORAGE_KEY, nextTheme);
    } catch {
      // Ignore localStorage write errors
    }
  };

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}

export function ThemeToggle({ className = "" }: { className?: string }) {
  const { theme, resolvedTheme, setTheme } = useTheme();

  return (
    <div
      role="group"
      aria-label="Theme mode selector"
      className={`inline-flex items-center p-1 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-bg)] shadow-sm backdrop-blur-md transition-colors ${className}`}
    >
      <button
        type="button"
        onClick={() => setTheme("system")}
        title="Auto / System Theme"
        aria-label="Auto / System Theme"
        className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-sans tracking-wider uppercase transition-all duration-300 ${
          theme === "system"
            ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-[0_2px_10px_var(--gallery-gold-glow)]"
            : "text-[var(--paper-muted)] hover:text-[var(--paper-white)]"
        }`}
      >
        <Monitor className="w-3 h-3" />
        <span className="hidden sm:inline">Auto</span>
      </button>

      <button
        type="button"
        onClick={() => setTheme("light")}
        title="Light / Day Gallery Theme"
        aria-label="Light / Day Gallery Theme"
        className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-sans tracking-wider uppercase transition-all duration-300 ${
          theme === "light"
            ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-[0_2px_10px_var(--gallery-gold-glow)]"
            : "text-[var(--paper-muted)] hover:text-[var(--paper-white)]"
        }`}
      >
        <Sun className="w-3 h-3" />
        <span className="hidden sm:inline">Light</span>
      </button>

      <button
        type="button"
        onClick={() => setTheme("dark")}
        title="Dark / Obsidian Salon Theme"
        aria-label="Dark / Obsidian Salon Theme"
        className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-sans tracking-wider uppercase transition-all duration-300 ${
          theme === "dark"
            ? "bg-[var(--gallery-gold)] text-[var(--canvas-bg)] font-semibold shadow-[0_2px_10px_var(--gallery-gold-glow)]"
            : "text-[var(--paper-muted)] hover:text-[var(--paper-white)]"
        }`}
      >
        <Moon className="w-3 h-3" />
        <span className="hidden sm:inline">Dark</span>
      </button>
    </div>
  );
}
