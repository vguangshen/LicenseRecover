# Phase 3 — Project source layout and unified verification

## Scope

Phase 3 standardizes the development layout without changing runtime behavior, authorization/protocol logic, patch algorithms, or the root-level distribution contract.

## Source layout

All Java production sources move from the repository root to:

`src/main/java/`

The refactor smoke test moves from `tests/` to:

`src/test/java/`

Java package declarations are intentionally unchanged. The existing default package is retained so compiled class names remain compatible with the legacy runtime JARs and overlay classpath.

## Unified verification

`scripts/verify.ps1` is the canonical verification/build pipeline for both local development and GitHub Actions. It performs Java compilation, smoke tests, deterministic overlay generation, distribution assembly, launcher checks and ZIP creation.

Windows developers can run `scripts\verify.cmd`; CI invokes the same PowerShell script directly.

## CI simplification

The GitHub Actions workflow now focuses on environment orchestration:

1. checkout;
2. Java 8 setup;
3. run `scripts/verify.ps1`;
4. upload the verified distribution;
5. synchronize a changed deterministic overlay on any `refactor/**` branch.

This removes duplicated build logic between CI and local development.

## Compatibility boundary

Root-level runtime files remain in place. In particular, Phase 3 does not move or rewrite the legacy JARs, EXE, .NET helper, launch BAT files or runtime assets. Those files remain the compatibility surface for existing deployments.
