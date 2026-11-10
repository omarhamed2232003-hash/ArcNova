# Engine status

This file describes the implementation at the current repository revision. CI-tested means the code and unit tests passed on a recorded GitHub Actions revision; it does not mean physical-device validation, release readiness, or malware scanning.

| Capability | Status | Notes |
|---|---|---|
| Android document picker | Implemented, CI-tested | Uses AndroidX Activity Result `OpenDocument` and `OpenDocumentTree`. Provider metadata/readability checks run on the worker thread. Real provider lifecycle, persisted grants, and device behavior remain unverified. |
| ZIP inspection | Implemented, CI-tested | Uses the Central Directory backend and bounded private seekable spool. Performs path preflight before returning a report; unsafe paths, duplicate normalized paths, malformed structures, integrity failures, limits, and unsupported features have distinct error outcomes. |
| ZIP extraction | Implemented, CI-tested | The app uses the Central Directory path. Enforces archive-input, entry-count, per-file, total-output, path, and expansion-ratio limits; rejects unsupported/encrypted entries before writing; validates streamed size and CRC32; supports cancellation and transactional rollback. |
| ZIP filesystem rollback | Implemented, CI-tested | Removes files/directories created by a failed extraction in reverse order and preserves pre-existing destination content. Cleanup failures are attached to the primary failure. |
| SAF destination copy | Implemented, CI-tested | Copies staged files through a transactional document-tree adapter with collision checks, cancellation, created-document tracking, reverse-order rollback, and explicit incomplete-rollback reporting. Fake-provider tests do not prove behavior of real Android providers. |
| Seekable archive spool | Implemented, CI-tested | Copies caller-owned input to bounded private cache storage, supports cancellation, preserves caller stream ownership, and explicitly cleans temporary files. App ZIP input cap is 2 GiB. |
| ZIP heuristic risk assessment | Implemented, CI-tested | Advisory archive indicators only. This is not antivirus, malware detection, or a safety guarantee. |
| Generic file signature detection | Implemented, CI-tested | Bounded header recognition for common files and selected containers including CAB, ISO 9660, VPK, XP3, Unity bundle variants, RPA, USTAR, and OLE Compound File. Recognition is not structural validation or extraction. |
| Streaming file analysis | Implemented, CI-tested | Streams file size and SHA-256 while retaining only a bounded header for signature/extension comparison. Header allocation is capped at 64 KiB. |
| Streaming file comparison | Implemented, CI-tested | Compares size and SHA-256 without loading complete files into memory. |
| Split-part filename hints | Partial | Recognizes selected ZIP, 7z, and RAR volume naming patterns. Complete set discovery, validation, and multipart extraction are not implemented. |
| 7z / RAR / TAR / gzip and other non-ZIP archive engines | Not implemented | Some signatures may be detected; no corresponding extraction engine is registered as available. |
| CAB / ISO 9660 operations | Detection only | Signature detection is implemented; parsing, inspection, mounting, and extraction are not. |
| Unity / VPK / XP3 / RPA operations | Detection only | Selected signatures are recognized; version-aware package parsing and extraction are not implemented. |
| Installer execution / Windows compatibility runtime | Not implemented | Current detector/planner scaffolding must not be presented as a functioning installer or Wine/Proton runtime. |
| SREP / LOLZ decoders | Blocked | No acceptable implementation and redistribution rights have been verified. Do not bundle proprietary plugins. |
| Physical-device / real SAF provider validation | Pending | Must be performed separately on target Android hardware and representative document providers. |

## Capability-labeling rules

- A byte-signature match is identification evidence, not proof that the complete file is valid.
- A filename extension is only a search hint; it is not a signature match.
- Detection does not imply inspection, extraction, creation, installation, or launch support.
- Only operations explicitly marked implemented and CI-tested in the capability registry may be offered as implemented. Device validation is a separate state.
- Encrypted ZIP entries and unsupported compression methods are rejected explicitly; password-protected ZIP support is not implemented.
- Do not describe heuristic archive risk indicators as antivirus or malware scanning.

## Current validation boundary

The latest verified GitHub Actions run on commit `3fe77df5d50bed9890abbeb3331928229aaab2a7` completed the `test` job successfully by running `gradle testDebugUnitTest`. The `preview-apk` job was skipped. This is CI unit-test evidence only. Physical-device testing, real SAF provider behavior, performance/thermal measurements, and release qualification remain pending.
