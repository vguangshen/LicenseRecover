#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected patch anchor not found in {rel}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")


def insert_after(rel, anchor, addition):
    replace_once(rel, anchor, anchor + addition)


# ---------------------------------------------------------------------------
# AutoRecovery: log the exact Java plan and make batch cancellation terminate
# the child Java process instead of leaving it running in the background.
# ---------------------------------------------------------------------------
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static Result recoverJava(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        File cli=new File(toolDir(),"LicenseRecover.jar");
        if (!cli.isFile()) return Result.fail("LicenseRecover.jar was not found.",d);
        List<String> cmd=new ArrayList<String>();
        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");
        cmd.add(d.runtimeDir.getAbsolutePath()+File.separator+"*"+File.pathSeparator+cli.getAbsolutePath());
        cmd.add("LicenseRecover"); cmd.add(d.appRoot.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run"); if (!backup) cmd.add("--no-backup"); if (!blockNet) cmd.add("--no-block-net");
        int rc=run(cmd,log);
        return new Result(rc==0,rc==0?"Java local authorization recovery completed and native self-check passed.":"Java recovery failed; see log.",d,null,null,null);
    }
''',
    '''    private static Result recoverJava(Detection d, boolean backup, boolean blockNet, boolean dryRun, Consumer<String> log) throws Exception {
        LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(d.appRoot);
        if (plan.detected) log.accept(plan.logSummary());
        File cli=new File(toolDir(),"LicenseRecover.jar");
        if (!cli.isFile()) return Result.fail("LicenseRecover.jar was not found.",d);
        List<String> cmd=new ArrayList<String>();
        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-cp");
        cmd.add(d.runtimeDir.getAbsolutePath()+File.separator+"*"+File.pathSeparator+cli.getAbsolutePath());
        cmd.add("LicenseRecover"); cmd.add(d.appRoot.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run"); if (!backup) cmd.add("--no-backup"); if (!blockNet) cmd.add("--no-block-net");
        int rc=run(cmd,log);
        if (dryRun) log.accept("[java-plan] native verify=PREVIEW (write-back check not executed)\\n");
        else log.accept(rc==0
                ? "[java-plan] native verify=PASS (RegisterMain.checkReInfo())\\n"
                : "[java-plan] native verify=FAILED (see CLI log)\\n");
        return new Result(rc==0,rc==0?"Java local authorization recovery completed and native self-check passed.":"Java recovery failed; see log.",d,null,null,null);
    }
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static int run(List<String>cmd,Consumer<String>log)throws Exception{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));String s;while((s=r.readLine())!=null)log.accept(s+"\\n");return p.waitFor();}
''',
    '''    private static int run(List<String>cmd,Consumer<String>log)throws Exception{
        Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try{
            BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));
            String s;while((s=r.readLine())!=null)log.accept(s+"\\n");
            return p.waitFor();
        }catch(InterruptedException ex){
            p.destroy();
            try{p.waitFor();}catch(InterruptedException again){Thread.currentThread().interrupt();}
            try{if(p.isAlive())p.destroyForcibly();}catch(Throwable ignore){}
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
''')

# ---------------------------------------------------------------------------
# Modern GUI batch tab: show the Java authorization plan in the table, expose
# batch-specific backup/block/preview options, and run the same one-click
# coordinator used by the single-app UI.
# ---------------------------------------------------------------------------
replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''    private final DefaultTableModel batchModel = new DefaultTableModel(
            new String[]{"应用", "类型", "状态", "路径"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable batchTable = new JTable(batchModel);
    private final List<BatchTarget> batchTargets = new ArrayList<BatchTarget>();
    private final JRadioButton batchWay1 = new JRadioButton("方式一：配置 / 本地授权", true);
    private final JRadioButton batchWay3 = new JRadioButton("方式三：移除联网授权代码");
    private final JButton batchScanButton = new JButton("扫描子目录");
    private final JButton batchRunButton = new JButton("批量执行");
    private final JButton batchCancelButton = new JButton("取消");
    private final JProgressBar batchProgress = new JProgressBar();
''',
    '''    private static final int BATCH_COL_VERIFY = 6;
    private static final int BATCH_COL_STATUS = 7;
    private final DefaultTableModel batchModel = new DefaultTableModel(
            new String[]{"应用", "类型 / 授权代际", "SoftVersionID", "ProName", "RegStr", "配置目标", "原生校验", "状态", "路径"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable batchTable = new JTable(batchModel);
    private final List<BatchTarget> batchTargets = new ArrayList<BatchTarget>();
    private final JRadioButton batchWay1 = new JRadioButton("一键恢复授权（推荐）", true);
    private final JRadioButton batchWay3 = new JRadioButton("方式三：移除联网授权代码");
    private final JCheckBox batchBackupCheck = new JCheckBox("写入前备份", true);
    private final JCheckBox batchBlockNetCheck = new JCheckBox("同时阻止残留联网", true);
    private final JCheckBox batchDryRunCheck = new JCheckBox("只预览，不写入", false);
    private final JButton batchScanButton = new JButton("扫描子目录");
    private final JButton batchRunButton = new JButton("批量执行");
    private final JButton batchCancelButton = new JButton("取消");
    private final JProgressBar batchProgress = new JProgressBar();
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''        c.gridx = 3; c.gridwidth = 1; c.weightx = 0;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        batchRunButton.addActionListener(e -> runBatch());
        batchCancelButton.setEnabled(false);
        batchCancelButton.addActionListener(e -> cancelBatch());
        actions.add(batchRunButton);
        actions.add(batchCancelButton);
        top.add(actions, c);
        root.add(top, BorderLayout.NORTH);

        batchTable.setFillsViewportHeight(true);
        batchTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        batchTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        batchTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        batchTable.getColumnModel().getColumn(3).setPreferredWidth(520);
        root.add(new JScrollPane(batchTable), BorderLayout.CENTER);
''',
    '''        c.gridx = 3; c.gridwidth = 1; c.weightx = 0;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        batchRunButton.addActionListener(e -> runBatch());
        batchCancelButton.setEnabled(false);
        batchCancelButton.addActionListener(e -> cancelBatch());
        actions.add(batchRunButton);
        actions.add(batchCancelButton);
        top.add(actions, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0;
        top.add(new JLabel("选项:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1;
        JPanel batchOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        batchOptions.add(batchBackupCheck);
        batchOptions.add(batchBlockNetCheck);
        batchOptions.add(batchDryRunCheck);
        top.add(batchOptions, c);
        root.add(top, BorderLayout.NORTH);

        batchTable.setFillsViewportHeight(true);
        batchTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = new int[]{120, 190, 120, 100, 220, 230, 160, 90, 420};
        for (int i = 0; i < widths.length; i++) batchTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        root.add(new JScrollPane(batchTable), BorderLayout.CENTER);
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''    private OperationResult runNetFor(AppInfo info, boolean patch) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        if (patch && !PatchSafety.prepare(info, this::appendLog)) {
            return OperationResult.failed("安全备份预检失败", 3);
        }
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        if (info.type == AppInfo.Type.DOTNET) {
            cmd.add("-jar");
            cmd.add(cli.getAbsolutePath());
        } else {
            cmd.add("-cp");
            cmd.add(cli.getAbsolutePath());
            cmd.add("LicenseRecover");
        }
        cmd.add(patch ? "--remove-net" : "--scan-net");
        cmd.add((info.type == AppInfo.Type.DOTNET ? info.binDir : info.appRoot).getAbsolutePath());
        if (dryRunCheck.isSelected()) cmd.add("--dry-run");
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }
''',
    '''    private OperationResult runNetFor(AppInfo info, boolean patch) {
        return runNetFor(info, patch, dryRunCheck.isSelected());
    }

    private OperationResult runNetFor(AppInfo info, boolean patch, boolean preview) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        if (patch && !preview && !PatchSafety.prepare(info, this::appendLog)) {
            return OperationResult.failed("安全备份预检失败", 3);
        }
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe());
        cmd.add("-Dfile.encoding=UTF-8");
        if (info.type == AppInfo.Type.DOTNET) {
            cmd.add("-jar");
            cmd.add(cli.getAbsolutePath());
        } else {
            cmd.add("-cp");
            cmd.add(cli.getAbsolutePath());
            cmd.add("LicenseRecover");
        }
        cmd.add(patch ? "--remove-net" : "--scan-net");
        cmd.add((info.type == AppInfo.Type.DOTNET ? info.binDir : info.appRoot).getAbsolutePath());
        if (preview) cmd.add("--dry-run");
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }
''')

