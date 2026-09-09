#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected one match, found {count}: {old[:160]!r}")
    write(rel, text.replace(old, new, 1))


version = read("VERSION.txt").strip()
if version == "1.2.29":
    print("v1.2.29 already applied; nothing to do.")
    raise SystemExit(0)
if version != "1.2.28":
    raise SystemExit(f"Expected VERSION.txt=1.2.28, found {version!r}")

# The current one-click failure uses JOptionPane with the entire native error on one
# line. Real GENCODE failures can be very long, which makes the dialog nearly as
# wide as the screen and hides the useful stage/product/exit information. Replace
# it with a structured, scrollable Java-8-compatible diagnostic dialog.
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    "import java.awt.*;\nimport java.io.File;\nimport java.util.ArrayList;",
    "import java.awt.*;\nimport java.awt.datatransfer.StringSelection;\nimport java.io.File;\nimport java.text.SimpleDateFormat;\nimport java.util.ArrayList;"
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    "import java.util.Arrays;\nimport java.util.List;",
    "import java.util.Arrays;\nimport java.util.Date;\nimport java.util.List;\nimport java.util.regex.Matcher;\nimport java.util.regex.Pattern;"
)

anchor = '''    private static String value(String text) {\n        return text == null || text.trim().isEmpty() ? "—" : text;\n    }\n\n'''
addition = anchor + r'''    static final class OneClickFailurePresentation {
        final String stage;
        final Integer exitCode;
        final String product;
        final String summary;
        final String details;

        OneClickFailurePresentation(String stage, Integer exitCode, String product,
                                    String summary, String details) {
            this.stage = stage;
            this.exitCode = exitCode;
            this.product = product;
            this.summary = summary;
            this.details = details;
        }
    }

    static OneClickFailurePresentation describeOneClickFailure(String message) {
        String raw = message == null ? "" : message.trim();
        Matcher stageMatch = Pattern.compile("^\\s*\\[([A-Z_]+)\\]").matcher(raw);
        String stage = stageMatch.find() ? stageMatch.group(1) : "UNKNOWN";

        Integer exit = null;
        Matcher exitMatch = Pattern.compile("(?i)\\bexit\\s*=\\s*(-?\\d+)").matcher(raw);
        if (exitMatch.find()) {
            try { exit = Integer.valueOf(exitMatch.group(1)); }
            catch (NumberFormatException ignore) { }
        }

        String nativeOutput = raw;
        int outputAt = raw.indexOf("output=");
        if (outputAt >= 0) nativeOutput = raw.substring(outputAt + "output=".length()).trim();
        String details = formatOneClickFailureDetails(nativeOutput);

        String product = null;
        Matcher productMatch = Pattern.compile("(?i)产品号\\s*[:：]\\s*([A-Z0-9._-]+)").matcher(details);
        if (productMatch.find()) product = productMatch.group(1);

        String summary;
        if ("GENCODE".equals(stage)) {
            summary = "生成离线授权码失败，目标注册组件在生成申请号 / 授权码时抛出异常。";
        } else if ("GENCODE_PARSE".equals(stage)) {
            summary = "生成结果解析失败：辅助程序已返回，但没有读取到完整的申请号或授权码。";
        } else if ("DOREG".equals(stage)) {
            summary = "本地注册提交失败，目标 DoRegistry() 没有接受本次授权数据。";
        } else if ("VERIFY".equals(stage)) {
            summary = "写入后的原生校验失败，目标 CheckReInfo() 没有确认授权状态。";
        } else if ("PRE_BLOCK".equals(stage)) {
            summary = "安全隔离未建立。为避免触发联网授权，工具已停止调用目标注册组件。";
        } else if ("HELPER".equals(stage)) {
            summary = "所需的 .NET 注册辅助程序缺失或无法启动。";
        } else if ("CHAIN".equals(stage)) {
            summary = "无法确认目标程序实际使用的 .NET 注册链。";
        } else {
            summary = "一键恢复在执行过程中失败，请查看下方详细信息。";
        }
        return new OneClickFailurePresentation(stage, exit, product, summary, details);
    }

    static String formatOneClickFailureDetails(String nativeOutput) {
        String text = nativeOutput == null ? "" : nativeOutput.replace('\r', ' ').trim();
        if (text.isEmpty()) return "没有返回更多错误信息。";
        text = text.replaceAll("={8,}", "\\n");
        text = text.replaceAll("\\s+(产品号\\s*[:：])", "\\n$1");
        text = text.replaceAll("\\s+(\\[错误\\])", "\\n$1");
        text = text.replaceAll("\\s+(RESULT\\s*[:：])", "\\n$1");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n[ \\t]+", "\\n");
        text = text.replaceAll("\\n{3,}", "\\n\\n");
        return text.trim();
    }

    private static String oneClickFailureClipboardText(
            LicenseRecoverModernGUIAutoRecovery.Result result,
            OneClickFailurePresentation p) {
        StringBuilder b = new StringBuilder();
        b.append("LicenseRecover 一键恢复错误\\n");
        if (result != null && result.detection != null && result.detection.versionId != null)
            b.append("应用: ").append(result.detection.versionId).append('\\n');
        b.append("阶段: ").append(p.stage).append('\\n');
        if (p.product != null) b.append("注册产品: ").append(p.product).append('\\n');
        if (p.exitCode != null) b.append("退出码: ").append(p.exitCode).append('\\n');
        b.append("摘要: ").append(p.summary).append("\\n\\n详细信息:\\n").append(p.details);
        return b.toString();
    }

    private static File currentPersistentLogFile() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return new File(new File(toolDir(), "logs"), "LicenseRecoverGUI-" + day + ".log");
    }

    private static void showOneClickFailureDialog(
            final JFrame frame, final LicenseRecoverModernGUIAutoRecovery.Result result) {
        final OneClickFailurePresentation p = describeOneClickFailure(result == null ? null : result.message);
        final JDialog dialog = new JDialog(frame, "一键恢复未完成", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 0));

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        Icon warningIcon = UIManager.getIcon("OptionPane.warningIcon");
        if (warningIcon != null) heading.add(new JLabel(warningIcon), BorderLayout.WEST);
        JPanel headingText = new JPanel();
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        String stageTitle = "GENCODE".equals(p.stage) ? "生成离线授权码失败" : "一键恢复失败";
        JLabel title = new JLabel(stageTitle + "（" + p.stage + "）");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JTextArea summary = new JTextArea(p.summary);
        summary.setEditable(false);
        summary.setOpaque(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setFont(UIManager.getFont("Label.font"));
        summary.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        headingText.add(title);
        headingText.add(summary);
        heading.add(headingText, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        JPanel info = new JPanel(new GridBagLayout());
        info.setBorder(BorderFactory.createTitledBorder("错误信息"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0; c.gridx = 0; info.add(new JLabel("应用:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        String app = result != null && result.detection != null ? value(result.detection.versionId) : "—";
        info.add(new JLabel(app), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("阶段:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.stage), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("注册产品:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.product == null ? "—" : p.product), c);
        c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; info.add(new JLabel("退出码:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; info.add(new JLabel(p.exitCode == null ? "—" : String.valueOf(p.exitCode)), c);
        center.add(info, BorderLayout.NORTH);

        JTextArea details = new JTextArea(p.details, 9, 72);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, details.getFont().getSize()));
        details.setFont(mono);
        JScrollPane scroll = new JScrollPane(details);
        scroll.setBorder(BorderFactory.createTitledBorder("详细信息"));
        center.add(scroll, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton copy = new JButton("复制详情");
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(oneClickFailureClipboardText(result, p)), null);
                copy.setText("已复制");
            } catch (Exception ex) {
                copy.setText("复制失败");
            }
        });
        final File logFile = currentPersistentLogFile();
        JButton openLog = new JButton("打开日志");
        openLog.setEnabled(logFile.isFile() && Desktop.isDesktopSupported());
        openLog.addActionListener(e -> {
            try { Desktop.getDesktop().open(logFile); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "无法打开日志文件：" + value(ex.getMessage()),
                        "打开日志", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dialog.dispose());
        buttons.add(copy);
        buttons.add(openLog);
        buttons.add(close);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setSize(Math.max(760, dialog.getWidth()), Math.max(500, dialog.getHeight()));
        dialog.setMinimumSize(new Dimension(680, 430));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

'''
replace_once("src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java", anchor, addition)

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''                    } else {\n                        JOptionPane.showMessageDialog(frame, result.message,\n                                "一键恢复未完成", JOptionPane.WARNING_MESSAGE);\n                    }\n                } catch (Exception ex) {\n                    verifyValue.setText("FAILED: " + value(ex.getMessage()));\n                    JOptionPane.showMessageDialog(frame, "一键恢复失败：" + ex.getMessage(),\n                            "一键恢复授权", JOptionPane.ERROR_MESSAGE);\n                }''',
    '''                    } else {\n                        showOneClickFailureDialog(frame, result);\n                    }\n                } catch (Exception ex) {\n                    verifyValue.setText("FAILED: " + value(ex.getMessage()));\n                    LicenseRecoverModernGUIAutoRecovery.Detection d =\n                            LicenseRecoverModernGUIAutoRecovery.detect(selected);\n                    LicenseRecoverModernGUIAutoRecovery.Result failure =\n                            LicenseRecoverModernGUIAutoRecovery.Result.fail(\n                                    "[UNKNOWN] 一键恢复失败; output=" + value(ex.getMessage()), d);\n                    showOneClickFailureDialog(frame, failure);\n                }'''
)

# Add parser/formatting regression coverage for the exact long GENCODE dialog shape
# reported from the real Windows UI.
test_anchor = '''        String nativeOut = "注册申请号     : A1B2C3D4\\n离线授权码     : 001122AABB\\n";\n        check("A1B2C3D4".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "注册申请号")),\n                "native .NET one-click parses target request code");\n        check("001122AABB".equals(LicenseRecoverModernGUIAutoRecovery.findLabeledHex(nativeOut, "离线授权码")),\n                "native .NET one-click parses target authorization code");\n'''
test_add = test_anchor + '''\n        LicenseRecoverModernGUIUiPatchLauncher.OneClickFailurePresentation failureUi =\n                LicenseRecoverModernGUIUiPatchLauncher.describeOneClickFailure(\n                        "[GENCODE] target-native request-code generation failed; exit=1; output="\n                                + "================ ITMC .NET 版 - 方式二：生成离线授权码 ================"\n                                + " 产品号: YX0302 [错误] 生成失败: 调用的目标发生了异常。 RESULT: FAILED");\n        check("GENCODE".equals(failureUi.stage)\n                        && Integer.valueOf(1).equals(failureUi.exitCode)\n                        && "YX0302".equals(failureUi.product),\n                "one-click failure dialog extracts stage/exit/product from native GENCODE failure");\n        check(failureUi.summary.contains("生成离线授权码失败")\n                        && failureUi.details.contains("\\n产品号:")\n                        && failureUi.details.contains("\\n[错误]")\n                        && failureUi.details.contains("\\nRESULT:"),\n                "one-click failure dialog converts long native output into readable multiline details");\n'''
replace_once("src/test/java/RefactorSmokeTest.java", test_anchor, test_add)

# CI should fail if the structured failure dialog is removed and the old one-line
# JOptionPane regression returns.
verify_anchor = "if (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }"
verify_add = verify_anchor + "\n$uiPatchSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUIUiPatchLauncher.java') -Raw\nif (-not $uiPatchSource.Contains('showOneClickFailureDialog')) { throw 'Structured one-click failure dialog is missing.' }\nif (-not $uiPatchSource.Contains('复制详情') -or -not $uiPatchSource.Contains('打开日志')) { throw 'One-click failure dialog diagnostic actions are missing.' }\nif ($uiPatchSource.Contains('JOptionPane.showMessageDialog(frame, result.message')) { throw 'One-click failure still renders the raw native message in a one-line JOptionPane.' }"
replace_once("scripts/verify.ps1", verify_anchor, verify_add)

write("VERSION.txt", "1.2.29\n")
replace_once("README.md", "当前稳定版本：**v1.2.28**", "当前稳定版本：**v1.2.29**")

changelog = '''## [1.2.29] - 2026-09-09\n\n### Changed\n\n- 重做“一键恢复未完成”错误弹窗：不再把 `[GENCODE] ... output=...` 整段原生输出挤在单行 `JOptionPane` 中。\n- 错误窗口现在按“应用 / 阶段 / 注册产品 / 退出码 / 摘要 / 详细信息”分层展示，详细信息支持自动换行和滚动。\n- 增加“复制详情”“打开日志”“关闭”三个操作，方便直接复制诊断内容或打开当天 `logs/LicenseRecoverGUI-YYYYMMDD.log`。\n- 针对 GENCODE / GENCODE_PARSE / DOREG / VERIFY / PRE_BLOCK / HELPER / CHAIN 提供简短中文摘要，同时保留完整原生输出用于排查。\n\n### Regression\n\n- 新增真实 Windows `GENCODE exit=1 / 产品号 YX0302 / 调用的目标发生了异常` 长消息解析测试，确保阶段、退出码、产品号和多行详情都能稳定提取。\n- CI 明确禁止恢复为直接 `JOptionPane.showMessageDialog(frame, result.message, ...)` 的单行错误弹窗。\n\n'''
write("CHANGELOG.md", changelog + read("CHANGELOG.md"))

