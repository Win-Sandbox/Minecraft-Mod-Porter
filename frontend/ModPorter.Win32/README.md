# ModPorter — Win32 原生前端

面向**低版本 Windows** 的前端，功能与 `frontend/ModPorter.WinUI/`（WinUI 3 版）完全一致。

- 纯 Win32 API + C++11，**不依赖 .NET / WinRT / Windows App SDK / MSVC 运行库**（静态链接 CRT）
- 单个 `ModPorter.exe`，复制即用；Windows XP SP3 ~ Windows 11 通用
- 只用 XP 时代就存在的 API：`SHBrowseForFolder`、`GetOpenFileName`、通用控件 6.0（Tab / ListView / ProgressBar / ComboBox）
- 与 WinUI 版**共用同一份配置** `%LOCALAPPDATA%\ModPorter\settings.json`，两个前端可随意换用

## 构建

在 Windows 上任选一种（macOS/Linux 无法构建 Win32 GUI 程序）：

```bat
:: 方式一：批处理（自动探测 MSVC 或 MinGW）
build.bat

:: 方式二：CMake
cmake -B build -A Win32     :: 或 -A x64
cmake --build build --config Release
```

- **MSVC**：需 Visual Studio Build Tools，在「Native Tools 命令提示符」中运行；`/MT` 静态链接 CRT
- **MinGW-w64**：`-municode -mwindows -static`，同样无运行库依赖
- 若要兼容 Windows XP：MSVC 需选用 `v141_xp` 工具集，或直接用 MinGW-w64 构建

## 运行前配置

首次启动到「设置」页填写：

| 项 | 说明 |
|---|---|
| Java 可执行文件 | 留空用 PATH 中的 `java`；后端需 Java 17+ |
| 后端 modporter.jar | 后端 `gradle jar` 产物路径 |
| 映射数据目录 | 版本数据库 `mappings/`，留空则用后端默认 |

点「测试后端连接」验证，成功后会显示加载器 / 版本 / 动作数量。

## 四个页面（与 WinUI 版一一对应）

| 页面 | 内容 |
|---|---|
| **版本转换** | 加载器 / 源版本 / 目标版本三个下拉框由后端 `capabilities` **自动发现**（数据库加版本后点「刷新版本列表」即出现）；输入 / 输出目录选择、dry-run 开关、实时进度条 + 逐文件日志、结果摘要 |
| **转换报告** | 读取 `modporter-report.json`：四张统计卡片 + 级别过滤（TODO / ERROR / WARN / INFO）+ 明细列表（级别 / 位置 / 分类 / 说明）+ 打开输出目录 / Markdown 报告 / **TODO 清单** |
| **扩展功能** | 由 `capabilities.actions` **动态生成**：左侧动作列表，右侧描述 + 按参数类型动态生成的表单（path 带浏览按钮、bool 复选框、enum 下拉框）；后端未实现的动作标注「待后端实现」并禁用，后端把 `available` 置 true 后自动可用，**前端无需重新编译** |
| **设置** | Java / jar / 映射目录 + 测试连接 |

## 与后端的通信

与 WinUI 版使用完全相同的三条 JSON 协议通道：

1. `modporter capabilities` — 能力清单（同步调用，stderr 单独线程排空避免管道死锁）
2. `modporter run <actionId> --params <文件>` — NDJSON 流式进度，工作线程逐行读取后
   `PostMessage` 回 UI 线程（`WM_APP_PROGRESS` / `WM_APP_DONE`）
3. `modporter-report.json` / `MODPORTER-TODOS.md` — 结果产物

> 参数经**临时 JSON 文件**传入（后端 `--params` 同时接受 JSON 字符串与文件路径），
> 从而绕开 Windows 命令行对内嵌引号的转义问题；临时文件在动作结束后自动删除。

## 源码结构

```
app.h              共享声明（设置 / 能力清单 / 页面基类 / 全局会话状态）
json.h  json.cpp   极简 JSON DOM（保序）+ 转义 + 对象构造器，无第三方依赖
util.cpp           UTF-8↔UTF-16、目录/文件选择、控件与布局助手
settings.cpp       settings.json 读写（与 WinUI 版同路径同结构）
backend.cpp        进程启动、管道读取、capabilities 解析、NDJSON 流式动作
page_convert.cpp   版本转换页
page_report.cpp    转换报告页
page_extensions.cpp 扩展功能页（动态表单）
page_settings.cpp  设置页
main.cpp           WinMain、Tab 框架、消息循环
ModPorter.rc / app.manifest / resource.h   视觉样式、DPI、版本信息
```

## 已知限制

- 未内置应用图标资源（用系统默认图标）；如需图标，在 `ModPorter.rc` 加
  `IDI_APPICON ICON "app.ico"` 并放入 `app.ico` 即可
- 系统 DPI 感知（非 Per-Monitor V2）：跨不同 DPI 显示器拖动时由系统缩放，可能略糊
- 扩展功能页最多支持 64 个参数的浏览按钮 id 范围（远超实际需要）
