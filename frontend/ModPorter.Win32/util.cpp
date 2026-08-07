#include "app.h"
#include <shlobj.h>
#include <commdlg.h>
#include <shellapi.h>

HFONT g_font = nullptr;
HFONT g_titleFont = nullptr;
int   g_dpi = 96;
int   g_rowH = 26;
int   g_labelH = 18;

int Sx(int px) {
    return MulDiv(px, g_dpi, 96);
}

void SetCtlFont(HWND h, HFONT font) {
    if (h && font) SendMessageW(h, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
}

std::wstring Utf8ToWide(const std::string& s) {
    if (s.empty()) return std::wstring();
    int need = MultiByteToWideChar(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), nullptr, 0);
    std::wstring out(static_cast<size_t>(need), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), &out[0], need);
    return out;
}

std::string WideToUtf8(const std::wstring& s) {
    if (s.empty()) return std::string();
    int need = WideCharToMultiByte(CP_UTF8, 0, s.data(), static_cast<int>(s.size()),
                                   nullptr, 0, nullptr, nullptr);
    std::string out(static_cast<size_t>(need), '\0');
    WideCharToMultiByte(CP_UTF8, 0, s.data(), static_cast<int>(s.size()),
                        &out[0], need, nullptr, nullptr);
    return out;
}

std::wstring Trim(const std::wstring& s) {
    size_t b = s.find_first_not_of(L" \t\r\n");
    if (b == std::wstring::npos) return std::wstring();
    size_t e = s.find_last_not_of(L" \t\r\n");
    return s.substr(b, e - b + 1);
}

// XP 起可用；不使用 Vista+ 的 IFileDialog，保证低版本 Windows 兼容。
std::wstring PickFolder(HWND owner, const wchar_t* title) {
    wchar_t display[MAX_PATH] = {};
    BROWSEINFOW bi = {};
    bi.hwndOwner = owner;
    bi.pszDisplayName = display;
    bi.lpszTitle = title;
    bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
    std::wstring result;
    LPITEMIDLIST idl = SHBrowseForFolderW(&bi);
    if (idl) {
        wchar_t path[MAX_PATH] = {};
        if (SHGetPathFromIDListW(idl, path)) result = path;
        IMalloc* malloc_ = nullptr;
        if (SUCCEEDED(SHGetMalloc(&malloc_)) && malloc_) {
            malloc_->Free(idl);
            malloc_->Release();
        }
    }
    return result;
}

std::wstring PickFile(HWND owner, const wchar_t* title, const wchar_t* filter) {
    wchar_t buffer[MAX_PATH] = {};
    OPENFILENAMEW ofn = {};
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = owner;
    ofn.lpstrFilter = filter;
    ofn.lpstrFile = buffer;
    ofn.nMaxFile = MAX_PATH;
    ofn.lpstrTitle = title;
    ofn.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_EXPLORER;
    if (GetOpenFileNameW(&ofn)) return buffer;
    return std::wstring();
}

bool ReadWholeFileUtf8(const std::wstring& path, std::string& out) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr,
                           OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    out.clear();
    char buf[8192];
    DWORD got = 0;
    while (ReadFile(h, buf, sizeof(buf), &got, nullptr) && got > 0) {
        out.append(buf, got);
    }
    CloseHandle(h);
    // 去掉可能存在的 UTF-8 BOM
    if (out.size() >= 3 && static_cast<unsigned char>(out[0]) == 0xEF
        && static_cast<unsigned char>(out[1]) == 0xBB
        && static_cast<unsigned char>(out[2]) == 0xBF) {
        out.erase(0, 3);
    }
    return true;
}

bool WriteWholeFileUtf8(const std::wstring& path, const std::string& content) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    BOOL ok = TRUE;
    if (!content.empty()) {
        ok = WriteFile(h, content.data(), static_cast<DWORD>(content.size()), &written, nullptr);
    }
    CloseHandle(h);
    return ok == TRUE;
}

std::wstring MakeTempFilePath(const wchar_t* prefix) {
    wchar_t dir[MAX_PATH] = {};
    if (!GetTempPathW(MAX_PATH, dir)) return std::wstring();
    wchar_t file[MAX_PATH] = {};
    if (!GetTempFileNameW(dir, prefix, 0, file)) return std::wstring();
    return file;
}

