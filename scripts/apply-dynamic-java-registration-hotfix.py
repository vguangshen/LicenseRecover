#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected one match, got {count}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')
    print('patched', path)


# 1) Read the runtime product and RegStr from the target application's own classes.
replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '''        String upper = soft == null ? "" : soft.toUpperCase(Locale.ROOT);\n        String confirmedClassesFamily = confirmedClassesAuthorizationFamily(root, soft);\n        String generation;''',
    '''        String upper = soft == null ? "" : soft.toUpperCase(Locale.ROOT);\n        LegacyJavaRegistrationMetadata.Mapping directoryMapping =\n                LegacyJavaRegistrationMetadata.inspect(root, soft);\n        boolean requiresDirectoryMapping =\n                LegacyJavaRegistrationMetadata.requiresDirectoryMapping(root, soft);\n        String confirmedClassesFamily = confirmedClassesAuthorizationFamily(root, soft);\n        String generation;''')

replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '''        String family = !blank(confirmedClassesFamily)\n                ? confirmedClassesFamily : authorizationFamilyFor(soft, newStyle, classesConfig.isFile());\n        String runtimeProduct = !blank(confirmedClassesFamily)\n                ? confirmedClassesFamily : (newStyle && !blank(soft) ? soft.trim() : runtimeProductFor(soft));\n        File jar = findRegJar(lib);\n        boolean packed = jar != null && isVirboxPackedJar(jar);\n        String products = resolveRegStr(root, soft, runtimeProduct, lib);\n        boolean ready = true;\n        String readiness = "可安全自动恢复";\n        if (blank(family) || "未确认".equals(family)) {''',
    '''        if (directoryMapping != null) generation = "经典 Java / 应用目录注册映射";\n        String family = directoryMapping != null ? directoryMapping.productMain\n                : (!blank(confirmedClassesFamily)\n                ? confirmedClassesFamily : authorizationFamilyFor(soft, newStyle, classesConfig.isFile()));\n        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain\n                : (!blank(confirmedClassesFamily)\n                ? confirmedClassesFamily : (newStyle && !blank(soft) ? soft.trim() : runtimeProductFor(soft)));\n        File jar = findRegJar(lib);\n        boolean packed = jar != null && isVirboxPackedJar(jar);\n        String products = directoryMapping != null ? directoryMapping.productMainNum\n                : resolveRegStr(root, soft, runtimeProduct, lib);\n        boolean ready = true;\n        String readiness = "可安全自动恢复";\n        if (requiresDirectoryMapping && directoryMapping == null) {\n            ready = false;\n            readiness = "目标软件目录未解析出注册ID映射";\n        } else if (blank(family) || "未确认".equals(family)) {''')

replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '''        if (new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml").isFile())\n            return null;\n        return FALLBACK_ALL_NUMS;''',
    '''        if (new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml").isFile())\n            return null;\n        if (LegacyJavaRegistrationMetadata.requiresDirectoryMapping(root, softId)) return null;\n        return FALLBACK_ALL_NUMS;''')

# 2) Core CLI: use the same target-derived mapping before any fixed compatibility fallback.
replace_once(
    'src/main/java/LicenseRecover.java',
    '''        // 自动识别产品号\n        String softId = readSoftId(appRoot);\n        if (productOverride == null || productOverride.trim().isEmpty()) {\n            if (softId != null && !softId.trim().isEmpty()) {\n                if (newStyle) {''',
    '''        // 自动识别产品号：经典平台优先从目标软件自己的 Global/RegisterUtil 读取，禁止猜测。\n        String softId = readSoftId(appRoot);\n        LegacyJavaRegistrationMetadata.Mapping directoryMapping =\n                LegacyJavaRegistrationMetadata.inspect(new File(appRoot), softId);\n        boolean requiresDirectoryMapping =\n                LegacyJavaRegistrationMetadata.requiresDirectoryMapping(new File(appRoot), softId);\n        if ((productOverride == null || productOverride.trim().isEmpty())\n                && requiresDirectoryMapping && directoryMapping == null) {\n            System.err.println("自动恢复已阻止：目标软件目录未解析出注册ID映射，不会使用固定 QT1001/QT04 兜底。");\n            System.out.println("RESULT: FAILED");\n            return 2;\n        }\n        if (productOverride == null || productOverride.trim().isEmpty()) {\n            if (softId != null && !softId.trim().isEmpty()) {\n                if (directoryMapping != null) {\n                    productMain = directoryMapping.productMain;\n                    System.out.println("[识别] 目标软件目录 VersionID = " + softId\n                            + "  -> 运行注册ID " + productMain\n                            + "  授权项 " + directoryMapping.productMainNum\n                            + "  来源=" + directoryMapping.source);\n                } else if (newStyle) {''')

