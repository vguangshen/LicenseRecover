LicenseRecover v1.1.1
=====================

ITMC 云实训平台离线授权恢复工具

本工具支持 Java 与 .NET 应用，面向原授权服务不可用后的本地恢复场景。
当前发行继续兼容 Java 8，并保留 Windows 7 / Windows Server 2008 的 GUI 兼容目标。

免安装 Java
-----------
推荐下载 LicenseRecover-latest.zip。
该便携版已经内置：
  Amazon Corretto 8.492.09.2
  OpenJDK Runtime 1.8.0_492-b09
  Windows x64 JRE

解压后直接双击 run_gui.bat 即可，不需要另外安装 Java。
启动脚本优先使用 jre\bin\javaw.exe；只有内置 JRE 缺失时才回退系统 Java / JAVA_HOME。

JRE 保留 LICENSE、ASSEMBLY_EXCEPTION、THIRD_PARTY_README，并附带 JRE_SOURCE_NOTICE.txt。

推荐启动方式
------------
1. 解压完整发行包。
2. 双击 run_gui.bat。
3. 选择应用目录。
4. 点击“检测环境”。
5. 根据检测结果执行推荐操作。

入口说明
--------
  run_gui.bat               默认现代 GUI + 后台检查更新
  run_gui_modern.bat        显式启动现代 GUI
  run_gui_legacy.bat        旧 GUI 回退入口
  run.bat                   CLI 方式一 / 方式二
  run_removenet.bat         默认安全方式三入口
  run_removenet_safe.bat    显式安全方式三入口
  run_removenet_legacy.bat  旧方式三回退入口

软件自动更新
------------
从 v1.1.0 起，默认 modern GUI 会检查 GitHub 最新正式 Release。

v1.1.1 起：
  - 优先下载 LicenseRecover-update.zip（轻量更新包，不带 JRE）；
  - 已安装的 jre\ 会原样保留；
  - 如果旧 Release 没有轻量更新包，则回退下载 LicenseRecover-latest.zip；
  - 下载后必须通过 SHA-256 校验；
  - 更新完成后自动重新启动软件。

手动检查：
  run_gui.bat --update-only

软件使用 GitHub 公共 Releases 接口，因此仓库需要设置为 Public 后，未登录 GitHub 的客户端才能正常检查更新。

支持的应用
----------
Java：
  - 标准 WEB-INF / WEB-INF/lib 布局；
  - 已知的 WEB-INF/WEB-INF 嵌套布局；
  - 自动读取 SoftVersionID 和产品信息。

.NET：
  - ASP.NET / .NET 应用；
  - 自动定位 bin 与 itmcRegedit.dll 等授权组件；
  - DS01xx 系列根据 SoftVersionID 自动使用旧协议适配。

三种方式
--------
方式一：
  Java：写入本地授权相关配置，默认先建立 prewrite 备份。
  .NET：主要用于把失效的自动联网授权地址改为本地无响应地址，不修改 DLL。

方式二：
  根据申请号生成离线授权码，然后由应用自身的本地注册页面提交。

方式三：
  修改授权校验相关 Java 字节码或 .NET DLL。
  默认安全入口会先建立 prepatch 备份并校验，备份失败时不会继续写入。

安全保护
--------
默认现代路径包含：
  - 关键写入前强制备份；
  - 文件长度 + SHA-256 备份校验；
  - 备份失败即终止高风险写入；
  - Java 方式三 dry-run / 只扫描；
  - 统一子进程超时与结果处理；
  - modern / legacy 双入口；
  - 更新包 SHA-256 校验；
  - ZIP 路径穿越拦截；
  - 轻量更新不会覆盖 jre\。

Release 资产
------------
  LicenseRecover-latest.zip   完整便携版，包含 jre\
  LicenseRecover-update.zip   轻量自更新包，不包含 jre\
  SHA256SUMS.txt              两份 ZIP 的 SHA-256

固定 JRE runtime 支持资产：
  runtime-corretto8-8.492.09.2-win-x64

源码位置
--------
生产 Java 源码：src\main\java\
测试源码：      src\test\java\
验证脚本：      scripts\verify.ps1
版本说明：      release-notes\vX.Y.Z.md

更详细的开发说明见仓库 DEVELOPMENT.md。
