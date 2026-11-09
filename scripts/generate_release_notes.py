#!/usr/bin/env python3
"""
ArtWallpaper Automated Release Notes Generator
Supports:
1. Google Gemini AI generation (when GEMINI_API_KEY is available)
2. Smart Git Commit Parser fallback (zero-dependency, matches the exact witty museum curator style)
3. Custom user release notes passthrough
4. Deduplicated single Full Changelog comparison link
"""

import os
import sys
import json
import re
import subprocess
import urllib.request
import urllib.error

def get_git_output(args):
    try:
        result = subprocess.run(
            ["git"] + args,
            capture_output=True,
            text=True,
            check=True,
            encoding="utf-8",
            errors="replace"
        )
        return result.stdout.strip()
    except Exception:
        return ""

def get_previous_tag():
    # Try finding the latest tag reachable from parent of HEAD
    tag = get_git_output(["describe", "--tags", "--abbrev=0", "HEAD^"])
    if tag:
        return tag
    # Try finding any latest tag
    tag = get_git_output(["describe", "--tags", "--abbrev=0"])
    if tag:
        return tag
    return "3.3.0"

def get_commits_since(tag):
    if tag:
        log_range = f"{tag}..HEAD"
    else:
        log_range = "HEAD~25..HEAD"
    
    raw = get_git_output(["log", log_range, "--pretty=format:%s (%h)", "--no-merges"])
    if not raw:
        raw = get_git_output(["log", "-n", "20", "--pretty=format:%s (%h)", "--no-merges"])
    
    return [line.strip() for line in raw.splitlines() if line.strip()]

def get_version():
    try:
        with open("gradle.properties", "r", encoding="utf-8") as f:
            for line in f:
                if line.startswith("appVersion="):
                    return line.strip().split("=")[1].strip()
    except Exception:
        pass
    return "4.0.3"

def generate_with_gemini(api_key, version, prev_tag, commits):
    models = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-flash-latest"]
    commits_text = "\n".join(f"- {c}" for c in commits)
    
    prompt = f"""You are the lead product storyteller and release curator for ArtWallpaper (a desktop application bringing fine art from museum archives like The Met Open Access to Windows and macOS desktops).

Your release notes are famous for being charming, witty, delightfully written, and full of personality (similar to Discord release notes, but with museum curator & fine art humor).

Here are the git commits for version v{version} since {prev_tag}:
{commits_text}

Generate the complete GitHub release notes in markdown following this exact structure and persona:

# Art Wallpaper Manager 🎨

> [Insert a clever, witty 1-line quote about wallpapers, art, and desktops]

## 🌟 What's New in {version}?

[Create 5 to 7 bullet points based on the actual commit work done. Each bullet must start with an emoji, a bold feature title, and a witty description with personality. For example:
- 🖼️ **True Screen Fit Engine**: Smart ambient blur matting with bottom taskbar clearance. No more stretched masterpieces or paintings hidden under your Windows taskbar!
- 🔄 **In-App Auto Updater**: Silent background update checks and one-click upgrades — no more manually foraging for installers on GitHub like an archaeologist.
- 🌓 **Dynamic Theme Harmony**: Adapts automatically to Windows light and dark themes (sunlit Daytime Museum or evening Obsidian gallery).
- 🛡️ **Wallpaper Retention Guard**: Closing or minimizing the window to the tray keeps your curated masterpiece safe and locked on your desktop.
- ⚡ **Lean Storage Optimization**: Lean 20-slot offline cache and trimmed runtime footprint for instant wakeups.
- 🍏 **Cross-Platform macOS & Windows**: Native MSI installers for Windows and DMG packages for Apple Silicon and Intel Macs.]

## ✨ All Features

- 🖼️ Daily curated artwork from world-class museum collections (The Met Open Access)
- 📐 True laptop screen fit with ambient blur, museum matting, and taskbar clearance
- 🌓 Dynamic light & dark mode theme integration
- ⏰ Rock-solid update scheduling that respects your exact preferred hour
- 🔄 In-app auto-updater with one-click seamless upgrades
- 💾 Smart 20-slot offline caching for airplane mode and spotty Wi-Fi
- 🎯 Native Windows 11 Fluent and macOS system tray controls
- 🚀 Instant startup with system tray minimization
- 📊 Zero repeat artwork guarantee with persistent unvisited index
- ⚡ Fast, native memory footprint with Compose Multiplatform

## 🚀 Installation

* **Windows**: Download [`ArtWallpaper-{version}-Windows.msi`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-Windows.msi) and run the installer.
* **macOS**: Download [`ArtWallpaper-{version}-macOS.dmg`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-macOS.dmg), open it, and drag ArtWallpaper into Applications.

## 💻 System Requirements

- **Windows**: Windows 10 or 11 (64-bit)
- **macOS**: macOS 12+ Monterey, Ventura, Sonoma, Sequoia (Apple Silicon & Intel)
- **Display**: Seamless support for all laptop & desktop screens (16:9, 16:10, 3:2)
- Internet connection for fetching fresh museum artworks

## 🎮 Quick Tips

- Right-click the system tray icon anytime for instant **Next Wallpaper**, **Pause**, or **Settings**.
- Double-click the tray icon to reopen the main exhibition gallery.
- All preferences and history are safely stored in `%USERPROFILE%/.artwallpaper/` (or `~/.artwallpaper/` on Mac).

## 🙏 Credits

- Artwork provided by The Metropolitan Museum of Art Open Access archive
- Built with Kotlin and JetBrains Compose Multiplatform
- Made with ❤️, caffeine, and a firm belief that your desktop deserves better than generic corporate wallpapers

---

*No artworks were harmed in the making of this update, but they're now presented with more style than a museum curator at a gallery opening!* 🎨✨

**Full Changelog**: https://github.com/Princeyyyy/art-wallpaper/compare/{prev_tag}...v{version}

Do NOT wrap the entire output in ```markdown or ``` codeblocks. Return raw markdown text directly.
"""

    payload = {
        "contents": [{
            "parts": [{
                "text": prompt
            }]
        }],
        "generationConfig": {
            "temperature": 0.3,
            "maxOutputTokens": 2500
        }
    }

    data_bytes = json.dumps(payload).encode("utf-8")

    for model in models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
        req = urllib.request.Request(
            url,
            data=data_bytes,
            headers={"Content-Type": "application/json"}
        )
        try:
            print(f"[ReleaseNotes] Trying model {model}...")
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts and "text" in parts[0]:
                        content = parts[0]["text"].strip()
                        if content.startswith("```markdown"):
                            content = content[len("```markdown"):].strip()
                        if content.startswith("```"):
                            content = content[len("```"):].strip()
                        if content.endswith("```"):
                            content = content[:-3].strip()
                        print(f"[ReleaseNotes] Successfully generated notes with {model}!")
                        return content
        except Exception as e:
            print(f"[ReleaseNotes] Model {model} attempt failed ({e}), checking next model...")

    return None

