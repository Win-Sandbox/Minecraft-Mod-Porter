# 资源格式更新（2026-10-04）

本轮对应原检查第4项：新版 pack.mcmeta 与客户端物品模型定义。
**全程不编译、不运行 Java/Gradle，不执行这些 Java 回归测试。**

## 1. pack.mcmeta

- `PackFormat` 用整数主/次版本表示；映射 schema 为 `resourcePackFormat: [major,minor]`、`dataPackFormat: [major,minor]`、`packMetadataStyle: legacy|range`，兼容原 `packFormat`。
- `VersionInfo.copy()`、MappingRepository alias/overlay、Python校验器都支持新字段和非法值诊断。
- `PackMetadataPass` 根据包根目录下 assets/data 选择格式。新版写精确 min_format/max_format，删除旧pack_format/supported_formats；旧版反向写pack_format、删除新字段。声明范围收窄到目标单版本，报告提示，不虚构多版本兼容。
- overlays 升到新版时只保留源版本激活的入口，改为目标单版本，保留全部目录文件；重复迁移幂等。带overlay降级需要扁平化资源，本轮不自动执行，原元数据保留+TODO。
- 同目录同时含 assets/data 且格式不同、未知包类型、无格式数据、非法JSON/范围：原文件保留并TODO。混合包并未被“假装修好”。

## 2. 物品模型定义

- `itemModelDefinitions` 能力驱动，无引擎版本号硬编码。
- false→true：为同资源根下 models/item/*.json 生成 items/*.json，指向原模型；工程前缀/嵌套路径/命名空间保持，不覆盖已有文件。
- true→false：仅支持顶层单model、内层type+model的纯minecraft:model，引用须本地JSON对象；必要时生成旧 models/item 路径的parent桥接。
- 不删除items目录；复杂range_dispatch/select/condition/tints、自定义模型与缺失/循环引用均保留TODO。旧overrides不能仅靠基础定义保留全部行为，本轮明确提示人工转换。
- 非JSON文件不会生成定义；防范路径穿越、重复输出、重复.json后缀；无命名空间模型引用按minecraft解析。
- 父链需要本地可确认，只有minecraft:item/generated、minecraft:item/handheld、minecraft:builtin/generated允许作为非本地终点；不猜未知外部模型。
- 对Java颜色注册/模型tintindex仅做提醒，不推断运行期颜色。扫描models/item可能为辅助模型生成冗余定义，不等于已验证物品注册关系。
- DefaultPortEngine写出前执行跨文件Pass，dry-run也分析；重复或越界输出在任何文件写出前失败，避免悄悄取最后一份覆盖。
- 资源分派按assets/<ns>/<dir>完整路径段识别，防止items/lang或items/models子路径误进入旧资源Pass。

## 3. 数据来源

55个版本目录、全部别名（81个可选版本）显式设置能力。主代理独立核对43个不同MC版本的官方manifest/client ZIP目录及version.json，HTTP Range读取而非下载/执行整个客户端。子代理的数据亦归档供比对。

- 1.21.9/10：资源69.0，数据88.0。
- 1.21.11：资源75.0，数据94.1。
- 26.1/1.1/1.2：资源84.0，数据101.1。
- 26.2：资源88.0，数据107.1。
- 1.12.x客户端没有version.json，资源3沿用原数据，dataPackFormat显式null，不采纳未经一手证实的数据包格式3。
- 新字段支持的schema与官方公告链接见 `mappings/sources/resource-formats.md`。引擎不内置这些版本表。

## 4. 检查与剩余工作

- 37个Java文件经tree-sitter静态语法解析：0错误；不等于Java类型检查或编译通过。
- `python3 -B mappings/tools/validate_mappings.py`：ERROR 0 / WARN 0。
- 81可选版本的新字段、schema类型、能力边界与alias/overlay有效值由Python静态断言核对。
- 新增3个无JUnit的main回归入口：PackFormatMappingTest、PackMetadataPassTest、ItemModelDefinitionPassMainTest（多组用例）。允许编译后可执行 `gradle resourceRegression`；**该命令会编译，本轮未执行**。
- 项目包含上轮5个安全回归入口；本轮未修改它们，亦未执行。
- 不保证转换产物在Minecraft中可加载。资源内容schema、复杂模型双向语义、混合包策略、overlay降级扁平化、注册物品对应关系仍需人工/后续真实工程验证。

## 5. 审核与备份

派2个GLM子会话（数据/物品模型），主代理实现pack逻辑并审核。物品模型补丁曾退回修正；整合时继续修复后缀、父链、冲突等问题。子任务原始产物不等同最终版本。

共享目录 `modporter/20261004-resource-update/` 包含本轮前置备份 pre-resource-update.tar.gz、最终final.patch/manifest.json、final/文件、official-data/43份证据和采集脚本。原项目在最终检查后统一写回。
