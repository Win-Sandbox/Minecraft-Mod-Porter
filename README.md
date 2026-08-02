# mc-mod-porter

Minecraft 模组**源代码**跨版本转换器。当前支持 Forge 1.12.2 ↔ 1.19.2（引擎本身与版本无关，双向可用）。

- 纯数据驱动：所有对照表都在 `mappings/` 下的 JSON 文件里，代码中不含任何版本知识
- **IR/pivot 架构**：每个版本只维护一份「该版本 ↔ 规范 IR」映射，任意两版本间的转换都是 `源版本 → IR → 目标版本` **一步完成**，不存在链式二次转换。支持 N 个版本只需要 N 份数据
- 无法自动转换的代码保留原样，插入 `// TODO [modporter] ...` 注释，并汇总到转换报告
- 纯 CLI；核心是库 API（`io.modporter.core.PortEngine`），后续 UI 直接调用同一接口

## 构建与使用

> **Windows 用户请看 [BUILD-WINDOWS.md](BUILD-WINDOWS.md)**：从装工具到组装出可用程序的完整步骤。

```bash
gradle jar   # 产物 build/libs/mc-mod-porter-0.1.0.jar（fat jar，已含全部依赖）

# 列出支持的版本
modporter versions

# 1.12.2 -> 1.19.2
modporter port \
  --from 1.12.2 --to 1.19.2 \
  --input  /path/to/old-mod-project \
  --output /path/to/new-mod-project \
  [--loader forge] [--mappings ./mappings] [--dry-run]
```

映射数据目录默认取 `./mappings`，也可用环境变量 `MODPORTER_MAPPINGS` 或 `--mappings` 指定。

转换完成后输出目录中会生成：

- `MODPORTER-REPORT.md` — 人类可读报告（错误 / 待人工处理 / 警告 / 自动改写明细）
- `modporter-report.json` — 结构化报告，供 UI / 脚本消费
- `MODPORTER-TODOS.md` — TODO 清单（仅当转换后工程中存在注释 TODO/FIXME 时生成）：扫描全部文本产物，
  按文件汇总每一处 TODO 的行号与内容，既包括转换器插入的 `TODO [modporter]`，也包括模组作者源码中原有的 TODO

## 转换范围

| 内容 | 处理方式 |
|---|---|
| Java 源码 | AST 级改写：导入、类型引用、方法/字段重命名（含字段↔方法形态变化）、被覆写方法声明重命名（`@Override` 的 `readFromNBT` → `load` 等，同步修正 this/super 调用）、`@Mod` 注解风格、生命周期注解、惯用法（如 `new TextComponentString(x)` ↔ `Component.literal(x)`） |
| 元数据 | `mcmod.info` ↔ `mods.toml`（解析为 IR 后按目标模板生成） |
| 语言文件 | `.lang` ↔ `.json`，并按键模式迁移本地化键（`tile.modid.x.name` → `block.modid.x`） |
| blockstates / models | `"normal"` 变体 ↔ `""`、纹理路径前缀（`blocks/` ↔ `block/`）、forge_marker 标记 TODO |
| pack.mcmeta | `pack_format` 按目标版本更新 |
| build.gradle | 按目标版本模板重新生成（原脚本自定义逻辑记入报告，需人工搬运） |
| 第三方依赖库 / 依赖模组 | **不处理**（按设计） |

## 前端与前后端协议

两个功能完全一致的 Windows 原生前端，按目标系统任选（都需在 Windows 上构建）：

| 前端 | 技术 | 适用 | 说明 |
|---|---|---|---|
| [`frontend/ModPorter.WinUI/`](frontend/ModPorter.WinUI) | WinUI 3 / C# | Windows 10 1809+ | Fluent 设计：Mica 背景 + NavigationView，需 Windows App SDK |
| [`frontend/ModPorter.Win32/`](frontend/ModPorter.Win32) | 纯 Win32 API / C++11 | **Windows XP ~ 11** | 无 .NET / WinRT / VC 运行库依赖（静态链接 CRT），单 exe 复制即用 |

