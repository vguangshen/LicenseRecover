import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class RefactorSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("licenserecover-smoke");

        Path javaRoot = base.resolve("javaApp");
        Path javaLib = javaRoot.resolve("WEB-INF/lib");
        Files.createDirectories(javaLib);
        Files.write(javaLib.resolve("ITMCReg.jar"), new byte[]{1, 2, 3});
        Files.write(javaLib.resolve("config.xml"), Arrays.asList("<ROOT><reg/></ROOT>"));
        Files.write(javaRoot.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID: YT00123"));
        AppInfo javaInfo = AppDetector.detect(javaRoot.toFile());
        check(javaInfo.type == AppInfo.Type.JAVA, "detect Java app");
        check("YT00123".equals(javaInfo.softVersionId), "read Java SoftVersionID");
        check(javaInfo.appRoot.getCanonicalFile().equals(javaRoot.toFile().getCanonicalFile()),
                "resolve Java app root");
        check(ConfigSafety.prepareJavaWay1(javaInfo, System.out::print),
                "Java way-1 prewrite backup succeeds");
        check(Files.list(javaLib).anyMatch(p -> p.getFileName().toString().contains("prewrite")),
                "Java way-1 prewrite backup exists");

        Path nestedRoot = base.resolve("nestedApp");
        Path nestedLib = nestedRoot.resolve("WEB-INF/WEB-INF/lib");
        Files.createDirectories(nestedLib);
        Files.write(nestedLib.resolve("ITMCReg-v.jar"), new byte[]{4});
        AppInfo nestedInfo = AppDetector.detect(nestedRoot.toFile());
        check(nestedInfo.type == AppInfo.Type.JAVA, "detect nested WEB-INF Java app");
        check(nestedInfo.appRoot.getCanonicalFile().equals(nestedRoot.toFile().getCanonicalFile()),
                "resolve nested Java app root");

        Path dotnetRoot = base.resolve("dotnetApp");
        Path dotnetBin = dotnetRoot.resolve("bin");
        Files.createDirectories(dotnetBin);
        Files.write(dotnetBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(dotnetBin.resolve("itmcRegedit.dll"), new byte[]{2, 3});
        Files.write(dotnetRoot.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft></ROOT>"));
        AppInfo dotnetInfo = AppDetector.detect(dotnetRoot.toFile());
        check(dotnetInfo.type == AppInfo.Type.DOTNET, "detect .NET app");
        check("DS0101".equals(dotnetInfo.softVersionId), "read .NET SoftVersionID");
        check(PatchSafety.prepare(dotnetInfo, System.out::print), ".NET prepatch backup succeeds");
        check(Files.list(dotnetBin).anyMatch(p -> p.getFileName().toString().contains("prepatch")),
                ".NET prepatch backup exists");

        Path parentApp = base.resolve("parentApp");
        Path parentBin = parentApp.resolve("bin");
        Path ordinaryChild = parentApp.resolve("ordinaryChild");
        Files.createDirectories(parentBin);
        Files.createDirectories(ordinaryChild);
        Files.write(parentBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(parentBin.resolve("itmcRegedit.dll"), new byte[]{1});
        check(AppDetector.detect(ordinaryChild.toFile()).type == AppInfo.Type.DOTNET,
                "single-app detection may walk upward");
        check(AppDetector.detectLocal(ordinaryChild.toFile()).type == AppInfo.Type.UNKNOWN,
                "batch local detection does not walk into parent app");

        Path source = base.resolve("source.bin");
        Path backup = base.resolve("source.bak");
        Files.write(source, new byte[]{9, 8, 7});
        SafetyBackup.requireCopy(source, backup, System.out::print);
        check(Files.size(source) == Files.size(backup), "mandatory backup size validation");

        List<String> dryPatch = Arrays.asList("java", "-cp", "x", "LicenseRecover",
                "--remove-net", javaRoot.toString(), "--dry-run");
        List<String> normalized = ProcessRunner.normalizeCommand(dryPatch, System.out::print);
        check(normalized.contains("--scan-net") && !normalized.contains("--remove-net"),
                "Java way-3 dry-run becomes scan-only");

        List<String> productAlias = Arrays.asList("java", "-jar", "/tmp/LicenseRecover.jar",
                "--gencode", dotnetRoot.toString(), "--product", "YX030204");
        List<String> productNormalized = ProcessRunner.normalizeCommand(productAlias, System.out::print);
        check(productNormalized.contains("-p") && !productNormalized.contains("--product"),
                "CLI product override is normalized to -p");

        String javaExe = System.getProperty("java.home") + File.separator + "bin"
                + File.separator + "java";
        OperationResult process = ProcessRunner.run(Arrays.asList(javaExe, "-version"),
                System.out::print, 10);
        check(process.isSuccess(), "process runner executes and returns success");

        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
    }
}
