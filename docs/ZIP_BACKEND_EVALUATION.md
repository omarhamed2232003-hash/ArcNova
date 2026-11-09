# ZIP backend evaluation — Android / ARM64

**Status:** research decision; no dependency has been added and no backend has been marked implemented.

## Decision summary

Use **Apache Commons Compress as the preferred general archive foundation**, subject to a small Android integration spike and adversarial tests. For ZIP extraction specifically, prefer its seekable `ZipFile` API over any streaming local-header reader. Keep **Zip4j as a credible specialist alternative** if password-protected ZIP support becomes an immediate requirement or if its Android behavior proves materially better in the spike.

Do not integrate both libraries by default. Avoid adding dependencies until the selected API passes the same ArcNova safety and rollback tests.

## Why this matters

ZIP has local file records followed by a Central Directory at the end. A stream-only reader cannot reliably determine which local records are authoritative. Apache's own documentation explains that `ZipArchiveInputStream` (like Java's `ZipInputStream`) can expose entries absent from the Central Directory or duplicate names, and may lack complete metadata; its `ZipFile` reads the Central Directory first and is recommended where possible. This directly addresses the behavior found in ArcNova's truncated-directory investigation.

## Candidate comparison

| Area | Apache Commons Compress 1.28.0 | Zip4j 2.11.6 |
|---|---|---|
| License | Apache-2.0 | Apache-2.0 |
| Runtime baseline | Java 8+; ArcNova builds with Java 17 | Java 8+ in current 2.x; Android use is documented |
| ZIP Central Directory | `ZipFile` reads it first; use seekable file access | `ZipFile.getFileHeaders()` reads central-directory headers |
| Stream-only API | `ZipArchiveInputStream` has documented limitations | Has stream API, but it reads local headers; use file-based `ZipFile` for central-directory-first workflow |
| Password ZIP | No ZIP encryption support | AES and traditional ZIP encryption supported |
| Split ZIP | Support exists in newer versions, with limitations | Explicit split ZIP support |
| Broader archive roadmap | ZIP, TAR, 7z, CPIO, AR, ARJ, dump, and more; some formats read-only or optional dependencies | ZIP-focused |
| Dependencies / footprint | More dependencies (Commons IO, Lang, Codec); optional codecs can add more | Smaller, pure-Java focused ZIP dependency |
| Release / maintenance signal | 1.28.0 published 2025-07-26; official security page lists known fixed issues | 2.11.6 is the latest listed release; repository shows active issue traffic, including 2026 issues asking about a new release |
| Android ARM64 | Java implementation is a plausible fit; still requires Android build/runtime verification | Java implementation and Android usage are documented; still requires app-specific verification |

## Security and integration notes

### Apache Commons Compress

- Pin 1.28.0 or a later reviewed release; do not use old versions. The official security page lists historical denial-of-service issues fixed in later releases (including fixes in 1.19, 1.21, 1.24, and 1.26).
- Core ZIP/TAR Java code does not require an ARM64 native library. Optional codec integrations may introduce native or additional runtime dependencies; do not enable them blindly.
- The `ZipFile` API needs seekable file access. ArcNova receives SAF `InputStream` objects, so a safe bounded spool to private cache is needed before opening the archive. Account for temporary storage and cancellation.
- ZIP encryption is not supported by Commons Compress. Do not silently claim password-protected ZIP support.

### Zip4j

- Apache-2.0 and Java/Android-friendly, with AES and standard ZIP encryption, Zip64, split ZIP, Unicode names, and progress APIs.
- Use `ZipFile` / `getFileHeaders()` plus per-header input streams for a Central-Directory-first path. Do not assume its `ZipInputStream` streaming API validates the Central Directory.
- Its documented `isValidZipFile()` checks headers, not the integrity of every entry's payload. Payload reads still need full consumption and CRC/integrity tests.
- The latest listed release is 2.11.6; repository issue activity in 2026 includes requests for a newer release. This is a maintenance-cadence consideration, not proof of abandonment or a known vulnerability.

## Recommended architecture

1. Keep ArcNova's policy layer: safe normalized paths, duplicate-path rejection, output and entry limits, expansion-ratio checks, cancellation, progress, no-overwrite, and transactional rollback.
2. Put the parser/decoder behind a narrow ZIP backend interface. Do not let library extraction-to-directory APIs bypass ArcNova's policy and rollback.
3. For the first implementation spike, use Commons Compress `ZipFile` with a private seekable temp archive, enumerate Central Directory entries, validate all names and metadata before writing, then stream each selected entry through ArcNova's bounded transactional writer.
4. Consume every entry stream fully so decompression errors and CRC failures surface. Validate declared sizes against actual bytes and ArcNova limits. Treat central-directory presence as structural validation, not proof that payloads are intact.
5. Keep ZIP encryption as a clearly unsupported capability until a separate Zip4j adapter and password-handling design are implemented and tested.
6. Add tests for: missing/truncated Central Directory; local-header entries absent from Central Directory; duplicate central-directory names; duplicate normalized paths; CRC mismatch; truncated compressed payload; Zip64; data descriptors; stored and deflated entries; oversized metadata/counts; cancellation mid-entry; rollback and pre-existing-file preservation.
7. Measure APK/dependency size, peak memory, temp-storage overhead, extraction throughput, and behavior on Android API 26+ and a real ARM64 device before finalizing the backend.

## Current recommendation

**Preferred next spike: Commons Compress 1.28.0 `ZipFile`.** It best aligns with ArcNova's multi-format roadmap and has a documented Central-Directory-first API. Zip4j remains the fallback/specialist candidate for encrypted and split ZIP workflows. This is a research recommendation only; current ArcNova ZIP implementation remains the existing bounded `ZipInputStream` engine until a replacement passes the full regression suite.
