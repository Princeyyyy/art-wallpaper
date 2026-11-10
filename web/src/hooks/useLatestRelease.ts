"use client";

import { useState, useEffect } from "react";
import { CURRENT_APP_VERSION, CURRENT_APP_TAG } from "../lib/version";

export interface ReleaseInfo {
  version: string;
  tag: string;
  windowsMsiUrl: string;
  macDmgUrl: string;
  isLoading: boolean;
}

function constructDownloadUrls(tag: string, version: string) {
  const cleanTag = tag.startsWith("v") ? tag : `v${tag}`;
  const cleanVersion = version.replace(/^v/, "");
  return {
    msi: `https://github.com/Princeyyyy/art-wallpaper/releases/download/${cleanTag}/ArtWallpaper-${cleanVersion}-Windows.msi`,
    dmg: `https://github.com/Princeyyyy/art-wallpaper/releases/download/${cleanTag}/ArtWallpaper-${cleanVersion}-macOS.dmg`,
  };
}

const fallbackUrls = constructDownloadUrls(CURRENT_APP_TAG, CURRENT_APP_VERSION);
const CACHE_KEY = "art_wallpaper_latest_release";

export function useLatestRelease(): ReleaseInfo {
  const [release, setRelease] = useState<ReleaseInfo>({
    version: CURRENT_APP_VERSION,
    tag: CURRENT_APP_TAG,
    windowsMsiUrl: fallbackUrls.msi,
    macDmgUrl: fallbackUrls.dmg,
    isLoading: true,
  });

  useEffect(() => {
    let isMounted = true;

    // Check session cache first to respect GitHub API rate limits
    try {
      const cached = sessionStorage.getItem(CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (parsed.version && parsed.windowsMsiUrl && parsed.macDmgUrl) {
          setRelease({ ...parsed, isLoading: false });
          return;
        }
      }
    } catch {
      // Ignore sessionStorage read errors
    }

    async function fetchLatest() {
      try {
        const res = await fetch(
          "https://api.github.com/repos/Princeyyyy/art-wallpaper/releases/latest",
          {
            headers: {
              Accept: "application/vnd.github.v3+json",
            },
          }
        );

        if (!res.ok) {
          if (isMounted) setRelease((prev) => ({ ...prev, isLoading: false }));
          return;
        }

        const data = await res.json();
        const rawTag: string = data.tag_name || CURRENT_APP_TAG;
        const cleanVersion = rawTag.startsWith("v") ? rawTag.substring(1) : rawTag;

        let msiUrl = "";
        let dmgUrl = "";

        if (Array.isArray(data.assets)) {
          for (const asset of data.assets) {
            if (asset.name?.endsWith(".msi") && asset.browser_download_url) {
              msiUrl = asset.browser_download_url;
            } else if (asset.name?.endsWith(".dmg") && asset.browser_download_url) {
              dmgUrl = asset.browser_download_url;
            }
          }
        }

        // Fallback constructed pattern if assets not yet populated
        const generated = constructDownloadUrls(rawTag, cleanVersion);
        if (!msiUrl) msiUrl = generated.msi;
        if (!dmgUrl) dmgUrl = generated.dmg;

        const resolved: ReleaseInfo = {
          version: cleanVersion,
          tag: rawTag,
          windowsMsiUrl: msiUrl,
          macDmgUrl: dmgUrl,
          isLoading: false,
        };

        if (isMounted) {
          setRelease(resolved);
          try {
            sessionStorage.setItem(CACHE_KEY, JSON.stringify(resolved));
          } catch {
            // Ignore cache write errors
          }
        }
      } catch {
        if (isMounted) {
          setRelease((prev) => ({ ...prev, isLoading: false }));
        }
      }
    }

    fetchLatest();

    return () => {
      isMounted = false;
    };
  }, []);

  return release;
}
