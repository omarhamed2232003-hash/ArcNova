# ArcNova — AI Handoff & Development Instructions

> Read this file first whenever an AI assistant opens this repository in a new chat or session. This is the durable project handoff. Do not rely on chat memory.

## 1. Start here: mandatory first actions

1. Read this file and `docs/SESSION_HANDOFF.md` first. The session handoff is the concise, date-stamped snapshot of the active branch, PR, latest commits/CI, completed work, unresolved issues, and exact next task. Treat it as a pointer, not as a substitute for source verification.
2. Read `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md`, `DISCLAIMER.md`, and `THIRD_PARTY_NOTICES.md` when present; if a named file is absent, record that fact rather than inventing its contents.
3. Re-sync GitHub: fetch `main`, the active development branch, open PRs, repository tree, latest Actions runs and job steps. Confirm the actual branch/commit before relying on any handoff snapshot. Never infer current state from chat memory or a stale commit hash.
4. Inspect the actual source, tests, workflow, and relevant docs before changing anything. Search for existing abstractions before adding duplicates.
5. Separate implemented, detected, planned, CI-tested, device-tested, and end-to-end validated capabilities. Update the handoff and affected status docs whenever meaningful state changes.
6. Continue from the real repository state. Do not restart, rewrite, or replace the project just because this is a new chat.

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
- Communicate with the owner in the language they use in the conversation; this communication preference does not change the project's English-only requirement. Teach through the real project work without turning every task into a lesson.
- Preserve the existing project, workflow, milestones, and backups. Do not impose a new workflow, extra audit/reporting phase, or reorder milestones without a concrete reason.
- Do not ask for approval for ordinary, reversible engineering decisions. Ask only when a genuinely owner-controlled decision is required (for example, choosing a project-wide open-source license, spending money, accepting a proprietary license, or destructive/unrecoverable action).
- Do not claim to work in the background after the chat/session ends. Make progress during the active session, report exactly what was changed, and leave a useful handoff for the next session.
- Do not make the owner download large files over mobile data without asking first.



## English-only project language policy

All project-owned content and development artifacts must be written in English only. This applies to source code, identifiers, comments, tests, test names and descriptions, documentation, README and handoff files, AI-agent instructions, application UI text, user-facing messages, logs, commit messages, branch names, pull request titles and descriptions, issue content, and release materials. Do not add Arabic or other non-English text to project files or GitHub project artifacts. Preserve legally required third-party notices and upstream text where alteration would be inappropriate; do not translate or remove mandatory legal notices. The language used in a private conversation with the owner is separate from the language of the repository and product: agents may communicate with the owner in the language the owner chooses, while all ArcNova project artifacts remain English-only.

## 4. Current implementation truth

Treat the repository as the source of truth; this section is a handoff snapshot and may become stale. Re-check files and CI before relying on it.

Known implementation baseline:
- Android app scaffold; Kotlin, Android Gradle Plugin/Gradle project, Java 17, Kotlin 17, compile/target SDK 35, min SDK 26.
- MainActivity currently offers document-picker based ZIP extraction flow: choose archive, choose SAF destination, extract, cancel, and progress reporting.
- ZIP extraction engine has safe path validation, overwrite refusal, entry/file/total-output/expansion-ratio limits, cancellation, streaming writes, and transactional rollback of every file and directory created by the extraction when an entry, limit, cancellation, or progress callback fails. Pre-existing destination content is preserved.
- SAF output stages extracted data in app cache before copying to the selected document tree. The copy is now routed through `TransactionalTreeCopy`, which tracks every SAF document it creates, checks destination-name collisions before creation, streams files with cancellation checks, guards against staged-directory cycles, and deletes created documents in reverse order if a later step fails. Existing destination items are not overwritten or deleted. If a provider refuses rollback deletion, the operation reports an explicit incomplete-rollback error with cleanup failures attached. Staging still temporarily requires extra storage.
- Existing unit tests cover normal ZIP extraction, traversal rejection, limits, cancellation before and after completed entries, overwrite refusal, backslash traversal, caller-owned input-stream lifecycle, full ZIP rollback after later-entry/callback failures, and preservation of pre-existing destination content. `TransactionalTreeCopyTest` covers nested SAF copy, later provider-create failure rollback, existing-name collision preservation, cancellation, output-open failure, progress-callback failure, and explicit reporting when a provider refuses rollback deletion.
- File/archive detection code includes signature/extension hints for common formats, specialist compression extensions (WinRK, KGB, UHARC, ZPAQ, DGCA, StuffIt), game packages (Unreal PAK/IoStore, Unity AssetBundles, Source VPK, XP3, RPA), and multipart naming patterns. These are filename hints unless a signature detector explicitly validates bytes.
- `FileSignatureDetector` performs bounded magic-byte identification for common image, document, executable-candidate, archive, audio, video, and database headers. Its InputStream overload reads a limited prefix and leaves the caller-owned stream open; it does not validate the full file.
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
8. **Handoff:** update `docs/SESSION_HANDOFF.md` after every meaningful work batch with verified branch/PR/commit/CI state, changed paths, tests, remaining risks, and one concrete next task. Update this file when durable architecture, owner decisions, safety rules, or workflow rules change. Keep both current.

