// 「版本转换」页：加载器/源版本/目标版本三个下拉框由后端 capabilities 自动发现。
#include "app.h"

namespace {

enum {
    ID_LOADER = 1001, ID_FROM, ID_TO,
    ID_INPUT, ID_INPUT_BROWSE, ID_OUTPUT, ID_OUTPUT_BROWSE,
    ID_DRYRUN, ID_CONVERT, ID_REFRESH,
    ID_PROGRESS, ID_LOG
};

class ConvertPage : public PageBase {
public:
    void OnCreate() override;
    void OnSize(int w, int h) override;
    void OnCommand(int id, int code, HWND ctl) override;
    LRESULT OnAppMessage(UINT msg, WPARAM wp, LPARAM lp) override;
    void OnActivate() override;

private:
    HWND title_ = nullptr, desc_ = nullptr, status_ = nullptr;
    HWND loaderLabel_ = nullptr, fromLabel_ = nullptr, toLabel_ = nullptr, arrow_ = nullptr;
    HWND loader_ = nullptr, from_ = nullptr, to_ = nullptr;
    HWND inputLabel_ = nullptr, input_ = nullptr, inputBrowse_ = nullptr;
    HWND outputLabel_ = nullptr, output_ = nullptr, outputBrowse_ = nullptr;
    HWND dryRun_ = nullptr, convert_ = nullptr, refresh_ = nullptr;
    HWND progress_ = nullptr, progressText_ = nullptr, result_ = nullptr;
    HWND logLabel_ = nullptr, log_ = nullptr;
    bool running_ = false;
    bool loaded_ = false;

    void ReloadCapabilities();
    void FillVersionsForLoader();
    void SetStatus(const std::wstring& text);
    void StartConversion();
    void HandleProgressLine(const std::wstring& line);
};

void ConvertPage::OnCreate() {
    title_ = MakeCtl(hwnd, L"STATIC", L"版本转换", 0, -1);
    SetCtlFont(title_, g_titleFont);
    desc_ = MakeCtl(hwnd, L"STATIC",
                    L"将模组源码工程从一个 Minecraft 版本转换到另一个版本。可用版本由后端映射数据库自动发现。",
                    0, -1);
    status_ = MakeCtl(hwnd, L"STATIC", L"", SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);

    loaderLabel_ = MakeCtl(hwnd, L"STATIC", L"加载器", 0, -1);
    fromLabel_ = MakeCtl(hwnd, L"STATIC", L"源版本", 0, -1);
    toLabel_ = MakeCtl(hwnd, L"STATIC", L"目标版本", 0, -1);
    loader_ = MakeCtl(hwnd, L"COMBOBOX", L"", CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP, ID_LOADER);
    from_ = MakeCtl(hwnd, L"COMBOBOX", L"", CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP, ID_FROM);
    arrow_ = MakeCtl(hwnd, L"STATIC", L"→", SS_CENTER, -1);
    to_ = MakeCtl(hwnd, L"COMBOBOX", L"", CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP, ID_TO);

    inputLabel_ = MakeCtl(hwnd, L"STATIC", L"输入工程目录", 0, -1);
    input_ = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL, ID_INPUT);
    inputBrowse_ = MakeCtl(hwnd, L"BUTTON", L"浏览…", BS_PUSHBUTTON | WS_TABSTOP, ID_INPUT_BROWSE);

    outputLabel_ = MakeCtl(hwnd, L"STATIC", L"输出工程目录（不能位于输入目录内）", 0, -1);
    output_ = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL, ID_OUTPUT);
    outputBrowse_ = MakeCtl(hwnd, L"BUTTON", L"浏览…", BS_PUSHBUTTON | WS_TABSTOP, ID_OUTPUT_BROWSE);

    dryRun_ = MakeCtl(hwnd, L"BUTTON", L"只分析不写出（dry-run）",
                      BS_AUTOCHECKBOX | WS_TABSTOP, ID_DRYRUN);
    convert_ = MakeCtl(hwnd, L"BUTTON", L"开始转换",
                       BS_DEFPUSHBUTTON | WS_TABSTOP, ID_CONVERT);
    refresh_ = MakeCtl(hwnd, L"BUTTON", L"刷新版本列表", BS_PUSHBUTTON | WS_TABSTOP, ID_REFRESH);

    progress_ = MakeCtl(hwnd, PROGRESS_CLASSW, L"", 0, ID_PROGRESS);
    SendMessageW(progress_, PBM_SETRANGE32, 0, 1000);
    progressText_ = MakeCtl(hwnd, L"STATIC", L"", SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);
    result_ = MakeCtl(hwnd, L"STATIC", L"", SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);

    logLabel_ = MakeCtl(hwnd, L"STATIC", L"转换日志", 0, -1);
    log_ = MakeCtl(hwnd, L"LISTBOX", L"",
                   WS_BORDER | WS_VSCROLL | WS_HSCROLL | LBS_NOINTEGRALHEIGHT | LBS_NOSEL, ID_LOG);
}

