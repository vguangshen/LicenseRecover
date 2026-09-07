from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


core = "src/main/java/LicenseRecover.java"
replace_once(core,
    '        if ("QT100101".equals(id)) return "QT100101";\n        if (id.startsWith("DS501")) return "DS501";',
    '        if ("QT100101".equals(id)) return "QT100101";\n        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501")) return "DS501";')
replace_once(core,
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();',
    '        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();')
replace_once(core,
    '        if (softId != null && "QT100101".equalsIgnoreCase(softId.trim())) return "QT100101";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();',
    '        if (softId != null && "QT100101".equalsIgnoreCase(softId.trim())) return "QT100101";\n        if (softId != null && "DS2406".equalsIgnoreCase(softId.trim())) return "DS24";\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("DS501")) return softId.trim();')
replace_once(core,
    '        File libBak = null;\n        if (backupCfg && cfg.exists()) {',
    '        File libBak = null;\n        File rootBak = null;\n        if (backupCfg && cfg.exists()) {')
replace_once(core,
    '                File bak = new File(appRoot, "config.xml." + stamp + ".bak");\n                try { Files.copy(cfgRoot.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING); }\n                catch (Exception e) { System.out.println("[警告] 备份失败: " + e.getMessage()); }\n                System.out.println("已备份原配置(webapp根): " + bak.getName());',
    '                rootBak = new File(appRoot, "config.xml." + stamp + ".bak");\n                try { Files.copy(cfgRoot.toPath(), rootBak.toPath(), StandardCopyOption.REPLACE_EXISTING); }\n                catch (Exception e) { System.out.println("[警告] 备份失败: " + e.getMessage()); rootBak = null; }\n                if (rootBak != null) System.out.println("已备份原配置(webapp根): " + rootBak.getName());')
replace_once(core,
    '            } catch (Exception e) {\n                // 单个目录校验异常不阻断另一个目录（新架构有 libDir + appRoot 两处）\n                System.err.println("自校验异常(" + cd + "): " + e.getMessage() + "（继续尝试其它目录）");\n            }',
    '            } catch (Throwable e) {\n                // LinkageError/NoClassDefFoundError 也属于校验器不可用，不能让整个子 JVM 直接崩掉。\n                // 但也绝不能把它当作授权成功；若所有目录都未通过，下面会回滚本次写入。\n                String detail = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());\n                System.err.println("自校验异常(" + cd + "): " + detail + "（继续尝试其它目录）");\n            }')
replace_once(core,
    '        System.out.println("RESULT: FAILED —— 授权未生效，请把输出发给我排查。");\n        return 1;',
    '        if (backupCfg) {\n            if (libBak != null && libBak.exists()) {\n                try { Files.copy(libBak.toPath(), cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);\n                    System.out.println("[回滚] 原生校验未通过，已恢复 " + cfg.getAbsolutePath()); }\n                catch (Exception e) { System.out.println("[警告] 回滚 lib config.xml 失败: " + e.getMessage()); }\n            }\n            if (rootBak != null && rootBak.exists()) {\n                File rootCfg = new File(appRoot, "config.xml");\n                try { Files.copy(rootBak.toPath(), rootCfg.toPath(), StandardCopyOption.REPLACE_EXISTING);\n                    System.out.println("[回滚] 原生校验未通过，已恢复 " + rootCfg.getAbsolutePath()); }\n                catch (Exception e) { System.out.println("[警告] 回滚 webapp 根 config.xml 失败: " + e.getMessage()); }\n            }\n        }\n        System.out.println("RESULT: FAILED —— 授权未生效；若启用了备份，本次写入已自动回滚。");\n        return 1;')

plan = "src/main/java/LicenseRecoverModernGUIJavaPlan.java"
replace_once(plan,
    '            generation = upper.matches("DS28\\\\d{2}")\n                    ? "DS28 / data-config" : "YT/兼容根配置 / data-config";',
    '            generation = "DS2406".equals(upper)\n                    ? "DS24 / data-config"\n                    : (upper.matches("DS28\\\\d{2}") ? "DS28 / data-config" : "YT/兼容根配置 / data-config");')
replace_once(plan,
    '        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();',
    '        if ("DS2406".equalsIgnoreCase(softId)) return "DS2406";\n        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("DS501")) return softId.trim();\n        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();')
replace_once(plan,
    '        if ("QT100101".equals(id)) return "QT100101";\n        if (id.startsWith("DS501")) return "DS501";',
    '        if ("QT100101".equals(id)) return "QT100101";\n        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501")) return "DS501";')
