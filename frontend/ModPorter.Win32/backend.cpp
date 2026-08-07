// 后端进程封装：与 WinUI 3 版使用完全相同的 JSON 协议
//   capabilities            -> 能力清单（版本列表 + 动作列表）
//   run <action> --params   -> NDJSON 流式进度
// 参数经临时文件传入（后端的 --params 支持 JSON 字符串或 JSON 文件路径），
// 从而绕开 Windows 命令行对内嵌引号的转义问题。
#include "app.h"
#include <process.h>
#include <cstdint>   // uintptr_t（_beginthreadex 的返回值）

namespace {

// 按 CommandLineToArgvW 的规则给单个参数加引号。
void AppendArg(std::wstring& cmd, const std::wstring& arg) {
    if (!cmd.empty()) cmd += L' ';
    cmd += L'"';
    size_t backslashes = 0;
    for (wchar_t c : arg) {
        if (c == L'\\') {
            backslashes++;
            continue;
        }
        if (c == L'"') {
            cmd.append(backslashes * 2 + 1, L'\\');
            backslashes = 0;
            cmd += L'"';
            continue;
        }
        cmd.append(backslashes, L'\\');
        backslashes = 0;
        cmd += c;
    }
    cmd.append(backslashes * 2, L'\\');
    cmd += L'"';
}

std::wstring BuildCommandLine(const AppSettings& s, const std::vector<std::wstring>& args) {
    std::wstring cmd;
    AppendArg(cmd, s.javaPath.empty() ? L"java" : s.javaPath);
    AppendArg(cmd, L"-Dfile.encoding=UTF-8");
    AppendArg(cmd, L"-jar");
    AppendArg(cmd, s.jarPath);
    for (const auto& a : args) AppendArg(cmd, a);
    if (!Trim(s.mappingsDir).empty()) {
        AppendArg(cmd, L"--mappings");
        AppendArg(cmd, Trim(s.mappingsDir));
    }
    return cmd;
}

struct DrainArgs {
    HANDLE pipe;
    std::string* sink;
};

unsigned __stdcall DrainThread(void* param) {
    DrainArgs* a = static_cast<DrainArgs*>(param);
    char buf[4096];
    DWORD got = 0;
    while (ReadFile(a->pipe, buf, sizeof(buf), &got, nullptr) && got > 0) {
        a->sink->append(buf, got);
    }
    CloseHandle(a->pipe);
    delete a;
    return 0;
}

// 同步执行并抓取 stdout/stderr（stderr 单独线程排空，避免管道写满导致死锁）。
bool RunCapture(const AppSettings& s, const std::vector<std::wstring>& args,
                std::string& outStd, std::string& outErr, DWORD& exitCode,
                std::wstring& error) {
    if (Trim(s.jarPath).empty()) {
        error = L"尚未配置后端 modporter.jar 路径（请到「设置」页填写）";
        return false;
    }

    SECURITY_ATTRIBUTES sa = {};
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;

    HANDLE outRead = nullptr, outWrite = nullptr, errRead = nullptr, errWrite = nullptr;
    if (!CreatePipe(&outRead, &outWrite, &sa, 0) || !CreatePipe(&errRead, &errWrite, &sa, 0)) {
        error = L"创建管道失败";
        return false;
    }
    SetHandleInformation(outRead, HANDLE_FLAG_INHERIT, 0);
    SetHandleInformation(errRead, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = outWrite;
    si.hStdError = errWrite;
    si.hStdInput = nullptr;

    std::wstring cmd = BuildCommandLine(s, args);
    std::vector<wchar_t> mutableCmd(cmd.begin(), cmd.end());
    mutableCmd.push_back(L'\0');

    PROCESS_INFORMATION pi = {};
    BOOL ok = CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, TRUE,
                             CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi);
    CloseHandle(outWrite);
    CloseHandle(errWrite);
    if (!ok) {
        CloseHandle(outRead);
        CloseHandle(errRead);
        error = L"无法启动 Java 进程，请检查「设置」中的 Java 与 modporter.jar 路径";
        return false;
    }

    DrainArgs* da = new DrainArgs{errRead, &outErr};
    uintptr_t drain = _beginthreadex(nullptr, 0, DrainThread, da, 0, nullptr);

    char buf[4096];
    DWORD got = 0;
    while (ReadFile(outRead, buf, sizeof(buf), &got, nullptr) && got > 0) {
        outStd.append(buf, got);
    }
    CloseHandle(outRead);

    if (drain) {
        // stdout 已读到 EOF 说明子进程已退出，stderr 管道随即关闭，这里不会长时间阻塞
        WaitForSingleObject(reinterpret_cast<HANDLE>(drain), INFINITE);
        CloseHandle(reinterpret_cast<HANDLE>(drain));
    }
    WaitForSingleObject(pi.hProcess, INFINITE);
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return true;
}

// ---- 流式动作运行 ----

struct RunContext {
    HWND notify;
    AppSettings settings;
    std::wstring actionId;
    std::wstring paramsFile;
};

void ParseResultEvent(const mjson::Value& evt, RunOutcome& outcome) {
    outcome.ok = true;
    outcome.status = evt.Str(L"status");
    outcome.info = evt.Int(L"info");
    outcome.warn = evt.Int(L"warn");
    outcome.todo = evt.Int(L"todo");
    outcome.error = evt.Int(L"error");
    outcome.reportJson = evt.Str(L"reportJson");
    outcome.reportMd = evt.Str(L"reportMd");
    outcome.todoReport = evt.Str(L"todoReport");
}

unsigned __stdcall RunThread(void* param) {
    RunContext* ctx = static_cast<RunContext*>(param);
    RunOutcome* outcome = new RunOutcome();

    SECURITY_ATTRIBUTES sa = {};
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;

    HANDLE readEnd = nullptr, writeEnd = nullptr;
    if (!CreatePipe(&readEnd, &writeEnd, &sa, 0)) {
        outcome->message = L"创建管道失败";
        PostMessageW(ctx->notify, WM_APP_DONE, 0, reinterpret_cast<LPARAM>(outcome));
        if (!ctx->paramsFile.empty()) DeleteFileW(ctx->paramsFile.c_str());
        delete ctx;
        return 0;
    }
    SetHandleInformation(readEnd, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = writeEnd;
    si.hStdError = writeEnd;    // stderr 并入同一管道：非 JSON 行按普通消息展示

    std::vector<std::wstring> args;
    args.push_back(L"run");
    args.push_back(ctx->actionId);
    args.push_back(L"--params");
    args.push_back(ctx->paramsFile);

    std::wstring cmd = BuildCommandLine(ctx->settings, args);
    std::vector<wchar_t> mutableCmd(cmd.begin(), cmd.end());
    mutableCmd.push_back(L'\0');

    PROCESS_INFORMATION pi = {};
    BOOL ok = CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, TRUE,
                             CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi);
    CloseHandle(writeEnd);
    if (!ok) {
        CloseHandle(readEnd);
        outcome->message = L"无法启动后端进程，请检查「设置」中的 Java 与 modporter.jar 路径";
        PostMessageW(ctx->notify, WM_APP_DONE, 0, reinterpret_cast<LPARAM>(outcome));
        if (!ctx->paramsFile.empty()) DeleteFileW(ctx->paramsFile.c_str());
        delete ctx;
        return 0;
    }

    std::string pending;
    char buf[4096];
    DWORD got = 0;
    while (ReadFile(readEnd, buf, sizeof(buf), &got, nullptr) && got > 0) {
        pending.append(buf, got);
        size_t pos;
        while ((pos = pending.find('\n')) != std::string::npos) {
            std::string line = pending.substr(0, pos);
            pending.erase(0, pos + 1);
            if (!line.empty() && line[line.size() - 1] == '\r') line.erase(line.size() - 1);
            if (line.empty()) continue;

            std::wstring wide = Utf8ToWide(line);
            mjson::Value evt;
            if (mjson::Parse(wide, evt) && evt.IsObject() && evt.Str(L"type") == L"result") {
                ParseResultEvent(evt, *outcome);
            }
            PostMessageW(ctx->notify, WM_APP_PROGRESS, 0,
                         reinterpret_cast<LPARAM>(new std::wstring(wide)));
        }
    }
    CloseHandle(readEnd);

    DWORD exitCode = 0;
    WaitForSingleObject(pi.hProcess, INFINITE);
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    if (!outcome->ok && outcome->message.empty()) {
        outcome->message = L"后端进程未返回结果（退出码 "
                         + NumStr(static_cast<long long>(exitCode)) + L"）";
    }
    PostMessageW(ctx->notify, WM_APP_DONE, 0, reinterpret_cast<LPARAM>(outcome));

    if (!ctx->paramsFile.empty()) DeleteFileW(ctx->paramsFile.c_str());
    delete ctx;
    return 0;
}

} // namespace