void ConvertPage::OnSize(int w, int h) {
    const int M = Sx(16);        // 外边距
    const int G = Sx(10);        // 控件间距
    const int R = g_rowH;        // 单行控件高度（按字体推导）
    const int LH = g_labelH;     // 单行文本高度
    const int DROP = Sx(280);    // 下拉框展开高度
    int x = M, y = M, cw = w - M * 2;
    if (cw < Sx(320)) cw = Sx(320);

    Place(title_, x, y, cw, LH + Sx(8));    y += LH + Sx(14);
    Place(desc_, x, y, cw, LH * 2);         y += LH * 2 + Sx(4);
    Place(status_, x, y, cw, LH);           y += LH + Sx(14);

    // 三个下拉框按可用宽度自适应，窄窗口下也不会互相压叠
    int arrowW = Sx(26);
    int comboW = (cw - arrowW - G * 3) / 3;
    if (comboW > Sx(230)) comboW = Sx(230);
    if (comboW < Sx(90))  comboW = Sx(90);

    int cx = x;
    int loaderX = cx;                       cx += comboW + G;
    int fromX = cx;                         cx += comboW + G;
    int arrowX = cx;                        cx += arrowW + G;
    int toX = cx;

    Place(loaderLabel_, loaderX, y, comboW, LH);
    Place(fromLabel_, fromX, y, comboW, LH);
    Place(toLabel_, toX, y, comboW, LH);
    y += LH + Sx(3);
    Place(loader_, loaderX, y, comboW, DROP);
    Place(from_, fromX, y, comboW, DROP);
    Place(arrow_, arrowX, y + (R - LH) / 2, arrowW, LH);
    Place(to_, toX, y, comboW, DROP);
    y += R + Sx(16);

    int browseW = Sx(96);
    Place(inputLabel_, x, y, cw, LH);       y += LH + Sx(3);
    Place(input_, x, y, cw - browseW - G, R);
    Place(inputBrowse_, x + cw - browseW, y, browseW, R);
    y += R + G;

    Place(outputLabel_, x, y, cw, LH);      y += LH + Sx(3);
    Place(output_, x, y, cw - browseW - G, R);
    Place(outputBrowse_, x + cw - browseW, y, browseW, R);
    y += R + Sx(16);

    int btnW = Sx(130);
    Place(dryRun_, x, y + (R - LH) / 2, cw - btnW * 2 - G * 2, LH);
    Place(convert_, x + cw - btnW * 2 - G, y, btnW, R);
    Place(refresh_, x + cw - btnW, y, btnW, R);
    y += R + Sx(16);

    Place(progress_, x, y, cw, Sx(18));     y += Sx(22);
    Place(progressText_, x, y, cw, LH);     y += LH + Sx(4);
    Place(result_, x, y, cw, LH);           y += LH + Sx(14);

    Place(logLabel_, x, y, cw, LH);         y += LH + Sx(3);
    int logH = h - y - M;
    if (logH < Sx(80)) logH = Sx(80);
    Place(log_, x, y, cw, logH);
}

void ConvertPage::OnActivate() {
    if (!loaded_) {
        loaded_ = true;
        ReloadCapabilities();
    }
}

void ConvertPage::SetStatus(const std::wstring& text) {
    SetCtlText(status_, text);
}

void ConvertPage::ReloadCapabilities() {
    HCURSOR old = SetCursor(LoadCursor(nullptr, IDC_WAIT));
    AppSettings settings = LoadSettings();
    std::wstring error;
    Capabilities caps;
    bool ok = FetchCapabilities(settings, caps, error);
    SetCursor(old);

    if (!ok) {
        SetStatus(L"无法连接后端：" + error);
        ComboFill(loader_, std::vector<std::wstring>(), -1);
        ComboFill(from_, std::vector<std::wstring>(), -1);
        ComboFill(to_, std::vector<std::wstring>(), -1);
        return;
    }
    g_caps = caps;
    if (caps.loaders.empty()) {
        SetStatus(L"映射数据库中没有任何版本，请到「设置」页检查映射数据目录");
        return;
    }

    std::vector<std::wstring> loaderIds;
    for (const auto& l : caps.loaders) loaderIds.push_back(l.id);
    ComboFill(loader_, loaderIds, 0);
    FillVersionsForLoader();

    SetStatus(L"后端 " + caps.backendName + L" " + caps.backendVersion + L"："
              + NumStr(static_cast<long long>(caps.loaders.size())) + L" 个加载器 / "
              + NumStr(caps.VersionCount()) + L" 个版本");
}

void ConvertPage::FillVersionsForLoader() {
    std::wstring id = ComboText(loader_);
    const LoaderInfo* info = g_caps.FindLoader(id);
    std::vector<std::wstring> versions = info ? info->versions : std::vector<std::wstring>();
    ComboFill(from_, versions, 0);
    ComboFill(to_, versions, static_cast<int>(versions.size()) - 1);
}

