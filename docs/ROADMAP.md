# ArcNova development roadmap

This roadmap is the source of truth for sequencing. The current Android screen is an engineering harness, not the final product UI. Debug APKs produced by CI are internal validation builds, not releases.

## Phase 1 — Core file and archive foundations (COMPLETED — 2026-11-10)

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

**Exit-gate result:** Satisfied on `main` commit `700bb50e305a8bd478fd6a05e56ef0a23d38f3f6`; GitHub Actions [Run #42899934445](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42899934445) passed `gradle testDebugUnitTest`. Physical-device and real SAF-provider validation remain in Phase 7 and are not claimed as complete.

## Phase 2 — General archive engines

Add formats in deliberate groups (for example TAR/GZIP, then 7z, then RAR where licensing permits). Each format needs real parsing/decoding, fixtures, integrity checks, limits, cancellation, tests and capability-registry updates. Filename detection alone never counts as format support.

**Current implementation batch:** TAR extraction and single-file GZIP decompression use the existing Commons Compress 1.28.0 dependency. 7z extraction uses Commons Compress 1.28.0 with XZ for Java 1.12; only implemented method/filter chains are accepted, encrypted entries and multipart volumes are rejected, and LZMA/LZMA2 decoder memory is capped at 256 MiB. The engineering picker routes ZIP, 7z, TAR, and GZIP to their engines; ZIP inspection remains ZIP-only. Unit tests passed in [Run #42903071257](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42903071257) on commit `f93c7b3dd9e58860bf21205db91bc98bb08f6030`; the major-change internal preview is being built. No physical-device validation is claimed.

## Phase 3 — Specialist compression and game containers

Research and implement FreeArc/multipart and game package formats such as Unreal PAK/IoStore, Unity AssetBundles, Source VPK, XP3 and RPA where specifications and licensing permit. SREP, LOLZ and Oodle remain blocked until lawful implementation or redistribution rights are verified.

## Phase 4 — Installer intelligence

Identify installer technologies, inspect manifests and payloads, safely extract supported formats, and build an isolated installer workflow. Do not execute unknown installers in the app process.

## Phase 5 — Compatibility runtime

Integrate and validate runtime components, ABI translation, graphics/audio/input adapters, isolated prefixes and per-title profiles. Runtime planning/probing is not the same as a working Wine/Proton runtime. Compatibility is qualified title by title.

## Phase 6 — Final product UI and brand system (MANDATORY — owner-confirmed)

This is a required full product phase, not optional polish and not a small restyle. The owner explicitly confirmed on 2026-11-09 that it must remain in the phase plan and agent handoff and must never be forgotten or skipped. Only after core workflows and stable engine interfaces are established, replace the engineering harness with the finished ArcNova experience: professional ArcNova wordmark (capital A and N in the same accent color), launcher icon, light/dark system, responsive tablet/phone layouts, file manager, progress/job center, panels, accessibility and polished empty/error states. Do not mistake the current plain screen for the intended design. Deliver a complete premium modern Windows-inspired workspace: responsive tablet/phone layouts, file manager, archive browser, task/progress center, polished navigation and panels, coherent design system, light/dark modes, refined transitions/motion, accessible empty/loading/error states, and useful pointer/keyboard/context-menu/drag-drop interactions where appropriate. Include original final wordmark, launcher icon and consistent brand artwork. Phase 6 must be completed before Phase 7 device qualification and Phase 8 release readiness.

## Phase 7 — Device qualification

Run real-device tests on target Android devices, including OnePlus Pad 2 / Snapdragon 8 Gen 3: large files, SAF providers, cancellation, storage pressure, memory, battery/thermal behavior, Vulkan/runtime compatibility and recovery. CI success alone does not pass this gate.

## Phase 8 — Release readiness

Complete security review, privacy/data-flow review, dependency and license notices, signing, versioning, crash diagnostics, release notes and reproducible release checks. Only then prepare a public beta/release.

## Build and validation policy

- Every development commit runs `gradle testDebugUnitTest`. For a major implementation change on the development branch, mark the commit message with `[major-change]`; after unit tests pass, CI automatically assembles and uploads an internal preview APK artifact retained for 7 days. Use this marker for substantial engine integrations, architecture changes, major UI work, or other changes whose behavior needs an end-to-end visual/runtime check; do not use it for routine fixes or documentation-only commits.
- These recurring internal previews are validation artifacts, not releases, and do not replace unit tests or Phase 7 physical-device qualification. The one-off personal preview exception remains distinct from these CI artifacts. Do not build or distribute a release APK until all eight phases are complete and the owner explicitly authorizes final packaging.
- Do not spend time polishing the prototype UI before the engine contracts are stable, except for small controls needed to exercise and validate functionality.
- Do not mark a capability implemented until its actual operation has tests; do not mark it device-validated until it passes on a physical device.
- Preserve existing files and backups; extend the current architecture incrementally rather than restarting the project.
- Prefer batches of focused tasks, but stop and fix a failing CI run before stacking unrelated changes.