When using GitHub API tools, write operations must be sequential and use exact current SHAs. Never claim a commit, build, test, or release succeeded unless the tool result confirms it.

## 9. Suggested next engineering priorities

Re-evaluate against current source/CI before choosing; do not treat this list as a rigid new milestone order.
1. Expand the universal file-analysis foundation: richer byte-signature detection, unknown-file reports, safe metadata inspection, checksums, and file comparison. The initial operation-level format registry now exists; continue enriching it with signatures, decoder provenance, license state, version coverage, and validation evidence as those facts are established.
2. Validate the new Android SAF destination transaction on real providers/devices, especially cancellation and provider delete failures. The unit-tested `TransactionalTreeCopy` tracks created document IDs and rolls them back in reverse order; then consider direct SAF streaming to reduce cache duplication.
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


## 11. Product quality bar and UI/desktop experience — owner-confirmed direction

This section records explicit product-owner decisions from 2026-11-09 and is as important as the product mission above. Preserve both this section and the broad product mission; do not reduce ArcNova to a basic archive utility.

### Quality bar
- ArcNova must be a genuinely premium, professional application. The owner explicitly referenced the visual quality and polish achieved in the ATLAS website as the minimum quality ambition. Do not ship a generic stack of default grey Android buttons as the intended product UI.
- The selected visual direction is **modern Windows-inspired desktop software**, not retro/classic Windows. It should feel powerful, refined, contemporary, cohesive, and intentional.
- Target a rich design system: coherent brand palette, dark and light themes, typography hierarchy, iconography, responsive layouts, polished cards/panels, purposeful gradients/material treatments, smooth transitions, meaningful motion, and carefully designed empty/loading/progress/error states.
- Motion must be functional and refined: animated navigation/tab transitions, drawers or side panels that slide in/out, expandable panels, dialogs, progress feedback, and subtle interaction states. Avoid decorative animation that harms performance or accessibility.
- The owner wants a desktop-like interaction model where useful: pointer/mouse support, keyboard navigation and shortcuts, context menus, drag-and-drop, resizable or multi-pane work areas where appropriate, and window/panel behavior. Adapt responsively to Android phones and tablets; do not make the mobile layout a cramped desktop imitation.
- The UI must represent the real capability level accurately. Planned engines must not appear as working features; show clear availability/coming-soon states until implemented and validated.

### Windows-style workspace and installer experience
- Because ArcNova will inspect and process Windows files and may eventually run supported Windows installers/software, plan a modern Windows-inspired workspace rather than an old-fashioned Android utility screen.
- When installer/runtime capabilities are implemented, design a guided setup experience: installer identity and detected technology, compatibility/readiness checks, destination/options, component selection where applicable, progress, detailed logs, errors/recovery, completion, and launch action only when supported.
- A desktop-like mode/shell is a product direction, not an immediate prerequisite to every file-engine task. Establish architectural seams now (navigation, tool panels, task/progress center, file/workspace model, input abstraction) so it can be introduced without rewriting the application later.
- Do not build a fake Windows desktop or simulate successful installation. Desktop-like UI must be backed by real operations and honest capability states.