release_notes = '''# LicenseRecover v1.2.29\n\n本版集中优化一键恢复失败时的诊断弹窗。此前 `.NET` 原生注册链一旦返回较长错误（例如 `GENCODE exit=1`），GUI 会把整个 `[GENCODE] ... output=...` 文本塞进一个单行 `JOptionPane`，窗口会被拉得非常宽，真正有用的错误阶段和产品信息反而难以阅读。\n\n## 改进\n\n- 新的错误弹窗分层展示：**应用、失败阶段、注册产品、退出码、中文摘要**。\n- 原生输出进入独立的“详细信息”滚动区域，并把长分隔线、`产品号`、`[错误]`、`RESULT` 自动整理成多行。\n- 新增 **复制详情**，一次复制完整诊断文本。\n- 新增 **打开日志**，直接打开当天 `logs/LicenseRecoverGUI-YYYYMMDD.log`。\n- `GENCODE / GENCODE_PARSE / DOREG / VERIFY / PRE_BLOCK / HELPER / CHAIN` 都有对应的简短中文摘要；完整原始信息仍保留。\n\n本版只改进诊断与交互，不改变 v1.2.28 的 `.NET` 小写优先 / 大写回退注册链选择逻辑。\n'''
write("release-notes/v1.2.29.md", release_notes)

print("v1.2.29 staged successfully")
