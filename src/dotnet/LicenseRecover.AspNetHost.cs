using System;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Hosting;

internal static class LicenseRecoverAspNetHost
{
    private const string BridgeFileName = "LicenseRecover.NET.AspNetBridge.dll";
    private const string BridgeTypeName = "LicenseRecoverAspNetBridgeRunner";

    internal static string WithTrailingSeparator(string path)
    {
        string full = Path.GetFullPath(path);
        if (!full.EndsWith(Path.DirectorySeparatorChar.ToString(), StringComparison.Ordinal))
            full += Path.DirectorySeparatorChar;
        return full;
    }

    internal static string ResolveAppRoot(string runtimeDir)
    {
        string full = Path.GetFullPath(runtimeDir);
        string leaf = new DirectoryInfo(full).Name;
        if (string.Equals(leaf, "bin", StringComparison.OrdinalIgnoreCase))
        {
            DirectoryInfo parent = Directory.GetParent(full);
            if (parent != null) return parent.FullName;
        }
        return full;
    }

    private static string SelectHelper(string toolDir, string runtimeDir, string command, out string chain)
    {
        if (string.Equals(command, "legacy-gencode", StringComparison.OrdinalIgnoreCase)
            || string.Equals(command, "legacy-doreg", StringComparison.OrdinalIgnoreCase)
            || string.Equals(command, "legacy-verify", StringComparison.OrdinalIgnoreCase)
            || string.Equals(command, "legacy-local-probe", StringComparison.OrdinalIgnoreCase)
            || string.Equals(command, "legacy-web-probe", StringComparison.OrdinalIgnoreCase))
        {
            if (!File.Exists(Path.Combine(runtimeDir, "ITMC.Regedit.dll")))
                throw new FileNotFoundException("DS0101 uppercase registration DLL is required");
            chain = "DS0101-native-offline";
            string legacy = Path.Combine(toolDir, "LicenseRecover.NET.Legacy.dll");
            if (!File.Exists(legacy)) throw new FileNotFoundException("DS0101 legacy helper is missing", legacy);
            return legacy;
        }
        string lower = Path.Combine(runtimeDir, "itmcRegedit.dll");
        string upper = Path.Combine(runtimeDir, "ITMC.Regedit.dll");
        string helper;

        if (File.Exists(upper))
        {
            chain = "uppercase-ITMC.Regedit";
            helper = Path.Combine(toolDir, "LicenseRecover.NET.Modern.exe");
        }
        else if (File.Exists(lower))
        {
            chain = "lowercase-itmcRegedit";
            helper = Path.Combine(toolDir, "LicenseRecover.NET.exe");
        }
        else
        {
            throw new FileNotFoundException(
                "Target bin contains neither exact itmcRegedit.dll nor ITMC.Regedit.dll.");
        }

        if (!File.Exists(helper))
            throw new FileNotFoundException("Selected LicenseRecover helper is missing: " + helper);
        return helper;
    }

    private static string Sha256(string path)
    {
        using (SHA256 sha = SHA256.Create())
        using (FileStream stream = File.OpenRead(path))
        {
            byte[] hash = sha.ComputeHash(stream);
            StringBuilder sb = new StringBuilder(hash.Length * 2);
            for (int i = 0; i < hash.Length; i++)
                sb.Append(hash[i].ToString("x2"));
            return sb.ToString();
        }
    }

    private static bool EnsureBridgeInTargetBin(string source, string target)
    {
        if (File.Exists(target))
        {
            string sourceHash = Sha256(source);
            string targetHash = Sha256(target);
            if (!string.Equals(sourceHash, targetHash, StringComparison.OrdinalIgnoreCase))
                throw new IOException(
                    "Target bin already contains a different " + BridgeFileName
                    + "; refusing to overwrite it: " + target);

            Console.WriteLine("[ASPNET_HOST] bridge already present with matching hash=" + target);
            return false;
        }

        File.Copy(source, target, false);
        Console.WriteLine("[ASPNET_HOST] staged bridge=" + target);
        return true;
    }

    private static void DeleteBridgeWithRetry(string path)
    {
        for (int i = 0; i < 20; i++)
        {
            try
            {
                if (!File.Exists(path)) return;
                File.Delete(path);
                if (!File.Exists(path)) return;
            }
            catch
            {
            }
            Thread.Sleep(100);
        }

        if (File.Exists(path))
            Console.Error.WriteLine("[ASPNET_HOST] warning: temporary bridge cleanup failed: " + path);
    }

