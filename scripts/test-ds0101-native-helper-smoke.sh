#!/usr/bin/env bash
# Test only the native DS0101 helper call wiring against synthetic stubs.
# No vendor binaries, license keys, license bypass, or production registration.
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/site/bin"
mcs -nologo -target:library -optimize+ \
  "-out:$tmp/LicenseRecover.NET.Legacy.dll" src/dotnet/LicenseRecover.LegacyHelper.cs
cat > "$tmp/Regedit.cs" <<'CS'
using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
namespace ITMC.Regedit {
  public sealed class RegeditInfo { public string ProName { get; set; } public string RegStr { get; set; } public DateTime EndDate { get; set; } }
  public sealed class RegeditMain {
    public string ProName { get; set; }
    public string UserID { get; set; }
    public RegeditMain(string product, string init) {
      if (product != "itmcIEC" || String.IsNullOrEmpty(init))
        throw new InvalidOperationException("wrong native initializer");
      byte[] md5 = MD5.Create().ComputeHash(Encoding.UTF8.GetBytes("itmcIECRegeditNew"));
      string hash = BitConverter.ToString(md5).Replace("-", "");
      byte[] key = Encoding.ASCII.GetBytes(hash.Substring(0, 8));
      byte[] encrypted = new byte[init.Length / 2];
      for (int i = 0; i < encrypted.Length; i++)
        encrypted[i] = Convert.ToByte(init.Substring(i * 2, 2), 16);
      using (DES des = DES.Create()) {
        des.Key = key; des.IV = key;
        des.Mode = CipherMode.CBC; des.Padding = PaddingMode.PKCS7;
        byte[] plain = des.CreateDecryptor().TransformFinalBlock(encrypted, 0, encrypted.Length);
        if (Encoding.UTF8.GetString(plain) != "itmcsoft")
          throw new InvalidOperationException("invalid native constructor DES initializer");
      }
    }
    public string NewRegistry() {
      if (ProName != "itmcIEC" || UserID != "itmc")
        throw new InvalidOperationException("missing original Regester.aspx property setup");
      return "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";
    }
    public bool DoRegistry(string seq, string code) {
      if (seq != NewRegistry() || code != "SYNTHETIC_TEST_CODE") return false;
      File.WriteAllText(Environment.GetEnvironmentVariable("DS0101_MOCK_STATE"), "registered");
      return true;
    }
    public bool CheckReInfo(ref RegeditInfo info) {
      return !File.Exists(Environment.GetEnvironmentVariable("DS0101_MOCK_STATE"));
    }
    public bool CheckLocalReg(ref RegeditInfo info) {
      bool registered = File.Exists(Environment.GetEnvironmentVariable("DS0101_MOCK_STATE"));
      if (registered) { info.ProName = "itmcIEC"; info.RegStr = "DS0101"; info.EndDate = new DateTime(2099,12,31); }
      return registered;
    }
  }
}
CS
cat > "$tmp/Web.cs" <<'CS'
using System;
using System.IO;
namespace ITMC.Web {
  public sealed class funpublic {
    public static bool CheckReg() {
      return !(File.Exists(Environment.GetEnvironmentVariable("DS0101_MOCK_STATE"))
        && Environment.GetEnvironmentVariable("DS0101_MOCK_WEB_OK") == "yes");
    }
  }
}
CS
cat > "$tmp/Driver.cs" <<'CS'
using System;
using System.Reflection;
public static class Driver {
  public static int Main(string[] args) {
    Assembly a = Assembly.LoadFrom(args[0]);
    Type program = a.GetType("LicenseRecoverNet.Program", true);
    MethodInfo parse = program.GetMethod("ParseArgs",
      BindingFlags.Static | BindingFlags.NonPublic);
    MethodInfo run = program.GetMethod("RunDirect",
      BindingFlags.Static | BindingFlags.Public);
    string[] forwarded = new string[args.Length - 1];
    Array.Copy(args, 1, forwarded, 0, forwarded.Length);
    object options = parse.Invoke(null, new object[] { forwarded });
    if (options == null) return 12;
    object result = run.Invoke(null, new object[] { options });
    return Convert.ToInt32(result);
  }
}
CS
mcs -nologo -target:library "-out:$tmp/site/bin/ITMC.Regedit.dll" "$tmp/Regedit.cs"
mcs -nologo -target:library "-out:$tmp/site/bin/ITMC.Web.dll" "$tmp/Web.cs"
mcs -nologo -target:exe "-out:$tmp/Driver.exe" "$tmp/Driver.cs"
export DS0101_MOCK_STATE="$tmp/site/mock-registered.txt"
export DS0101_MOCK_WEB_OK="yes"

# The helper's native request API must work against real .NET reflection.
mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-gencode "$tmp/site/bin" --product itmcIEC > "$tmp/request.log"
grep -q "RESULT: LEGACY_NATIVE_REQUEST_ONLY" "$tmp/request.log"
test ! -e "$DS0101_MOCK_STATE"

# Verify must not produce a false success before registration.
if mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
     legacy-verify "$tmp/site/bin" --product itmcIEC > "$tmp/before.log" 2>&1; then
  echo "FAIL: verify accepted a non-registered synthetic site" >&2
  exit 1
fi

# Direct local check must refuse before any registration.
if mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-local-probe "$tmp/site/bin" --product itmcIEC > "$tmp/local-before.log" 2>&1; then
  echo "FAIL: local probe accepted an unregistered synthetic site" >&2
  exit 1
fi

# Native registration rejects an incorrect code, preserving original state.
if mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-doreg "$tmp/site/bin" --product itmcIEC \
  --seq 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF \
  --code WRONG_TEST_CODE > "$tmp/rejected.log" 2>&1; then
  echo "FAIL: DoRegistry rejection was ignored" >&2
  exit 1
fi
test ! -e "$DS0101_MOCK_STATE"

# The fake site models native registration and separate web verification.
mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-doreg "$tmp/site/bin" --product itmcIEC \
  --seq 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF \
  --code SYNTHETIC_TEST_CODE > "$tmp/registered.log"
grep -q "RESULT: LEGACY_NATIVE_DOREG_ACCEPTED" "$tmp/registered.log"
test -e "$DS0101_MOCK_STATE"

# Direct native local check should now accept the registered synthetic site.
mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-local-probe "$tmp/site/bin" --product itmcIEC > "$tmp/local-after.log"
grep -q "RESULT: LEGACY_NATIVE_LOCAL_CHECK_OK" "$tmp/local-after.log"

# A fresh process must verify BOTH CheckReInfo and Web.CheckReg.
DS0101_MOCK_WEB_OK=no \
mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-verify "$tmp/site/bin" --product itmcIEC \
  > "$tmp/web-rejected.log" 2>&1 && {
  echo "FAIL: application Web.CheckReg rejection was ignored" >&2
  exit 1
}
mono "$tmp/Driver.exe" "$tmp/LicenseRecover.NET.Legacy.dll" \
  legacy-verify "$tmp/site/bin" --product itmcIEC \
  > "$tmp/verified.log"
grep -q "RESULT: LEGACY_NATIVE_CHECKREG_OK" "$tmp/verified.log"
echo "PASS: Synthetic DS0101 native request / DoRegistry / CheckReInfo / Web.CheckReg wiring."
