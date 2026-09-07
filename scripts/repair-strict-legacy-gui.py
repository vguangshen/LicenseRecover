#!/usr/bin/env python3
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'src/main/java/LicenseRecoverGUI.java'
BASE = '07ccb260bb7a1fcedf8862ee1ffb96d6e94dace2'
source = subprocess.check_output(['git','show', f'{BASE}:src/main/java/LicenseRecoverGUI.java'], text=True, encoding='utf-8')

def between(text, start, end, replacement, label):
    a=text.find(start)
    if a<0: raise SystemExit('missing start '+label)
    b=text.find(end,a)
    if b<0: raise SystemExit('missing end '+label)
    return text[:a]+replacement+text[b:]

def repl(text, old, new, label):
    if old not in text: raise SystemExit('missing '+label)
    return text.replace(old,new,1)

# Remove the catch-all authorization list from the legacy GUI executable path.
start='    static final String ALL_NUMS =\n'
end='    /** 在 lib 目录中定位授权 jar'
a=source.find(start)
if a>=0:
    b=source.find(end,a)
    source=source[:a]+source[b:]

# Unknown/static mapping is not executable in the legacy GUI anymore.
ps='    static String productMainFor(String softId) {'
pe='    /**\n     * 方式二: 生成离线授权码。'
product_method='''    /** Deprecated display helper. Executable paths use target-directory evidence only. */
    static String productMainFor(String softId) { return null; }

'''
source=between(source, ps, pe, product_method+pe, 'productMainFor')

# Java offline-code construction now requires explicit directory-derived RegStr.
gs='    static String[] genRegisterCode(String seq, String registerProductID) throws Exception {'
ge='    // ---- GUI ----'
oldblock=source[source.find(gs):source.find(ge,source.find(gs))]
newblock='''    static String[] genRegisterCode(String seq, String registerProductID, String regStr) throws Exception {
        if (registerProductID == null || registerProductID.trim().isEmpty())
            throw new IllegalArgumentException("注册产品族未从目标目录确认");
        if (regStr == null || regStr.trim().isEmpty())
            throw new IllegalArgumentException("RegStr 未从目标目录确认");
        GetRegisterCode rc = new GetRegisterCode();
        if (seq == null || seq.trim().isEmpty()) {
            DesUtil d = new DesUtil();
            String data = d.getRandom() + d.getMotherboardSN() + d.getRandom() + d.getCurrentTime() + d.getRandom();
            seq = new GetRegisterCode().buildCiphertext(data);
        }
        String myRegNO = rc.decrypt("itmcsoft", seq);
        if (myRegNO == null || myRegNO.length() < 43) throw new Exception("无法解析注册申请号（申请号格式不正确或已损坏）");
        String sn = myRegNO.substring(4, 20);
        String time19 = myRegNO.substring(24, 43);
        String plain = "00" + time19 + "00" + sn + "00" + "2099-12-31" + "00" + "1"
                + "00" + "-001" + "00" + "-1" + "00" + regStr.trim();
        String code = rc.encrypt("itmc" + registerProductID.trim(), plain);
        return new String[]{seq, code, sn, time19};
    }

'''
source=source.replace(oldblock,newblock,1)

