# ArcNova — AI Handoff & Development Instructions

> Read this file first whenever an AI assistant opens this repository in a new chat or session. This is the durable project handoff. Do not rely on chat memory.

## 1. Start here: mandatory first actions

1. Read this file, then read `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md`, `DISCLAIMER.md`, and `THIRD_PARTY_NOTICES.md`.
2. Inspect the current branch, latest commit, working tree (if using a local checkout), repository tree, and latest GitHub Actions results. Never assume the commit hash or project state recorded in an older conversation is still current.
3. Inspect the actual source and tests before changing anything. Search for existing abstractions before adding duplicates.
4. Identify what is implemented, what is only detected/planned, and what has been validated on a real Android device. Update this handoff when meaningful project state changes.
5. Continue from the real repository state. Do not restart, rewrite, or replace the project just because this is a new chat.

## 2. Product mission — do not narrow the vision

ArcNova is an **Android-first universal file and software toolkit**, designed for Android ARM64. It is not merely an archive manager, not a game, and not a wrapper around one existing app.

The long-term product combines:
- General file intelligence and operations: identify unknown files, inspect signatures/metadata, validate integrity, compare, split/join, transform, convert, and safely process files in batches.
- Common and specialist archive/container support, multipart and solid archive handling, compression codecs, and filter pipelines.
- Installer intelligence: identify installer technologies, inspect contents, extract supported payloads, and execute supported installers in a managed/isolated compatibility environment.
- Game package and asset inspection/extraction.
- A managed Windows software/game compatibility runtime for supported titles, with per-title profiles and measured performance.
- Specialist repack workflows, including FreeArc-style containers and SREP/LOLZ stages when lawful implementations or licenses are available.
- Steam integration through Valve's official client/distribution flow.

A defining ambition is **cross-environment Android ARM64 support**: formats and workflows traditionally tied to Windows, Linux, macOS, command-line tools, or specialist desktop utilities should be investigated for direct handling inside ArcNova on Android. The goal is not to make the user leave ArcNova and open the original desktop tool for every supported operation. This is a target, not a claim that every format is already decodable.

FitGirl repacks and Steam PC games are important compatibility targets, but they are only part of the wider toolkit. Never redefine ArcNova as a FitGirl-only or game-only application.

## 3. User/product-owner working preferences

- The owner primarily works from an Android tablet (OnePlus Pad 2, Snapdragon 8 Gen 3, Android 16, ARM64) and currently has no PC/laptop available. Design workflows and testing instructions with this constraint in mind.
- The owner wants the AI collaborator to do the substantial repository inspection, coding, tests, and GitHub work directly whenever connected tools permit. Avoid assigning routine coding or complicated command sequences to the owner.
- Explain important changes in clear, concise Egyptian Arabic when communicating with the owner. Teach through the real project work without turning every task into a lesson.
- Preserve the existing project, workflow, milestones, and backups. Do not impose a new workflow, extra audit/reporting phase, or reorder milestones without a concrete reason.
- Do not ask for approval for ordinary, reversible engineering decisions. Ask only when a genuinely owner-controlled decision is required (for example, choosing a project-wide open-source license, spending money, accepting a proprietary license, or destructive/unrecoverable action).
- Do not claim to work in the background after the chat/session ends. Make progress during the active session, report exactly what was changed, and leave a useful handoff for the next session.
- Do not make the owner download large files over mobile data without asking first.

## 4. Current implementation truth

Treat the repository as the source of truth; this section is a handoff snapshot and may become stale. Re-check files and CI before relying on it.

