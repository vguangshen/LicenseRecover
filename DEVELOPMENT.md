# Development

## Source layout

- `src/main/java/` — all Java production sources.
- `src/test/java/` — source-level smoke tests.
- `scripts/verify.ps1` — canonical compile/test/overlay/distribution pipeline used locally and in GitHub Actions.
- `scripts/verify.cmd` — Windows wrapper for the PowerShell verification script.
- Repository root — runtime compatibility files (`LicenseRecover*.jar`, launch scripts, legacy EXE, .NET helper and assets).

The project intentionally keeps Java classes in the default package for compatibility with the existing runtime JARs. Moving sources under `src/` does not change compiled class names.

## Requirements

- JDK 8 or a newer JDK capable of compiling the current Java 8-compatible sources.
- Windows PowerShell 5.1+ for `scripts\verify.cmd`, or PowerShell 7+ for direct cross-platform execution.

The bundled runtime JARs remain required as compile/runtime compatibility inputs.

## Verify locally

Windows:

```bat
scripts\verify.cmd
```

PowerShell 7 / Linux / macOS:

```powershell
./scripts/verify.ps1
```

A successful run performs the same major checks as CI:

1. Ensures Java source files are under `src/main/java` rather than the repository root.
2. Ensures the release ZIP is not tracked at repository root.
3. Compiles all production Java sources.
4. Compiles and runs `RefactorSmokeTest`.
5. Builds the deterministic `LicenseRecoverOverlay.jar`.
6. Assembles the runtime distribution without changing legacy runtime JARs.
7. Verifies modern default launchers and legacy fallback launchers.
8. Creates `build/LicenseRecover-latest.zip` and `build/SHA256SUMS.txt`.

Generated files are kept under `build/` and are ignored by Git.

## Release flow

Every successful push verification on `main` updates the `rolling-latest` GitHub prerelease. The release contains:

- `LicenseRecover-latest.zip`
- `SHA256SUMS.txt`

The `rolling-latest` tag intentionally moves to the newest successfully verified `main` commit. Stable versioned releases can be added later without changing this continuous delivery path.

## Runtime compatibility boundary

Do not move or rename root-level runtime files casually. Existing users and launch scripts still rely on the current root distribution layout. Development sources, build output and release assets are intentionally separated from that runtime compatibility surface.
