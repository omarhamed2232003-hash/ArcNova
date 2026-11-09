# ArcNova

> **Important:** ArcNova is software — it is not a game. This repository contains no games, game data, installers, ROMs, repacks, or copyrighted game files. It contains only ArcNova's source code, documentation, and project resources. Any game or installer files used for testing must be supplied separately by users who have the legal right to use them.

**ArcNova is an Android-first archive, compression, PC game installation and compatibility platform.**

Its two primary game compatibility goals are **Steam PC games** and **user-provided FitGirl repacks**, alongside general Windows games/software installers. The project combines a universal archive manager, specialist decompression pipelines, and a managed compatibility runtime for Android ARM64.

SREP + LOLZ / FreeArc-style pipelines are first-class FitGirl targets. Steam/Proton is a first-class runtime target. Neither is implemented yet.

> **Current reality:** early scaffold and research stage. ArcNova does not currently extract FitGirl repacks, install Steam games, execute Windows installers, or launch PC games. Compatibility must be demonstrated title-by-title with reproducible tests.

## Product tracks

1. **Universal archive manager** — detect, inspect, test, extract, and eventually create common archive formats and multi-part archives.
2. **FitGirl / specialist repack pipeline** — inspect split volumes, parse supported containers (including FreeArc where validated), identify codec/filter chains, and decode with legally distributable implementations. SREP and LOLZ are major targets, not currently implemented.
3. **Steam PC game path** — manage a compatible runtime, use Steam through Valve's official client/distribution flow, install user-owned games, manage per-game Proton/Wine profiles, and launch compatible titles.
4. **Standalone PC installer path** — detect supported setup packages, distinguish unpacking from setup execution, and run supported Windows installers in an isolated compatibility environment.

FitGirl is treated as a **user-supplied archive/installer format target**, not a source of game payloads for this repository. ArcNova will not host or distribute copyrighted game files or proprietary repack plugins. Users must provide game files they are legally entitled to use.

## Architecture

- **Android orchestrator:** Storage Access Framework, permissions, job queue, progress, cancellation, logs and destination selection.
- **Archive/container layer:** signatures, headers, solid blocks, split-volume discovery and entry metadata.
- **Codec/filter pipeline:** independent decoders/encoders and transforms (DEFLATE, LZMA, Zstandard, LZ4, BCJ and future licensed specialist codecs).
- **FitGirl/installer layer:** FreeArc research and validated installer handlers.
- **Runtime manager:** versioned Wine/Proton prefixes, x86 translation, graphics/audio/input components and per-game profiles.
- **Steam bridge:** official Steam client flow, library discovery, game install/launch state and controller mapping. Steam credentials never pass through ArcNova servers.
- **Graphics adapters:** Vulkan capability checks; Turnip where supported; DXVK for D3D9/10/11; VKD3D-Proton for D3D12; WineD3D fallback, subject to exact device validation.
- **Verification/safety:** checksums, safe paths, output limits, temporary output, cleanup and installation manifests.
- **Compatibility lab:** legal fixtures, smoke tests, per-device/per-game profiles, diagnostics and benchmarks.

## Runtime performance policy

There is no universally fastest Wine/Proton + Box64/FEX + graphics-driver combination. ArcNova will probe device capabilities and choose only compatible candidates, then prefer measured per-game profiles. It must account for ARM64 ABI, Vulkan extensions/version, memory pressure, thermal headroom, audio, input and storage behavior. Generic heuristics are fallbacks, not benchmark claims.

## Compatibility tiers

- **Tier 0 — Detect:** identify archive, installer, game source and runtime requirements.
- **Tier 1 — Inspect:** list entries, volumes, manifests and metadata.
- **Tier 2 — Extract:** decode and verify files.
- **Tier 3 — Install:** execute supported setup logic or apply a validated recipe.
- **Tier 4 — Launch:** start the game in a managed runtime.
- **Tier 5 — Playable:** pass defined gameplay, graphics, audio, controls, save/load and stability tests.

“Steam client opens” does not mean a Steam game is playable. “FitGirl files extracted” does not mean setup completed.

## Current implementation

- Android project scaffold and CI build.
- Bounded streaming transfer primitive and unit tests.
- Platform-neutral runtime candidate planner with ABI/Vulkan/DirectX/32-bit/RAM/thermal checks and per-game profile validation.\n- Initial Android capability probe for supported ABIs, memory, CPU count, declared Vulkan feature version and thermal status; native Vulkan extension validation remains pending.
- Research records for SREP/LOLZ and external runtime/archive candidates.

The runtime planner is not an installed runtime. No Steam game or FitGirl repack is currently installable/playable through ArcNova.

## Build

Requires JDK 17, Android SDK 35 and Gradle:
```bash
gradle testDebugUnitTest
gradle assembleDebug
```

No game data, Steam proprietary assets, FitGirl installer binaries or proprietary CLS executables are included. User-provided archives remain untouched; output will go to a user-selected destination.

## Source and licensing

Third-party components are integrated only after auditing their exact source commit, submodules, bundled binaries, dependencies, license obligations and provenance. Top-level project licenses do not automatically cover dependencies. Required notices and source obligations will be preserved.

## Founding

ArcNova is being initiated collaboratively by its human project owner and ChatGPT as a development/research collaborator. Technical decisions, source provenance, tests and releases will be recorded transparently.


## Security, privacy and legal notices

- [Security policy](SECURITY.md)
- [Privacy notice](PRIVACY.md)
- [Legal, safety and compatibility disclaimer](DISCLAIMER.md)
- [Third-party notices and component provenance](THIRD_PARTY_NOTICES.md)
- [Contributing guide](CONTRIBUTING.md)

These documents describe the current early-stage scaffold and must be updated as capabilities and data handling evolve.

## Releases

Push a version tag such as `v0.1.0-alpha.1` to trigger the release workflow. It runs unit tests and builds the APK before publishing a GitHub Release with:
- the debug APK (experimental and not a production-signed build);
- SHA-256 checksum file;
- source ZIP and TAR.GZ snapshots for that exact tag;
- automatically generated release notes.

GitHub also provides source archives for repository tags. Stable releases must not be published until signing, compatibility qualification, dependency notices, and release checks are ready. No release is considered production-ready merely because CI builds successfully.