replace_once(
    'src/main/java/LicenseRecover.java',
    '''        String classes = normalizeCsv(readJavaConfigElement(\n                new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml"), "regInfo"));\n        if (classes != null) return classes;\n\n        String runtimeProduct = productMainFor(softId);''',
    '''        String classes = normalizeCsv(readJavaConfigElement(\n                new File(root, "WEB-INF" + File.separator + "classes" + File.separator + "config.xml"), "regInfo"));\n        if (classes != null) return classes;\n\n        LegacyJavaRegistrationMetadata.Mapping directoryMapping =\n                LegacyJavaRegistrationMetadata.inspect(root, softId);\n        if (directoryMapping != null) return directoryMapping.productMainNum;\n        if (LegacyJavaRegistrationMetadata.requiresDirectoryMapping(root, softId)) return null;\n\n        String runtimeProduct = productMainFor(softId);''')

replace_once(
    'src/main/java/LicenseRecover.java',
    '''                boolean dataStyle = isNewStyleApp(appArg);\n                registerPid = localRegisterProductFor(softId, dataStyle);\n                System.out.println("[识别] VersionID = " + softId + "  -> 本地注册产品族 " + registerPid);''',
    '''                boolean dataStyle = isNewStyleApp(appArg);\n                LegacyJavaRegistrationMetadata.Mapping directoryMapping =\n                        LegacyJavaRegistrationMetadata.inspect(new File(appArg), softId);\n                if (directoryMapping != null) {\n                    registerPid = directoryMapping.productMain;\n                    System.out.println("[识别] VersionID = " + softId + "  -> 本地注册产品族 "\n                            + registerPid + "（来源=" + directoryMapping.source + "）");\n                } else if (LegacyJavaRegistrationMetadata.requiresDirectoryMapping(new File(appArg), softId)) {\n                    System.err.println("[错误] 目标软件目录未解析出注册ID映射，已阻止生成授权码。");\n                    System.out.println("RESULT: FAILED");\n                    return 2;\n                } else {\n                    registerPid = localRegisterProductFor(softId, dataStyle);\n                    System.out.println("[识别] VersionID = " + softId + "  -> 本地注册产品族 " + registerPid);\n                }''')

# 3) AutoRecovery child JVM must run the freshly compiled core from the overlay before the tracked legacy jar.
replace_once(
    'src/main/java/LicenseRecoverModernGUIAutoRecovery.java',
    '''        List<String> cmd=new ArrayList<String>();\n        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");\n        cmd.add(d.runtimeDir.getAbsolutePath()+File.separator+"*"+File.pathSeparator+cli.getAbsolutePath());\n        cmd.add("LicenseRecover"); cmd.add(d.appRoot.getAbsolutePath());''',
    '''        List<String> cmd=new ArrayList<String>();\n        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");\n        File overlay = new File(toolDir(), "LicenseRecoverOverlay.jar");\n        String childCp = d.runtimeDir.getAbsolutePath()+File.separator+"*";\n        if (overlay.isFile()) childCp += File.pathSeparator + overlay.getAbsolutePath();\n        childCp += File.pathSeparator + cli.getAbsolutePath();\n        cmd.add(childCp);\n        cmd.add("LicenseRecover"); cmd.add(d.appRoot.getAbsolutePath());''')

