# Engine status

| Capability | Status | Notes |
|---|---|---|
| Android document picker | Implemented, CI-tested | Uses AndroidX Activity Result `OpenDocument` / `OpenDocumentTree`; provider metadata and readability probes run on the worker thread. SAF destination copy uses transactional created-document tracking, collision checks, cancellation, and reverse-order rollback. Real provider/device behavior remains pending. |
| ZIP extraction | Implemented, CI-tested | Bounded output, safe paths, cancellation, no overwrite, transactional rollback of all files/directories created by a failed extraction; pre-existing content is preserved. The SAF copy is separately transactional through created-document tracking and reverse-order rollback; real provider/device behavior remains pending. |
| ZIP inspection | Implemented, CI-tested | Streams entries without writing them; reports unsafe paths and duplicates |
| Seekable ZIP archive spool | Implemented, CI-tested | Copies caller-owned input to a bounded private temp file for Central Directory access; supports cancellation and explicit cleanup. |
| Central Directory ZIP adapter | Integrated into app inspection and extraction paths; CI-tested | MainActivity routes ZIP extraction through `extractCentralDirectory`, which performs bounded seekable spooling and Central Directory preflight before writing. It rejects unsafe, duplicate, and file/directory-conflicting paths and validates actual sizes, expansion ratio, and CRC32 while streaming; filesystem rollback remains enabled. App spool input cap is currently 2 GiB. The legacy streaming engine remains available for regression coverage. Physical-device/provider validation is pending. |
| ZIP heuristic risk assessment | Implemented, CI-tested | Advisory indicators only; not malware scanning |
| Generic file signature detection | Implemented, CI-tested | Bounded header recognition; not full-file validation |
| Streaming file analysis | Implemented, CI-tested | Size, signature/extension comparison, SHA-256 |
| Streaming file comparison | Implemented, CI-tested | Compares sizes and SHA-256 without loading full files into memory |
| Split-part detection | Not implemented | Research/implementation pending |
| SREP decoder | Blocked | Upstream source reserves all rights; no permission or redistributable license verified |
| LOLZ decoder | Blocked | No redistributable native Android ARM64 implementation verified; known CLS plugins are proprietary |
| FitGirl installer pipeline | Not implemented | User-provided fixtures only; no game payloads in repository |
| Physical-device validation | Pending | CI is not proof of Android device behavior |

A CI-tested implementation is not automatically device-validated, production-ready, or malware-safe. Do not assume an algorithm name implies an available redistributable decoder. Record upstream URL, version/commit, license, tested architectures, and fixture provenance before integration. Do not bundle proprietary CLS DLLs/executables or copyrighted game files.


- Extraction now translates archive-spool cancellation and input-size-limit failures into the public `ZipExtractionCancelledException` / `ZipExtractionLimitException` contract. This ensures the app-facing engine reports consistent errors even when failure occurs before the Central Directory reader opens.


- ZIP extraction now distinguishes policy-limit failures from malformed-archive failures at the backend boundary via `CentralDirectoryZipLimitException`; the app-facing extraction API maps backend cancellation and limit violations to `ZipExtractionCancelledException` and `ZipExtractionLimitException`. The actual-stream total-output rollback test now asserts the public limit exception and verifies prior destination content is preserved. This latest refinement is awaiting CI.


- Latest passing CI run #189 validates the extraction error-contract/actual-output rollback batch on the PR merge ref. A follow-up now normalizes Central Directory preflight policy-limit failures (entry count, declared sizes, expansion ratio, path length/depth) to `CentralDirectoryZipLimitException`, which the extraction facade maps to `ZipExtractionLimitException`. Added a test asserting a declared-output preflight rejection happens before creating the destination and cleans the spool. This newest refinement is pending CI.


- ZIP inspection error contract refinement (2026-11-10): inspection now distinguishes policy-limit failures, unsafe entry paths, CRC/integrity failures, and invalid archive structure instead of mapping every Central Directory exception to a size-limit error. New regression tests cover unsafe paths, CRC corruption, and file/directory path conflicts. Code commits: `2363268598fcc3c68e5a4312b1835f43b4ab9636`, `83a9f497ac0a0742eef50b4cb9b6175afcbd4560`, `b422019e4261d3e3e876b2ec4fc6ce3bceb611bb`. Matching CI is running; do not mark this refinement CI-tested until Run #245 (or a later run on the same head) succeeds.
