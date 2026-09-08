# LicenseRecover

ITMC 云实训平台离线授权恢复工具，支持 Java 与 .NET 应用。项目面向原授权服务不可用后的本地恢复场景，保留 Java 8 / Windows 7 / Windows Server 2008 兼容边界。

当前稳定版本：**v1.2.21**

## 下载

- **稳定版**：GitHub Releases 中最新正式 `vX.Y.Z`。
- **滚动版**：`rolling-latest`，对应最近一次通过完整 CI 的 `main` 构建。
- `LicenseRecover-latest.zip`：推荐给普通用户的完整便携版，内置 Windows x64 Java 8 JRE。
- `LicenseRecover-update.zip`：软件自更新使用的轻量包，不重复携带 JRE。
- `SHA256SUMS.txt`：同时校验上述两个 ZIP。

## 快速开始

1. 下载并解压 `LicenseRecover-latest.zip`。
2. Windows 下直接双击 **`LicenseRecoverGUI.exe`**。
3. 选择应用目录并点击“检测环境”。
4. 根据检测结果使用推荐操作。

**无需另外安装 Java。** 便携版自带 `jre/`，原生 `LicenseRecoverGUI.exe` 会优先使用 `jre\bin\javaw.exe`，仅在内置 JRE 缺失时回退到系统 Java / `JAVA_HOME`。

从 v1.2.1 起，完整便携包只保留一个用户可见的根目录 EXE 启动入口：`LicenseRecoverGUI.exe`。旧 `LicenseRecoverGUI-legacy.exe` 与根目录 BAT 启动器不再放入 portable ZIP。

## 内置 Java 运行时

便携版内置：

- Amazon Corretto 8.492.09.2
- OpenJDK Runtime `1.8.0_492-b09`
- Windows x64 JRE

运行时保留 `LICENSE`、`ASSEMBLY_EXCEPTION`、`THIRD_PARTY_README`，并在发行包中附带 `JRE_SOURCE_NOTICE.txt`。

为了避免把 100 MB+ JRE 文件重新提交进 Git 源码树，固定运行时单独维护在支持型 prerelease：

`runtime-corretto8-8.492.09.2-win-x64`

CI 构建正式 portable ZIP 时会下载该固定 runtime、校验 SHA-256，再将 `jre/` 打入最终包。
