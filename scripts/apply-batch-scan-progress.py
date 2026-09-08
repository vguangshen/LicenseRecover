#!/usr/bin/env python3
from pathlib import Path

p = Path('src/main/java/LicenseRecoverModernGUI.java')
text = p.read_text(encoding='utf-8')

old = '''    private final JProgressBar batchProgress = new JProgressBar();
    private SwingWorker<Void, Void> batchWorker;

    private AppInfo currentInfo = AppInfo.unknown(null);'''
new = '''    private final JProgressBar batchProgress = new JProgressBar();
    private SwingWorker<?, ?> batchWorker;

    private static final class BatchScanRow {
        final BatchTarget target;
        final Object[] cells;
        final int completed;
        final int total;
        final int detected;
        final int skipped;
        final int errors;
        final String name;

        BatchScanRow(BatchTarget target, Object[] cells, int completed, int total,
                     int detected, int skipped, int errors, String name) {
            this.target = target;
            this.cells = cells;
            this.completed = completed;
            this.total = total;
            this.detected = detected;
            this.skipped = skipped;
            this.errors = errors;
            this.name = name;
        }
    }

    private AppInfo currentInfo = AppInfo.unknown(null);'''
if old not in text:
    raise SystemExit('batch worker field anchor not found')
text = text.replace(old, new, 1)

