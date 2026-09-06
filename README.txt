ITMC 云实训平台 离线授权恢复工具
=================================

背景说明
--------
本软件（ITMC 云实训平台，产品序列号如 YT00123）原本通过联网授权服务器
（regservice.itmc.cn）校验授权。厂商跑路、授权服务器关闭后，软件因联网
校验失败而无法正常使用。

Java 版方式一和方式二利用软件"自带"的本地授权通道（config.xml 的 regType=1）写入配置；
.NET 版方式一只修改授权服务地址，防止软件自动联网校验，不修改 DLL；方式三会在备份后修改授权 DLL。
Java 版方式一/二写入后：
  - 启动时 RegisterMain.checkReInfo() 先走本地校验并直接通过；
  - 联网的 CheckNet()（SOAP 到 regservice.itmc.cn）不会再触发；
  - 每天 01:00 的定时重检同样离线通过。
.NET 版方式一只负责阻断自动联网请求；需要写入本地授权时使用方式二，或使用方式三移除 DLL 中的联网授权代码。

.NET 版支持
-----------
本工具同时支持 ITMC 的 .NET 版（ASP.NET 应用，bin 目录内含 itmcRegedit.dll /
ITMC.Web.dll，产品号如 YX0302）。

  - 方式一（防止软件自动联网校验）：只修改应用实际使用的授权配置文件，
    将 `reg/Service` 以及 `Web.config`、`*.dll.config`、`*.exe.config` 中指向
    `regservice.itmc.cn` 的地址改为 `http://127.0.0.1:9/Service.asmx`；ASP.NET 取网站根目录，
    独立 .NET 程序优先取 bin 目录。此方式不生成本地授权、不修改任何 DLL，写入前默认备份，
    可用 `--dry-run` 预览；其它服务地址（例如 AI 服务）不会改动；
  - 方式二（离线授权码）：新版项目按应用产品号使用新版协议；读取 .NET 根目录
    config.xml 的 SoftVersionID，匹配 DS01xx 系列时自动使用旧版 itmcIEC 协议（申请号用 itmcsoft 解码，
    授权码用 itmc + 应用 ProName 生成，内容末尾写入实际 SoftVersionID（DS0101 的密钥为
    itmcitmcIEC），走应用「本地注册」界面激活。旧版 ASP.NET 必须先在注册页取得申请号；
  - 方式三（移除联网授权代码）：内嵌 FOAP 脱壳算法（不依赖外部脱壳工具），
    自动脱壳 itmcRegedit.dll 后用 dnlib 补丁 RegeditMain 的
    CheckReInfo/CheckNet/getNetRegInfo/RegNOWebCheck/CheckLocalReg/getLocalRegInfo 并替换。
    部分新版 DLL（如 GM00401）在 FOAP 壳之上还有商业混淆器的"第二层方法加密"
    （方法体是分发桩、真实体 RC4 加密存于文件，且 RegeditMain 静态构造器带反篡改
    校验）。工具会自动检测并：解开方法加密 → 清空反篡改静态构造器 → 补丁 →
    对引用运行时才解析 token 的混淆器运行时桩自动中和（补丁后为死代码），
    补丁后用应用自身 CheckReInfo 自校验，失败自动恢复 .bak。

使用方法：选择 .NET 应用的 bin 目录（含 itmcRegedit.dll 的那个目录，如
D:\...\app\bin），GUI/CLI 自动识别为 .NET 版并调用内嵌助手。
方式三执行前请先停止 IIS 应用池（否则 DLL 被占用无法替换）；工具会自动备份
itmcRegedit.dll.<时间戳>.bak；同目录的大写 `ITMC.Regedit.dll` 不会被修改。
若部署的 .NET 产品号不是 YX0302，请在「产品主编号」手动填写（会传给助手）。

文件组成
--------
  GUI 版（推荐）：
    LicenseRecoverGUI.jar   自包含图形界面程序（双击即可用）
    run_gui.bat             启动 GUI 的一键脚本
  命令行版：
    LicenseRecover.java     源码
    LegacyDotNetProtocol.java  DS01xx .NET 旧版授权协议适配源码
    LicenseRecover.jar      命令行程序（含方式一/二/三）
    run.bat                 CLI 方式一/二一键脚本
    run_removenet.bat       CLI 方式三（移除联网授权）一键脚本
  .NET 版内嵌助手：
    LicenseRecover.NET\LicenseRecover.NET.exe    .NET 版助手（net48）
    LicenseRecover.NET\LicenseRecover.NET.exe.config
    LicenseRecover.NET\dnlib.dll                 方式三补丁用（与 exe 同目录）
  （Java 版 / .NET 版共用同一个 GUI 与 CLI，自动识别应用类型）

  Java 环境：工具自动检测 Java（java.conf 手动指定 / JAVA_HOME / 应用附近 Tomcat 的 catalina.bat JAVA_HOME / 常见目录 / PATH）。
  若服务器 Tomcat 自带 Java 但未配系统环境变量，工具会从应用目录向上自动找到 Tomcat 并读取其 Java。
  本文件 README.txt

