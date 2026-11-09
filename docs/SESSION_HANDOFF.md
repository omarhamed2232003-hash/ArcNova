# ArcNova — Current Session Handoff

> **Purpose:** Fast, reliable recovery when a new AI chat/session opens. Read this after `AGENTS.md`, then re-check GitHub before acting. This is a snapshot, not an authority over live repository state.
> **Snapshot verified:** 2026-11-09 20:05 UTC.

## Repository and working lane

- Repository: https://github.com/omarhamed2232003-hash/ArcNova
- Primary branch: `main`
- Active development branch: `feature/phase1-zip-transactional-rollback`
- Open PR: [#1 — Make ZIP extraction and SAF destination copy transactional](https://github.com/omarhamed2232003-hash/ArcNova/pull/1)
- PR #1 is **open, Draft, not merged**. Do not merge it or modify `main` without explicit owner authorization.
- Verified `main` HEAD: `ff50413b044cc788f7d1604728a365a76119889f`.
- Verified development HEAD at this snapshot: `415a95ff25fb38b478b1fffbde0fe03398598cff`.
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
- Added integration tests for successful extraction, Central Directory CRC failure rollback while preserving pre-existing files, cancellation rollback, and preflight path rejection. Added adversarial adapter tests for file/directory path conflicts, expansion-ratio limits, and cancellation while copying. Runs #127-#130 are pending at this snapshot; do not claim integration or newest tests passed until Actions completes.
- The original `ZipInputStream` API remains the production default. The new Central Directory path is experimental and callable but not yet promoted; full regression/adversarial CI and device validation remain required.
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

**Current concrete task:** verify CI runs #127 onward for `extractCentralDirectory` and the new rollback/adversarial tests. Fix any compile/test failures using job logs. Add any missing rollback tests (especially callback/output-write failure and total-output limits), then run the full existing ZIP regression suite. Do not promote this path to production default until the full suite passes; do not replace the existing extractor yet. Keep Zip4j as a specialist fallback for encrypted/split ZIP if needed.

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
