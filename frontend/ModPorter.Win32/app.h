// ModPorter Win32 前端：共享声明。
// 目标：无 .NET / 无 WinRT 依赖，Windows XP ~ Windows 11 通用。
// 与 WinUI 3 版本功能一致，且共用同一份后端 JSON 协议与 settings.json。
#pragma once

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0501     // Windows XP
#endif
#ifndef WINVER
#define WINVER 0x0501
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <string>
#include <vector>

#include "json.h"

// ---------------- 编码与通用工具（util.cpp） ----------------

std::wstring Utf8ToWide(const std::string& s);
std::string  WideToUtf8(const std::wstring& s);
std::wstring Trim(const std::wstring& s);
std::wstring PickFolder(HWND owner, const wchar_t* title);
std::wstring PickFile(HWND owner, const wchar_t* title, const wchar_t* filter);
bool ReadWholeFileUtf8(const std::wstring& path, std::string& out);
bool WriteWholeFileUtf8(const std::wstring& path, const std::string& content);
std::wstring MakeTempFilePath(const wchar_t* prefix);
bool FileExists(const std::wstring& path);
bool DirectoryExists(const std::wstring& path);
std::wstring PathJoin(const std::wstring& dir, const std::wstring& name);
void OpenWithShell(HWND owner, const std::wstring& path);

// ---- DPI 与字体度量（manifest 声明了 dpiAware，缩放必须自己算） ----
extern HFONT g_font;        // 系统消息字体（随区域自动含中文字形）
extern HFONT g_titleFont;   // 标题用的加粗大字号
extern int   g_dpi;         // 每英寸像素，96 = 100%
extern int   g_rowH;        // 单行控件高度（输入框 / 按钮 / 下拉框），由字体推导
extern int   g_labelH;      // 单行文本高度

/// 把按 96 DPI 设计的像素值换算到当前 DPI。
int Sx(int px);

HWND MakeCtl(HWND parent, const wchar_t* cls, const wchar_t* text,
             DWORD style, int id, DWORD exStyle = 0);
/// 给控件换字体（标题用 g_titleFont）。
void SetCtlFont(HWND h, HFONT font);
void SetCtlText(HWND h, const std::wstring& t);
std::wstring GetCtlText(HWND h);
void Place(HWND h, int x, int y, int w, int hgt);
std::wstring ComboText(HWND combo);
// 用字符串拼接代替 wsprintf：wsprintfW 不支持 %lld，且各编译器的宽字符 printf 行为不一致。
std::wstring NumStr(long long v);
void ComboFill(HWND combo, const std::vector<std::wstring>& items, int select);
void ListBoxAppend(HWND list, const std::wstring& line);

// ---------------- 设置（settings.cpp，与 WinUI 版共用 settings.json） ----------------

struct AppSettings {
    std::wstring javaPath = L"java";
    std::wstring jarPath;
    std::wstring mappingsDir;
};
AppSettings LoadSettings();
bool SaveSettings(const AppSettings& s);

// ---------------- 后端能力清单与动作（backend.cpp） ----------------

struct ActionParam {
    std::wstring id, type, title;
    bool required = false;
};

struct ActionInfo {
    std::wstring id, title, description;
    bool available = false;
    bool builtin = false;
    std::vector<ActionParam> params;
};

struct LoaderInfo {
    std::wstring id;
    std::vector<std::wstring> versions;
};

struct Capabilities {
    bool valid = false;
    std::wstring backendName, backendVersion;
    std::vector<LoaderInfo> loaders;
    std::vector<ActionInfo> actions;

    const LoaderInfo* FindLoader(const std::wstring& id) const;
    std::vector<std::wstring> AllVersions() const;
    int VersionCount() const;
};

// 同步调用 `modporter capabilities`（含 stderr 排空，避免管道死锁）。
bool FetchCapabilities(const AppSettings& settings, Capabilities& out, std::wstring& error);

// 一次动作运行的最终结果（来自 NDJSON 的 type=result 事件）。
struct RunOutcome {
    bool ok = false;
    std::wstring status;
    std::wstring message;          // 失败时的说明
    long long info = 0, warn = 0, todo = 0, error = 0;
    std::wstring reportJson, reportMd, todoReport;
};

// 工作线程 -> UI 线程的消息。
#define WM_APP_PROGRESS (WM_APP + 1)   // lParam = std::wstring*（一行 NDJSON 或普通文本），接收方负责 delete
#define WM_APP_DONE     (WM_APP + 2)   // lParam = RunOutcome*，接收方负责 delete

// 异步启动 `modporter run <actionId> --params <临时文件>`；进度经上面两个消息回投。
bool StartAction(HWND notify, const AppSettings& settings,
                 const std::wstring& actionId, const std::wstring& paramsJson,
                 std::wstring& error);

// ---------------- 页面框架（main.cpp） ----------------

class PageBase {
public:
    HWND hwnd = nullptr;
    virtual ~PageBase() {}
    virtual void OnCreate() {}
    virtual void OnSize(int /*w*/, int /*h*/) {}
    virtual void OnCommand(int /*id*/, int /*code*/, HWND /*ctl*/) {}
    virtual void OnNotify(NMHDR* /*hdr*/) {}
    virtual LRESULT OnAppMessage(UINT /*msg*/, WPARAM /*wp*/, LPARAM /*lp*/) { return 0; }
    virtual void OnActivate() {}
};

PageBase* CreateConvertPage();
PageBase* CreateReportPage();
PageBase* CreateExtensionsPage();
PageBase* CreateSettingsPage();

// ---------------- 全局会话状态（相当于 WinUI 版的 AppState） ----------------

extern Capabilities g_caps;
extern std::wstring g_lastReportJson;
extern std::wstring g_lastReportMd;
extern std::wstring g_lastTodoReport;
extern std::wstring g_lastOutputDir;
extern PageBase*    g_runOwner;      // 当前发起后端动作的页面，主窗口据此转发 WM_APP_*
extern HWND         g_mainWnd;

// 供设置页保存后通知其它页面重新拉取能力清单。
void InvalidateCapabilities();