使用方法（GUI 版，Windows 7 / Server 2008 可用）
-----------------------------------------------
  1) 双击 run_gui.bat（或直接双击 LicenseRecoverGUI.jar）；
  2) 在窗口中点击"选择..."，选部署了该软件的"应用根目录"
     （即包含 WEB-INF 的目录，软件本身所在位置）；
  3) 点"检测环境"，确认产品主编号自动识别正确；
  4) 点"生成离线授权并写入"，日志区出现 RESULT: OK 即成功；
  5) 重启应用服务即可正常使用。
  选项说明：
    写入前备份原配置   默认勾选，安全建议保留；
    Java 版把授权服务地址指向本地   默认勾选，杜绝任何联网请求；
    只预览不写入       勾选后可先看效果，不实际写入。

使用方法（命令行版）
--------------------
  Java 版方式一（放应用根目录双击）：
    1) 在目标服务器上，把 run.bat 和 LicenseRecover.jar 复制到应用根目录
       （即包含 WEB-INF 的那个目录），双击 run.bat；
    2) 看到 RESULT: OK 即成功，重启应用服务即可正常使用。
  Java 版方式一（带参数运行，可从任意位置）：
    1) 打开命令行，进入本工具目录；
    2) 执行：  run.bat D:\server\cloud_training
       （参数为应用根目录，即包含 WEB-INF 的目录；工具会自动向上查找）；
    3) Java 版看到 RESULT: OK 即成功，重启应用服务即可正常使用；
       .NET 版默认执行方式一“防止软件自动联网校验”，只修改授权配置文件，不修改 DLL。

  .NET 版方式一（防止软件自动联网校验）：
    java -jar LicenseRecover.jar --block-net <ASP.NET 根目录或 bin 目录>
    也可以直接把 .NET 应用目录传给 run.bat；工具会自动定位 bin、网站根目录 config.xml/Web.config
    和 bin 中的 DLL 配置文件。
    看到“自动联网授权校验已由配置阻断”后，重启应用服务使配置生效。

方式二：生成离线授权码（在应用注册界面激活）
--------------------------------------------
软件自带的「本地注册」界面（Java 常见路径为 /softRegister?type=local，ASP.NET 项目通常为网站内的 Regester.aspx）需要填写：
  - 注册申请号（sequenceNumber）：应用点"获取申请号"生成，绑定本机主板号；
  - 离线授权码（registerCode）：厂商根据申请号签发。
本工具可以根据申请号生成离线授权码，再交由应用页面提交：

   GUI 版：在界面下方的"方式二"区域填写申请号后点"生成离线授权码"，
          得到授权码，点"复制授权码"，粘贴进应用注册界面提交即可。
   命令行版：
          java -cp "<应用根目录>\WEB-INF\lib\*;LicenseRecover.jar" \
                LicenseRecover --gencode <应用根目录> [--seq <已获取的申请号>]
           （Java 可不带 --seq 在本机自动生成申请号；ASP.NET 必须先从网站本地注册页取得申请号，再传入 --seq）
           .NET 版也可直接运行：java -jar LicenseRecover.jar --gencode <ASP.NET 根目录> --seq <申请号>

   说明：常规新版 .NET 项目按应用产品号使用新版协议；工具读取 SoftVersionID，只有匹配
         DS01xx 时才切换到旧版 itmcIEC 协议；YX030204 这类 YX03xx 版本继续使用新版协议。
         旧协议使用 itmcsoft 解码申请号、
         用 itmc + ProName 生成固定字段授权码，并在授权内容末尾写入 SoftVersionID
         （DS0101 的密钥为 itmcitmcIEC）。DS0101 的 Web.config productName 不是授权产品号，
         不应再按 YX0302 生成。ASP.NET 项目必须先从网站本地注册页取得申请号；提交和写入
         仍由应用自己的注册页完成，工具进程不代替该 Web 上下文执行 DoRegistry 自校验。
         旧协议输出的授权码不是 JSON，成功后把它粘贴到对应 DS01xx 应用的「本地注册」页提交。
   提示：ASP.NET 应用提交成功后会由网站把授权写入根目录 config.xml；如需彻底移除联网路径，
         请执行方式三。Java 应用仍可按需使用方式一把授权服务地址指向本地。

