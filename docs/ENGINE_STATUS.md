# Engine status

| Capability | Status | Notes |
|---|---|---|
| Android document picker | Implemented, CI-tested | SAF-based archive and destination selection; destination copy uses transactional created-document tracking, collision checks, cancellation, and reverse-order rollback; incomplete provider rollback is reported explicitly. Real provider/device behavior remains pending. |
| ZIP extraction | Implemented, CI-tested | Bounded output, safe paths, cancellation, no overwrite, transactional rollback of all files/directories created by a failed extraction; pre-existing content is preserved. The SAF copy is separately transactional through created-document tracking and reverse-order rollback; real provider/device behavior remains pending. |
| ZIP inspection | Implemented, CI-tested | Streams entries without writing them; reports unsafe paths and duplicates |
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
