# ArcNova

Android-first research project for inspecting and eventually extracting split archives using compression pipelines such as SREP and LOLZ.

**Status:** initial scaffold. SREP and LOLZ are NOT implemented. No compatibility claim is made until a real decoder is integrated and tested.

## Milestones
1. Android file access and read-only archive inspection.
2. Research decoder source, license, ARM64 support, and compatibility.
3. Integrate only legally redistributable engines.
4. Validate with known-good fixtures, split archives, cancellation, and large files.
5. Publish APKs only after tests pass.

## Build
Requires Android Studio or Gradle with JDK 17 and Android SDK 35:
```bash
gradle assembleDebug
```

No game data, FitGirl installer binaries, or proprietary CLS executables are included. Original archive parts must remain untouched; extracted output will go to a user-selected destination.
