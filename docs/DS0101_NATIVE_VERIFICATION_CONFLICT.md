# DS0101: native offline registration accepted, but site verification disagrees

Analysis dated 2026-10-28. Evidence: owned, proprietary DS0101 assemblies (restored from FOAP), executed only inside a private GitHub Windows runner with disposable, synthetic XML. No original license or machine identifier is published.

## Confirmed by private real-DLL runtime tests

- Native `NewRegistry()` generated a request (`legacy-gencode` exit 0).
- Production `LegacyDotNetProtocol.generateAuthorizationCode` generated a locally self-consistent matching code.
- Original uppercase `ITMC.Regedit.RegeditMain.DoRegistry(request, code)` returned true and wrote `config.xml` and `Register.xml` in the disposable site.
- Original native `CheckLocalReg` returned true after writeback and returned false on the same fixture before writeback.
- After writeback, `CheckLocalReg` returned a non-null RegeditInfo with RegStr containing DS0101, ProName matching itmcIEC, and EndDate not expired; these are boolean/classification comparisons only, not plaintext values.
- Original uppercase `CheckReInfo` returned false. Forcing the site-copy `regType` to each of 1, 2 and 3 did not produce an accepted combined verifier.
- Original `ITMC.Web.funpublic.CheckReg` returned false both before and after registration in separate hosted test runs.
- No real IIS application activation or restart was performed. The root config and web.config are deliberately synthetic, not a realistic copy of the full production site.

## Static CIL clue, not yet a proven runtime root cause

The recovered uppercase `ITMC.Regedit.RegeditMain.CheckReInfo` CIL has an early credential guard and then mode-dependent short-circuit checks. In the `regType=1` branch, the compiled sequence at IL offsets 0x0102–0x0111 invokes CheckLocalReg, compares its bool result to zero, and on a *true* CheckLocalReg result immediately stores false as the return value; when CheckLocalReg is false it continues to CheckNet. This is the actual control-flow instruction interpretation, not an endorsement of the resulting semantics.

That inversion explains why a direct CheckLocalReg=true alone cannot be treated as CheckReInfo=true, and is consistent with the private Windows result. However, the full website code and static field lifetimes may impose additional conditions. No vendor method body or license check has been changed to force a result.

The recovered website `ITMC.Web.funpublic.CheckReg` also calls GetProVersion -> PubCode.RegisterVersion.GetRegisterVersionList and maintains static RegeditInfo state. The synthetic web.config and config.xml cannot demonstrate that this startup/version-map logic is equivalent to production.

## Next safe diagnostic boundary

Use disposable private Windows copies to differentiate (a) native RegInfo being populated and valid, (b) the exact upper CheckReInfo return semantics, and (c) original website front-door state and version map initialization. Do not claim a successful license until the website checks and fresh IIS restart both pass.

No proprietary DLL, key, request, machine identifier, connection string or decrypted registration value is included here.
