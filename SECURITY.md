# Security

Jarvis is designed to fail closed when a trust check fails. This document describes the current controls and their limits.

## Current controls

- Provider, cloud-voice, and embedded-runtime credentials are encrypted with AES-256-GCM. The encryption key is held by Android Keystore and is not stored in app preferences.
- On startup, legacy plaintext provider, TTS, and runtime credentials are migrated into encrypted storage. Old duplicate plaintext keys are removed after successful migration.
- Android app backups are disabled.
- Remote AI and voice endpoints must use HTTPS. Cleartext HTTP is allowed only for loopback services on the same device.
- Model downloads use HTTPS, an allowlist of GitHub release hosts, fixed expected sizes, SHA-256 checks where upstream metadata is available, bounded download sizes, and traversal-safe archive extraction.
- GGUF model downloads require a valid SHA-256 from a pinned value or the model host. Redirects cannot downgrade an HTTPS download to HTTP.
- The Termux model server is forced to bind to 127.0.0.1, requires a generated API key, and refuses to start if the llama-server binary does not support API-key authentication.
- Jarvis can start only the installer-owned Termux script. It cannot execute an arbitrary preference-controlled path.
- Screen text, messages, notifications, and web pages are treated as untrusted content. They cannot grant permission or change agent rules.
- Every phone-control action and every screen-data transfer requires a visible approval. Coordinate taps, long presses, and submit-by-enter actions require an additional confirmation.
- Provider error bodies are not written to Logcat.
- GitHub Actions are pinned to commit SHAs. CI uses read-only repository permissions, checks the Gradle wrapper, builds native runtimes, and runs tests. CodeQL, dependency review, and Dependabot are configured.
- Release publication is manual, requires an explicit confirmation input, accepts only tags reachable from master, and signs APKs with Android's `apksigner` after Gradle has finished. The signing key is not exposed to Gradle scripts or plugins. APK building is separated from the job that receives release-write permission.

## Known limitations

- The upstream wake-word archive does not currently expose a machine-readable SHA-256 in the release metadata used by this project. Its HTTPS source and exact archive size are checked, but a cryptographic digest is not yet pinned.
- A compromised Android OS, rooted device, malicious accessibility service, compromised provider account, or malicious code already running with Jarvis's app UID can bypass some app-level protections.
- A user can still approve a harmful action after being shown its details. Approval prompts reduce risk; they cannot replace judgment.
- CodeQL, dependency review, and CI are configured but their first successful GitHub Actions runs have not yet been observed.
- The `production-release` GitHub environment should be configured in repository settings with required reviewers if an additional human approval is desired before publication.

## Reporting a vulnerability

Do not post API keys, passwords, personal data, or exploit details in a public issue. Use GitHub's private vulnerability reporting / Security Advisories for this repository if enabled. Include the affected commit, reproduction steps, impact, and any safe proof of concept.
