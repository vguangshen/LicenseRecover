from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit('pattern not found in %s: %r' % (path, old[:120]))
    text = text.replace(old, new, 1)
    p.write_text(text, encoding='utf-8', newline='\n')


# 1) Core Java recovery: DS28xx uses vendor ProName DS28 and must authorize the active DS28xx product id.
replace_once(
    'src/main/java/LicenseRecover.java',
    '        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";\n        return ALL_NUMS;',
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n'
    '        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";\n'
    '        return ALL_NUMS;')
replace_once(
    'src/main/java/LicenseRecover.java',
    '    static String productMainFor(String softId) {\n        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("XMT01")) return "XMT01";',
    '    static String productMainFor(String softId) {\n'
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).matches("DS28\\\\d{2}")) return "DS28";\n'
    '        if (softId != null && softId.toUpperCase(java.util.Locale.ROOT).startsWith("XMT01")) return "XMT01";')

# 2) GUI Java plan: keep preview aligned with the core, while staying file-only / classpath-independent.
replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '        } else if (dataConfig.isFile()) {\n            generation = "YT/兼容根配置 / data-config";',
    '        } else if (dataConfig.isFile()) {\n'
    '            generation = soft != null && soft.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")\n'
    '                    ? "DS28 / data-config" : "YT/兼容根配置 / data-config";')
replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";\n        return FALLBACK_ALL_NUMS;',
    '        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return softId.trim();\n'
    '        if ("YT00129".equalsIgnoreCase(softId)) return "QT0420";\n'
    '        return FALLBACK_ALL_NUMS;')
replace_once(
    'src/main/java/LicenseRecoverModernGUIJavaPlan.java',
    '    private static String productMainFor(String softId) {\n        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("XMT01")) return "XMT01";',
    '    private static String productMainFor(String softId) {\n'
    '        if (softId != null && softId.toUpperCase(Locale.ROOT).matches("DS28\\\\d{2}")) return "DS28";\n'
    '        if (softId != null && softId.toUpperCase(Locale.ROOT).startsWith("XMT01")) return "XMT01";')

# 3) Batch scan: isolate each child so one malformed/novel app can never abort the remaining rows.
p = Path('src/main/java/LicenseRecoverModernGUI.java')
text = p.read_text(encoding='utf-8')
start = text.index('    private void scanBatch() {')
end = text.index('\n    private void runBatch()', start)
new_method = r'''    private void scanBatch() {
        batchTargets.clear();
        batchModel.setRowCount(0);
        File parent = new File(batchRootField.getText().trim());
        File[] children = parent.isDirectory() ? parent.listFiles(File::isDirectory) : null;
        if (children == null) {
            setStatus("批量父目录无效", false);
            return;
        }
        Arrays.sort(children);
        int detected = 0;
        int skipped = 0;
        int errors = 0;
        for (File child : children) {
            try {
                AppInfo info = AppDetector.detect(child);
                BatchTarget target;
                if (info.type == AppInfo.Type.JAVA) {
                    target = BatchTarget.javaTarget(child.getName(), info.appRoot, info.libDir);
                    LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(info.appRoot);
                    batchModel.addRow(new Object[]{child.getName(),
                            plan.detected ? "Java / " + plan.generation : "Java",
                            valueOrDash(plan.softVersionId), valueOrDash(plan.productName),
                            plan.regStrSummary(), plan.configTargets,
                            plan.detected ? "待执行: RegisterMain" : "待识别", "待处理",
                            info.appRoot.getAbsolutePath()});
                    detected++;
                } else if (info.type == AppInfo.Type.DOTNET) {
                    target = BatchTarget.dotNetTarget(child.getName(), info.binDir);
                    LicenseRecoverModernGUIAutoRecovery.Detection d =
                            LicenseRecoverModernGUIAutoRecovery.detect(info.binDir);
                    String kind = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                            ? ".NET Modern" : ".NET Legacy";
                    String verify = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                            ? "待执行: 写回解密校验" : "兼容方式一: 不适用";
                    batchModel.addRow(new Object[]{child.getName(), kind,
                            valueOrDash(d.versionId), valueOrDash(d.productName), "—", "config.xml",
                            verify, "待处理", info.binDir.getAbsolutePath()});
                    detected++;
                } else {
                    target = BatchTarget.skipped(child.getName());
                    batchModel.addRow(new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "未识别", child.getAbsolutePath()});
                    skipped++;
                }
                batchTargets.add(target);
            } catch (Throwable ex) {
                errors++;
                batchTargets.add(BatchTarget.skipped(child.getName()));
                String msg = ex.getMessage();
                String reason = ex.getClass().getSimpleName() + (msg == null || msg.trim().isEmpty() ? "" : ": " + msg.trim());
                batchModel.addRow(new Object[]{child.getName(), "检测异常", "—", "—", "—", "—", "—",
                        reason, child.getAbsolutePath()});
                appendLog("[批量扫描] " + child.getName() + " 检测异常: " + reason + "\n");
            }
        }
        batchProgress.setMinimum(0);
        batchProgress.setMaximum(batchTargets.size());
        batchProgress.setValue(0);
        batchProgress.setString("扫描完成：" + batchTargets.size() + " 个目录；识别 " + detected
                + "，未识别 " + skipped + "，异常 " + errors);
        setStatus(errors == 0 ? "批量扫描完成" : "批量扫描完成（存在检测异常）", errors == 0);
    }
'''
text = text[:start] + new_method + text[end:]
p.write_text(text, encoding='utf-8', newline='\n')

print('Applied DS28 mapping and batch scan isolation.')
