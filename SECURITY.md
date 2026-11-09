# Security Policy

## Scope
ArcNova is an early-stage Android archive and Windows-game compatibility project. Security controls are being built incrementally; this policy does not imply the current scaffold is production-hardened.

## Reporting
Do not publish exploitable details in a public issue. Use GitHub's **Report a vulnerability** feature if private vulnerability reporting is enabled. Otherwise contact the repository owner privately through a channel listed on the GitHub profile. Include the affected version/commit, Android version/device, reproduction steps, impact, and a safe proof of concept.

Do not access, modify, or disclose data that is not yours. Allow maintainers reasonable time to investigate and prepare a fix before public disclosure.

## Security design goals
- Treat archive paths, names, metadata, installer scripts, and game files as untrusted input.
- Prevent path traversal, absolute-path writes, symlink escapes, archive bombs, decompression-limit bypasses, integer overflow, partial-file corruption, and unsafe cleanup.
- Use bounded streaming, cancellation, temporary output, checksum verification, and safe cleanup.
- Request Android storage access only when a feature needs it; prefer the Storage Access Framework and user-selected URIs.
- Never collect Steam passwords, session cookies, or authentication tokens. Never send them to ArcNova servers.
- Never silently execute installers or scripts. Any future execution feature must require deliberate user action and clearly explain risks.
- Pin dependencies and audit source, build provenance, licenses, and transitive components.

## Current limitations
The current scaffold has no network client, account system, telemetry pipeline, archive extraction implementation, bundled Wine/Proton runtime, or game-launch capability. Revisit this statement whenever features are added.

Security fixes target the latest published release. Pre-release builds are experimental.
