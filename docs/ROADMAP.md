# ArcNova development roadmap

This roadmap is the source of truth for sequencing. The current Android screen is an engineering harness, not the final product UI. Debug APKs produced by CI are internal validation builds, not releases.

## Current phase — Phase 1: Core file and archive foundations

**Goal:** build reliable, reusable engines before investing in the final interface.

In scope:
- file signature detection and streaming file analysis;
- SHA-256 and file comparison;
- operation-level capability registry that never advertises planned work as available;
- bounded ZIP inspection and extraction;
- archive risk indicators clearly labelled as heuristics, not antivirus;
- safe cancellation, temporary output, cleanup and rollback;
- automated tests for malformed inputs, limits, path traversal, duplicates, stream ownership and failure recovery.

Exit gate:
- all unit tests pass;
- extraction and inspection have explicit resource limits and reliable cleanup/rollback;
- no partial output is silently presented as a completed operation;
- code review confirms capability labels match actual implementations.

## Phase 2 — General archive engines

Add formats in deliberate groups (for example TAR/GZIP, then 7z, then RAR where licensing permits). Each format needs real parsing/decoding, fixtures, integrity checks, limits, cancellation, tests and capability-registry updates. Filename detection alone never counts as format support.

## Phase 3 — Specialist compression and game containers

Research and implement FreeArc/multipart and game package formats such as Unreal PAK/IoStore, Unity AssetBundles, Source VPK, XP3 and RPA where specifications and licensing permit. SREP, LOLZ and Oodle remain blocked until lawful implementation or redistribution rights are verified.

## Phase 4 — Installer intelligence

Identify installer technologies, inspect manifests and payloads, safely extract supported formats, and build an isolated installer workflow. Do not execute unknown installers in the app process.

## Phase 5 — Compatibility runtime

Integrate and validate runtime components, ABI translation, graphics/audio/input adapters, isolated prefixes and per-title profiles. Runtime planning/probing is not the same as a working Wine/Proton runtime. Compatibility is qualified title by title.

## Phase 6 — Final product UI and brand system

Only after core workflows and stable engine interfaces are established, replace the engineering harness with the finished ArcNova experience: professional ArcNova wordmark (capital A and N in the same accent color), launcher icon, light/dark system, responsive tablet/phone layouts, file manager, progress/job center, panels, accessibility and polished empty/error states. Do not mistake the current plain screen for the intended design.

## Phase 7 — Device qualification

Run real-device tests on target Android devices, including OnePlus Pad 2 / Snapdragon 8 Gen 3: large files, SAF providers, cancellation, storage pressure, memory, battery/thermal behavior, Vulkan/runtime compatibility and recovery. CI success alone does not pass this gate.

## Phase 8 — Release readiness

Complete security review, privacy/data-flow review, dependency and license notices, signing, versioning, crash diagnostics, release notes and reproducible release checks. Only then prepare a public beta/release.

## Build and validation policy

- CI runs unit tests and assembles a Debug APK after code changes. This is a developer feedback loop, not a user-facing release.
- Do not spend time polishing the prototype UI before the engine contracts are stable, except for small controls needed to exercise and validate functionality.
- Do not mark a capability implemented until its actual operation has tests; do not mark it device-validated until it passes on a physical device.
- Preserve existing files and backups; extend the current architecture incrementally rather than restarting the project.
- Prefer batches of focused tasks, but stop and fix a failing CI run before stacking unrelated changes.