Known implementation baseline:
- Android app scaffold; Kotlin, Android Gradle Plugin/Gradle project, Java 17, Kotlin 17, compile/target SDK 35, min SDK 26.
- MainActivity currently offers document-picker based ZIP extraction flow: choose archive, choose SAF destination, extract, cancel, and progress reporting.
- ZIP extraction engine has safe path validation, overwrite refusal, entry/file/total-output/expansion-ratio limits, cancellation, streaming writes, and cleanup of the partially written current file on failure.
- SAF output currently stages extracted data in app cache before copying to the selected document tree. This may temporarily require extra storage; a direct SAF output-sink design is a future improvement, but preserve current behavior until a tested replacement is ready.
- Existing unit tests cover normal extraction, traversal rejection, limits, cancellation, overwrite refusal, backslash traversal, and caller-owned input-stream lifecycle.
- File/archive detection code includes signature/extension hints for common formats, specialist compression extensions (WinRK, KGB, UHARC, ZPAQ, DGCA, StuffIt), game packages (Unreal PAK/IoStore, Unity AssetBundles, Source VPK, XP3, RPA), and multipart naming patterns. These are filename hints unless a signature detector explicitly validates bytes.
- `FormatCapabilityRegistry` is now the typed operation-level inventory for general, specialist, and game formats. It records detection/inspection/extraction/creation/encoding/install/launch states. ZIP extraction is the only archive extraction operation currently marked implemented and CI-tested; roadmap entries must not be presented as working decoders.
- StreamingTransfer is a bounded stream-copy primitive, not an archive extractor.
- WindowsExecutableDetector and WindowsInstallerDetector are preliminary metadata/heuristic detectors, not execution engines.
- RuntimeCompatibility is a platform-neutral candidate planner/heuristic, not an installed Wine/Proton runtime and not proof that a game can launch.
- AndroidDeviceCapabilityProbe provides preliminary ABI/RAM/CPU/Vulkan/thermal hints; it is not full native Vulkan extension or driver validation.
- ArchiveEngine, CodecEngine, EngineRegistry and related interfaces are scaffolding. An empty registry means no real decoder is registered.
- Current app functionality is far smaller than the product vision. Do not market roadmap targets as working features.

### Compatibility truth tiers

Always report support at the correct level:
- **Detect** — recognize a format or likely technology.
- **Inspect** — parse/list metadata and entries.
- **Extract** — correctly decode and verify payloads.
- **Install** — successfully complete supported installer/installation logic.
- **Launch** — successfully start software in the managed runtime.
- **Playable** — pass defined graphics, audio, input, gameplay, save/load, and stability tests.

Detection is not extraction. Listing is not extraction. Extraction is not installation. Launching is not proof of playability. A CI build passing is not a physical-device test.

## 5. Explicit format and compatibility roadmap

These are named research/implementation targets, not currently supported claims. Preserve all of them when updating plans.

### General-purpose and specialist compression/archive targets
- ZIP, 7z, RAR, TAR, gzip, bzip2, XZ, Zstandard.
- FreeArc / `.arc`, including multipart archives and validated codec/filter chains.
- SREP and LOLZ codec/filter stages used in specialist repack pipelines.
- WinRK / RK.
- KGB / `.kgb`.
- UHARC / `.uha`.
- ZPAQ / `.zpaq`.
- DGCA / `.dgc`.
- StuffIt / `.sit` / `.sitx`.

### Game package and asset targets
- Unreal Engine PAK and IoStore.
- Unity AssetBundles and related asset containers.
- Source engine VPK.
- XP3 and RPA packages.
- Oodle Kraken / Leviathan only if required SDK, patent, and redistribution permissions permit integration.

### FitGirl / repack workflow
Investigate the real container/volume format, metadata, solid blocks, codec/filter chain, checksums, and installer technology independently. The owner's existing local Black Ops 2 FitGirl files are user-provided test material; do not ask them to redownload them. Their known folder was:
`/storage/emulated/0/Download/Call of Duty - Black Ops 2 [FitGirl Repack]`
with `setup.exe`, `fg-01.bin` through `fg-09.bin`, and optional MP/Zombies bins. Do not assume these files are ordinary standalone archives. Prior Android tests found ZArchiver and 7-Zip did not list the BIN volumes as ordinary archives; Bannerlator/Wine setup was extremely slow and its bundled Android FreeArc `libunarc` did not support SREP. The Inno Setup Extractor only unpacked helper `tmp` content. Re-check actual files and evidence before drawing new conclusions.

### Runtime targets
- Windows installer analysis and supported execution.
- Wine/Proton-like managed runtime, x86/x64 translation on ARM64, graphics/audio/input adapters, per-game profiles and diagnostics.
- Steam via Valve's official client/distribution flow. Never route Steam credentials through ArcNova servers.
- Titles such as NFS and Call of Duty are test/compatibility targets, not included payloads.

## 6. Legal, licensing, and security boundaries