跨机器使用（软件在云服务器，本机生成授权码）
--------------------------------------------
授权码绑定的是"申请号所属主机"的主板号。因此：
  - 不能在本机"凭空"生成授权码直接贴到服务器 —— 会因主板号不匹配而失败；
  - 正确做法是让工具基于服务器的申请号生成授权码：

    1) 在服务器上打开应用的「本地注册」页面，点"获取申请号"，
       复制显示的申请号；
    2) 在本机运行工具，把这个申请号填入：
         GUI 版：方式二面板的"注册申请号"输入框，再点"生成离线授权码"；
         CLI 版：LicenseRecover --gencode --seq <服务器的申请号>
    3) 工具会输出一个"绑定该服务器"的离线授权码；
    4) 把这个授权码粘贴到服务器注册页面的"离线授权码"输入框提交即可。
       （申请号保持用服务器页面上获取的那个，不需要改）

  CLI 完整命令示例（在本机）：
    java -cp "<你本机某处>\WEB-INF\lib\*;LicenseRecover.jar" \
         LicenseRecover --gencode --seq <服务器上获取的申请号>
  说明：工具读取服务器申请号后，会自动识别并绑定其主机码，跨机器属正常，
        输出中会明确提示"授权码将绑定申请号所属的那台服务器"。

  重要提示：在本机（没有软件文件）生成授权码，直接使用 GUI 版即可——
  LicenseRecoverGUI.jar 是自包含程序，把 ITMCReg 等类打包在内，本机
  双击 run_gui.bat 就能用，无需任何软件的 WEB-INF 文件。
  （CLI 版需要应用 lib 是因为它还要支持"方式一"直接写 config.xml。）

方式三：移除联网授权代码（暴力，直接改写字节码）
--------------------------------------------------
原理：用 Javassist（应用自带 javassist-3.21.0-GA.jar）直接改写字节码——
  1) RegisterUtil.checkRegister()   启动+每天定时校验的总入口 → 改为直接标记"已注册"；
  2) RegisterMain（ITMCReg.jar 内） 所有联网授权方法 → 改为不联网的短路实现
     （CheckNet/getReditInfo/doNetRegistry/RegNOWebCheck/writeRegisterUser/checkReInfo）。
不改动程序其它逻辑，无需任何授权码，重启即"已注册"。

  GUI 版：界面下方"方式三"面板 → "扫描识别联网授权文件"（先看识别结果）→
          "移除联网授权并回写"。
  命令行版：
    run_removenet.bat <应用根目录>
    或  java -cp "<应用根目录>\WEB-INF\lib\javassist-3.21.0-GA.jar;LicenseRecover.jar" \
              LicenseRecover --remove-net <应用根目录>
    仅扫描识别（不修改）：
    java -cp "...javassist-3.21.0-GA.jar;LicenseRecover.jar" LicenseRecover --scan-net <应用根目录>

  重要：
  - 执行前请先停止应用服务（Tomcat），否则 ITMCReg.jar 被占用无法替换；
  - 命令行版必须用上面的"最小 classpath"（javassist + LicenseRecover.jar），
    不能带 WEB-INF\lib\* 通配，否则 ITMCReg.jar 会被本进程占用；
  - 若目标类仍是 Virbox BCE 保护（minor version=32768），本工具会**自动脱壳**
    （内置密钥流还原 Code 属性 + 恢复 minor=0），无需额外操作；
  - 工具会自动备份：RegisterUtil.class.<时间戳>.bak / ITMCReg.jar.<时间戳>.bak。

