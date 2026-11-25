/** Rejects retired entrypoints left behind by older installations. */
public final class SafeNetRemoverCLI {
    static final int EXIT_REMOVED = 2;
    static final String REMOVED_MESSAGE =
            "[REMOVED_OPERATION] 方式三（移除联网授权代码）已移除。请使用一键恢复；未修改任何目标文件。";

    private SafeNetRemoverCLI() { }

    static int reject() {
        System.err.println(REMOVED_MESSAGE);
        return EXIT_REMOVED;
    }

    public static void main(String[] args) {
        System.exit(reject());
    }
}
