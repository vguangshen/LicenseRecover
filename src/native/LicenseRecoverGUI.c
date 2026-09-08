#define UNICODE
#define _UNICODE

#include <windows.h>
#include <shellapi.h>
#include <wininet.h>
#include <wchar.h>
#include <stdlib.h>

#define ARRAY_LEN(x) (sizeof(x) / sizeof((x)[0]))

static const wchar_t *kMainClass = L"LicenseRecoverModernGUILauncherUiPatch";
static const wchar_t *kOverlayJar = L"LicenseRecoverOverlay.jar";
static const wchar_t *kGuiJar = L"LicenseRecoverGUI.jar";

static int file_exists(const wchar_t *path) {
    DWORD attr = GetFileAttributesW(path);
    return attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY);
}

static void show_error(const wchar_t *message) {
    MessageBoxW(NULL, message, L"LicenseRecover", MB_OK | MB_ICONERROR | MB_SETFOREGROUND);
}

static int join_path(wchar_t *out, size_t cap, const wchar_t *dir, const wchar_t *name) {
    size_t a = wcslen(dir), b = wcslen(name);
    if (a + 1 + b + 1 > cap) return 0;
    wcscpy(out, dir);
    if (a > 0 && out[a - 1] != L'\\' && out[a - 1] != L'/') {
        out[a++] = L'\\';
        out[a] = L'\0';
    }
    wcscat(out, name);
    return 1;
}

static int get_exe_dir(wchar_t *out, size_t cap) {
    DWORD n = GetModuleFileNameW(NULL, out, (DWORD)cap);
    if (n == 0 || n >= cap) return 0;
    wchar_t *slash = wcsrchr(out, L'\\');
    if (!slash) slash = wcsrchr(out, L'/');
    if (!slash) return 0;
    *slash = L'\0';
    return 1;
}

static int find_java(const wchar_t *dir, wchar_t *out, size_t cap) {
    wchar_t candidate[32768];
    if (join_path(candidate, ARRAY_LEN(candidate), dir, L"jre\\bin\\javaw.exe") && file_exists(candidate)) {
        wcsncpy(out, candidate, cap - 1);
        out[cap - 1] = L'\0';
        return 1;
    }
    if (join_path(candidate, ARRAY_LEN(candidate), dir, L"jre\\bin\\java.exe") && file_exists(candidate)) {
        wcsncpy(out, candidate, cap - 1);
        out[cap - 1] = L'\0';
        return 1;
    }

    DWORD found = SearchPathW(NULL, L"javaw.exe", NULL, (DWORD)cap, out, NULL);
    if (found > 0 && found < cap) return 1;
    found = SearchPathW(NULL, L"java.exe", NULL, (DWORD)cap, out, NULL);
    if (found > 0 && found < cap) return 1;

    wchar_t javaHome[32768];
    DWORD homeLen = GetEnvironmentVariableW(L"JAVA_HOME", javaHome, ARRAY_LEN(javaHome));
    if (homeLen > 0 && homeLen < ARRAY_LEN(javaHome)) {
        if (join_path(candidate, ARRAY_LEN(candidate), javaHome, L"bin\\javaw.exe") && file_exists(candidate)) {
            wcsncpy(out, candidate, cap - 1);
            out[cap - 1] = L'\0';
            return 1;
        }
        if (join_path(candidate, ARRAY_LEN(candidate), javaHome, L"bin\\java.exe") && file_exists(candidate)) {
            wcsncpy(out, candidate, cap - 1);
            out[cap - 1] = L'\0';
            return 1;
        }
    }
    return 0;
}

static int append_text(wchar_t *buffer, size_t cap, size_t *len, const wchar_t *text) {
    size_t n = wcslen(text);
    if (*len + n + 1 > cap) return 0;
    memcpy(buffer + *len, text, n * sizeof(wchar_t));
    *len += n;
    buffer[*len] = L'\0';
    return 1;
}

