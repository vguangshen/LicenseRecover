# DS01xx .NET old-protocol family detection (experimental)

## Motivation

DS0101 RC3 worked on the analyzed original binaries, but DS0107 and other
DS01xx deployments were incorrectly blocked by a DS0101-specific hash gate.
This change separates *fast family identification* from *actual ABI/runtime
compatibility* and retains the existing shared one-click and batch executor.

## Detection and fail-closed checks

1. Read SoftVersionID from the selected site's own config.xml. Only DS01xx
   strings are old-protocol candidates, not modern YX/GM versions.
2. Resolve ProName from the site's ITMC.Web.dll strings; require itmcIEC and
   presence of the upper native ITMC.Regedit.dll and ITMC.Web.dll. Folder
   name, a DS01xx version alone, or copied DLL filenames are insufficient.
3. Before modifying any target file, use the dedicated Windows host's
   reflection-only `legacy-probe` to check the original RegeditMain
   constructor, NewRegistry/DoRegistry/CheckReInfo/CheckLocalReg signatures,
   RegeditInfo fields, and website funpublic.CheckReg method.
4. Only if that ABI probe passes, share the existing .NET transaction,
   ephemeral network isolation, native request generator and original
   DoRegistry. Generate the code using the selected SoftVersionID (not
   hard-coded DS0101); verify native local RegInfo against the same version.
5. Require original CheckLocalReg and the website's error-flag checks to
   agree, including the final recheck after config changes. Any failure
   restores snapshots and reports failure; do not treat DoRegistry=true
   alone as activation.

## Scope and outstanding validation

- Exact DS0101 original binaries were previously validated on a disposable
  private Windows runner and by the user's local RC3 test.
- Synthetic DS0107 tests cover site identification, DLL/version failure
  boundaries, and native registration verification for a different version.
- **Real DS0107 vendor DLL behavior, website initialization, and IIS recycle
  persistence have not been validated.** Native signatures may match while
  semantics differ. Do not claim successful production DS0107 activation
  based on mock DLL tests.
- Batch and one-click share the same eligibility and native validation code;
  unsupported/ambiguous deployments remain diagnostic-only.

No original vendor binaries, machine IDs, authorization values, or private
website configuration are published here.
