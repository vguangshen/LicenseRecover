LicenseRecover v1.1.2
=====================

ITMC 云实训平台离线授权恢复工具

本工具支持 Java 与 .NET 应用，继续兼容 Java 8 / Windows 7 / Windows Server 2008。

免安装 Java
-----------
推荐下载 LicenseRecover-latest.zip。
便携版内置 Amazon Corretto 8.492.09.2 / OpenJDK Runtime 1.8.0_492-b09 Windows x64 JRE。
解压后直接双击 run_gui.bat 即可，不需要另外安装 Java。

v1.1.2 界面修复
---------------
- 长注册申请号 / 离线授权码不再把右侧按钮顶出窗口；
- 超出可见范围的内容保留在文本框内部，不再撑宽整个界面；
- GUI 底部右下角显示当前版本和“检查更新”按钮；
- run_gui_legacy.bat 也使用同样的布局修复和更新入口。

入口说明
--------
  run_gui.bat               默认现代 GUI + 后台检查更新 + 底部更新按钮
  run_gui_modern.bat        显式启动现代 GUI
  run_gui_legacy.bat        旧 GUI 回退入口（含长授权码布局修复）
  run.bat                   CLI 方式一 / 方式二
  run_removenet.bat         默认安全方式三入口
  run_removenet_safe.bat    显式安全方式三入口
  run_removenet_legacy.bat  旧方式三回退入口

软件自动更新
------------
- 只检查 GitHub 最新正式 vX.Y.Z Release；
- 优先下载 LicenseRecover-update.zip，不重复下载 JRE；
- SHA-256 校验通过后才安装；
- 更新完成后自动重启；
- 也可执行 run_gui.bat --update-only 手动检查。

注意：软件更新使用 GitHub 公共 Releases 接口，因此仓库需设置为 Public 后，未登录 GitHub 的客户端才能正常检查更新。

安全保护
--------
- 关键写入前强制备份；
- 文件长度 + SHA-256 备份校验；
- 备份失败即终止高风险写入；
- Java 方式三支持 dry-run / 只扫描；
- 更新包 SHA-256 校验；
- ZIP 路径穿越拦截；
- 轻量更新不会覆盖 jre\。

Release 资产
------------
  LicenseRecover-latest.zip   完整便携版，包含 jre\
  LicenseRecover-update.zip   轻量自更新包，不包含 jre\
  SHA256SUMS.txt              两份 ZIP 的 SHA-256

更详细说明见 README.md 与 DEVELOPMENT.md。
