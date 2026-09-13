from pathlib import Path

src = Path('src/main/java/LegacyJavaRegistrationMetadata.java')
text = src.read_text(encoding='utf-8')
start = text.index('    private static GlobalScan scanGlobal(File global, String softId) {')
end = text.index('    private static byte[] readVirboxKey() {', start)
replacement = r'''    private static GlobalScan scanGlobal(File global, String softId) {
        if (global == null || !global.isFile()) return new GlobalScan(false, true, null);
        try {
            ClassFile cf = new ClassFile(Files.readAllBytes(global.toPath()));
            byte[] code = cf.clinitCode;
            if (code == null) return new GlobalScan(true, false, null);
            if (cf.minor == 32768) {
                byte[] key = readVirboxKey();
                if (key == null || key.length < code.length) return new GlobalScan(true, false, null);
                code = code.clone();
                for (int i = 0; i < code.length; i++) code[i] ^= key[i];
            }

            String productMain = null;
            String productMainNum = null;
            int beanLocal = -1;
            Mapping pending = null;
            int pendingLocal = -1;
            for (int i = 0; i < code.length - 4; i++) {
                int op = code[i] & 0xff;

                // A setter sequence is only a candidate. Some production Global.<clinit>
                // blocks construct a RegisterProductBean and fill all three fields but
                // never add that object to registerProductBeans. BKSM4 contains exactly
                // such a stale QT04/QT0435 row before its committed PT02/QT0445 row.
                // Reset candidate state whenever another bean allocation begins.
                if (op == 0xbb && i + 2 < code.length) { // new
                    String className = cf.className(u2(code, i + 1));
                    if (className != null && className.endsWith("RegisterProductBean")) {
                        productMain = null;
                        productMainNum = null;
                        beanLocal = -1;
                        pending = null;
                        pendingLocal = -1;
                    }
                }

                int cpIndex;
                int invokePos;
                if (op == 0x12) { // ldc
                    cpIndex = code[i + 1] & 0xff;
                    invokePos = i + 2;
                } else if (op == 0x13) { // ldc_w
                    cpIndex = u2(code, i + 1);
                    invokePos = i + 3;
                } else {
                    cpIndex = -1;
                    invokePos = -1;
                }
                if (cpIndex > 0 && invokePos + 2 < code.length
                        && (code[invokePos] & 0xff) == 0xb6) {
                    String value = cf.stringAt(cpIndex);
                    String method = cf.methodName(u2(code, invokePos + 1));
                    int local = loadedLocalBefore(code, i);
                    if (value != null && method != null) {
                        if ("setProductMain".equals(method)) {
                            productMain = value.trim();
                            productMainNum = null;
                            beanLocal = local;
                            pending = null;
                            pendingLocal = -1;
                        } else if ("setProductMainNum".equals(method) && local == beanLocal) {
                            productMainNum = value.trim();
                        } else if ("setProductNums".equals(method) && local == beanLocal) {
                            String productNums = value.trim();
                            if (containsCsv(productNums, softId)
                                    && !blank(productMain) && !blank(productMainNum)) {
                                pending = new Mapping(productMain, productMainNum, productNums,
                                        "Global.registerProductBeans");
                                pendingLocal = beanLocal;
                            } else {
                                pending = null;
                                pendingLocal = -1;
                            }
                        }
                    }
                }

                // Accept the candidate only when the very same local bean is actually
                // committed through the target's registerProductBeans.add(bean) call.
                // Merely seeing the setter constants is not executable registration data.
                if (pending != null && isRegisterProductBeansAdd(code, i, cf, pendingLocal)) {
                    return new GlobalScan(true, true, pending);
                }
            }
            return new GlobalScan(true, true, null);
        } catch (Throwable ignore) {
            return new GlobalScan(true, false, null);
        }
    }

    static int loadedLocalBefore(byte[] code, int pos) {
        if (code == null || pos <= 0) return -1;
        int previous = code[pos - 1] & 0xff;
        if (previous >= 0x2a && previous <= 0x2d) return previous - 0x2a; // aload_0..aload_3
        if (pos >= 2 && (code[pos - 2] & 0xff) == 0x19) return code[pos - 1] & 0xff; // aload n
        return -1;
    }

    static boolean isRegisterProductBeansAdd(byte[] code, int invokePos,
                                             ClassFile cf, int expectedLocal) {
        if (code == null || cf == null || expectedLocal < 0
                || invokePos < 0 || invokePos + 2 >= code.length) return false;
        int op = code[invokePos] & 0xff;
        if (op != 0xb9 && op != 0xb6) return false; // invokeinterface / invokevirtual
        int methodRef = u2(code, invokePos + 1);
        if (!"add".equals(cf.methodName(methodRef))
                || !"(Ljava/lang/Object;)Z".equals(cf.methodDescriptor(methodRef))) return false;
        String owner = cf.methodOwner(methodRef);
        if (owner == null || !(owner.equals("java/util/List")
                || owner.equals("java/util/Collection")
                || owner.equals("java/util/ArrayList"))) return false;

        int actualLocal = loadedLocalBefore(code, invokePos);
        if (actualLocal != expectedLocal) return false;
        int aloadStart = -1;
        int previous = code[invokePos - 1] & 0xff;
        if (previous >= 0x2a && previous <= 0x2d) aloadStart = invokePos - 1;
        else if (invokePos >= 2 && (code[invokePos - 2] & 0xff) == 0x19) aloadStart = invokePos - 2;
        if (aloadStart < 3 || (code[aloadStart - 3] & 0xff) != 0xb2) return false; // getstatic
        int fieldRef = u2(code, aloadStart - 2);
        return "registerProductBeans".equals(cf.fieldName(fieldRef));
    }

'''
text = text[:start] + replacement + text[end:]