start = '''    private void scanBatch() {
'''
end = '''    private void cancelBatch() {
'''
path = ROOT / "src/main/java/LicenseRecoverModernGUI.java"
text = path.read_text(encoding="utf-8")
a = text.find(start)
b = text.find(end, a)
if a < 0 or b < 0:
    raise SystemExit("batch method block anchors not found")
new_batch = '''    private void scanBatch() {
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
        for (File child : children) {
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
                batchModel.addRow(new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "跳过", child.getAbsolutePath()});
            }
            batchTargets.add(target);
        }
        batchProgress.setMinimum(0);
        batchProgress.setMaximum(batchTargets.size());
        batchProgress.setValue(0);
        batchProgress.setString("已识别 " + detected + " 个 ITMC 应用；Java 授权计划已展开");
        setStatus("批量扫描完成", true);
    }

    private void runBatch() {
        if (batchTargets.isEmpty()) {
            scanBatch();
            if (batchTargets.isEmpty()) return;
        }
        final boolean usePatch = batchWay3.isSelected();
        final boolean backup = batchBackupCheck.isSelected();
        final boolean blockNet = batchBlockNetCheck.isSelected();
        final boolean preview = batchDryRunCheck.isSelected();
        if (usePatch && !preview) {
            int answer = JOptionPane.showConfirmDialog(frame,
                    "批量方式三会逐个修改授权文件，并为每个应用先建立 prepatch 备份。\\n请确认相关应用服务已停止。",
                    "确认批量危险操作", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
        }
        batchRunButton.setEnabled(false);
        batchScanButton.setEnabled(false);
        batchCancelButton.setEnabled(true);
        batchWorker = new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                int done = 0;
                for (int row = 0; row < batchTargets.size(); row++) {
                    if (isCancelled()) break;
                    BatchTarget target = batchTargets.get(row);
                    final int currentRow = row;
                    if (target.type == BatchTarget.Type.NONE) {
                        done++;
                        updateBatchProgress(done, "跳过 " + target.name);
                        continue;
                    }
                    SwingUtilities.invokeLater(() -> {
                        batchModel.setValueAt("处理中", currentRow, BATCH_COL_STATUS);
                        batchModel.setValueAt(usePatch ? "方式三" : "执行中", currentRow, BATCH_COL_VERIFY);
                    });
                    AppInfo info = AppDetector.detect(target.type == BatchTarget.Type.JAVA ? target.appRoot : target.binDir);
                    appendLog("\\n===== [" + target.name + "] " + (usePatch ? "方式三" : "一键恢复") + " =====\\n");
                    OperationResult result = usePatch
                            ? runNetFor(info, true, preview)
                            : runBatchRecommended(target, backup, blockNet, preview);
                    if (isCancelled()) break;
                    final String textResult = result.isSuccess() ? (result.status == OperationResult.Status.PREVIEW ? "PREVIEW" : "OK")
                            : (result.status == OperationResult.Status.CANCELLED ? "取消" : "FAILED");
                    final String verify = batchVerification(target, usePatch, preview, result);
                    SwingUtilities.invokeLater(() -> {
                        batchModel.setValueAt(verify, currentRow, BATCH_COL_VERIFY);
                        batchModel.setValueAt(textResult, currentRow, BATCH_COL_STATUS);
                    });
                    done++;
                    updateBatchProgress(done, target.name + " : " + textResult);
                    if (result.status == OperationResult.Status.CANCELLED) break;
                }
                return null;
            }
            protected void done() {
                batchRunButton.setEnabled(true);
                batchScanButton.setEnabled(true);
                batchCancelButton.setEnabled(false);
                batchProgress.setString(isCancelled() ? "已取消" : "批量执行完成");
                setStatus(isCancelled() ? "批量执行已取消" : "批量执行完成", !isCancelled());
            }
        };
        batchWorker.execute();
    }

    private OperationResult runBatchRecommended(BatchTarget target, boolean backup, boolean blockNet, boolean preview) {
        File selected = target.type == BatchTarget.Type.JAVA ? target.appRoot : target.binDir;
        LicenseRecoverModernGUIAutoRecovery.Detection d = LicenseRecoverModernGUIAutoRecovery.detect(selected);
        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) {
            return runLegacyBatchWay1(target.binDir, backup, preview);
        }
        LicenseRecoverModernGUIAutoRecovery.Result result =
                LicenseRecoverModernGUIAutoRecovery.recover(selected, backup, blockNet, preview, this::appendLog);
        if (Thread.currentThread().isInterrupted()) return OperationResult.cancelled("批量任务已取消");
        if (!result.success) return OperationResult.failed(result.message, 1);
        return preview ? OperationResult.preview(result.message) : OperationResult.success(result.message);
    }

    private OperationResult runLegacyBatchWay1(File binDir, boolean backup, boolean preview) {
        File cli = cliJar();
        if (!cli.isFile()) return OperationResult.failed("未找到 LicenseRecover.jar", 1);
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaExe()); cmd.add("-Dfile.encoding=UTF-8"); cmd.add("-jar"); cmd.add(cli.getAbsolutePath());
        cmd.add("--block-net"); cmd.add(binDir.getAbsolutePath());
        if (preview) cmd.add("--dry-run");
        if (!backup) cmd.add("--no-backup");
        appendLog("[batch] .NET Legacy 使用兼容方式一：仅阻断失效授权服务，不重建本地授权。\\n");
        return ProcessRunner.run(cmd, this::appendLog, PROCESS_TIMEOUT_SECONDS);
    }

    private String batchVerification(BatchTarget target, boolean usePatch, boolean preview, OperationResult result) {
        if (usePatch) return preview ? "方式三预览/扫描" : (result.isSuccess() ? "方式三完成" : "方式三失败");
        if (target.type == BatchTarget.Type.JAVA) {
            if (preview) return "预览: 未执行写后校验";
            return result.isSuccess() ? "RegisterMain: OK" : "RegisterMain: FAILED";
        }
        LicenseRecoverModernGUIAutoRecovery.Detection d = LicenseRecoverModernGUIAutoRecovery.detect(target.binDir);
        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) return "兼容方式一: 不适用";
        if (preview) return "预览: 未执行写回校验";
        return result.isSuccess() ? "写回解密校验: OK" : "写回解密校验: FAILED";
    }

'''
path.write_text(text[:a] + new_batch + text[b:], encoding="utf-8", newline="\n")

