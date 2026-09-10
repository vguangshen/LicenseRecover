from pathlib import Path

plan = Path('src/main/java/LicenseRecoverModernGUIJavaPlan.java')
s = plan.read_text(encoding='utf-8')
s = s.replace('matches("QT401\\d{2}")', 'matches("QT401\\\\d{2}")', 1)
s = s.replace('text.contains("id="" + concrete + """)', 'text.contains("id=\\\"" + concrete + "\\\"")', 1)
plan.write_text(s, encoding='utf-8')

test = Path('src/test/java/RefactorSmokeTest.java')
t = test.read_text(encoding='utf-8')
anchor = '''        check("XMT01".equals(xmt0102Plan.authorizationFamily)
                        && "XMT0102".equals(xmt0102Plan.runtimeProductId)
                        && "QT100110,QT100106".equals(xmt0102Plan.regStr)
                        && xmt0102Plan.automaticRecoveryReady,
                "XMT0102 proves concrete startup ProductID when RegStr is consumed through JSONObject");
'''
addition = anchor + '''
        Path xmt0103Root = base.resolve("java-XMT0103-json-regstr");
        Path xmt0103Lib = xmt0103Root.resolve("WEB-INF/lib");
        Path xmt0103Classes = xmt0103Root.resolve("WEB-INF/classes");
        Files.createDirectories(xmt0103Lib);
        Files.createDirectories(xmt0103Classes.resolve("com/itmc/register/utils"));
        Files.createDirectories(xmt0103Classes.resolve("com/itmc/register/service"));
        Files.write(xmt0103Lib.resolve("ITMCReg.jar"), new byte[]{1});
        Files.write(xmt0103Root.resolve("systemConfig.yml"),
                Arrays.asList("global.system.VersionID=XMT0103"), StandardCharsets.UTF_8);
        Files.write(xmt0103Classes.resolve("config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>XMT0103</SoftVersionID><regInfo>QT100102</regInfo></SystemSoft></ROOT>"),
                StandardCharsets.UTF_8);
        Files.write(xmt0103Classes.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0103Classes.resolve("com/itmc/register/utils/SystemInfo.class"),
                "config.xml SystemSoft registerId".getBytes(StandardCharsets.US_ASCII));
        Files.write(xmt0103Classes.resolve("com/itmc/register/service/RegisterListener.class"),
                "com/itmc/register/utils/SystemInfo registerId itmc/regedit/RegisterMain checkReInfo getRegInfo "
                        .concat("com/alibaba/fastjson/JSONObject toJSONString parseObject regStr versionID contains")
                        .getBytes(StandardCharsets.US_ASCII));
        LicenseRecoverModernGUIJavaPlan xmt0103Plan =
                LicenseRecoverModernGUIJavaPlan.inspect(xmt0103Root.toFile());
        check("XMT01".equals(xmt0103Plan.authorizationFamily)
                        && "XMT0103".equals(xmt0103Plan.runtimeProductId)
                        && "QT100102".equals(xmt0103Plan.regStr)
                        && xmt0103Plan.automaticRecoveryReady,
                "XMT0103 reuses the XMT Fastjson startup profile without a product-specific exception");
'''
if 'java-XMT0103-json-regstr' not in t:
    if anchor not in t:
        raise SystemExit('XMT0102 fixture anchor not found')
    t = t.replace(anchor, addition, 1)

source_guard_anchor = '''        check(preBlockAt >= 0 && generateAt > preBlockAt && applyAt > generateAt && verifyAt > applyAt,
                ".NET pre-block guard is established before gencode, DoRegistry and CheckReInfo in source order");
'''
source_guard_add = source_guard_anchor + '''
        int javaDoRegAt = autoRecoverySource.indexOf("[java-stage] DOREG: OK");
        int javaFreshVerifyAt = autoRecoverySource.indexOf("[java-stage] VERIFY_FRESH: OK");
        int javaSuccessAt = autoRecoverySource.indexOf("Java local authorization was applied through the target-native registration chain");
        check(javaDoRegAt >= 0 && javaFreshVerifyAt > javaDoRegAt && javaSuccessAt > javaFreshVerifyAt,
                "Java final success is emitted only after fresh-JVM target verification, never after DoRegistry alone");
        check(autoRecoverySource.contains("containsAllRegStrTokens(persisted, regStr)")
                        && autoRecoverySource.contains("verifiedAttempt == null"),
                "Java final success requires persisted target RegStr and a successful startup-derived verifier attempt");

        String javaHostSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/LicenseRecoverJavaHost.java")), StandardCharsets.UTF_8);
        int javaVerifyMethodAt = javaHostSource.indexOf("static int verify");
        int javaVerifyOkAt = javaHostSource.indexOf("[java-stage] VERIFY_FRESH: OK", javaVerifyMethodAt);
        int javaResultOkAt = javaHostSource.indexOf("RESULT: OK", javaVerifyMethodAt);
        check(javaHostSource.contains("Boolean.TRUE.equals(unregistered)")
                        && javaHostSource.contains("containsAllCsv(regStr, o.regStr)")
                        && javaHostSource.contains("persisted ProName mismatch")
                        && javaVerifyMethodAt >= 0 && javaVerifyOkAt > javaVerifyMethodAt
                        && javaResultOkAt > javaVerifyOkAt,
                "Java host prints final RESULT: OK only after checkReInfo/RegStr/ProName verification passes");
'''
if 'Java final success is emitted only after fresh-JVM target verification' not in t:
    if source_guard_anchor not in t:
        raise SystemExit('source guard anchor not found')
    t = t.replace(source_guard_anchor, source_guard_add, 1)
test.write_text(t, encoding='utf-8')

doc = Path('docs/java-runtime-profile-v1.2.41.md')
d = doc.read_text(encoding='utf-8')
row = '| XMT0102 | `ServletContext.getRealPath("/") -> RegisterListener.getRegisterMain(product,root,request) -> (String product, String token, String configPath)` | webapp-root path; family `XMT01`, startup ProductID `XMT0102`, RegStr `QT100110,QT100106` | yes |\n'
if '| XMT0103 |' not in d:
    if row not in d:
        raise SystemExit('XMT0102 matrix row not found')
    d = d.replace(row, row + '| XMT0103 | same XMT Fastjson startup flow and three-arg token/path ABI | webapp-root path; family `XMT01`, startup ProductID `XMT0103`, RegStr `QT100102` | yes |\n', 1)
    d += '\nXMT0103 confirms the XMT0102 Fastjson consumer is a reusable lineage profile: the concrete runtime ProductID comes from target `SystemInfo.registerId`, while `XMT01` remains only the broader authorization family. Its target-declared RegStr is `QT100102`.\n'
doc.write_text(d, encoding='utf-8')