def generate_fallback_notes(version, prev_tag, commits):
    # Parse commits to dynamically highlight what was done
    has_fit = any("fit" in c.lower() or "matting" in c.lower() or "taskbar" in c.lower() for c in commits)
    has_update = any("updater" in c.lower() or "update" in c.lower() for c in commits)
    has_theme = any("theme" in c.lower() or "dark" in c.lower() for c in commits)
    has_storage = any("storage" in c.lower() or "cache" in c.lower() or "shrink" in c.lower() for c in commits)
    has_wallpaper_fix = any("replace" in c.lower() or "close" in c.lower() or "wallpaper" in c.lower() for c in commits)
    has_mac = any("mac" in c.lower() or "dmg" in c.lower() for c in commits)

    whats_new_bullets = []
    if has_fit:
        whats_new_bullets.append("- 🖼️ **True Screen Fit Engine**: Smart ambient blur matting with bottom taskbar clearance. No more stretched art or paintings trapped behind your Windows taskbar!")
    if has_update:
        whats_new_bullets.append("- 🔄 **In-App Auto Updater**: Silent background update checks and one-click upgrades — no more hunting on GitHub like an archaeologist.")
    if has_theme:
        whats_new_bullets.append("- 🌓 **Dynamic Theme Harmony**: Seamlessly syncs with Windows dark & light modes (from sunlit Daytime Museum to midnight Obsidian).")
    if has_wallpaper_fix:
        whats_new_bullets.append("- 🛡️ **Wallpaper Retention Guard**: Closing or minimizing the window to the tray keeps your curated wallpaper safe and locked on your desktop.")
    if has_storage:
        whats_new_bullets.append("- ⚡ **Lean Storage Optimization**: Lean 20-slot offline cache and trimmed runtime footprint for instant wakeups.")
    if has_mac:
        whats_new_bullets.append("- 🍏 **Cross-Platform macOS & Windows**: Native installers for both platforms (MSI for Windows, DMG for macOS).")

    # If few specific matches, add parsed commit titles
    if len(whats_new_bullets) < 4:
        for c in commits[:6]:
            clean = re.sub(r"^(feat|fix|perf|chore|ci|build)(\([^)]+\))?:\s*", "", c, flags=re.I)
            whats_new_bullets.append(f"- 🎨 **{clean.capitalize()}**")

    bullets_text = "\n".join(whats_new_bullets)

    output = f"""# Art Wallpaper Manager 🎨

> Because staring at the same wallpaper for months is like eating the same breakfast for a year — technically possible, but why would you do that to yourself?

## 🌟 What's New in {version}?

{bullets_text}

## ✨ All Features

- 🖼️ Daily curated artwork from world-class museum collections (The Met Open Access)
- 📐 True laptop screen fit with ambient blur, museum matting, and taskbar clearance
- 🌓 Dynamic light & dark mode theme integration
- ⏰ Rock-solid update scheduling that respects your exact preferred hour
- 🔄 In-app auto-updater with one-click seamless upgrades
- 💾 Smart 20-slot offline caching for airplane mode and spotty Wi-Fi
- 🎯 Native Windows 11 Fluent and macOS system tray controls
- 🚀 Instant startup with system tray minimization
- 📊 Zero repeat artwork guarantee with persistent unvisited index
- ⚡ Fast, native memory footprint with Compose Multiplatform

## 🚀 Installation

* **Windows**: Download [`ArtWallpaper-{version}-Windows.msi`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-Windows.msi) and run the installer.
* **macOS**: Download [`ArtWallpaper-{version}-macOS.dmg`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-macOS.dmg), open it, and drag ArtWallpaper into Applications.

## 💻 System Requirements

- **Windows**: Windows 10 or 11 (64-bit)
- **macOS**: macOS 12+ Monterey, Ventura, Sonoma, Sequoia (Apple Silicon & Intel)
- **Display**: Seamless support for all laptop & desktop screens (16:9, 16:10, 3:2)
- Internet connection for fetching fresh museum artworks

## 🎮 Quick Tips

- Right-click the system tray icon anytime for instant **Next Wallpaper**, **Pause**, or **Settings**.
- Double-click the tray icon to reopen the main exhibition gallery.
- All preferences and history are safely stored in `%USERPROFILE%/.artwallpaper/` (or `~/.artwallpaper/` on Mac).

## 🙏 Credits

- Artwork provided by The Metropolitan Museum of Art Open Access archive
- Built with Kotlin and JetBrains Compose Multiplatform
- Made with ❤️, caffeine, and a firm belief that your desktop deserves better than generic corporate wallpapers

---

*No artworks were harmed in the making of this update, but they're now presented with more style than a museum curator at a gallery opening!* 🎨✨

**Full Changelog**: https://github.com/Princeyyyy/art-wallpaper/compare/{prev_tag}...v{version}
"""
    return output.strip() + "\n"