### Reuse of external code and projects
- The owner explicitly authorizes studying and reusing third-party code where legally permitted, including projects such as Bannerlator, to reach the strongest practical result; do not assume everything must be reinvented.
- Prefer mature, tested, maintained components when their license, Android ARM64 compatibility, provenance, security, and redistribution terms are acceptable. Adapt and integrate them into ArcNova's own modular orchestration and user experience.
- Preserve copyright notices, licenses, source-offer obligations, and attribution. Do not copy code merely because it is publicly visible. No proprietary/closed-source binaries or restrictive codec plugins (including SREP/LOLZ/CLS/Oodle components) may be bundled without the required explicit permission/license.
- Do not wrap another app and call it ArcNova. ArcNova must own its file workflows, capability registry, operation orchestration, task state, diagnostics, profiles, and coherent UI even when it delegates a lawful engine.

### Priority discipline: neither defer essentials nor build premature polish
At every step, choose work by dependency, risk reduction, user value, and readiness:
1. Do now when it unblocks architecture or prevents expensive rework: define UI/design-system and navigation foundations; keep engine/UI boundaries clean; model task state, progress, cancellation, diagnostics, and capability status; verify Android ARM64/device constraints; check licenses before dependency choices.
2. Do now when it delivers the next dependable foundation: complete generic file analysis (metadata, signature-vs-extension mismatch, streaming SHA-256, safe reporting) and streaming file comparison; expand tests and validate on device where practical.
3. Do alongside engine development when a capability becomes real: add its UI flow, progress, error handling, and integration tests. Do not wait until every format is complete to expose all useful functionality.
4. Defer expensive desktop-shell visuals, full mouse/keyboard workspace, elaborate installer wizard, and runtime-specific UI until the corresponding underlying operations and architecture are sufficiently real. However, preserve their design and architectural requirements now.
5. Do not spend time on premature visual flourishes, fake buttons, unsupported format claims, a simulated Windows environment, or large UI rewrites that do not advance a real user workflow.
6. Do not postpone security, licensing, storage limits, cancellation, rollback, diagnostics, or device-performance work when it is a prerequisite for a safe/reliable engine.

The owner explicitly said the premium interface and Windows-inspired desktop direction need not be the immediate task if a more important foundation comes first, but essential work must not be forgotten or unnecessarily postponed. Make the next-step choice yourself based on the actual repository state; do not ask the owner to re-state this direction.


## 12. Container profiles, switchable interfaces, and guest environments — owner-confirmed direction

The owner explicitly requested (2026-11-09) that ArcNova's container-building experience support switching between interfaces/environments, and provide as broad a set as practical of Windows versions and other operating systems that can help inspect, browse, open, extract, or work with files.

### Product behavior
- A container/workspace must not be permanently locked to one visual shell. Where the backend permits, the user can switch the active presentation/interface for the same underlying workspace and files.
- Distinguish three independent concepts in both architecture and UI: (1) visual shell/interface profile; (2) actual guest OS/compatibility profile; (3) tool/engine profile, including parsers, archive utilities, codecs, runtimes, and file handlers.
- Switching a visual shell must not silently claim to change the actual guest OS, Windows version, filesystem, or installed runtime. Explain what is being switched and what is preserved.
- A container/workspace should retain access to selected files and mounted storage when changing a compatible shell. Preserve task state and avoid losing active operations; block or defer a switch safely when an operation cannot survive it.
- Support touch-first navigation and desktop interaction: mouse/pointer, keyboard, context menus, drag/drop, multi-pane browsing, and resizable panels where supported.
- Provide a clear container creation flow: purpose/template, genuinely available guest/runtime profile, interface profile, storage mounts/permissions, resource limits, and optional tools.

### OS and version breadth
- Investigate practically relevant Windows compatibility targets, including legacy versions when an application requires them and modern Windows targets. Do not assume every Windows release can run as a full guest OS on Android.
- Investigate Linux userspaces/distributions and desktop shells as first-class file-processing and compatibility environments. Lightweight/headless environments may be better for extraction and CLI tools; full desktops are for GUI software and browsing.
- Consider other operating systems only when they provide a concrete supported file-access, inspection, extraction, or runtime benefit. Do not promise arbitrary macOS, BSD, ChromeOS, or other OS virtualization on Android ARM64 without a viable lawful backend and device validation.
- The goal is broad access to files and formats, not collecting OS logos or cosmetic version selectors. Where an Android-native engine can parse a format directly, prefer it over booting a guest OS solely for that operation.

