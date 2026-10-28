# DS0101 native registration return-value semantics — verified from original CIL

Important: This is static verification of **original application control flow**, not a patch, a modified vendor DLL, or proof of a working IIS deployment.

## The existing .NET recovery pattern

`LicenseRecoverModernGUIAutoRecovery.recoverDotNet` already uses a shared Windows ASP.NET-hosted process for `gencode` → `doreg` → `verify`, with snapshots, backup, temporary network isolation and rollback. The v2/v3 Windows uppercase/dual-assembly mock integrations explicitly return `false` from the synthetic `RegeditMain.CheckReInfo` while the existing hosted `verify` step succeeds. Boolean return values require interpreting the actual vendor API contract, not assuming `true` always means authorized.

## Original DS0101 `ITMC.Web.dll`: site call sites

- `ITMC.Web.login.button_ServerClick`: after calling `funpublic.CheckReg`, the `brfalse.s` branch **skips** redirection to `~/Regester.aspx` and continues toward `CheckMaxCon`. A `true` return triggers a redirect to registration.
- `ITMC.Web.NetReg.NewReg.Button1_Click`: after `funpublic.CheckReg`, a `false` return enters the branch that displays `软件已经注册，请不要重复注册！` and returns. `true` continues the registration workflow.
- `ITMC.Web.Global.Application_Start` calls `funpublic.CheckReg` at startup, highlighting the importance of testing actual application initialization as well as a separate helper AppDomain.

These two independent call sites establish that the site treats `CheckReg == true` as a condition requiring registration, and `CheckReg == false` as the registered/no-error condition.

## Original uppercase `ITMC.Regedit.dll`: local check

- In `RegeditMain.CheckReInfo`, `regType=1` calls `CheckLocalReg` at CIL offset `0x0102`.
- The next instructions compare the return to zero. If `CheckLocalReg` returned true, the method enters a path storing `0` (false) for the outer `CheckReInfo` result.
- This confirms that `CheckLocalReg == true` and `CheckReInfo == false` can be simultaneously valid; they use opposite Boolean conventions.

## Reinterpretation of the private Windows fixture

- Native `NewRegistry` worked and original `DoRegistry` accepted the offline request/code.
- `CheckLocalReg` was false before and true after registration; local RegInfo after registration had the expected product token and was not expired.
- `CheckReInfo == false` and `Web.CheckReg == false` following registration **must not be described as rejection solely because they returned false**.
- However, the synthetic fixture also yielded `Web.CheckReg == false` before registration. That means this one value cannot establish a valid before/after transition on its own; the synthetic site lacks full production configuration and startup context.

## Minimal architectural consequence

No new licensing framework is needed. The existing common hosted runner, backup and rollback paths are reusable. The remaining correctness work is to align the legacy helper's reported status with the original Boolean error-flag conventions and verify a genuine before/after change under the application's real startup environment. A successful offline code submission alone is not enough to claim deployed website activation.

The exact vendor binaries, user configuration, request values and authorization codes are intentionally omitted.