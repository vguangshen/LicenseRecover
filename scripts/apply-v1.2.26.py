#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{rel}: expected one match, found {count}: {old[:100]!r}")
    write(rel, text.replace(old, new, 1))


version = read("VERSION.txt").strip()
if version == "1.2.26":
    print("v1.2.26 already applied; nothing to do.")
    raise SystemExit(0)
if version != "1.2.25":
    raise SystemExit(f"Expected VERSION.txt=1.2.25, found {version!r}")

prepare_helper = r'''#!/usr/bin/env python3
"""Build a modern ITMC.Regedit adapter copy of the tracked .NET helper.

The historical helper reflects the lowercase itmcRegedit assembly/type. Modern
applications use the uppercase ITMC.Regedit assembly/type. This script creates a
separate modern-only copy by patching three AppReflection #US token operands after
strictly verifying the source binary hash. The original helper is never modified.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SOURCE_SHA256 = "b83832e9be135d977a16b9faf8bea4f87e645734691ee0125480513116508d23"
EXPECTED_OUTPUT_SHA256 = "aef8bcc41471de2ae361f3f8cd21978f498bd9f7ffa45c86db848dc8da096f36"

DONORS = (
    (bytes([33]) + "not a FOAP table".encode("utf-16le") + b"\x00",
     bytes([33]) + "ITMC.Regedit.dll".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.dll"),
    (bytes([49]) + "  [错误] 中和循环超过 64 次，放弃写回。".encode("utf-16le") + b"\x01",
     bytes([49]) + "ITMC.Regedit.RegeditMain".encode("utf-16le") + b"\x00",
     "ITMC.Regedit.RegeditMain"),
)

TOKEN_PATCHES = (
    (0x04E9, bytes.fromhex("01000070"), bytes.fromhex("9d000070")),
    (0x0503, bytes.fromhex("19000070"), bytes.fromhex("e9170070")),
    (0x02CA, bytes.fromhex("39000070"), bytes.fromhex("db150070")),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def replace_once(data: bytearray, old: bytes, new: bytes, label: str) -> None:
    if len(old) != len(new):
        raise SystemExit(f"{label}: replacement changes PE size")
    count = bytes(data).count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one donor payload, found {count}")
    pos = bytes(data).find(old)
    data[pos:pos + len(old)] = new


def patch(source: Path, destination: Path) -> None:
    raw = source.read_bytes()
    actual = sha256(raw)
    if actual != SOURCE_SHA256:
        raise SystemExit(
            "Unsupported LicenseRecover.NET.exe build. "
            f"Expected SHA-256 {SOURCE_SHA256}, got {actual}."
        )

    data = bytearray(raw)
    for old_blob, new_blob, label in DONORS:
        replace_once(data, old_blob, new_blob, label)

    for offset, before, after in TOKEN_PATCHES:
        current = bytes(data[offset:offset + len(before)])
        if current != before:
            raise SystemExit(
                f"IL token precondition failed at 0x{offset:x}: "
                f"expected {before.hex()}, found {current.hex()}"
            )
        data[offset:offset + len(before)] = after

    out = bytes(data)
    for required in ("ITMC.Regedit", "ITMC.Regedit.dll", "ITMC.Regedit.RegeditMain"):
        if required.encode("utf-16le") not in out:
            raise SystemExit(f"Patched helper is missing required user string: {required}")

    out_sha = sha256(out)
    if out_sha != EXPECTED_OUTPUT_SHA256:
        raise SystemExit(
            "Patched helper hash mismatch. "
            f"Expected {EXPECTED_OUTPUT_SHA256}, got {out_sha}."
        )

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(out)
    print(f"Modern helper: {destination}")
    print(f"SHA-256: {out_sha}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    patch(args.source, args.destination)


if __name__ == "__main__":
    main()
'''
write("scripts/prepare-dotnet-modern-helper.py", prepare_helper)

replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''        File helper = findDotNetNativeHelper();\n        if (helper == null) return Result.fail("LicenseRecover.NET.exe was not found; native IIS registration cannot run.", d);''',
    '''        File helper = findDotNetModernNativeHelper();\n        if (helper == null) return Result.fail("[HELPER] LicenseRecover.NET.Modern.exe was not found; modern native registration cannot run.", d);'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''                throw new IOException("无法建立 .NET 临时出站防火墙隔离。请从 LicenseRecoverGUI.exe 启动并通过管理员权限(UAC)，"\n                        + "同时确认 Windows Firewall 服务可用；为避免联网校验，已拒绝调用目标注册组件。");''',
    '''                throw new IOException("[PRE_BLOCK] 无法建立 .NET 临时出站防火墙隔离。请从 LicenseRecoverGUI.exe 启动并通过管理员权限(UAC)，"\n                        + "同时确认 Windows Firewall 服务可用；为避免联网校验，已拒绝调用目标注册组件。");\n            log.accept("[dotnet-stage] PRE_BLOCK: OK (" + helper.getName() + ")\\n");'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);\n            if (generated.exitCode != 0)\n                throw new IOException("Target-native .NET request-code generation failed under network isolation");''',
    '''            log.accept("[dotnet-stage] GENCODE: start\\n");\n            NativeProcessResult generated = runNativeCapture(generate, d.appRoot, log);\n            if (generated.exitCode != 0)\n                throw nativeFailure("GENCODE", "target-native request-code generation failed", generated);\n            log.accept("[dotnet-stage] GENCODE: OK\\n");'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            if (blank(request) || blank(auth))\n                throw new IOException("Native helper did not return both request code and authorization code under network isolation");''',
    '''            if (blank(request) || blank(auth))\n                throw new IOException("[GENCODE_PARSE] helper returned success but request/auth fields were not parsed; output="\n                        + compactNativeOutput(generated.output));\n            log.accept("[dotnet-stage] GENCODE_PARSE: OK\\n");'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);\n            if (applied.exitCode != 0) throw new IOException("target RegeditMain.DoRegistry() rejected the generated code");''',
    '''            log.accept("[dotnet-stage] DOREG: start\\n");\n            NativeProcessResult applied = runNativeCapture(apply, d.appRoot, log);\n            if (applied.exitCode != 0)\n                throw nativeFailure("DOREG", "target RegeditMain.DoRegistry() rejected the generated code", applied);\n            log.accept("[dotnet-stage] DOREG: OK\\n");'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);\n            if (checked.exitCode != 0) throw new IOException("target RegeditMain.CheckReInfo() did not confirm the native write-back");''',
    '''            log.accept("[dotnet-stage] VERIFY: start\\n");\n            NativeProcessResult checked = runNativeCapture(verify, d.appRoot, log);\n            if (checked.exitCode != 0)\n                throw nativeFailure("VERIFY", "target RegeditMain.CheckReInfo() did not confirm the native write-back", checked);\n            log.accept("[dotnet-stage] VERIFY: OK\\n");'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static File findDotNetNativeHelper() {\n        File dir = toolDir();\n        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.exe");\n        if (nested.isFile()) return nested;\n        File flat = new File(dir, "LicenseRecover.NET.exe");\n        return flat.isFile() ? flat : null;\n    }''',
    '''    private static IOException nativeFailure(String stage, String action, NativeProcessResult result) {\n        return new IOException("[" + stage + "] " + action + "; exit=" + result.exitCode\n                + "; output=" + compactNativeOutput(result.output));\n    }\n\n    static String compactNativeOutput(String output) {\n        if (output == null) return "<empty>";\n        String s = output.replace('\\r', ' ').replace('\\n', ' ');\n        s = s.replaceAll("(?i)(注册申请号|申请号|离线授权码|授权码)\\\\s*[:：]\\\\s*[0-9a-f]{32,}", "$1:<redacted>");\n        s = s.replaceAll("\\\\s+", " ").trim();\n        if (s.isEmpty()) return "<empty>";\n        return s.length() <= 600 ? s : "..." + s.substring(s.length() - 600);\n    }\n\n    private static File findDotNetModernNativeHelper() {\n        File dir = toolDir();\n        File nested = new File(dir, "LicenseRecover.NET" + File.separator + "LicenseRecover.NET.Modern.exe");\n        if (nested.isFile()) return nested;\n        File flat = new File(dir, "LicenseRecover.NET.Modern.exe");\n        return flat.isFile() ? flat : null;\n    }'''
)

replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''import java.io.File;\nimport java.util.ArrayList;''',
    '''import java.io.File;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\nimport java.nio.file.StandardOpenOption;\nimport java.text.SimpleDateFormat;\nimport java.util.Date;\nimport java.util.ArrayList;'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''    private final JTextArea logArea = new JTextArea();''',
    '''    private final JTextArea logArea = new JTextArea();\n    private final Object persistentLogLock = new Object();'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''    private void appendLog(String text) {\n        if (text == null || text.isEmpty()) return;\n        SwingUtilities.invokeLater(() -> {\n            logArea.append(text);\n            logArea.setCaretPosition(logArea.getDocument().getLength());\n        });\n    }''',
    '''    private void appendLog(String text) {\n        if (text == null || text.isEmpty()) return;\n        appendPersistentLog(text);\n        SwingUtilities.invokeLater(() -> {\n            logArea.append(text);\n            logArea.setCaretPosition(logArea.getDocument().getLength());\n        });\n    }\n\n    private void appendPersistentLog(String text) {\n        try {\n            synchronized (persistentLogLock) {\n                File dir = new File(toolDir(), "logs");\n                Files.createDirectories(dir.toPath());\n                String day = new SimpleDateFormat("yyyyMMdd").format(new Date());\n                File file = new File(dir, "LicenseRecoverGUI-" + day + ".log");\n                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());\n                String entry = "[" + stamp + "] " + text;\n                Files.write(file.toPath(), entry.getBytes(StandardCharsets.UTF_8),\n                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);\n            }\n        } catch (Throwable ignore) {\n            // Diagnostics must never turn a recovery result into a failure.\n        }\n    }'''
)
replace_once(
    "src/main/java/LicenseRecoverModernGUI.java",
    '''        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) return "兼容方式一: 不适用";\n        if (preview) return "预览: 未执行写回校验";\n        return result.isSuccess() ? "DoRegistry + CheckReInfo: OK" : "DoRegistry + CheckReInfo: FAILED";''',
    '''        if (d.kind == LicenseRecoverModernGUIAutoRecovery.Kind.DOTNET_LEGACY) return "兼容方式一: 不适用";\n        if (preview) return "预览: 未执行写回校验";\n        if (!result.isSuccess()) {\n            String reason = result.message == null ? "" : result.message;\n            if (reason.startsWith("[HELPER]")) return "Helper: FAILED";\n            if (reason.startsWith("[PRE_BLOCK]")) return "PRE-BLOCK: FAILED";\n            if (reason.startsWith("[GENCODE]")) return "gencode: FAILED";\n            if (reason.startsWith("[GENCODE_PARSE]")) return "gencode parse: FAILED";\n            if (reason.startsWith("[DOREG]")) return "DoRegistry: FAILED";\n            if (reason.startsWith("[VERIFY]")) return "CheckReInfo: FAILED";\n            return "Native flow: FAILED";\n        }\n        return "DoRegistry + CheckReInfo: OK";'''
)

replace_once(
    "scripts/verify.ps1",
    '''Write-Host 'Verifying directory-only registration identity policy...'\n$coreSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecover.java') -Raw''',
    '''Write-Host 'Building uppercase ITMC.Regedit modern native helper adapter...'\n$modernNativeHelper = Join-Path $buildRoot 'LicenseRecover.NET.Modern.exe'\nInvoke-External -Command 'python3' -ArgumentList @(\n    (Join-Path $repoRoot 'scripts/prepare-dotnet-modern-helper.py'),\n    $nativeHelper,\n    $modernNativeHelper\n)\nif (-not (Test-Path -LiteralPath $modernNativeHelper)) { throw 'Modern .NET helper adapter was not created.' }\n\nWrite-Host 'Verifying directory-only registration identity policy...'\n$coreSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecover.java') -Raw'''
)
replace_once(
    "scripts/verify.ps1",
    '''$legacyGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverGUI.java') -Raw''',
    '''$legacyGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverGUI.java') -Raw\n$modernGuiSource = Get-Content -LiteralPath (Join-Path $mainSourceDir 'LicenseRecoverModernGUI.java') -Raw'''
)
replace_once(
    "scripts/verify.ps1",
    '''if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }''',
    '''if (-not $autoSource.Contains('VersionID/default-list fallback is disabled')) { throw '.NET fail-closed guard is missing.' }\nif (-not $autoSource.Contains('LicenseRecover.NET.Modern.exe')) { throw 'Modern .NET one-click is not using the uppercase helper adapter.' }\nif (-not $modernGuiSource.Contains('appendPersistentLog')) { throw 'Modern GUI persistent diagnostics are missing.' }'''
)
replace_once(
    "scripts/verify.ps1",
    '''Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force\nCopy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.exe') -Destination (Join-Path $distDir 'LicenseRecoverGUI-legacy.exe') -Force''',
    '''Copy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecover.NET') -Destination $distDir -Recurse -Force\n$distNativeDir = Join-Path $distDir 'LicenseRecover.NET'\nCopy-Item -LiteralPath $modernNativeHelper -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe') -Force\n$nativeConfig = Join-Path $repoRoot 'LicenseRecover.NET/LicenseRecover.NET.exe.config'\nif (Test-Path -LiteralPath $nativeConfig) {\n    Copy-Item -LiteralPath $nativeConfig -Destination (Join-Path $distNativeDir 'LicenseRecover.NET.Modern.exe.config') -Force\n}\nCopy-Item -LiteralPath (Join-Path $repoRoot 'LicenseRecoverGUI.exe') -Destination (Join-Path $distDir 'LicenseRecoverGUI-legacy.exe') -Force'''
)