### Technical architecture and capability truth
- Model ContainerProfile, GuestEnvironment, ShellProfile, ToolchainProfile, mounted storage, resource limits, compatibility constraints, and capability declarations independently.
- A profile can be marked available only after its backend, architecture support, licensing, install lifecycle, storage integration, and device performance have been verified.
- Use explicit states such as planned, installable, installed, running, and device-validated. A UI skin is not an OS installation; Wine is not Windows; a Linux userspace is not a full Linux VM; a compatibility profile is not proof that arbitrary Windows software works.
- Prefer reusable backends and isolated per-container configurations. Do not bundle proprietary OS images or redistribute copyrighted OS files. Users must supply licensed OS images/installers where required.
- Research established Android-compatible components and architectures (Wine-based environments, Box64/FEX where appropriate, Linux userspace/container approaches, and Android-native parsers) with license and ARM64 audits before integration. Desktop Proton or x86 Linux binaries are not assumed to be drop-in Android ARM64 solutions.
- For file viewing, support safe preview/inspection through native handlers where possible. Treat untrusted documents, executables, archives, scripts, and media as hostile: sandbox execution, restrict permissions/network, enforce resource limits, and never auto-run selected files.
- Preserve the modern Windows-inspired professional design, switchable shells, responsive tablet/phone layouts, and honest capability status.

### Priority guidance
- Record the container/profile model in architecture before adding cosmetic shell selectors.
- Do not interrupt current universal file-analysis and common-format foundations for a premature full OS virtualization project.
- Introduce container abstractions when they help real file operations or runtime compatibility. Build a minimal real profile manager first; expand guest OS choices only as viable backends are proven.
- Keep the owner's broad Windows-version and multi-OS ambition on the roadmap; do not silently narrow it to one Windows skin or one Wine prefix.


## 13. Competitive research and best-of-breed integration strategy — owner-confirmed direction (2026-11-09)

The owner wants ArcNova to learn from the strongest relevant projects without copying any one product's identity, UI, or code wholesale. The ambition is to build a unified, unusually capable Android-first file/software/game toolkit. Treat the following as a standing product strategy, not a one-time comparison.

### Reference projects and what to learn

- **Winlator / Wine-based Android projects:** study the Android lifecycle, Wine prefix/container setup, per-game launch profiles, Box86/Box64 integration, graphics/audio/input translation, environment tuning, and practical compatibility troubleshooting. These projects demonstrate that Windows software can run on Android for selected workloads, not that every Windows program or game is compatible.
- **Bannerlator and related multi-backend launchers:** study isolated containers, selectable Wine/runtime versions, Box64 and FEX alternatives, graphics wrapper/driver catalogs, per-title settings, component version management, import/export, accurate backend pairing, and diagnostics. Treat runtime components as versioned, independently replaceable adapters rather than hard-coded app internals.
- **PeaZip and mature archive tools:** study broad format coverage, multipart volumes, archive conversion, split/join, checksums/hashes, batch operations, archive browsing/search, file-manager integration, and mature CLI/engine reuse. Evaluate the actual decoder underneath each claimed format; a format list alone is not evidence of reliable support.
- **Strata and advanced Android file managers:** study dual-pane workflows, SAF/storage integration, batch actions, drag/drop, bookmarks, search, previews/editors, network storage, privacy controls, and touch-first power-user UX.
- **7-Zip, libarchive, and other maintained format libraries:** investigate as candidate engine sources for native parsing/extraction, subject to Android ARM64 build feasibility, exact format/version coverage, security history, dependency provenance, and license obligations.
- **Box64, FEX, Wine, DXVK, VKD3D-Proton, Mesa/Turnip, and related projects:** study each at its actual layer. CPU translation, Windows API compatibility, Direct3D translation, Vulkan drivers, and Android integration solve different problems; they are not interchangeable and must be tested as a matched stack.

Reference projects are research inputs, not automatic dependencies. For every proposed reuse, record upstream URL, exact revision, license, Android ABI/build status, supported operations and format versions, known limitations, security posture, redistribution/source obligations, and validation evidence. Prefer upstream libraries/engines over copying another app's whole codebase. Do not bundle another app, scrape proprietary components, or misrepresent another project's work as ours.

### ArcNova's differentiator: one orchestration layer

