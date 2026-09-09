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

        string hostAssembly = Assembly.GetExecutingAssembly().Location;
        string toolDir = Path.GetDirectoryName(hostAssembly);
        string helper = Path.Combine(toolDir, "LicenseRecover.NET.exe");
        string bridgeSource = Path.Combine(toolDir, BridgeFileName);
        if (!File.Exists(helper))
        {
            Console.Error.WriteLine("[ASPNET_HOST] LicenseRecover.NET.exe not found beside host executable.");
            return 2;
        }
        if (!File.Exists(bridgeSource))
        {
            Console.Error.WriteLine("[ASPNET_HOST] " + BridgeFileName + " not found beside host executable.");
            return 2;
        }

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
