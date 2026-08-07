// 「扩展功能」页：完全由后端 capabilities.actions 动态生成。
// 后端新增/启用动作后本页自动出现新入口与参数表单，前端无需改动。
#include "app.h"

namespace {

enum {
    ID_ACTION_LIST = 1301, ID_RUN, ID_REFRESH,
    ID_PARAM_BASE = 1400,          // 动态参数控件 id 起点
    ID_BROWSE_BASE = 1500          // 对应的浏览按钮 id 起点
};

struct ParamCtl {
    ActionParam meta;
    HWND label = nullptr;
    HWND input = nullptr;
    HWND browse = nullptr;
};

class ExtensionsPage : public PageBase {
public:
    void OnCreate() override;
    void OnSize(int w, int h) override;
    void OnCommand(int id, int code, HWND ctl) override;
    LRESULT OnAppMessage(UINT msg, WPARAM wp, LPARAM lp) override;
    void OnActivate() override;

private:
    HWND title_ = nullptr, desc_ = nullptr, status_ = nullptr;
    HWND listLabel_ = nullptr, list_ = nullptr;
    HWND detailTitle_ = nullptr, detailDesc_ = nullptr, badge_ = nullptr;
    HWND run_ = nullptr, refresh_ = nullptr, runStatus_ = nullptr;
    std::vector<ParamCtl> params_;
    std::vector<const ActionInfo*> shown_;
    bool loaded_ = false, running_ = false;

    void ReloadActions();
    void RebuildDetail();
    void ClearParams();
    const ActionInfo* Selected() const;
    void RunSelected();
    void LayoutDetail(int w, int h);
    int  HeaderHeight() const;
};

void ExtensionsPage::OnCreate() {
    title_ = MakeCtl(hwnd, L"STATIC", L"扩展功能", 0, -1);
    SetCtlFont(title_, g_titleFont);
    desc_ = MakeCtl(hwnd, L"STATIC",
                    L"本页由后端能力清单动态生成。后端实现新功能后这里会自动出现可用入口，前端无需更新。",
                    0, -1);
    status_ = MakeCtl(hwnd, L"STATIC", L"", SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);
    listLabel_ = MakeCtl(hwnd, L"STATIC", L"可用动作", 0, -1);
    list_ = MakeCtl(hwnd, L"LISTBOX", L"",
                    WS_BORDER | WS_VSCROLL | WS_TABSTOP | LBS_NOTIFY, ID_ACTION_LIST);
    detailTitle_ = MakeCtl(hwnd, L"STATIC", L"", 0, -1);
    badge_ = MakeCtl(hwnd, L"STATIC", L"", SS_CENTER | WS_BORDER, -1);
    detailDesc_ = MakeCtl(hwnd, L"STATIC", L"", 0, -1);
    run_ = MakeCtl(hwnd, L"BUTTON", L"执行", BS_PUSHBUTTON | WS_TABSTOP, ID_RUN);
    refresh_ = MakeCtl(hwnd, L"BUTTON", L"刷新", BS_PUSHBUTTON | WS_TABSTOP, ID_REFRESH);
    runStatus_ = MakeCtl(hwnd, L"STATIC", L"", SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);
    EnableWindow(run_, FALSE);
    ShowWindow(badge_, SW_HIDE);
}

void ExtensionsPage::OnActivate() {
    if (!loaded_) {
        loaded_ = true;
        ReloadActions();
    }
}

void ExtensionsPage::ReloadActions() {
    if (!g_caps.valid) {
        HCURSOR old = SetCursor(LoadCursor(nullptr, IDC_WAIT));
        std::wstring error;
        Capabilities caps;
        bool ok = FetchCapabilities(LoadSettings(), caps, error);
        SetCursor(old);
        if (!ok) {
            SetCtlText(status_, L"无法获取后端能力清单：" + error);
            return;
        }
        g_caps = caps;
    }

    SendMessageW(list_, LB_RESETCONTENT, 0, 0);
    shown_.clear();
    for (const auto& a : g_caps.actions) {
        if (a.builtin) continue;      // 内置的 port 在「版本转换」页
        shown_.push_back(&a);
        std::wstring label = a.title + (a.available ? L"" : L"（待后端实现）");
        SendMessageW(list_, LB_ADDSTRING, 0, reinterpret_cast<LPARAM>(label.c_str()));
    }
    if (shown_.empty()) {
        SetCtlText(status_, L"后端没有声明任何扩展动作。");
    } else {
        SetCtlText(status_, L"共 " + NumStr(static_cast<long long>(shown_.size()))
                            + L" 个扩展动作（标注「待后端实现」的为预留入口）");
        SendMessageW(list_, LB_SETCURSEL, 0, 0);
    }
    RebuildDetail();
}

const ActionInfo* ExtensionsPage::Selected() const {
    int idx = static_cast<int>(SendMessageW(list_, LB_GETCURSEL, 0, 0));
    if (idx < 0 || idx >= static_cast<int>(shown_.size())) return nullptr;
    return shown_[static_cast<size_t>(idx)];
}

void ExtensionsPage::ClearParams() {
    for (auto& p : params_) {
        if (p.label) DestroyWindow(p.label);
        if (p.input) DestroyWindow(p.input);
        if (p.browse) DestroyWindow(p.browse);
    }
    params_.clear();
}

void ExtensionsPage::RebuildDetail() {
    ClearParams();
    const ActionInfo* action = Selected();
    if (!action) {
        SetCtlText(detailTitle_, L"");
        SetCtlText(detailDesc_, L"");
        SetCtlText(runStatus_, L"");
        ShowWindow(badge_, SW_HIDE);
        EnableWindow(run_, FALSE);
        return;
    }

    SetCtlText(detailTitle_, action->title);
    SetCtlText(detailDesc_, action->description);
    SetCtlText(runStatus_, L"");
    if (action->available) {
        ShowWindow(badge_, SW_HIDE);
        SetCtlText(run_, L"执行");
    } else {
        SetCtlText(badge_, L"待后端实现");
        ShowWindow(badge_, SW_SHOW);
        SetCtlText(run_, L"执行（后端未实现）");
    }
    EnableWindow(run_, action->available && !running_ ? TRUE : FALSE);

    int index = 0;
    for (const auto& meta : action->params) {
        ParamCtl pc;
        pc.meta = meta;
        std::wstring label = meta.title + (meta.required ? L" *" : L"");
        pc.label = MakeCtl(hwnd, L"STATIC", label.c_str(), 0, -1);

        if (meta.type == L"bool") {
            pc.input = MakeCtl(hwnd, L"BUTTON", L"", BS_AUTOCHECKBOX | WS_TABSTOP,
                               ID_PARAM_BASE + index);
        } else if (meta.type.compare(0, 5, L"enum:") == 0) {
            pc.input = MakeCtl(hwnd, L"COMBOBOX", L"",
                               CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP, ID_PARAM_BASE + index);
            std::wstring source = meta.type.substr(5);
            std::vector<std::wstring> items;
            if (source == L"loaders") {
                for (const auto& l : g_caps.loaders) items.push_back(l.id);
            } else if (source == L"versions") {
                items = g_caps.AllVersions();
            }
            ComboFill(pc.input, items, 0);
        } else {
            pc.input = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL,
                               ID_PARAM_BASE + index);
            if (meta.type == L"pathList") {
                SetCtlText(pc.label, label + L"（多个路径用分号 ; 分隔）");
            }
            if (meta.type == L"path") {
                pc.browse = MakeCtl(hwnd, L"BUTTON", L"浏览…", BS_PUSHBUTTON | WS_TABSTOP,
                                    ID_BROWSE_BASE + index);
            }
        }
        EnableWindow(pc.input, action->available ? TRUE : FALSE);
        if (pc.browse) EnableWindow(pc.browse, action->available ? TRUE : FALSE);
        params_.push_back(pc);
        index++;
    }