insert_after(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
''',
    '''
    private static String valueOrDash(String value) {
        return empty(value) ? "—" : value;
    }
''')

# ---------------------------------------------------------------------------
# UI patch: add the single-app Java plan card and update the native validation
# status after one-click execution.
# ---------------------------------------------------------------------------
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''        JPanel primary = new JPanel(new BorderLayout(8, 8));
        primary.setName(ONE_CLICK_CONTROL_NAME);
        JLabel help = new JLabel("自动识别软件 → 获取本机标识 → 生成本地授权 → 写入 → 重新读取验证");
        help.setForeground(new Color(0x57606a));
        primary.add(help, BorderLayout.NORTH);

        final JButton recover = new JButton("一键恢复授权");
        recover.setFont(recover.getFont().deriveFont(Font.BOLD, 15f));
        recover.setPreferredSize(new Dimension(220, 42));
        recover.setToolTipText("自动选择 Java / .NET 授权适配器；写入前默认备份，成功后自动校验");
        recover.addActionListener(e -> runOneClick(frame, recover));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        buttonRow.add(recover);
        primary.add(buttonRow, BorderLayout.CENTER);
''',
    '''        JPanel primary = new JPanel(new BorderLayout(8, 8));
        primary.setName(ONE_CLICK_CONTROL_NAME);
        JLabel help = new JLabel("自动识别软件 → 展示授权计划 → 获取本机标识 → 写入 → 原生重新读取验证");
        help.setForeground(new Color(0x57606a));

        final JLabel generationValue = new JLabel("—");
        final JLabel productValue = new JLabel("—");
        final JTextArea regStrValue = detailArea();
        final JTextArea targetValue = detailArea();
        final JLabel jarValue = new JLabel("—");
        final JLabel verifyValue = new JLabel("待执行");
        JPanel details = new JPanel(new GridBagLayout());
        details.setBorder(BorderFactory.createTitledBorder("授权识别详情"));
        addDetailRow(details, 0, "授权代际", generationValue);
        addDetailRow(details, 1, "ProName", productValue);
        addDetailRow(details, 2, "RegStr", regStrValue);
        addDetailRow(details, 3, "配置目标", targetValue);
        addDetailRow(details, 4, "授权组件", jarValue);
        addDetailRow(details, 5, "原生校验", verifyValue);

        JPanel overview = new JPanel(new BorderLayout(0, 6));
        overview.add(help, BorderLayout.NORTH);
        overview.add(details, BorderLayout.CENTER);
        primary.add(overview, BorderLayout.NORTH);

        final JButton recover = new JButton("一键恢复授权");
        recover.setFont(recover.getFont().deriveFont(Font.BOLD, 15f));
        recover.setPreferredSize(new Dimension(220, 42));
        recover.setToolTipText("Java 会展示授权代际 / ProName / RegStr / config 目标，并在写入后执行 RegisterMain 原生校验");
        recover.addActionListener(e -> runOneClick(frame, recover,
                generationValue, productValue, regStrValue, targetValue, jarValue, verifyValue));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        buttonRow.add(recover);
        primary.add(buttonRow, BorderLayout.CENTER);

        final JTextField planRoot = findTextFieldInTitledPanel(frame.getContentPane(), "应用目录");
        final Runnable refresh = () -> refreshPlan(frame, generationValue, productValue,
                regStrValue, targetValue, jarValue, verifyValue);
        if (planRoot != null) {
            planRoot.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() { SwingUtilities.invokeLater(refresh); }
                public void insertUpdate(DocumentEvent e) { changed(); }
                public void removeUpdate(DocumentEvent e) { changed(); }
                public void changedUpdate(DocumentEvent e) { changed(); }
            });
        }
        SwingUtilities.invokeLater(refresh);
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''    private static void runOneClick(final JFrame frame, final JButton button) {
''',
    '''    private static void addDetailRow(JPanel panel, int row, String name, Component value) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 5, 2, 5);
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        JLabel label = new JLabel(name + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, c);
    }

    private static JTextArea detailArea() {
        JTextArea area = new JTextArea(2, 48);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        return area;
    }

    private static void refreshPlan(JFrame frame, JLabel generation, JLabel product,
                                    JTextArea regStr, JTextArea targets, JLabel jar, JLabel verify) {
        JTextField appRoot = findTextFieldInTitledPanel(frame.getContentPane(), "应用目录");
        if (appRoot == null || appRoot.getText().trim().isEmpty()) {
            generation.setText("—"); product.setText("—"); regStr.setText("—"); targets.setText("—");
            jar.setText("—"); verify.setText("待选择应用");
            return;
        }
        File selected = new File(appRoot.getText().trim());
        LicenseRecoverModernGUIAutoRecovery.Detection detection = LicenseRecoverModernGUIAutoRecovery.detect(selected);
        if (detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.JAVA) {
            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(selected);
            generation.setText(plan.generation);
            product.setText(value(plan.productName));
            regStr.setText(value(plan.regStr)); regStr.setCaretPosition(0);
            targets.setText(plan.configTargets); targets.setCaretPosition(0);
            jar.setText(plan.registrationJarSummary());
            verify.setText("待执行: " + plan.verificationPlan);
        } else if (detection.isDetected()) {
            generation.setText(detection.kind.toString());
            product.setText(value(detection.productName));
            regStr.setText(".NET 由对应适配器自动识别");
            targets.setText("config.xml / 授权 sidecar（按检测结果）");
            jar.setText("不适用");
            verify.setText(detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                    ? "待执行: 写回解密校验" : "Legacy: 仅兼容路径");
        } else {
            generation.setText("未识别"); product.setText("—"); regStr.setText("—"); targets.setText("—");
            jar.setText("—"); verify.setText("未识别到支持的授权结构");
        }
    }

    private static String value(String text) {
        return text == null || text.trim().isEmpty() ? "—" : text;
    }

    private static void runOneClick(final JFrame frame, final JButton button,
                                    final JLabel generationValue, final JLabel productValue,
                                    final JTextArea regStrValue, final JTextArea targetValue,
                                    final JLabel jarValue, final JLabel verifyValue) {
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''        final boolean doBackup = backup == null || backup.isSelected();
        final boolean doBlock = blockNet == null || blockNet.isSelected();
        final boolean preview = dryRun != null && dryRun.isSelected();
        button.setEnabled(false);
''',
    '''        final boolean doBackup = backup == null || backup.isSelected();
        final boolean doBlock = blockNet == null || blockNet.isSelected();
        final boolean preview = dryRun != null && dryRun.isSelected();
        refreshPlan(frame, generationValue, productValue, regStrValue, targetValue, jarValue, verifyValue);
        verifyValue.setText(preview ? "预览执行中..." : "执行并校验中...");
        button.setEnabled(false);
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''                    if (result.success) {
                        StringBuilder msg = new StringBuilder(result.message);
                        if (result.machineId != null) msg.append("\\n\\n机器标识: ").append(result.machineId);
                        JOptionPane.showMessageDialog(frame, msg.toString(),
                                "一键恢复完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(frame, result.message,
                                "一键恢复未完成", JOptionPane.WARNING_MESSAGE);
                    }
''',
    '''                    boolean javaTarget = result.detection != null
                            && result.detection.kind == LicenseRecoverModernGUIAutoRecovery.Kind.JAVA;
                    if (javaTarget) {
                        verifyValue.setText(preview ? "PREVIEW: 未执行写后校验"
                                : (result.success ? "PASS: RegisterMain.checkReInfo()" : "FAILED: 查看运行日志"));
                    } else {
                        verifyValue.setText(preview ? "PREVIEW: 未执行写回校验"
                                : (result.success ? "PASS: 写回校验" : "FAILED: 查看运行日志"));
                    }
                    if (result.success) {
                        StringBuilder msg = new StringBuilder(result.message);
                        if (javaTarget) msg.append("\\n\\n原生校验: ")
                                .append(preview ? "预览模式未执行" : "RegisterMain.checkReInfo() 通过");
                        if (result.machineId != null) msg.append("\\n机器标识: ").append(result.machineId);
                        JOptionPane.showMessageDialog(frame, msg.toString(),
                                "一键恢复完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(frame, result.message,
                                "一键恢复未完成", JOptionPane.WARNING_MESSAGE);
                    }
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "一键恢复失败：" + ex.getMessage(),
                            "一键恢复授权", JOptionPane.ERROR_MESSAGE);
                }
''',
    '''                } catch (Exception ex) {
                    verifyValue.setText("FAILED: " + value(ex.getMessage()));
                    JOptionPane.showMessageDialog(frame, "一键恢复失败：" + ex.getMessage(),
                            "一键恢复授权", JOptionPane.ERROR_MESSAGE);
                }
''')