void ConvertPage::OnCommand(int id, int code, HWND ctl) {
    (void)ctl;
    switch (id) {
        case ID_LOADER:
            if (code == CBN_SELCHANGE) FillVersionsForLoader();
            break;
        case ID_INPUT_BROWSE: {
            std::wstring p = PickFolder(hwnd, L"选择输入工程目录");
            if (!p.empty()) SetCtlText(input_, p);
            break;
        }
        case ID_OUTPUT_BROWSE: {
            std::wstring p = PickFolder(hwnd, L"选择输出工程目录");
            if (!p.empty()) SetCtlText(output_, p);
            break;
        }
        case ID_REFRESH:
            ReloadCapabilities();
            break;
        case ID_CONVERT:
            StartConversion();
            break;
        default:
            break;
    }
}

void ConvertPage::StartConversion() {
    if (running_) return;

    std::wstring loader = ComboText(loader_);
    std::wstring from = ComboText(from_);
    std::wstring to = ComboText(to_);
    std::wstring input = Trim(GetCtlText(input_));
    std::wstring output = Trim(GetCtlText(output_));

    if (loader.empty() || from.empty() || to.empty()) {
        SetCtlText(result_, L"请先选择加载器与版本");
        return;
    }
    if (from == to) {
        SetCtlText(result_, L"源版本与目标版本相同");
        return;
    }
    if (input.empty() || output.empty()) {
        SetCtlText(result_, L"请先选择输入与输出目录");
        return;
    }
    if (!DirectoryExists(input)) {
        SetCtlText(result_, L"输入目录不存在：" + input);
        return;
    }

    bool dry = SendMessageW(dryRun_, BM_GETCHECK, 0, 0) == BST_CHECKED;
    mjson::Builder b;
    b.AddString(L"loader", loader);
    b.AddString(L"from", from);
    b.AddString(L"to", to);
    b.AddString(L"input", input);
    b.AddString(L"output", output);
    b.AddBool(L"dryRun", dry);

    std::wstring error;
    g_runOwner = this;
    if (!StartAction(hwnd, LoadSettings(), L"port", b.Build(), error)) {
        g_runOwner = nullptr;
        SetCtlText(result_, L"启动失败：" + error);
        return;
    }

    running_ = true;
    EnableWindow(convert_, FALSE);
    SendMessageW(log_, LB_RESETCONTENT, 0, 0);
    SendMessageW(progress_, PBM_SETPOS, 0, 0);
    SetCtlText(result_, L"");
    SetCtlText(progressText_, L"正在启动后端…");
    g_lastReportJson.clear();
    g_lastReportMd.clear();
    g_lastTodoReport.clear();
    g_lastOutputDir = output;
}

void ConvertPage::HandleProgressLine(const std::wstring& line) {
    mjson::Value evt;
    if (!mjson::Parse(line, evt) || !evt.IsObject()) {
        ListBoxAppend(log_, line);      // 非 JSON 行（如 Java 堆栈）按普通消息展示
        return;
    }
    std::wstring type = evt.Str(L"type");
    if (type == L"file") {
        long long index = evt.Int(L"index");
        long long total = evt.Int(L"total");
        if (total > 0) {
            int pos = static_cast<int>(index * 1000 / total);
            SendMessageW(progress_, PBM_SETPOS, static_cast<WPARAM>(pos), 0);
        }
        SetCtlText(progressText_, L"[" + NumStr(index + 1) + L"/" + NumStr(total) + L"] "
                                  + evt.Str(L"path"));
    } else if (type == L"fileDone") {
        long long todos = evt.Int(L"todos");
        if (todos > 0) {
            ListBoxAppend(log_, evt.Str(L"path") + L"   [TODO x" + NumStr(todos) + L"]");
        }
    } else if (type == L"message") {
        ListBoxAppend(log_, evt.Str(L"text"));
    } else if (type == L"fatal") {
        SetCtlText(result_, L"错误：" + evt.Str(L"text"));
        ListBoxAppend(log_, evt.Str(L"text"));
    }
}

LRESULT ConvertPage::OnAppMessage(UINT msg, WPARAM wp, LPARAM lp) {
    (void)wp;
    if (msg == WM_APP_PROGRESS) {
        std::wstring* line = reinterpret_cast<std::wstring*>(lp);
        if (line) {
            HandleProgressLine(*line);
            delete line;
        }
        return 0;
    }
    if (msg == WM_APP_DONE) {
        RunOutcome* outcome = reinterpret_cast<RunOutcome*>(lp);
        running_ = false;
        g_runOwner = nullptr;
        EnableWindow(convert_, TRUE);
        SendMessageW(progress_, PBM_SETPOS, 1000, 0);
        SetCtlText(progressText_, L"");
        if (outcome) {
            if (outcome->ok) {
                g_lastReportJson = outcome->reportJson;
                g_lastReportMd = outcome->reportMd;
                g_lastTodoReport = outcome->todoReport;
                SetCtlText(result_,
                           L"转换完成：自动改写 " + NumStr(outcome->info) + L" 处，警告 "
                           + NumStr(outcome->warn) + L" 处，待人工 " + NumStr(outcome->todo)
                           + L" 处，错误 " + NumStr(outcome->error) + L" 处（详见「转换报告」页）");
            } else {
                SetCtlText(result_, L"转换失败：" + outcome->message);
            }
            delete outcome;
        }
        return 0;
    }
    return 0;
}

} // namespace

PageBase* CreateConvertPage() {
    return new ConvertPage();
}