两者页面一一对应：版本转换（版本列表从后端自动发现）、转换报告（读 modporter-report.json + TODO 清单）、扩展功能（由能力清单动态渲染）、设置；并**共用同一份配置** `%LOCALAPPDATA%\ModPorter\settings.json`，可随时互换使用。

前后端之间只有三条**版本化 JSON 协议**通道，后端新增功能前端零改动：

1. `modporter capabilities` — 能力清单：`loaders`（各加载器可用版本，前端版本下拉框数据源）+ `actions`（动作列表，含参数描述；`available:false` 表示已规划未实现，前端渲染为禁用卡片）。`mappings/actions.json` 可追加/覆盖动作，后端功能可随数据包分发。当前预留入口：**跨加载器转换（Fabric/Quilt/NeoForge）、批量转换、映射数据包导入**。
2. `modporter run <actionId> --params <json>` — 通用动作入口，NDJSON 逐行进度（`begin/file/fileDone/message/result/fatal`），内置 `port` 与未来动作走同一通道。
3. 报告文件 `modporter-report.json` — 结构化结果。

## UI 集成接口

未来 UI 只需要依赖 `io.modporter.core`：

```java
PortEngine engine = new DefaultPortEngine(new MappingRepository(mappingsDir));
engine.supportedVersions();                       // 版本下拉框数据
PortResult result = engine.port(request, listener); // listener 提供逐文件进度回调
result.report().entries();                        // 结构化报告条目（严重级/文件/行号/分类/消息）
```

## 映射数据格式（如何新增一个版本）

在 `mappings/versions/<loader>/<mcVersion>/` 下新建一套文件即可，**不需要改任何代码**。
新版本加入后，它与所有已有版本之间即自动支持双向转换。

```
mappings/versions/forge/1.19.2/
├── version.json    # 版本特性：元数据格式、lang 格式与键模式、纹理前缀、@Mod 风格、生命周期风格、pack_format、Forge 版本等
├── classes.json    # IR 类 id -> 本版本 FQCN（值可为字符串，或 {"name": fqcn, "note": "迁入本版本时的注意事项"}）
├── members.json    # IR 类 id -> { IR 成员名 -> {"name": 本版本名, "kind": "method|field", "note": ...} }
├── removed.json    # 本版本存在、但没有 IR 对应的类/成员 -> {"concept": 概念id, "message": 通用说明}
├── idioms.json     # forms: 惯用法在本版本的形态（constructor / staticCall，可带 arity 限定参数个数，
│                   #        用于区分同一构造器的不同参数形态，如 ResourceLocation 单参/双参）
│                   # guidance: 概念id -> 「迁入本版本」时的做法说明（removed 概念在目标侧的指导文字）
│                   # supported: 在本版本仍原样可用的概念（源版本标记 removed 的符号若属于它们则原样保留，不打 TODO）
└── templates/      # 本版本的 mods.toml / mcmod.info / build.gradle 模板（${modid} 等占位符）
```

### Java 平台映射（mappings/java/）

MC 版本跨度伴随 Java 8 → 16 → 17 → 21 的平台变化，独立建档于 `mappings/java/`（与 MC 版本解耦，
经各 version.json 的 `javaVersion` 关联；互通的 Java 版本用文件内 `"aliases": [16]` 一个文件覆盖）：

- `features.json` — 语法特性 id → 引入的 Java 版本。**降级**时引擎按此检测源代码里的高版本语法
  （var/record/switch 表达式/instanceof 模式/sealed → TODO；文本块 → 自动降级为普通字符串）
- `<版本>.json` — `illegalIdentifiers`（如 Java 9+ 非法的 `_`，引擎自动改名）、
  `restrictedTypeNames`（var/record/sealed/permits/yield 等不能再作类型名，打 TODO）、
  `removedClasses`（JDK 移除的类库：JAXB/EE javax.*、Nashorn、sun.misc.BASE64* 等 → 命中导入时打 TODO + 迁移指导）、
  `encapsulatedPackages`（被模块系统封锁的 sun.misc 等内部包 → 指导）