# ---------------------------------------------------------------------------
# Smoke tests for plan parity with the existing Java mapping fixtures.
# ---------------------------------------------------------------------------
insert_after(
    "src/test/java/RefactorSmokeTest.java",
    '''        check("QT0420".equals(LicenseRecover.resolveJavaRegStr(yt129Root.toString(), "YT00129")), "YT00129 embeds QT0420 authorization product");
''',
    '''        LicenseRecoverModernGUIJavaPlan yt129Plan = LicenseRecoverModernGUIJavaPlan.inspect(yt129Root.toFile());
        check(yt129Plan.detected && "QT04".equals(yt129Plan.productName),
                "Java GUI plan maps YT00129 to QT04");
        check("QT0420".equals(yt129Plan.regStr) && yt129Plan.rootConfigStyle,
                "Java GUI plan exposes YT00129 RegStr and root config target");
''')

insert_after(
    "src/test/java/RefactorSmokeTest.java",
    '''        check("QT30101,QT30102,QT30103,QT30104".equals(LicenseRecover.resolveJavaRegStr(qt30103Root.toString(), "QT30103")), "QT30103 preserves data regInfo");
''',
    '''        LicenseRecoverModernGUIJavaPlan qtPlan = LicenseRecoverModernGUIJavaPlan.inspect(qt30103Root.toFile());
        check(qtPlan.detected && "QT30103".equals(qtPlan.productName),
                "Java GUI plan keeps QT30xxx SoftVersionID as ProName");
        check(qtPlan.generation.contains("QT30xxx") && qtPlan.configTargets.contains("webapp根"),
                "Java GUI plan reports QT30xxx generation and dual config targets");
''')

