import java.io.File;

/**
 * 对一次 ITMC 应用检测结果的不可变描述。
 * GUI、CLI、批量模式共用该模型，避免依赖 Object[] 或散落的路径约定。
 */
public final class AppInfo {
    public enum Type { JAVA, DOTNET, UNKNOWN }

    public final Type type;
    public final File selectedPath;
    public final File appRoot;
    public final File libDir;
    public final File binDir;
    public final String softVersionId;

    private AppInfo(Type type, File selectedPath, File appRoot, File libDir,
                    File binDir, String softVersionId) {
        this.type = type;
        this.selectedPath = selectedPath;
        this.appRoot = appRoot;
        this.libDir = libDir;
        this.binDir = binDir;
        this.softVersionId = softVersionId;
    }

    public static AppInfo javaApp(File selectedPath, File appRoot, File libDir, String softVersionId) {
        return new AppInfo(Type.JAVA, selectedPath, appRoot, libDir, null, softVersionId);
    }

    public static AppInfo dotNetApp(File selectedPath, File appRoot, File binDir, String softVersionId) {
        return new AppInfo(Type.DOTNET, selectedPath, appRoot, null, binDir, softVersionId);
    }

    public static AppInfo unknown(File selectedPath) {
        return new AppInfo(Type.UNKNOWN, selectedPath, null, null, null, null);
    }

    public boolean isDetected() {
        return type != Type.UNKNOWN;
    }
}
