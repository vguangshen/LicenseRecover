LicenseRecover v1.2.1
=====================

ITMC 云实训平台离线授权恢复工具
支持 Java 与 .NET 应用，并继续兼容 Java 8 / Windows 7 / Windows Server 2008。

推荐启动方式
------------
下载 LicenseRecover-latest.zip，解压后直接双击：

  LicenseRecoverGUI.exe

便携版内置 Amazon Corretto 8.492.09.2 Windows x64 JRE，不需要另外安装 Java。
从 v1.2.1 起，portable 包根目录只保留这一个 EXE 启动入口，不再提供旧 EXE 或 BAT 启动器。

v1.2.1 更新优化
---------------
- 在线更新下载时显示进度条、百分比和下载大小；
- 下载完成后显示 SHA-256 校验状态；
- 新 updater 安装后直接重新启动 LicenseRecoverGUI.exe，不再依赖 cmd/BAT 重启；
- 修复旧版在线更新完成后出现“文件名/路径语法不正确”“此时不应有 &&”等命令行错误的问题；
- 轻量 update 包保留一个最小 run_gui.bat，仅用于 v1.1.x/v1.2.0 updater 完成最后一次旧式重启；
- 新程序启动后会清理旧 LicenseRecoverGUI-legacy.exe 与 run*.bat 入口；
- CI 校验完整便携包根目录只有一个 EXE，且没有 BAT 启动入口。

注意：旧版本下载 v1.2.1 时运行的仍是旧 updater，因此这一次下载不会获得新进度条；升级到 v1.2.1 后，后续在线更新都会显示进度。

软件自动更新
------------
- 只检查 GitHub 最新正式 vX.Y.Z Release；
- 优先下载 LicenseRecover-update.zip，不重复下载 JRE；
- SHA-256 校验通过后才安装；
- 更新完成后自动重新启动 LicenseRecoverGUI.exe；
- 更新 ZIP 拒绝路径穿越；
- 轻量更新不会覆盖现有 jre\。

内部组件
--------
LicenseRecover.NET\LicenseRecover.NET.exe 是 .NET 兼容辅助组件，不是用户启动入口。

Release 资产
------------
  LicenseRecover-latest.zip   完整便携版，包含 jre\，唯一入口 LicenseRecoverGUI.exe
  LicenseRecover-update.zip   轻量自更新包，不包含 jre\
  SHA256SUMS.txt              两份 ZIP 的 SHA-256

更详细说明见 README.md 与 DEVELOPMENT.md。
