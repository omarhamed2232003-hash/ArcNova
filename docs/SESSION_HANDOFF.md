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