insert_after(
    "src/test/java/RefactorSmokeTest.java",
    '''        check("QT0423,QT0428,QT0424,QT0425,QT0427,QT0426,QT0406,QT0430".equals(LicenseRecover.resolveJavaRegStr(xmtRoot.toString(), "XMT0107")), "XMT0107 preserves classes regInfo");
''',
    '''        LicenseRecoverModernGUIJavaPlan xmtPlan = LicenseRecoverModernGUIJavaPlan.inspect(xmtRoot.toFile());
        check(xmtPlan.detected && "XMT01".equals(xmtPlan.productName),
                "Java GUI plan maps XMT0107 to XMT01");
        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项"),
                "Java GUI plan exposes XMT generation and compact RegStr summary");
''')

# ---------------------------------------------------------------------------
# Version metadata and documentation.
# ---------------------------------------------------------------------------
(ROOT / "VERSION.txt").write_text("1.2.2\n", encoding="utf-8")

replace_once(
    "README.md",
    "当前稳定版本：**v1.2.1**",
    "当前稳定版本：**v1.2.2**")

insert_after(
    "README.md",
    '''### Java 应用

支持包含 `WEB-INF` / `WEB-INF/lib` 的 ITMC Java 应用，能够识别标准布局和已知的嵌套 `WEB-INF/WEB-INF` 布局，并覆盖当前已验证的 YT、XMT、QT30xxx 等产品代际。
''',
    '''
从 v1.2.2 起，单个应用的一键恢复区会直接显示 **授权代际、SoftVersionID 对应的 ProName、实际 RegStr、将写入的 config.xml 位置、授权组件以及 RegisterMain 原生校验结果**。批量页使用同一套识别模型，扫描后会逐项展开这些信息，并让批量“一键恢复授权”走与单个应用相同的恢复/校验链路。
''')

