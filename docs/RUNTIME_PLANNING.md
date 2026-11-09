# Runtime planning foundation

This document describes the first platform-neutral runtime-planning code. It is not a bundled Wine/Proton runtime and cannot launch games by itself.

## What was added

- Explicit host ABI, Windows compatibility layer, x86-64 translator, graphics API and DirectX backend models.
- Device capability input for Vulkan, 32-bit execution, available RAM and thermal headroom.
- A deterministic candidate selector that rejects incompatible combinations instead of silently selecting them.
- Steam preference and explicit translator preference.
- Conservative worker recommendations bounded by CPU count, available memory and thermal headroom.
- Per-game runtime profiles with basic validation.
- Unit tests for selection, Vulkan/DirectX 12 rejection, 32-bit requirements, thermal throttling and profile validation.

## Performance policy

There is no universal fastest combination. Box64 vs FEX, Wine vs Proton, DXVK vs VKD3D-Proton, and driver choice vary by game, graphics API, device firmware and runtime build. The selector therefore treats preferences as hints, not measured truth. A future benchmark/profile database must record game build, device model/SoC/GPU, Android version, runtime and translator versions, graphics driver, settings, frame-time distribution, memory use, temperature and stability.

The initial worker heuristic is intentionally conservative and applies to ArcNova background jobs, not to the game renderer or translator thread pool. Runtime processes must receive their own resource budgets.

## Next integration gates

1. Add a real Android capability probe (ABI, Vulkan extensions/version, memory class, thermal status, supported ABIs).
2. Add runtime component manifests with exact source commit, license, ABI, checksum and install size.
3. Provision Wine/Proton and translators as separate versioned components; do not bundle until dependency licenses and distribution rights are audited.
4. Implement process lifecycle, prefix creation, storage mapping, logs and cleanup.
5. Integrate graphics backends only when their exact Android build and driver pairing pass device tests.
6. Build per-game profiles from reproducible tests, not guessed presets.

## Current limitation

No runtime candidate is registered in the shipping app yet. No Steam game, Windows installer or FitGirl repack is currently installable or playable through this code.


## Deeper repository audit — 2026-11-09

### Winlator WB64 source availability

The current WB64 README explicitly says its public source snapshot is up to version 7.1, while the current application/release line is newer and the maintainer does not update the source repository frequently. The tree contains a `Source-Code` directory with marker files stating that it is not the main repository, alongside prebuilt installable component archives. Therefore:
- Use the public source snapshot for architectural study only after pinning the exact commit and comparing it with the binary/release version.
- Do not assume current APK behavior is represented by the available source snapshot.
- Do not treat the prebuilt `.tzst` runtime archives as source or as automatically redistributable.
- Winlator's README points to per-shortcut settings and Box64 performance/stability presets. ArcNova should model these as per-game profile choices, then qualify each preset on actual games rather than copying names or claiming equivalent speed.

### Steamlator build and distribution boundary

Steamlator's README describes Steam as already included in its APK and says its build requires separate runtime/container archives downloaded from a Mega folder (including Proton and filesystem images). This is a useful integration reference but a warning for ArcNova:
- Study the app's orchestration and Steam launch flow.
- Do not copy its prebuilt Steam assets, runtime bundles or Mega archives into ArcNova.
- Build reproducibility requires identifying the exact upstream sources and licenses for every payload, not merely the top-level MIT application license.
- ArcNova should use official Steam client acquisition/update mechanisms and maintain a clean separation between app code and proprietary Steam components.

### Vapor: deeper architectural reference

Repository: https://github.com/giminycricket0-blip/vapor  
Default branch observed: `Vapor`; top-level license metadata: GPL-3.0.

The repository exposes a much broader Android runtime implementation than a simple launcher. Its tree includes:
- Native Android Steam-oriented UI and catalog/install flows.
- Runtime and Linux launcher components with device tests.
- Graphics driver packages and wrapper variants.
- Box64 configuration profiles.
- Steam bootstrap/client bridge components in C++ and Rust.
- Controller integration and game-session lifecycle tests.
- A documented qualification protocol that separates source-level checks, emulator probes and physical-device gameplay evidence.

Its build documentation also shows the real cost of a production-grade stack: Android NDK/CMake, Rust for `aarch64-linux-android`, cross-compilation for Windows helper executables, Git LFS assets and multiple Git submodules. This is not a small Kotlin-only dependency that can be dropped into ArcNova.

Decision:
- Vapor is currently the richest architecture/qualification reference found for Steam-first Android gaming.
- Because it is GPL-3.0, copying or adapting its application code may require ArcNova's combined distribution to comply with GPL-3.0. Keep it as a reference until the project's licensing strategy is explicitly chosen.
- Do not import its Steam client binaries/assets or prebuilt driver packages. Their provenance and redistribution rights must be checked independently.
- Extract the engineering lessons: explicit runtime lifecycle, device capability probing, per-game compatibility data, component manifests, driver qualification, input/audio testing and repeatable physical-device tests.

### Proton build reality

Valve's Proton README describes Proton as a Steam-client compatibility tool built around Wine and recommends using the Proton supplied by Steam for ordinary users. Its source tree uses many submodules, including Wine, DXVK, VKD3D-Proton, Vulkan components, GStreamer, FFmpeg and others. Its build instructions expect a containerized Linux build environment.

Decision:
- Proton is a source/build-system reference and a runtime component candidate, not a ready-made Android ARM64 package.
- A real Android port must account for the ARM64 host, translated x86-64 userland, graphics stack, Android process model, storage, audio and controller paths.
- Every submodule is an independent license/provenance audit item. Never infer all dependency licenses from Proton's top-level license.

### Performance measurement rules

For every benchmark, record:
- exact game title/build and store/installer source;
- exact tablet model, SoC/GPU, Android build and available RAM;
- runtime and Wine/Proton version;
- Box64/FEX version and configuration;
- graphics driver, DXVK/VKD3D/WineD3D version and API;
- resolution, graphics settings, frame pacing and power mode;
- median and 1% low frame rate, frame-time variance, RAM, CPU/GPU load, temperature, battery drain and crash/relaunch behavior.

Do not select a default runtime solely because it wins one FPS test. Prefer stable frame pacing and repeatable launch/gameplay, then optimize per game. Thermal throttling and memory pressure can reverse an initially faster configuration.
