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