start = text.index('    private void scanBatch() {')
end = text.index('    private void runBatch() {', start)
new_scan = r'''    private void scanBatch() {
        scanBatch(false);
    }

    private void scanBatch(final boolean runAfterScan) {
        if (batchWorker != null && !batchWorker.isDone()) {
            setStatus("已有批量任务正在进行，请先等待或取消当前任务", false);
            return;
        }

        File parent = new File(batchRootField.getText().trim());
        File[] children = parent.isDirectory() ? parent.listFiles(File::isDirectory) : null;
        if (children == null) {
            setStatus("批量父目录无效", false);
            return;
        }
        Arrays.sort(children);

        batchTargets.clear();
        batchModel.setRowCount(0);
        final int total = children.length;
        batchProgress.setIndeterminate(false);
        batchProgress.setMinimum(0);
        batchProgress.setMaximum(Math.max(1, total));
        batchProgress.setValue(0);
        batchProgress.setString(total == 0 ? "扫描完成：0 个目录" : "准备扫描：0 / " + total);

        if (total == 0) {
            setStatus("批量扫描完成：父目录下没有子目录", true);
            return;
        }

        batchScanButton.setEnabled(false);
        batchRunButton.setEnabled(false);
        batchCancelButton.setEnabled(true);
        setStatusInfo("正在扫描子目录：0 / " + total);
        appendLog("[批量扫描] 开始扫描 " + total + " 个子目录。\n");

        batchWorker = new SwingWorker<Void, BatchScanRow>() {
            private int detectedCount;
            private int skippedCount;
            private int errorCount;

            protected Void doInBackground() {
                for (int i = 0; i < children.length; i++) {
                    if (isCancelled()) break;
                    final File child = children[i];
                    final int current = i + 1;
                    final int completedBefore = i;
                    SwingUtilities.invokeLater(() -> {
                        if (isCancelled()) return;
                        int percent = total == 0 ? 100 : (completedBefore * 100 / total);
                        batchProgress.setValue(completedBefore);
                        batchProgress.setString("已完成 " + completedBefore + " / " + total
                                + " (" + percent + "%)，正在扫描：" + child.getName());
                        setStatusInfo("正在扫描：" + child.getName() + "（" + current + " / " + total + "）");
                    });

                    BatchTarget target;
                    Object[] cells;
                    try {
                        AppInfo info = AppDetector.detect(child);
                        if (info.type == AppInfo.Type.JAVA) {
                            target = BatchTarget.javaTarget(child.getName(), info.appRoot, info.libDir);
                            LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(info.appRoot);
                            String verifyText = !plan.detected ? "待识别"
                                    : (plan.automaticRecoveryReady ? "待执行: RegisterMain"
                                    : "未执行: " + plan.recoveryReadiness);
                            String statusText = !plan.detected ? "待识别"
                                    : (plan.automaticRecoveryReady ? "待处理" : "待确认");
                            cells = new Object[]{child.getName(),
                                    plan.detected ? "Java / " + plan.generation : "Java",
                                    valueOrDash(plan.softVersionId), valueOrDash(plan.authorizationFamily),
                                    valueOrDash(plan.runtimeProductId), plan.regStrSummary(), plan.configTargets,
                                    verifyText, statusText, info.appRoot.getAbsolutePath()};
                            detectedCount++;
                        } else if (info.type == AppInfo.Type.DOTNET) {
                            target = BatchTarget.dotNetTarget(child.getName(), info.binDir);
                            LicenseRecoverModernGUIAutoRecovery.Detection d =
                                    LicenseRecoverModernGUIAutoRecovery.detect(info.binDir);
                            String kind = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                                    ? ".NET Modern" : ".NET Legacy";
                            String dotNetRegStr = d.productName == null ? null
                                    : LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                            new File(info.binDir, "ITMC.Web.dll"), d.productName);
                            boolean dotNetIdentityReady = d.productName != null && !d.productName.trim().isEmpty();
                            boolean dotNetRegReady = dotNetRegStr != null && !dotNetRegStr.trim().isEmpty();
                            String verify = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                                    ? (dotNetIdentityReady && dotNetRegReady
                                            ? "待执行: DoRegistry + CheckReInfo"
                                            : "未执行: DLL注册证据不足")
                                    : "兼容方式一: 不适用";
                            String dotNetStatus = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                                    && (!dotNetIdentityReady || !dotNetRegReady) ? "待确认" : "待处理";
                            cells = new Object[]{child.getName(), kind,
                                    valueOrDash(d.versionId), valueOrDash(d.productName), valueOrDash(d.productName),
                                    valueOrDash(dotNetRegStr), "config.xml", verify, dotNetStatus,
                                    info.binDir.getAbsolutePath()};
                            detectedCount++;
                        } else {
                            target = BatchTarget.skipped(child.getName());
                            cells = new Object[]{child.getName(), "—", "—", "—", "—", "—", "—", "—",
                                    "未识别", child.getAbsolutePath()};
                            skippedCount++;
                        }
                    } catch (Throwable ex) {
                        errorCount++;
                        target = BatchTarget.skipped(child.getName());
                        String msg = ex.getMessage();
                        String reason = ex.getClass().getSimpleName()
                                + (msg == null || msg.trim().isEmpty() ? "" : ": " + msg.trim());
                        cells = new Object[]{child.getName(), "检测异常", "—", "—", "—", "—", "—", "—",
                                reason, child.getAbsolutePath()};
                        appendLog("[批量扫描] " + child.getName() + " 检测异常: " + reason + "\n");
                    }

                    publish(new BatchScanRow(target, cells, current, total,
                            detectedCount, skippedCount, errorCount, child.getName()));
                }
                return null;
            }

            protected void process(List<BatchScanRow> chunks) {
                for (BatchScanRow row : chunks) {
                    batchTargets.add(row.target);
                    batchModel.addRow(row.cells);
                    batchProgress.setMaximum(Math.max(1, row.total));
                    batchProgress.setValue(row.completed);
                    int percent = row.total == 0 ? 100 : (row.completed * 100 / row.total);
                    batchProgress.setString("扫描 " + row.completed + " / " + row.total
                            + " (" + percent + "%)；识别 " + row.detected
                            + "，未识别 " + row.skipped + "，异常 " + row.errors
                            + "；刚完成：" + row.name);
                }
            }

            protected void done() {
                boolean cancelled = isCancelled();
                batchScanButton.setEnabled(true);
                batchRunButton.setEnabled(true);
                batchCancelButton.setEnabled(false);

                int processed = batchTargets.size();
                if (cancelled) {
                    batchProgress.setValue(processed);
                    batchProgress.setString("扫描已取消：已完成 " + processed + " / " + total);
                    statusLabel.setText("批量扫描已取消（已完成 " + processed + " / " + total + "）");
                    statusLabel.setForeground(new Color(0x57606a));
                    appendLog("[批量扫描] 已取消，完成 " + processed + " / " + total + " 个目录。\n");
                } else {
                    batchProgress.setValue(total);
                    batchProgress.setString("扫描完成：" + total + " 个目录；识别 " + detectedCount
                            + "，未识别 " + skippedCount + "，异常 " + errorCount);
                    setStatus(errorCount == 0 ? "批量扫描完成" : "批量扫描完成（存在检测异常）",
                            errorCount == 0);
                    appendLog("[批量扫描] 完成：" + total + " 个目录；识别 " + detectedCount
                            + "，未识别 " + skippedCount + "，异常 " + errorCount + "。\n");
                }

                batchWorker = null;
                if (!cancelled && runAfterScan && !batchTargets.isEmpty()) {
                    SwingUtilities.invokeLater(() -> runBatch());
                }
            }
        };
        batchWorker.execute();
    }

'''
text = text[:start] + new_scan + text[end:]

