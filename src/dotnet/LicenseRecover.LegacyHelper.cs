using System;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;

// Dedicated legacy DS0101 native bridge. It uses the target's own registration
// methods for request generation, submission and independent verification.
// It must run inside the target ASP.NET hosted AppDomain via AspNetHost.
// Never edit vendor DLLs or bypass CheckReInfo / funpublic.CheckReg.
namespace LicenseRecoverNet
{
    public static class Program
    {
        public sealed class Options
        {
            public string Command, RuntimeDir, Product, Version, Sequence, Code;
        }

        private static Options ParseArgs(string[] args)
        {
            if (args == null || args.Length < 2) return null;
            Options o = new Options();
            o.Command = args[0];
            o.RuntimeDir = Path.GetFullPath(args[1]);
            o.Product = null;
            o.Version = "DS0101"; // compatibility for existing DS0101 test calls
            for (int i = 2; i + 1 < args.Length; i += 2)
            {
                if (args[i] == "--product") o.Product = args[i + 1];
                else if (args[i] == "--version") o.Version = args[i + 1];
                else if (args[i] == "--seq") o.Sequence = args[i + 1];
                else if (args[i] == "--code") o.Code = args[i + 1];
                else return null;
            }
            if (o.Product != "itmcIEC") return null;
            if (String.IsNullOrEmpty(o.Version) || o.Version.Length != 6 ||
                !o.Version.StartsWith("DS01", StringComparison.OrdinalIgnoreCase) ||
                !Char.IsDigit(o.Version[4]) || !Char.IsDigit(o.Version[5])) return null;
            if (o.Command != "legacy-gencode" && o.Command != "legacy-doreg"
                && o.Command != "legacy-verify" && o.Command != "legacy-local-probe"
                && o.Command != "legacy-web-probe") return null;
            if (o.Command == "legacy-doreg" &&
                (String.IsNullOrEmpty(o.Sequence) || String.IsNullOrEmpty(o.Code)))
                return null;
            return o;
        }

