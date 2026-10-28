# DS0101 offline activation: native writeback prerequisites and cloud gate

Status: static MethodDef/IL analysis of the user-provided DS0101 sample; offline helper build and synthetic end-to-end call test completed. **No licensed DS0101 IIS runtime activation verified.**

## What is actually present

- `ITMC.Web.Regester.Button1_Click` constructs the uppercase `ITMC.Regedit.RegeditMain` and calls `DoRegistry(sequence, code)`.
- `NewRegistry()` generates a native request value, and `LegacyDotNetProtocol` already knows the original request/authorization field layout and cipher.
- `DoRegistry()` invokes `CheckServerID(code)` **before** decrypting and matching the request/authorization machine ID and request timestamp.
- `CheckServerID()` accesses site-root `notRegFile.xml`. If that file is absent, it calls `RegNOWebCheck(code)` and may create the local record from the result.
- `RegNOWebCheck()` calls the remote `itmcService.Service.CheckNOReg` method. The method's exception path returns the default false result. Thus a missing cloud service may cause `DoRegistry()` to reject otherwise correctly formatted offline codes.
- When `notRegFile.xml` exists, `CheckServerID()` also examines the local record using `DiGuiReadXml`; branch behaviour depends on the exact record contents. Do not assume arbitrary XML is a valid vendor-issued record.
- `CheckLocalReg` may update `config.xml`, so neither application verification nor request generation should be assumed free of side effects.

## New native helper and synthetic contract test

- `src/dotnet/LicenseRecover.LegacyHelper.cs` builds as `LicenseRecover.NET.Legacy.dll` and is selected only for `legacy-gencode`, `legacy-doreg`, and `legacy-verify` commands through the ASP.NET host.
- `legacy-gencode` calls target `NewRegistry` rather than fabricating a request string.
- `legacy-doreg` forwards the exact request and authorization code to target `DoRegistry`, rejecting a native `false` return.
- `legacy-verify` calls target `CheckReInfo` and independent web `funpublic.CheckReg`; both must return true before it prints a positive result.
- `scripts/test-ds0101-native-helper-smoke.sh` compiles **synthetic** C# DLLs, checks the constructor DES initializer and page setup, verifies that rejection propagates, then tests registration and fresh-process web verification.
- CI runs this synthetic test without storing or executing the user's commercial DLLs.

## Status of one-click and batch

**Not enabled.** `DOTNET_LEGACY` is still blocked in Java single-app and batch flows. The host/helper scaffolding is prepared but the cloud gate has not been resolved by the original application or verified with a legitimately held offline registration record. This PR must remain Draft.

Activation acceptance must include: (1) target-specific native request; (2) compatible offline authorization code; (3) accepted target DoRegistry; (4) native CheckReInfo and independent Web.CheckReg in a fresh process; (5) IIS restart verification; (6) no data loss or incorrect success on failure; (7) independent rollback for each batch target.

Never interpret a successful helper build, synthetic test, offline deobfuscation or metadata probe as evidence of actual DS0101 activation.

## Real Windows preflight (non-destructive)

The new `scripts/test-ds0101-isolated-request.ps1` copies a DS0101
website into a uniquely named temporary folder and uses the CI-built
ASP.NET host plus `LicenseRecover.NET.Legacy.dll` to check:

1. The deployed DLL ABI with `legacy-probe`.
2. The original native `NewRegistry()` request path with
   `legacy-gencode` (only when the ABI probe succeeds).
3. Whether the copy's `config.xml`, `Register.xml` or
   `notRegFile.xml` changed during request generation.

For example, from the checked-out repository on Windows, with the
verified CI bundle unpacked to `C:\LicenseRecover-test`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-ds0101-isolated-request.ps1 -SiteRoot "D:\DS0101" -ToolRoot "C:\LicenseRecover-test"
```

The report shows success flags and *filenames* of any changed files;
it suppresses machine identifiers, request codes, license material, and
original exception text. It deletes the isolated site copy by default.
Do not use `-KeepIsolatedCopy` unless the temporary copy can be
secured because it contains the website's original configuration.

This preflight does **not** call `DoRegistry`, `CheckReInfo` or
`funpublic.CheckReg`, and never modifies the selected source site.
It therefore cannot prove successful activation. A separate, explicitly
authorized Windows/IIS integration test is still necessary.


## Private Windows native offline-registration findings (2026-10-28)

A separate **private** GitHub Windows runner exercised an explicitly sanitized
DS0101 fixture (original managed assemblies, synthetic non-sensitive XML).
The original site archive was not committed to this public repository, and no
machine-bound request, generated authorization code, raw registration XML, or
vendor exception was published in the run summaries.

Observed, with protected CIL restored into test-only copies:

- The native ASP.NET host and reflection-only ABI probe succeed.
- Target `NewRegistry()` returns a valid request in the isolated Windows AppDomain.
- The production Java `LegacyDotNetProtocol` produces an internally matching
  DS0101 offline code for that request.
- Target `DoRegistry(sequence, code)` **returns true** (exit 0), modifies
  the copied `config.xml` and `Register.xml`, and writes `regType=1`.
- Target native `CheckLocalReg` returns **false before** registration and
  **true afterward**. This is meaningful native local-writeback evidence,
  not merely a syntactically valid generated code.
- Target `CheckReInfo` still returns **false** (the composite
  `legacy-verify` exits 5). Invoking `CheckLocalReg` first does not make
  the composite check succeed in a subsequent fresh hosted process.
- Independent mode-switch experiments all failed to verify; their nonzero
  host exit codes do **not** establish a supported production mode switch.
- No actual IIS restart, commercial login, or licensed production site
  acceptance has been verified.

**Consequences:** There is evidence that native offline registration is
accepted and locally persisted, but not that the complete original website
accepts it. The default DS01xx one-click and batch paths remain fail-closed.
Never report license activation success from `DoRegistry` alone.

The private diagnostic harness additionally compares the website's own
`funpublic.CheckReg()` before and after registration. Its independent result
must be checked before deciding whether any production recovery workflow is safe.
