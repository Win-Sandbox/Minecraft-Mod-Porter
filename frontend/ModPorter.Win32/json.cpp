#include "json.h"
#include <cstdlib>
#include <cwchar>

namespace mjson {

const Value* Value::Find(const std::wstring& key) const {
    if (type != Type::Object) return nullptr;
    for (const auto& m : members) {
        if (m.first == key) return &m.second;
    }
    return nullptr;
}

std::wstring Value::Str(const std::wstring& key, const std::wstring& def) const {
    const Value* v = Find(key);
    return (v && v->type == Type::String) ? v->text : def;
}

double Value::Num(const std::wstring& key, double def) const {
    const Value* v = Find(key);
    return (v && v->type == Type::Number) ? v->number : def;
}

long long Value::Int(const std::wstring& key, long long def) const {
    const Value* v = Find(key);
    return (v && v->type == Type::Number) ? static_cast<long long>(v->number) : def;
}

bool Value::Bool(const std::wstring& key, bool def) const {
    const Value* v = Find(key);
    return (v && v->type == Type::Bool) ? v->boolean : def;
}

const Value* Value::Arr(const std::wstring& key) const {
    const Value* v = Find(key);
    return (v && v->type == Type::Array) ? v : nullptr;
}

// ---------------- 解析 ----------------

namespace {

struct Parser {
    const std::wstring& s;
    size_t i = 0;
    int depth = 0;
    explicit Parser(const std::wstring& text) : s(text) {}

    void Skip() {
        while (i < s.size() && (s[i] == L' ' || s[i] == L'\t' || s[i] == L'\r' || s[i] == L'\n')) i++;
    }
    bool Literal(const wchar_t* lit) {
        size_t n = wcslen(lit);
        if (s.compare(i, n, lit) != 0) return false;
        i += n;
        return true;
    }
    bool ParseString(std::wstring& out) {
        if (i >= s.size() || s[i] != L'"') return false;
        i++;
        out.clear();
        while (i < s.size()) {
            wchar_t c = s[i++];
            if (c == L'"') return true;
            if (c != L'\\') { out.push_back(c); continue; }
            if (i >= s.size()) return false;
            wchar_t e = s[i++];
            switch (e) {
                case L'"':  out.push_back(L'"');  break;
                case L'\\': out.push_back(L'\\'); break;
                case L'/':  out.push_back(L'/');  break;
                case L'b':  out.push_back(L'\b'); break;
                case L'f':  out.push_back(L'\f'); break;
                case L'n':  out.push_back(L'\n'); break;
                case L'r':  out.push_back(L'\r'); break;
                case L't':  out.push_back(L'\t'); break;
                case L'u': {
                    if (i + 4 > s.size()) return false;
                    unsigned code = 0;
                    for (int k = 0; k < 4; k++) {
                        wchar_t h = s[i++];
                        code <<= 4;
                        if (h >= L'0' && h <= L'9')      code |= (h - L'0');
                        else if (h >= L'a' && h <= L'f') code |= (h - L'a' + 10);
                        else if (h >= L'A' && h <= L'F') code |= (h - L'A' + 10);
                        else return false;
                    }
                    out.push_back(static_cast<wchar_t>(code)); // UTF-16：代理对天然保留
                    break;
                }
                default: return false;
            }
        }
        return false;
    }
    bool ParseValue(Value& v) {
        if (++depth > 64) return false;          // 防御性递归深度限制
        struct DepthGuard { int& d; ~DepthGuard() { d--; } } guard{depth};

        Skip();
        if (i >= s.size()) return false;
        wchar_t c = s[i];
        if (c == L'{') {
            i++;
            v.type = Value::Type::Object;
            Skip();
            if (i < s.size() && s[i] == L'}') { i++; return true; }
            while (true) {
                Skip();
                std::wstring key;
                if (!ParseString(key)) return false;
                Skip();
                if (i >= s.size() || s[i] != L':') return false;
                i++;
                Value child;
                if (!ParseValue(child)) return false;
                v.members.emplace_back(key, child);
                Skip();
                if (i < s.size() && s[i] == L',') { i++; continue; }
                if (i < s.size() && s[i] == L'}') { i++; return true; }
                return false;
            }
        }
        if (c == L'[') {
            i++;
            v.type = Value::Type::Array;
            Skip();
            if (i < s.size() && s[i] == L']') { i++; return true; }
            while (true) {
                Value child;
                if (!ParseValue(child)) return false;
                v.elements.push_back(child);
                Skip();
                if (i < s.size() && s[i] == L',') { i++; continue; }
                if (i < s.size() && s[i] == L']') { i++; return true; }
                return false;
            }
        }
        if (c == L'"') {
            v.type = Value::Type::String;
            return ParseString(v.text);
        }
        if (Literal(L"true"))  { v.type = Value::Type::Bool; v.boolean = true;  return true; }
        if (Literal(L"false")) { v.type = Value::Type::Bool; v.boolean = false; return true; }
        if (Literal(L"null"))  { v.type = Value::Type::Null; return true; }

        // 数字
        size_t start = i;
        if (i < s.size() && (s[i] == L'-' || s[i] == L'+')) i++;
        bool any = false;
        while (i < s.size() && ((s[i] >= L'0' && s[i] <= L'9') || s[i] == L'.' ||
                                s[i] == L'e' || s[i] == L'E' || s[i] == L'-' || s[i] == L'+')) {
            i++;
            any = true;
        }
        if (!any) return false;
        v.type = Value::Type::Number;
        v.number = _wtof(s.substr(start, i - start).c_str());
        return true;
    }
};

} // namespace

bool Parse(const std::wstring& textIn, Value& out) {
    Parser p(textIn);
    out = Value();
    if (!p.ParseValue(out)) return false;
    p.Skip();
    return true;
}

std::wstring Escape(const std::wstring& s) {
    std::wstring out;
    out.reserve(s.size() + 8);
    for (wchar_t c : s) {
        switch (c) {
            case L'"':  out += L"\\\""; break;
            case L'\\': out += L"\\\\"; break;
            case L'\b': out += L"\\b";  break;
            case L'\f': out += L"\\f";  break;
            case L'\n': out += L"\\n";  break;
            case L'\r': out += L"\\r";  break;
            case L'\t': out += L"\\t";  break;
            default:
                if (c < 0x20) {
                    static const wchar_t* kHex = L"0123456789abcdef";
                    unsigned v = static_cast<unsigned>(c);
                    out += L"\\u00";
                    out.push_back(kHex[(v >> 4) & 0xF]);
                    out.push_back(kHex[v & 0xF]);
                } else {
                    out.push_back(c);
                }
        }
    }
    return out;
}

void Builder::Comma() {
    if (!body_.empty()) body_ += L",";
}

void Builder::AddString(const std::wstring& key, const std::wstring& value) {
    Comma();
    body_ += L"\"" + Escape(key) + L"\":\"" + Escape(value) + L"\"";
}

void Builder::AddBool(const std::wstring& key, bool value) {
    Comma();
    body_ += L"\"" + Escape(key) + L"\":" + (value ? L"true" : L"false");
}

void Builder::AddStringArray(const std::wstring& key, const std::vector<std::wstring>& values) {
    Comma();
    body_ += L"\"" + Escape(key) + L"\":[";
    for (size_t k = 0; k < values.size(); k++) {
        if (k) body_ += L",";
        body_ += L"\"" + Escape(values[k]) + L"\"";
    }
    body_ += L"]";
}

std::wstring Builder::Build() const {
    return L"{" + body_ + L"}";
}

} // namespace mjson
