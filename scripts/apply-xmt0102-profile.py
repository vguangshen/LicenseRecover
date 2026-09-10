from pathlib import Path

# JavaPlan: accept the target-proven Fastjson RegStr consumer used by XMT0102.
p = Path('src/main/java/LicenseRecoverModernGUIJavaPlan.java')
s = p.read_text(encoding='utf-8')
method = s.index('static String confirmedConfigDrivenRuntimeProduct')
start = s.index('        if (classFileContainsAll(systemInfo, "config.xml", "SystemSoft", "registerId")', method)
end = s.index('\n\n        // Another target-owned generation', start)
replacement = '''        if (classFileContainsAll(systemInfo, "config.xml", "SystemSoft", "registerId")) {
            boolean directGetter = classFileContainsAll(listener,
                    "com/itmc/register/utils/SystemInfo", "registerId",
                    "itmc/regedit/RegisterMain", "getRegStr", "versionID", "contains");
            // XMT0102 serializes RegeditInfo through Fastjson and reads the lower-case
            // regStr property instead of invoking RegeditInfo.getRegStr() directly.
            boolean jsonGetter = classFileContainsAll(listener,
                    "com/itmc/register/utils/SystemInfo", "registerId",
                    "itmc/regedit/RegisterMain", "checkReInfo", "getRegInfo",
                    "com/alibaba/fastjson/JSONObject", "toJSONString", "parseObject",
                    "regStr", "versionID", "contains");
            if (directGetter || jsonGetter) return concrete;
        }'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')

# Regression fixture: family XMT01, concrete startup ProductID XMT0102, aliased RegStr.
p = Path('src/test/java/RefactorSmokeTest.java')
s = p.read_text(encoding='utf-8')
marker = '        Path ds501Root = base.resolve("java-DS50109");\n'
if marker not in s:
    raise SystemExit('RefactorSmokeTest DS501 marker not found')
fixture = '''        Path xmt0102Root = base.resolve("java-XMT0102-json-regstr");
        Path xmt0102Lib = xmt0102Root.resolve("WEB-INF/lib");
        Path xmt0102Classes = xmt0102Root.resolve("WEB-INF/classes");
        Files.createDirectories(xmt0102Lib);
        Files.createDirectories(xmt0102Classes.resolve("com/itmc/register/utils"));
        Files.createDirectories(xmt0102Classes.resolve("com/itmc/register/service"));
        Files.write(xmt0102Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmt0102Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=XMT0102"), StandardCharsets.UTF_8);
        Files.write(xmt0102Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>XMT0102</SoftVersionID><regInfo>QT100110,QT100106</regInfo></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(xmt0102Classes.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0102Classes.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0102Classes.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain checkReInfo getRegInfo "
                        .concat("com/alibaba/fastjson/JSONObject toJSONString parseObject regStr versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan xmt0102Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(xmt0102Root.toFile());
        check("XMT01".equals(xmt0102Plan.authorizationFamily)
                        && "XMT0102".equals(xmt0102Plan.runtimeProductId)
                        && "QT100110,QT100106".equals(xmt0102Plan.regStr)
                        && xmt0102Plan.automaticRecoveryReady,
                "XMT0102 proves concrete startup ProductID when RegStr is consumed through JSONObject");

'''
s = s.replace(marker, fixture + marker, 1)
p.write_text(s, encoding='utf-8')

# Sample-matrix documentation.
p = Path('docs/java-runtime-profile-v1.2.41.md')
s = p.read_text(encoding='utf-8')
marker = '| QT40101 | `RegisterListener -> SystemInitService.getRegisterMain(product,path) -> (String product, String token, String configPath)` | classpath root (`WEB-INF/classes`); startup consumes `getRegInfo()` without `checkReInfo()`; product `QT401`, RegStr `QT40101` | no (`ITMCReg-1.0.5.jar`) |\n'
if marker not in s:
    raise SystemExit('QT40101 matrix row not found')
row = '| XMT0102 | `ServletContext.getRealPath("/") -> RegisterListener.getRegisterMain(product,root,request) -> (String product, String token, String configPath)` | webapp-root path; family `XMT01`, startup ProductID `XMT0102`, RegStr `QT100110,QT100106` | yes |\n'
s = s.replace(marker, marker + row, 1)
s += '''\n\n## XMT0102 JSON RegStr consumer\n\nThe supplied XMT0102 sample uses the packed ITMCReg generation shared with DS2802/DS3110/YT001xx. `WEB-INF/classes/config.xml` declares `SoftVersionID=XMT0102` and `regInfo=QT100110,QT100106`. Startup constructs `RegisterMain` with concrete `SystemInfo.registerId` (`XMT0102`) and the Servlet webapp root. It reads RegStr by serializing `getRegInfo()` through Fastjson and fetching the lower-case `regStr` property, so concrete runtime-product proof accepts this target-proven consumer shape in addition to direct `getRegStr()` callers.\n\nThe sample's existing local payload is family-keyed as `XMT01`; the real startup constructor uses `XMT0102`. A target-native write using ProductID `XMT0102`, webapp-root path, and RegStr `QT100110,QT100106` passes fresh `checkReInfo()` and the application `RegisterListener.checkReInfoNew()` flow.\n'''
p.write_text(s, encoding='utf-8')