replace_once(plan,
    '        if ("QT100101".equals(id)) return "QT100101";\n        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();',
    '        if ("QT100101".equals(id)) return "QT100101";\n        if ("DS2406".equals(id)) return "DS24";\n        if (id.startsWith("DS501") || id.startsWith("YX0305")) return softId.trim();')

test = "src/test/java/RefactorSmokeTest.java"
marker = '        Path qt30103Root = base.resolve("java-QT30103");'
block = '''        Path ds2406Root = base.resolve("java-DS2406");
        Path ds2406Lib = ds2406Root.resolve("WEB-INF/lib");
        Files.createDirectories(ds2406Lib);
        Files.createDirectories(ds2406Root.resolve("data"));
        Files.write(ds2406Lib.resolve("ITMCReg-1.0.2.jar"), new byte[]{1});
        Files.write(ds2406Root.resolve("systemConfig.yml"), Arrays.asList(
                "global.system.VersionID=DS2406",
                "global.system.VersionName=跨境电子商务数据分析与应用系统"), StandardCharsets.UTF_8);
        Files.write(ds2406Root.resolve("data/config.xml"), Arrays.asList(
                "<ROOT><SystemSoft><SoftVersionID>DS2406</SoftVersionID></SystemSoft></ROOT>"), StandardCharsets.UTF_8);
        check("DS24".equals(LicenseRecover.productMainFor("DS2406")),
                "DS2406 startup RegisterMain uses DS24 family");
        check("DS24".equals(LicenseRecover.localRegisterProductFor("DS2406", false)),
                "DS2406 local-registration page uses DS24 family");
        check("DS2406".equals(LicenseRecover.resolveJavaRegStr(ds2406Root.toString(), "DS2406")),
                "DS2406 RegStr contains the concrete VersionID required by the application gate");
        LicenseRecoverModernGUIJavaPlan ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check(ds2406Plan.generation.contains("DS24")
                        && "DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId),
                "Java GUI plan models DS2406 as DS24 authorization family");
        check("DS2406".equals(ds2406Plan.regStr) && ds2406Plan.automaticRecoveryReady,
                "DS2406 GUI plan uses sample-verified RegStr and is ready");

'''
replace_once(test, marker, block + marker)

Path("VERSION.txt").write_text("1.2.11\n", encoding="utf-8")
for name in ("README.md", "README.txt"):
    p = Path(name)
    if p.exists():
        text = p.read_text(encoding="utf-8")
        text = text.replace("v1.2.10", "v1.2.11").replace("1.2.10", "1.2.11")
        p.write_text(text, encoding="utf-8")

changelog = Path("CHANGELOG.md")
old = changelog.read_text(encoding="utf-8")
section = '''## v1.2.11 — DS2406 真实授权族与失败回滚修复

- 依据 DS2406 生产包业务代码，将启动/本地注册授权产品号从通用 `QT1001` 修正为 `DS24`。
- DS2406 `RegStr` 使用业务启动门实际要求的具体 `VersionID=DS2406`，不再写入 44 项通用列表。
- Java 原生校验捕获 `LinkageError/NoClassDefFoundError`，避免缺可选 HASP 依赖时子进程直接崩溃。
- 原生校验最终未通过时，默认自动恢复本次写入前的 lib 与 webapp 根 `config.xml` 备份。
- 新增 DS2406 回归测试，覆盖 GUI 识别、运行 ProName、本地注册族与 RegStr。

'''
if "## v1.2.11 " not in old:
    changelog.write_text(section + old, encoding="utf-8")

notes = Path("release-notes/v1.2.11.md")
notes.parent.mkdir(parents=True, exist_ok=True)
notes.write_text('''# LicenseRecover v1.2.11

本版依据真实 DS2406 生产包修正 Java 授权模型，并加强失败回滚。

- DS2406 官方业务代码在启动校验、本地注册和在线注册路径均使用 `RegisterMain("DS24", ...)`。
- DS2406 启动授权门读取 `RegStr` 并要求包含当前 `VersionID=DS2406`，因此恢复模型调整为：授权族/运行校验ID `DS24`，RegStr `DS2406`。
- 移除 DS2406 旧的 `QT1001 + 44 项` fallback。
- ITMCReg 1.0.2 声明 `hasp-srm-api` 依赖；若本地授权无效才会继续跌落到 HASP 路径。校验器现在会捕获缺依赖错误，而不会直接崩溃。
- 如果最终原生校验没有任何一个目标通过，默认从写入前备份恢复配置，避免批量执行留下错误授权。
''', encoding="utf-8")

Path(".github/workflows/one-time-ds2406-v1.2.11.yml").unlink(missing_ok=True)
Path(".github/ds2406-v1.2.11-patch.py").unlink(missing_ok=True)
