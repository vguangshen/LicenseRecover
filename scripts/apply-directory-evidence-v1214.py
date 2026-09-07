#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def patch(path, old, new, count=1):
    p = ROOT / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'anchor missing in {path}: {old[:120]!r}')
    s2 = s.replace(old, new, count)
    p.write_text(s2, encoding='utf-8', newline='\n')
    print('patched', path)

# 1) Java plan: accept identity only when the historical candidate is literally
# proven by target application binaries; allow RegStr to be obtained read-only
# from the target RegisterMain at execution time.
path = 'src/main/java/LicenseRecoverModernGUIJavaPlan.java'
old = '''        String family = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : null));
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : null));
        File jar = findRegJar(lib);
        boolean packed = jar != null && isVirboxPackedJar(jar);
        String products = directoryMapping != null ? directoryMapping.productMainNum
                : resolveRegStr(root, runtimeProduct, lib);

        String recoveredLocalRegStr = blank(runtimeProduct)
                ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
        boolean directoryIdentity = directoryMapping != null
                || directDataIdentity || !blank(confirmedClassesFamily);
        boolean directoryRegStr = directoryMapping != null
                || dataRegInfo != null || classesRegInfo != null || recoveredLocalRegStr != null;

        boolean ready = true;
        String readiness = "可安全自动恢复（注册ID/RegStr均来自目标目录）";
        if (blank(soft)) {
            ready = false;
            readiness = "目标软件目录未找到 SoftVersionID";
        } else if (requiresDirectoryMapping && directoryMapping == null) {
            ready = false;
            readiness = "目标软件自带注册分派类，但未解析出注册ID映射";
        } else if (!directoryIdentity) {
            ready = false;
            readiness = "注册ID仅能由旧兼容规则推测，缺少目标目录证据";
        } else if (blank(family) || "未确认".equals(family) || blank(runtimeProduct)) {
            ready = false;
            readiness = "目标软件目录未确认授权族/运行注册ID";
        } else if (!directoryRegStr || blank(products)) {
            ready = false;
            readiness = "目标软件目录未声明或恢复出 RegStr，禁止使用默认授权项";
        }
'''
new = '''        // Candidate rules are never executable evidence by themselves. For product families
        // that are prefixes of the concrete SoftVersionID (DS24/DS28/DS501/XMT/YX0305...),
        // accept the candidate only when that exact token is also present in this target's
        // own class/JAR bytes. This keeps fail-closed semantics without discarding real apps
        // whose registration identity lives in bytecode instead of XML.
        String binaryFamily = confirmedBinaryAuthorizationFamily(
                root, lib, soft, newStyle, classesConfig.isFile());
        String family = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : binaryFamily));
        String binaryRuntime = confirmedBinaryRuntimeProduct(
                root, lib, soft, binaryFamily, newStyle, classesConfig.isFile());
        String runtimeProduct = directoryMapping != null ? directoryMapping.productMain
                : (!blank(confirmedClassesFamily) ? confirmedClassesFamily
                : (directDataIdentity ? soft.trim() : binaryRuntime));
        File jar = findRegJar(lib);
        boolean packed = jar != null && isVirboxPackedJar(jar);
        String products = directoryMapping != null ? directoryMapping.productMainNum
                : resolveRegStr(root, runtimeProduct, lib);

        String recoveredLocalRegStr = blank(runtimeProduct)
                ? null : ExistingLocalRegStrProbe.recover(root, lib, runtimeProduct);
        boolean binaryIdentity = !blank(binaryFamily) && !blank(binaryRuntime);
        boolean directoryIdentity = directoryMapping != null
                || directDataIdentity || !blank(confirmedClassesFamily) || binaryIdentity;
        boolean directoryRegStr = directoryMapping != null
                || dataRegInfo != null || classesRegInfo != null || recoveredLocalRegStr != null;
        // A missing static regInfo is not itself a reason to guess. If the target ships
        // ITMCReg and its product identity is already proven by directory evidence, the
        // CLI can ask that exact target RegisterMain.getRegInfo() for RegStr before any write.
        boolean runtimeRegStrProbe = !directoryRegStr && jar != null
                && directoryIdentity && !blank(runtimeProduct);

        boolean ready = true;
        String readiness = "可安全自动恢复（注册ID/RegStr均来自目标目录）";
        if (blank(soft)) {
            ready = false;
            readiness = "目标软件目录未找到 SoftVersionID";
        } else if (requiresDirectoryMapping && directoryMapping == null) {
            ready = false;
            readiness = "目标软件自带注册分派类，但未解析出注册ID映射";
        } else if (!directoryIdentity) {
            ready = false;
            readiness = "注册ID仅能由旧兼容规则推测，缺少目标目录二进制/配置证据";
        } else if (blank(family) || "未确认".equals(family) || blank(runtimeProduct)) {
            ready = false;
            readiness = "目标软件目录未确认授权族/运行注册ID";
        } else if (!directoryRegStr || blank(products)) {
            if (runtimeRegStrProbe) {
                readiness = "可安全自动恢复（注册ID来自目标目录；RegStr执行时由目标RegisterMain.getRegInfo()读取）";
            } else {
                ready = false;
                readiness = "目标软件目录未声明 RegStr，且无法使用目标注册组件只读获取";
            }
        }
'''
patch(path, old, new)

