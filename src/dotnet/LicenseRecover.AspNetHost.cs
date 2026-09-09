using System;
using System.IO;
using System.Reflection;
using System.Text;
using System.Web;
using System.Web.Hosting;

internal static class LicenseRecoverAspNetHost
{
    private static string WithTrailingSeparator(string path)
    {
        string full = Path.GetFullPath(path);
        if (!full.EndsWith(Path.DirectorySeparatorChar.ToString(), StringComparison.Ordinal))
            full += Path.DirectorySeparatorChar;
        return full;
    }

    private static string ResolveAppRoot(string runtimeDir)
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

    private static HttpContext CreateContext(string appRoot)
    {
        string physical = WithTrailingSeparator(appRoot);

        // Classic ASP.NET uses these AppDomain data keys as the application root.
        // The non-overriding SimpleWorkerRequest constructor is required after the
        // application path is established; otherwise .NET Framework rejects an
        // attempt to replace the application path.
        AppDomain.CurrentDomain.SetData(".appPath", physical);
        AppDomain.CurrentDomain.SetData(".appVPath", "/");
        SimpleWorkerRequest worker = new SimpleWorkerRequest(
            "default.aspx", "", TextWriter.Null);
        return new HttpContext(worker);
    }

    private static Exception Unwrap(Exception ex)
    {
        Exception current = ex;
        while (current is TargetInvocationException && current.InnerException != null)
            current = current.InnerException;
        return current;
    }

    private static Assembly ResolveFromTargetBin(string runtimeDir, ResolveEventArgs args)
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

    private static void PreloadLowercaseRegistrationAssembly(string runtimeDir)
    {
        string target = Path.Combine(runtimeDir, "itmcRegedit.dll");
        if (!File.Exists(target)) return;

        Console.WriteLine("[ASPNET_HOST] preload target registration assembly=" + target);
        Assembly.LoadFrom(target);
    }

    private static int InvokeHelperDirect(string helper, string[] args)
    {
        Assembly assembly = Assembly.LoadFrom(helper);
        Type program = assembly.GetType("LicenseRecoverNet.Program", true);

        // Program.Main -> Program.Run normally creates a child AppDomain for the
        // lowercase itmcRegedit.dll path. A child AppDomain cannot inherit the
        // HttpContext created above, and Program.RunInBinDomain also resolves its
        // DomainRunner from Assembly.GetEntryAssembly(), which is this host EXE.
        // Parse the helper's own arguments, then call its public RunDirect method
        // in this same AppDomain so the target registration component keeps the
        // ASP.NET HttpContext required by Server.MapPath("~/...").
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

        Console.WriteLine("[ASPNET_HOST] helperDispatch=RunDirect/same-AppDomain");
        object value = runDirect.Invoke(null, new object[] { options });
        return value == null ? 0 : Convert.ToInt32(value);
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

        string helper = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "LicenseRecover.NET.exe");
        if (!File.Exists(helper))
        {
            Console.Error.WriteLine("[ASPNET_HOST] LicenseRecover.NET.exe not found beside host executable.");
            return 2;
        }

        HttpContext previous = HttpContext.Current;
        ResolveEventHandler resolver = delegate(object sender, ResolveEventArgs resolveArgs)
        {
            return ResolveFromTargetBin(runtimeDir, resolveArgs);
        };

        try
        {
            HttpContext.Current = CreateContext(appRoot);
            Console.WriteLine("[ASPNET_HOST] appRoot=" + appRoot);
            Console.WriteLine("[ASPNET_HOST] runtimeDir=" + runtimeDir);

            // RunDirect executes inside the tool AppDomain, whose BaseDirectory is
            // LicenseRecover.NET. The legacy helper therefore otherwise probes its
            // own directory for itmcRegedit.dll. Redirect missing target assemblies
            // and their dependencies to the application's real bin directory, and
            // preload the lowercase registration component so Assembly.Load by name
            // resolves the same assembly used by the web application.
            AppDomain.CurrentDomain.AssemblyResolve += resolver;
            PreloadLowercaseRegistrationAssembly(runtimeDir);
            return InvokeHelperDirect(helper, args);
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
            AppDomain.CurrentDomain.AssemblyResolve -= resolver;
            HttpContext.Current = previous;
        }
    }
}
