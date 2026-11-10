# ArcNova — Current Session Handoff

> **Purpose:** Fast, reliable recovery when a new AI chat/session opens. Read this after `AGENTS.md`, then re-check GitHub before acting. This is a snapshot, not an authority over live repository state.
> **Snapshot verified:** 2026-11-09 22:20 UTC.

## Repository and working lane

- Repository: https://github.com/omarhamed2232003-hash/ArcNova
- Primary branch: `main`
- Active development branch: `feature/phase1-zip-transactional-rollback`
- Open PR: [#1 — Make ZIP extraction and SAF destination copy transactional](https://github.com/omarhamed2232003-hash/ArcNova/pull/1)
- PR #1 is **open, Draft, not merged**. Do not merge it or modify `main` without explicit owner authorization.
- Verified `main` HEAD: `ff50413b044cc788f7d1604728a365a76119889f`.
- Verified development HEAD at this snapshot: `fc81f1e9bb1cb76dc83f394ce22d396aad565c0a`.
- Development HEAD is ahead of `main`; all work described below is on the development branch. `main` has not been changed by this work. This does not mean `main` has been independently proven stable.

## Current CI truth

- Workflow: `.github/workflows/android.yml`.
- Current policy: run unit tests only with `gradle testDebugUnitTest`; no automatic Debug APK build or artifact upload on each change.
- Seekable spool tests passed in [Run #113](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42662783427) and [Run #114](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42662822367). Documentation updates passed in [Run #115](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42662855952) and [Run #116](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42662875655).
- Commons Compress 1.28.0 is a temporary evaluation dependency. Its `ZipFile` Central Directory spike tests passed for normal listing/streaming, missing Central Directory rejection, orphan local record exclusion, duplicate central-directory name enumeration, CRC mismatch detection responsibility, and ZIP64 reading. It is not wired into production extraction.
- Added `SeekableArchiveSpool`: bounded disk spooling, cancellation, caller-owned input preservation, and explicit temp-file cleanup; its five focused unit tests passed.
- Added `CentralDirectoryZipBackend`: preflights Central Directory paths and declared limits, streams payloads with actual-size and CRC32 validation, and owns cleanup of both `ZipFile` and spool. The initial CI attempt exposed a Kotlin backslash escaping compile error; it was fixed in commit `33601c3...`, and [Run #122](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42663863045) passed.
- Added preflight rejection for file-vs-directory ancestor conflicts; Runs #123 and #124 passed. Run #125 and #126 also passed, including documentation/status updates.
- Added `ZipExtractionEngine.extractCentralDirectory(...)`: it maps existing `ExtractionPolicy` limits into the backend, creates output through the existing no-overwrite filesystem transaction, reports progress, and rolls back created files/directories on CRC, copy, cancellation, or callback failure. It closes the backend before reporting success so spool cleanup failure also aborts the operation.
- Added integration tests for successful extraction, Central Directory CRC failure rollback while preserving pre-existing files, cancellation rollback, and preflight path rejection. Added adversarial adapter tests for file/directory path conflicts, expansion-ratio limits, and cancellation while copying. Runs #127-#131 passed. The follow-up progress-callback rollback test in commit `dae378b7a8bf93a32a090596e359c744bbd67056` also passed on GitHub Actions Run [#42686986328](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42686986328); job `test` and step `Run unit tests` completed successfully.
- A further backend regression test was added in `c7b8216`: it verifies that a child entry appearing before its file-parent entry is rejected during Central Directory preflight (the opposite ordering was already covered). This newest test is written but not yet CI-tested; GitHub Actions run #42686986328 validates the preceding `dae378b` commit only.\n- The original `ZipInputStream` API remains the production default. The new Central Directory path and its current regression/adversarial unit tests are CI-tested, but the path remains experimental and is not promoted as the production default. Physical-device and real SAF provider validation remain required.
- CI success does not prove physical Android/SAF provider behavior or release readiness.
- Previous Run #87 on `bed52b8...` also passed. Do not confuse older run results with the latest run.

## Product direction — preserve

ArcNova is an Android-first, ARM64-oriented universal file/software/game toolkit, not merely a ZIP utility and not a wrapper around another app. Preserve the full mission and long-term roadmap in `AGENTS.md`, `docs/ROADMAP.md`, and the architecture/research docs.

Owner constraints and decisions:
- Owner primarily works from a OnePlus Pad 2 (Snapdragon 8 Gen 3, Android 16, ARM64), currently without a PC/laptop. Do substantial repo/GitHub work directly; avoid dumping complex manual steps on the owner.
- Explain meaningful work in concise Egyptian Arabic.
- Preserve existing files, backups, milestones, and workflow. No restart or unnecessary audit/reporting stage.
- Do not rush the final UI or APK. Finish dependable engines and automated tests first; APK creation is for deliberate integration/device-testing checkpoints, not every commit.
- Final UI must be premium, professional, modern Windows-inspired (not retro), responsive, touch-first with optional desktop-like interactions. Do not ship fake controls or claim unsupported features.
- Keep visual shell/interface profile, guest OS/compatibility profile, and toolchain/engine profile separate. Switching a shell must not pretend to change the guest OS.
- Research and reuse third-party components where legally and technically suitable; audit provenance, exact license, ARM64 feasibility, security, and redistribution obligations first.
- Keep specialist codecs/components with restrictive or unclear rights (SREP, LOLZ/CLS, Oodle) blocked until permission/licensing is verified.
- Never add game payloads, repacks, private files, secrets, or signing keys to Git.

## Current engineering state

### Implemented / CI-tested (verify source before relying)
- Android Kotlin app scaffold and document-picker ZIP workflow.
- ZIP extraction: bounded streaming, safe path validation, overwrite refusal, entry/file/total-output/expansion-ratio limits, cancellation, and rollback of every file/directory created by a failed operation; pre-existing content preserved.
- ZIP inspection and heuristic risk indicators (not antivirus).
- Generic bounded file signature detection (header recognition, not full-file validation).
- Streaming file analysis (size, signature/extension comparison, SHA-256).
- Streaming file comparison (size and SHA-256 without loading whole files).
- Typed operation-level format capability registry; ZIP extraction is the only archive extraction engine marked implemented and CI-tested.
- `TransactionalTreeCopy` for SAF destination commits: tracks created documents, checks collisions, streams with cancellation, detects staged-directory cycles, rolls back created documents in reverse order, and explicitly reports incomplete rollback when provider deletion fails.
- Tests include ZIP safety/rollback/limits/cancellation/stream ownership, SAF copy success/failure/collision/cancellation/callback failure/rollback failure, and file analysis/comparison coverage as recorded in `docs/ENGINE_STATUS.md`.

### Not yet proven / limitations
- SAF behavior across real Android document providers and on the OnePlus Pad 2 remains device-pending.
- SAF staging temporarily duplicates extracted data in app cache; direct SAF streaming may be considered later if safe and justified.
- CI success is not physical-device validation, production readiness, malware scanning, or proof that planned codecs/runtime work.
- 7z/RAR/TAR and specialist decoders are not implemented merely because they appear in detection hints or roadmap.
- Installer detectors/runtime planners are preliminary heuristics/planners, not a functioning installer execution system or Wine/Proton runtime.
- No final premium UI, full desktop shell, or final brand artwork has been delivered yet; these remain planned product work.

## Current phase and next action

**Current phase: Phase 1 — core file/archive foundations.** Do not jump to Phase 2 or final UI until Phase 1 exit criteria are met.

Latest completed work:
1. Added transactional rollback to ZIP extraction and SAF tree copy.
2. Added regression tests for rollback and failure paths.
3. Fixed an InputStream-failure test fixture so the payload is not compressed below the intended read-failure threshold.
4. Changed CI to unit tests only (no per-change Debug APK).
5. Confirmed Run #88 passed on `8fbae0f...`.

**Current concrete task:** Run #136 passed for code commit `49d6398`, validating the understated Central Directory size backend test and output-stream failure/ownership test. The next focused increment is an integration test that drives the actual streamed total-output limit through `extractCentralDirectory` and verifies rollback of both a partially written current file and earlier output, while preserving pre-existing content and cleaning the spool. After that, review close/spool-cleanup behavior and the full ZIP regression suite. Keep the Central Directory route experimental; do not switch the production default until all tests and Android device/provider checks justify it. Investigate Zip4j only if encrypted/split ZIP becomes an active requirement.

After that, re-evaluate Phase 1 exit criteria and the best next dependency-driven increment. Keep the broader universal file-analysis foundation in view; do not treat the ZIP task as the whole product.

## Required new-session procedure

1. Read `AGENTS.md`, then this file.
2. Fetch live `main` and development branch heads, PR #1, repository tree, latest Actions runs, job steps, and relevant logs.
3. Confirm whether the branch, PR, workflow, and CI state still match this snapshot. If not, update this handoff from verified live facts before coding.
4. Inspect the exact source/tests/docs for the next task; do not infer class locations or APIs from this file.
5. Choose one focused increment. Add regression tests. Run CI and inspect failure logs before stacking unrelated work.
6. Update this file and `docs/ENGINE_STATUS.md` / `docs/ROADMAP.md` when the verified implementation status or next milestone changes.
7. Report exact files, commit, test result, and what remains untested to the owner in Egyptian Arabic.

## Current guardrails

- Never merge PR #1 or write to `main` without explicit owner authorization.
- Never claim physical-device validation unless performed on the actual device.
- Never build APKs on every code change; ask/coordinate before any large mobile-data download.
- Never mark a roadmap target as implemented based on filename detection, interface scaffolding, or a successful compile alone.
- If repository state differs from this handoff, live GitHub/source wins; reconcile the document rather than forcing code to match stale notes.


## Owner-confirmed update — 2026-11-09 (mandatory phase plan and preview truth)

- Owner explicitly confirmed that the full eight-phase plan must remain in the agent instructions and must not be forgotten. `AGENTS.md` section 15 now records all eight phases, phase gates, mandatory Phase 6, and the one-preview-only APK rule. `docs/ROADMAP.md` now labels Phase 6 mandatory and expands its required deliverables.
- **Phase 6 is mandatory:** complete professional modern Windows-inspired ArcNova UI and brand system, not a minor restyle. Deliver responsive phone/tablet workspace, file manager, archive browser, task/progress center, navigation/panels, dark/light themes, refined motion, accessibility and polished empty/loading/error states, plus final original wordmark, launcher icon and consistent artwork. Phase 6 precedes Phase 7 and Phase 8.
- **APK policy:** the one owner-authorized personal preview has been built and delivered (workflow Runs #145/#146, both successful; artifact `ArcNova-Preview-0.1.0`). This exception is consumed. Do not build another APK until all eight phases are complete and the owner explicitly authorizes final packaging. Keep CI to unit tests.
- **Important preview limitation:** the delivered APK is essentially the existing engineering harness with a provisional name/icon. Its `MainActivity` still calls `ZipExtractionEngine.extract(...)`, the original `ZipInputStream` route. It does not call `extractCentralDirectory(...)`. The Commons Compress Central Directory implementation is an experimental engine path with tests, not yet wired into the app's live extraction workflow. Therefore the owner correctly observed that the visible app and extraction behavior looked similar to the previous app experiment. Do not claim the APK demonstrated the deeper backend changes. The newer SAF `TransactionalTreeCopy` and extraction safety improvements exist in code, but no new rich UI or end-to-end device validation has been demonstrated by this preview.
- Latest verified development branch HEAD: `4444326fdd00a6c449cb943130bc53e0e2a837dc`. PR #1 remains open, Draft, not merged; base/main remains `ff50413b044cc788f7d1604728a365a76119889f`. No work on main.
- Documentation commits triggered unit-test workflow Runs #147/#148 (on `4e69480`) and #149 (on `4444326`); at snapshot time #147/#148 were in progress and #149 queued. Verify live CI before further changes.
- Next engineering action: let these documentation-only CI runs finish, then continue Phase 1 from the actual source and CI state. Keep the experimental Central Directory path separate until it is intentionally integrated, reviewed, and tested. Do not create another APK.


## Phase 1 integration increment — 2026-11-09

- Wired the app's live ZIP extraction action in `MainActivity.startExtraction()` from the legacy `ZipExtractionEngine.extract(...)` streaming route to `ZipExtractionEngine.extractCentralDirectory(...)`.
- The live route now uses a bounded seekable archive spool in `cacheDir`, Central Directory preflight before staged output creation, per-entry actual size/CRC/expansion validation, cancellation, and existing filesystem rollback. The existing transactional SAF tree-copy remains the second stage and is unchanged.
- App-level archive spool cap is currently 2 GiB (`MAX_ARCHIVE_INPUT_BYTES`); this is a deliberate bound, not a claim of unlimited ZIP support. Revisit based on real cache/storage/device tests before raising it.
- The old `ZipExtractionEngine.extract(...)` implementation remains available and covered by tests as a legacy streaming path; do not delete it until regression and compatibility review is complete.
- This change is code-written only at commit `8ea99fe419970c69fed677de7a7f47d44353aa23`; it must pass GitHub Actions before being called CI-tested. No APK is authorized. No Android device test has been performed.
- Next: verify CI for the integration commit, inspect exact job/test result, then add targeted integration coverage for the new 2 GiB app-bound wiring/configuration if useful; review cancellation, large archive cache capacity, provider stream behavior, and progress/copy rollback semantics before declaring Phase 1 complete.


## Follow-up: extraction spool error contract — 2026-11-09

- Verified Runs #170 and #171 succeeded, including ZIP inspection spool input-limit mapping and caller InputStream ownership tests.
- Found an API consistency gap: during extraction, cancellation or max-input-size rejection can occur while creating the seekable spool, before the backend is opened. These internal spool exceptions were previously leaking through the public extraction API.
- Updated `extractCentralDirectory` to map spool cancellation to `ZipExtractionCancelledException` and spool size rejection to `ZipExtractionLimitException`, preserving copied-byte counts and rollback behavior.
- Added tests proving both errors use the public extraction exception types, remove temporary spool files, and remove a destination root created by the failed operation.
- Code commits: `9c186f1ee34049f661389b3cce97972a49ee944d` and `ac0c634310a764facf73502d89cda0295ed713ba`. These newest tests are pending CI; do not describe them as passed until a matching Actions run succeeds.


## Phase 1 — actual-output rollback and public error contract — 2026-11-09

- Confirmed Runs #176 and #179 passed; #176 includes the extraction spool-limit and cancellation rollback tests. Preview APK job was skipped.
- Reviewed the existing actual-stream total-output test: it deliberately understates the Central Directory uncompressed size, then confirms rollback removes the partial file and earlier extracted files/directories while preserving pre-existing destination content and cleaning the spool.
- Improved error typing: added `CentralDirectoryZipLimitException` for policy/actual-output limit violations; the extraction engine maps backend cancellation and limit exceptions to the public `ZipExtractionCancelledException` and `ZipExtractionLimitException` types. CRC and malformed archive exceptions remain distinct.
- Updated the actual-output rollback test to assert the public `ZipExtractionLimitException` contract, not the backend-internal exception.
- Commits: `d0e334f86572a4f8c329d3c1e3df4557dcd5391e`, `306fb1c42bdc28d2d52e955895c15c527c8e160f`, `6a698b9b4f02fcc2e7abcf98467903d9ee5599ef`. Latest tests pending CI. No APK built; no device validation.


## ZIP64 app-path regression — 2026-11-09

- Run #184 failed on an intermediate PR merge commit because the existing actual-output rollback test still asserted the backend-internal exception after the public exception contract was intentionally changed. The failure was isolated to that stale assertion; no compile failure.
- Updated the test to assert `ZipExtractionLimitException` and confirmed the subsequent PR merge run #189 passed. Run #189 checked out merge SHA `8c29cf53d900b92352d2fc6336ba215771a549a7`; the `test` job and `Run unit tests` step completed successfully; `preview-apk` was skipped.
- Added an end-to-end ZIP64 extraction regression using Apache Commons Compress ZIP64 output with `Zip64Mode.Always`. It verifies extraction result counts, exact bytes, payload contents, and spool cleanup through the app-facing central-directory engine.
- ZIP64 end-to-end test commit: `3ede5bbae7e5aa6c3554cb2985ccda0fd8c56262`. Awaiting its own CI run; not yet CI-tested or device-tested.
- No APK built. No change to `main`.


## ZIP preflight limit error consistency — 2026-11-09

- Run #184 failed one test because the actual-stream output limit was exposed as a backend exception rather than the public extraction limit exception at that point in the commit sequence. The subsequent Run #189 on the later PR merge ref passed all tests, including the actual-output rollback/public exception test. The preview APK job was skipped.
- Follow-up code review found several metadata preflight limits still threw the generic backend exception (per-file/total declared bytes, declared expansion ratio, declared output overflow, path length/depth). These now throw `CentralDirectoryZipLimitException`, consistently mapped by the extraction facade to `ZipExtractionLimitException`.
- Added `centralDirectoryDeclaredOutputLimitUsesPublicLimitExceptionBeforeWriting` to assert preflight rejects a declared total-output breach before creating the destination and removes the spool.
- Commits: `e0fc3f8c6e9dd661cde0f969da7b40a9e7e36391`, `550ba3c4bd08d795987da82f142fd0b5cff10845`. Latest change is code-written, CI pending; not device-tested. No APK built.


## SAF destination copy failure-path coverage — 2026-11-09

- Verified ZIP64 end-to-end CI Runs #192/#195 and ZIP preflight/error contract Runs #193/#202 passed. The preview APK job was skipped.
- Reviewed `TransactionalTreeCopy` and its fake document-provider tests. Existing coverage exercises create/open/partial-write/progress/cancellation failures and rollback refusal reporting.
- Added a missing provider lifecycle case: output stream close fails after a payload has been written. The test asserts the newly created SAF document is rolled back and pre-existing destination content remains untouched.
- Test commit: `fa11c95ebf3cae0dfb32afb3dcb12c8fb2571223`; awaiting matching CI. This remains a fake-provider unit test, not a real Android SAF/device validation.
- No APK built and no main-branch changes.


## SAF picker responsiveness and result validation — 2026-11-09

- User reported intermittent file selection/read issues and trouble choosing an extraction destination through Android's "Use this folder" flow on the preview APK.
- Code review found `MainActivity.onActivityResult` performed both provider metadata query (`OpenableColumns`) and a read of up to 64 KiB for signature detection synchronously on the UI thread. SAF providers can be slow or cloud-backed; blocking the UI during these operations is a concrete responsiveness and timeout risk.
- Moved metadata lookup, open/read probe, and signature detection to the existing single-thread worker. The UI now shows a checking state, enables extraction/inspection only after a successful read probe and format decision, and reports provider access errors explicitly.
- Added validation of the returned document-tree URI before accepting it as the extraction destination.
- This is a code-level mitigation, not proof of full SAF provider compatibility. Real-device validation on the user's OnePlus Pad 2 and testing with multiple providers (Downloads, internal storage, SD/cloud provider where available) remain required.
- Commits: `9e80c29607ebf70b6e264107b14385aaeb08e5bf`, `6004758e5b84ad82c818835eae09e151af34f7cb`, `48b0b51ef2ab7edb444533175afcd012b3e8d311`. Awaiting CI; no APK built.


## Owner-mandated modern-first research policy — 2026-11-10

- Owner explicitly requires broad, careful technology research before **every future engineering step and every phase**, using the wider internet and existing projects—not GitHub alone.
- The requirement is now recorded durably in `AGENTS.md` section 16. Before selecting an API, library, engine, codec, installer/runtime component, UI technology, storage approach, or build/test tool, research official documentation, upstream sources/releases, changelogs, issue trackers, security advisories, specifications/papers, package registries, and credible engineering reports as relevant.
- Search broadly for mature reusable projects beyond GitHub. Compare credible candidates when alternatives exist; inspect exact versions/tags, maintenance/stability, Android/API/NDK/ARM64 support, performance/resource requirements, security, transitive dependencies, source provenance, and exact license obligations. Prefer the newest stable, maintained, technically suitable option—not simply the highest version number.
- Material choices must be recorded in existing research/architecture docs with dated links, candidates, tradeoffs, decision rationale, versions, compatibility strategy, and validation still needed. Keep notes proportional; this is normal engineering, not a new phase or an excuse to stall.
- Explicitly avoid deprecated APIs in new code when modern supported alternatives exist. Any legacy exception needs a documented technical/compatibility reason and migration path.
- Research policy commit: `8b9a1b15b560b18f3d2415c29397a31e050215c8`. Documentation-only change; matching CI must be checked before claiming validation.
- The SAF picker modernization is a separate code change currently awaiting CI; see the preceding SAF picker section. Do not claim the new picker code has passed CI or been physically tested until matching evidence exists.


## Modern AndroidX SAF picker migration — 2026-11-10

### Research and decision
- Owner requirement: modern-first research before each material engineering choice, using public sources beyond GitHub.
- Compared platform legacy `startActivityForResult/onActivityResult` with AndroidX Activity Result APIs. Android's official guide explicitly recommends Activity Result APIs and explains unconditional lifecycle registration so results survive Activity recreation: https://developer.android.com/training/basics/intents/result
- Verified official contracts: `ActivityResultContracts.OpenDocument` (https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument) and `OpenDocumentTree` (https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocumentTree).
- Checked AndroidX Activity release notes: stable `androidx.activity:activity-ktx:1.13.0`; `1.14.0-alpha03` is prerelease as of 2026-11-10. Selected stable 1.13.0, not alpha: https://developer.android.com/jetpack/androidx/releases/activity
- AndroidX contracts remain backed by the Storage Access Framework actions; SAF is still the supported document/tree access mechanism. This migration modernizes lifecycle/result handling, it does not replace SAF.
- AndroidX Activity is Apache-2.0; recorded in `THIRD_PARTY_NOTICES.md`.

### Implemented
- `MainActivity` now extends `ComponentActivity`.
- Registered `OpenDocument` and `OpenDocumentTree` launchers as Activity properties, before Activity creation, and launches them from the picker actions.
- Removed `startActivityForResult`, request-code constants, and `onActivityResult`.
- Retained background-thread provider metadata/open/read probing and user-visible error reporting.
- Added `androidx.activity:activity-ktx:1.13.0` to app dependencies.

### Validation status
- Code has been committed to the feature branch; compile/CI and physical-device checks are pending.
- Persistable URI grant is attempted from the returned URI. Provider-specific grant behavior still requires validation; cancellation returns null and leaves the current selection unchanged.
- Must test picking from Downloads, internal storage, and any cloud/document provider available; select a destination folder; extract; inspect; cancel; and verify permissions after Activity/process recreation.
- This change does not prove the earlier intermittent "Use this folder" issue is fully resolved. It removes the deprecated result callback path and improves lifecycle correctness, while provider compatibility remains an end-to-end test requirement.


## Build-toolchain compatibility correction — 2026-11-10

### Failure diagnosis
- Runs #219–#228 failed at `:app:checkDebugAarMetadata`, before Kotlin compilation or unit tests.
- Exact root cause from Actions logs: AndroidX Activity 1.13.0 and its transitive AndroidX Core 1.18.0 require compileSdk 36+ and AGP 8.9.1+. The repository still had compileSdk 35 and AGP 8.7.3.
- This was a dependency/toolchain compatibility miss in the earlier modernization. Do not describe it as a ZIP test failure.

### Research and selected alignment
- Official AndroidX Activity release notes identify 1.13.0 as stable and 1.14.0-alpha03 as prerelease: https://developer.android.com/jetpack/androidx/releases/activity
- Official AGP API reference lists 9.5.1 as current stable and 9.6.0-alpha03 as preview: https://developer.android.com/reference/tools/gradle-api
- Official AGP 9.5 release notes specify Gradle 9.6.0 minimum/default, SDK Build Tools 36.0.0 and JDK 17: https://developer.android.com/build/releases/agp-9-5-0-release-notes
- Official AGP guidance demonstrates Kotlin Android plugin 2.4.10 with AGP 9.5.0: https://developer.android.com/build/releases/about-agp
- CI previously ran Gradle 9.8.2 and JDK 17, satisfying the documented Gradle/JDK minimums for AGP 9.5.1. No wrapper file is tracked in this repository tree; verify the CI setup action's actual Gradle selection from the next run.
- Updated root plugin versions to AGP 9.5.1 and Kotlin Android 2.4.10; raised app compileSdk to 36. Kept targetSdk 35 intentionally separate: compileSdk enables compilation against newer APIs; targetSdk opts into runtime behavior and must be changed only after a separate behavior/compatibility review.
- This is a toolchain alignment, not a reason to jump targetSdk or minSdk without research.

### Pending validation
- Next CI run must resolve plugin and Kotlin DSL compatibility and execute `testDebugUnitTest`. If AGP 9.5.1 exposes migration issues, inspect exact compiler/plugin errors and follow official AGP 9 migration guidance rather than randomly downgrading dependencies.
- No APK build requested or triggered intentionally; no merge to main.


### AGP 9 built-in Kotlin follow-up
- Runs #229–#234 advanced past dependency metadata and failed while applying `org.jetbrains.kotlin.android`: AGP 9 already provides built-in Kotlin, so applying the external Kotlin Android plugin conflicts.
- Official migration guidance: remove `org.jetbrains.kotlin.android` from both root and module plugin declarations; migrate `android.kotlinOptions` to the `kotlin.compilerOptions` DSL. Sources: https://developer.android.com/build/migrate-to-built-in-kotlin and https://kotlinlang.org/docs/gradle-compiler-options.html
- Removed the external Kotlin Android plugin declaration/application and migrated JVM target 17 configuration to `kotlin.compilerOptions`.
- Next CI run must verify the built-in Kotlin DSL and compile the app/tests. Do not claim success until matched run passes.


## Session continuation — 2026-11-10 (live verification and ZIP inspection errors)

- Live GitHub re-sync confirmed PR #1 remains open and Draft, not merged; base `main` is still `ff50413b044cc788f7d1604728a365a76119889f`. Development branch before this batch was `3e9ab0b0070efdc2f3c18a1a6289b1b75293e9b4`. Runs #239/#240 both passed on that SHA; the `test` job and `Run unit tests` step succeeded. The preview APK job was skipped.
- Source inspection confirmed MainActivity uses AndroidX Activity Result `OpenDocument` and `OpenDocumentTree`, performs provider metadata/readability probing on the worker, and routes app extraction through `extractCentralDirectory`. These are code/CI facts only; real SAF provider and OnePlus Pad 2 testing remain pending.
- Modern-first research for the SAF picker was rechecked against Android's official Activity Result guidance/contracts and AndroidX Activity release notes. Stable Activity 1.13.0 remains selected; 1.14.0-alpha03 remains prerelease. Sources: https://developer.android.com/training/basics/intents/result ; https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument ; https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocumentTree ; https://developer.android.com/jetpack/androidx/releases/activity
- Reviewed Commons Compress 1.28.0 official release notes, security page, and ZipFile Javadoc. The current bounded seekable Central Directory approach remains suitable for this ZIP path; no dependency change was made. Sources: https://commons.apache.org/proper/commons-compress/changes.html ; https://commons.apache.org/proper/commons-compress/security.html ; https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.html
- Found and fixed a semantic error in `ZipInspectionEngine.inspectCentralDirectory`: all Central Directory exceptions were previously converted to `ZipInspectionLimitException`, incorrectly labeling unsafe paths, CRC mismatches, and structural conflicts as size-limit failures.
- Added distinct public inspection errors: `ZipInspectionUnsafePathException`, `ZipInspectionIntegrityException`, and `ZipInspectionFormatException`; retained `ZipInspectionLimitException` only for actual configured limits and preserved cancellation mapping.
- Added tests for CRC-corrupted payloads, unsafe entry paths, and file-vs-directory path conflicts. Changed files: `app/src/main/java/com/arcnova/app/engine/ZipInspectionEngine.kt`, `app/src/test/java/com/arcnova/app/engine/ZipInspectionEngineTest.kt`, `docs/ENGINE_STATUS.md`.
- Code commits: `2363268598fcc3c68e5a4312b1835f43b4ab9636`, `83a9f497ac0a0742eef50b4cb9b6175afcbd4560`, `b422019e4261d3e3e876b2ec4fc6ce3bceb611bb`. Documentation commit: pending this handoff update.
- Latest code-head CI Run #245 (PR event) and associated push runs were in progress at handoff preparation; verify matching SHA after documentation commits before calling the new tests passed. Do not confuse earlier successful Runs #239/#240 with the new code.
- No APK built. No physical-device test. No `main` changes and no merge.
- Next task: verify CI on the final docs-updated head; inspect/fix any test failure. Then continue Phase 1 adversarial ZIP and SAF-provider coverage, preserving the modern AndroidX picker and the experimental/production status already documented.


### CI follow-up — ZIP inspection CRC fixture correction

- PR Run #243 / push Run #242 failed during test-source compilation, not production Kotlin compilation or ZIP runtime tests. Exact compiler diagnostic: `ZipInspectionEngineTest.kt:111:47 Argument type mismatch: actual type is 'ByteArray', but 'Byte' was expected.`
- Root cause: the new test used `ByteArray.indexOf(ByteArray)`, but Kotlin's `indexOf` searches for one `Byte`, not a byte subsequence.
- Replaced it with a bounded explicit subsequence search in the test fixture. Fix commit: `a58cebbe3c042b7299c1dbffa27c77657a1e9b15`.
- Matching CI for this fix is queued as Run #250 (push) at handoff time. Verify its exact SHA/result before calling the ZIP inspection changes CI-tested. Earlier failures #242/#243 must remain recorded as test-fixture compilation failure, not as production code failure.


### CI follow-up — deterministic CRC corruption fixture (2026-11-10)

- Runs #250–#253 compiled production and test Kotlin successfully and executed 145 tests, but the new CRC test failed at its assertion expecting a CRC-specific exception. The original fixture used DEFLATED data; flipping a compressed byte can cause decompression failure before the backend reaches its explicit CRC comparison.
- Reworked the fixture to create a ZIP STORED entry with known size and CRC, then flip one payload byte while leaving the central-directory CRC unchanged. This isolates the intended CRC mismatch path. Fix commit: `e87738029b06da2fc33f6897b79e956a084abdf0`.
- This is a test-fixture correction, not a change to production ZIP validation. Await and inspect matching CI before marking this test passed.


### ZIP unsupported-feature preflight — 2026-11-10

- Rechecked Apache Commons Compress 1.28.0 official ZIP guide and ZipFile API. The library explicitly documents that ZipFile.canReadEntryData(entry) returns false for encrypted entries or compression methods not implemented by the library; attempting to open such entries throws UnsupportedZipFeatureException. Sources: https://commons.apache.org/proper/commons-compress/zip ; https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.html ; https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/UnsupportedZipFeatureException.html
- Added a Central Directory preflight guard using canReadEntryData before returning the archive to extraction/inspection callers. Unsupported/encrypted entries now fail before any destination payload is written and are reported via CentralDirectoryZipUnsupportedFeatureException.
- Added a regression fixture that sets the traditional encryption flag in both local and Central Directory headers, then asserts the backend rejects the archive during preflight and cleans the temporary spool.
- Commits: backend d4e862a9cad54e6ce0cd325f678fa58f797caa05; test b3ed11674b3514d97d4ac8cc1064647f55ad140a.
- This does not add password support. Commons Compress still cannot decrypt encrypted ZIP entries; the behavior is explicit early rejection. A separate Zip4j evaluation is required before any password-protected ZIP feature is claimed.
- Matching CI is pending. No APK built, no physical-device validation, no main-branch change, no merge.

## Continuation — source reuse policy and ZIP preflight compile fix (2026-11-10)

### Owner-confirmed ArcNova identity rule
- The owner clarified that ArcNova must not take/adapt code from a project if doing so would require ArcNova to use or display that project's name/brand as part of the integration. If a component requires that kind of naming/branding, do not use its code; study public technical concepts and build an independent implementation or select a different component.
- Source reuse is acceptable only when the exact license/version permits the intended modification, integration, and distribution without third-party product branding in ArcNova's user-facing identity. Public availability is not a license waiver.
- Never remove legally required notices or misrepresent upstream authorship. Keep mandatory legal notices in appropriate documentation when allowed; if a license requires a visible credit, naming, reciprocal source release, or other incompatible condition, reject that code and choose/reimplement another solution.
- Keep ArcNova's brand, UI, orchestration, adapters, and workflows independent. Distinguish ArcNova-authored work from upstream components and preserve provenance in `THIRD_PARTY_NOTICES.md`. This is now recorded in `AGENTS.md` section 17.

### CI failure diagnosis and correction
- Live check of PR #1 confirmed it remains open and Draft, not merged. The latest head before this continuation was `0a2a1e609df994bba12882caf1e8cec807a78234`; `main` remains unchanged.
- PR workflow Run #263 (run ID `42733839062`) failed during Kotlin compilation before unit tests ran. Exact diagnostic: `CentralDirectoryZipBackend.kt:75:18 Unresolved reference 'zipFile'`.
- Root cause: the unsupported-feature preflight referenced the archive reader from an object-level helper where that reader was not in scope. Fixed by passing the opened `ZipFile` explicitly into `preflight(...)`, retaining the early `canReadEntryData(entry)` guard before any payload write.
- Code fix commit: `3abec59c3f640b6097d54161f2b2baece7e80ac1`. This is a compile-scope correction; it does not add encrypted ZIP/password support.
- Added the owner-confirmed identity/source-reuse policy to this handoff and `AGENTS.md` section 17. Documentation commits follow the code fix. Matching CI for the corrected head is pending and must be checked before reporting success.
- No APK built, no physical-device test, no changes to `main`, and no PR merge.

### Next task
- Verify the latest branch head and CI after these commits. If green, continue Phase 1 with targeted ZIP unsupported-feature/encryption behavior tests and broader adversarial archive coverage. Continue to research candidates against the owner's independent-identity rule; do not integrate a source project that requires incompatible product naming/branding.

### Unsupported ZIP compression method preflight regression (2026-11-10)
- Research checked the Commons Compress 1.28.0 ZIP guide, `ZipFile.canReadEntryData(ZipArchiveEntry)` Javadoc, `ZipMethod` Javadoc, and unsupported-feature exception docs.
- Official references: https://commons.apache.org/proper/commons-compress/zip ; https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.html ; https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipMethod.html ; https://commons.apache.org/proper/commons-compress/changes.html . The documented API says `canReadEntryData` may return false for encryption or compression methods not implemented by the reader. Commons Compress 1.28.0 documents ZIP Zstandard support (method 93 and deprecated method 20), so do not incorrectly label every non-DEFLATE method unsupported.
- Added a backend regression fixture that changes both the local and central-directory method fields to an unknown method code (22222), then asserts `CentralDirectoryZipUnsupportedFeatureException` is thrown during preflight and the spool file is removed.
- Test commit: `c61b586458d0f088ffe147475b19e517369d3bab`. Matching CI is pending; do not report this new test as passing until the exact head has a successful unit-test run.
- No dependency change, no APK, no device test, no main change, no merge.


## Verified continuation — 2026-11-10 (ZIP feature handling)

- Live GitHub verification: development branch `feature/phase1-zip-transactional-rollback` currently advanced through commit `2c5b2c26a04cafa97e8af4f42fe736a9b65ffa2d`; PR #1 remains open and unmerged; `main` remains `ff50413b044cc788f7d1604728a365a76119889f`.
- GitHub Actions Runs #270/#271 passed for code commit `c61b586458d0f088ffe147475b19e517369d3bab`; Runs #272/#273 passed for current docs-inclusive head `2c5b2c26a04cafa97e8af4f42fe736a9b65ffa2d`. The `test` job succeeded; `preview-apk` was skipped. This validates the unknown compression-method preflight regression test. No APK was built.
- The Commons Compress backend rejects encrypted and unknown/unsupported compression entries during preflight using `ZipFile.canReadEntryData(entry)`; supported ZIP compression methods must not be mistakenly rejected by a simplistic DEFLATE-only allowlist.
- Follow-up error-taxonomy improvement: `ZipInspectionEngine.inspectCentralDirectory` now maps `CentralDirectoryZipUnsupportedFeatureException` to the distinct public `ZipInspectionUnsupportedFeatureException`, instead of reporting an encrypted/unsupported entry as malformed ZIP format. A regression test mutates both local and central ZIP encryption flags and verifies the public error type and spool cleanup.
- The above taxonomy code/test commits are newer than Run #273 and require fresh CI validation; do not describe them as passed until a run for the latest branch head completes.
- Still pending: map unsupported-feature errors to a dedicated public extraction exception if useful; validate Android SAF picker and extraction on the actual OnePlus Pad 2; finish Phase 1 exit criteria before Phase 2. No APK until all eight phases are complete and owner explicitly authorizes final packaging. No merge or changes to `main`.


## Verified continuation — 2026-11-10 (file signature registry alignment)

- Verified GitHub Actions Run #293 (`42758444074`) for commit `e04ca24aab70b0b2b64742cca853bdd0c3b38158`: workflow conclusion success; `test` job and `Run unit tests` step succeeded; `preview-apk` was skipped as required.
- Re-synced repository state before edits: active branch `feature/phase1-zip-transactional-rollback` at `e04ca24aab70b0b2b64742cca853bdd0c3b38158`; PR #1 open, Draft, unmerged; `main` remains `ff50413b044cc788f7d1604728a365a76119889f`.
- Aligned byte-signature detector IDs with the operation-level registry: UnityFS/UnityWeb/UnityRaw/UnityArchive/UnityWebData now resolve to `unity-asset-bundle`; RPA 2.0/3.0 resolves to `rpa`; USTAR resolves to `tar`.
- Added Microsoft CAB and ISO 9660 as registry entries in `GENERAL_ARCHIVE`, with DETECT marked CI-tested and INSPECT/EXTRACT remaining PLANNED. OLE Compound File remains a document/container detection result and is intentionally not misclassified as an archive capability.
- Updated capability notes to distinguish byte-signature recognition from filename-only hints and real parsing/decoding. Added registry tests for all new detect-only entries, consistent IDs, and extension lookup.
- Commits for this alignment: `ead5bf13d70b284bdc3de789d4b373b00807bcf7` (detector IDs), `3d4b9601cbc9c5ebad05541640e0ca2e6b897b44` (CAB/ISO registry entries), `4a24d50319b67910c41bdceb16c4de0bf504277a` (consistency tests), `1c9543c1b1e20957a9f263674b2e3b7750998f57` (engine status documentation).
- The registry-alignment commits require a fresh CI run on the final docs-inclusive head; do not report them as CI-tested until that run passes.
- No APK built, no physical-device validation, no change to `main`, and no PR merge.

### Next task
- Verify CI for the final registry-alignment head. Then continue Phase 1 by strengthening the universal file-analysis foundation (safe metadata inspection/reporting and SHA-256/comparison edge cases) while reviewing the Phase 1 exit gate. Do not start Phase 2 until the documented Phase 1 gate is met.


## Continuation — 2026-11-10 (bounded file-analysis header allocation)

- Verified the previous file-analysis stream lifecycle/read-failure tests on commit `7c2c39f697117abc29cc79773c07dd39afec686d`: push Run #310 and PR Run #311 both completed successfully; unit-test job passed and `preview-apk` was skipped.
- Source review found a resource-bound gap: `FileAnalysisAnalyzer.analyze` and `FileSignatureDetector.detect(InputStream)` accepted any positive `maxHeaderBytes`, then allocated a byte array of that caller-supplied size. This meant the nominally bounded detector could be asked to allocate an extremely large buffer.
- Both entry points now enforce a hard maximum of `FileSignatureDetector.DEFAULT_MAX_HEADER_BYTES` (64 KiB) before allocation. Added regression tests asserting that a request for 64 KiB + 1 is rejected in both APIs.
- Code/test commits: `da49ee135d18e58f0300df6a450d996dda0c2bce`, `77cff777974720fc50a2642581d4d55991687b81`, `647b6b464dc1bf0e884c636f514be6c2eef179d7`, `88830a01af9ab7d2a92c5a3ed54fdf64e5d2cbd9`. Status documentation commit follows.
- Matching CI runs for intermediate heads #312–#315 were still running when this handoff was prepared; final code head was queued as Run #316. Verify the final docs-inclusive head and its PR/push unit-test results before marking this bound CI-tested.
- Current branch remains `feature/phase1-zip-transactional-rollback`; PR #1 remains open/Draft/unmerged; `main` remains `ff50413b044cc788f7d1604728a365a76119889f`. No APK, no device test, no merge.
- Next task after CI: continue the Phase 1 universal file-analysis foundation with metadata/report edge cases and comparison behavior, then reconcile the Phase 1 exit gate. Do not begin Phase 2 or final UI early.


## Continuation — 2026-11-10 (SHA-256 report formatting)

- Verified the bounded-header change on final documentation-inclusive commit `5784c6a9a944a8987dba19dfc7f5c3e845a2007f`: push Run #318 and PR Run #319 both succeeded; `Run unit tests` passed and `preview-apk` was skipped.
- Continued review of `FileAnalysisAnalyzer` and found avoidable per-byte allocation in digest presentation: `String.format`-style formatting was invoked once for every SHA-256 output byte. Replaced it with direct nibble-to-hex character encoding, preserving the same 64-character lowercase digest and the streaming hash implementation.
- Added a known SHA-256 test vector for all 256 possible byte values, verifying unsigned-byte and hexadecimal nibble handling.
- Code/test commits: `f9a53ea8dddd4995dfc389476eeea8142c49a8c8`, `cf9b88c3b777da76b1a35e5ef1269e2580ea43e1`. Documentation updates follow; verify CI for the final docs-inclusive head before marking this optimization CI-tested.
- No API or report schema change. No APK, device test, PR merge, or change to `main`.
- Next task: inspect Phase 1 comparison/report semantics and reconcile the documented exit gate against actual code and tests.


## Continuation — 2026-11-10 (Phase 1 Batch 1 ZIP contracts)

- Confirmed the active branch and local HEAD before editing: `feature/phase1-zip-transactional-rollback` at `f36d8d0750839c82e42d829d15e05121d745d7b3`; worktree was clean. No branch switch, commit, push, merge, or APK build was performed.
- ZIP extraction selection now checks the detector's multipart flag; numbered `.zip.001` parts remain detected as ZIP but are not offered to the single-file backend. Added focused detector tests for multipart rejection and ordinary ZIP extension/signature acceptance.
- Central Directory duplicate normalized paths now produce a typed backend and public inspection exception. The app explicitly says unsafe/duplicate path rejection did not produce a risk report; those cases remain rejected before payload processing. Added a normalized backslash/slash duplicate regression test and updated README/engine/backend status documentation to describe the actual report contract.
- Legacy `ZipInputStream` extraction now closes its ZIP reader/inflater through a non-closing wrapper, preserving caller ownership of the input stream. Added a failure-path stream-ownership regression test; the existing success-path ownership test remains.
- Focused `gradle testDebugUnitTest` for `ArchiveFormatDetectorTest`, `ZipInspectionEngineTest`, and `ZipExtractionEngineTest` stopped before compilation/tests because no Android SDK is configured (`ANDROID_HOME` and `sdk.dir` absent). Editor diagnostics found no errors in touched Kotlin files; `git diff --check` passed. Tests are not verified as passing.
- Next: provide/configure an Android SDK and run the focused unit tests, then the full `gradle testDebugUnitTest`; review the final diff before any commit. Physical-device/SAF-provider testing remains pending. Do not build an APK or advance beyond Phase 1.


## Continuation — 2026-11-10 (split ZIP filename hints)

- Added `.zNN` split-ZIP first-volume filename detection alongside the existing `.zip.NNN` pattern. Ordinary `.zip` files remain single-volume eligible; existing RAR `.partN.rar` and `.rNN` detection is unchanged and now has regression coverage.
- Updated `docs/ENGINE_STATUS.md` to describe filename hints as partial: complete volume-set discovery, validation, and multipart extraction remain unimplemented.
- Focused `gradle testDebugUnitTest --tests 'com.arcnova.app.engine.ArchiveFormatDetectorTest'` stopped before compilation/tests because Android SDK configuration is missing. Editor diagnostics found no Kotlin errors and `git diff --check` passed; the new tests are not verified as passing.
- No branch change, staging, commit, push, merge, or APK build. Continue with SDK-backed test execution when an SDK is available; preserve the English-only project language policy.


## Continuation — 2026-11-10 (ZIP extension/signature eligibility)

- `ArchiveDetection.supportsZipExtraction` remains the production eligibility check used by `MainActivity`. It blocks multipart files regardless of signature, rejects conflicts between a known filename format and a known signature, permits a known `.zip` extension when the signature is absent or unknown, and permits a ZIP signature when the filename extension is unknown.
- `FileAnalysisAnalyzer` continues to report known extension/signature mismatch as `false`; the picker now disables extraction and explicitly reports a mismatch instead of letting one known signal override the other. RAR multipart detection is unchanged and regression-tested for `.partN.rar` and `.rNN`.
- Focused `gradle testDebugUnitTest --tests 'com.arcnova.app.engine.ArchiveFormatDetectorTest'` stopped before compilation/tests because Android SDK configuration is missing. Editor diagnostics found no Kotlin errors and `git diff --check` passed; the tests are not verified as passing.


## Continuation — 2026-11-10 (Batch 1 review follow-ups)

- Corrected `docs/ZIP_BACKEND_EVALUATION.md` to describe the integrated Central Directory ZIP path and remaining validation gates. `docs/ENGINE_STATUS.md` now states that CI-tested labels refer to previously validated revisions; current uncommitted Batch 1 changes are not CI-validated.
- Expanded ZIP eligibility tests for `ArchiveFormat.UNKNOWN` signatures/extensions and for multipart ZIP rejection with null and non-ZIP signatures.
- Added an internal `ZipInputStream` factory seam used only by tests to verify that the reader is closed on success and failure while the caller-owned input remains open. The public extraction API still constructs the standard `ZipInputStream`; existing ownership tests remain unchanged.
- Local `gradle testDebugUnitTest` completed successfully: 171 tests, 0 failures, 0 errors, and 0 skipped. The local run used JDK 21 with Android SDK Platform 36 and Build Tools 36.0.0.
- The CI workflow specifies JDK 17, so this local result was obtained with a different JDK than CI. The current uncommitted Batch 1 changes have not been CI-validated.
- Physical Android device and real SAF-provider validation remain pending. No APK was built.


## Continuation — 2026-11-10 (staging cleanup failure reporting)

- `MainActivity` now checks staging-directory cleanup. Cleanup failures after an otherwise successful extraction/copy are reported in the UI; when extraction or SAF copy already failed, the cleanup `IOException` is attached as suppressed and the original failure remains primary.
- Added `ExtractionStagingCleanup` with focused tests for successful deletion, failed deletion reporting, and suppressed cleanup failure preservation.
- `gradle testDebugUnitTest --tests "com.arcnova.app.engine.ExtractionStagingCleanupTest"` completed successfully. No APK was built. Physical Android device and real SAF-provider validation remain pending.


## Verified continuation — 2026-11-10 (PR merge and current Phase 1 baseline)

- Live GitHub state supersedes earlier handoff entries that describe PR #1 as open or unmerged: PR #1 is **merged** into `main` at merge commit `3fe77df5d50bed9890abbeb3331928229aaab2a7`. The PR was closed/merged at 2026-11-10 14:41:20 UTC. Do not repeat the stale open/Draft status above as current.
- The active development branch `feature/phase1-zip-transactional-rollback` and `main` currently point to the merged project state at `3fe77df5d50bed9890abbeb3331928229aaab2a7`. The earlier main SHA `ff50413b044cc788f7d1604728a365a76119889f` is the merge base, not current main.
- Latest Actions run: [Run #42836624965](https://github.com/omarhamed2232003-hash/ArcNova/actions/runs/42836624965), head SHA `3fe77df5d50bed9890abbeb3331928229aaab2a7`. Job `test` and step `Run unit tests` completed successfully. Job `preview-apk` was skipped. No APK was built.
- Source review confirms that MainActivity routes ZIP inspection/extraction through the Central Directory backend; the ZIP extraction path uses bounded spooling, metadata preflight, streamed size/CRC checks, and transactional filesystem rollback. SAF destination copy uses `TransactionalTreeCopy` with collision checks, cancellation, created-document tracking, and reverse-order rollback.
- Phase 1 also includes bounded file-signature detection, streaming file analysis/SHA-256, file comparison, and an operation-level capability registry. Selected CAB/ISO/game-container signatures and split-volume filename patterns are detection hints only; they do not imply parsers or extraction support.
- Corrected `docs/ENGINE_STATUS.md` and `THIRD_PARTY_NOTICES.md` to remove stale statements that contradicted the merged source (including the outdated claim that Commons Compress was not wired into app extraction/inspection). The exact current revision remains the authority.
- Phase 1 roadmap exit gate remains: all unit tests pass; inspection/extraction enforce resource limits and cleanup/rollback; failed operations do not silently appear complete; and capability labels match actual code. Latest CI provides the unit-test gate evidence. Real-device/SAF-provider validation is explicitly a later device-qualification requirement, not evidence to invent or claim now.
- Next engineering action: perform the final Phase 1 source-to-registry and failure-path review, fix any concrete discrepancy found, run unit-test CI on the resulting head, then reconcile the roadmap/handoff gate. Do not start Phase 2 until the documented Phase 1 gate is honestly satisfied.
- No APK build. Preserve the one-preview-only rule and all eight phases, including mandatory Phase 6.
