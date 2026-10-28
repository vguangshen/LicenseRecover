#!/usr/bin/env bash
# Run offline metadata-only DS0101 probe against throw-on-execute synthetic DLLs.
# No commercial vendor assembly is used or published in CI.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v mcs >/dev/null
command -v mono >/dev/null
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/host" "$tmp/valid/bin" "$tmp/invalid/bin" "$tmp/missing/bin"
mcs -nologo -target:exe -r:System.Web.dll \
  "-out:$tmp/host/LicenseRecover.NET.AspNetHost.exe" \
  src/dotnet/LicenseRecover.AspNetHost.cs

cat > "$tmp/Regedit.cs" <<'CS'
using System;
using System.IO;
namespace ITMC.Regedit {
  public sealed class RegeditInfo {
    public string ProName { get; set; }
    public string RegStr { get; set; }
    public DateTime EndDate { get; set; }
  }
  public sealed class RegeditMain {
    static RegeditMain() {
      File.WriteAllText(Environment.GetEnvironmentVariable("DS0101_PROBE_TRIPWIRE"), "executed");
      throw new InvalidOperationException("vendor code must never execute in read-only probe");
    }
    public RegeditMain(string product, string serial) { throw new InvalidOperationException(); }
    public string NewRegistry() { throw new InvalidOperationException(); }
    public bool DoRegistry(string request, string code) { throw new InvalidOperationException(); }
    public bool CheckReInfo(ref RegeditInfo info) { throw new InvalidOperationException(); }
    public bool CheckLocalReg(ref RegeditInfo info) { throw new InvalidOperationException(); }
  }
}
CS
cat > "$tmp/RegeditInvalid.cs" <<'CS'
namespace ITMC.Regedit {
  public sealed class RegeditInfo {}
  public sealed class RegeditMain {
    public RegeditMain(string a, string b) {}
    public string NewRegistry() { return ""; }
    public bool DoRegistry(string a, string b) { return true; }
    public string CheckReInfo(ref RegeditInfo info) { return ""; } // wrong return type
  }
}
CS
cat > "$tmp/Web.cs" <<'CS'
using System;
using System.IO;
namespace ITMC.Web {
  public sealed class funpublic {
    static funpublic() {
      File.WriteAllText(Environment.GetEnvironmentVariable("DS0101_PROBE_TRIPWIRE"), "executed");
      throw new InvalidOperationException("web code must never execute in read-only probe");
    }
    public static bool CheckReg() { throw new InvalidOperationException(); }
  }
}
CS
mcs -nologo -target:library "-out:$tmp/valid/bin/ITMC.Regedit.dll" "$tmp/Regedit.cs"
mcs -nologo -target:library "-out:$tmp/valid/bin/ITMC.Web.dll" "$tmp/Web.cs"
mcs -nologo -target:library "-out:$tmp/invalid/bin/ITMC.Regedit.dll" "$tmp/RegeditInvalid.cs"
cp "$tmp/valid/bin/ITMC.Web.dll" "$tmp/invalid/bin/"
cp "$tmp/valid/bin/ITMC.Regedit.dll" "$tmp/missing/bin/"
export DS0101_PROBE_TRIPWIRE="$tmp/method-executed.txt"
(
  cd "$tmp/valid"
  find . -type f -print0 | sort -z | xargs -0 sha256sum
) > "$tmp/before.sha256"

# The probe must NOT require its ASP.NET bridge, a gencode helper, IIS
# registration, a license file, or an executable method body.
mono "$tmp/host/LicenseRecover.NET.AspNetHost.exe" legacy-probe \
     "$tmp/valid/bin" > "$tmp/valid.out" 2>&1 || {
  cat "$tmp/valid.out" >&2
  echo "Valid DS0101 metadata probe failed" >&2
  exit 1
}
grep -q 'RESULT: LEGACY_SIGNATURES_VERIFIED_ONLY' "$tmp/valid.out"
test ! -e "$DS0101_PROBE_TRIPWIRE"
test ! -e "$tmp/valid/bin/LicenseRecover.NET.AspNetBridge.dll"
(
  cd "$tmp/valid"
  find . -type f -print0 | sort -z | xargs -0 sha256sum
) > "$tmp/after.sha256"
cmp "$tmp/before.sha256" "$tmp/after.sha256"

if mono "$tmp/host/LicenseRecover.NET.AspNetHost.exe" legacy-probe \
       "$tmp/invalid/bin" > "$tmp/invalid.out" 2>&1; then
  echo "Invalid registration signature was incorrectly accepted" >&2
  exit 1
fi
grep -q 'DS01xx native signatures do not match' "$tmp/invalid.out"
test ! -e "$DS0101_PROBE_TRIPWIRE"

if mono "$tmp/host/LicenseRecover.NET.AspNetHost.exe" legacy-probe \
       "$tmp/missing/bin" > "$tmp/missing.out" 2>&1; then
  echo "Probe incorrectly accepted a missing Web DLL" >&2
  exit 1
fi
grep -q 'Missing ITMC.Regedit.dll or ITMC.Web.dll' "$tmp/missing.out"
test ! -e "$DS0101_PROBE_TRIPWIRE"

echo "PASS: DS0101 probe is read-only, accepts exact signatures, rejects invalid/missing DLLs."