# Environment detection displays only target-derived identities. No YX0302/QT1001 defaults.
ds='    static void detect(ActionEvent e) {'
de='    static void run(ActionEvent e) {'
newdetect='''    static void detect(ActionEvent e) {
        runBtn.setEnabled(false);
        detectBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                String appRoot = appRootField.getText().trim();
                File rootFile = new File(appRoot);
                String lib = locateLibDir(rootFile);
                if (lib == null) {
                    String dotnetBin = locateDotNetBin(rootFile);
                    if (dotnetBin != null) {
                        setAppTypeBadge("DOTNET");
                        LicenseRecoverModernGUIAutoRecovery.Detection nd =
                                LicenseRecoverModernGUIAutoRecovery.detect(new File(dotnetBin));
                        String product = nd.productName;
                        String regStr = product == null ? null : LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                new File(dotnetBin, "ITMC.Web.dll"), product);
                        if (product == null || product.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 ProName；不会填入默认产品号。\\n");
                            SwingUtilities.invokeLater(() -> { productField.setText(""); productAutoFilled = true; });
                        } else {
                            appendLog("[识别] .NET ProName=" + product + "（来自目标 ITMC.Web.dll）"
                                    + (regStr == null || regStr.trim().isEmpty() ? "；RegStr 未确认" : "；RegStr=" + regStr) + "\\n");
                            final String fp = product;
                            SwingUtilities.invokeLater(() -> { productField.setText(fp); productAutoFilled = true; });
                        }
                        return null;
                    }
                    setAppTypeBadge("");
                    appendLog("[错误] 未找到受支持的 Java/.NET 授权结构。\\n");
                    return null;
                }
                setAppTypeBadge("JAVA");
                File appRootDir = new File(lib).getParentFile().getParentFile();
                LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(appRootDir);
                if (!plan.detected || !plan.automaticRecoveryReady) {
                    appendLog("[阻止] Java 注册身份未从目标目录完整确认："
                            + (plan.detected ? plan.recoveryReadiness : "未识别注册结构") + "\\n");
                    SwingUtilities.invokeLater(() -> { productField.setText(""); productAutoFilled = true; });
                } else {
                    appendLog("[识别] Java VersionID=" + plan.softVersionId + " RuntimeProductID=" + plan.runtimeProductId
                            + " AuthorizationFamily=" + plan.authorizationFamily + " RegStr=" + plan.regStr
                            + "（目标目录证据）\\n");
                    final String fp = plan.runtimeProductId;
                    SwingUtilities.invokeLater(() -> { productField.setText(fp); productAutoFilled = true; });
                }
                appendLog("lib 目录         : " + lib + "\\n");
                return null;
            }
            protected void done() { runBtn.setEnabled(true); detectBtn.setEnabled(true); }
        }.execute();
    }

'''
source=between(source, ds, de, newdetect+de, 'detect')

# Mode-1 Java CLI is already fail-closed; remove UI-side fabricated defaults.
source=repl(source,
'        if (productMain.isEmpty()) productMain = (dotnetBin != null) ? "YX0302" : "QT1001";\n',
'', 'run defaults')