class_anchor = '        List<String> stringConstants() {\n'
helpers = r'''        String className(int index) {
            return index > 0 && index < tag.length && tag[index] == 7 ? utf(a[index]) : null;
        }

        String methodOwner(int index) {
            if (index <= 0 || index >= tag.length || (tag[index] != 10 && tag[index] != 11)) return null;
            return className(a[index]);
        }

        String methodDescriptor(int index) {
            if (index <= 0 || index >= tag.length || (tag[index] != 10 && tag[index] != 11)) return null;
            int nat = b[index];
            if (nat <= 0 || nat >= tag.length || tag[nat] != 12) return null;
            return utf(b[nat]);
        }

        String fieldName(int index) {
            if (index <= 0 || index >= tag.length || tag[index] != 9) return null;
            int nat = b[index];
            if (nat <= 0 || nat >= tag.length || tag[nat] != 12) return null;
            return utf(a[nat]);
        }

'''
if class_anchor not in text:
    raise SystemExit('ClassFile stringConstants anchor missing')
text = text.replace(class_anchor, helpers + class_anchor, 1)
src.write_text(text, encoding='utf-8')

test = Path('src/test/java/RefactorSmokeTest.java')
t = test.read_text(encoding='utf-8')
anchor = '''        check("YT001".equals(LegacyJavaRegistrationMetadata.selectFallbackPrefix(\n                        Arrays.asList("QT1001", "DS26", "YT001", "YT00129"), "YT00138")),\n                "legacy runtime family is derived from target RegisterUtil constants");\n'''
if anchor not in t:
    raise SystemExit('RefactorSmokeTest insertion anchor missing')