**方法级检测**（导入检查覆盖不到的用法，复用成员映射的「声明类型 + scope」启发式）：

- `removedMethods` — `"Thread#stop"` 形式的方法规格 → 指导。接收者类型由本文件内的变量/字段/参数声明类型
  与静态调用 scope 推断：**判定得出类型时按类型精确匹配**（`StringUtils.isBlank(x)` 不会被当成 `String#isBlank`），
  判定不出时只报告标了 `"anyReceiver": true` 的条目，避免误伤同名自定义方法。
  升级方向覆盖 `Thread.stop/suspend/resume`（JDK 20 起抛 UOE）、`System.setSecurityManager`、
  `runFinalizersOnExit`、`Class.newInstance` 等；**降级方向**（8.json）覆盖 40 个 Java 9~16 新增 API
  （`List.of`、`String.isBlank`、`Stream.toList`、`Files.readString`、`Optional.isEmpty` …），每条都给出 Java 8 等价写法。
- `argumentIssues` — 方法还在但某些字符串实参失效，如 `getEngineByName("nashorn")` 在 Java 15+ 返回 null。
- `reflectiveLookups` — `Class.forName("…")` / `ClassLoader.loadClass("…")` 的字符串类名会回查
  `removedClasses`/`encapsulatedPackages`，捕获反射方式使用已移除类的场景。

构建工具链：各 version.json 的 `gradleVersion` 声明该版本 ForgeGradle 兼容的 Gradle；
转换时 `gradle-wrapper.properties` 的 distributionUrl 会自动改写到该版本（wrapper jar/脚本保留并提示刷新）。

### 版本复用：别名与覆盖层

一套映射数据可以服务多个互通版本，两个机制都声明在 version.json 里：

- **`"aliases"`（完全互通）**：`"aliases": {"1.19.1": {"forgeVersion": "42.0.9", "loaderVersionRange": "[42,)"}}` ——
  别名版本直接复用本套映射，只按别名覆盖 forgeVersion/packFormat 等元信息（影响 build.gradle / mods.toml 生成）。
- **`"basedOn"`（细微差别）**：`"basedOn": "1.19.4"` —— 本目录只写与基版本的**差异条目**，加载时先载入基版本再叠加：
  条目按 key 覆盖；各 json 顶层 `"!remove"` 删除基版本条目（classes: IR id 数组；members: `"classIr#memberIr"` 或整个 `"classIr"`；
  removed: `{"classes": [...], "members": [...]}`；idioms 另有 `"!removeSupported"`）；templates/ 先查本目录再回退基版本。
  两个机制可组合（别名可以挂在覆盖层目录上）。

### 当前版本矩阵

| 数据集 | 形态 | 同时覆盖（别名） |
|---|---|---|
| 1.12.2 | 全量 | 1.12、1.12.1 |
| 1.14.4 | 覆盖层（基于 1.15.2） | — |
| 1.15.2 | 全量 | — |
| 1.16.5 | 全量 | 1.16.4 |
| 1.17.1 | 全量 | — |
| 1.18.1 | 覆盖层（基于 1.18.2） | 1.18 |
| 1.18.2 | 全量 | — |
| 1.19.1 | 覆盖层（基于 1.19.2） | 1.19 |
| 1.19.2 | 全量 | — |
| 1.19.3 | 覆盖层（基于 1.19.4） | — |
| 1.19.4 | 全量 | — |
| 1.20.1 | 全量 | 1.20 |
| 1.21.1 | 覆盖层（基于 1.20.1） | 1.21 |

共 20 个可选版本，任意两版本一步直达（380 个方向）。

### IR 约定

