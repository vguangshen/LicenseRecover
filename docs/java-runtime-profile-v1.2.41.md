# Java runtime-profile sample matrix (v1.2.41 branch)

This note records the runtime ABI evidence used by the Java target-native registration refactor. The implementation must derive the constructor path from target startup bytecode and must not choose a constructor only because it happens to exist in `ITMCReg*.jar`.

| Sample | Startup `RegisterMain` ABI | Path semantics | Packed registration JAR |
| --- | --- | --- | --- |
| DS50109 | `(String product)` | target/default path | no |
| DS2406 | `(String product, String configPath)` | explicit webapp-root path | no |
| QT30103 | `(String product, String token, String configPath)` | explicit webapp-root path | no |
| DS2802 | three-arg token/path, then two-arg token/default fallback | explicit root then target/default | yes |
| DS3110 | `(String product, String token)` | target/default path derived by registration component | yes |
| YT00138 | `SysParamInit.getRealPath("/") -> RegisterUtil.checkRegister(root) -> (String product, String token, String configPath)` | explicit webapp-root path; product `YT001`, RegStr `YT00138` | yes |
| YT00129 | same wrapper/root relay and three-arg token/path ABI | explicit webapp-root path; product alias `QT04`, RegStr alias `QT0420` | yes |
| QT40101 | `RegisterListener -> SystemInitService.getRegisterMain(product,path) -> (String product, String token, String configPath)` | classpath root (`WEB-INF/classes`); startup consumes `getRegInfo()` without `checkReInfo()`; product `QT401`, RegStr `QT40101` | no (`ITMCReg-1.0.5.jar`) |
| XMT0102 | `ServletContext.getRealPath("/") -> RegisterListener.getRegisterMain(product,root,request) -> (String product, String token, String configPath)` | webapp-root path; family `XMT01`, startup ProductID `XMT0102`, RegStr `QT100110,QT100106` | yes |
| XMT0103 | same XMT Fastjson startup flow and three-arg token/path ABI | webapp-root path; family `XMT01`, startup ProductID `XMT0103`, RegStr `QT100102` | yes |

The supplied DS3110 registration component has SHA-256 `ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa`, identical to the DS2802 `ITMCReg.jar` sample. Its two-argument constructor derives the configuration directory from the registration class CodeSource, so the isolated host must preserve the original JAR CodeSource when restoring protected classes in memory.

For DS3110, target startup bytecode uses the two-argument token constructor and checks `checkReInfo()` followed by `getRegInfo().regStr.contains(soft_num)`. Directory evidence resolves the direct platform/runtime pair as `DS31` / `DS3110`; no catch-all RegStr or constructor guessing is allowed.

The supplied Virbox `.bin` keystream and the decoded `.hex` keystream are byte-identical and are used only for temporary in-memory restoration of protected target classes. Original application JAR/class files must remain unchanged by the target-native recovery path.

## YT001xx wrapper relay evidence

The supplied YT00138 and YT00129 WEB-INF samples use the same packed `ITMCReg.jar` generation as DS2802/DS3110. In this lineage the class that obtains the Servlet root (`SysParamInit`) is not the class that constructs `RegisterMain` (`RegisterUtil`). Runtime profiling therefore proves the cross-class relay `ServletContext.getRealPath("/") -> RegisterUtil.checkRegister(String)` before selecting the three-argument token/path constructor. A proven non-empty root relay must not invent the wrapper's internal two-argument/default-path branch as a Tomcat fallback.

YT00138 falls through the application's own registration mapping to product `YT001` with required startup membership token `YT00138`. YT00129 is different: its application mapping selects product `QT04` and required registration token `QT0420`. Consequently Java persistence verification must preserve the target-proven RegStr token(s), not globally require the current SoftVersionID to appear inside RegStr.

The branch smoke suite separately asserts this alias invariant: a persisted `QT0420` satisfies the YT00129 startup mapping even though the literal SoftVersionID `YT00129` is not present in that RegStr.

The supplied YT00129 archive is WEB-INF-only and does not itself provide the site-root `systemConfig.yml`/config identity. Production detection remains fail-closed in that incomplete layout; tests may only add `YT00129` root identity as an explicit fixture when modelling the missing deployment root.


## QT40101 classpath-root generation

The supplied QT40101 sample stores registration XML under `WEB-INF/classes/config.xml`. `config1.xml` proves registration product `QT401` while the concrete system is `QT40101`. Startup obtains the exploded classpath root with `ClassUtils.getDefaultClassLoader().getResource("").getPath()`, passes that path through `SystemInitService.getRegisterMain(product,path)`, and then consumes `RegisterMain.getRegInfo().getRegStr()`; this startup path does not call `RegisterMain.checkReInfo()`. The target-native verifier therefore mirrors RegInfo-only validation for this proven profile rather than forcing an API the application does not use at startup.


## XMT0102 JSON RegStr consumer

The supplied XMT0102 sample uses the packed ITMCReg generation shared with DS2802/DS3110/YT001xx. `WEB-INF/classes/config.xml` declares `SoftVersionID=XMT0102` and `regInfo=QT100110,QT100106`. Startup constructs `RegisterMain` with concrete `SystemInfo.registerId` (`XMT0102`) and the Servlet webapp root. It reads RegStr by serializing `getRegInfo()` through Fastjson and fetching the lower-case `regStr` property, so concrete runtime-product proof accepts this target-proven consumer shape in addition to direct `getRegStr()` callers.

The sample's existing local payload is family-keyed as `XMT01`; the real startup constructor uses `XMT0102`. A target-native write using ProductID `XMT0102`, webapp-root path, and RegStr `QT100110,QT100106` passes fresh `checkReInfo()` and the application `RegisterListener.checkReInfoNew()` flow.

The branch smoke suite includes a dedicated XMT0102 fixture for this Fastjson consumer path so future refactors cannot silently fall back to the broader `XMT01` runtime product.

XMT0103 confirms the XMT0102 Fastjson consumer is a reusable lineage profile: the concrete runtime ProductID comes from target `SystemInfo.registerId`, while `XMT01` remains only the broader authorization family. Its target-declared RegStr is `QT100102`.
