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

        // Publish the target application's virtual/physical root through the same
        // AppDomain data keys used by classic ASP.NET. Once those keys are present,
        // the five-argument SimpleWorkerRequest constructor is not legal because it
        // attempts to override an already-established application path. Use the
        // non-overriding constructor so Server.MapPath("~/...") resolves against the
        // target web root without triggering HttpException on real .NET Framework.
        AppDomain.CurrentDomain.SetData(".appPath", physical);
        AppDomain.CurrentDomain.SetData(".appVPath", "/");
        SimpleWorkerRequest worker = new SimpleWorkerRequest(
            "default.aspx", "", TextWriter.Null);
        return new HttpContext(worker);
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
        try
        {
            HttpContext.Current = CreateContext(appRoot);
            Console.WriteLine("[ASPNET_HOST] appRoot=" + appRoot);
            Console.WriteLine("[ASPNET_HOST] runtimeDir=" + runtimeDir);

            Assembly assembly = Assembly.LoadFrom(helper);
            MethodInfo entry = assembly.EntryPoint;
            if (entry == null)
                throw new InvalidOperationException("LicenseRecover.NET.exe has no EntryPoint.");

            object[] invokeArgs = entry.GetParameters().Length == 0
                ? null
                : new object[] { args };
            object value = entry.Invoke(null, invokeArgs);
            if (entry.ReturnType == typeof(int) && value != null)
                return (int)value;
            return 0;
        }
        catch (TargetInvocationException ex)
        {
            Exception inner = ex.InnerException ?? ex;
            Console.Error.WriteLine("[ASPNET_HOST] helper invocation failed: "
                + inner.GetType().FullName + ": " + inner.Message);
            if (!string.IsNullOrEmpty(inner.StackTrace))
                Console.Error.WriteLine(inner.StackTrace);
            return 1;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[ASPNET_HOST] " + ex.GetType().FullName + ": " + ex.Message);
            if (!string.IsNullOrEmpty(ex.StackTrace))
                Console.Error.WriteLine(ex.StackTrace);
            return 1;
        }
        finally
        {
            HttpContext.Current = previous;
        }
    }
}
