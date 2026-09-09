using System;
using System.IO;
using System.Reflection;
using System.Text;
using System.Web;
using System.Web.Hosting;

public sealed class LicenseRecoverTargetDomainRunner : MarshalByRefObject
{
    public override object InitializeLifetimeService()
    {
        return null;
    }

    public int Run(string appRoot, string runtimeDir, string helper, string[] args)
    {
        HttpContext previous = HttpContext.Current;
        string previousDirectory = Directory.GetCurrentDirectory();
        ResolveEventHandler resolver = delegate(object sender, ResolveEventArgs resolveArgs)
        {
            return LicenseRecoverAspNetHost.ResolveFromTargetBin(runtimeDir, resolveArgs);
        };

        try
        {
            Directory.SetCurrentDirectory(appRoot);
            HttpContext.Current = LicenseRecoverAspNetHost.CreateContext(appRoot);
            AppDomain.CurrentDomain.AssemblyResolve += resolver;

            Console.WriteLine("[ASPNET_HOST] appRoot=" + appRoot);
            Console.WriteLine("[ASPNET_HOST] runtimeDir=" + runtimeDir);
            Console.WriteLine("[ASPNET_HOST] appDomainBase=" + AppDomain.CurrentDomain.BaseDirectory);
            Console.WriteLine("[ASPNET_HOST] appDomainConfig=" + AppDomain.CurrentDomain.SetupInformation.ConfigurationFile);

            LicenseRecoverAspNetHost.PreloadLowercaseRegistrationAssembly(runtimeDir);
            return LicenseRecoverAspNetHost.InvokeHelperDirect(helper, args);
        }
        catch (Exception ex)
        {
            Exception inner = LicenseRecoverAspNetHost.Unwrap(ex);
            Console.Error.WriteLine("[ASPNET_HOST] " + inner.GetType().FullName + ": " + inner.Message);
            if (!string.IsNullOrEmpty(inner.StackTrace))
                Console.Error.WriteLine(inner.StackTrace);
            return 1;
        }
        finally
        {
            AppDomain.CurrentDomain.AssemblyResolve -= resolver;
            HttpContext.Current = previous;
            try { Directory.SetCurrentDirectory(previousDirectory); } catch { }
        }
    }
}

internal static class LicenseRecoverAspNetHost
{
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

    internal static HttpContext CreateContext(string appRoot)
    {
        string physical = WithTrailingSeparator(appRoot);
        AppDomain.CurrentDomain.SetData(".appPath", physical);
        AppDomain.CurrentDomain.SetData(".appVPath", "/");
        SimpleWorkerRequest worker = new SimpleWorkerRequest(
            "default.aspx", "", TextWriter.Null);
        return new HttpContext(worker);
    }

    internal static Exception Unwrap(Exception ex)
    {
        Exception current = ex;
        while (current is TargetInvocationException && current.InnerException != null)
            current = current.InnerException;
        return current;
    }

    internal static Assembly ResolveFromTargetBin(string runtimeDir, ResolveEventArgs args)
    {
        try
        {
            string simpleName = new AssemblyName(args.Name).Name;
            if (string.IsNullOrEmpty(simpleName)) return null;

            string[] extensions = new[] { ".dll", ".exe" };
            foreach (string extension in extensions)
            {
                string candidate = Path.Combine(runtimeDir, simpleName + extension);
                if (!File.Exists(candidate)) continue;

                Console.WriteLine("[ASPNET_HOST] target-bin resolve: "
                    + simpleName + " -> " + candidate);
                return Assembly.LoadFrom(candidate);
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[ASPNET_HOST] target-bin resolve failed: "
                + ex.GetType().FullName + ": " + ex.Message);
        }
        return null;
    }

    internal static void PreloadLowercaseRegistrationAssembly(string runtimeDir)
    {
        string target = Path.Combine(runtimeDir, "itmcRegedit.dll");
        if (!File.Exists(target)) return;

        Console.WriteLine("[ASPNET_HOST] preload target registration assembly=" + target);
        Assembly.LoadFrom(target);
    }

    internal static int InvokeHelperDirect(string helper, string[] args)
    {
        Assembly assembly = Assembly.LoadFrom(helper);
        Type program = assembly.GetType("LicenseRecoverNet.Program", true);

        MethodInfo parseArgs = program.GetMethod(
            "ParseArgs", BindingFlags.Static | BindingFlags.NonPublic);
        MethodInfo runDirect = program.GetMethod(
            "RunDirect", BindingFlags.Static | BindingFlags.Public);
        if (parseArgs == null || runDirect == null)
            throw new MissingMethodException(
                "LicenseRecover.NET helper is missing ParseArgs/RunDirect.");

        object options = parseArgs.Invoke(null, new object[] { args });
        if (options == null)
            throw new ArgumentException("LicenseRecover.NET helper rejected the command line.");

        Console.WriteLine("[ASPNET_HOST] helperDispatch=RunDirect/target-AppDomain");
        object value = runDirect.Invoke(null, new object[] { options });
        return value == null ? 0 : Convert.ToInt32(value);
    }

    private static string FindWebConfig(string appRoot)
    {
        string lower = Path.Combine(appRoot, "web.config");
        if (File.Exists(lower)) return lower;
        string upper = Path.Combine(appRoot, "Web.config");
        return File.Exists(upper) ? upper : null;
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
        string helper = Path.Combine(Path.GetDirectoryName(hostAssembly), "LicenseRecover.NET.exe");
        if (!File.Exists(helper))
        {
            Console.Error.WriteLine("[ASPNET_HOST] LicenseRecover.NET.exe not found beside host executable.");
            return 2;
        }

        AppDomain targetDomain = null;
        try
        {
            AppDomainSetup setup = new AppDomainSetup();
            setup.ApplicationBase = WithTrailingSeparator(appRoot);
            setup.PrivateBinPath = "bin";
            setup.ShadowCopyFiles = "false";

            string webConfig = FindWebConfig(appRoot);
            if (!string.IsNullOrEmpty(webConfig))
                setup.ConfigurationFile = webConfig;

            Console.WriteLine("[ASPNET_HOST] creating target AppDomain; base=" + setup.ApplicationBase
                + " config=" + (setup.ConfigurationFile ?? "<default>"));

            targetDomain = AppDomain.CreateDomain(
                "LicenseRecover.Target." + Guid.NewGuid().ToString("N"),
                null,
                setup);

            LicenseRecoverTargetDomainRunner runner =
                (LicenseRecoverTargetDomainRunner)targetDomain.CreateInstanceFromAndUnwrap(
                    hostAssembly,
                    typeof(LicenseRecoverTargetDomainRunner).FullName);
            return runner.Run(appRoot, runtimeDir, helper, args);
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
            if (targetDomain != null)
            {
                try { AppDomain.Unload(targetDomain); } catch { }
            }
        }
    }
}