    private static IRegisteredObject CreateHostedBridge(
        ApplicationManager manager,
        string appRoot,
        Type bridgeType,
        out string appId)
    {
        // Ask ASP.NET itself to create the application AppDomain. This internal
        // compatibility entry point is used by non-IIS hosting scenarios and
        // sets DontCallAppInitialize, so it establishes the real HostingEnvironment
        // / virtual-to-physical mapping without running the target application's
        // startup code.
        MethodInfo create = typeof(ApplicationManager).GetMethod(
            "CreateObjectWithDefaultAppHostAndAppId",
            BindingFlags.Instance | BindingFlags.NonPublic,
            null,
            new Type[] {
                typeof(string), typeof(string), typeof(Type),
                typeof(string).MakeByRefType(), typeof(IApplicationHost).MakeByRefType()
            },
            null);
        if (create == null)
            throw new MissingMethodException(
                "System.Web.ApplicationManager.CreateObjectWithDefaultAppHostAndAppId is unavailable.");

        object[] invokeArgs = new object[] {
            WithTrailingSeparator(appRoot), "/", bridgeType, null, null
        };
        object value = create.Invoke(manager, invokeArgs);
        appId = invokeArgs[3] as string;
        IRegisteredObject registered = value as IRegisteredObject;
        if (registered == null || string.IsNullOrEmpty(appId))
            throw new InvalidOperationException("ASP.NET ApplicationManager did not create the hosted bridge.");
        return registered;
    }

    private static Exception Unwrap(Exception ex)
    {
        Exception current = ex;
        while (current.InnerException != null
               && (current is TargetInvocationException || current is TypeInitializationException))
            current = current.InnerException;
        return current;
    }

    public static int Main(string[] args)
    {
        try { Console.OutputEncoding = Encoding.UTF8; } catch { }
        if (args == null || args.Length < 2)
        {
            Console.Error.WriteLine("[ASPNET_HOST] expected: <command> <target-bin> ...");
            return 2;
        }

        string runtimeDir = Path.GetFullPath(args[1]);
        string appRoot = ResolveAppRoot(runtimeDir);
        if (!Directory.Exists(runtimeDir) || !Directory.Exists(appRoot))
        {
            Console.Error.WriteLine("[ASPNET_HOST] target directory does not exist: " + runtimeDir);
            return 2;
        }

        // Standalone metadata-only inspection: do not stage the ASP.NET bridge,
        // choose a license helper, construct a hosted AppDomain, or execute
        // any third-party application code for this command.
        if (string.Equals(args[0], "legacy-probe", StringComparison.OrdinalIgnoreCase))
            return LicenseRecoverLegacyMetadataProbe.Inspect(runtimeDir);

        string hostAssembly = Assembly.GetExecutingAssembly().Location;
        string toolDir = Path.GetDirectoryName(hostAssembly);
        string chain;
        string helper;
        try
        {
            helper = SelectHelper(toolDir, runtimeDir, args[0], out chain);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[ASPNET_HOST] " + ex.GetType().FullName + ": " + ex.Message);
            return 2;
        }

        string bridgeSource = Path.Combine(toolDir, BridgeFileName);
        if (!File.Exists(bridgeSource))
        {
            Console.Error.WriteLine("[ASPNET_HOST] " + BridgeFileName + " not found beside host executable.");
            return 2;
        }

        Console.WriteLine("[ASPNET_HOST] target registration chain=" + chain
            + "; helper=" + Path.GetFileName(helper));

        string targetBridge = Path.Combine(runtimeDir, BridgeFileName);
        bool bridgeCreated = false;
        ApplicationManager manager = null;
        string appId = null;
        try
        {
            bridgeCreated = EnsureBridgeInTargetBin(bridgeSource, targetBridge);

            Assembly bridgeAssembly = Assembly.LoadFrom(bridgeSource);
            Type bridgeType = bridgeAssembly.GetType(BridgeTypeName, true);
            MethodInfo run = bridgeType.GetMethod(
                "Run", BindingFlags.Instance | BindingFlags.Public);
            if (run == null)
                throw new MissingMethodException(BridgeTypeName + ".Run is unavailable.");

            manager = ApplicationManager.GetApplicationManager();
            manager.Open();
            IRegisteredObject bridge = CreateHostedBridge(manager, appRoot, bridgeType, out appId);
            Console.WriteLine("[ASPNET_HOST] hosted AppDomain created; appId=" + appId);

            object result = run.Invoke(bridge, new object[] { appRoot, runtimeDir, helper, args });
            return result == null ? 0 : Convert.ToInt32(result);
        }
        catch (Exception ex)
        {
            Exception inner = Unwrap(ex);
            Console.Error.WriteLine("[ASPNET_HOST] " + inner.GetType().FullName + ": " + inner.Message);
            if (!string.IsNullOrEmpty(inner.StackTrace))
                Console.Error.WriteLine(inner.StackTrace);
            return 1;
        }
        finally
        {
            if (manager != null)
            {
                if (!string.IsNullOrEmpty(appId))
                {
                    try { manager.ShutdownApplication(appId); } catch { }
                }
                try { manager.Close(); } catch { }
            }

            if (bridgeCreated)
                DeleteBridgeWithRetry(targetBridge);
        }
    }
}


