# Private Windows DS0101 testing

This workflow is a non-destructive native .NET diagnostic, not license activation.

## Keep the original site private

Never commit the original commercial DLLs, customer configuration, registration data, or the private test ZIP into the public LicenseRecover repository or a public Actions artifact. The package contains proprietary binaries even though its XML is synthetic.

To make the stripped test archive from the licensed site ZIP:

```powershell
python scripts/make-ds0101-private-fixture.py D:\DS0101.zip D:\DS0101-private-Windows-fixture.zip
```

The fixture keeps the original bin DLL/EXE files but replaces config.xml, Register.xml, and web.config with non-sensitive test placeholders, and omits logs, databases, real licence values, and notRegFile.xml. It is not an operable production website.

## Call the reusable workflow from a PRIVATE repository

1. Create a separate GitHub repository with Private visibility.
2. Upload the stripped DS0101-private-Windows-fixture.zip to its root (not to LicenseRecover).
3. Add the following file as .github/workflows/ds0101.yml in that private repository:

```yaml
name: DS0101 private Windows diagnostics
on:
  workflow_dispatch:
permissions:
  contents: read
jobs:
  private-diagnostics:
    uses: vguangshen/LicenseRecover/.github/workflows/ds0101-private-windows.yml@eda1aac4a4868d046455a09a22af2c8ff6dea5db
    with:
      archive_path: DS0101-private-Windows-fixture.zip
      restore_cil: true
```

4. In the private repository, open Actions and manually Run workflow.

The example pins the reusable workflow to an immutable commit. Its checkout of LicenseRecover diagnostic source is independently pinned to an earlier tested commit as well; do not replace either with an unreviewed moving branch.

## What Windows Runner actually does

GitHub-hosted windows-latest in the PRIVATE caller repository checks out the private sample, checks out public diagnostic source separately, extracts only in temporary space, optionally restores original protected CIL, compiles the ASP.NET host and native helper, and invokes the isolated read-only request diagnostic. The temporary directory is deleted in a finally block; the workflow has no artifact upload step.

The JSON results include signatureProbePassed, nativeRequestGenerated, copiedSiteFilesChanged and actualLicenseVerified. The last is always false in this preflight, because DoRegistry, CheckReInfo and the website CheckReg are deliberately not invoked. Request codes, machine identifiers and licence values are not part of the JSON report.

The private fixture uses replacement XML. A native method failing there could reflect missing settings or dependencies; it is not enough to conclude the product cannot register. Public CI with synthetic DLLs has already verified the hosted chain, but cannot demonstrate real DS0101 activation.

## Handoff limitation

ChatGPT conversation uploads are not automatically available inside GitHub-hosted Actions. The private fixture must be placed in the private calling repository or another appropriately authenticated private source. A public GitHub Actions workflow is not an acceptable destination for proprietary DLLs.
