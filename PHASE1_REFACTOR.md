# Phase 1 refactor

This branch introduces behavior-preserving engineering foundations before UI changes:

- `AppInfo` + `AppDetector`: shared Java/.NET application detection model.
- `BatchTarget`: typed batch target model replacing positional `Object[]` usage in the next wiring step.
- `OperationResult` + `ProcessRunner`: shared process result/timeout foundation.
- `SafetyBackup`: mandatory backup primitive for destructive patch operations; callers must abort when backup fails.
- `.github/workflows/verify-source.yml`: Java 8 source compilation gate.

The protocol/authorization algorithms are intentionally unchanged in this phase.
