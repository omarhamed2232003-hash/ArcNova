# Streaming runtime foundation

## What exists now

`StreamingTransfer` is the first bounded-I/O primitive in ArcNova. It copies from a caller-provided `InputStream` to a caller-provided `OutputStream` using a reusable 64 KiB buffer by default (configurable from 4 KiB to 1 MiB).

It provides:
- output-byte limits checked before each write;
- an expansion-ratio guard;
- cooperative cancellation between reads;
- progress callbacks with cumulative input/output byte counts;
- overflow checks for byte counters;
- stream ownership rules: it flushes the output but does not close either stream.

## Important boundary

This is **not yet an archive extractor, format reader, or decompressor**. It proves the bounded streaming and guardrail layer that future archive readers and codec engines can call. A codec may have its own dictionary/workspace memory requirements beyond this transfer buffer; those must be documented and tested per engine.

The ratio guard uses bytes consumed so far. Format adapters must choose suitable limits and account for codecs whose output/input ratio varies across blocks.

## Next integration

1. Add Android `ContentResolver` adapters that open selected document URIs without converting them to filesystem paths.
2. Add destination adapters using Storage Access Framework.
3. Connect archive-entry metadata to per-file and total extraction limits.
4. Write each entry to a temporary destination, verify size/checksum, then finalize it.
5. Add a real, license-reviewed archive backend and legal fixtures before exposing extraction in the UI.