        private static string Encrypt(string text, string password)
        {
            // Decompiled target ItmcBtoB.DBUtility.DESEncrypt:
            // first eight ASCII characters of the uppercase MD5 hex are DES
            // Key and IV, CBC mode with PKCS7 (PKCS5) padding.
            byte[] digest = MD5.Create().ComputeHash(Encoding.UTF8.GetBytes(password));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < digest.Length; i++) hex.Append(digest[i].ToString("X2"));
            byte[] key = Encoding.ASCII.GetBytes(hex.ToString().Substring(0, 8));
            using (DES des = DES.Create())
            {
                des.Mode = CipherMode.CBC;
                des.Padding = PaddingMode.PKCS7;
                des.Key = key;
                des.IV = key;
                using (ICryptoTransform transform = des.CreateEncryptor())
                {
                    byte[] data = transform.TransformFinalBlock(Encoding.UTF8.GetBytes(text), 0,
                        Encoding.UTF8.GetByteCount(text));
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < data.Length; i++) result.Append(data[i].ToString("X2"));
                    return result.ToString();
                }
            }
        }

        private static object NewNativeMain(Options options, out Type regInfo)
        {
            string dll = Path.Combine(options.RuntimeDir, "ITMC.Regedit.dll");
            if (!File.Exists(dll)) throw new FileNotFoundException("Legacy uppercase DLL missing", dll);
            Assembly assembly = Assembly.LoadFrom(dll);
            Type main = assembly.GetType("ITMC.Regedit.RegeditMain", true);
            regInfo = assembly.GetType("ITMC.Regedit.RegeditInfo", true);
            // Match the exact Regester.Button1_Click constructor path
            // from the protected original ITMC.Web.dll.
            object instance = Activator.CreateInstance(main, new object[] {
                options.Product, Encrypt("itmcsoft", options.Product + "RegeditNew")
            });
            PropertyInfo proName = main.GetProperty("ProName");
            PropertyInfo userID = main.GetProperty("UserID");
            if (proName == null || userID == null)
                throw new MissingMemberException("Target RegeditMain lacks ProName/UserID");
            proName.SetValue(instance, options.Product, null);
            userID.SetValue(instance, "itmc", null);
            return instance;
        }

        private static object Invoke(object target, string name, object[] args, Type[] signatures)
        {
            MethodInfo method = target.GetType().GetMethod(name,
                BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance,
                null, signatures, null);
            if (method == null) throw new MissingMethodException(target.GetType().FullName, name);
            return method.Invoke(target, args);
        }

        private static bool CheckTarget(object native, Type infoType)
        {
            object info = Activator.CreateInstance(infoType);
            object[] args = new object[] { info };
            object value = Invoke(native, "CheckReInfo", args,
                new Type[] { infoType.MakeByRefType() });
            return value is bool && (bool)value;
        }

        private static bool CheckWebFrontDoor(string runtimeDir)
        {
            Assembly web = Assembly.LoadFrom(Path.Combine(runtimeDir, "ITMC.Web.dll"));
            Type funpublic = web.GetType("ITMC.Web.funpublic", true);
            MethodInfo check = funpublic.GetMethod("CheckReg",
                BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static,
                null, Type.EmptyTypes, null);
            if (check == null || check.ReturnType != typeof(bool))
                throw new MissingMethodException("ITMC.Web.funpublic.CheckReg():bool");
            return (bool)check.Invoke(null, null);
        }

        // Sanitized diagnostic flags only. Never print original RegStr,
        // machine IDs, register codes, dates, exception messages or JSON.
        private static string LocalStateFlag(object info, string property, string expected,
                                             bool contains)
        {
            if (info == null) return "missing";
            try
            {
                PropertyInfo p = info.GetType().GetProperty(property,
                    BindingFlags.Public | BindingFlags.Instance);
                if (p == null || !p.CanRead) return "unknown";
                object value = p.GetValue(info, null);
                if (!(value is string)) return "unknown";
                string raw = (string)value;
                if (string.IsNullOrEmpty(raw)) return "empty";
                bool matches = contains
                    ? raw.IndexOf(expected, StringComparison.OrdinalIgnoreCase) >= 0
                    : string.Equals(raw, expected, StringComparison.OrdinalIgnoreCase);
                return matches ? "match" : "mismatch";
            }
            catch { return "unknown"; }
        }

        private static string LocalExpiryFlag(object info)
        {
            if (info == null) return "missing";
            try
            {
                PropertyInfo p = info.GetType().GetProperty("EndDate",
                    BindingFlags.Public | BindingFlags.Instance);
                if (p == null || !p.CanRead) return "unknown";
                object value = p.GetValue(info, null);
                if (!(value is DateTime)) return "unknown";
                return ((DateTime)value).Date >= DateTime.Today ? "future-or-today" : "expired";
            }
            catch { return "unknown"; }
        }

        private static void PrintSanitizedLocalState(object info, string version, string product)
        {
            // These are classification flags, not the actual license fields.
            Console.WriteLine("[LEGACY_LOCAL_STATE] hasRegInfo=" + (info != null ? "true" : "false"));
            Console.WriteLine("[LEGACY_LOCAL_STATE] regStrContainsSelectedVersion="
                + LocalStateFlag(info, "RegStr", version, true));
            if (String.Equals(version, "DS0101", StringComparison.OrdinalIgnoreCase))
                Console.WriteLine("[LEGACY_LOCAL_STATE] regStrContainsDS0101="
                    + LocalStateFlag(info, "RegStr", "DS0101", true));
            Console.WriteLine("[LEGACY_LOCAL_STATE] proNameMatches="
                + LocalStateFlag(info, "ProName", product, false));
            Console.WriteLine("[LEGACY_LOCAL_STATE] expiry=" + LocalExpiryFlag(info));
        }

        // Check the original local registration record before interpreting
        // the outer website error flags. The two APIs use different Boolean
        // conventions: CheckLocalReg true means local state exists, whereas
        // the site redirects to registration on CheckReg true.
        private static bool HasMatchingLocalRecord(object native, Type infoType, string version, string product)
        {
            MethodInfo local = native.GetType().GetMethod("CheckLocalReg",
                BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance,
                null, new Type[] { infoType.MakeByRefType() }, null);
            if (local == null || local.ReturnType != typeof(bool))
                throw new MissingMethodException("CheckLocalReg(ref RegeditInfo)");
            object[] args = new object[] { Activator.CreateInstance(infoType) };
            if (!(bool)local.Invoke(native, args)) return false;
            object info = args[0];
            return LocalStateFlag(info, "ProName", product, false) == "match"
                && LocalStateFlag(info, "RegStr", version, true) == "match"
                && LocalExpiryFlag(info) == "future-or-today";
        }

        public static int RunDirect(Options options)
        {
            if (options == null || options.Product != "itmcIEC")
                throw new ArgumentException("Invalid DS0101 legacy options");
            Type regInfoType;
            object native = NewNativeMain(options, out regInfoType);
            if (options.Command == "legacy-gencode")
            {
                string request = Invoke(native, "NewRegistry", new object[0], Type.EmptyTypes) as string;
                if (String.IsNullOrEmpty(request) || request.Length < 32)
                    throw new InvalidOperationException("Target native NewRegistry returned no request");
                // The Java coordinator verifies/decrypts the native request
                // and generates a machine-bound code locally using its
                // previously tested LegacyDotNetProtocol implementation.
                Console.WriteLine("注册申请号: " + request);
                Console.WriteLine("RESULT: LEGACY_NATIVE_REQUEST_ONLY");
                return 0;
            }
            if (options.Command == "legacy-doreg")
            {
                bool ok = (bool)Invoke(native, "DoRegistry",
                    new object[] { options.Sequence, options.Code },
                    new Type[] { typeof(string), typeof(string) });
                if (!ok)
                {
                    Console.Error.WriteLine("[LEGACY_DOREG] Native DoRegistry rejected offline code.");
                    return 4;
                }
                Console.WriteLine("RESULT: LEGACY_NATIVE_DOREG_ACCEPTED");
                return 0;
            }
            if (options.Command == "legacy-local-probe")
            {
                // Isolated diagnostic only: call the vendor's native local
                // verification path without changing its return value.
                // A separate temporary site copy is required because the
                // original CheckLocalReg can rewrite config.xml.
                MethodInfo selected = null;
                object[] inputs = null;
                foreach (MethodInfo candidate in native.GetType().GetMethods(
                    BindingFlags.Public | BindingFlags.NonPublic
                    | BindingFlags.Instance | BindingFlags.Static))
                {
                    if (candidate.Name != "CheckLocalReg" ||
                        candidate.ReturnType != typeof(bool)) continue;
                    ParameterInfo[] p = candidate.GetParameters();
                    object[] values = null;
                    if (p.Length == 0) values = new object[0];
                    else if (p.Length == 1 &&
                        (p[0].ParameterType == regInfoType.MakeByRefType()
                         || p[0].ParameterType == regInfoType))
                        values = new object[] { Activator.CreateInstance(regInfoType) };
                    if (values != null && selected == null)
                    {
                        selected = candidate;
                        inputs = values;
                    }
                }
                if (selected == null)
                {
                    Console.WriteLine("RESULT: LEGACY_LOCAL_SIGNATURE_UNSUPPORTED");
                    return 8;
                }
                object localResult = selected.Invoke(selected.IsStatic ? null : native, inputs);
                PrintSanitizedLocalState(inputs != null && inputs.Length > 0 ? inputs[0] : null,
                    options.Version, options.Product);
                if (localResult is bool && (bool)localResult)
                {
                    Console.WriteLine("RESULT: LEGACY_NATIVE_LOCAL_CHECK_OK");
                    return 0;
                }
                Console.WriteLine("RESULT: LEGACY_NATIVE_LOCAL_CHECK_REJECTED");
                return 7;
            }
            if (options.Command == "legacy-web-probe")
            {
                // Separate website front-door result, only from an isolated copy.
                // This may run vendor code and should never be used on live IIS.
                bool rawWebCheck = CheckWebFrontDoor(options.RuntimeDir);
                Console.WriteLine("[LEGACY_WEB_RAW] returned=" + (rawWebCheck ? "true" : "false"));
                if (!rawWebCheck)
                {
                    Console.WriteLine("RESULT: LEGACY_WEB_CHECK_REJECTED");
                    return 9;
                }
                Console.WriteLine("RESULT: LEGACY_WEB_CHECK_ACCEPTED");
                return 0;
            }
            if (options.Command == "legacy-verify")
            {
                if (!HasMatchingLocalRecord(native, regInfoType, options.Version, options.Product))
                {
                    Console.Error.WriteLine("[LEGACY_VERIFY] Original local registration record is invalid.");
                    return 5;
                }
                bool rawCheckReInfo = CheckTarget(native, regInfoType);
                Console.WriteLine("[LEGACY_CHECKREINFO_RAW] returned=" + (rawCheckReInfo ? "true" : "false"));
                if (rawCheckReInfo)
                {
                    Console.Error.WriteLine("[LEGACY_VERIFY] Native CheckReInfo requests registration.");
                    return 5;
                }
                // Cross-check the actual Web front door independently from
                // the registration helper. A match in CheckReInfo alone
                // is NOT sufficient to report the application activated.
                if (CheckWebFrontDoor(options.RuntimeDir))
                {
                    Console.Error.WriteLine("[LEGACY_VERIFY] Target Web.CheckReg requests registration.");
                    return 6;
                }
                Console.WriteLine("RESULT: LEGACY_NATIVE_CHECKREG_OK");
                return 0;
            }
            throw new ArgumentException("Unsupported legacy command");
        }
    }
}
