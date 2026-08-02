# Windows 10 构建与使用指南

从源码到可用程序，一共三样东西：

| 组成 | 来源 | 是否需要编译 |
|---|---|---|
| 后端 `modporter.jar` | `src/` + `build.gradle` | **要**，用 Gradle 打包（含依赖的 fat jar） |
| 前端 `ModPorter.exe` | `frontend/ModPorter.Win32/` | **要**，用 C++ 编译器（或走 WinUI 版，用 .NET） |
| 映射数据 `mappings/` | 仓库里的 JSON | **不用**，直接复制 |

---

## 一、装软件

Windows 10 自带 `winget`，以下命令在 **PowerShell** 或 **CMD** 里执行即可。

### 1. 后端必需：JDK 17 或更高

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

装完**关掉并重开**终端，验证：`java -version`（显示 21.x 即可；17/21/23 都行）。

### 2. 后端必需：Gradle

```powershell
winget install Gradle.Gradle
```

验证：`gradle -v`。

### 3. 前端：C++ 编译器（Win32 版），三选一

| 方案 | 命令 / 下载 | 体积 | 适合 |
|---|---|---|---|
| **w64devkit**（最省事，推荐） | 去 GitHub `skeeto/w64devkit` 下载 `w64devkit-x.y.z.zip`，解压即用 | ~80 MB | 只想编出 exe，不想装 IDE |
| **VS Build Tools 2022** | `winget install Microsoft.VisualStudio.2022.BuildTools`（安装时勾选 **使用 C++ 的桌面开发**） | ~4 GB | 想要微软官方工具链、更好的调试 |
| **MSYS2** | `winget install MSYS2.MSYS2`，再在 MSYS2 里 `pacman -S mingw-w64-ucrt-x86_64-gcc` | ~1 GB | 已经在用 MSYS2 生态 |

> 只想要 WinUI 3 版前端的话，这一步换成：
> `winget install Microsoft.VisualStudio.2022.Community`（勾选 **.NET 桌面开发** + **Windows 应用 SDK C# 模板**），
> 或只装 `winget install Microsoft.DotNet.SDK.8`。

### 4. 可选但好用

```powershell
winget install Git.Git                       # 版本管理
winget install Microsoft.VisualStudioCode    # 轻量编辑器（看/改 JSON 映射表很方便）
winget install JetBrains.IntelliJIDEA.Community   # 改后端 Java 代码最舒服，自带 Gradle
```

---

## 二、构建后端 jar

```powershell
cd "C:\你的路径\01 - Project"
gradle jar
```

- 首次运行会联网下载 JavaParser / Gson / picocli，等 1~3 分钟
- 产物：**`build\libs\mc-mod-porter-0.1.0.jar`**（已包含全部依赖，可直接 `java -jar` 运行）

验证后端能跑：

```powershell
java -jar build\libs\mc-mod-porter-0.1.0.jar versions --mappings mappings
```

应列出 20 个版本（1.12 ~ 1.21.1）。

---

## 三、构建前端 exe

### 方案 A：w64devkit（最省事）

1. 解压 w64devkit，双击里面的 `w64devkit.exe`（会打开一个终端）
2. 在这个终端里：

```sh
cd "/c/你的路径/01 - Project/frontend/ModPorter.Win32"
./build.bat mingw
```

> w64devkit 终端里路径用 `/c/...` 形式。若 `build.bat` 不能直接跑，就用里面的 g++ 命令行（README 里有完整命令）。

### 方案 B：VS Build Tools

1. 开始菜单搜 **"x64 Native Tools Command Prompt for VS 2022"**，用它打开终端（**不要用普通 CMD**，否则找不到 `cl`）
2. 执行：

```bat
cd /d "C:\你的路径\01 - Project\frontend\ModPorter.Win32"
build.bat msvc
```

### 方案 C：CMake（两种编译器都支持）

```powershell
cd "C:\你的路径\01 - Project\frontend\ModPorter.Win32"
cmake -B build
cmake --build build --config Release
```

产物：**`ModPorter.exe`**（单文件，已静态链接运行库，拷到别的电脑也能跑）。

---

## 四、组装成可用程序

新建一个文件夹，比如 `D:\ModPorter\`，放三样东西：

```
D:\ModPorter\
├── ModPorter.exe                     ← 第三步的产物
├── modporter.jar                     ← 第二步的产物，重命名过来
└── mappings\                         ← 直接从项目里复制整个文件夹
    ├── java\
    └── versions\forge\...
