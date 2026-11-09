# ArcNova

**ArcNova is an Android-first archive, compression, and game/software installation platform.**

The ambition is bigger than extracting one repack or supporting one game: build a reliable, extensible Android toolchain for inspecting, testing, creating, and extracting archive formats and compression pipelines—including difficult specialist pipelines—and for installing compatible Windows games and applications from user-provided installers.

SREP + LOLZ / FreeArc-style pipelines are a major specialist compatibility target, not the project's entire purpose. General archive support remains equally important.

> **Current reality:** early scaffold and research stage. SREP and LOLZ are not implemented. Windows installer execution is not implemented. No claim of compatibility or first-in-the-world status is made until independently verified against real fixtures and existing tools.

## Product vision

ArcNova has three cooperating capabilities:

1. **Universal archive manager** — detect, inspect, test, extract, and eventually create common archive formats and multi-part archives.
2. **Specialist codec pipeline** — support codec chains and filters such as FreeArc/SREP/LOLZ when a legally redistributable implementation and compatible specifications are available.
3. **Game and software installer** — identify installer types, unpack supported installer formats, and optionally run Windows setup programs inside a managed compatibility environment. The goal is to install and prepare supported PC games on Android—not to promise every Windows game will run.

## Project principles

- **Android-first:** prioritize modern Android devices, especially ARM64, without requiring a desktop PC.
- **Modular engines:** separate archive containers, codecs, filters, installer handlers, compatibility runtimes, checksums, split-volume handling, and UI.
- **Streaming and large-file safety:** avoid loading multi-gigabyte archives into memory; support cancellation, bounded memory, and resumability where possible.
- **Integrity first:** verify sizes and checksums when available; never silently report a partial extraction as success.
- **Safe installation:** isolate installer workspaces, request explicit destination permissions, prevent path traversal, track installed files, and provide cleanup/rollback where feasible.
- **Compatibility honesty:** distinguish “installer extracted”, “setup completed”, and “game launched successfully”. These are separate outcomes.
- **Legal and reproducible:** integrate only code with verified licenses and provenance; keep proprietary plugins and copyrighted game payloads out of the repository.
- **Evidence over claims:** publish compatibility matrices, fixture provenance, test results, and device benchmarks.

## Planned architecture

1. **Android app layer** — Storage Access Framework, permissions, job queue, progress, cancellation, logs, destination selection.
2. **Archive/container layer** — format detection, headers, solid blocks, multi-volume discovery, metadata and file-entry mapping.
3. **Codec engine layer** — independently registered decoders/encoders (for example stored, DEFLATE, LZMA, Zstandard, LZ4, and future licensed codecs).
4. **Filter/preprocessor layer** — BCJ and other transforms where specifications and implementations permit.
5. **Installer detection and extraction layer** — classify setup packages (including supported Inno Setup, MSI, InstallShield or custom formats only after validation), extract files where possible, and preserve installer metadata.
6. **Windows compatibility runtime adapter** — optional managed Wine + x86 translation + graphics stack for Windows setup executables and supported games. This is a distinct subsystem, not an archive codec; architecture, licensing, ABI, graphics, and per-game compatibility must be validated.
7. **Pipeline scheduler** — connect filters and codecs in the correct order; stream bounded buffers; coordinate worker threads and back-pressure.
8. **Verification and safety layer** — checksums, output validation, limits, safe paths, error recovery, installer logs and cleanup.
9. **Test and benchmark lab** — legal fixtures, malformed inputs, fuzzing, memory/thermal/performance measurements on ARM64, and a per-game compatibility matrix.

## Game compatibility targets

Initial validation should focus on classic PC titles and installers that can plausibly run through a Wine/Box86/Box64-style environment. Need for Speed titles and Doom 3 are examples of user-valued targets, not current compatibility promises. Each title needs separate tests for installer completion, required dependencies, graphics/API path, controls, audio, save paths, and actual gameplay.

## Milestones

- [x] Create Android project scaffold and CI build workflow.
- [x] Document initial SREP / LOLZ feasibility and licensing blockers.
- [x] Add bounded streaming transfer primitive and unit tests (CI pending for latest commit).
- [ ] Establish the modular engine API and archive-inspection baseline.
- [ ] Add a legal, tested baseline codec and streaming extraction path.
- [ ] Add split-volume/container support with fixture-based tests.
- [ ] Add installer detection and unpacking for selected formats.
- [ ] Prototype a legally compliant Windows compatibility runtime adapter.
- [ ] Validate selected PC games (including target examples) on ARM64 Android.
- [ ] Resolve SREP through a clearly licensed implementation or permission.
- [ ] Resolve LOLZ through a redistributable implementation or documented compatibility boundary.
- [ ] Publish device benchmarks and release APKs only after validation.

## Build

Requires JDK 17, Android SDK 35, and Gradle:
```bash
gradle assembleDebug
```

No game data, FitGirl installer binaries, or proprietary CLS executables are included. Original archive parts remain untouched; extracted/installed output will go to a user-selected destination.

## Founding and contribution

ArcNova is being initiated collaboratively by its human project owner and ChatGPT as a development/research collaborator. Technical decisions, code provenance, tests, and releases will be recorded transparently in the repository.