    RECT rc;
    GetClientRect(hwnd, &rc);
    LayoutDetail(rc.right - rc.left, rc.bottom - rc.top);
}

// 头部（标题/说明/状态）占用的高度，OnSize 与 LayoutDetail 共用同一套算法
int ExtensionsPage::HeaderHeight() const {
    const int LH = g_labelH;
    return Sx(16) + (LH + Sx(14)) + (LH * 2 + Sx(4)) + (LH + Sx(14));
}

void ExtensionsPage::LayoutDetail(int w, int h) {
    const int M = Sx(16), G = Sx(10), R = g_rowH, LH = g_labelH;
    int listW = Sx(240);
    int x = M + listW + G * 2;
    int cw = w - x - M;
    if (cw < Sx(240)) cw = Sx(240);
    int y = HeaderHeight() + LH + Sx(3);      // 与左侧列表顶端对齐

    int badgeW = Sx(120);
    Place(detailTitle_, x, y, cw - badgeW - G, LH + Sx(4));
    Place(badge_, x + cw - badgeW, y, badgeW, LH + Sx(4));
    y += LH + Sx(12);
    Place(detailDesc_, x, y, cw, LH * 2);
    y += LH * 2 + Sx(12);

    for (auto& p : params_) {
        Place(p.label, x, y, cw, LH);
        y += LH + Sx(3);
        if (p.browse) {
            int browseW = Sx(96);
            Place(p.input, x, y, cw - browseW - G, R);
            Place(p.browse, x + cw - browseW, y, browseW, R);
        } else if (p.meta.type == L"bool") {
            Place(p.input, x, y, cw, LH + Sx(4));
        } else if (p.meta.type.compare(0, 5, L"enum:") == 0) {
            int comboW = cw > Sx(260) ? Sx(260) : cw;
            Place(p.input, x, y, comboW, Sx(280));
        } else {
            Place(p.input, x, y, cw, R);
        }
        y += R + G;
    }

    y += Sx(6);
    int runW = Sx(180), refreshW = Sx(100);
    Place(run_, x, y, runW, R);
    Place(refresh_, x + runW + G, y, refreshW, R);
    y += R + Sx(12);
    int statusH = h - y - M;
    if (statusH < LH) statusH = LH;
    if (statusH > LH * 3) statusH = LH * 3;
    Place(runStatus_, x, y, cw, statusH);
}

