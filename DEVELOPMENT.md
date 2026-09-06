# Development

## Source layout

- `src/main/java/` — all Java production sources.
- `src/test/java/` — source-level smoke tests.
- `scripts/verify.ps1` — canonical compile/test/overlay/distribution pipeline used locally and in GitHub Actions.
- `scripts/verify.cmd` — Windows wrapper for the PowerShell verification script.
- Repository root — runtime/distribution compatibility files (`LicenseRecover*.jar`, launch scripts, legacy EXE, .NET helper and assets).

The project intentionally keeps Java classes in the default package for compatibility with the existing runtime JARs. Phase 3 changes source locations only; it does not introduce Java package names.

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
2. Compiles all production Java sources.
3. Compiles and runs `RefactorSmokeTest`.
4. Builds the deterministic `LicenseRecoverOverlay.jar`.
5. Assembles the runtime test distribution without changing legacy runtime JARs.
6. Verifies modern default launchers and legacy fallback launchers.
7. Creates `build/LicenseRecover-test.zip`.

Generated files are kept under `build/` and are ignored by Git.

## Runtime compatibility boundary

Do not move or rename root-level runtime files casually. Existing users and launch scripts still rely on the current root distribution layout. Source layout and runtime layout are intentionally separate until a future packaging phase provides a migration path.