replace_once(
    "README.txt",
    "LicenseRecover v1.2.1\n=====================",
    "LicenseRecover v1.2.2\n=====================")

insert_after(
    "README.txt",
    '''从 v1.2.1 起，portable 包根目录只保留这一个 EXE 启动入口，不再提供旧 EXE 或 BAT 启动器。
''',
    '''
v1.2.2 Java / 批量识别增强
-------------------------
- 单个 Java 一键恢复区显示授权代际、ProName、RegStr、实际 config.xml 目标和授权组件；
- 写入后直接显示 RegisterMain.checkReInfo() 原生校验结果；
- 批量扫描表同步展示上述授权计划，每个 Java 应用可在执行前核对；
- 批量默认操作升级为与单个应用相同的一键恢复链路，并提供独立的备份、阻断联网、只预览选项；
- 批量 Java 完成后逐行显示 RegisterMain 原生校验是否通过。
''')

insert_after(
    "CHANGELOG.md",
    '''All notable user-visible and engineering changes are tracked here from the first stable release onward.
''',
    '''
## [1.2.2] - 2026-09-07

### Added

- Java 单个应用一键恢复区新增授权计划详情：授权代际、ProName、RegStr、实际 config.xml 目标、ITMCReg 组件与原生校验状态。
- 新增共享 `LicenseRecoverModernGUIJavaPlan`，让单个 GUI、批量 GUI 与真实 CLI 使用同一套 Java 产品/配置识别规则。
- 批量表新增 SoftVersionID、ProName、RegStr、配置目标与原生校验列，并提供独立的备份、阻断联网、只预览选项。

### Changed

- 批量默认“方式一”升级为与单个应用相同的一键恢复协调器；Java 项执行后逐行显示 `RegisterMain.checkReInfo()` 结果。
- .NET Modern 批量项使用一键写回校验；.NET Legacy 保持兼容方式一，仅阻断失效授权服务，避免改变旧代协议行为。
- 批量取消会中断并终止当前 Java 子进程，避免取消后恢复任务继续在后台运行。

''')

