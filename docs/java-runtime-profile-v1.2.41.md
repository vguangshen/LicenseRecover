# Java runtime-profile sample matrix (v1.2.41 branch)

This note records the runtime ABI evidence used by the Java target-native registration refactor. The implementation must derive the constructor path from target startup bytecode and must not choose a constructor only because it happens to exist in `ITMCReg*.jar`.

| Sample | Startup `RegisterMain` ABI | Path semantics | Packed registration JAR |
| --- | --- | --- | --- |
| DS50109 | `(String product)` | target/default path | no |
| DS2406 | `(String product, String configPath)` | explicit webapp-root path | no |
| QT30103 | `(String product, String token, String configPath)` | explicit webapp-root path | no |
| DS2802 | three-arg token/path, then two-arg token/default fallback | explicit root then target/default | yes |
| DS3110 | `(String product, String token)` | target/default path derived by registration component | yes |

The supplied DS3110 registration component has SHA-256 `ef00e4751fcbc5ea6c100e4bbaf73f60094310d26c1894ca3906e70c73f45cfa`, identical to the DS2802 `ITMCReg.jar` sample. Its two-argument constructor derives the configuration directory from the registration class CodeSource, so the isolated host must preserve the original JAR CodeSource when restoring protected classes in memory.

For DS3110, target startup bytecode uses the two-argument token constructor and checks `checkReInfo()` followed by `getRegInfo().regStr.contains(soft_num)`. Directory evidence resolves the direct platform/runtime pair as `DS31` / `DS3110`; no catch-all RegStr or constructor guessing is allowed.

The supplied Virbox `.bin` keystream and the decoded `.hex` keystream are byte-identical and are used only for temporary in-memory restoration of protected target classes. Original application JAR/class files must remain unchanged by the target-native recovery path.