block = r'''

        Path bksm4FixtureRoot = base.resolve("java-BKSM4-committed-row");
        Path bksm4FixtureClasses = bksm4FixtureRoot.resolve("WEB-INF/classes");
        Path bksm4FixtureSrc = base.resolve("java-BKSM4-fixture-src");
        Path beanSrc = bksm4FixtureSrc.resolve("com/taobao/bean/RegisterProductBean.java");
        Path globalSrc = bksm4FixtureSrc.resolve("com/common/global/Global.java");
        Files.createDirectories(beanSrc.getParent());
        Files.createDirectories(globalSrc.getParent());
        Files.createDirectories(bksm4FixtureClasses);
        Files.write(beanSrc, Arrays.asList(
                "package com.taobao.bean;",
                "public class RegisterProductBean {",
                "  public RegisterProductBean setProductMain(String v) { return this; }",
                "  public RegisterProductBean setProductMainNum(String v) { return this; }",
                "  public RegisterProductBean setProductNums(String v) { return this; }",
                "}"), StandardCharsets.UTF_8);
        Files.write(globalSrc, Arrays.asList(
                "package com.common.global;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import com.taobao.bean.RegisterProductBean;",
                "public class Global {",
                "  public static final List<RegisterProductBean> registerProductBeans = new ArrayList<RegisterProductBean>();",
                "  static {",
                "    RegisterProductBean bean = new RegisterProductBean();",
                "    bean.setProductMain(\"QT04\");",
                "    bean.setProductMainNum(\"QT0435\");",
                "    bean.setProductNums(\"BKSM4\");",
                "    bean = new RegisterProductBean();",
                "    bean.setProductMain(\"PT02\");",
                "    bean.setProductMainNum(\"QT0445\");",
                "    bean.setProductNums(\"BKSM4\");",
                "    registerProductBeans.add(bean);",
                "  }",
                "}"), StandardCharsets.UTF_8);
        Process fixtureCompile = new ProcessBuilder("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-d", bksm4FixtureClasses.toString(), beanSrc.toString(), globalSrc.toString())
                .inheritIO().start();
        check(fixtureCompile.waitFor() == 0, "compile BKSM4 committed-row regression fixture");
        LegacyJavaRegistrationMetadata.Mapping bksm4FixtureMapping =
                LegacyJavaRegistrationMetadata.inspect(bksm4FixtureRoot.toFile(), "BKSM4");
        check(bksm4FixtureMapping != null
                        && "PT02".equals(bksm4FixtureMapping.productMain)
                        && "QT0445".equals(bksm4FixtureMapping.productMainNum)
                        && "BKSM4".equals(bksm4FixtureMapping.productNums),
                "BKSM4 Global parser ignores stale uncommitted QT04/QT0435 bean and selects committed PT02/QT0445 row");

        Path uncommittedRoot = base.resolve("java-BKSM4-uncommitted-row");
        Path uncommittedClasses = uncommittedRoot.resolve("WEB-INF/classes");
        Path uncommittedSrc = base.resolve("java-BKSM4-uncommitted-src/com/common/global/Global.java");
        Files.createDirectories(uncommittedSrc.getParent());
        Files.createDirectories(uncommittedClasses);
        Files.write(uncommittedSrc, Arrays.asList(
                "package com.common.global;",
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import com.taobao.bean.RegisterProductBean;",
                "public class Global {",
                "  public static final List<RegisterProductBean> registerProductBeans = new ArrayList<RegisterProductBean>();",
                "  static {",
                "    RegisterProductBean bean = new RegisterProductBean();",
                "    bean.setProductMain(\"QT04\");",
                "    bean.setProductMainNum(\"QT0435\");",
                "    bean.setProductNums(\"BKSM4\");",
                "    bean = new RegisterProductBean();",
                "    bean.setProductMain(\"QT04\");",
                "    bean.setProductMainNum(\"QT0436\");",
                "    bean.setProductNums(\"YT00128\");",
                "    registerProductBeans.add(bean);",
                "  }",
                "}"), StandardCharsets.UTF_8);
        Process uncommittedCompile = new ProcessBuilder("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-cp", bksm4FixtureClasses.toString(), "-d", uncommittedClasses.toString(), uncommittedSrc.toString())
                .inheritIO().start();
        check(uncommittedCompile.waitFor() == 0, "compile BKSM4 uncommitted-row regression fixture");
        check(LegacyJavaRegistrationMetadata.inspect(uncommittedRoot.toFile(), "BKSM4") == null,
                "BKSM4 Global parser stays fail-closed when matching setter row is never committed to registerProductBeans");
'''
t = t.replace(anchor, anchor + block, 1)
test.write_text(t, encoding='utf-8')
