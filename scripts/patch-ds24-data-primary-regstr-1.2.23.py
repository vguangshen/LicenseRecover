from pathlib import Path

root = Path('.')
plan_path = root / 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
test_path = root / 'src/test/java/RefactorSmokeTest.java'
readme_path = root / 'README.md'
version_path = root / 'VERSION.txt'
notes_path = root / 'release-notes/v1.2.23.md'

plan = plan_path.read_text(encoding='utf-8')
old = '''        String configDrivenPrimaryRegStr = confirmedConfigDrivenPrimaryRegStr(
                root, soft, family, configDrivenRuntime);
        String recoveredLocalRegStr = null;
'''
new = '''        String configDrivenPrimaryRegStr = confirmedConfigDrivenPrimaryRegStr(
                root, soft, family, configDrivenRuntime);
        String dataDrivenPrimaryRegStr = confirmedDataDrivenPrimaryRegStr(
                root, soft, family, runtimeProduct);
        String recoveredLocalRegStr = null;
'''
assert old in plan, 'primary RegStr anchor missing'
plan = plan.replace(old, new, 1)

old = '''        } else if (!blank(configDrivenPrimaryRegStr)) {
            products = configDrivenPrimaryRegStr;
        } else {
'''
new = '''        } else if (!blank(configDrivenPrimaryRegStr)) {
            products = configDrivenPrimaryRegStr;
        } else if (!blank(dataDrivenPrimaryRegStr)) {
            products = dataDrivenPrimaryRegStr;
        } else {
'''
assert old in plan, 'products anchor missing'
plan = plan.replace(old, new, 1)

old = '''                || dataRegInfo != null || classesRegInfo != null
                || !blank(configDrivenPrimaryRegStr) || recoveredLocalRegStr != null;
'''
new = '''                || dataRegInfo != null || classesRegInfo != null
                || !blank(configDrivenPrimaryRegStr) || !blank(dataDrivenPrimaryRegStr)
                || recoveredLocalRegStr != null;
'''
assert old in plan, 'directoryRegStr anchor missing'
plan = plan.replace(old, new, 1)

anchor = '''    private static String readSystemConfigVersionId(File root) {
'''
method = '''    /**
     * Confirm a legacy data-config generation whose own startup code maps
     * systemConfig.yml VersionID -> IStatic._SYS_CODE and then requires the
     * primary RegisterMain RegStr to contain that code.  The family/runtime
     * identity must also be proved by this target's IGlobal/local-register/startup
     * classes.  No VersionID naming rule is executable evidence by itself.
     */
    static String confirmedDataDrivenPrimaryRegStr(File root, String softId,
                                                    String confirmedFamily,
                                                    String confirmedRuntimeProduct) {
        if (root == null || blank(softId) || blank(confirmedFamily)
                || blank(confirmedRuntimeProduct)) return null;
        String concrete = softId.trim();
        String family = confirmedFamily.trim();
        String runtime = confirmedRuntimeProduct.trim();
        if (!family.equalsIgnoreCase(runtime)) return null;
        if (!concrete.toUpperCase(Locale.ROOT).startsWith(family.toUpperCase(Locale.ROOT))) return null;

        String ymlVersion = readSystemConfigVersionId(root);
        if (blank(ymlVersion) || !concrete.equalsIgnoreCase(ymlVersion.trim())) return null;
        File dataConfig = new File(root, "data" + File.separator + "config.xml");
        String configured = readElement(dataConfig, "SoftVersionID");
        if (blank(configured) || !concrete.equalsIgnoreCase(configured.trim())) return null;

        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        File xmlUtil = new File(classes, "util" + File.separator + "IXmlUtil.class");
        File iGlobal = new File(classes, "global" + File.separator + "IGlobal.class");
        File listener = new File(classes, "listener" + File.separator + "SystemSetListener.class");
        File softReg = new File(classes, "action" + File.separator + "SoftRegAction.class");

        if (!classFileContainsAll(xmlUtil, "systemConfig.yml", "global.system.VersionID",
                "/data/config.xml", "global/IStatic", "_SYS_CODE")) return null;
        if (!classFileContainsAll(iGlobal, "SYS_PRODUCT_CODE", family)) return null;
        if (!classFileContainsAll(softReg, family, "itmc/regedit/RegisterMain", "doRegistry")) return null;
        if (!classFileContainsAll(listener, family, "itmc/regedit/RegisterMain",
                "checkReInfo", "getRegInfo", "regStr", "global/IStatic", "_SYS_CODE", "contains")) return null;
        return concrete;
    }

'''
assert anchor in plan, 'method insertion anchor missing'
plan = plan.replace(anchor, method + anchor, 1)
plan_path.write_text(plan, encoding='utf-8')

