# Privacy Notice

**Status:** Project-stage notice for the current ArcNova source scaffold, not a promise about future releases.

## Current data handling
The current repository scaffold does not implement user accounts, cloud storage, analytics, advertising, telemetry, or a backend service. It does not currently upload selected archives or game files to an ArcNova server. The app currently declares no Android permissions in its manifest.

Android, Google Play (if later used), GitHub Actions, and third-party components may have data practices outside ArcNova's control.

## Files and permissions
ArcNova's intended design is local-first. Future file features should use Android's Storage Access Framework and user-granted file access wherever practical. Future versions may read or write only files and destinations selected by the user, subject to permission prompts and feature behavior. This notice must be updated before adding broader storage permissions, background file access, network services, crash reporting, analytics, or telemetry.

## Credentials
ArcNova must not request or proxy Steam passwords, Steam Guard codes, session cookies, or other account secrets. Steam authentication, where supported, must use Valve's official client flow.

## Retention and deletion
The current scaffold does not maintain an ArcNova user database or server-side archive store. Future local logs, caches, runtime components, and game manifests must have documented locations and user-facing cleanup controls.

Update this notice whenever data collection, permissions, network communication, analytics, or third-party SDKs change.