def main():
    if hasattr(sys.stdout, 'reconfigure'):
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except Exception:
            pass

    version_input = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("RELEASE_VERSION", "")
    version = version_input.strip().lstrip("vV") if version_input else get_version()
    
    output_path = sys.argv[2] if len(sys.argv) > 2 else "release_body.md"
    
    custom_notes = os.environ.get("CUSTOM_NOTES", "").strip()
    boilerplate = "ArtWallpaper — Cross-platform Windows & macOS release."
    has_custom_notes = custom_notes and custom_notes != boilerplate

    gemini_key = os.environ.get("GEMINI_API_KEY", "").strip()

    prev_tag = get_previous_tag()
    commits = get_commits_since(prev_tag)

    body_content = ""

    # Strategy 1: User explicitly provided custom notes in the workflow dispatch input
    if has_custom_notes:
        print("[ReleaseNotes] Using user-provided custom release notes.")
        body_content = f"""# Art Wallpaper Manager 🎨

## 🌟 What's New in {version}?

{custom_notes}

## 🚀 Installation

* **Windows**: Download [`ArtWallpaper-{version}-Windows.msi`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-Windows.msi) and run the installer.
* **macOS**: Download [`ArtWallpaper-{version}-macOS.dmg`](https://github.com/Princeyyyy/art-wallpaper/releases/download/v{version}/ArtWallpaper-{version}-macOS.dmg), open it, and drag ArtWallpaper into Applications.

---

**Full Changelog**: https://github.com/Princeyyyy/art-wallpaper/compare/{prev_tag}...v{version}
"""
    # Strategy 2: Use Google Gemini AI when GEMINI_API_KEY is present
    elif gemini_key:
        print(f"[ReleaseNotes] Generating AI release notes with Google Gemini for v{version} (since {prev_tag})...")
        try:
            ai_notes = generate_with_gemini(gemini_key, version, prev_tag, commits)
            if ai_notes:
                print("[ReleaseNotes] Successfully generated release notes with Gemini AI!")
                body_content = ai_notes
            else:
                print("[ReleaseNotes] Gemini AI returned empty content, falling back to smart curator generator.")
                body_content = generate_fallback_notes(version, prev_tag, commits)
        except Exception as e:
            print(f"[ReleaseNotes] Warning: Gemini AI request failed ({e}), falling back to smart curator generator.")
            body_content = generate_fallback_notes(version, prev_tag, commits)

    # Strategy 3: Graceful smart fallback (no key needed, works anytime!)
    else:
        print(f"[ReleaseNotes] GEMINI_API_KEY not configured. Generating smart curator release notes from git commits for v{version}...")
        body_content = generate_fallback_notes(version, prev_tag, commits)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(body_content)

    print(f"[ReleaseNotes] Successfully written to {output_path}")
    print("--- PREVIEW ---")
    print(body_content)

if __name__ == "__main__":
    main()