ArcNova should own the cohesive product experience and orchestration:
1. Ingest a user-selected file, folder, installer, game package, or application.
2. Identify likely type using signatures, metadata, extension hints, and confidence; report ambiguity instead of guessing.
3. Build a capability plan from the registry: inspect, validate, hash, compare, extract, convert, install, or launch only where a real handler exists.
4. Select the best eligible engine/backend for the device, architecture, format version, and task; explain why it was selected and offer alternatives only when meaningful.
5. Execute as a cancellable, observable task with progress, logs, bounded resources, safe staging/rollback, and diagnostics.
6. Preserve reusable per-file, per-game, per-container, and per-toolchain profiles without conflating UI shell, guest environment, runtime, or decoder.
7. Present truthful results with separate states for detected, inspected, validated, extracted, installed, launched, and playable.

The intended experience is not a pile of unrelated tools. It is a unified workspace that routes each job to the right native engine, specialist codec pipeline, installer handler, or compatibility runtime while keeping files, tasks, profiles, and diagnostics connected.

### Product areas to benchmark continuously

- **Universal file intelligence:** file signatures, metadata, hashes, integrity verification, comparison, duplicate discovery, hex/text/media previews, safe batch operations, split/join, and conversion.
- **Archive and repack engineering:** common formats, multipart/solid archives, encrypted archives, specialist codecs/filters, archive testing, recovery/error reporting, and high-performance streaming.
- **Windows software and PC games:** installer technology detection, safe extraction, managed Wine/compatibility environments, x86/x64-to-ARM translation, graphics/audio/input backends, per-game profiles, controller/mouse/keyboard handling, and compatibility diagnostics.
- **Game assets and packages:** inspect/extract supported engine containers and provide format-aware browsing without bundling copyrighted game content.
- **Container/workspace system:** multiple isolated environments, independently switchable visual shells, guest/runtime profiles, toolchains, mounts, resource limits, import/export, and safe task continuity.
- **Professional UX:** modern Windows-inspired visual language, responsive phone/tablet layouts, touch-first navigation, optional desktop-like pointer/keyboard/multi-pane workflows, meaningful animation, accessible progress/error states, and no fake controls.
- **Reliability and trust:** hostile-input sandboxing, resource limits, cancellation, cleanup/rollback, privacy, transparent permissions, license provenance, and reproducible validation.

### Integration and prioritization rules

- Do not attempt to implement every feature simultaneously. Select the next capability by user value, technical dependencies, Android ARM64 feasibility, security, and legal readiness.
- Prefer direct native Android parsing for files when it is safer and more efficient than launching a guest OS. Use compatibility environments when the format/tool or Windows application genuinely requires them.
- Reuse a mature component when it is legally distributable and technically suitable; write a clean adapter when that preserves ArcNova's architecture. Reimplement only when existing engines are unsuitable, unavailable, or incompatible with our requirements.
- Do not copy UI layouts, branding, assets, or code wholesale. Learn the interaction principles and build a coherent ArcNova identity.
- Benchmark real workflows and target-device performance, not marketing feature counts. Measure throughput, memory, storage overhead, battery/thermal behavior, cancellation latency, and correctness on representative files.
- Keep the separate future game-specific GitHub project independent. It is not part of ArcNova's current scope; later integration may use documented interfaces only if the owner chooses.
- Maintain an explicit distinction between researched, planned, implemented, CI-tested, device-tested, and end-to-end validated capabilities.


## 14. Professional visual identity and app artwork — owner-confirmed requirement (2026-11-09)

The owner explicitly asked that a professional image/design for the application not be forgotten. Treat brand identity and visual assets as a required product deliverable, not optional decoration.

- After the final product name is selected, create an original, professional logo/wordmark and a distinctive Android launcher icon. Do not lock final artwork to the temporary ArcNova name if renaming is still under consideration.
- Create a polished app promotional/hero artwork suitable for a store listing or project presentation, with a visual language consistent with the in-app premium UI.
- Define a coherent palette, typography, icon style, light/dark variants, and asset sizing/export rules. Ensure the icon remains legible at small launcher sizes and works on varied Android icon masks.
- Keep all assets original; do not imitate another product's logo, brand, or proprietary artwork.
- Use the same identity consistently across the Android app, GitHub README/screenshots, and future promotional materials.
- Final image generation and asset integration should happen after the product name/brand direction is settled, while the requirement remains on the active roadmap.

