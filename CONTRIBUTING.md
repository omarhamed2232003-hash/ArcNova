# Contributing to ArcNova

Thanks for helping improve ArcNova. The project is experimental and prioritizes correctness, security, reproducibility, and lawful component reuse.

## Before submitting
- Keep changes focused and explain user-visible behavior.
- Do not add game payloads, Steam client files, proprietary repack plugins, credentials, signing keys, or copyrighted assets.
- Do not copy code from GPL or other reciprocal-license projects until compatibility with ArcNova's licensing strategy has been reviewed.
- Record exact upstream versions, source commits, licenses, notices, and build steps for new dependencies.
- Treat archives and installer metadata as untrusted input.

## Tests and builds
Run:
```bash
gradle testDebugUnitTest
gradle assembleDebug
```

For archive/runtime changes, add tests for malformed input, cancellation, limits, path safety, and failure cleanup where applicable. Do not claim format or game support without reproducible fixtures and test evidence.

## Pull requests
Describe the problem, implementation, tests, security impact, and dependency/license changes. Include device/runtime details for compatibility reports. Never attach copyrighted game files; use small, legally redistributable test fixtures.
