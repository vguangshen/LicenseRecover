# v1.2.69 逐应用检测与验证记录

本机独立扫描输入副本的实际结果；代码、配置或机器变化会重新计算状态，不按目录名套用。两种最终 ZIP 均通过真实 GUI 5 秒时限检查和逐应用独立期望表比对：26 识别、0 异常、8 接受、18 未验证。

绿色属于独立 Acceptance 维度；不是目标原生方法实际执行证明、原版/Patch 来源认定或全部业务健康证明。

| 应用 | 状态 | 方法 |
| --- | --- | --- |
| DS0101 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| DS0105 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| DS0107 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| DS0109 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| DS110102 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS2406 | 授权条件通过，统一绿色 | audited-static-authorization-gate |
| DS2801 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS3402 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS3503 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS3504 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS3505 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS50111 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS50112 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| DS50113 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| QT100101 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| QT30103 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| QT30104 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| XMT0106 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| XMT0107 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| YT00138 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| YX0102 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| YX030107 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| YX030204 | 未验证，灰色 | file-only-sha256+format+reviewed-pair+audited-acceptance |
| YX030308 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| YX030321 | 授权条件通过，统一绿色 | audited-local-positive-predicate |
| YX030322 | 授权条件通过，统一绿色 | audited-local-positive-predicate |

通过项的精确输入与正向谓词分别见 [独立 .NET 审核](DOTNET_READ_ONLY_ACCEPTANCE.md)、[Java 静态门槛](JAVA_STATIC_ACCEPTANCE_PROOF.md)。

其余15个Java应用尚缺完整本地启动/机器/产品规则等价证明；YX0102为未审核协议分支，YX030107与YX030204尚无适用的已闭合本地正向证明，网络/硬件狗或其他授权方式未被据此否定。

最终包包含126个新编译主类/内部类，并逐项验证SHA-256与实际类加载位置；最终完整包、更新包均运行模型、文件证据、PE元数据、Java只读规划、UI、机器元数据、Java静态门槛及.NET正向谓词回归。未提交厂商二进制、真实配置、授权载荷、机器标识或私有结果文件。

审计辅助脚本曾误写DS2406 WEB-INF/web.xml，已披露并从独立保存的原始副本恢复字节与原修改时间，恢复SHA-256=23a521e1f90a8126d8406502a48f082678e192dd180b3d17ea199369e01f9ee2。之后重新核对最初11,458个源输入未发现残留差异；这不等于证明所有瞬时副作用不存在，不能把本轮称为全程只读。出错脚本已禁用，生产扫描器没有这条输出路径。
