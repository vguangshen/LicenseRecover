# Development

## Source layout

- `src/main/java/` — Java production sources.
- `src/test/java/` — source-level smoke tests.
- `scripts/verify.ps1` — canonical compile/test/overlay/distribution pipeline used locally and in GitHub Actions.
- `scripts/verify.cmd` — Windows wrapper for the PowerShell verification script.
- `release-notes/` — version-specific stable release notes.
- Repository root — runtime/distribution compatibility files (`LicenseRecover*.jar`, launch scripts, legacy EXE, .NET helper and assets).

Java classes intentionally remain in the default package so compiled class names stay compatible with the existing runtime JARs and overlay classpath.

## Requirements

- JDK 8 or a newer JDK capable of compiling the current Java 8-compatible sources.
- Windows PowerShell 5.1+ for `scripts\verify.cmd`, or PowerShell 7+ for direct cross-platform execution.

The bundled runtime JARs remain compile/runtime compatibility inputs.

## Verify locally

Windows:

```bat
scripts\verify.cmd
```

PowerShell 7 / Linux / macOS:

```powershell
./scripts/verify.ps1
```

A successful run:

1. verifies the standard source layout;
2. validates `VERSION.txt` and matching release notes;
3. compiles all Java production sources;
4. compiles and runs `RefactorSmokeTest`;
5. builds the deterministic `LicenseRecoverOverlay.jar`;
6. assembles the user-facing runtime distribution;
7. verifies modern default and legacy fallback launchers;
8. creates `build/LicenseRecover-latest.zip`;
9. creates `build/SHA256SUMS.txt`.

Generated files stay under `build/` and are ignored by Git.

## PT RegStr selection

Explicit PT recovery selects `RegStr` in this order: an existing value from the target plan, a non-empty value read from the target registration component, then the application root directory name when it matches `PT[0-9]{4,6}`. The directory fallback is a single token, for example `PT0405`; it does not combine the XML `<System id>` inventory. A missing root `config.xml` or an empty native RegStr can therefore continue through this fallback.

Keep the selected value and its source visible in the recovery log: target plan, target-native read, or PT directory fallback. Continue through the target's constructor/path, backup, write-back, fresh-JVM verification and rollback. A fallback value is a recovery input; success is reported after verification. Synthetic coverage of this selection order and write-back lifecycle does not establish successful recovery or business startup for every real PT installation.

## Versioning

`VERSION.txt` is the stable version source of truth and contains a semantic version without the `v` prefix, for example:

```text
1.0.0
```

For every version there must be a matching file:

```text
release-notes/v1.0.0.md
```

When preparing a new stable version:

1. update `VERSION.txt`;
2. update `CHANGELOG.md`;
3. add `release-notes/vX.Y.Z.md`;
4. run `scripts\verify.cmd` or `scripts/verify.ps1`;
5. merge through a passing PR.

After the verified change reaches `main`, CI creates the stable `vX.Y.Z` Release only if it does not already exist. Existing stable releases are never overwritten by normal later `main` builds.

## Release channels

- `rolling-latest`: prerelease, automatically moved to the latest successfully verified `main` commit.
- `vX.Y.Z`: stable release, fixed to the verified commit that first published that version.

Both channels publish `LicenseRecover-latest.zip` and `SHA256SUMS.txt`.

## Distribution boundary

The user-facing ZIP intentionally contains runtime files and user documentation only. Engineering documents (`DEVELOPMENT.md`, `PHASE*.md`) remain in the repository and are not copied into the runtime ZIP.

Do not move or rename root-level runtime files casually. Existing users and launch scripts still rely on the current root distribution layout.