## 15. Mandatory eight-phase delivery plan — owner-confirmed and must not be forgotten

The owner explicitly confirmed on 2026-11-09 that the complete eight-phase plan in `docs/ROADMAP.md` is binding project direction. Every new agent/session must preserve it, consult it before selecting work, and update the handoff with the current phase and exit-gate status. Do not silently drop, merge, reorder, or skip phases.

1. **Phase 1 — Core file/archive foundations:** file intelligence, safe ZIP inspection/extraction, transactional SAF operations, bounded resources, cancellation, rollback, and adversarial regression tests.
2. **Phase 2 — General archive engines:** real TAR/gzip, 7z, RAR and other common formats using verified libraries/decoders, licenses, fixtures, and integrity tests.
3. **Phase 3 — Specialist compression and game containers:** FreeArc/multipart and specialist codecs where rights permit, plus Unreal, Unity, Source, XP3/RPA and other game package formats.
4. **Phase 4 — Installer intelligence:** installer detection, manifest/payload inspection, safe extraction, and isolated supported installer workflows.
5. **Phase 5 — Compatibility runtime:** managed Windows compatibility environment, ARM64 translation, graphics/audio/input adapters, profiles, and per-title validation.
6. **Phase 6 — Full professional UI and brand system (MANDATORY):** replace the current engineering harness; deliver the complete premium modern Windows-inspired ArcNova experience, responsive tablet/phone workspace, file manager, archive browser, job/progress center, navigation/panels, polished dialogs/empty/error/loading states, accessibility, refined motion, pointer/keyboard/context-menu/drag-drop support where useful, coherent light/dark design system, final logo/wordmark, launcher icon, and consistent original artwork. This phase is not optional, must not be forgotten or reduced to a cosmetic refresh, and must be completed before device qualification/release phases.
7. **Phase 7 — Physical-device qualification:** validate real workflows and performance on target Android hardware/providers, including OnePlus Pad 2, large files, SAF, cancellation, storage, memory, thermal/battery, and runtime compatibility.
8. **Phase 8 — Release readiness:** security/privacy/license review, signing, versioning, diagnostics, release notes, reproducible checks, and beta/release preparation.

### Phase gates and preview APK rule
- Advance only when the current phase's documented exit criteria are met; record incomplete items honestly. Research, detection hints, scaffolding, and CI success do not count as implemented engine support.
- The owner authorized exactly one early personal preview APK to inspect the current state. That exception has been used. **Do not build or request another APK until all eight phases are complete and the owner explicitly authorizes final release/device packaging.** Continue ordinary unit tests/CI without APK assembly.
- The personal preview was an engineering snapshot, not evidence that the latest experimental ZIP backend was wired into the UI or that the app's extraction behavior had already changed. Always distinguish previewed app behavior from engine code that is only experimental/tested.
- Keep the temporary preview name/icon clearly provisional; final identity/artwork belongs to mandatory Phase 6 after the product identity is settled.
- When explaining progress, state both the current user-visible app behavior and the deeper engine capabilities separately. Never imply the preview APK contains a capability merely because a corresponding experimental class or unit test exists.


## 16. Owner-mandated technology research and modern-first selection — 2026-11-10

This is a durable product-owner requirement and applies to **every future engineering decision and every phase**, not only Android file picking.