- **IR 类 id**：稳定的规范标识，如 `mc.item.Item`、`forge.SubscribeEvent`，与任何具体版本解耦（命名上参考现代版本，但只是习惯）
- **IR 成员名**：类内成员的规范名（如 `putInt`）。某版本 `members.json` 未列出的成员，约定为「该版本成员名 = IR 名、形态不变」，因此只需要登记**有差异**的成员
- **概念 id**（`removed.json` / `guidance`）：跨版本语义迁移的锚点。源版本用它声明「这个符号属于什么概念」，目标版本用 `guidance` 声明「这个概念在我这里怎么做」，两侧各自独立维护，互不引用具体版本
- **惯用法 id**（`idioms.json`）：同一语义在不同版本的不同写法（构造器 vs 静态工厂等），引擎识别源形态、生成目标形态

### 语义映射示例

- 生命周期：`FMLPreInitializationEvent` 与 `FMLInitializationEvent` 都映射到 IR `forge.lifecycle.commonSetup` / `forge.lifecycle.init`，在 1.19 侧都落到 `FMLCommonSetupEvent` 并附带 note 提醒可能需要合并方法
- `@Mod.EventHandler` 方法会被改为 `@SubscribeEvent` 并插入 TODO，说明需要注册到 Mod 事件总线（指导文字来自目标版本 `guidance["lifecycle.eventHandler"]`）
- `GameRegistry` / `setRegistryName` / `@SidedProxy` / 代码注册合成表等 1.13+ 彻底移除的机制：保留原码 + TODO + 目标版本给出的迁移指导

## 映射数据的依据

1.12.2 → 1.19.2 的映射条目按以下资料逐项核对：

- [williewillus 的 1.13/1.14 Update Primer](https://gist.github.com/williewillus/353c872bcf1a6ace9921189f6100d09a)（Forge 官方文档指定的 1.12→1.13/1.14 迁移参考），覆盖扁平化、注册、生命周期、代理、lang/模型、配方、网络、命令、TileEntity、Capability、世界生成等全部章节
- [Forge 官方 Porting 文档](https://docs.minecraftforge.net/en/1.14.x/legacy/porting1214/)
- ChampionAsh5357 的 1.18.2 → 1.19 迁移 primer（RegisterEvent、Component 工厂方法、sendSystemMessage、事件包移动、setRegistryName 移除等 1.19 专属变化）

已知名字冲突（如 1.12 的 `getDisplayName` 同时存在于 Entity 与 ItemStack、`getPos` 同时存在于事件与 TileEntity）通过「歧义候选」数据条目守护：引擎遇到时不猜测，而是打 TODO 请人工按接收者类型确认。

## 已知限制（v0.1）

- 成员重命名基于名字启发式（不做完整类型推导）：静态调用会校验 scope 类名（不会误改 `String.format`），实例调用无法校验接收者类型，请通过报告里的「自动改写明细」审查；歧义映射一律不改、打 TODO
- Java 输出使用 JavaParser 的标准格式化打印，原有代码格式（空行、对齐）不保留；普通注释保留
- 注册系统（GameRegistry → DeferredRegister）、网络（SimpleNetworkWrapper → SimpleChannel）、Capability、GUI/Container 等结构性重构不做自动改写，统一打 TODO 并附迁移指导
- metadata（物品子类型）、方块属性构建器等扁平化语义变化需人工拆分
- `mods.toml` 解析为极简实现，只覆盖常见字段
- 降级方向（如 1.19 → 1.12）架构上已支持、数据双向可用，但未经重点验证

## 工程结构

```
src/main/java/io/modporter/
├── core/       # 对外 API：PortEngine / PortRequest / PortResult / Report / ProgressListener
├── mappings/   # 映射数据加载（MappingRepository）与解析（MappingResolver：源→IR→目标）
├── engine/     # DefaultPortEngine：文件扫描、Pass 分派、报告输出；ModMeta（元数据 IR）
├── passes/     # JavaSourcePass / MetadataPass / LangPass / AssetJsonPass / BuildGradlePass
└── cli/        # picocli 命令行入口
```
