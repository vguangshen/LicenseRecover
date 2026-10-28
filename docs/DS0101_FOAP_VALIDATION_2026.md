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

## One-command site/archive analysis

To recover the three protected assemblies from an existing site folder
(or its original ZIP) into a separate directory without executing vendor
code, run:

```powershell
python scripts/ds0101_unpack_site.py DS0101.zip DS0101-analysis
python -m unittest discover -s scripts/tests -v
```

The first command produces three `*.unpacked.dll` inspection copies,
three per-assembly `*.report.json` files and a `manifest.json` summary.
A sample ZIP containing the original DS0101 site was verified locally to
restore exactly 7,127 methods. All three output DLL SHA-256 hashes were
identical to the independent single-assembly recovery run. The test
suite includes RC4/IL body verification and ZIP path/duplicate/output
safety guards. No live IIS or native license checks were executed.

## Explicit destructive-patch backup fix

Previously, `PatchSafety.prepareDotNetPatch()` only backed up
`itmcRegedit.dll`, even when a site contained an uppercase
`ITMC.Regedit.dll`, and it could refuse uppercase-only deployments.
The new safety preflight backs up every existing core candidate:
`ITMC.Web.dll`, `ITMC.Regedit.dll`, `itmcRegedit.dll`. Each copy is
size- and SHA-256-checked before a destructive operation can continue,
and its timestamp includes a unique suffix to avoid same-second
collisions. Added Java smoke cases for uppercase-only, dual-component,
and missing-component sites.

This backup fix does **not** enable the DS01xx one-click authorization
path. The explicit `DOTNET_LEGACY` rejection remains unchanged.

## Proven DS0101 hybrid registration calls (unpacked MethodDef IL)

The original site's `Regester.aspx` declares an ASP.NET code-behind
class `ITMC.Web.Regester`. The static CIL call-reference inspection of
the recovered `ITMC.Web.dll` shows:

| MethodDef | Code-behind entry | Referenced registration method |
|---|---|---|
| `0x06000C5D` | `Regester.Page_Load` | `ITMC.Regedit.RegeditMain.NewRegistry` |
| `0x06000C5E` | `Regester.Button1_Click` | `ITMC.Regedit.RegeditMain.DoRegistry` |
| `0x060006A0` | `funpublic.CheckReg` | `ITMC.Regedit.RegeditMain.CheckReInfo` **and** `itmcRegedit.RegeditMain.CheckReInfo` |

Therefore DS0101's website is a **dual-component/hybrid** case:
presence of an older offline code format does not prove the actual
ASP.NET registration page calls the lowercase DLL. The real page uses
the uppercase DLL, while `CheckReg` retains references to both
generations. Presence of call instructions does **not** prove which
branches execute at runtime, that a code is accepted, or that stored
credentials are valid.

The uploaded site's root config has `regType=3`; that value is a
registration mode selector, **not** evidence of a successful local
registration. Live `config.xml` authorization fields and
`Register.xml` contents are intentionally omitted from reports.

The command below captures the narrowly scoped, redacted static graph:

```powershell
python scripts/inspect_ds01xx_registration_chain.py DS0101-analysis/ITMC.Web.unpacked.dll --json DS0101-analysis/chain.json
```

From this branch onward, the ZIP/site batch analyzer includes the
same call evidence in its `manifest.json`, and the Java GUI legacy
preflight reports the actual file inventory and root `regType` without
logging `regName`, user IDs, hardware identifiers or registration codes.

### Safety gate retained

The site contains two `CheckReInfo` implementations. One-click success
cannot be enabled merely from a static call graph: the exact supplied
registered runtime must be confirmed in Windows / IIS with the
application's own legal registration path, then verified through
`funpublic.CheckReg` and a post-restart check. Until then
`DOTNET_LEGACY` remains fail-closed; no modified vendor DLL or
authorization-bypass binary is published.

## regType controls native check priority, not a single verification mechanism

Additional branch-level inspection of *both* recovered
`RegeditMain.CheckReInfo` MethodDefs confirms the order of three
registration checks in each case (read-only disassembly; no execution):

| Site-root `regType` | Uppercase `ITMC.Regedit.dll` | Lowercase `itmcRegedit.dll` |
|---|---|---|
| `1` | `CheckLocalReg` → `CheckNet` → `CheckHASPDog` | `CheckLocalReg` → `CheckDog` → `CheckNet` |
| `2` | `CheckHASPDog` → `CheckLocalReg` → `CheckNet` | `CheckDog` → `CheckLocalReg` → `CheckNet` |
| `3` | `CheckNet` → `CheckLocalReg` → `CheckHASPDog` | `CheckNet` → `CheckLocalReg` → `CheckDog` |