### Mandatory research before choosing an approach
- Before implementing a new subsystem, API integration, file format, codec, archive engine, installer handler, runtime component, UI framework, storage flow, networking feature, or build/test tool, research the current best options on the public internet. Do not limit research to GitHub: investigate official platform/vendor documentation, upstream project sites and source repositories, release notes/changelogs, issue trackers, security advisories/CVEs, technical papers/specifications, package registries, and credible engineering reports as relevant.
- Search broadly enough to discover mature projects and reusable code outside GitHub as well as on GitHub. The owner's explicit instruction is to use the wider internet and existing projects rather than rebuilding mature components from scratch.
- Prefer the newest **stable and maintained** solution that is technically appropriate for ArcNova. "Newest version" alone is not a quality criterion: distinguish stable releases from previews/nightlies, check release dates and maintenance activity, supported Android/API/NDK/ABI targets, ARM64 viability, performance/memory/storage behavior, security history, interoperability, and test maturity.
- Check official Android/Kotlin/Gradle/NDK documentation for platform APIs and current recommended patterns. Do not introduce deprecated APIs or legacy callbacks into new code when a modern supported API is suitable. Existing legacy code may remain temporarily for compatibility, but document the reason and create a migration path when justified.
- For third-party code, inspect the actual upstream source and exact release/tag/commit, license text and obligations, transitive dependencies, binary provenance, known vulnerabilities, native build requirements, maintenance health, and redistribution constraints. Never copy code blindly or infer a license from a repository badge alone.
- Compare at least two credible candidates when meaningful alternatives exist. If only one viable candidate exists, explain why. If no suitable maintained/open implementation exists, document the gap and only then consider implementing the required component ourselves.
- Prefer reusable, battle-tested upstream engines/libraries and adapters over bespoke implementations, while keeping ArcNova's architecture modular and retaining tests around integration boundaries.

### Required decision record
For each material technology choice, record in an appropriate existing research/architecture document (do not create a new process phase):
1. The problem and ArcNova requirements.
2. Research date and links to authoritative docs, upstream repositories/releases, specifications, security advisories, and relevant issue discussions.
3. Candidates considered and comparison of stability, maintenance, Android/ARM64 support, performance/resource needs, security, API fit, license/provenance, and integration cost.
4. The selected option and why it is the best fit; alternatives rejected and reasons.
5. Exact dependency/API versions and migration/compatibility strategy.
6. Tests and evidence still required, clearly separating source review, CI validation, emulator validation, and physical-device/end-to-end validation.

Keep the record proportional: a small API choice needs a concise note; a core engine/runtime choice needs a thorough evaluation. This is part of normal engineering and existing documentation, **not an extra audit phase or reason to stall implementation**.

### Research integrity
- Cite sources and dates in project docs. Prefer official documentation and upstream release notes for factual claims; use issue trackers/community reports as supplementary evidence and verify important claims independently.
- Never claim to have surveyed "the entire internet" or every project. Search widely, disclose the scope and remaining uncertainty, and revisit decisions when upstream status changes or a better candidate emerges.
- Re-check current versions and maintenance when a dependency is upgraded or a phase begins; do not rely on old research snapshots.
- Do not choose an obsolete API merely because it is familiar or easier to wire into the current scaffold. Any exception requires a concrete compatibility or technical reason documented with the decision.

## 17. ArcNova identity, independent implementation, and source reuse — owner-confirmed rule (2026-11-10)

- ArcNova must be presented and developed as an independent product with its own brand, interface, workflows, architecture, integration layer, and user experience. Do not make it look like a re-skinned copy or a bundle of unrelated third-party applications.
- The owner does **not** want ArcNova to take or adapt source code from a project when doing so would require ArcNova to use, display, or be identified by that project's name/brand as a condition of the intended integration. In that case, do not take that project's code; study public technical concepts and implement an independent alternative, or choose a different suitable component.
- Reusable source code may be considered only when its actual license and terms permit the intended use, modification, integration, and distribution without requiring third-party product branding or naming in ArcNova's user-facing identity. Verify the exact license for the exact version/revision and all bundled/transitive components before adoption; a public repository or the phrase "open source" alone is not permission to ignore terms.
- Do not remove, falsify, or conceal legally required copyright, license, patent, attribution, or source-offer notices. Such notices may be kept in the appropriate legal/about documentation rather than promoted as product branding, where the license permits that presentation. If a license requires a visible credit, product-name reference, reciprocal source release, or other obligation incompatible with the owner's identity requirement, do not use that code; find another implementation or write an independent one.
- Never claim ArcNova owns upstream code merely because it was modified. Distinguish ArcNova-authored code, ArcNova's original adapters/orchestration/UX, and third-party components governed by their own licenses. Preserve provenance and required notices in `THIRD_PARTY_NOTICES.md`.
- Do not copy another app's UI, logo, assets, product name, or whole codebase. Prefer independently licensed libraries/engines with clean ArcNova adapters; if no candidate meets both the technical and identity requirements, implement the needed capability independently.
- This rule does not prohibit learning from public documentation, specifications, algorithms, or observable technical behavior. Reimplementation must still respect applicable patents, copyrights, trade secrets, and other legal restrictions.
