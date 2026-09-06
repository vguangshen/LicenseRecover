LicenseRecover v1.0.0
=====================

ITMC 云实训平台离线授权恢复工具

本工具支持 Java 与 .NET 应用，面向原授权服务不可用后的本地恢复场景。
当前发行继续兼容 Java 8，并保留 Windows 7 / Windows Server 2008 的 GUI 兼容目标。

推荐启动方式
------------
1. 解压完整发行包。
2. 双击 run_gui.bat。
3. 选择应用目录。
4. 点击“检测环境”。
5. 根据检测结果执行推荐操作。

重要：新版 GUI 由 LicenseRecoverOverlay.jar + LicenseRecoverGUI.jar 组合加载。
请使用 run_gui.bat（或 run_gui_modern.bat）启动新版 GUI。
直接双击旧 LicenseRecoverGUI.jar 不等同于启动新版 GUI。

入口说明
--------
  run_gui.bat               默认现代 GUI
  run_gui_modern.bat        显式启动现代 GUI
  run_gui_legacy.bat        旧 GUI 回退入口
  run.bat                   CLI 方式一 / 方式二
  run_removenet.bat         默认安全方式三入口
  run_removenet_safe.bat    显式安全方式三入口
  run_removenet_legacy.bat  旧方式三回退入口

支持的应用
----------
Java：
  - 标准 WEB-INF / WEB-INF/lib 布局；
  - 已知的 WEB-INF/WEB-INF 嵌套布局；
  - 自动读取 SoftVersionID 和产品信息。

.NET：
  - ASP.NET / .NET 应用；
  - 自动定位 bin 与 itmcRegedit.dll 等授权组件；
  - DS01xx 系列根据 SoftVersionID 自动使用旧协议适配；
  - 其他产品继续使用新版路径。

三种方式
--------
方式一：
  Java：写入本地授权相关配置，默认先建立 prewrite 备份。
  .NET：主要用于把失效的自动联网授权地址改为本地无响应地址，不修改 DLL。

方式二：
  根据申请号生成离线授权码，然后由应用自身的本地注册页面提交。
  ASP.NET 应用通常应先在服务器上的注册页面获取申请号。

方式三：
  修改授权校验相关 Java 字节码或 .NET DLL。
  默认安全入口会先建立 prepatch 备份并校验，备份失败时不会继续写入。
  .NET 应用执行前建议停止 IIS 应用池或其他占用目标 DLL 的进程。

安全保护
--------
默认现代路径包含：
  - 关键写入前强制备份；
  - 备份文件长度校验；
  - SHA-256 内容校验；
  - 备份失败即终止高风险写入；
  - Java 方式三 dry-run / 只扫描路径；
  - 统一子进程超时与结果处理；
  - modern / legacy 双入口回退。

发行包文件
----------
  LicenseRecover.jar
  LicenseRecoverGUI.jar
  LicenseRecoverOverlay.jar
  LicenseRecover.NET\
  LicenseRecoverGUI-legacy.exe
  LicenseRecoverGUI.ico
  virbox_keystream.bin

  run.bat
  run_gui.bat
  run_gui_modern.bat
  run_gui_legacy.bat
  run_removenet.bat
  run_removenet_safe.bat
  run_removenet_legacy.bat

  README.md
  README.txt
  VERSION.txt
  CHANGELOG.md
  RELEASE_NOTES.md

版本与下载
----------
当前稳定版本：v1.0.0

正式稳定版：GitHub Releases 中的 vX.Y.Z。
滚动测试版：rolling-latest。
每个 Release 包含：
  LicenseRecover-latest.zip
  SHA256SUMS.txt

根目录不再跟踪生成的 LicenseRecover-latest.zip；正式包由统一验证链路生成后发布到 GitHub Release。

源码位置
--------
生产 Java 源码：src\main\java\
测试源码：      src\test\java\
验证脚本：      scripts\verify.ps1
Windows 验证：  scripts\verify.cmd
版本说明：      release-notes\vX.Y.Z.md

本地验证
--------
Windows：
  scripts\verify.cmd

PowerShell 7 / Linux / macOS：
  ./scripts/verify.ps1

验证链路会执行 Java 8 编译、smoke tests、deterministic overlay 构建、发行包组装、入口检查和 SHA-256 生成。

兼容与回退
----------
为避免影响既有部署，本版本没有移除旧 GUI / 旧方式三入口。
如果现代入口出现兼容性问题，可以使用 run_gui_legacy.bat 或 run_removenet_legacy.bat 回退。

更详细的开发说明见仓库 DEVELOPMENT.md。