old = '''    private void runBatch() {
        if (batchTargets.isEmpty()) {
            scanBatch();
            if (batchTargets.isEmpty()) return;
        }
        final boolean usePatch = batchWay3.isSelected();'''
new = '''    private void runBatch() {
        if (batchWorker != null && !batchWorker.isDone()) {
            setStatus("请等待当前批量任务完成，或先取消当前任务", false);
            return;
        }
        if (batchTargets.isEmpty()) {
            scanBatch(true);
            return;
        }
        final boolean usePatch = batchWay3.isSelected();'''
if old not in text:
    raise SystemExit('runBatch initial anchor not found')
text = text.replace(old, new, 1)

old = '''        batchRunButton.setEnabled(false);
        batchScanButton.setEnabled(false);
        batchCancelButton.setEnabled(true);
        batchWorker = new SwingWorker<Void, Void>() {'''
new = '''        batchRunButton.setEnabled(false);
        batchScanButton.setEnabled(false);
        batchCancelButton.setEnabled(true);
        batchProgress.setIndeterminate(false);
        batchProgress.setMinimum(0);
        batchProgress.setMaximum(Math.max(1, batchTargets.size()));
        batchProgress.setValue(0);
        batchProgress.setString("准备批量执行：0 / " + batchTargets.size());
        batchWorker = new SwingWorker<Void, Void>() {'''
if old not in text:
    raise SystemExit('runBatch progress reset anchor not found')
text = text.replace(old, new, 1)

old = '''            protected void done() {
                batchRunButton.setEnabled(true);
                batchScanButton.setEnabled(true);
                batchCancelButton.setEnabled(false);
                batchProgress.setString(isCancelled() ? "已取消" : "批量执行完成");
                setStatus(isCancelled() ? "批量执行已取消" : "批量执行完成", !isCancelled());
            }
        };'''
new = '''            protected void done() {
                batchRunButton.setEnabled(true);
                batchScanButton.setEnabled(true);
                batchCancelButton.setEnabled(false);
                batchProgress.setString(isCancelled() ? "已取消" : "批量执行完成");
                setStatus(isCancelled() ? "批量执行已取消" : "批量执行完成", !isCancelled());
                batchWorker = null;
            }
        };'''
if old not in text:
    raise SystemExit('runBatch done anchor not found')
text = text.replace(old, new, 1)

old = '''    private void setStatus(String text, boolean ok) {
        statusLabel.setText(text);
        statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb42318));
    }

    private void appendLog(String text) {'''
new = '''    private void setStatus(String text, boolean ok) {
        statusLabel.setText(text);
        statusLabel.setForeground(ok ? new Color(0x1a7f37) : new Color(0xb42318));
    }

    private void setStatusInfo(String text) {
        statusLabel.setText(text);
        statusLabel.setForeground(new Color(0x0969da));
    }

    private void appendLog(String text) {'''
if old not in text:
    raise SystemExit('status helper anchor not found')
text = text.replace(old, new, 1)

p.write_text(text, encoding='utf-8')
print('patched asynchronous batch scan progress')