bool FileExists(const std::wstring& path) {
    if (path.empty()) return false;
    DWORD attr = GetFileAttributesW(path.c_str());
    return attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY);
}

bool DirectoryExists(const std::wstring& path) {
    if (path.empty()) return false;
    DWORD attr = GetFileAttributesW(path.c_str());
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

std::wstring PathJoin(const std::wstring& dir, const std::wstring& name) {
    if (dir.empty()) return name;
    wchar_t last = dir[dir.size() - 1];
    if (last == L'\\' || last == L'/') return dir + name;
    return dir + L"\\" + name;
}

void OpenWithShell(HWND owner, const std::wstring& path) {
    if (path.empty()) return;
    ShellExecuteW(owner, L"open", path.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
}

HWND MakeCtl(HWND parent, const wchar_t* cls, const wchar_t* text,
             DWORD style, int id, DWORD exStyle) {
    HWND h = CreateWindowExW(exStyle, cls, text, WS_CHILD | WS_VISIBLE | style,
                             0, 0, 10, 10, parent,
                             reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
                             GetModuleHandleW(nullptr), nullptr);
    if (h && g_font) SendMessageW(h, WM_SETFONT, reinterpret_cast<WPARAM>(g_font), TRUE);
    return h;
}

void SetCtlText(HWND h, const std::wstring& t) {
    if (h) SetWindowTextW(h, t.c_str());
}

std::wstring GetCtlText(HWND h) {
    if (!h) return std::wstring();
    int len = GetWindowTextLengthW(h);
    if (len <= 0) return std::wstring();
    std::wstring out(static_cast<size_t>(len) + 1, L'\0');
    GetWindowTextW(h, &out[0], len + 1);
    out.resize(static_cast<size_t>(len));
    return out;
}

void Place(HWND h, int x, int y, int w, int hgt) {
    if (h) MoveWindow(h, x, y, w, hgt, TRUE);
}

std::wstring NumStr(long long v) {
    wchar_t buf[32];
    bool neg = v < 0;
    unsigned long long u = neg ? static_cast<unsigned long long>(-(v + 1)) + 1ULL
                               : static_cast<unsigned long long>(v);
    int i = 31;
    buf[i--] = L'\0';
    if (u == 0) buf[i--] = L'0';
    while (u > 0 && i >= 0) {
        buf[i--] = static_cast<wchar_t>(L'0' + (u % 10));
        u /= 10;
    }
    if (neg && i >= 0) buf[i--] = L'-';
    return std::wstring(buf + i + 1);
}

std::wstring ComboText(HWND combo) {
    if (!combo) return std::wstring();
    int idx = static_cast<int>(SendMessageW(combo, CB_GETCURSEL, 0, 0));
    if (idx < 0) return std::wstring();
    int len = static_cast<int>(SendMessageW(combo, CB_GETLBTEXTLEN, idx, 0));
    if (len <= 0) return std::wstring();
    std::wstring out(static_cast<size_t>(len) + 1, L'\0');
    SendMessageW(combo, CB_GETLBTEXT, idx, reinterpret_cast<LPARAM>(&out[0]));
    out.resize(static_cast<size_t>(len));
    return out;
}

void ComboFill(HWND combo, const std::vector<std::wstring>& items, int select) {
    if (!combo) return;
    SendMessageW(combo, CB_RESETCONTENT, 0, 0);
    for (const auto& s : items) {
        SendMessageW(combo, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(s.c_str()));
    }
    if (!items.empty()) {
        if (select < 0) select = 0;
        if (select >= static_cast<int>(items.size())) select = static_cast<int>(items.size()) - 1;
        SendMessageW(combo, CB_SETCURSEL, static_cast<WPARAM>(select), 0);
    }
}

void ListBoxAppend(HWND list, const std::wstring& line) {
    if (!list) return;
    int idx = static_cast<int>(SendMessageW(list, LB_ADDSTRING, 0,
                                            reinterpret_cast<LPARAM>(line.c_str())));
    if (idx >= 0) SendMessageW(list, LB_SETTOPINDEX, static_cast<WPARAM>(idx), 0);
}