# 4) Single-app button must use the same one-click safety coordinator as batch mode.
replace_once(
    'src/main/java/LicenseRecoverModernGUI.java',
    '''    private OperationResult runWay1For(AppInfo info) {\n        File cli = cliJar();''',
    '''    private OperationResult runWay1For(AppInfo info) {\n        if (info.type == AppInfo.Type.JAVA) {\n            boolean preview = dryRunCheck.isSelected();\n            LicenseRecoverModernGUIAutoRecovery.Result result =\n                    LicenseRecoverModernGUIAutoRecovery.recover(info.appRoot, backupCheck.isSelected(),\n                            blockNetCheck.isSelected(), preview, this::appendLog);\n            if (!result.success) return OperationResult.failed(result.message, 1);\n            return preview ? OperationResult.preview(result.message) : OperationResult.success(result.message);\n        }\n        File cli = cliJar();''')

# 5) Package the new target reader and updated core CLI into the runtime overlay.
replace_once(
    'scripts/verify.ps1',
    '''    'ExistingLocalRegStrProbe',\n    'AppInfo',''',
    '''    'ExistingLocalRegStrProbe',\n    'LegacyJavaRegistrationMetadata',\n    'AppInfo',''')

replace_once(
    'scripts/verify.ps1',
    '''foreach ($prefix in $classPrefixes) {\n    $matches = @(Get-ChildItem -Path (Join-Path $verifyDir ($prefix + '*.class')) -File)\n    if ($matches.Count -eq 0) {\n        throw "No compiled classes found for overlay prefix: $prefix"\n    }\n    foreach ($match in $matches) {\n        Copy-Item -LiteralPath $match.FullName -Destination $overlayDir -Force\n    }\n}\n\n$fixedTime''',
    '''foreach ($prefix in $classPrefixes) {\n    $matches = @(Get-ChildItem -Path (Join-Path $verifyDir ($prefix + '*.class')) -File)\n    if ($matches.Count -eq 0) {\n        throw "No compiled classes found for overlay prefix: $prefix"\n    }\n    foreach ($match in $matches) {\n        Copy-Item -LiteralPath $match.FullName -Destination $overlayDir -Force\n    }\n}\n$coreMatches = @(Get-ChildItem -LiteralPath $verifyDir -Filter 'LicenseRecover*.class' -File |\n    Where-Object { $_.Name -eq 'LicenseRecover.class' -or $_.Name.StartsWith('LicenseRecover$') })\nif ($coreMatches.Count -eq 0) {\n    throw 'No compiled LicenseRecover core classes found for overlay.'\n}\nforeach ($match in $coreMatches) {\n    Copy-Item -LiteralPath $match.FullName -Destination $overlayDir -Force\n}\n\n$fixedTime''')

replace_once(
    'scripts/verify.ps1',
    '''if ($overlayEntries -notcontains 'ExistingLocalRegStrProbe.class') {\n    throw 'Overlay is missing ExistingLocalRegStrProbe.class.'\n}\n''',
    '''if ($overlayEntries -notcontains 'ExistingLocalRegStrProbe.class') {\n    throw 'Overlay is missing ExistingLocalRegStrProbe.class.'\n}\nif ($overlayEntries -notcontains 'LegacyJavaRegistrationMetadata.class') {\n    throw 'Overlay is missing LegacyJavaRegistrationMetadata.class.'\n}\nif ($overlayEntries -notcontains 'LicenseRecover.class') {\n    throw 'Overlay is missing updated LicenseRecover.class.'\n}\n''')

# 6) Regression: the family prefix is selected from target-owned constants, not a built-in YT00138 rule.
replace_once(
    'src/test/java/RefactorSmokeTest.java',
    '''        check("fwq".equals(LicenseRecover.LOCAL_AUTH_USER_ID),\n                "local authorization UserID fixed to fwq");\n''',
    '''        check("fwq".equals(LicenseRecover.LOCAL_AUTH_USER_ID),\n                "local authorization UserID fixed to fwq");\n        check("YT001".equals(LegacyJavaRegistrationMetadata.selectFallbackPrefix(\n                        Arrays.asList("QT1001", "DS26", "YT001", "YT00129"), "YT00138")),\n                "legacy runtime family is derived from target RegisterUtil constants");\n''')

print('dynamic Java registration hotfix applied')
