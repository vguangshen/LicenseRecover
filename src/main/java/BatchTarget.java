import java.io.File;

public final class BatchTarget {
    public enum Type { JAVA, DOTNET, NONE }

    public final String name;
    public final Type type;
    public final File appRoot;
    public final File libDir;
    public final File binDir;

    private BatchTarget(String name, Type type, File appRoot, File libDir, File binDir) {
        this.name = name;
        this.type = type;
        this.appRoot = appRoot;
        this.libDir = libDir;
        this.binDir = binDir;
    }

    public static BatchTarget javaTarget(String name, File appRoot, File libDir) {
        return new BatchTarget(name, Type.JAVA, appRoot, libDir, null);
    }

    public static BatchTarget dotNetTarget(String name, File binDir) {
        return new BatchTarget(name, Type.DOTNET, null, null, binDir);
    }

    public static BatchTarget skipped(String name) {
        return new BatchTarget(name, Type.NONE, null, null, null);
    }
}
