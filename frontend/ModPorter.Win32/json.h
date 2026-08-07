// 极简 JSON DOM（保序），仅供前端解析后端输出与设置文件使用。
#pragma once
#include <string>
#include <utility>
#include <vector>

namespace mjson {

class Value;
using Members = std::vector<std::pair<std::wstring, Value>>;
using Elements = std::vector<Value>;

class Value {
public:
    enum class Type { Null, Bool, Number, String, Array, Object };

    Type type = Type::Null;
    bool boolean = false;
    double number = 0;
    std::wstring text;
    Elements elements;   // Array
    Members members;     // Object（保持原始顺序）

    bool IsNull()  const { return type == Type::Null; }
    bool IsArray() const { return type == Type::Array; }
    bool IsObject()const { return type == Type::Object; }

    const Value* Find(const std::wstring& key) const;
    std::wstring Str(const std::wstring& key, const std::wstring& def = L"") const;
    double       Num(const std::wstring& key, double def = 0) const;
    long long    Int(const std::wstring& key, long long def = 0) const;
    bool         Bool(const std::wstring& key, bool def = false) const;
    const Value* Arr(const std::wstring& key) const;
};

// 解析（宽字符）。失败返回 false。
bool Parse(const std::wstring& textIn, Value& out);

// 转义字符串内容（不含外层引号）。
std::wstring Escape(const std::wstring& s);

// 简易对象构造器，用于拼装 --params。
class Builder {
public:
    void AddString(const std::wstring& key, const std::wstring& value);
    void AddBool(const std::wstring& key, bool value);
    void AddStringArray(const std::wstring& key, const std::vector<std::wstring>& values);
    std::wstring Build() const;   // 返回完整 {...}
private:
    std::wstring body_;
    void Comma();
};

} // namespace mjson
