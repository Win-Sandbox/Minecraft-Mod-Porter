// 「转换报告」页：读取 modporter-report.json，统计 + 级别过滤 + 明细列表。
#include "app.h"

namespace {

enum {
    ID_FILTER = 1201, ID_OPEN_OUTPUT, ID_OPEN_MD, ID_OPEN_TODO, ID_RELOAD, ID_LIST
};

struct Entry {
    std::wstring severity, location, category, message;
};

class ReportPage : public PageBase {
public:
    void OnCreate() override;
    void OnSize(int w, int h) override;
    void OnCommand(int id, int code, HWND ctl) override;
    void OnActivate() override;

private:
    HWND title_ = nullptr, subtitle_ = nullptr;
    HWND stats_[4] = {nullptr, nullptr, nullptr, nullptr};
    HWND filterLabel_ = nullptr, filter_ = nullptr;
    HWND openOutput_ = nullptr, openMd_ = nullptr, openTodo_ = nullptr, reload_ = nullptr;
    HWND list_ = nullptr;
    std::vector<Entry> entries_;
    std::wstring loadedFrom_;

    void LoadReport(bool force);
    void ApplyFilter();
};

void ReportPage::OnCreate() {
    title_ = MakeCtl(hwnd, L"STATIC", L"转换报告", 0, -1);
    SetCtlFont(title_, g_titleFont);
    subtitle_ = MakeCtl(hwnd, L"STATIC", L"还没有转换报告。先在「版本转换」页完成一次转换。",
                        SS_LEFTNOWORDWRAP | SS_ENDELLIPSIS, -1);
    for (int i = 0; i < 4; i++) {
        stats_[i] = MakeCtl(hwnd, L"STATIC", L"", SS_CENTER | WS_BORDER, -1);
    }
    filterLabel_ = MakeCtl(hwnd, L"STATIC", L"级别过滤", 0, -1);
    filter_ = MakeCtl(hwnd, L"COMBOBOX", L"", CBS_DROPDOWNLIST | WS_TABSTOP, ID_FILTER);
    std::vector<std::wstring> items;
    items.push_back(L"全部");
    items.push_back(L"待人工 (TODO)");
    items.push_back(L"错误 (ERROR)");
    items.push_back(L"警告 (WARN)");
    items.push_back(L"自动改写 (INFO)");
    ComboFill(filter_, items, 0);

    openOutput_ = MakeCtl(hwnd, L"BUTTON", L"打开输出目录", BS_PUSHBUTTON | WS_TABSTOP, ID_OPEN_OUTPUT);
    openMd_ = MakeCtl(hwnd, L"BUTTON", L"打开 Markdown 报告", BS_PUSHBUTTON | WS_TABSTOP, ID_OPEN_MD);
    openTodo_ = MakeCtl(hwnd, L"BUTTON", L"打开 TODO 清单", BS_PUSHBUTTON | WS_TABSTOP, ID_OPEN_TODO);
    reload_ = MakeCtl(hwnd, L"BUTTON", L"重新载入", BS_PUSHBUTTON | WS_TABSTOP, ID_RELOAD);

    list_ = MakeCtl(hwnd, WC_LISTVIEWW, L"",
                    LVS_REPORT | LVS_SHOWSELALWAYS | LVS_SINGLESEL | WS_TABSTOP | WS_BORDER,
                    ID_LIST);
    ListView_SetExtendedListViewStyle(list_, LVS_EX_FULLROWSELECT | LVS_EX_GRIDLINES);

    struct { const wchar_t* text; int width; } cols[] = {
        {L"级别", 78}, {L"位置", 280}, {L"分类", 130}, {L"说明", 700}
    };
    for (int i = 0; i < 4; i++) {
        LVCOLUMNW col = {};
        col.mask = LVCF_TEXT | LVCF_WIDTH | LVCF_SUBITEM;
        col.pszText = const_cast<wchar_t*>(cols[i].text);
        col.cx = Sx(cols[i].width);
        col.iSubItem = i;
        ListView_InsertColumn(list_, i, &col);
    }
}

void ReportPage::OnSize(int w, int h) {
    const int M = Sx(16), G = Sx(10);
    const int R = g_rowH, LH = g_labelH;
    int x = M, y = M, cw = w - M * 2;
    if (cw < Sx(320)) cw = Sx(320);

    Place(title_, x, y, cw, LH + Sx(8));    y += LH + Sx(14);
    Place(subtitle_, x, y, cw, LH);         y += LH + Sx(12);

    int cardW = (cw - G * 3) / 4;
    int cardH = LH * 2 + Sx(14);            // 两行文本（标题 + 数字）+ 内边距
    for (int i = 0; i < 4; i++) {
        Place(stats_[i], x + i * (cardW + G), y, cardW, cardH);
    }
    y += cardH + Sx(14);

    int labelW = Sx(64), filterW = Sx(180);
    Place(filterLabel_, x, y + (R - LH) / 2, labelW, LH);
    Place(filter_, x + labelW, y, filterW, Sx(240));

    // 三个「打开…」按钮宽度随窗口自适应，窄窗口下不会挤出可视区
    int btnLeft = x + labelW + filterW + G;
    int avail = cw - (btnLeft - x);
    int btnW = (avail - G * 3) / 4;
    if (btnW > Sx(160)) btnW = Sx(160);
    if (btnW < Sx(80))  btnW = Sx(80);
    Place(openOutput_, btnLeft, y, btnW, R);
    Place(openMd_, btnLeft + (btnW + G), y, btnW, R);
    Place(openTodo_, btnLeft + (btnW + G) * 2, y, btnW, R);
    Place(reload_, btnLeft + (btnW + G) * 3, y, btnW, R);
    y += R + Sx(12);

    int listH = h - y - M;
    if (listH < Sx(100)) listH = Sx(100);
    Place(list_, x, y, cw, listH);
}

void ReportPage::OnActivate() {
    LoadReport(false);
}

void ReportPage::LoadReport(bool force) {
    if (g_lastReportJson.empty()) {
        SetCtlText(subtitle_, L"还没有转换报告。先在「版本转换」页完成一次转换。");
        return;
    }
    if (!force && loadedFrom_ == g_lastReportJson) return;

    std::string raw;
    if (!ReadWholeFileUtf8(g_lastReportJson, raw)) {
        SetCtlText(subtitle_, L"报告文件不存在或无法读取：" + g_lastReportJson);
        return;
    }
    mjson::Value root;
    if (!mjson::Parse(Utf8ToWide(raw), root) || !root.IsObject()) {
        SetCtlText(subtitle_, L"报告文件无法解析：" + g_lastReportJson);
        return;
    }
    loadedFrom_ = g_lastReportJson;

    SetCtlText(subtitle_, root.Str(L"sourceVersion") + L"  →  " + root.Str(L"targetVersion")
                          + L"      " + g_lastReportJson);

    struct { const wchar_t* label; const wchar_t* key; } cards[] = {
        {L"自动改写", L"infoCount"}, {L"警告", L"warnCount"},
        {L"待人工处理", L"todoCount"}, {L"错误", L"errorCount"}
    };
    for (int i = 0; i < 4; i++) {
        SetCtlText(stats_[i], std::wstring(cards[i].label) + L"\n" + NumStr(root.Int(cards[i].key)));
    }

    entries_.clear();
    if (const mjson::Value* arr = root.Arr(L"entries")) {
        for (const auto& e : arr->elements) {
            Entry item;
            item.severity = e.Str(L"severity");
            item.category = e.Str(L"category");
            item.message = e.Str(L"message");
            std::wstring file = e.Str(L"file");
            if (!file.empty()) {
                const mjson::Value* lineVal = e.Find(L"line");
                if (lineVal && lineVal->type == mjson::Value::Type::Number) {
                    file += L":" + NumStr(static_cast<long long>(lineVal->number));
                }
            }
            item.location = file;
            entries_.push_back(item);
        }
    }
    ApplyFilter();
}

void ReportPage::ApplyFilter() {
    int sel = static_cast<int>(SendMessageW(filter_, CB_GETCURSEL, 0, 0));
    const wchar_t* want = nullptr;
    switch (sel) {
        case 1: want = L"TODO";  break;
        case 2: want = L"ERROR"; break;
        case 3: want = L"WARN";  break;
        case 4: want = L"INFO";  break;
        default: want = nullptr; break;
    }

    SendMessageW(list_, WM_SETREDRAW, FALSE, 0);
    ListView_DeleteAllItems(list_);
    int row = 0;
    for (const auto& e : entries_) {
        if (want && e.severity != want) continue;
        LVITEMW item = {};
        item.mask = LVIF_TEXT;
        item.iItem = row;
        item.pszText = const_cast<wchar_t*>(e.severity.c_str());
        ListView_InsertItem(list_, &item);
        ListView_SetItemText(list_, row, 1, const_cast<wchar_t*>(e.location.c_str()));
        ListView_SetItemText(list_, row, 2, const_cast<wchar_t*>(e.category.c_str()));
        ListView_SetItemText(list_, row, 3, const_cast<wchar_t*>(e.message.c_str()));
        row++;
    }
    SendMessageW(list_, WM_SETREDRAW, TRUE, 0);
    InvalidateRect(list_, nullptr, TRUE);
}

void ReportPage::OnCommand(int id, int code, HWND ctl) {
    (void)ctl;
    switch (id) {
        case ID_FILTER:
            if (code == CBN_SELCHANGE) ApplyFilter();
            break;
        case ID_OPEN_OUTPUT:
            if (DirectoryExists(g_lastOutputDir)) OpenWithShell(hwnd, g_lastOutputDir);
            else MessageBoxW(hwnd, L"还没有输出目录。", L"ModPorter", MB_OK | MB_ICONINFORMATION);
            break;
        case ID_OPEN_MD:
            if (FileExists(g_lastReportMd)) OpenWithShell(hwnd, g_lastReportMd);
            else MessageBoxW(hwnd, L"还没有 Markdown 报告。", L"ModPorter", MB_OK | MB_ICONINFORMATION);
            break;
        case ID_OPEN_TODO:
            if (FileExists(g_lastTodoReport)) {
                OpenWithShell(hwnd, g_lastTodoReport);
            } else {
                MessageBoxW(hwnd,
                            L"本次转换没有生成 TODO 清单（说明转换后的工程里没有任何 TODO/FIXME）。",
                            L"ModPorter", MB_OK | MB_ICONINFORMATION);
            }
            break;
        case ID_RELOAD:
            LoadReport(true);
            break;
        default:
            break;
    }
}

} // namespace

PageBase* CreateReportPage() {
    return new ReportPage();
}