/* Quote one argv element according to the Windows CommandLineToArgvW rules. */
static int append_arg(wchar_t *buffer, size_t cap, size_t *len, const wchar_t *arg) {
    if (*len != 0 && !append_text(buffer, cap, len, L" ")) return 0;

    int needQuotes = (*arg == L'\0' || wcspbrk(arg, L" \t\n\v\"") != NULL);
    if (!needQuotes) return append_text(buffer, cap, len, arg);
    if (!append_text(buffer, cap, len, L"\"")) return 0;

    const wchar_t *p = arg;
    while (*p) {
        size_t slashes = 0;
        while (*p == L'\\') { ++slashes; ++p; }
        if (*p == L'\"') {
            for (size_t i = 0; i < slashes * 2 + 1; ++i)
                if (!append_text(buffer, cap, len, L"\\")) return 0;
            if (!append_text(buffer, cap, len, L"\"")) return 0;
            ++p;
        } else {
            for (size_t i = 0; i < slashes; ++i)
                if (!append_text(buffer, cap, len, L"\\")) return 0;
            if (*p) {
                wchar_t one[2] = {*p++, L'\0'};
                if (!append_text(buffer, cap, len, one)) return 0;
            }
        }
    }

    size_t trailing = 0;
    for (const wchar_t *q = arg + wcslen(arg); q > arg && q[-1] == L'\\'; --q) ++trailing;
    for (size_t i = 0; i < trailing; ++i)
        if (!append_text(buffer, cap, len, L"\\")) return 0;
    return append_text(buffer, cap, len, L"\"");
}

