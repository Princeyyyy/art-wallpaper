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

interface GitHubAsset {
  name?: string;
  browser_download_url?: string;
}

interface GitHubReleaseResponse {
  tag_name?: string;
  assets?: GitHubAsset[];
}

interface CachedRelease extends ReleaseInfo {
  buildVersion: string;
  cachedAt: number;
}

function constructDownloadUrls(tag: string, version: string) {
  const cleanTag = tag.startsWith("v") ? tag : `v${tag}`;
  const cleanVersion = version.replace(/^v/i, "");
  return {
    msi: `https://github.com/Princeyyyy/art-wallpaper/releases/download/${cleanTag}/ArtWallpaper-${cleanVersion}-Windows.msi`,
    dmg: `https://github.com/Princeyyyy/art-wallpaper/releases/download/${cleanTag}/ArtWallpaper-${cleanVersion}-macOS.dmg`,
  };
}

function compareSemver(a: string, b: string): number {
  const parse = (v: string) =>
    v
      .replace(/^v/i, "")
      .split("-")[0]
      .split(".")
      .map((part) => {
        const num = Number.parseInt(part, 10);
        return Number.isFinite(num) ? num : 0;
      });

  const partsA = parse(a);
  const partsB = parse(b);
  const len = Math.max(partsA.length, partsB.length);

  for (let i = 0; i < len; i++) {
    const numA = partsA[i] ?? 0;
    const numB = partsB[i] ?? 0;
    if (numA > numB) return 1;
    if (numA < numB) return -1;
  }
  return 0;
}

function parseReleasePayload(data: GitHubReleaseResponse): ReleaseInfo {
  const rawTag: string = data.tag_name || CURRENT_APP_TAG;
  const cleanVersion = rawTag.replace(/^v/i, "");
  const cleanTag = rawTag.startsWith("v") ? rawTag : `v${rawTag}`;

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
  const generated = constructDownloadUrls(cleanTag, cleanVersion);
  return {
    version: cleanVersion,
    tag: cleanTag,
    windowsMsiUrl: msiUrl || generated.msi,
    macDmgUrl: dmgUrl || generated.dmg,
    isLoading: false,
  };
}

const fallbackUrls = constructDownloadUrls(CURRENT_APP_TAG, CURRENT_APP_VERSION);
const CACHE_KEY = "art_wallpaper_latest_release";
const CACHE_TTL_MS = 2 * 60 * 1000;
const REPO_RELEASES_API = "https://api.github.com/repos/Princeyyyy/art-wallpaper/releases";

export function useLatestRelease(): ReleaseInfo {
  const [release, setRelease] = useState<ReleaseInfo>({
    version: CURRENT_APP_VERSION,
    tag: CURRENT_APP_TAG,
    windowsMsiUrl: fallbackUrls.msi,
    macDmgUrl: fallbackUrls.dmg,
    isLoading: true,
  });

  useEffect(() => {
    let ignore = false;
    const controller = new AbortController();

    // Check session cache, ignoring expired entries or entries older than the current build
    try {
      const cached = sessionStorage.getItem(CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached) as Partial<CachedRelease>;
        const isFresh =
          typeof parsed.cachedAt === "number" &&
          Date.now() - parsed.cachedAt < CACHE_TTL_MS;
        const matchesBuild =
          parsed.buildVersion === CURRENT_APP_VERSION &&
          typeof parsed.version === "string" &&
          compareSemver(parsed.version, CURRENT_APP_VERSION) >= 0;

        if (isFresh && matchesBuild && parsed.tag && parsed.windowsMsiUrl && parsed.macDmgUrl) {
          setRelease({
            version: parsed.version!,
            tag: parsed.tag,
            windowsMsiUrl: parsed.windowsMsiUrl,
            macDmgUrl: parsed.macDmgUrl,
            isLoading: false,
          });
          return () => {
            ignore = true;
            controller.abort();
          };
        }

        sessionStorage.removeItem(CACHE_KEY);
      }
    } catch {
      // Ignore sessionStorage read errors
    }

    async function fetchReleaseJson(url: string): Promise<GitHubReleaseResponse | null> {
      const res = await fetch(url, {
        headers: {
          Accept: "application/vnd.github.v3+json",
        },
        cache: "no-store",
        signal: controller.signal,
      });
      if (!res.ok) return null;
      return (await res.json()) as GitHubReleaseResponse;
    }

    function persistCache(resolved: ReleaseInfo) {
      try {
        const entry: CachedRelease = {
          ...resolved,
          buildVersion: CURRENT_APP_VERSION,
          cachedAt: Date.now(),
        };
        sessionStorage.setItem(CACHE_KEY, JSON.stringify(entry));
      } catch {
        // Ignore cache write errors
      }
    }

    async function fetchLatest() {
      try {
        const latestData = await fetchReleaseJson(`${REPO_RELEASES_API}/latest`);
        if (!latestData) {
          if (!ignore) setRelease((prev) => ({ ...prev, isLoading: false }));
          return;
        }

        let resolved = parseReleasePayload(latestData);
        let isCacheable = true;

        // If /releases/latest returns an older version (e.g. GitHub CDN 60s lag right after release
        // or a pending CI release build), probe the current build tag directly before downgrading.
        if (compareSemver(resolved.version, CURRENT_APP_VERSION) < 0) {
          const currentTagData = await fetchReleaseJson(`${REPO_RELEASES_API}/tags/${CURRENT_APP_TAG}`);
          if (currentTagData) {
            resolved = parseReleasePayload(currentTagData);
          } else {
            // Current build tag is not published yet: use latest published installers so downloads
            // do not 404, but skip long-lived caching so the new release appears as soon as ready.
            isCacheable = false;
          }
        }

        if (!ignore) {
          setRelease(resolved);
          if (isCacheable) {
            persistCache(resolved);
          }
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        if (!ignore) {
          setRelease((prev) => ({ ...prev, isLoading: false }));
        }
      }
    }

    fetchLatest();

    return () => {
      ignore = true;
      controller.abort();
    };
  }, []);

  return release;
}