old = '''    public String regStrSummary() {
        if (blank(regStr)) return "未静态声明";
'''
new = '''    public String regStrSummary() {
        if (blank(regStr)) return automaticRecoveryReady ? "目标组件动态读取" : "未静态声明";
'''
patch(path, old, new)

anchor = '''    private static String readElement(File file, String element) {
'''
helpers = r'''    static String confirmedBinaryAuthorizationFamily(File root, File lib, String softId,
                                                       boolean newStyle, boolean classesStyle) {
        if (blank(softId)) return null;
        String candidate = authorizationFamilyFor(softId, newStyle, classesStyle);
        if (blank(candidate) || "未确认".equals(candidate)) return null;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        String c = candidate.trim().toUpperCase(Locale.ROOT);
        // Never use unrelated legacy mappings (for example YT -> QT04) as binary-prefix proof.
        if (!id.equals(c) && !id.startsWith(c)) return null;
        if (newStyle && id.equals(c)) return candidate.trim();
        return hasDirectoryBinaryToken(root, lib, candidate) ? candidate.trim() : null;
    }

    static String confirmedBinaryRuntimeProduct(File root, File lib, String softId,
                                                String confirmedFamily,
                                                boolean newStyle, boolean classesStyle) {
        if (blank(softId) || blank(confirmedFamily)) return null;
        String candidate = runtimeProductFor(softId);
        if (blank(candidate)) return null;
        String id = softId.trim().toUpperCase(Locale.ROOT);
        String c = candidate.trim().toUpperCase(Locale.ROOT);
        if (!id.equals(c) && !id.startsWith(c)) return null;
        if (candidate.equalsIgnoreCase(confirmedFamily)) return confirmedFamily;
        // Concrete runtime IDs (DS501xx/YX0305xx) must also occur as an exact token
        // in target bytecode; the occurrence in config.xml alone is intentionally insufficient.
        return hasDirectoryBinaryToken(root, lib, candidate) ? candidate.trim() : null;
    }

    static boolean hasDirectoryBinaryToken(File root, File lib, String token) {
        if (root == null || blank(token)) return false;
        File jar = findRegJar(lib);
        if (jar != null && jarContainsToken(jar, token.trim())) return true;
        int[] budget = new int[]{12000};
        File classes = new File(root, "WEB-INF" + File.separator + "classes");
        if (classTreeContainsToken(classes, token.trim(), budget)) return true;
        File nested = new File(root, "WEB-INF" + File.separator + "WEB-INF"
                + File.separator + "classes");
        return classTreeContainsToken(nested, token.trim(), budget);
    }

    private static boolean classTreeContainsToken(File dir, String token, int[] budget) {
        if (dir == null || !dir.isDirectory() || budget[0] <= 0) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (budget[0]-- <= 0) return false;
            if (f.isDirectory()) {
                if (classTreeContainsToken(f, token, budget)) return true;
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".class")
                    && f.length() <= 8L * 1024L * 1024L) {
                try {
                    if (bytesContainExactAsciiToken(Files.readAllBytes(f.toPath()), token)) return true;
                } catch (Exception ignore) { }
            }
        }
        return false;
    }

    private static boolean jarContainsToken(File jar, String token) {
        JarFile jf = null;
        try {
            jf = new JarFile(jar);
            java.util.Enumeration<JarEntry> entries = jf.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                if (entry.getSize() > 8L * 1024L * 1024L) continue;
                InputStream in = null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    in = jf.getInputStream(entry);
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, n);
                        if (out.size() > 8 * 1024 * 1024) break;
                    }
                    if (bytesContainExactAsciiToken(out.toByteArray(), token)) return true;
                } catch (Exception ignore) {
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignore) { }
                }
            }
        } catch (Exception ignore) {
            return false;
        } finally {
            try { if (jf != null) jf.close(); } catch (Exception ignore) { }
        }
        return false;
    }

    private static boolean bytesContainExactAsciiToken(byte[] data, String token) {
        if (data == null || blank(token)) return false;
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                int a = data[i + j] & 0xff;
                int b = needle[j] & 0xff;
                if (a != b && Character.toUpperCase((char) a) != Character.toUpperCase((char) b))
                    continue outer;
            }
            if (i > 0 && isProductTokenChar(data[i - 1] & 0xff)) continue;
            int after = i + needle.length;
            if (after < data.length && isProductTokenChar(data[after] & 0xff)) continue;
            return true;
        }
        return false;
    }

    private static boolean isProductTokenChar(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

'''
# Need ByteArrayOutputStream import.
p = ROOT / path
s = p.read_text(encoding='utf-8')
if 'import java.io.ByteArrayOutputStream;' not in s:
    s = s.replace('import java.io.DataInputStream;\n', 'import java.io.ByteArrayOutputStream;\nimport java.io.DataInputStream;\n', 1)