```

双击 `ModPorter.exe` → 进「设置」页：

| 填什么 | 填成 |
|---|---|
| Java 可执行文件 | 留空（用 PATH 里的 java）或 `C:\Program Files\Eclipse Adoptium\jdk-21...\bin\java.exe` |
| 后端 modporter.jar | `D:\ModPorter\modporter.jar` |
| 映射数据目录 | `D:\ModPorter\mappings` |

点 **保存** → **测试后端连接**，显示"连接成功：1 个加载器 / 20 个版本 / 4 个动作"就通了。

之后到「版本转换」页：选版本 → 选输入/输出目录 → 开始转换。

---

## 五、常见问题

**`gradle` 或 `java` 提示不是内部命令**
→ 装完没重开终端。关掉所有终端窗口重新打开。

**`gradle jar` 报 "Unsupported class file major version" 或 Java 版本错误**
→ Gradle 版本太老配不上新 JDK。`winget upgrade Gradle.Gradle` 升到 8.x。

**终端输出中文乱码（形如 `閿欒: 鎵句笉鍒扮鍙`）**

这**不是**文件编码问题，也与项目在 Mac 上编写无关——仓库里所有文件都是 UTF-8 无 BOM（跨平台通用标准），
且 javac / MSVC 都已被显式告知按 UTF-8 读源码。乱码发生在 Windows 终端这一层：
程序按 UTF-8 输出，而 cmd / PowerShell 默认按系统代码页（简体中文为 **936 / GBK**）解码，
同一串字节编解码不一致就成了乱码。**不影响构建结果和程序功能**，只是显示问题。

四种解法，按推荐顺序：

1. **临时切到 UTF-8**（当前终端窗口有效，最简单）

   ```powershell
   chcp 65001
   ```

   PowerShell 里若仍有个别乱码，再加一句：

   ```powershell
   [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
   ```

2. **让工具直接说英文**（不改编码，适合看编译报错）

   ```powershell
   gradle jar -Duser.language=en -Duser.country=US
   ```

3. **用 `modporter.bat` 跑 CLI**（已内置 `chcp 65001` 与 JVM 编码参数，免配置）

   ```powershell
   .\modporter.bat versions
   ```

4. **系统级永久启用 UTF-8**（一劳永逸，但会影响其它老程序，酌情使用）
   设置 → 时间和语言 → 语言和区域 → 管理语言设置 → 更改系统区域设置 →
   勾选「Beta: 使用 Unicode UTF-8 提供全球语言支持」→ 重启

**永久生效**（PowerShell 用户推荐）：把下面两行写进配置文件，重开终端即可。

```powershell
if (!(Test-Path $PROFILE)) { New-Item -ItemType File -Path $PROFILE -Force }
notepad $PROFILE
```

```powershell
# 加入 $PROFILE
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8   # 解码外部程序（java/gradle/gcc）的输出
$OutputEncoding = [System.Text.Encoding]::UTF8             # PowerShell 自身写入管道的编码
```

> **注意：用 Windows Terminal 并不能解决本问题。** 它只负责渲染，编码协商仍发生在
> 控制台 API 层（`chcp` 代码页），拿到的已经是解错的字符。Windows Terminal 改善的是
> 中文字形显示——若设对编码后看到的是方块 □□□ 而非乱码，那才是字体问题，
> 换成 Microsoft YaHei Mono / Sarasa Term SC 等含中文字形的等宽字体即可。
>
> 图形前端不受此问题影响：它启动 Java 时已强制 `-Dstdout.encoding=UTF-8` 并按 UTF-8 解析，
> 与终端代码页无关。

**路径里有空格（`01 - Project`）导致命令出错**
→ 路径一定要用英文双引号包起来：`cd "C:\...\01 - Project"`。

**`cl` 不是内部命令**
→ 用错终端了。必须用开始菜单里的 **"x64 Native Tools Command Prompt for VS 2022"**。

**编出来的 exe 界面中文乱码**
→ 编译时漏了 `/utf-8`（MSVC）。用仓库里的 `build.bat` 或 CMakeLists 就已经带上了，别手敲 `cl` 命令。

**双击 exe 被 SmartScreen 拦截（"Windows 已保护你的电脑"）**
→ 自己编译的程序没有代码签名，属正常。点「更多信息」→「仍要运行」。

**前端点"测试后端连接"失败**
→ 先在终端手动跑一遍第二步末尾那条 `java -jar ... versions` 命令，看真实报错。多半是 jar 路径填错，或 mappings 目录没复制。

---

## 六、最省事的组合（如果你只想快点跑起来）

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Gradle.Gradle
# 然后手动下载解压 w64devkit
```

总共约 500 MB，不装 IDE，两条 build 命令出成品。
