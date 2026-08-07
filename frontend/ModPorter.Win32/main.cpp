// ModPorter Win32 前端入口：Tab 页框架（相当于 WinUI 版的 NavigationView）。
#include "app.h"
#include "resource.h"
#include <ole2.h>   // OleInitialize/OleUninitialize（WIN32_LEAN_AND_MEAN 下 windows.h 不含）

#ifdef _MSC_VER   // MinGW 由构建脚本 / CMake 负责链接这些库
#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "comdlg32.lib")
#pragma comment(lib, "ole32.lib")
#endif

// ---------------- 全局会话状态 ----------------
Capabilities g_caps;
std::wstring g_lastReportJson;
std::wstring g_lastReportMd;
std::wstring g_lastTodoReport;
std::wstring g_lastOutputDir;
PageBase*    g_runOwner = nullptr;
HWND         g_mainWnd = nullptr;

void InvalidateCapabilities() {
    g_caps = Capabilities();
}

namespace {

const wchar_t* kMainClass = L"ModPorterMainWindow";
const wchar_t* kPageClass = L"ModPorterPage";
const int kTabId = 100;

struct PageEntry {
    const wchar_t* caption;
    PageBase* page;
};

PageEntry g_pages[4] = {
    {L"版本转换", nullptr},
    {L"转换报告", nullptr},
    {L"扩展功能", nullptr},
    {L"设置",     nullptr},
};
HWND g_tab = nullptr;
int  g_active = 0;

// 页面首次显示后再触发 OnActivate（其中可能同步调用后端），避免窗口未绘制就卡住
#define WM_APP_PAGEREADY (WM_APP + 3)

LRESULT CALLBACK PageProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    PageBase* page = reinterpret_cast<PageBase*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    switch (msg) {
        case WM_SIZE:
            if (page) page->OnSize(LOWORD(lp), HIWORD(lp));
            // 控件被 MoveWindow 挪动后，其原位置的父窗口背景不会自动重绘，
            // 会残留旧像素（表现为标签发白）。这里连同所有子控件强制重画。
            RedrawWindow(hwnd, nullptr, nullptr,
                         RDW_INVALIDATE | RDW_ERASE | RDW_ALLCHILDREN);
            return 0;
        case WM_COMMAND:
            if (page) page->OnCommand(LOWORD(wp), HIWORD(wp), reinterpret_cast<HWND>(lp));
            return 0;
        case WM_NOTIFY:
            if (page) page->OnNotify(reinterpret_cast<NMHDR*>(lp));
            return 0;
        case WM_CTLCOLORSTATIC: {
            // 让静态文本与页面背景一致
            HDC dc = reinterpret_cast<HDC>(wp);
            SetBkMode(dc, TRANSPARENT);
            return reinterpret_cast<LRESULT>(GetSysColorBrush(COLOR_BTNFACE));
        }
        case WM_APP_PROGRESS:
        case WM_APP_DONE:
            if (page) return page->OnAppMessage(msg, wp, lp);
            return 0;
        default:
            break;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

HWND CreatePageWindow(HWND parent, PageBase* page) {
    HWND h = CreateWindowExW(WS_EX_CONTROLPARENT, kPageClass, L"",
                             WS_CHILD | WS_CLIPCHILDREN,
                             0, 0, 100, 100, parent, nullptr,
                             GetModuleHandleW(nullptr), nullptr);
    if (!h) return nullptr;
    SetWindowLongPtrW(h, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(page));
    page->hwnd = h;
    page->OnCreate();
    return h;
}

void LayoutPages(HWND main) {
    RECT rc;
    GetClientRect(main, &rc);
    MoveWindow(g_tab, 0, 0, rc.right, rc.bottom, TRUE);

    RECT display = rc;
    SendMessageW(g_tab, TCM_ADJUSTRECT, FALSE, reinterpret_cast<LPARAM>(&display));
    int w = display.right - display.left;
    int h = display.bottom - display.top;
    if (w < 10) w = 10;
    if (h < 10) h = 10;
    for (int i = 0; i < 4; i++) {
        if (g_pages[i].page && g_pages[i].page->hwnd) {
            MoveWindow(g_pages[i].page->hwnd, display.left, display.top, w, h, TRUE);
        }
    }
}

void ActivateTab(HWND main, int index) {
    if (index < 0 || index > 3) return;
    g_active = index;
    for (int i = 0; i < 4; i++) {
        if (!g_pages[i].page || !g_pages[i].page->hwnd) continue;
        ShowWindow(g_pages[i].page->hwnd, i == index ? SW_SHOW : SW_HIDE);
    }
    // 延后到消息循环里再执行，让页面先完成绘制
    PostMessageW(main, WM_APP_PAGEREADY, static_cast<WPARAM>(index), 0);
}

LRESULT CALLBACK MainProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_CREATE: {
            g_tab = CreateWindowExW(0, WC_TABCONTROLW, L"",
                                    WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS | TCS_TABS,
                                    0, 0, 100, 100, hwnd,
                                    reinterpret_cast<HMENU>(static_cast<INT_PTR>(kTabId)),
                                    GetModuleHandleW(nullptr), nullptr);
            if (g_font) SendMessageW(g_tab, WM_SETFONT, reinterpret_cast<WPARAM>(g_font), TRUE);

            g_pages[0].page = CreateConvertPage();
            g_pages[1].page = CreateReportPage();
            g_pages[2].page = CreateExtensionsPage();
            g_pages[3].page = CreateSettingsPage();

            for (int i = 0; i < 4; i++) {
                TCITEMW item = {};
                item.mask = TCIF_TEXT;
                item.pszText = const_cast<wchar_t*>(g_pages[i].caption);
                TabCtrl_InsertItem(g_tab, i, &item);
                CreatePageWindow(hwnd, g_pages[i].page);
            }
            LayoutPages(hwnd);
            ActivateTab(hwnd, 0);
            return 0;
        }
        case WM_APP_PAGEREADY: {
            int index = static_cast<int>(wp);
            if (index >= 0 && index < 4 && g_pages[index].page) {
                g_pages[index].page->OnActivate();
            }
            return 0;
        }
        case WM_SIZE:
            if (g_tab) LayoutPages(hwnd);
            return 0;
        case WM_GETMINMAXINFO: {
            MINMAXINFO* mmi = reinterpret_cast<MINMAXINFO*>(lp);
            mmi->ptMinTrackSize.x = Sx(820);
            mmi->ptMinTrackSize.y = Sx(620);
            return 0;
        }
        case WM_NOTIFY: {
            NMHDR* hdr = reinterpret_cast<NMHDR*>(lp);
            if (hdr && hdr->hwndFrom == g_tab && hdr->code == TCN_SELCHANGE) {
                ActivateTab(hwnd, TabCtrl_GetCurSel(g_tab));
                return 0;
            }
            break;
        }
        case WM_APP_PROGRESS:
        case WM_APP_DONE: {
            // 转发给发起该次运行的页面（可能已切到别的标签页）
            if (g_runOwner) return g_runOwner->OnAppMessage(msg, wp, lp);
            if (msg == WM_APP_PROGRESS) delete reinterpret_cast<std::wstring*>(lp);
            if (msg == WM_APP_DONE) delete reinterpret_cast<RunOutcome*>(lp);
            return 0;
        }
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
        default:
            break;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

void CreateUiFont() {
    HDC screen = GetDC(nullptr);
    if (screen) {
        g_dpi = GetDeviceCaps(screen, LOGPIXELSX);
        if (g_dpi <= 0) g_dpi = 96;
    }

    NONCLIENTMETRICSW ncm = {};
    ncm.cbSize = sizeof(ncm);
    // 按当前系统区域取消息字体：中文环境下自动是可显示中文的字体，且已按系统 DPI 缩放
    if (SystemParametersInfoW(SPI_GETNONCLIENTMETRICS, sizeof(ncm), &ncm, 0)) {
        g_font = CreateFontIndirectW(&ncm.lfMessageFont);

        LOGFONTW title = ncm.lfMessageFont;
        title.lfWeight = FW_SEMIBOLD;
        title.lfHeight = MulDiv(title.lfHeight, 13, 10);   // 标题放大 1.3 倍
        g_titleFont = CreateFontIndirectW(&title);
    }
    if (!g_font) {
        g_font = static_cast<HFONT>(GetStockObject(DEFAULT_GUI_FONT));
    }
    if (!g_titleFont) g_titleFont = g_font;

    // 行高按实际字体度量推导，避免高 DPI / 大字体下文字被挤压
    if (screen) {
        HFONT old = static_cast<HFONT>(SelectObject(screen, g_font));
        TEXTMETRICW tm = {};
        if (GetTextMetricsW(screen, &tm)) {
            g_labelH = static_cast<int>(tm.tmHeight) + Sx(5);
            g_rowH = static_cast<int>(tm.tmHeight) + Sx(13);
        }
        SelectObject(screen, old);
        ReleaseDC(nullptr, screen);
    }
    if (g_labelH < Sx(18)) g_labelH = Sx(18);
    if (g_rowH < Sx(26)) g_rowH = Sx(26);
}

} // namespace

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE, LPWSTR, int nCmdShow) {
    INITCOMMONCONTROLSEX icc = {};
    icc.dwSize = sizeof(icc);
    icc.dwICC = ICC_WIN95_CLASSES | ICC_STANDARD_CLASSES | ICC_PROGRESS_CLASS
              | ICC_TAB_CLASSES | ICC_LISTVIEW_CLASSES;
    InitCommonControlsEx(&icc);
    OleInitialize(nullptr);      // SHBrowseForFolder 的新式对话框需要

    CreateUiFont();

    WNDCLASSEXW pageClass = {};
    pageClass.cbSize = sizeof(pageClass);
    pageClass.lpfnWndProc = PageProc;
    pageClass.hInstance = hInstance;
    pageClass.hCursor = LoadCursor(nullptr, IDC_ARROW);
    pageClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_BTNFACE + 1);
    pageClass.lpszClassName = kPageClass;
    RegisterClassExW(&pageClass);

    WNDCLASSEXW mainClass = {};
    mainClass.cbSize = sizeof(mainClass);
    mainClass.lpfnWndProc = MainProc;
    mainClass.hInstance = hInstance;
    mainClass.hCursor = LoadCursor(nullptr, IDC_ARROW);
    mainClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_BTNFACE + 1);
    mainClass.lpszClassName = kMainClass;
    mainClass.hIcon = LoadIconW(hInstance, MAKEINTRESOURCEW(IDI_APPICON));
    if (!mainClass.hIcon) mainClass.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    mainClass.hIconSm = mainClass.hIcon;
    RegisterClassExW(&mainClass);

    g_mainWnd = CreateWindowExW(0, kMainClass,
                                L"ModPorter — MC 模组跨版本转换器",
                                WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN,
                                CW_USEDEFAULT, CW_USEDEFAULT, Sx(1020), Sx(740),
                                nullptr, nullptr, hInstance, nullptr);
    if (!g_mainWnd) {
        MessageBoxW(nullptr, L"无法创建主窗口。", L"ModPorter", MB_OK | MB_ICONERROR);
        return 1;
    }
    ShowWindow(g_mainWnd, nCmdShow);
    UpdateWindow(g_mainWnd);

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
        HWND activePage = (g_active >= 0 && g_pages[g_active].page)
                        ? g_pages[g_active].page->hwnd : nullptr;
        if (activePage && IsDialogMessageW(activePage, &msg)) continue;
        if (IsDialogMessageW(g_mainWnd, &msg)) continue;
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    for (int i = 0; i < 4; i++) delete g_pages[i].page;
    OleUninitialize();
    return static_cast<int>(msg.wParam);
}