test = test_path.read_text(encoding='utf-8')
old = '''        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.regStr == null
                        && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 accepts target-binary family evidence and defers RegStr to target component");
'''
new = old + '''
        Path ds2406XmlUtil = ds2406Root.resolve("WEB-INF/classes/util/IXmlUtil.class");
        Path ds2406IGlobal = ds2406Root.resolve("WEB-INF/classes/global/IGlobal.class");
        Path ds2406Listener = ds2406Root.resolve("WEB-INF/classes/listener/SystemSetListener.class");
        Path ds2406SoftReg = ds2406Root.resolve("WEB-INF/classes/action/SoftRegAction.class");
        Files.createDirectories(ds2406XmlUtil.getParent());
        Files.createDirectories(ds2406IGlobal.getParent());
        Files.createDirectories(ds2406Listener.getParent());
        Files.createDirectories(ds2406SoftReg.getParent());
        Files.write(ds2406XmlUtil,
                "systemConfig.yml global.system.VersionID /data/config.xml global/IStatic _SYS_CODE"
                        .getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406IGlobal,
                "SYS_PRODUCT_CODE DS24".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406SoftReg,
                "DS24 itmc/regedit/RegisterMain doRegistry".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(ds2406Listener,
                "DS24 itmc/regedit/RegisterMain checkReInfo getRegInfo regStr global/IStatic _SYS_CODE"
                        .getBytes(StandardCharsets.ISO_8859_1));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check(ds2406Plan.regStr == null && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 incomplete startup membership evidence still defers RegStr dynamically");
        Files.write(ds2406Listener,
                "DS24 itmc/regedit/RegisterMain checkReInfo getRegInfo regStr global/IStatic _SYS_CODE contains"
                        .getBytes(StandardCharsets.ISO_8859_1));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && "DS2406".equals(ds2406Plan.regStr)
                        && ds2406Plan.automaticRecoveryReady
                        && !ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 primary RegStr is static only after target VersionID->_SYS_CODE->RegStr.contains flow is complete");
'''
assert old in test, 'DS2406 test anchor missing'
test = test.replace(old, new, 1)
test_path.write_text(test, encoding='utf-8')

version_path.write_text('1.2.23\n', encoding='utf-8')
readme = readme_path.read_text(encoding='utf-8')
assert '当前稳定版本：**v1.2.22**' in readme, 'README stable version anchor missing'
readme = readme.replace('当前稳定版本：**v1.2.22**', '当前稳定版本：**v1.2.23**', 1)
readme_path.write_text(readme, encoding='utf-8')

notes_path.write_text('''# LicenseRecover v1.2.23

本版根据真实 DS2406 目标目录补全 Java data-config 代的主 RegStr 静态证据链，进一步减少不必要的目标组件动态读取。

- **DS24/data-config 主 RegStr 可纯静态确认**：只有目标目录同时证明 `systemConfig.yml VersionID = data/config.xml SoftVersionID`、`IXmlUtil` 将 `global.system.VersionID` 写入 `IStatic._SYS_CODE`、`IGlobal.SYS_PRODUCT_CODE` 与本地注册页使用同一授权族，并且启动 `SystemSetListener` 通过 `RegisterMain(family).getRegInfo().regStr.contains(_SYS_CODE)` 校验当前产品时，才把当前 VersionID 作为主 RegStr。
- **真实 DS2406 结果**：扫描可静态得到 `AuthorizationFamily=DS24 / RuntimeProductID=DS24 / RegStr=DS2406`，不再显示“目标组件动态读取”。
- **Fail-closed 保持**：仅有 `DS24` 家族字符串、或缺失 `_SYS_CODE` 来源、`doRegistry` 本地注册链、`contains(_SYS_CODE)` 任一关键证据时，都不会静态采用 `DS2406`；仍回退目标组件只读获取或阻止自动恢复。
- **兼容授权不覆盖主身份**：目标目录中的其他 QT04 等兼容/附加授权关系不用于覆盖 DS2406 当前应用的主 RegStr。
- **PRE-BLOCK FIRST 保持**：首次 Patch 调用目标注册代码前的 Java/.NET 网络隔离逻辑不变。
''', encoding='utf-8')
