# Project Governance

This document outlines the governance model, roles, and decision-making processes for the **Art Wallpaper** open-source project.

---

## Governance Model: Benevolent Dictator for Life (BDFL)

Art Wallpaper follows the **Benevolent Dictator for Life (BDFL)** governance model.

- **Project Lead & Owner**: [@Princeyyyy](https://github.com/Princeyyyy)
- The Project Lead has ultimate decision-making authority over project direction, architectural roadmap, community contributions, code merges, and releases.

---

## Roles and Responsibilities

### 1. Repository Owner (`@Princeyyyy`)
- **Merging Authority**: Sole authority to approve and merge Pull Requests into the `master` branch.
- **Release Authority**: Sole authority to trigger GitHub Actions release workflows (`release.yml`), sign binaries, and publish releases.
- **Security & Infrastructure**: Manages repository secrets, signing keys, and repository settings.
- **Roadmap & Design**: Decides on UX aesthetics, feature prioritization, and major version increments.

### 2. Contributors
- Any member of the open-source community who submits issues, discussions, documentation, or Pull Requests.
- Responsibilities:
  - Adhere to the [Code of Conduct](CODE_OF_CONDUCT.md).
  - Follow the [Contributing Guide](CONTRIBUTING.md).
  - Submit all code changes via topic branches (`feat/*`, `fix/*`).
  - Write isolated, comprehensive unit tests for all proposed changes.

---

## Branch Protection and Merging Rules

1. **`master` Branch Protection**:
   - Direct pushes to `master` are strictly prohibited.
   - All code enters `master` through Pull Requests approved by the Project Lead.
2. **Mandatory Automated CI Checks**:
   - Every Pull Request must pass the full suite of automated CI tests on Windows, macOS, and Web before it can be merged.
3. **Commit Cleanliness**:
   - Commits should adhere to Conventional Commits standards.
   - Pull Requests are merged using **Squash and Merge** or **Rebase and Merge** to maintain a linear, readable history.

---

## Release Policy

- Releases are decoupled from everyday Pull Request merges.
- Merges into `master` do not automatically publish new desktop builds.
- New releases are triggered exclusively by the Project Lead via GitHub Actions `workflow_dispatch` following quality audits and release smoke testing.
