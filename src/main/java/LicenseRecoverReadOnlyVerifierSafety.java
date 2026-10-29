import java.util.List;

/** Observed unsafe-build deny records; NEVER original/Patch trust or license verdicts. */
public final class LicenseRecoverReadOnlyVerifierSafety {
    private LicenseRecoverReadOnlyVerifierSafety() { }
    static String rejection(List<LicenseRecoverReadOnlyInspection.Artifact> artifacts) {
        for (LicenseRecoverReadOnlyInspection.Artifact a : artifacts) {
            if (a.sha256.equals("ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa")
                    || a.sha256.equals("386726692ef56ba3e8953f17423e0e160b67dd5d9eec8ef2194e18d34f8d70e4")
                    || a.sha256.equals("05d848f45bd3cc63ea3d5e408e39c924c76e5d2088b73df0737f98a10477169e"))
                return "REJECTED_SIDE_EFFECTS: 当前注册 JAR 匹配已审计的非只读实现；CheckLocalReg 更新计次/配置，"
                        + "checkReInfo 可回退网络/HASP；见 docs/REAL_SAMPLE_JAVA_AUDIT.md";
            if (a.sha256.equals("735a7f868825111cd437cb5093eaa79f0a977330a79121fb2d18885290da743f")
                    || a.sha256.equals("6872470895de743974d5c565215d5b227678281fb9f863688d92b3f85856056d")
                    || a.sha256.equals("25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8")
                    || a.sha256.equals("48431b1c9be49ceba4f49d5dada802678d1ba033cbe939dc5650bb4174fb5d95")
                    || a.sha256.equals("c942c3c21466979e63eee1ffadbfe7273c83c2dc0a455f324c9da7e187953f46")
                    || a.sha256.equals("36f58b8657bce75a19356796f21c135104e28f5abc4bc9007f0d4ec517f479df")
                    || a.sha256.equals("222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725")
                    || a.sha256.equals("f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99"))
                return "REJECTED_SIDE_EFFECTS: 当前注册 DLL 匹配已审计的非只读实现；CheckLocalReg 存在配置/日志写入，"
                        + "CheckReInfo 可回退网络/HASP；见 docs/REAL_SAMPLE_DOTNET_AUDIT.md";
        }
        return "NOT_AUDITED: 当前组件尚无完整的原生验证只读安全证明";
    }
}