write("VERSION.txt", "1.2.26\n")
replace_once("README.md", "当前稳定版本：**v1.2.25**", "当前稳定版本：**v1.2.26**")
changelog = read("CHANGELOG.md")
write("CHANGELOG.md", '''## [1.2.26] - 2026-09-09\n\n### Fixed\n\n- 修复 .NET Modern 一键恢复公共链：旧 `LicenseRecover.NET.exe` 的 `gencode/doreg/verify` 仍反射小写 `itmcRegedit.RegeditMain`，而现代 YX0301/YX0302/YX0303/GM004 样本使用大写 `ITMC.Regedit.RegeditMain`。构建时现在从已校验原始 helper 生成独立 `LicenseRecover.NET.Modern.exe` 适配副本；旧 helper 保持不变。\n- YX030308/YX030322 这类没有小写 `itmcRegedit.dll` 的目标不再从错误程序集入口失败。\n- .NET Modern 批量失败现在区分 `PRE-BLOCK / gencode / gencode parse / DoRegistry / CheckReInfo` 阶段，不再统一显示 `DoRegistry + CheckReInfo: FAILED`。\n- 现代 GUI 运行日志同步持久化到 `logs/LicenseRecoverGUI-YYYYMMDD.log`，便于下一轮真实机回归定位。\n\n### Safety\n\n- 现代 helper 仅在构建时对固定 SHA-256 的内部兼容组件做三处受校验的元数据字符串 token 适配；源 helper 二进制不修改，hash 或 IL 前置条件不一致时 CI 直接失败。\n- PRE-BLOCK FIRST、目录证据 fail-closed、失败回滚与写后 `CheckReInfo` 校验保持不变。\n\n''' + changelog)
write("release-notes/v1.2.26.md", '''# LicenseRecover v1.2.26\n\n本版根据 v1.2.25 的真实 43 项批量回归继续修复剩余的 .NET Modern 公共失败链。\n\n- **修复 modern helper 反射程序集错误**：v1.2.25 的 `LicenseRecover.NET.exe` 在 `gencode / doreg / verify` 中仍加载旧的小写 `itmcRegedit` / `itmcRegedit.RegeditMain`；现代目标实际使用大写 `ITMC.Regedit` / `ITMC.Regedit.RegeditMain`。本版在 CI 中生成独立 `LicenseRecover.NET.Modern.exe`，只供 `.NET Modern` 调用，旧 helper 保持原样。\n- **覆盖没有小写兼容 DLL 的 YX0303**：YX030308 / YX030322 仅带大写 `ITMC.Regedit.dll` 时也能进入正确原生注册组件。\n- **失败阶段可见**：批量表格和运行日志明确区分 `PRE-BLOCK`、`gencode`、`gencode parse`、`DoRegistry`、`CheckReInfo`，避免所有错误都被折叠成同一条 FAILED。\n- **GUI 日志落盘**：现代界面日志同步保存到 `logs/LicenseRecoverGUI-YYYYMMDD.log`，下次真实机测试可直接上传这一份日志。\n- **安全边界不变**：调用目标注册代码前仍先建立临时出站隔离；目标身份/RegStr 继续只接受目录证据；失败仍回滚原配置。\n''')

print("v1.2.26 patch staged successfully")
