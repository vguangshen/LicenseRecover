using System;
using System.IO;
using System.Reflection;
using System.Web;
using System.Web.Hosting;

public sealed class LicenseRecoverAspNetBridgeRunner : MarshalByRefObject, IRegisteredObject
{
    private volatile bool stopped;

    public override object InitializeLifetimeService()
    {
        return null;
    }

    public void Stop(bool immediate)
    {
        stopped = true;
    }

    public int Run(string appRoot, string runtimeDir, string helper, string[] args)
    {
        if (stopped)
            throw new InvalidOperationException("ASP.NET bridge has already been stopped.");

        HttpContext previous = HttpContext.Current;
        string previousDirectory = Directory.GetCurrentDirectory();
        ResolveEventHandler resolver = delegate(object sender, ResolveEventArgs resolveArgs)
        {
            return ResolveFromTargetBin(runtimeDir, resolveArgs);
        };

        try
        {
            Directory.SetCurrentDirectory(appRoot);
            ValidateHostedEnvironment(appRoot);

            SimpleWorkerRequest worker = new SimpleWorkerRequest(
                "default.aspx", "", TextWriter.Null);
            HttpContext.Current = new HttpContext(worker);
            ValidateMapPaths(HttpContext.Current, appRoot);

            AppDomain.CurrentDomain.AssemblyResolve += resolver;
            Console.WriteLine("[ASPNET_HOST] appRoot=" + appRoot);
            Console.WriteLine("[ASPNET_HOST] runtimeDir=" + runtimeDir);
            Console.WriteLine("[ASPNET_HOST] appDomainBase=" + AppDomain.CurrentDomain.BaseDirectory);
            Console.WriteLine("[ASPNET_HOST] appDomainConfig=" + AppDomain.CurrentDomain.SetupInformation.ConfigurationFile);

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
            try { Directory.SetCurrentDirectory(previousDirectory); } catch { }
        }
    }

    private static string WithTrailingSeparator(string path)
    {
        string full = Path.GetFullPath(path);
        if (!full.EndsWith(Path.DirectorySeparatorChar.ToString(), StringComparison.Ordinal))
            full += Path.DirectorySeparatorChar;
        return full;
    }

    private static void ValidateHostedEnvironment(string appRoot)
    {
        string expected = WithTrailingSeparator(appRoot);
        string physical = HostingEnvironment.ApplicationPhysicalPath;
        string runtimePath = HttpRuntime.AppDomainAppPath;
        string virtualPath = HostingEnvironment.ApplicationVirtualPath;

        Console.WriteLine("[ASPNET_HOST] hostingEnvironment appVirtualPath="
            + (virtualPath ?? "<null>")
            + " appPhysicalPath=" + (physical ?? "<null>")
            + " runtimeAppPath=" + (runtimePath ?? "<null>"));

        if (!HostingEnvironment.IsHosted)
            throw new InvalidOperationException("System.Web HostingEnvironment is not hosted.");
        if (string.IsNullOrEmpty(physical)
            || string.IsNullOrEmpty(runtimePath)
            || !string.Equals(WithTrailingSeparator(physical), expected, StringComparison.OrdinalIgnoreCase)
            || !string.Equals(WithTrailingSeparator(runtimePath), expected, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                "ASP.NET hosted application path does not match target root. "
                + "HostingEnvironment=" + (physical ?? "<null>")
                + "; HttpRuntime=" + (runtimePath ?? "<null>")
                + "; expected=" + expected);
        }
    }

    private static void ValidateMapPaths(HttpContext context, string appRoot)
    {
        if (context == null || context.Server == null)
            throw new InvalidOperationException("ASP.NET HttpContext/Server is unavailable.");

        string mappedRegister = context.Server.MapPath("~/Register.xml");
        string mappedConfig = context.Server.MapPath("~/config.xml");
        string expectedRegister = Path.GetFullPath(Path.Combine(appRoot, "Register.xml"));
        string expectedConfig = Path.GetFullPath(Path.Combine(appRoot, "config.xml"));

        Console.WriteLine("[ASPNET_HOST] mapPath Register.xml=" + (mappedRegister ?? "<null>"));
        Console.WriteLine("[ASPNET_HOST] mapPath config.xml=" + (mappedConfig ?? "<null>"));

        if (string.IsNullOrEmpty(mappedRegister)
            || string.IsNullOrEmpty(mappedConfig)
            || !string.Equals(Path.GetFullPath(mappedRegister), expectedRegister, StringComparison.OrdinalIgnoreCase)
            || !string.Equals(Path.GetFullPath(mappedConfig), expectedConfig, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                "ASP.NET MapPath does not resolve to the target web root. "
                + "Register.xml=" + (mappedRegister ?? "<null>")
                + "; config.xml=" + (mappedConfig ?? "<null>"));
        }
    }

    private static Exception Unwrap(Exception ex)
    {
        Exception current = ex;
        while (current.InnerException != null
               && (current is TargetInvocationException || current is TypeInitializationException))
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

    private static bool IsGenCodeWithoutExplicitRequest(string[] args)
    {
        if (args == null || args.Length == 0
            || !string.Equals(args[0], "gencode", StringComparison.OrdinalIgnoreCase))
            return false;

        for (int i = 0; i < args.Length; i++)
        {
            if (string.Equals(args[i], "--seq", StringComparison.OrdinalIgnoreCase)
                || string.Equals(args[i], "-s", StringComparison.OrdinalIgnoreCase))
                return false;
        }
        return true;
    }

    private static string FindProductArgument(string[] args)
    {
        if (args == null) return "YX0302";
        for (int i = 0; i + 1 < args.Length; i++)
        {
            if (string.Equals(args[i], "--product", StringComparison.OrdinalIgnoreCase)
                || string.Equals(args[i], "-p", StringComparison.OrdinalIgnoreCase))
                return args[i + 1];
        }
        return "YX0302";
    }

    private static void ProbeNativeRequestPath(Assembly helperAssembly, string[] args)
    {
        if (!IsGenCodeWithoutExplicitRequest(args)) return;

        string product = FindProductArgument(args);
        Type appReflection = helperAssembly.GetType("LicenseRecoverNet.AppReflection", true);
        MethodInfo getRegNo = appReflection.GetMethod(
            "GetRegNo", BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
        if (getRegNo == null)
            throw new MissingMethodException("LicenseRecover.NET helper is missing AppReflection.GetRegNo.");

        try
        {
            object value = getRegNo.Invoke(null, new object[] { product });
            string hostId = value as string;
            Console.WriteLine("[ASPNET_HOST] target request-code probe=OK; hostIdLength="
                + (hostId == null ? 0 : hostId.Length));
        }
        catch (Exception ex)
        {
            Exception inner = Unwrap(ex);
            throw new InvalidOperationException(
                "target request-code probe failed: " + inner.GetType().FullName + ": " + inner.Message,
                inner);
        }
    }

    private static int InvokeHelperDirect(string helper, string[] args)
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

        ProbeNativeRequestPath(assembly, args);

        Console.WriteLine("[ASPNET_HOST] helperDispatch=RunDirect/hosted-AppDomain");
        object value = runDirect.Invoke(null, new object[] { options });
        return value == null ? 0 : Convert.ToInt32(value);
    }
}
