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

Decision: investigate a clean ARM64 build in an isolated test target first. Do not vendor the source into ArcNova until license and provenance are verified.

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

1. Verify SREP source license and inspect architecture-specific code.
2. Build a minimal SREP decoder test for Android `arm64-v8a` using the NDK; do not yet integrate it into the app.
3. Obtain a small legal SREP test fixture and compare output byte-for-byte/CRC.
4. Identify the exact LOLZ codec variant used by the archive and investigate only lawful, redistributable implementations.
5. Separately implement FreeArc volume parsing and split-volume discovery.
6. Only then wire decoders into the Android app and attempt a real archive.

## Sources reviewed

- SREP upstream: https://github.com/Intensity/srep
- SREP upstream README: https://github.com/Intensity/srep/blob/main/README.md
- SREP upstream Makefile: https://github.com/Intensity/srep/blob/main/Makefile
- Cellar research: https://github.com/joemunene-by/cellar
