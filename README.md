# ArcNova

**ArcNova is an Android-first, modular archive and compression engine project.**

The ambition is bigger than extracting one repack or supporting one game: build a reliable, extensible Android toolchain for inspecting, testing, creating, and extracting archive formats and compression pipelines that are difficult or unsupported on Android today.

SREP + LOLZ / FreeArc-style pipelines are an early research challenge and compatibility target—not the project's entire purpose.

> **Current reality:** early scaffold and research stage. SREP and LOLZ are not implemented. No claim of compatibility or first-in-the-world status is made until independently verified against real fixtures and existing tools.

## Project principles

- **Android-first:** prioritize modern Android devices, especially ARM64, without requiring a desktop Windows environment.
- **Modular engines:** separate archive containers, codecs, filters, checksums, split-volume handling, and the user interface.
- **Streaming and large-file safety:** avoid loading multi-gigabyte archives into memory; support cancellation, resumability where possible, and bounded memory.
- **Integrity first:** verify sizes and checksums when available; never silently report a partial extraction as success.
- **Safe extraction:** prevent path traversal, suspicious links, decompression bombs, and accidental overwrite; write to a user-selected destination.
- **Legal and reproducible:** integrate only code with verified licenses and provenance; keep proprietary plugins and copyrighted payloads out of the repository.
- **Evidence over claims:** publish compatibility matrices, fixture provenance, test results, and device benchmarks.

## Planned architecture

1. **Android app layer** — Storage Access Framework, permissions, job queue, progress, cancellation, logs, destination selection.
2. **Archive/container layer** — format detection, headers, solid blocks, multi-volume discovery, metadata and file-entry mapping.
3. **Codec engine layer** — independently registered decoders/encoders (for example stored, DEFLATE, LZMA, Zstandard, LZ4, and future licensed codecs).
4. **Filter/preprocessor layer** — BCJ and other transforms where specifications and implementations permit.
5. **Pipeline scheduler** — connect filters and codecs in the correct order; stream bounded buffers; coordinate worker threads and back-pressure.
6. **Verification and safety layer** — checksums, output validation, limits, safe paths, error recovery.
7. **Test and benchmark lab** — legal fixtures, malformed inputs, fuzzing, memory/thermal/performance measurements on ARM64.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the technical plan and [docs/ENGINE_STATUS.md](docs/ENGINE_STATUS.md) for verified implementation status.

## Milestones

- [x] Create Android project scaffold and CI build workflow.
- [x] Document initial SREP / LOLZ feasibility and licensing blockers.
- [ ] Establish the modular engine API and archive-inspection baseline.
- [ ] Add a legal, tested baseline codec and streaming extraction path.
- [ ] Add split-volume/container support with fixture-based tests.
- [ ] Resolve SREP through a clearly licensed implementation or permission.
- [ ] Resolve LOLZ through a redistributable implementation or documented compatibility boundary.
- [ ] Publish device benchmarks and release APKs only after validation.

## Build

Requires JDK 17, Android SDK 35, and Gradle:
```bash
gradle assembleDebug
```

No game data, FitGirl installer binaries, or proprietary CLS executables are included. Original archive parts must remain untouched; extracted output will go to a user-selected destination.

## Founding and contribution

ArcNova is being initiated collaboratively by its human project owner and ChatGPT as a development/research collaborator. Technical decisions, code provenance, tests, and releases will be recorded transparently in the repository.