if anchor not in s:
    raise SystemExit('JavaPlan helper insertion anchor missing')
s = s.replace(anchor, helpers + anchor, 1)
p.write_text(s, encoding='utf-8', newline='\n')
print('inserted JavaPlan binary evidence helpers')

# 2) Java CLI: if static RegStr is absent, read it from the target registration component
# before any write. No built-in list or VersionID fallback is introduced.
path = 'src/main/java/LicenseRecover.java'
old = '''        String resolvedRegStr = plan.regStr;
        if (resolvedRegStr == null || resolvedRegStr.trim().isEmpty()) {
            System.err.println("自动恢复已阻止：当前 classes-config 应用没有静态 regInfo，RegStr 未确认。");
            System.err.println("不会使用 44 项通用列表猜测授权项，也不会修改任何配置文件。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        info.setRegStr(resolvedRegStr);
'''
new = '''        String resolvedRegStr = plan.regStr;
        if (resolvedRegStr == null || resolvedRegStr.trim().isEmpty()) {
            resolvedRegStr = probeTargetRegStr(productMain, appRoot, libDir, rootConfigStyle);
            if (resolvedRegStr != null && !resolvedRegStr.trim().isEmpty()) {
                System.out.println("[识别] RegStr(目标组件 getRegInfo) = " + resolvedRegStr);
            }
        }
        if (resolvedRegStr == null || resolvedRegStr.trim().isEmpty()) {
            System.err.println("自动恢复已阻止：目标目录没有静态 RegStr，且目标 RegisterMain.getRegInfo() 也未返回 RegStr。");
            System.err.println("不会使用 44 项通用列表、VersionID 或任何默认授权项，也不会修改任何配置文件。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        info.setRegStr(resolvedRegStr);
'''
patch(path, old, new)

