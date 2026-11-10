# Contributing to Art Wallpaper

Thank you for your interest in contributing to **Art Wallpaper**!

Art Wallpaper brings daily curated fine art museum masterpieces to Windows and macOS desktops. We welcome contributions from developers, designers, and art enthusiasts worldwide to help build a world-class, privacy-first, open-source desktop experience.

Please take a moment to review this document before submitting contributions.

---

## Code of Conduct

All contributors, maintainers, and community members are expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project, you agree to uphold a welcoming, respectful, and inclusive environment.

---

## Governance and Project Rules

To ensure code quality, security, and project stability, the following rules are strictly enforced:

1. **`master` is a Protected Branch**:
   - Direct pushes to `master` are strictly disabled for all contributors.
   - All code changes must be introduced via Pull Requests.
2. **Owner-Only Merges**:
   - The repository owner ([@Princeyyyy](https://github.com/Princeyyyy)) is the sole authority with permissions to approve and merge pull requests.
3. **Owner-Only Deployments & Releases**:
   - Release workflows and binary generation (`release.yml`) are manually triggered exclusively by the repository owner.
4. **Mandatory CI Testing**:
   - Every Pull Request triggers automated CI testing on **Windows**, **macOS**, and **Web (Next.js)**.
   - Pull Requests cannot be merged until all automated test checks pass green.
5. **Zero Test Contamination**:
   - Unit tests must NEVER write to or alter the user's home directory (`~/.artwallpaper`). All tests must use isolated temporary directories via `Files.createTempDirectory(...)`.

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK)**: Version 17 (Eclipse Temurin or Microsoft OpenJDK recommended).
- **Gradle**: Handled automatically via `./gradlew` (Gradle 8.10 wrapper).
- **Operating System**: Windows 10/11 or macOS 12+ (both platforms are natively supported).
- **Node.js** (Optional, for web landing page): Version 20+.

### 1. Fork & Clone

1. Fork the repository on GitHub: `https://github.com/Princeyyyy/art-wallpaper/fork`.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/<your-username>/art-wallpaper.git
   cd art-wallpaper
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/Princeyyyy/art-wallpaper.git
   ```

### 2. Create a Feature Branch

Always branch off the latest upstream `master`:

```bash
git fetch upstream
git checkout -b feat/your-feature-name upstream/master
# OR for bug fixes:
git checkout -b fix/issue-description upstream/master
```

#### Branch Naming Guidelines
- `feat/<name>`: New feature or UI component.
- `fix/<issue>`: Bug fix or edge-case handling.
- `perf/<name>`: Performance enhancement.
- `docs/<name>`: Documentation improvements.
- `refactor/<name>`: Code restructuring without behavior changes.

---

## Local Development and Testing

### Running the Application

To launch the desktop application in development mode:

```bash
./gradlew run
```

### Running Tests

Run the full test suite across all modules:

```bash
# Run unit and integration tests
./gradlew test

# Compile without running tests
./gradlew compileKotlin
```

### Testing Packaging Locally

To package native platform bundles:

```bash
# Windows MSI installer (Windows only)
.\gradlew packageMsi

# macOS DMG disk image (macOS only)
./gradlew packageDmg
```

---

## Coding Standards and Architectural Conventions

### 1. Architecture & State Management
- Built with **Kotlin 2.1.0** and **Jetpack Compose Desktop 1.8.0-alpha03**.
- State flows unidirectionally using Kotlin Coroutines `StateFlow` and Compose `collectAsState()`.
- Thread safety: Use `Mutex` and `withContext(Dispatchers.IO)` for filesystem and network I/O.
- Coroutines: Never catch generic `Exception` without rethrowing `CancellationException`:
  ```kotlin
  try {
      doWork()
  } catch (e: CancellationException) {
      throw e
  } catch (e: Exception) {
      logger.error("Failed doing work", e)
  }
  ```

### 2. Wallpaper & Museum Picture Frame Engine
- Wallpapers are rendered at exact physical screen dimensions via `ImageProcessor.kt`.
- The background consists of the authentic silky ambient bokeh blur and vignette. **Do not modify the background styling without design discussion.**
- The artwork foreground must include the handcrafted walnut picture frame, gilded brass fillet inlay, archival sight-edge bevel, and ambient gallery drop shadow.

### 3. Operating System Integration
- **Windows**: Use JNA (`User32`, `Advapi32Util`). Windows wallpaper registry key: `HKCU\Control Panel\Desktop\Wallpaper`.
- **macOS**: Use AppleScript via `ProcessBuilder("osascript", "-e", ...)`.
- All OS integrations must fail gracefully and provide fallbacks.

### 4. Single Source of Truth for Versioning
- Version numbers are defined exclusively in `gradle.properties`:
  ```properties
  appVersion=4.1.6
  ```
- Never hardcode version strings in source files. Source code dynamically reads `AppInfo.version` from generated properties.

---

## Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

- `feat: add custom wallpaper fit mode for ultra-wide monitors`
- `fix(service): filter out coroutine cancellation from error logs`
- `perf(image): optimize separable box blur passes`
- `docs: update contributing and development guides`
- `test: add isolated unit test for registry wallpaper sync`

---

## Submitting a Pull Request

1. **Rebase on Upstream**:
   ```bash
   git fetch upstream
   git rebase upstream/master
   ```
2. **Verify Tests**: Ensure `./gradlew test` passes cleanly with 0 failures.
3. **Push to Your Fork**:
   ```bash
   git push origin feat/your-feature-name
   ```
4. **Open Pull Request**:
   - Go to `https://github.com/Princeyyyy/art-wallpaper`.
   - Open a PR from your feature branch to `master`.
   - Complete all sections of the [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).
5. **Code Review**:
   - The repository owner will review your PR, provide constructive feedback, and merge once all criteria are satisfied.

Thank you for helping make Art Wallpaper an extraordinary gallery experience for everyone.