release = ROOT / "release-notes" / "v1.2.2.md"
release.write_text('''# LicenseRecover v1.2.2

This release makes the Java authorization decision path visible before execution and brings the same behavior into batch recovery.

## Java authorization plan

- The single-app one-click area now shows the detected authorization generation, `SoftVersionID`, resolved `ProName`, effective `RegStr`, actual `config.xml` targets, registration JAR state, and the planned native verification path.
- The plan reuses the same product mapping and `regInfo` resolution helpers as the CLI, so the preview cannot silently drift from the real write path.
- After a real Java recovery, the UI reports whether the application's own `RegisterMain.checkReInfo()` validation passed. Preview mode is explicitly marked as not having performed a write-back verification.
- Virbox-protected `ITMCReg*.jar` is identified in the plan; execution continues to use the existing temporary-unpack path without replacing the target JAR.

## Batch recovery

- Batch scan now expands each detected Java target into generation, `SoftVersionID`, `ProName`, `RegStr`, config targets, and native-verification columns before anything is changed.
- The default batch operation is now the same one-click recovery coordinator used by the single-app UI.
- Batch mode has its own backup, residual-network blocking, and preview controls.
- Java rows report `RegisterMain: OK/FAILED` after execution; preview rows state that write-back verification was not executed.
- Modern .NET rows use the normal one-click write-back verification. Legacy .NET rows preserve the compatibility way-one behavior and only block the obsolete registration endpoint.
- Cancelling a batch now interrupts and terminates the current child Java process instead of letting it continue in the background.

## Compatibility

- Java 8 source/runtime compatibility is retained.
- Windows 7 / Windows Server 2008 remains within the existing GUI compatibility boundary.
- The portable package still exposes only `LicenseRecoverGUI.exe` as the user-facing root executable.
''', encoding="utf-8", newline="\n")

print("v1.2.2 Java plan + batch patches applied")
