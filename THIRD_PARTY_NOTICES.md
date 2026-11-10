# Third-Party Notices & Component Provenance

This file tracks third-party software considered or used by ArcNova. Researching or mentioning a project does not mean its code or binaries are included. ArcNova's product identity, UI, orchestration, and integration layer remain independently developed.

## Current direct build dependencies

- AndroidX Activity KTX 1.13.0 — Apache-2.0; used for `ComponentActivity` and lifecycle-aware Activity Result contracts. Upstream: https://developer.android.com/jetpack/androidx/releases/activity. Preserve required license notices in distributed builds.
- Apache Commons Compress 1.28.0 — Apache-2.0; used by ArcNova's Central Directory ZIP inspection and extraction adapter through `ZipFile`. ArcNova retains its own policy, preflight, streaming validation, destination writing, and rollback layer. Preserve the upstream LICENSE/NOTICE material and review the exact resolved transitive dependency licenses before release. Upstream: https://commons.apache.org/proper/commons-compress/
- JUnit 4.13.2 — test dependency declared in `app/build.gradle.kts`. Retain the license notice for the exact artifact used in each distributed build.

## Projects studied as references (not bundled)

| Project | Purpose studied | Distribution decision |
|---|---|---|
| Winlator WB64 | Android Wine/Box64 orchestration and per-game profiles | Architecture reference only; audit exact source snapshot and every payload before reuse |
| Steamlator | Steam bootstrap and Android launch flow | Study flow only; do not copy Steam assets or runtime archives |
| Vapor | Steam-first Android launcher and qualification architecture | GPL-3.0; do not copy/adapt code until licensing compatibility is reviewed |
| Valve Proton | Wine-based Steam compatibility stack | Build/source reference; Android ARM64 adaptation and submodule audits required |
| Box64 | x86-64 Linux userspace translation on ARM64 | Candidate component; exact license, build and integration audit required |
| FEX | Alternative x86-64 translation | Candidate component; exact license, build and integration audit required |
| libarchive-android | General archive handling | Candidate library; not integrated; validate artifact, ABI, API and transitive licenses |
| PArc | Free Pascal archiver / SREP-related research | GPL-2.0 and platform limitations; no code currently bundled |
| SREP | Repack codec research | No redistribution permission established; do not bundle |
| LOLZ / CLS plugins | Repack codec research | No redistributable open implementation established; do not bundle proprietary plugins |

## Required record for every future component

Before integration or release, record its exact version and immutable source commit; source URL and build toolchain; license identifier and required license text; copyright notices, modifications and source obligations; submodules, dependencies and bundled binaries; ABI/Android compatibility; reproducible build provenance; whether it is compiled into the APK, downloaded separately, or invoked externally; and redistribution permission for every shipped binary and asset.

A top-level repository license does not override dependency licenses. If a license or redistribution right is unclear, keep the component excluded until resolved.