anchor = '''    static RegisterMain newRegisterMain(String product, String json, String path) {
'''
helpers = '''    /**
     * Read RegStr from the selected application's own RegisterMain/RegeditInfo before any write.
     * This is intentionally read-only and is used only after product identity is proven from
     * target-directory evidence. Returning null keeps the caller fail-closed.
     */
    static String probeTargetRegStr(String product, String appRoot, String libDir, boolean rootConfigStyle) {
        if (product == null || product.trim().isEmpty() || libDir == null) return null;
        String jsonForMain;
        try { jsonForMain = new GetRegisterCode().encrypt(product + "RegeditNew", "itmcsoft"); }
        catch (Throwable ex) { return null; }
        String[] dirs = rootConfigStyle && appRoot != null
                ? new String[]{libDir, appRoot} : new String[]{libDir};
        for (String dir : dirs) {
            if (dir == null || dir.trim().isEmpty()) continue;
            String path = dir.endsWith(File.separator) ? dir : dir + File.separator;
            try {
                RegisterMain reg = newRegisterMain(product, jsonForMain, path);
                RegeditInfo info = reg.getRegInfo();
                String value = normalizeTargetRegStr(info == null ? null : info.getRegStr());
                if (value != null) return value;
            } catch (Throwable ex) {
                System.out.println("[只读探测] RegisterMain.getRegInfo(" + dir + ") 未返回可用 RegStr: "
                        + ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            }
        }
        return null;
    }

    static String normalizeTargetRegStr(String raw) {
        if (raw == null) return null;
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (String part : raw.split(",")) {
            String x = part == null ? "" : part.trim();
            if (!x.isEmpty()) values.add(x);
        }
        if (values.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String x : values) {
            if (out.length() > 0) out.append(',');
            out.append(x);
        }
        return out.toString();
    }

'''
p = ROOT / path
s = p.read_text(encoding='utf-8')
if anchor not in s:
    raise SystemExit('LicenseRecover probe insertion anchor missing')
s = s.replace(anchor, helpers + anchor, 1)
p.write_text(s, encoding='utf-8', newline='\n')
print('inserted target RegStr read-only probe')

old = '''        String registerPid = plan.authorizationFamily;
        String regStr = plan.regStr;
        if (productOverride != null && !productOverride.trim().isEmpty()
'''
new = '''        String registerPid = plan.authorizationFamily;
        String regStr = plan.regStr;
        if (regStr == null || regStr.trim().isEmpty()) {
            String libDir = locateLibDir(appArg);
            File libFile = libDir == null ? null : new File(libDir);
            File rootFile = libFile == null ? null : libFile.getParentFile();
            rootFile = rootFile == null ? null : rootFile.getParentFile();
            String resolvedRoot = rootFile == null ? appArg : rootFile.getAbsolutePath();
            regStr = probeTargetRegStr(plan.runtimeProductId, resolvedRoot, libDir,
                    usesRootConfigApp(resolvedRoot));
            if (regStr != null && !regStr.trim().isEmpty())
                System.out.println("[识别] RegStr(目标组件 getRegInfo) = " + regStr);
        }
        if (regStr == null || regStr.trim().isEmpty()) {
            System.err.println("[错误] 目标 RegisterMain.getRegInfo() 未返回 RegStr；不会使用默认授权项。");
            System.out.println("RESULT: FAILED");
            return 2;
        }
        if (productOverride != null && !productOverride.trim().isEmpty()
'''
patch(path, old, new)

