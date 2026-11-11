---
name: art-release-ops
description: Operational runbook and maintainer release manual for the Art Wallpaper repository owner. Use when bumping versions, preparing release candidates, reviewing pull requests, running GitHub Actions release workflows, validating installer checksums, or executing rollback runbooks.
---

# Art Wallpaper Release & Maintainer Operations

This skill is the operational runbook for the repository owner ([@Princeyyyy](https://github.com/Princeyyyy)) to manage pull request approvals, version increments, CI/CD validation, and official multi-platform releases.

---

## 1. Governance & Merging Mandates

1. **Sole Merging Authority**:
   - Only `@Princeyyyy` has permission to review, approve, and merge pull requests into `master`.
   - External contributors and AI agents must work through pull requests; direct pushes to `master` are blocked by branch protection.
2. **Sole Release Authority**:
   - Release workflows in `.github/workflows/release.yml` require manual triggering (`workflow_dispatch`) and include an actor verification gate:
     ```yaml
     if: github.repository_owner == github.actor
     ```
   - Only `@Princeyyyy` can initiate production builds and publish GitHub releases.
3. **Commit Cleanliness & History**:
   - Always merge pull requests using **Squash and Merge** (for multi-commit feature branches) or **Rebase and Merge** (for clean single-commit patches) to maintain a linear, readable git log.

---

## 2. Pre-Release Version Bumping Checklist

Art Wallpaper enforces a **single source of truth** versioning architecture:

### Step 1: Update `gradle.properties`
The version is governed by `appVersion`:
```properties
# ArtWallpaper Build Properties
appVersion=4.1.7
appGroup=com.prince.artwallpaper
```

### Step 2: Synchronize Reference Files
Ensure all documentation, CI workflows, and unit tests reflect the new version:
1. `.github/workflows/release.yml`:
   ```yaml
   workflow_dispatch:
     inputs:
       version:
         description: 'Release version (semver format e.g. 4.1.7)'
         required: true
         default: '4.1.7'
   ```
2. `README.md`:
   - Update header: `<h1 align="center">ArtWallpaper Manager v4.1.7</h1>`.
   - Update badge: `Release-v4.1.7`.
3. `src/test/kotlin/UpdateManagerTest.kt`:
   - Verify that semver comparison test suites assert the new version as newer than preceding releases.

### Step 3: Run Full Local Verification
Before triggering CI, verify compilation and tests locally:
```bash
# 1. Compile Kotlin sources
./gradlew compileKotlin

# 2. Run all unit and integration tests
./gradlew test
```

---

## 3. GitHub Actions Release Pipeline (`release.yml`)

Official installers for Windows (`.msi`) and macOS (`.dmg`) are built and published through GitHub Actions.

### Triggering the Release
1. Navigate to: **[GitHub Actions > Release](https://github.com/Princeyyyy/art-wallpaper/actions/workflows/release.yml)**.
2. Click **Run workflow**.
3. Select branch: `master`.
4. Enter target version (e.g., `4.1.7`).
5. Click **Run workflow**.

---

### Pipeline Mechanics & Job Breakdown

```
┌────────────────────────────────────────────────────────┐
│                      0. prepare                        │
│ - Validate semver format (e.g. 4.1.7)                  │
│ - Verify actor is repository owner                     │
└───────────────────────────┬────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│       1a. build-windows       │ │        1b. build-macos        │
│ - Runner: windows-latest      │ │ - Runner: macos-latest        │
│ - JDK 17 (Temurin)            │ │ - JDK 17 (Temurin)            │
│ - gradlew test                │ │ - ./gradlew test              │
│ - gradlew packageMsi          │ │ - ./gradlew packageDmg        │
│ - CertUtil SHA-256 generation │ │ - shasum -a 256 generation    │
│ - Smoke test MSI bundle       │ │ - Verify DMG mounting         │
└───────────────┬───────────────┘ └───────────────┬───────────────┘
                │                                 │
                └───────────────────┬─────────────┘
                                    ▼
┌────────────────────────────────────────────────────────┐
│                   2. publish-release                   │
│ - scripts/generate_release_notes.py (Gemini / fallback)│
│ - Strictly ZERO emojis in generated release text       │
│ - Create GitHub Release v4.1.7                         │
│ - Attach ArtWallpaper-4.1.7-Windows.msi + .sha256      │
│ - Attach ArtWallpaper-4.1.7-macOS.dmg + .sha256        │
│ - Push git tag v4.1.7                                  │
└────────────────────────────────────────────────────────┘
```

---

## 4. In-App Auto-Update Lifecycle & Installer Cleanup

### Update Detection (`UpdateManager.kt`)
1. Running clients poll GitHub API:
   `https://api.github.com/repos/Princeyyyy/art-wallpaper/releases/latest`
2. `UpdateManager.checkForUpdates()` compares the remote version against `AppInfo.version` using semver logic.
3. When a newer release is detected, the platform installer is downloaded in the background to:
   - Windows: `%USERPROFILE%\.artwallpaper\updates\ArtWallpaper-<version>-Windows.msi`
   - macOS: `~/.artwallpaper/updates/ArtWallpaper-<version>-macOS.dmg`

### Automatic Installer Pruning
To prevent downloaded installers from piling up on user disks:
- `UpdateManager.cleanupOldInstallers()` executes on application startup.
- Automatically deletes installer files from `.artwallpaper/updates` that are older than 24 hours or associated with older versions.

---

## 5. Post-Release Verification Runbook

Immediately after release publication:

1. **Verify Release Assets**:
   Visit `https://github.com/Princeyyyy/art-wallpaper/releases/latest` and confirm:
   - `ArtWallpaper-<version>-Windows.msi` and `ArtWallpaper-<version>-Windows.msi.sha256` are attached.
   - `ArtWallpaper-<version>-macOS.dmg` and `ArtWallpaper-<version>-macOS.dmg.sha256` are attached.
   - Release notes are formatted properly with zero emojis.
2. **Smoke Test Windows Installation**:
   - Download the `.msi` on a clean Windows machine.
   - Run installer, verify start menu shortcuts, auto-start registry entry, and launch to tray.
3. **Smoke Test macOS Installation**:
   - Download `.dmg` on macOS, mount disk image, drag to `/Applications`.
   - Verify menu bar accessory icon and wallpaper setting.
4. **Verify Client In-App Notification**:
   - Open an older client (e.g. `v4.1.4`) and confirm it displays the update notification banner and initiates silent download to `~/.artwallpaper/updates`.

---

## 6. Emergency Rollback & Hotfix Procedure

If a critical issue is identified in a newly deployed release:

### Emergency Rollback
1. Go to GitHub Releases and edit the faulty release.
2. Check **Set as a pre-release** or delete the release to immediately stop in-app auto-updaters from pulling the broken build.
3. Post an advisory in GitHub Discussions.

### Fast-Track Hotfix
1. Create a hotfix branch:
   ```bash
   git checkout -b fix/hotfix-issue master
   ```
2. Implement the fix and write a reproducing test in `src/test/kotlin/`.
3. Verify all tests pass: `./gradlew test`.
4. Bump patch version in `gradle.properties` (e.g. `4.1.7`).
5. Open PR, review, squash and merge into `master`.
6. Trigger the GitHub Actions release workflow for the new patch version.