void ExtensionsPage::OnSize(int w, int h) {
    const int M = Sx(16), G = Sx(10), LH = g_labelH;
    int y = M;
    int cw = w - M * 2;
    if (cw < Sx(320)) cw = Sx(320);

    Place(title_, M, y, cw, LH + Sx(8));    y += LH + Sx(14);
    Place(desc_, M, y, cw, LH * 2);         y += LH * 2 + Sx(4);
    Place(status_, M, y, cw, LH);           y += LH + Sx(14);

    int listW = Sx(240);
    Place(listLabel_, M, y, listW, LH);
    int listY = y + LH + Sx(3);
    int listH = h - listY - M;
    if (listH < Sx(100)) listH = Sx(100);
    Place(list_, M, listY, listW + G, listH);

    LayoutDetail(w, h);
}

void ExtensionsPage::RunSelected() {
    const ActionInfo* action = Selected();
    if (!action || !action->available || running_) return;

    mjson::Builder b;
    for (const auto& p : params_) {
        if (p.meta.type == L"bool") {
            b.AddBool(p.meta.id, SendMessageW(p.input, BM_GETCHECK, 0, 0) == BST_CHECKED);
        } else if (p.meta.type.compare(0, 5, L"enum:") == 0) {
            std::wstring v = ComboText(p.input);
            if (!v.empty()) b.AddString(p.meta.id, v);
        } else if (p.meta.type == L"pathList") {
            std::wstring raw = Trim(GetCtlText(p.input));
            std::vector<std::wstring> parts;
            size_t start = 0;
            while (start <= raw.size()) {
                size_t pos = raw.find(L';', start);
                std::wstring piece = Trim(raw.substr(start, pos == std::wstring::npos
                                                            ? std::wstring::npos : pos - start));
                if (!piece.empty()) parts.push_back(piece);
                if (pos == std::wstring::npos) break;
                start = pos + 1;
            }
            if (!parts.empty()) b.AddStringArray(p.meta.id, parts);
        } else {
            std::wstring v = Trim(GetCtlText(p.input));
            if (!v.empty()) b.AddString(p.meta.id, v);
        }
    }

    std::wstring error;
    g_runOwner = this;
    if (!StartAction(hwnd, LoadSettings(), action->id, b.Build(), error)) {
        g_runOwner = nullptr;
        SetCtlText(runStatus_, L"启动失败：" + error);
        return;
    }
    running_ = true;
    EnableWindow(run_, FALSE);
    SetCtlText(runStatus_, L"执行中…");
}

void ExtensionsPage::OnCommand(int id, int code, HWND ctl) {
    (void)ctl;
    if (id == ID_ACTION_LIST && code == LBN_SELCHANGE) {
        RebuildDetail();
        return;
    }
    if (id == ID_RUN) {
        RunSelected();
        return;
    }
    if (id == ID_REFRESH) {
        InvalidateCapabilities();
        loaded_ = false;
        OnActivate();
        return;
    }
    if (id >= ID_BROWSE_BASE && id < ID_BROWSE_BASE + 64) {
        size_t idx = static_cast<size_t>(id - ID_BROWSE_BASE);
        if (idx < params_.size()) {
            std::wstring p = PickFolder(hwnd, L"选择目录");
            if (!p.empty()) SetCtlText(params_[idx].input, p);
        }
    }
}

LRESULT ExtensionsPage::OnAppMessage(UINT msg, WPARAM wp, LPARAM lp) {
    (void)wp;
    if (msg == WM_APP_PROGRESS) {
        std::wstring* line = reinterpret_cast<std::wstring*>(lp);
        if (line) {
            mjson::Value evt;
            if (mjson::Parse(*line, evt) && evt.IsObject()) {
                std::wstring type = evt.Str(L"type");
                if (type == L"message" || type == L"fatal") {
                    SetCtlText(runStatus_, evt.Str(L"text"));
                } else if (type == L"file") {
                    SetCtlText(runStatus_, evt.Str(L"path"));
                }
            } else {
                SetCtlText(runStatus_, *line);
            }
            delete line;
        }
        return 0;
    }
    if (msg == WM_APP_DONE) {
        RunOutcome* outcome = reinterpret_cast<RunOutcome*>(lp);
        running_ = false;
        g_runOwner = nullptr;
        const ActionInfo* action = Selected();
        EnableWindow(run_, action && action->available ? TRUE : FALSE);
        if (outcome) {
            if (outcome->ok) {
                SetCtlText(runStatus_, L"完成：状态 " + outcome->status + L"，待人工 "
                                       + NumStr(outcome->todo) + L" 处，错误 "
                                       + NumStr(outcome->error) + L" 处");
            } else {
                SetCtlText(runStatus_, L"失败：" + outcome->message);
            }
            delete outcome;
        }
        return 0;
    }
    return 0;
}

} // namespace

PageBase* CreateExtensionsPage() {
    return new ExtensionsPage();
}
