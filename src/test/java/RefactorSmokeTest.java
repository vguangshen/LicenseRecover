import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RefactorSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("PASS: " + message);
    }

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("licenserecover-smoke");

        check("fwq".equals(LicenseRecover.LOCAL_AUTH_USER_ID),
                "local authorization UserID fixed to fwq");

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

        Path modernRoot = base.resolve("modernUppercaseOnly");
        Path modernBin = modernRoot.resolve("bin");
        Files.createDirectories(modernBin);
        Files.write(modernBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(modernBin.resolve("ITMC.Regedit.dll"), new byte[]{2});
        AppInfo modernInfo = AppDetector.detect(modernRoot.toFile());
        check(modernInfo.type == AppInfo.Type.DOTNET,
                "detect modern .NET app with uppercase ITMC.Regedit.dll only");
        check(!Files.exists(modernBin.resolve("itmcRegedit.dll")),
                "uppercase-only detector does not require legacy lowercase assembly");

        Path productFixture = base.resolve("YX0303-Web.dll");
        writeUtf16Fixture(productFixture, "YX0303", "YX030301", "YX030308", "YX030322");
        check("YX0303".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        productFixture.toFile(), null)),
                "one-click detects YX0303 ProName from target assembly strings");
        check("YX030301,YX030308,YX030322".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                productFixture.toFile(), "YX0303")),
                "one-click derives YX0303 local product list");

        Path dsFixture = base.resolve("DS01-Web.dll");
        writeUtf16Fixture(dsFixture, "itmcIEC", "DS0101", "DS0107", "DS0110", "DS0112");
        check("itmcIEC".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        dsFixture.toFile(), null)),
                "one-click detects itmcIEC DS01xx ProName");
        check("DS0101,DS0107,DS0110,DS0112".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                dsFixture.toFile(), "itmcIEC")),
                "one-click derives DS01xx local product list");

        String knownPlain = "123456{\"UserID\":\"fwq\"}654321";
        String knownCipher = "9ED04E8D57009B0173A79367751FB10CF6348ACA295E0F1D56826AC5E8CC163D";
        check(knownCipher.equals(LicenseRecoverModernGUIAutoRecovery.desEncryptHex(
                        knownPlain, "*ITMCYX0302OK*")),
                "one-click DES/CBC local-license encryption matches known vector");
        check(knownPlain.equals(LicenseRecoverModernGUIAutoRecovery.desDecryptHex(
                        knownCipher, "*ITMCYX0302OK*")),
                "one-click DES/CBC known vector decrypts correctly");

        String regJson = LicenseRecoverModernGUIAutoRecovery.buildRegInfoJson(
                "F000606A59904719", "YX0302", "YX030201,YX030204");
        check(regJson.contains("\"UserID\":\"fwq\""),
                "one-click RegInfo embeds fwq in encrypted local object");
        check(regJson.contains("\"CountDay\":10"),
                "one-click RegInfo preserves vendor CountDay=10 default");
        check(regJson.contains("\"RegID\":\"F000606A59904719\""),
                "one-click RegInfo embeds target RegID");

        String xml = "<ROOT><reg><regType>3</regType><regName>OLD</regName>"
                + "<WebSerUserID>keep-me</WebSerUserID>"
                + "<Service>http://regservice.itmc.cn/Service.asmx</Service></reg></ROOT>";
        String updatedXml = LicenseRecoverModernGUIAutoRecovery.updateLocalLicenseXml(
                xml, "A1B2C3D4", true);
        check(updatedXml.contains("<regType>1</regType>"),
                "one-click writes local regType=1");
        check(updatedXml.contains("<regName>A1B2C3D4</regName>"),
                "one-click replaces regName");
        check(updatedXml.contains("<WebSerUserID>keep-me</WebSerUserID>"),
                "one-click does not overwrite outer WebSerUserID");
        check(updatedXml.contains("<Service>http://127.0.0.1:9/Service.asmx</Service>"),
                "one-click blocks residual registration service when requested");

        Path parentApp = base.resolve("parentApp");
        Path parentBin = parentApp.resolve("bin");
        Path ordinaryChild = parentApp.resolve("ordinaryChild");
        Files.createDirectories(parentBin);
        Files.createDirectories(ordinaryChild);
        Files.write(parentBin.resolve("ITMC.Web.dll"), new byte[]{1});
        Files.write(parentBin.resolve("itmcRegedit.dll"), new byte[]{1});
        check(AppDetector.detect(parentApp.toFile()).type == AppInfo.Type.DOTNET,
                "single-app detection recognizes app root");
        check(AppDetector.detect(parentBin.toFile()).type == AppInfo.Type.DOTNET,
                "single-app detection recognizes standard bin directory");
        check(AppDetector.detect(ordinaryChild.toFile()).type == AppInfo.Type.UNKNOWN,
                "single-app detection does not cross unrelated child boundary");
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

        check(LicenseRecoverModernGUIGitHubUpdateService.compareVersions("1.1.1", "1.1.0") > 0,
                "GitHub updater semantic version comparison");
        check(LicenseRecoverModernGUIGitHubUpdateService.compareVersions("v1.1.1", "1.1.1") == 0,
                "GitHub updater normalizes v-prefix");
        String checksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String sums = checksum + "  LicenseRecover-latest.zip\n"
                + checksum + "  LicenseRecover-update.zip\n";
        check(checksum.equals(LicenseRecoverModernGUIGitHubUpdateService.parseChecksum(sums)),
                "GitHub updater parses portable checksum");
        check(checksum.equals(LicenseRecoverModernGUIGitHubUpdateService.parseChecksum(
                sums, "LicenseRecover-update.zip")),
                "GitHub updater parses slim update checksum");

        Path updateInstall = base.resolve("update-install");
        Files.createDirectories(updateInstall);
        Files.write(updateInstall.resolve("VERSION.txt"), Arrays.asList("1.1.0"), StandardCharsets.UTF_8);
        Files.write(updateInstall.resolve("runtime.txt"), Arrays.asList("old"), StandardCharsets.UTF_8);
        Path embeddedJava = updateInstall.resolve("jre/bin/java.exe");
        Files.createDirectories(embeddedJava.getParent());
        Files.write(embeddedJava, Arrays.asList("embedded-jre"), StandardCharsets.UTF_8);
        Path updateZip = base.resolve("update.zip");
        ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(updateZip)));
        try {
            writeZipEntry(zout, "VERSION.txt", "1.1.1\n");
            writeZipEntry(zout, "runtime.txt", "new\n");
            writeZipEntry(zout, "nested/new.txt", "created\n");
        } finally { zout.close(); }
        LicenseRecoverModernGUIUpdateInstaller.applyUpdate(updateZip.toFile(), updateInstall.toFile());
        check("1.1.1".equals(new String(Files.readAllBytes(updateInstall.resolve("VERSION.txt")),
                        StandardCharsets.UTF_8).trim()), "updater replaces VERSION.txt");
        check("new".equals(new String(Files.readAllBytes(updateInstall.resolve("runtime.txt")),
                        StandardCharsets.UTF_8).trim()), "updater overwrites runtime files");
        check(Files.isRegularFile(updateInstall.resolve("nested/new.txt")),
                "updater adds new runtime files");
        check("embedded-jre".equals(new String(Files.readAllBytes(embeddedJava),
                        StandardCharsets.UTF_8).trim()),
                "slim updater preserves existing embedded JRE");

        Path maliciousZip = base.resolve("malicious.zip");
        zout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(maliciousZip)));
        try { writeZipEntry(zout, "../escape.txt", "blocked\n"); }
        finally { zout.close(); }
        boolean blocked = false;
        try {
            LicenseRecoverModernGUIUpdateInstaller.applyUpdate(
                    maliciousZip.toFile(), updateInstall.toFile());
        } catch (IOException expected) { blocked = true; }
        check(blocked && !Files.exists(base.resolve("escape.txt")),
                "updater blocks zip-slip entries");

        System.out.println("ALL REFACTOR SMOKE TESTS PASSED");
    }

    private static void writeUtf16Fixture(Path path, String... values) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            text.append(value).append('\u0001');
        }
        Files.write(path, text.toString().getBytes(StandardCharsets.UTF_16LE));
    }

    private static void writeZipEntry(ZipOutputStream out, String name, String value)
            throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
