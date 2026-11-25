# Jarvis Master Plan

> Canonical project roadmap and decision log. Updated: 2026-11-25.
> Scope: Android-first Jarvis assistant, then desktop companion/workers. This file records goals and verified implementation status separately. A planned item is not an implemented feature.

## 1. Product goal

Build a capable, voice-first personal AI assistant called Jarvis that can work on the user's Android phone first and later extend to a PC. It should combine conversational AI, voice, controlled phone/app automation, local and cloud models, research, specialist agents, firmware/build engineering, data analysis and trading research.

The user remains in control. Jarvis may research, prepare, simulate, test and propose actions autonomously within granted permissions; it must not make consequential changes, spend money, publish releases, flash devices, or place real trades without explicit approval.

## 2. Non-negotiable operating and security rules

- Default-deny permissions. New agents and tools start disabled or read-only.
- Least privilege: grant each agent only the filesystem, network, repository and runtime access it needs.
- Human approval before repository writes, releases, deployments, account changes, sending messages, purchases, payments, deleting data, bootloader unlocks, partition writes, firmware flashing, or any live trade.
- Show the exact action, target, data to be sent, expected side effects and risk before approval. Approval must be scoped to that exact action and expire; no blanket consent.
- Separate analysis from execution. Agents may draft and simulate without receiving execution credentials.
- Treat model output, web pages, files, screen text, notifications, repositories and downloaded agent packages as untrusted input, never as permission grants or system instructions.
- Secrets must not be committed, logged, embedded in APKs, or exposed to build plugins. Use Android Keystore on device and an OS-backed secret store on desktop.
- Network calls use TLS/HTTPS; validate hosts, redirects, certificates, response sizes and downloaded artifact hashes. Local services bind to loopback unless the user explicitly configures otherwise.
- Sandbox third-party agents and build tools. Restrict CPU, memory, time, filesystem and network access; record provenance and versions.
- Maintain audit logs for agent activation, permission grants, approvals, tool calls, repository changes and trading simulations. Avoid logging credentials or private content.
- Provide a global stop/pause control, per-agent disable switch, revocation of credentials and safe recovery after crashes.
- Never promise complete immunity from hacking. Document residual risks and verify controls with tests and security review.

## 3. Current codebase: verified baseline

Repository: `zelenkovmaksim892-ux/jarvis`
Working branch: `jarvis-development`
Master branch is not the active development target.

Evidence checked on 2026-11-25:
- Android app source is Kotlin; README declares Android 8.0 / API 26 minimum.
- Voice stack includes offline wake-word detection, speech recognition, Kokoro on-device TTS and cloud voice providers with local fallback.
- AI provider support and local-model/Termux integration are present in the documented app.
- Phone automation uses Android Accessibility and includes user-visible approval controls.
- Task history/replay, memory, app tips and crash reporting have source files and tests.
- A security document describes encrypted credentials using AES-256-GCM with Android Keystore, disabled backups, HTTPS restrictions, model download checks and CI security controls. These claims still require independent code review and passing tests.
- Agent catalog exists with default policy `deny`; firmware, software, data, market and strategy agents are planned or prototype, not fully integrated autonomous agents.
- Trading engine contains public market-data adapters, local paper-account simulation, backtesting and risk/policy primitives. Its README explicitly says there is no live exchange order adapter and no live trading.
- Firmware build agent is a catalog design only. No claim is made that Android kernels/ROMs can currently be built on the phone itself.
- Android APK packaging supports arm64-v8a, armeabi-v7a and x86_64 in Gradle configuration. A successful build does not by itself prove installation or runtime compatibility on the user's handset.

## 4. Work completed during this development session

Changes committed to `jarvis-development` on 2026-11-25:
- Added a Robolectric-only ephemeral AES test key path; production Android devices continue to use Android Keystore.
- Made dangerous-action detection Unicode-aware for case-insensitive Russian text.
- Hardened model archive path validation so traversal components are rejected before the archive's top-level folder is stripped.
- Added a functional JSON dependency for JVM tests.

Latest known branch head when this plan was written: `1418b77452c0bfd7b20767c561cdc5d41e1b03eb`.

