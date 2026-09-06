import java.util.function.Consumer;

/** Java 方式三安全入口：备份预检失败时绝不进入 NetRemover。 */
public final class SafeNetRemoverCLI {
    private SafeNetRemoverCLI() { }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].trim().isEmpty()) {
            System.err.println("用法: SafeNetRemoverCLI <Java应用根目录>");
            System.exit(2);
            return;
        }
        String appRoot = args[0];
        Consumer<String> log = s -> System.out.print(s.endsWith("\n") ? s : s + "\n");
        if (!PatchSafety.prepareJavaPatch(appRoot, log)) {
            System.out.println("RESULT: FAILED —— 安全备份未完成，未修改任何目标文件。");
            System.exit(3);
            return;
        }
        boolean ok = NetRemover.removeNetValidation(appRoot, log);
        System.exit(ok ? 0 : 1);
    }
}