# Replace offline code generation for both .NET and Java with directory evidence only.
gcs='    static void genCode(ActionEvent e) {'
gce='    static void copyCode(ActionEvent e) {'
newgen='''    static void genCode(ActionEvent e) {
        String appRoot = appRootField.getText().trim();
        String seq = seqField.getText().trim();
        genBtn.setEnabled(false);
        copyBtn.setEnabled(false);
        lastCode = "";
        codeField.setText("");
        final String fDotnet = appRoot.isEmpty() ? null : locateDotNetBin(new File(appRoot));
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() {
                try {
                    if (fDotnet != null) {
                        appendLog("--- 方式二：生成离线授权码（.NET 版） ---\\n");
                        LicenseRecoverModernGUIAutoRecovery.Detection nd =
                                LicenseRecoverModernGUIAutoRecovery.detect(new File(fDotnet));
                        String product = nd.productName;
                        if (product == null || product.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 ProName；不会使用固定 itmcIEC/YX0302。\\n");
                            return null;
                        }
                        if (LegacyDotNetProtocol.isLegacyTarget(new File(fDotnet))) {
                            String legacyProduct = LegacyDotNetProtocol.readProductName(new File(fDotnet));
                            if (legacyProduct == null || legacyProduct.trim().isEmpty()) {
                                appendLog("[阻止] DS01xx 目标 DLL 未确认 ProName。\\n"); return null;
                            }
                            if (seq.trim().isEmpty()) {
                                appendLog("[提示] DS01xx 旧协议必须先从应用本地注册页获取申请号。\\n"); return null;
                            }
                            LegacyDotNetProtocol.CodeResult result = LegacyDotNetProtocol.generateAuthorizationCode(
                                    seq.trim(), LegacyDotNetProtocol.readSoftVersion(new File(fDotnet)), legacyProduct);
                            appendLog("[识别] 产品标识=" + legacyProduct + "（来自目标 DLL），授权版本="
                                    + result.authorization.product + "\\n");
                            appendLog("离线授权码       : " + result.code + "\\n");
                            publishGeneratedCode(result.code);
                            return null;
                        }
                        String regStr = LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                new File(fDotnet, "ITMC.Web.dll"), product);
                        if (regStr == null || regStr.trim().isEmpty()) {
                            appendLog("[阻止] 目标 ITMC.Web.dll 未解析出 RegStr；不会使用 VersionID/默认列表兜底。\\n");
                            return null;
                        }
                        String shown = productField.getText().trim();
                        if (!shown.isEmpty() && !shown.equalsIgnoreCase(product)) {
                            appendLog("[阻止] 界面产品号与目标 DLL 解析结果不一致：" + shown + " != " + product + "\\n");
                            return null;
                        }
                        if (seq.trim().isEmpty() && isAspNetDotNetBin(fDotnet)) {
                            appendLog("[提示] ASP.NET 项目请先从网站本地注册页获取申请号。\\n"); return null;
                        }
                        java.util.List<String> cmd = new java.util.ArrayList<>();
                        cmd.add("gencode"); cmd.add(fDotnet);
                        cmd.add("--product"); cmd.add(product);
                        cmd.add("--regstr"); cmd.add(regStr);
                        if (!seq.trim().isEmpty()) { cmd.add("--seq"); cmd.add(seq.trim()); }
                        String helperOutput = runDotNetCapture(cmd, "");
                        String generated = extractGeneratedCode(helperOutput);
                        if (generated != null) publishGeneratedCode(generated);
                        return null;
                    }

                    if (appRoot.isEmpty()) {
                        appendLog("[阻止] 必须选择目标 Java 应用目录；不会使用默认 YT001/QT1001。\\n");
                        return null;
                    }
                    LicenseRecoverModernGUIJavaPlan plan = LicenseRecoverModernGUIJavaPlan.inspect(new File(appRoot));
                    if (!plan.detected || !plan.automaticRecoveryReady) {
                        appendLog("[阻止] Java 注册ID/RegStr未从目标目录确认："
                                + (plan.detected ? plan.recoveryReadiness : "未识别注册结构") + "\\n");
                        return null;
                    }
                    appendLog("--- 方式二：生成离线授权码（Java 版） ---\\n");
                    appendLog("目录产品族=" + plan.authorizationFamily + "  RegStr=" + plan.regStr + "\\n");
                    boolean hasSeq = !seq.trim().isEmpty();
                    String[] r = genRegisterCode(hasSeq ? seq : null, plan.authorizationFamily, plan.regStr);
                    appendLog("申请时间         : " + r[3] + "\\n");
                    appendLog("注册申请号       : " + r[0] + "\\n");
                    appendLog("离线授权码       : " + r[1] + "\\n");
                    publishGeneratedCode(r[1]);
                } catch (Exception ex) {
                    appendLog("[错误] " + ex + "\\n");
                }
                return null;
            }
            protected void done() { genBtn.setEnabled(true); }
        }.execute();
    }

'''
source=between(source, gcs, gce, newgen+gce, 'genCode')

# Strong guards: executable fallback strings must be gone from this GUI source.
for forbidden in ['LegacyDotNetProtocol.PRODUCT_NAME','genRegisterCode(hasSeq ? seq : null, registerPid)','+ ALL_NUMS']:
    if forbidden in source: raise SystemExit('forbidden legacy GUI fallback remains: '+forbidden)

TARGET.write_text(source, encoding='utf-8', newline='\n')
print('Legacy GUI restored from baseline and selectively hardened.')