CI and CodeQL were triggered for the latest commits, but no successful run for this exact head had yet been confirmed at the time of writing. Do not mark these changes as fully validated until the workflows complete and failures are resolved.
- Extended irreversible-action detection and confirmation phrase handling to Russian labels and speech responses; added regression tests for Russian yes/no phrases and false positives. Commits: `0123bf4`, `6432605`, `8353116`, `41a02c2`, `0c4bc5c`, `e216576`, `1418b77`.
- Follow-up security review found that the model archive downloader pins SHA-256 for speech and Kokoro archives, but the wake-word archive currently pins only size. GGUF downloader refuses models without a trusted SHA-256, restricts HTTP to loopback, and rejects HTTPS downgrade redirects; redirect host allowlisting remains a review item. These findings are not yet resolved.

## 5. Product requirements and roadmap

Status key: **Implemented** = source/docs/tests show the feature exists; **Prototype** = partial implementation; **Planned** = not yet implemented or not yet verified end-to-end.

### A. Core assistant and voice
- [x] Android-first voice assistant foundation.
- [x] Wake phrase detection and offline speech components.
- [x] On-device Kokoro TTS and cloud voice fallback.
- [x] Multiple AI providers and local-model route documented.
- [ ] Russian-first UI, commands, speech recognition, prompts and help content; verify full Russian coverage rather than assuming Hindi localization covers it.
- [ ] Configurable Jarvis persona and natural conversation style, with user-editable settings.
- [ ] Voice selection architecture that can use a legally licensed voice or a user-provided recording/model. Do not copy a protected actor/character voice without rights.
- [ ] Reliable interruption, wake-word behavior, latency and battery benchmarks on the target phone.
- [ ] Optional desktop microphone/speaker integration.

### B. Interface and daily workflow
- [x] Existing home, model, settings and task UI; task history/replay source exists.
- [ ] Review and finish consistent Jarvis visual identity, icons, status indicators and accessible controls.
- [ ] Unified task board with queued/running/paused/approval-needed/completed/failed states.
- [ ] User-visible activity log: what the assistant did, why, which agent/provider it used and what data left the device.
- [ ] Easy pause, cancel, retry, undo where possible, and per-agent controls.
- [ ] Backup/export and restore for user settings and task metadata, excluding secrets by default.

### C. Agents and integrations
- [x] Agent catalog schema and default-deny policy exist.
- [ ] Implement an agent manager with install/enable/disable, permission scopes, version pinning, health checks and resource limits.
- [ ] Discover and recommend free/open-source agents and AI tools; verify license, maintenance, permissions, cost, hardware requirements and network behavior before listing.
- [ ] Support local agents and remote workers through a narrow, authenticated API.
- [ ] Each agent must declare capabilities, inputs/outputs, data handling, dependencies, license, network hosts and side effects.
- [ ] No agent may silently install another agent, change permissions or call a privileged tool.
- [ ] Add connectors only after explicit user authorization and show exactly which account scopes are requested.

### D. Firmware and Android build engineering
- [ ] Firmware Build Engineer: repository/device-tree/kernel source analysis, dependency checks, reproducible build setup, logs and build artifact verification.
- [ ] Provide isolated Linux build workers/containers for large Android ROM and kernel builds; Android phone acts as controller, not presumed build host.
- [ ] Build recipes must pin source revisions, toolchains and dependencies; produce checksums, provenance and build logs.
- [ ] Device compatibility database: exact model/codename, SoC, partition layout, bootloader state, vendor blobs, kernel and recovery requirements.
- [ ] Never flash, unlock bootloader, erase data or write partitions without a separate explicit confirmation for the exact device and artifact.
- [ ] First target research: user's Hyundai Accent TagAZ 2008 head unit (Android 8, MTK6765, MCU CW32F030, model string CMZXR62N-U1); investigate compatibility only. No firmware modification or flashing until exact board, firmware and recovery path are verified.
- [ ] Secondary target research: Redmi Note 4; identify exact variant/SoC/bootloader before selecting ROMs.
- [ ] Main app target: Redmi Note 10 Pro (M2101K6G); verify ABI, Android version, permissions, battery behavior and real-device performance.

### E. Research and data analysis
- [ ] Research agent with source citations, source freshness, cross-checking and clear uncertainty.
- [ ] Data Analyst agent for CSV, XLSX, SQL, statistics, charts and repeatable reports.
- [ ] Data import must be read-only by default; never upload user files to a third party without explicit approval.
- [ ] Add exportable reports and reproducible analysis artifacts.

