# ArcNova architecture plan

## Goal

A modular, Android-native archive workbench—not a one-format unpacker. The first engineering objective is a dependable foundation that can grow without tying the UI to a specific codec or archive family.

## Core separation

### 1. App / job orchestration
- Kotlin UI and Android Storage Access Framework.
- Persisted URI permissions where supported.
- Background extraction jobs with progress, pause/cancel semantics, and structured logs.
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

Candidate baseline codecs include stored, DEFLATE, LZMA, Zstandard, and LZ4, subject to dependency/license review and integration tests. SREP and LOLZ remain research targets, not available capabilities.

### 4. Filters and transforms
A pipeline may contain transforms before or after a codec (for example executable BCJ filters). The container metadata must define the exact order. Each transform is separately versioned and tested.

### 5. Pipeline runtime
- Bounded reusable buffers and back-pressure.
- Streaming to destination; no whole-archive ByteArray requirement.
- Cancellation and cleanup of temporary output.
- Optional parallelism only for independent blocks.
- Thermal and memory-aware worker limits on mobile devices.

### 6. Safety and verification
- Canonicalize output paths and block path traversal.
- Enforce configurable entry count, output size, expansion ratio, and nesting limits.
- Validate checksums and declared sizes when available.
- Write to temporary files and atomically finalize only after successful verification where Android filesystem semantics allow.
- Treat partial output as incomplete and report exact failure stage.

## Compatibility tiers

- **Tier 0 — Detect:** identify a format or codec signature.
- **Tier 1 — Inspect:** safely list metadata and volume structure.
- **Tier 2 — Extract:** decode and write validated output.
- **Tier 3 — Create:** encode archives in the format.
- **Tier 4 — Advanced:** solid blocks, multi-volume creation, filters, encryption, recovery, and parallel scheduling.

A format must not be marked supported beyond the highest tier with passing tests.

## Initial engineering sequence

1. Define Kotlin interfaces for container readers, codec engines, and extraction jobs.
2. Add a streaming source/sink abstraction that can work with Android content URIs.
3. Implement safe output path handling and bounded extraction limits.
4. Add a baseline codec/container combination with legal public fixtures.
5. Add split-volume discovery and missing-part diagnostics.
6. Add fuzzing and malformed-archive tests.
7. Only then add specialist pipelines such as FreeArc/SREP/LOLZ, once legal implementations and specifications are established.

## Non-goals

- Bundling game files or repack payloads.
- Bundling proprietary CLS DLL/EXE plugins without redistribution rights.
- Claiming support based solely on a successful compile or a UI demo.
- Claiming global first/only status without a documented competitor and compatibility survey.
