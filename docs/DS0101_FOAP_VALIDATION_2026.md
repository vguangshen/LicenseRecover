# DS0101 FOAP/VBPD static recovery baseline (2026-10-28)

## Scope

The DS0101 deployment archive supplied for local analysis contains a
`config.xml` with `SystemSoft/SoftVersionID=DS0101`, `regType=3` and
a local-only service endpoint. This evidence is **not** a valid license
and does not show successful application startup.

The following DLLs were examined **read-only**, without running the
ASP.NET application or any vendor executable:

| Assembly | Source SHA-256 | FOAP records | Verified body bytes |
|---|---|---:|---:|
| ITMC.Web.dll | `16b13b547893b160ef928fd6f06568770da1fdd2326a1dfdd4bc5c54e047562c` | 6455 | 1,114,961 |
| ITMC.Regedit.dll | `735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f` | 346 | 30,232 |
| itmcRegedit.dll | `6872470895de743974d5c565215d5b227678281fb9f863688d92b3f85856056d` | 326 | 18,893 |

All 7,127 records passed RC4/VBPD trailer, ECMA-335 CIL body-length
(including exception-handler sections) and rebuilt MethodDef.RVA
round-trip checks in an independent, dependency-free implementation.
This does not establish semantic correctness or Windows/IIS runtime
compatibility.

## Important method evidence

The protected original ITMC.Web.dll maps
`ITMC.Web.funpublic.CheckReg` (MethodDef `0x060006A0`) to the shared
protector placeholder RVA `0x292C00`.
The FOAP entry provides its encrypted body at RVA `0x16DA7C`.
The lowercase and uppercase registration DLLs **both** include
`CheckReInfo`, `CheckLocalReg` and `getLocalRegInfo` and are
different assemblies. Their coexistence does not justify choosing a
modern local-license protocol for the DS0101 target.

## Reproduction

Use the non-executing offline tool:

```powershell
python scripts/itmc_foap_unpack_stdlib.py ITMC.Web.dll ITMC.Web.unpacked.dll --json ITMC.Web.report.json
python scripts/itmc_foap_unpack_stdlib.py ITMC.Regedit.dll ITMC.Regedit.unpacked.dll --json ITMC.Regedit.report.json
python scripts/itmc_foap_unpack_stdlib.py itmcRegedit.dll itmcRegedit.unpacked.dll --json itmcRegedit.report.json
```

Reconstructed files are for decompilation and inspection in ILSpy or
dnSpyEx. They are not license-patched binaries and should not replace
production IIS assemblies.

## One-click status and remaining gap

The v1.2.58 one-click coordinator explicitly rejects
`DOTNET_LEGACY` in `LicenseRecoverModernGUIAutoRecovery.recover()`.
The new static restorer does not modify or relax this safety decision.

Before offering production DS01xx one-click functionality, trace the
vendor's legitimate offline-registration write-back, implement
version-specific native verification and rollback, and test on an
isolated Windows/.NET Framework/IIS environment. A successful IL
restore alone is not an authorization restore. No deployment binaries
or credential material are committed to the repository.