### F. Markets, trading research and bots
- [x] Trading research package with public market-data adapters, backtesting and paper-account primitives exists.
- [x] Current trading package documentation says no exchange credentials, live order placement or withdrawals.
- [ ] Validate market-data sources, timestamps, rate limits, symbol mappings and missing/stale data handling.
- [ ] Add robust backtesting: fees, slippage, spread, funding, survivorship/look-ahead bias controls, walk-forward tests and out-of-sample validation.
- [ ] Add paper-trading persistence, portfolio/risk dashboards, drawdown limits and kill switch.
- [ ] Evaluate Freqtrade as an isolated optional worker; pin version and run dry-run first.
- [ ] Strategy agents may propose code and run tests but cannot access live credentials.
- [ ] Live trading remains disabled. Any future live-trading module must be separate, opt-in, use restricted API keys with withdrawals disabled, enforce hard limits and require explicit per-order approval. Never promise profit or imply backtest returns predict future performance.

### G. Desktop expansion
- [ ] Desktop companion for Windows/Linux, with macOS considered later.
- [ ] Local worker manager for builds, research, data analysis and local models.
- [ ] Secure phone-to-PC pairing with mutual authentication, explicit device approval, encrypted transport and revocation.
- [ ] Keep mobile and desktop task history/settings synchronized only with user consent.
- [ ] PC may run resource-intensive ROM builds and local AI models; phone remains the remote control and approval device.

### H. Quality, release and operations
- [ ] Restore green CI on the latest development head; investigate every failing test rather than suppressing it.
- [ ] Run CodeQL, dependency review, secret scanning and Gradle wrapper verification successfully.
- [ ] Pin a trusted SHA-256 for the wake-word model archive, or migrate to a release asset with published integrity metadata.
- [ ] Review and constrain GGUF redirect destinations to an explicit trusted-host policy; retain HTTPS-only redirects for remote downloads.
- [ ] Add tests for Russian dangerous-action detection, archive traversal variants, Keystore migration, permission gates and agent prompt-injection boundaries.
- [ ] Add instrumentation tests and manual test checklist on Redmi Note 10 Pro.
- [ ] Verify APK signatures, hashes, ABI splits, install/upgrade path and rollback.
- [ ] Keep release signing isolated; require human approval and protected GitHub environment for publication.
- [ ] Maintain changelog, architecture docs, threat model and recovery instructions.

## 6. Definition of done

A feature is not complete merely because its catalog entry, README paragraph or UI button exists. Mark it **Implemented** only when:
1. Source code is merged into the development branch.
2. Unit/integration tests cover normal, failure and abuse cases.
3. Relevant CI jobs pass on the exact commit.
4. Security and permission boundaries are reviewed.
5. Real-device or isolated-worker testing is completed where applicable.
6. User-facing documentation explains setup, limitations, costs and data flows.
7. Any external side effect remains behind explicit user approval.

## 7. Immediate execution order

1. Restore CI and CodeQL to green on the current development branch; resolve test failures without weakening production security.
2. Re-review the recent Keystore, dangerous-action and archive extraction changes; add focused regression tests.
3. Audit Android permissions, accessibility actions, network endpoints, secret storage, local server and agent trust boundaries.
4. Confirm a reproducible APK build and install/test it on Redmi Note 10 Pro before calling it a phone-ready release.
5. Turn the agent catalog into a permissioned manager, starting with read-only research and data-analysis agents.
6. Extend trading research and paper-trading validation; keep live orders disabled.
7. Design the isolated Linux firmware build worker and compatibility checks.
8. Build the desktop companion and secure phone-to-PC control path.

## 8. Decision and change log

- 2026-11-25: User confirmed continuing the Jarvis project and approved creating this master plan.
- 2026-11-25: Recorded the user's agreed direction: Android-first, later PC; controlled autonomous agents; firmware build tools; analytics and trading-bot research; strong security; explicit user approval for consequential actions.
- 2026-11-25: Recorded current repository status separately from planned features to prevent roadmap items being mistaken for shipped functionality.

## 9. Change management

Before adding a major feature, update this plan with its status, dependencies, risks and acceptance tests. Never silently remove a user requirement. If a requirement conflicts with safety, platform limits, licensing or technical feasibility, document the conflict and propose a safe alternative before changing scope.