The method bodies have short-circuit branches: an earlier check can
terminate evaluation before subsequent methods are called. Therefore
`regType=3` in the submitted `config.xml` is **not** proof that an
online service is required for every execution, nor does it certify
offline license validity. The two DLLs even differ in their order for
`regType=1`. The runtime semantics, which check is called by the
website, and the eventual application login outcome require a real,
licensed Windows/IIS test.

The mode order can be reproduced from the recovered assemblies:

```powershell
python scripts/inspect_ds01xx_registration_modes.py DS0101-analysis/ITMC.Regedit.unpacked.dll DS0101-analysis/itmcRegedit.unpacked.dll --json DS0101-analysis/mode-priority.json
```

The batch archive analyzer includes this non-sensitive evidence in
`manifest.json` when it can be proven from the selected binaries.
Do not apply the order to unverified builds merely because their folder
name begins with `DS01`.

## Native registration side effects and rollback fix (2026-10-28)

Static CIL inspection of the actual uploaded `ITMC.Regedit.dll` revealed
an important transactional gap unrelated to the FOAP unpacker:

- `RegeditMain.DoRegistry` invokes `CheckServerID`. The latter reads,
  creates or updates **site-root `notRegFile.xml`**. This file is absent
  in the supplied DS0101 archive, so rollback must remember its original
  *absence* rather than only backing up existing files.
- `DoRegistry` writes **site-root `config.xml`** with registration state
  and writes messages to **`Register.xml`**.
- `RegeditMain.CheckLocalReg` itself can rewrite `config.xml`
  while updating local registration usage. A request/verification
  attempt must not be assumed side-effect-free.
- The uppercase native local-license writer uses
  `get_lastNOpsw` (the `*ITMC<ProName>OK*` family) and serializes
  `RegeditInfo`. The lowercase component contains a distinct local
  registration format. This is further evidence not to force either
  generation's write-back schema onto the other.

Prior code only added **existing** registration files to a memory
snapshot with `addIfFile`, even though its rollback logic attempted
to delete `null`/initially-absent entries. Thus newly generated
`notRegFile.xml`, `Register.xml`, or `config.xml` files could survive
a failed native operation.

The transaction now snapshots six fixed output paths at both the
website root and `bin`: `config.xml`, `Register.xml`, and
`notRegFile.xml`, including absence tombstones. Existing sidecar
configurations are also captured. Restore deletes created outputs and
restores modified originals byte-for-byte; it skips unchanged files,
returns an explicit success flag, and surfaces incomplete rollback
instead of silently indicating success. Disk backup copies are also
checked against the initial snapshot and verified with SHA-256 before
any write.

Modern .NET one-click `dryRun` also invokes native request generation,
so it now snapshots and restores the same set of files even when the
operation is a preview. A successful preview does not claim that the
native program was intrinsically read-only.

Added synthetic Java regression tests for successful restore, repeated
restore, absent-to-created file cleanup, and a deliberately unremovable
file whose rollback must return failure.

**Limitations:** This is an important data-safety fix, not an authorized
or unauthorized license bypass. DS01xx still fails closed before
automatic registration; a genuine Windows/IIS execution and restored
application verification are required before enabling it.

## Standalone, metadata-only DS0101 ABI probe

The known DS0101 DLLs expose these signatures (read from the original
protected CLR metadata):

- `ITMC.Regedit.RegeditMain(string,string)` constructor
- `NewRegistry(): string`
- `DoRegistry(string,string): bool`
- `CheckReInfo(ref ITMC.Regedit.RegeditInfo): bool`
- `ITMC.Web.funpublic.CheckReg(): bool` (static)

The local registration page uses the uppercase DLL, while `CheckReg`
references both registration generations. These are static facts, not
proof that a license is valid or that native registration succeeds.

For a test website, run the packaged native host:

```bat
LicenseRecover.NET\LicenseRecover.NET.AspNetHost.exe legacy-probe "C:\TestSites\DS0101\bin"
```

The `legacy-probe` command now dispatches **before** selecting a helper
or staging `LicenseRecover.NET.AspNetBridge.dll` in the target site.
It uses `Assembly.ReflectionOnlyLoadFrom` and reflection-only resolution
to inspect assembly metadata, without invoking vendor methods or static
constructors. It needs no registration helper and does not edit site files.

On successful signature verification it reports:

```text
RESULT: LEGACY_SIGNATURES_VERIFIED_ONLY
```

The CI harness compiles synthetic native DLLs with deliberately
throwing static constructors and checks: successful matching, refusal
of incorrect signatures/missing assemblies, no tripwire execution,
and byte-identical contents of the target site's files before/after.

**Limitations:** this checks binary interfaces only. It is not a
license generator, registration, Patch, authorization success result,
or IIS application startup test. `DOTNET_LEGACY` writeback is still
blocked until genuinely authorized registration can be tested with
the target runtime and verified after site restart.

The implementation remains on a draft PR, not a stable release.