/// <summary>
/// Offline, read-only ABI compatibility check for DS0101's native .NET assemblies.
/// ReflectionOnlyLoadFrom reads CLR metadata without executing method bodies or
/// type initializers. It does NOT prove the presence of a valid software license.
/// </summary>
internal static class LicenseRecoverLegacyMetadataProbe
{
    private const BindingFlags Any = BindingFlags.Public | BindingFlags.NonPublic
        | BindingFlags.Static | BindingFlags.Instance;

    private static bool MatchParameters(ParameterInfo[] parameters, string[] typeNames)
    {
        if (parameters.Length != typeNames.Length) return false;
        for (int i = 0; i < parameters.Length; i++)
        {
            Type type = parameters[i].ParameterType;
            if (!string.Equals(type.FullName, typeNames[i], StringComparison.Ordinal))
                return false;
        }
        return true;
    }

    private static bool HasConstructor(Type type, params string[] parameterTypes)
    {
        foreach (ConstructorInfo method in type.GetConstructors(Any))
        {
            if (MatchParameters(method.GetParameters(), parameterTypes))
                return true;
        }
        return false;
    }

    private static bool HasMethod(Type type, string name, string returnType, bool requireStatic,
        params string[] parameterTypes)
    {
        foreach (MethodInfo method in type.GetMethods(Any))
        {
            if (!string.Equals(method.Name, name, StringComparison.Ordinal)
                || method.IsStatic != requireStatic
                || !string.Equals(method.ReturnType.FullName, returnType, StringComparison.Ordinal))
                continue;
            if (MatchParameters(method.GetParameters(), parameterTypes))
                return true;
        }
        return false;
    }

    public static int Inspect(string runtimeDir)
    {
        string regPath = Path.Combine(runtimeDir, "ITMC.Regedit.dll");
        string webPath = Path.Combine(runtimeDir, "ITMC.Web.dll");
        if (!File.Exists(regPath) || !File.Exists(webPath))
        {
            Console.Error.WriteLine("[LEGACY_PROBE] Missing ITMC.Regedit.dll or ITMC.Web.dll.");
            return 2;
        }

        ResolveEventHandler resolver = delegate(object sender, ResolveEventArgs args)
        {
            string simpleName = new AssemblyName(args.Name).Name;
            // Only permit local .NET dependencies with normal single-segment
            // assembly names; reject path-like names and traversal.
            if (!string.IsNullOrEmpty(simpleName)
                && string.Equals(Path.GetFileName(simpleName), simpleName, StringComparison.Ordinal)
                && simpleName.IndexOfAny(new char[]{'/', '\\', ':'}) == -1)
            {
                string dependency = Path.Combine(runtimeDir, simpleName + ".dll");
                if (File.Exists(dependency)) return Assembly.ReflectionOnlyLoadFrom(dependency);
            }
            // Framework types such as System.String are resolved from the
            // installed runtime's trusted assembly load context, not the site.
            return Assembly.ReflectionOnlyLoad(args.Name);
        };

        AppDomain.CurrentDomain.ReflectionOnlyAssemblyResolve += resolver;
        try
        {
            Assembly registration = Assembly.ReflectionOnlyLoadFrom(regPath);
            Assembly web = Assembly.ReflectionOnlyLoadFrom(webPath);
            Type main = registration.GetType("ITMC.Regedit.RegeditMain", true);
            Type info = registration.GetType("ITMC.Regedit.RegeditInfo", true);
            Type funpublic = web.GetType("ITMC.Web.funpublic", true);
            if (!HasConstructor(main, "System.String", "System.String")
                || !HasMethod(main, "NewRegistry", "System.String", false)
                || !HasMethod(main, "DoRegistry", "System.Boolean", false,
                              "System.String", "System.String")
                || !HasMethod(main, "CheckReInfo", "System.Boolean", false,
                              info.FullName + "&")
                || !HasMethod(funpublic, "CheckReg", "System.Boolean", true))
            {
                Console.Error.WriteLine("[LEGACY_PROBE] DS0101 native signatures do not match.");
                return 3;
            }

            Console.WriteLine("[LEGACY_PROBE] Found DS0101 upper registration signature set.");
            Console.WriteLine("[LEGACY_PROBE] Found static ITMC.Web.funpublic.CheckReg():bool.");
            Console.WriteLine("[LEGACY_PROBE] No vendor method was executed; no site files staged.");
            Console.WriteLine("RESULT: LEGACY_SIGNATURES_VERIFIED_ONLY");
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[LEGACY_PROBE] Metadata read failed: "
                + ex.GetType().FullName + ": " + ex.Message);
            return 4;
        }
        finally
        {
            AppDomain.CurrentDomain.ReflectionOnlyAssemblyResolve -= resolver;
        }
    }
}
