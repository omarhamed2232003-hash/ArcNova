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