# 3) .NET: decode both UTF-16 and ASCII metadata strings and extract product tokens
# even when they are embedded in a CSV/key-value string.
path = 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
old = '''    static Set<String> extractUtf16Ascii(File f) {
        LinkedHashSet<String> out=new LinkedHashSet<String>(); if(f==null||!f.isFile())return out;
        try{byte[]b=Files.readAllBytes(f.toPath());for(int parity=0;parity<2;parity++){StringBuilder s=new StringBuilder();for(int i=parity;i+1<b.length;i+=2){int c=b[i]&255,z=b[i+1]&255;if(z==0&&c>=32&&c<=126)s.append((char)c);else{if(s.length()>=4)out.add(s.toString());s.setLength(0);}}if(s.length()>=4)out.add(s.toString());}}catch(Exception ignore){}return out;
    }
'''
new = r'''    static Set<String> extractUtf16Ascii(File f) {
        LinkedHashSet<String> raw = new LinkedHashSet<String>();
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (f == null || !f.isFile()) return out;
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            // .NET #US strings are commonly UTF-16LE.
            for (int parity = 0; parity < 2; parity++) {
                StringBuilder s = new StringBuilder();
                for (int i = parity; i + 1 < b.length; i += 2) {
                    int c = b[i] & 255, z = b[i + 1] & 255;
                    if (z == 0 && c >= 32 && c <= 126) s.append((char) c);
                    else { if (s.length() >= 4) raw.add(s.toString()); s.setLength(0); }
                }
                if (s.length() >= 4) raw.add(s.toString());
            }
            // Metadata/string heaps can also expose plain ASCII/UTF-8 runs.
            StringBuilder ascii = new StringBuilder();
            for (byte value : b) {
                int c = value & 255;
                if (c >= 32 && c <= 126) ascii.append((char) c);
                else { if (ascii.length() >= 4) raw.add(ascii.toString()); ascii.setLength(0); }
            }
            if (ascii.length() >= 4) raw.add(ascii.toString());
        } catch (Exception ignore) { return out; }

        Pattern token = Pattern.compile("(?i)(?<![A-Z0-9])(itmcIEC|DS\\d{4}|YX\\d{4}(?:\\d{2})?|GM\\d{3}(?:\\d{2})?)(?![A-Z0-9])");
        for (String text : raw) {
            out.add(text);
            Matcher m = token.matcher(text);
            while (m.find()) {
                String x = m.group(1);
                out.add("itmcIEC".equalsIgnoreCase(x) ? "itmcIEC" : x.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }
'''
patch(path, old, new)

# 4) Modern batch table: show the RegStr actually parsed from ITMC.Web.dll and
# expose fail-closed status rather than a hard-coded dash.
path = 'src/main/java/LicenseRecoverModernGUI.java'
old = '''                    String verify = d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_MODERN
                            ? "待执行: 写回解密校验" : "兼容方式一: 不适用";
                    batchModel.addRow(new Object[]{child.getName(), kind,
                            valueOrDash(d.versionId), valueOrDash(d.productName), valueOrDash(d.productName),
                            "—", "config.xml", verify, "待处理", info.binDir.getAbsolutePath()});
'''
new = '''                    String dotNetRegStr = d.productName == null ? null
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
                    batchModel.addRow(new Object[]{child.getName(), kind,
                            valueOrDash(d.versionId), valueOrDash(d.productName), valueOrDash(d.productName),
                            valueOrDash(dotNetRegStr), "config.xml", verify, dotNetStatus,
                            info.binDir.getAbsolutePath()});
'''
patch(path, old, new)

# 5) Regression coverage: binary-gated Java identity + mixed ASCII .NET product lists.
path = 'src/test/java/RefactorSmokeTest.java'
old = '''        check(!ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.recoveryReadiness.contains("目录"),
                "DS2406 stays blocked when fixture lacks directory registration-id evidence");
'''
new = '''        check(!ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.recoveryReadiness.contains("目录"),
                "DS2406 stays blocked when fixture lacks directory registration-id evidence");
        Files.createDirectories(ds2406Root.resolve("WEB-INF/classes"));
        Files.write(ds2406Root.resolve("WEB-INF/classes/RegistrationEvidence.class"),
                "DS24".getBytes(StandardCharsets.US_ASCII));
        ds2406Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds2406Root.toFile());
        check("DS24".equals(ds2406Plan.authorizationFamily)
                        && "DS24".equals(ds2406Plan.runtimeProductId)
                        && ds2406Plan.automaticRecoveryReady
                        && ds2406Plan.regStr == null
                        && ds2406Plan.regStrSummary().contains("动态"),
                "DS2406 accepts target-binary family evidence and defers RegStr to target component");
'''
patch(path, old, new)

old = '''        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项")
                        && !xmtPlan.automaticRecoveryReady,
                "XMT directory RegStr may be displayed but execution stays blocked until identity is proven");
'''
new = '''        check(xmtPlan.generation.contains("XMT") && xmtPlan.regStrSummary().startsWith("8 项")
                        && !xmtPlan.automaticRecoveryReady,
                "XMT directory RegStr may be displayed but execution stays blocked until identity is proven");
        Files.write(xmtClasses.resolve("RegistrationEvidence.class"),
                "XMT01".getBytes(StandardCharsets.US_ASCII));
        xmtPlan = LicenseRecoverModernGUIJavaPlan.inspect(xmtRoot.toFile());
        check("XMT01".equals(xmtPlan.authorizationFamily)
                        && "XMT01".equals(xmtPlan.runtimeProductId)
                        && xmtPlan.automaticRecoveryReady,
                "XMT family becomes executable only after exact token appears in target bytecode");
'''
patch(path, old, new)

