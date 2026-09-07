LicenseRecover v1.2.11
=====================

ITMC 云实训平台离线授权恢复工具
支持 Java 与 .NET 应用，并继续兼容 Java 8 / Windows 7 / Windows Server 2008。

推荐启动方式
------------
下载 LicenseRecover-latest.zip，解压后直接双击：

  LicenseRecoverGUI.exe

便携版内置 Amazon Corretto 8.492.09.2 Windows x64 JRE，不需要另外安装 Java。
从 v1.2.1 起，portable 包根目录只保留这一个 EXE 启动入口，不再提供旧 EXE 或 BAT 启动器。

v1.2.3 Java / 批量扫描修复
-------------------------
- 修复 v1.2.2 批量扫描在第一个 Java 应用处因 GUI classpath 缺少 LicenseRecover.class 而提前终止的问题；
- Java 授权计划预览改为纯文件/XML/JAR 元数据分析，不再要求把目标应用授权类加载进 GUI 进程；
- 每个批量子目录独立捕获检测异常，单个异常不会阻断后续应用；
- 扫描完成后显示目录总数、识别数、未识别数和异常数；
- 新增 DS28xx Java 代际映射：ProName=DS28；无显式 regInfo 时使用当前 SoftVersionID（例如 DS2802）作为 RegStr；
- 单应用与批量页面都会显示 DS28 / data-config、ProName、RegStr 和原生 RegisterMain 校验计划。

v1.2.2 Java / 批量识别增强
-------------------------
- 单个 Java 一键恢复区显示授权代际、ProName、RegStr、实际 config.xml 目标和授权组件；
- 写入后直接显示 RegisterMain.checkReInfo() 原生校验结果；
- 批量扫描表同步展示上述授权计划，每个 Java 应用可在执行前核对；
- 批量默认操作升级为与单个应用相同的一键恢复链路，并提供独立的备份、阻断联网、只预览选项；
- 批量 Java 完成后逐行显示 RegisterMain 原生校验是否通过。

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

v1.2.4 更新可靠性
-------------------
- 更新进度按 GitHub Release 资产的真实字节大小计算，显示 0%-100% 与 已下载/总大小；
- GitHub 下载连接超时提高到 30 秒，读取超时提高到 60 秒；
- 下载失败会自动重试 4 次，并在服务器支持时从已下载字节继续；
- 原生 EXE 启动内置 Java 时启用 Windows 系统代理发现，避免浏览器能访问 GitHub 而 Java 直连超时。

v1.2.5 Java classes-config 授权族识别
------------------------------------
- 不再把已确认的 classes-config 平台统称为“新式”；
- DS501xx：授权族 DS501，运行校验ID 为具体 SoftVersionID，缺少 regInfo 时 RegStr 使用具体 SoftVersionID；
- YX0305xx：授权族 YX0305，运行校验ID 为具体 SoftVersionID，RegStr 使用 classes/config.xml 显式 regInfo；
- 单应用与批量表均分别展示“授权族”和“运行校验ID”。

v1.2.6 GM00401 .NET 识别
-------------------------
- 实际 config.xml 确认 SoftVersionID=GM00401；
- ITMC.Web.dll 同时包含 GM00401 与 GM004 时确认授权族 GM004；
- RegStr 从 DLL 同族具体项提取，本样本为 GM00401；
- 忽略 Web.config 中与实际产品冲突的历史 productName=YX0301。

v1.2.7 QT40101 与执行资格保护
-------------------------------
- QT40101：具体版本 QT40101，授权族 / 运行校验ID 为 QT401；
- QT40101 未静态声明 RegStr，不再回退到 44 项通用列表；
- 授权族或 RegStr 未确认的 Java classes-config 项目显示“待确认”，推荐一键恢复与批量执行会自动阻止/跳过，确保不猜测写入；
- .NET Modern 产品号未确认时同样不会自动写回。

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
