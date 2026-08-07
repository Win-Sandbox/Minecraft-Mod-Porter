// 设置读写：%LOCALAPPDATA%\ModPorter\settings.json
// 与 WinUI 3 版本使用同一路径与同一份 JSON 结构，两个前端可互换使用。
#include "app.h"
#include <shlobj.h>

namespace {

std::wstring SettingsDir() {
    wchar_t path[MAX_PATH] = {};
    // CSIDL_LOCAL_APPDATA 在 XP 起可用（SHGetFolderPath 兼容旧系统）
    if (FAILED(SHGetFolderPathW(nullptr, CSIDL_LOCAL_APPDATA, nullptr, 0, path))) {
        return std::wstring();
    }
    return PathJoin(path, L"ModPorter");
}

std::wstring SettingsFile() {
    std::wstring dir = SettingsDir();
    if (dir.empty()) return std::wstring();
    return PathJoin(dir, L"settings.json");
}

} // namespace

AppSettings LoadSettings() {
    AppSettings s;
    std::wstring file = SettingsFile();
    std::string raw;
    if (file.empty() || !ReadWholeFileUtf8(file, raw)) return s;

    mjson::Value root;
    if (!mjson::Parse(Utf8ToWide(raw), root) || !root.IsObject()) return s;

    std::wstring java = root.Str(L"JavaPath");
    if (java.empty()) java = root.Str(L"javaPath");
    if (!java.empty()) s.javaPath = java;

    std::wstring jar = root.Str(L"BackendJarPath");
    if (jar.empty()) jar = root.Str(L"backendJarPath");
    s.jarPath = jar;

    std::wstring maps = root.Str(L"MappingsDir");
    if (maps.empty()) maps = root.Str(L"mappingsDir");
    s.mappingsDir = maps;
    return s;
}

bool SaveSettings(const AppSettings& s) {
    std::wstring dir = SettingsDir();
    if (dir.empty()) return false;
    SHCreateDirectoryExW(nullptr, dir.c_str(), nullptr);   // 已存在返回错误码，忽略即可

    // 键名与 WinUI 版的 C# 属性名保持一致，两端互读。
    std::wstring json =
        L"{\n"
        L"  \"JavaPath\": \"" + mjson::Escape(s.javaPath) + L"\",\n"
        L"  \"BackendJarPath\": \"" + mjson::Escape(s.jarPath) + L"\",\n"
        L"  \"MappingsDir\": \"" + mjson::Escape(s.mappingsDir) + L"\"\n"
        L"}\n";
    return WriteWholeFileUtf8(SettingsFile(), WideToUtf8(json));
}
