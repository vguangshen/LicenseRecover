import java.io.File;
import java.util.function.Consumer;

/**
 * 方式三统一安全入口：Java / .NET 都必须先完成独立 prepatch 备份与内容校验。
 * 备份失败时绝不进入实际补丁逻辑。
 */
public final class SafeNetRemoverCLI {
    private SafeNetRemoverCLI() { }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].trim().isEmpty()) {
            System.err.println("用法: SafeNetRemoverCLI <Java应用根目录或.NET应用/bin目录>");
            System.exit(2);
            return;
        }

        File selected = new File(args[0]);
        AppInfo info = AppDetector.detect(selected);
        Consumer<String> log = s -> System.out.print(s.endsWith("\n") ? s : s + "\n");
        if (!info.isDetected()) {
            System.out.println("RESULT: FAILED —— 未识别到 ITMC Java / .NET 应用。");
            System.exit(2);
            return;
        }

        if (!PatchSafety.prepare(info, log)) {
            System.out.println("RESULT: FAILED —— 安全备份未完成，未修改任何目标文件。");
            System.exit(3);
            return;
        }

        if (info.type == AppInfo.Type.DOTNET) {
            System.out.println("安全预检完成，进入 .NET 方式三补丁流程 ...");
            LicenseRecover.dotNetMain(new String[]{"--remove-net", info.binDir.getAbsolutePath()},
                    info.binDir.getAbsolutePath());
            System.out.println("RESULT: OK —— .NET 方式三执行完成。");
            return;
        }

        boolean ok = NetRemover.removeNetValidation(info.appRoot.getAbsolutePath(), log);
        System.exit(ok ? 0 : 1);
    }
}