const LoaderInfo* Capabilities::FindLoader(const std::wstring& id) const {
    for (const auto& l : loaders) {
        if (l.id == id) return &l;
    }
    return nullptr;
}

std::vector<std::wstring> Capabilities::AllVersions() const {
    std::vector<std::wstring> all;
    for (const auto& l : loaders) {
        for (const auto& v : l.versions) {
            bool dup = false;
            for (const auto& x : all) {
                if (x == v) { dup = true; break; }
            }
            if (!dup) all.push_back(v);
        }
    }
    return all;
}

int Capabilities::VersionCount() const {
    int n = 0;
    for (const auto& l : loaders) n += static_cast<int>(l.versions.size());
    return n;
}

bool FetchCapabilities(const AppSettings& settings, Capabilities& out, std::wstring& error) {
    out = Capabilities();
    std::string stdOut, stdErr;
    DWORD exitCode = 0;
    std::vector<std::wstring> args;
    args.push_back(L"capabilities");
    if (!RunCapture(settings, args, stdOut, stdErr, exitCode, error)) return false;

    if (exitCode != 0) {
        error = L"后端 capabilities 调用失败：" + Trim(Utf8ToWide(stdErr));
        if (Trim(error).empty()) error = L"后端 capabilities 调用失败";
        return false;
    }

    mjson::Value root;
    if (!mjson::Parse(Utf8ToWide(stdOut), root) || !root.IsObject()) {
        error = L"capabilities 输出无法解析";
        return false;
    }

    const mjson::Value* backend = root.Find(L"backend");
    if (backend && backend->IsObject()) {
        out.backendName = backend->Str(L"name");
        out.backendVersion = backend->Str(L"version");
    }

    if (const mjson::Value* loaders = root.Arr(L"loaders")) {
        for (const auto& l : loaders->elements) {
            LoaderInfo info;
            info.id = l.Str(L"id");
            if (const mjson::Value* versions = l.Arr(L"versions")) {
                for (const auto& v : versions->elements) {
                    if (v.type == mjson::Value::Type::String) info.versions.push_back(v.text);
                }
            }
            out.loaders.push_back(info);
        }
    }

    if (const mjson::Value* actions = root.Arr(L"actions")) {
        for (const auto& a : actions->elements) {
            ActionInfo info;
            info.id = a.Str(L"id");
            info.title = a.Str(L"title");
            info.description = a.Str(L"description");
            info.available = a.Bool(L"available");
            info.builtin = a.Bool(L"builtin");
            if (const mjson::Value* params = a.Arr(L"params")) {
                for (const auto& p : params->elements) {
                    ActionParam param;
                    param.id = p.Str(L"id");
                    param.type = p.Str(L"type", L"string");
                    param.title = p.Str(L"title");
                    param.required = p.Bool(L"required");
                    info.params.push_back(param);
                }
            }
            out.actions.push_back(info);
        }
    }

    out.valid = true;
    return true;
}

bool StartAction(HWND notify, const AppSettings& settings,
                 const std::wstring& actionId, const std::wstring& paramsJson,
                 std::wstring& error) {
    if (Trim(settings.jarPath).empty()) {
        error = L"尚未配置后端 modporter.jar 路径（请到「设置」页填写）";
        return false;
    }
    std::wstring temp = MakeTempFilePath(L"mpp");
    if (temp.empty() || !WriteWholeFileUtf8(temp, WideToUtf8(paramsJson))) {
        error = L"无法写入临时参数文件";
        return false;
    }

    RunContext* ctx = new RunContext();
    ctx->notify = notify;
    ctx->settings = settings;
    ctx->actionId = actionId;
    ctx->paramsFile = temp;

    uintptr_t th = _beginthreadex(nullptr, 0, RunThread, ctx, 0, nullptr);
    if (!th) {
        DeleteFileW(temp.c_str());
        delete ctx;
        error = L"无法创建工作线程";
        return false;
    }
    CloseHandle(reinterpret_cast<HANDLE>(th));
    return true;
}