static int native_download(const wchar_t *url, const wchar_t *target) {
    if (!url || !target || _wcsnicmp(url, L"https://", 8) != 0) return 21;

    HINTERNET internet = InternetOpenW(L"LicenseRecover-Updater",
            INTERNET_OPEN_TYPE_PRECONFIG, NULL, NULL, 0);
    if (!internet) return 22;

    DWORD connectTimeout = 12000, receiveTimeout = 60000, sendTimeout = 30000;
    InternetSetOptionW(internet, INTERNET_OPTION_CONNECT_TIMEOUT, &connectTimeout, sizeof(connectTimeout));
    InternetSetOptionW(internet, INTERNET_OPTION_RECEIVE_TIMEOUT, &receiveTimeout, sizeof(receiveTimeout));
    InternetSetOptionW(internet, INTERNET_OPTION_SEND_TIMEOUT, &sendTimeout, sizeof(sendTimeout));

    const wchar_t *headers = L"Accept: application/octet-stream, */*;q=0.8\r\nAccept-Encoding: identity\r\n";
    HINTERNET request = InternetOpenUrlW(internet, url, headers, (DWORD)-1,
            INTERNET_FLAG_RELOAD | INTERNET_FLAG_NO_CACHE_WRITE | INTERNET_FLAG_SECURE | INTERNET_FLAG_NO_UI, 0);
    if (!request) {
        InternetCloseHandle(internet);
        return 23;
    }

    DWORD status = 0, statusLen = sizeof(status);
    if (!HttpQueryInfoW(request, HTTP_QUERY_STATUS_CODE | HTTP_QUERY_FLAG_NUMBER,
                        &status, &statusLen, NULL) || status < 200 || status >= 300) {
        InternetCloseHandle(request);
        InternetCloseHandle(internet);
        return 24;
    }

    HANDLE file = CreateFileW(target, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                              FILE_ATTRIBUTE_TEMPORARY | FILE_ATTRIBUTE_NOT_CONTENT_INDEXED, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        InternetCloseHandle(request);
        InternetCloseHandle(internet);
        return 25;
    }

    BYTE buffer[65536];
    DWORD got = 0;
    int rc = 0;
    for (;;) {
        if (!InternetReadFile(request, buffer, sizeof(buffer), &got)) { rc = 26; break; }
        if (got == 0) break;
        DWORD written = 0;
        if (!WriteFile(file, buffer, got, &written, NULL) || written != got) { rc = 27; break; }
    }
    FlushFileBuffers(file);
    CloseHandle(file);
    InternetCloseHandle(request);
    InternetCloseHandle(internet);
    if (rc != 0) DeleteFileW(target);
    return rc;
}

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE previous, LPWSTR commandLine, int showCommand) {
    (void)instance; (void)previous; (void)commandLine; (void)showCommand;

    int earlyArgc = 0;
    LPWSTR *earlyArgv = CommandLineToArgvW(GetCommandLineW(), &earlyArgc);
    if (earlyArgv && earlyArgc == 4 && wcscmp(earlyArgv[1], L"--native-download") == 0) {
        int rc = native_download(earlyArgv[2], earlyArgv[3]);
        LocalFree(earlyArgv);
        return rc;
    }
    if (earlyArgv) LocalFree(earlyArgv);

    wchar_t dir[32768], javaExe[32768], overlay[32768], guiJar[32768], classpath[65536];
    if (!get_exe_dir(dir, ARRAY_LEN(dir))) {
        show_error(L"无法确定 LicenseRecover 安装目录。");
        return 2;
    }
    if (!join_path(overlay, ARRAY_LEN(overlay), dir, kOverlayJar) || !file_exists(overlay)) {
        show_error(L"缺少 LicenseRecoverOverlay.jar。请使用完整发行包或重新执行软件更新。");
        return 3;
    }
    if (!join_path(guiJar, ARRAY_LEN(guiJar), dir, kGuiJar) || !file_exists(guiJar)) {
        show_error(L"缺少 LicenseRecoverGUI.jar。请使用完整发行包或重新执行软件更新。");
        return 4;
    }
    if (!find_java(dir, javaExe, ARRAY_LEN(javaExe))) {
        show_error(L"未找到 Java 运行环境。完整便携版应包含 jre\\bin\\javaw.exe。");
        return 5;
    }

    if (_snwprintf(classpath, ARRAY_LEN(classpath), L"%ls;%ls", overlay, guiJar) < 0) {
        show_error(L"安装路径过长，无法构造 Java classpath。");
        return 6;
    }

    const size_t cmdCap = 131072;
    wchar_t *cmd = (wchar_t *)calloc(cmdCap, sizeof(wchar_t));
    if (!cmd) {
        show_error(L"内存不足，无法启动 LicenseRecover。");
        return 7;
    }
    size_t len = 0;
    if (!append_arg(cmd, cmdCap, &len, javaExe) ||
        !append_arg(cmd, cmdCap, &len, L"-Dfile.encoding=UTF-8") ||
        !append_arg(cmd, cmdCap, &len, L"-Djava.net.useSystemProxies=true") ||
        !append_arg(cmd, cmdCap, &len, L"-Djava.net.preferIPv4Stack=true") ||
        !append_arg(cmd, cmdCap, &len, L"-Dsun.java2d.dpiaware=true") ||
        !append_arg(cmd, cmdCap, &len, L"-Dsun.java2d.noddraw=true") ||
        !append_arg(cmd, cmdCap, &len, L"-cp") ||
        !append_arg(cmd, cmdCap, &len, classpath) ||
        !append_arg(cmd, cmdCap, &len, kMainClass)) {
        free(cmd);
        show_error(L"启动参数过长，无法启动 LicenseRecover。");
        return 8;
    }

    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv) {
        for (int i = 1; i < argc; ++i) {
            if (!append_arg(cmd, cmdCap, &len, argv[i])) {
                LocalFree(argv);
                free(cmd);
                show_error(L"启动参数过长，无法启动 LicenseRecover。");
                return 9;
            }
        }
        LocalFree(argv);
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    ZeroMemory(&pi, sizeof(pi));
    si.cb = sizeof(si);

    BOOL ok = CreateProcessW(javaExe, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi);
    DWORD error = ok ? ERROR_SUCCESS : GetLastError();
    free(cmd);

    if (!ok) {
        wchar_t message[512];
        _snwprintf(message, ARRAY_LEN(message), L"启动 Java GUI 失败（Windows 错误 %lu）。", (unsigned long)error);
        show_error(message);
        return 10;
    }

    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
