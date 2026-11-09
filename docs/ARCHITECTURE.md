# ArcNova architecture plan

## Goal

A modular, Android-native archive, compression, and game/software installation platform. It must serve everyday archive tasks while also researching specialist pipelines such as FreeArc/SREP/LOLZ and supporting installation of selected Windows PC games and applications on Android.

## Core subsystems

### 1. App / job orchestration
- Kotlin UI and Android Storage Access Framework.
- Persisted URI permissions where supported.
- Background jobs with progress, cancellation, structured logs, and clear stages.
- Destination selection and overwrite policy.
- Never require broad storage access when a user-selected document tree is sufficient.

### 2. Container readers
A container reader understands how an archive stores metadata and compressed blocks. It must not implement codec internals itself.
- Probe/detect format from bytes and metadata, not filename extension alone.
- Parse entries and solid-block relationships.
- Discover ordered split volumes and detect missing/duplicate parts.
- Expose bounded input streams or seekable sources to the pipeline.
- Reject malformed offsets, integer overflow, and out-of-range reads.

### 3. Codec registry
Each codec is an independent module with:
- Stable identifier and version.
- Input/output contract and streaming capability.
- Memory requirements and supported architectures.
- License/provenance record.
- Conformance fixtures and expected checksums.
- Explicit unsupported/error states (never silently substitute a different codec).

Candidate baseline codecs include stored, DEFLATE, LZMA, Zstandard, and LZ4, subject to dependency/license review and integration tests. SREP and LOLZ are specialist research targets, not available capabilities.

### 4. Filters and transforms
A pipeline may contain transforms before or after a codec (for example executable BCJ filters). Container metadata must define exact order. Each transform is separately versioned and tested.

### 5. Installer detection and extraction
This subsystem handles Windows installer packages as a separate concern from archive codecs.
- Identify known installer families by signatures and validated metadata, not extensions alone.
- Support safe unpacking of selected installer formats where a native parser exists.
- For custom or script-driven installers, do not pretend that unpacking files is equivalent to executing setup logic.
- Capture required files, registry actions, dependencies, shortcuts, and installation paths when supported.
- Keep per-installer logs and stage outputs in a managed workspace.
- Clearly report whether the result is merely unpacked, setup-complete, or launch-tested.

Initial installer families to investigate: Inno Setup, MSI, InstallShield, and common repack wrappers. Each needs independent fixtures and license review.

### 6. Windows compatibility runtime adapter
Some installers are executable Windows programs (for example setup.exe) and cannot be handled by archive extraction alone. ArcNova therefore needs an optional runtime adapter that can provision and manage a compatible environment.
- Research Wine plus x86 translation (Box86/Box64) and Android graphics integration as candidate components.
- Keep the runtime isolated from the archive/codec engine APIs.
- Track runtime version, ABI, graphics backend, environment, dependencies, and per-game profile.
- Do not assume every setup.exe or game will work. Drivers, DRM, launchers, APIs, CPU instructions, and graphics requirements can block execution.
- Prefer integration through well-understood components with compatible licenses over writing a Windows compatibility layer from scratch.
- Do not bundle or redistribute third-party binaries until their exact licenses and obligations are reviewed.

### 7. Pipeline runtime
- Bounded reusable buffers and back-pressure.
- Streaming to destination; no whole-archive ByteArray requirement.
- Cancellation and cleanup of temporary output.
- Optional parallelism only for independent blocks.
- Thermal and memory-aware worker limits on mobile devices.
- Codec dictionary/workspace memory is accounted for separately from transfer-buffer memory.

### 8. Safety, verification, and installation state
- Canonicalize output paths and block path traversal.
- Enforce configurable entry count, per-file output size, total output size, expansion ratio, and nesting limits.
- Validate checksums and declared sizes when available.
- Write to temporary files and atomically finalize only after successful verification where Android filesystem semantics allow.
- Keep installer workspaces isolated; avoid silently overwriting user data.
- Maintain an installation manifest so users can inspect and remove files ArcNova installed where feasible.
- Treat partial output as incomplete and report exact failure stage.

## Compatibility tiers

- **Tier 0 — Detect:** identify a format, installer family, or codec signature.
- **Tier 1 — Inspect:** safely list metadata, volume structure, and installer contents where possible.
- **Tier 2 — Extract:** decode and write validated output.
- **Tier 3 — Install:** execute supported setup logic or apply a validated installation recipe.
- **Tier 4 — Launch:** start the installed game/application in a compatible runtime.
- **Tier 5 — Advanced:** create archives, solid blocks, multi-volume creation, filters, encryption, recovery, and optimized scheduling.

A format or game must not be marked supported beyond the highest tier with passing tests. “Installer extracted” is not the same as “game installed”, and “game installed” is not the same as “game launches”.

## Initial engineering sequence

1. Define Kotlin interfaces for container readers, codec engines, installer handlers, runtime adapters, and extraction/install jobs.
2. Add streaming source/sink abstractions for Android content URIs.
3. Add safe output path handling and bounded extraction limits.
4. Add a real, license-reviewed baseline archive backend with legal fixtures.
5. Add installer-family detection and safe unpacking for a small validated set.
6. Prototype a Windows compatibility adapter using license-reviewed components.
7. Add split-volume discovery and missing-part diagnostics.
8. Add fuzzing, malformed-archive, installer, and per-game compatibility tests.
9. Research specialist FreeArc/SREP/LOLZ pipelines only with legal implementations and exact format compatibility evidence.

## Non-goals

- Bundling game files or repack payloads.
- Bundling proprietary CLS DLL/EXE plugins without redistribution rights.
- Claiming universal Windows game compatibility.
- Claiming support based solely on a successful compile or a UI demo.
- Claiming global first/only status without a documented competitor and compatibility survey.