批量应用（一个父目录下多个软件一键恢复）
--------------------------------------------------
适用场景：某个文件夹下按产品代号放了多个 ITMC 软件（如 YX030505、YT00123、
YX030201 等，每个代号一个文件夹，内含一个 ITMC 应用——Java 或 .NET 版）。
工具自动扫描每个子目录、识别应用类型（Java: WEB-INF/lib/ITMCReg*.jar——兼容 ITMCReg.jar
或带版本号的 ITMCReg-1.0.5.jar 等；.NET: itmcRegedit.dll），逐个执行所选方式并报告结果。

  GUI 版：切到「批量应用」标签 → 选择父目录 → 点"扫描子目录"（列表显示
          每个应用与类型）→ 选方式（方式一写本地授权 / 方式三移除联网）→
          "批量执行"，每行状态实时更新；方式一对 .NET 只阻断联网校验，对 Java 写入本地授权。
  命令行版：
    java -jar LicenseRecover.jar --batch <父目录>                # 方式一（.NET防止联网 / Java写本地授权）
    java -jar LicenseRecover.jar --batch <父目录> --remove-net   # 方式三（移除联网代码）
    java -jar LicenseRecover.jar --batch <父目录> --scan-net     # 仅扫描识别
    可选：-p <产品号>（所有应用统一指定产品号）；--dry-run（只预览不写入）
    批量存在失败项目时进程返回非 0；成功完成或仅跳过非 ITMC 目录时返回 0。

  说明：
  - 每个子目录的识别与恢复与"单个应用"完全一致：Java 应用自动识别产品号，
    若 ITMCReg.jar 带 Virbox 壳会自动脱壳；.NET 应用自动调用内嵌助手；
  - 批量方式三执行前请先停止各应用服务；工具逐个备份后替换；
  - 已补丁过的应用（联网授权代码已移除）再次扫描会代码级识别并标记"已补丁"，执行时跳过重复修补；
  - 目录中含非 ITMC 软件文件夹时，扫描会列出并跳过（状态显示 SKIPPED）。
  - 批量方式一对 .NET 逐个修改实际使用的授权配置文件以阻断联网，对 Java 逐个写入本地授权；

工具做了什么
------------
Java 版方式一在 <应用根目录>\WEB-INF\lib\config.xml 中写入：
  reg/regType      = 1                （启用本地授权模式）
  reg/regName      = <加密的永续授权>  （有效期至 2099 年，不限并发/班级）
  reg/WebSerUserID = itmc             （阻止联网申请授权的残留逻辑）
  reg/Service      = http://127.0.0.1:9/Service.asmx（把授权服务地址指向本地端口）

.NET 版方式一会在应用实际使用的配置中写入/更新：
  config.xml       reg/Service = http://127.0.0.1:9/Service.asmx
  Web.config 等    仅把 regservice.itmc.cn 的授权服务地址改为上述本机地址
它不会写入 regType/regName，也不会修改任何 DLL；每个被改动的配置文件都会在写入前备份。

可选参数（CLI / GUI 单应用均可）：
  --no-block-net   Java 方式一不把 reg/Service 指向本地（默认写）
  --no-backup      写入前不备份原 config.xml（默认备份，建议保持）

新架构 QT3xxx（Spring Boot，如数据分析平台 QT30103）：
  - 应用在 webapp 根目录读注册 config.xml（RegisterMain 的 basePath=getRealPath("/")），
    不在 WEB-INF/lib 下；加密密钥用原始产品号（如 QT30103）而非映射主编号。
  - 工具检测到 webapp 根存在 data/config.xml 时自动识别为新架构：产品号直接用
    systemConfig.yml 的 VersionID 或 data/config.xml 的 SystemSoft/SoftVersionID，
    并同时把授权写到 <应用根目录>\config.xml（webapp 根）与 WEB-INF\lib\config.xml，
    两处均用应用自身 RegisterMain 自校验。

安全性说明
----------
  - Java 方式一/二不修改 / 不替换任何 class / jar 文件，不注入代码；
  - .NET 方式一只修改授权配置文件中的服务地址，不修改 / 不替换任何 DLL；
  - 生成的授权密文由软件自带的 itmc.regedit.GetRegisterCode 加密，
    与应用厂商签发的本地授权格式完全一致，软件自身的校验逻辑即可验证通过；
  - Java 方式一运行后会用软件自身的 RegisterMain.checkReInfo() 做自校验；
    .NET 方式一以配置写入成功为判断依据，重启应用服务后生效；
  - 若误写，可用各配置文件旁的 `.bak` 备份还原。

产品号说明
----------
工具会读取应用根目录 systemConfig.yml 的 global.system.VersionID 自动识别
产品主编号（YT00123 系 → QT1001；YT00128/129/139/132/141/126/154 系 → QT04）。
  GUI 版：对应"产品主编号"输入框（可手动改）与"只预览不写入"勾选框；
  命令行版：强制指定用 -p QT04，预览用 --dry-run。

注意事项
--------
  - Java 方式一（直接写入 config.xml）：必须在部署该软件的服务器上运行；
  - .NET 方式一（防止软件自动联网校验）：只修改授权配置文件，不修改 DLL；
  - 方式二（生成离线授权码）：可在本机运行，但需先用服务器的申请号（见"跨机器使用"）；
  - 方式三（移除联网授权代码）：需停止应用服务后执行，备份在 WEB-INF 内；
  - 需要本机装有 JRE 8（运行该软件本身就需要）；
  - 若授权修复后仍有功能异常，通常是数据库或 AI/GPT/题库同步等独立服务问题，
    与本工具无关，按提示排查即可。
