// 「设置」页：Java / 后端 jar / 映射数据目录，与 WinUI 版共用 settings.json。
#include "app.h"

namespace {

enum {
    ID_JAVA = 1601, ID_JAR, ID_JAR_BROWSE, ID_MAPPINGS, ID_MAPPINGS_BROWSE,
    ID_SAVE, ID_TEST
};

class SettingsPage : public PageBase {
public:
    void OnCreate() override;
    void OnSize(int w, int h) override;
    void OnCommand(int id, int code, HWND ctl) override;

private:
    HWND title_ = nullptr;
    HWND javaLabel_ = nullptr, java_ = nullptr;
    HWND jarLabel_ = nullptr, jar_ = nullptr, jarBrowse_ = nullptr;
    HWND mapsLabel_ = nullptr, maps_ = nullptr, mapsBrowse_ = nullptr;
    HWND save_ = nullptr, test_ = nullptr, status_ = nullptr, hint_ = nullptr;

    AppSettings Collect() const;
};

void SettingsPage::OnCreate() {
    title_ = MakeCtl(hwnd, L"STATIC", L"设置", 0, -1);
    SetCtlFont(title_, g_titleFont);

    javaLabel_ = MakeCtl(hwnd, L"STATIC",
                         L"Java 可执行文件（留空使用 PATH 中的 java；后端需 Java 17+）", 0, -1);
    java_ = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL, ID_JAVA);

    jarLabel_ = MakeCtl(hwnd, L"STATIC", L"后端 modporter.jar 路径", 0, -1);
    jar_ = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL, ID_JAR);
    jarBrowse_ = MakeCtl(hwnd, L"BUTTON", L"浏览…", BS_PUSHBUTTON | WS_TABSTOP, ID_JAR_BROWSE);

    mapsLabel_ = MakeCtl(hwnd, L"STATIC",
                         L"映射数据目录（版本数据库，留空则用后端默认 ./mappings）", 0, -1);
    maps_ = MakeCtl(hwnd, L"EDIT", L"", WS_BORDER | WS_TABSTOP | ES_AUTOHSCROLL, ID_MAPPINGS);
    mapsBrowse_ = MakeCtl(hwnd, L"BUTTON", L"浏览…", BS_PUSHBUTTON | WS_TABSTOP, ID_MAPPINGS_BROWSE);

    save_ = MakeCtl(hwnd, L"BUTTON", L"保存", BS_DEFPUSHBUTTON | WS_TABSTOP, ID_SAVE);
    test_ = MakeCtl(hwnd, L"BUTTON", L"测试后端连接", BS_PUSHBUTTON | WS_TABSTOP, ID_TEST);
    status_ = MakeCtl(hwnd, L"STATIC", L"", 0, -1);
    hint_ = MakeCtl(hwnd, L"STATIC",
                    L"设置保存于 %LOCALAPPDATA%\\ModPorter\\settings.json，与 WinUI 版本共用同一份配置。",
                    0, -1);

    AppSettings s = LoadSettings();
    SetCtlText(java_, s.javaPath);
    SetCtlText(jar_, s.jarPath);
    SetCtlText(maps_, s.mappingsDir);
}

void SettingsPage::OnSize(int w, int h) {
    (void)h;
    const int M = Sx(16), G = Sx(10), R = g_rowH, LH = g_labelH;
    int x = M, y = M, cw = w - M * 2;
    if (cw > Sx(820)) cw = Sx(820);
    if (cw < Sx(320)) cw = Sx(320);
    int browseW = Sx(96);

    Place(title_, x, y, cw, LH + Sx(8));    y += LH + Sx(18);

    Place(javaLabel_, x, y, cw, LH);        y += LH + Sx(3);
    Place(java_, x, y, cw, R);              y += R + Sx(14);

    Place(jarLabel_, x, y, cw, LH);         y += LH + Sx(3);
    Place(jar_, x, y, cw - browseW - G, R);
    Place(jarBrowse_, x + cw - browseW, y, browseW, R);
    y += R + Sx(14);

    Place(mapsLabel_, x, y, cw, LH);        y += LH + Sx(3);
    Place(maps_, x, y, cw - browseW - G, R);
    Place(mapsBrowse_, x + cw - browseW, y, browseW, R);
    y += R + Sx(20);

    int saveW = Sx(110), testW = Sx(160);
    Place(save_, x, y, saveW, R);
    Place(test_, x + saveW + G, y, testW, R);
    y += R + Sx(16);

    Place(status_, x, y, cw, LH * 2);       y += LH * 2 + Sx(12);
    Place(hint_, x, y, cw, LH * 2);
}

AppSettings SettingsPage::Collect() const {
    AppSettings s;
    std::wstring java = Trim(GetCtlText(java_));
    s.javaPath = java.empty() ? L"java" : java;
    s.jarPath = Trim(GetCtlText(jar_));
    s.mappingsDir = Trim(GetCtlText(maps_));
    return s;
}

void SettingsPage::OnCommand(int id, int code, HWND ctl) {
    (void)code;
    (void)ctl;
    switch (id) {
        case ID_JAR_BROWSE: {
            std::wstring p = PickFile(hwnd, L"选择 modporter.jar",
                                      L"Jar 文件 (*.jar)\0*.jar\0所有文件 (*.*)\0*.*\0\0");
            if (!p.empty()) SetCtlText(jar_, p);
            break;
        }
        case ID_MAPPINGS_BROWSE: {
            std::wstring p = PickFolder(hwnd, L"选择映射数据目录");
            if (!p.empty()) SetCtlText(maps_, p);
            break;
        }
        case ID_SAVE: {
            if (SaveSettings(Collect())) {
                InvalidateCapabilities();
                SetCtlText(status_, L"设置已保存。其它页面会在下次刷新时重新拉取能力清单。");
            } else {
                SetCtlText(status_, L"设置保存失败（无法写入 %LOCALAPPDATA%\\ModPorter）。");
            }
            break;
        }
        case ID_TEST: {
            AppSettings s = Collect();
            SaveSettings(s);
            InvalidateCapabilities();
            HCURSOR old = SetCursor(LoadCursor(nullptr, IDC_WAIT));
            Capabilities caps;
            std::wstring error;
            bool ok = FetchCapabilities(s, caps, error);
            SetCursor(old);
            if (ok) {
                g_caps = caps;
                SetCtlText(status_,
                           L"后端连接成功：" + caps.backendName + L" " + caps.backendVersion
                           + L"，" + NumStr(static_cast<long long>(caps.loaders.size()))
                           + L" 个加载器 / " + NumStr(caps.VersionCount()) + L" 个版本 / "
                           + NumStr(static_cast<long long>(caps.actions.size())) + L" 个动作");
            } else {
                SetCtlText(status_, L"后端连接失败：" + error);
            }
            break;
        }
        default:
            break;
    }
}

} // namespace

PageBase* CreateSettingsPage() {
    return new SettingsPage();
}
