# Decoder feasibility research (2026-11-09)

This is a research note, not an implementation or compatibility claim.

## SREP: promising source candidate

Upstream candidate: https://github.com/Intensity/srep

Observed upstream facts:
- The repository describes SREP 3.93a beta, dated 2014-10-11, as an unofficial repackage of the last known source.
- Its README says it was tested on Debian Linux 11 and FreeBSD on Intel architecture; other targets may need modifications.
- The Makefile hard-codes Intel-oriented options/defines including `-msse2` and `FREEARC_INTEL_BYTE_ORDER`.
- The Makefile builds `Compression/Common.cpp` and `Compression/SREP/srep.cpp`, with the SREP decompression implementation among its dependencies.

Android ARM64 implications:
- This is source code, not a ready-made Android library.
- The x86/SSE2 build flags cannot be copied into an ARM64 build. We must inspect all architecture-specific code and use Android NDK/Clang with ARM64-safe settings.
- The upstream repository's license must be confirmed from the actual source headers or an explicit license file before copying or redistributing any code. No license conclusion is made here.
- Even a successful ARM64 compile does not prove compatibility with the exact SREP variant/configuration embedded in this FitGirl archive. A known-good, legally distributable fixture is required.

Decision: **do not start an ARM64 port from this source yet**. The licensing gate is currently failed/unresolved. Continue searching for an independently licensed SREP-compatible implementation or obtain permission from the rights holder. The architecture port becomes actionable only after that gate is cleared.

## LOLZ: major blocker

The Cellar project (https://github.com/joemunene-by/cellar) documents FitGirl's heavier data blocks as using closed-source CLS plugins such as `lolzi`, `lolzx`, `lolly`, and `lollypop`. Its approach uses a small Windows PE helper to load the corresponding CLS DLL through Wine.

Implications:
- Cellar is useful technical research, but its Wine/Windows helper architecture is not a native Android ARM64 decoder.
- The existence of `cls-lolz_x64.exe` or a DLL does not grant redistribution rights.
- ArcNova will not bundle proprietary CLS executables/DLLs or attempt to bypass licensing.
- We need either a documented, redistributable LOLZ decoder/source implementation, or a user-supplied compatible runtime with a license that permits the intended use. If neither exists, native LOLZ support may not be feasible.

## FreeArc container format

A FitGirl `fg-*.bin` is not simply a ZIP/7z file. ArcNova must eventually parse the FreeArc container and its block metadata, then dispatch each block to the correct decoder. A working SREP/LOLZ decoder alone is not enough.

Cellar's `freearc-native` notes support for container parsing and open codecs such as storing, LZMA, and Zstandard; its closed-codec path relies on CLS plugins and Wine. This is not evidence that those closed codecs can run natively on Android.

## Next engineering gates

1. Find a SREP-compatible implementation with an explicit license permitting the intended use, or obtain written permission for the upstream source.
2. Only after the license gate is cleared, build a minimal SREP decoder test for Android `arm64-v8a` using the NDK; do not yet integrate it into the app.
3. Obtain a small legal SREP test fixture and compare output byte-for-byte/CRC.
4. Identify the exact LOLZ codec variant used by the archive and investigate only lawful, redistributable implementations.
5. Separately implement FreeArc volume parsing and split-volume discovery.
6. Only then wire decoders into the Android app and attempt a real archive.

## Sources reviewed

- SREP upstream: https://github.com/Intensity/srep
- SREP upstream README: https://github.com/Intensity/srep/blob/main/README.md
- SREP upstream Makefile: https://github.com/Intensity/srep/blob/main/Makefile
- SREP source copyright/commercial-license notice: https://github.com/Intensity/srep/blob/main/Compression/SREP/decompress.cpp
- Cellar research: https://github.com/joemunene-by/cellar

## Additional implementation candidates reviewed

### PArc: SREP exists, but it is not a drop-in FreeArc reader

Repository: https://github.com/YadeWira/PArc

Observed facts from its current README and repository metadata:
- PArc is a Free Pascal archiver targeting Windows x86/x64 and Linux x64; Android ARM64 is not listed as a supported target.
- It implements an SREP codec among several codecs, including chains such as `srep+lzma`.
- **PArc does not read or write FreeArc `.arc` files.** Its native `.parc` container is a separate format; the README points to a companion FreeArc-ex project for `.arc` support.
- The repository declares GPL-2.0. Its README also attributes codec ports to FreeArc 0.67 sources and says original notices are preserved in `reference/`.

Decision:
- This is valuable evidence that an SREP implementation can exist in a Pascal codebase, but it does not solve FitGirl FreeArc container parsing or Android ARM64 support.
- Do not copy or port its implementation into ArcNova until we review the exact source-file notices and dependency provenance, decide whether GPL-2.0 is compatible with ArcNova's distribution goals, and verify all relevant rights. No code has been copied.

### libarchive-android: practical baseline for common formats

Project: https://github.com/zhanghai/libarchive-android

Observed facts:
- The Android package provides libarchive through Android NDK and Java bindings.
- Its published metadata identifies Apache-2.0; the repository README describes bundled zlib, bzip2, lzma, lz4, zstd and mbedcrypto support.
- The upstream libarchive format list includes common containers such as tar, ZIP, 7-Zip, RAR/RAR5, CAB, ISO9660, LHA/LZH and others.
- This is a candidate baseline for conventional archive support, not a FreeArc `.arc` or FitGirl SREP/LOLZ solution.

Decision:
- Evaluate it as an independently licensed common-format backend, with an explicit ABI/version check for arm64-v8a and streaming behavior before integration.
- Keep ArcNova's own safety policy, extraction path validation, cancellation and output limits around any third-party backend. Library support does not automatically provide all application-level protections.

## Updated direction

ArcNova should pursue two complementary tracks:
1. **Broad everyday archive support:** evaluate libarchive-android as a baseline for standard formats.
2. **Specialized codec/container research:** continue independent FreeArc container and SREP/LOLZ work, without assuming PArc is compatible or bundling proprietary CLS components.

The PArc review narrows the search: an SREP codec alone is insufficient; the target must also match the FreeArc container/block layout and run lawfully on Android ARM64.