- Never add game payloads, repacks, ROMs, proprietary installer payloads, Steam assets, or copyrighted game files to this repository.
- User-provided test files must remain outside the repository and be supplied by someone legally entitled to use them.
- Preserve third-party license notices, source obligations, provenance, and exact dependency versions. Audit transitive dependencies, submodules, and bundled binaries—not just the top-level project license.
- **SREP:** available research indicates restrictive/all-rights-reserved terms and an author request to contact them for a commercial license. Do not bundle or redistribute without explicit permission.
- **LOLZ / CLS plugins:** no redistributable open implementation was established in prior research; treat CLS DLLs/EXEs and proprietary plugins as non-redistributable unless proven otherwise.
- **Oodle:** commercial/licensed SDK; do not bundle without permission.
- Do not silently copy GPL or other copyleft code into the project. Evaluate obligations and obtain the owner's decision where the project's license choice or distribution model is affected.
- The repository currently has no project LICENSE (verify before changing). Do not choose MIT, Apache, GPL, or another project-wide license without the owner's explicit decision.
- Do not add credentials, tokens, signing keys, keystores, passwords, private user files, or local test payloads to Git.
- Keep archive extraction defensive: path traversal protection, bounded output, cancellation, safe overwrite behavior, cleanup, and adversarial test cases.

## 7. Architecture principles

- Keep Android UI, file access/storage, archive/container parsing, codecs/filters, installer handlers, runtime management, and game/package parsers modular.
- Use streaming and bounded buffers for large files; avoid loading whole archives or game payloads into memory.
- Respect Android Storage Access Framework and persist URI permissions only when needed.
- Long-running I/O must not block the UI thread. Provide cancellation and meaningful progress.
- Keep caller-owned streams open unless ownership is explicitly transferred.
- Validate paths and destinations; do not trust archive entry names or metadata.
- Registry entries must distinguish planned, detected, inspectable, extractable, encodable, and runtime-only capabilities.
- Prefer small, testable increments and preserve public interfaces unless a migration is necessary.
- Avoid adding large dependencies before checking current maintenance, Android ARM64 support, API, licenses, native build requirements, binary provenance, and testability.

## 8. Engineering workflow — every session

1. **Re-sync:** fetch latest `main` SHA and current tree; never write using stale SHAs from chat history.
2. **Inspect:** read relevant Kotlin, Gradle, manifest, tests, docs, and workflow files. Understand current behavior before editing.
3. **Plan narrowly:** select the next useful increment that advances the product without introducing unnecessary process or scope changes.
4. **Implement:** keep changes cohesive, preserve unrelated files and backups, and add tests for new behavior and regressions.
5. **Validate:** run unit tests/build via GitHub Actions or available environment. Inspect logs on failure and fix the cause. A passing CI build must be reported as CI-only unless the app was actually tested on the target tablet.
6. **Commit safely:** fetch fresh branch head and parent tree immediately before writing; create blobs/tree/commit, then update `main` with an expected-SHA guard. Never force-push or overwrite concurrent work. If the lease fails, re-fetch and reconcile.
7. **Verify:** inspect the resulting commit, changed paths, and Actions run. Report commit hash, changed files, tests, CI result, and anything not tested.
8. **Handoff:** update this file when architecture, status, next step, or important decisions change. Keep the next-step section current.

When using GitHub API tools, write operations must be sequential and use exact current SHAs. Never claim a commit, build, test, or release succeeded unless the tool result confirms it.

## 9. Suggested next engineering priorities

Re-evaluate against current source/CI before choosing; do not treat this list as a rigid new milestone order.
1. Expand the universal file-analysis foundation: richer byte-signature detection, unknown-file reports, safe metadata inspection, checksums, and file comparison. The initial operation-level format registry now exists; continue enriching it with signatures, decoder provenance, license state, version coverage, and validation evidence as those facts are established.
2. Improve archive destination handling and failure rollback, including considering direct SAF streaming to avoid cache duplication.
3. Audit and integrate a common-format decoder only after dependency/license and Android ARM64 review; test with legal fixtures and malformed/adversarial inputs.
4. Research specialist formats one by one and document feasibility, native decoder options, licensing, encryption/key needs, and a real Android test plan.
5. Build installer/runtime layers only after a real, legally distributable component plan and measurable device validation.

## 10. Communication and handoff format

When reporting to the owner:
- Use concise Egyptian Arabic.
- State what changed, why it matters, exact commit, CI result, and what remains.
- Clearly separate implemented, researched, planned, and physically tested.
- Do not inflate progress or claim unsupported compatibility.
- Do not ask the owner to repeat project goals already documented here.
- If the chat ends, leave this file and repository history as the durable handoff for the next assistant/session.

**Repository:** https://github.com/omarhamed2232003-hash/ArcNova  
**Primary branch:** `main`