old = '''        check(ds501Plan.runtimeProductId == null && ds501Plan.regStr == null
                        && !ds501Plan.automaticRecoveryReady,
                "DS501 stays fail-closed without directory identity/RegStr evidence");
'''
new = '''        check(ds501Plan.runtimeProductId == null && ds501Plan.regStr == null
                        && !ds501Plan.automaticRecoveryReady,
                "DS501 stays fail-closed without directory identity/RegStr evidence");
        Files.write(ds501Classes.resolve("RegistrationEvidence.class"),
                "DS501 DS50109".getBytes(StandardCharsets.US_ASCII));
        ds501Plan = LicenseRecoverModernGUIJavaPlan.inspect(ds501Root.toFile());
        check("DS501".equals(ds501Plan.authorizationFamily)
                        && "DS50109".equals(ds501Plan.runtimeProductId)
                        && ds501Plan.automaticRecoveryReady
                        && ds501Plan.regStrSummary().contains("动态"),
                "DS501 family/runtime IDs require exact target-binary tokens and use runtime RegStr probe");
'''
patch(path, old, new)

old = '''        check(yx305Plan.runtimeProductId == null
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && !yx305Plan.automaticRecoveryReady,
                "YX0305 directory RegStr is retained for diagnostics but execution is blocked without identity proof");
'''
new = '''        check(yx305Plan.runtimeProductId == null
                        && "QT100101,QT100102".equals(yx305Plan.regStr)
                        && !yx305Plan.automaticRecoveryReady,
                "YX0305 directory RegStr is retained for diagnostics but execution is blocked without identity proof");
        Files.write(yx305Classes.resolve("RegistrationEvidence.class"),
                "YX0305 YX030506".getBytes(StandardCharsets.US_ASCII));
        yx305Plan = LicenseRecoverModernGUIJavaPlan.inspect(yx305Root.toFile());
        check("YX0305".equals(yx305Plan.authorizationFamily)
                        && "YX030506".equals(yx305Plan.runtimeProductId)
                        && yx305Plan.automaticRecoveryReady,
                "YX0305 family/runtime IDs become ready only with target-binary evidence");
'''
patch(path, old, new)

old = '''        check("DS0101,DS0107,DS0110,DS0112".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                dsFixture.toFile(), "itmcIEC")),
                "one-click derives DS01xx local product list");
'''
new = '''        check("DS0101,DS0107,DS0110,DS0112".equals(
                        LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                dsFixture.toFile(), "itmcIEC")),
                "one-click derives DS01xx local product list");
        Path dsAsciiFixture = base.resolve("DS01-Web-ascii.dll");
        Files.write(dsAsciiFixture,
                "ProName=itmcIEC;RegStr=DS0101,DS0105,DS0107".getBytes(StandardCharsets.US_ASCII));
        check("itmcIEC".equals(LicenseRecoverModernGUIAutoRecovery.detectProduct(
                        dsAsciiFixture.toFile(), "DS0101"))
                        && "DS0101,DS0105,DS0107".equals(
                                LicenseRecoverModernGUIAutoRecovery.detectProductList(
                                        dsAsciiFixture.toFile(), "itmcIEC")),
                ".NET parser extracts product tokens from ASCII/CSV metadata instead of requiring whole-string equality");
'''
patch(path, old, new)

# 6) CI source guards for the new non-fallback path.
path = 'scripts/verify.ps1'
anchor = '''if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }
'''
extra = '''if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }
if (-not $planSource.Contains('hasDirectoryBinaryToken')) { throw 'Java target-binary identity proof is missing.' }
if (-not $coreSource.Contains('probeTargetRegStr')) { throw 'Java target RegisterMain.getRegInfo RegStr probe is missing.' }
'''
patch(path, anchor, extra)

print('directory-evidence v1.2.14 patch prepared')
