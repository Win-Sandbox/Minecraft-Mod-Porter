# TODO

本次扩充版本映射（Fabric 1.15–26.x、NeoForge 1.20.1–26.2、Forge IR 回补、Java 25）之后尚未完成的工作。
每条都附有原因；涉及的事实来源见 `mappings/sources/*.md`。
修改映射数据后运行 `python3 mappings/tools/validate_mappings.py`，结果应保持 ERROR 0。

## 2026-10-04 会话审计优先级（先于其它条目处理）

> 本节由唯一GLM审计代理整理、主代理独立核对并校订；证据来自 `20261004-session-audit/` 17 个导出会话、5 份审查结论、`PRIMARY-CHECK.md`、`PRIMARY-STATIC-CHECK.md` 和当前挂载项目只读核对。tree-sitter 43/0 与 mappings ERROR 0/WARN 0 不能推翻下列 Java 类型/API 问题。

- [x] **P0 `VersionMappings.withInfo()` 构造实参顺序错误**：`src/main/java/io/modporter/mappings/VersionMappings.java:198–206` 要求 `idioms, guidance, supportedConcepts, annotationForms`，但 `:243–252` 传 `idioms, annotationForms, guidance, supportedConcepts`；Map/Set 类型不匹配，编译必然阻断。复现：静态阅读两个位置；完成标准：参数顺序一致并补版本别名携带 annotations 的回归。证据：EC07/BA2A/74CF/B969、PRIMARY-CHECK。
- [x] **P0 `Expression` 调用不存在的 `getNameAsString()`**：`src/main/java/io/modporter/passes/AnnotationMigrationPass.java:121–122` 遍历 `NameExpr` 却声明 `Expression`。复现：静态阅读/后续编译；完成标准：循环变量改为 `NameExpr` 或等价有该方法的类型，并保留 NameExpr 测试。证据：EC07/BA2A/74CF、PRIMARY-CHECK。
- [x] **P1 历史晚到审查静态闭环**：EC07/BA2A/B969/74CF 四份报告逐项采纳/拒绝及当前文件证据见 `shared/modporter/20261004-merge-closure/audit/MATRIX.md`；其中发现的 annotations 极端导入、removed 误报、NeoForge 1.20.1 Bus 数据缺口分别按证据修复，补 main 回归源码。**仍未编译/运行 Java**，静态闭环不是功能验收；当时审查报告的 FAIL 不能直接当作当前代码结论。
- [x] **P1 annotations 晚到 `gameBus` 稳定 IR 补丁未合入**：共享 annotations 最新交付用 `gameBus→FORGE`，当前项目 1.20.4 仍 `FORGE→FORGE`，1.20.5/1.21.1 仍有 `GAME`/`FORGE` 分裂；`PRIMARY-STATIC-CHECK.md` 模拟 1.20.4→1.21.1 仍输出 FORGE、逆向仍 GAME、1.20.5→1.20.4 冲突。完成标准：仅按最新 annotations delta 合并，保留集成 Java 补丁；全部 NeoForge 版本/别名正反向输出真实常量且 validator 能捕获坏数据。
- [x] **P1 注解 valueClass 引用闭合校验仍缺**：Python validator 与 Java `MappingRepository.mergeAnnotations` 均验证 `valueClass` 对应当前合并 classes IR；`!remove` 与继承合并保持原 schema。
- [x] **P2 旧归档清单格式修复**：`shared/modporter/20261004-next-update/final/manifest.json` 已转换为真实 JSON（algorithm、409 项路径/sha256），逐项对旧快照复核；旧 SHA 文本保留为 `manifest.sha256`。旧文本还含无法自洽的清单自身条目，JSON 按实际409个项目文件列出、不含自身。此为历史归档修复，不表示归档快照等于当前 Working。
- **P2 执行纪律**：新派生必须显式指定预算模型并核对 send/status 返回的 `model_name`；不得因 exit 15 反复派发。默认最多 1 实现 + 1 必要审查；未经用户同意不用 GPT Astra/Opus 等旗舰；本轮禁止再派生。完成标准：派发回执、会话状态和最终审计三者模型一致。

---


## 1. 需要扩展引擎或数据格式的迁移规则

这些变化都已用一手来源核实，但目前的数据格式表达不了，或者引擎还不会执行，所以现在只写成 guidance / note，或在转换时打 TODO。

### 1.1 Mixin `@At` / `method` 描述符字符串

**2026-10-04 已实现保守子集**：`MixinReferencePass` 在类型/import 改名前保存源 owner，严格确认 Sponge 注解来源，处理 `@Mixin(targets/value)` 的源目标、注入注解 method 字面量/数组、`@At` 的 INVOKE/INVOKE_ASSIGN/FIELD 完整引用；多个 target 只有映射结果一致才改。JVM 描述符严格校验；未知/removed 类、成员无记录、通配符、构造器、带 note 的签名变化、动态表达式等保持整条引用并 TODO。`remap=false` 字符串不动。`compatibilityLevel` 更新仍保留。

限制：不生成 refmap，不处理所有 MixinExtras/Accessor/Invoker/NEW 形态；未知通配符注解导入不猜来源。成员表还没有重载/参数变化 schema；新增映射仍须验证其语义。以下规则中只写在 guidance 而没有成员条目的，仍需人工迁移。

**已核实、等待落地的条目**：

| 版本跳跃 | 所在类（26.1 名） | 旧方法名 → 新方法名 |
|---|---|---|
| 1.21.11 → 26.1 | Screen | `render` → `extractRenderState` |
| 1.21.11 → 26.1 | Screen | `renderBackground` → `extractBackground` |
| 1.21.11 → 26.1 | AbstractContainerScreen | `renderBg` → `extractBackground` |
| 1.21.11 → 26.1 | AbstractContainerScreen | `renderLabels` → `extractLabels` |
| 1.21.11 → 26.1 | AbstractContainerScreen | `renderSlot` → `extractSlot` |
| 1.21.11 → 26.1 | GuiGraphics → GuiGraphicsExtractor | `renderItem` → `item` |
| 1.19.1 / 1.19.2（Fabric） | ChatHud / GuiMessageTag | `addMessage` 描述符随聊天签名变化，另有 `systemSinglePlayer` |

降级方向（26.1 → 1.21.11）就是把上表反过来。

**实现及后续**：
- [x] 复用 `classes.json` + `members.json`，通过 `ReferenceMapper` 和 `MixinDescriptors` 原子改写 owner/成员/参数返回类型。
- [x] 以精确 owner 查成员，不把外部类的成员候选错套到内部类。
- [x] Access Widener v1/v2：Yarn `named → named` 自动迁移 class/field/method，v2 transitive 规则，保留注释、空格和换行。失败保留整行。
- [x] AW `official → official` 同命名空间保守子集已接入（两侧 Fabric 且 `mappingsChannel=official`，文件 header 实际为 `official` 才逐行尝试；IR 不足时原行保留 TODO）。已补回归源码但未编译/运行；官方实例映射覆盖很低，不能宣称真实 AW 文件已自动迁移。证据见 `shared/modporter/20261005-next12/engine/{REPORT.md,aw-review/REPORT.md}`。
- [ ] AW `intermediary`、跨命名空间与跨loader：缺独立 AW runtime namespace 元数据、intermediary 映射表及目标 header 的结构化保证，整文件保持原样并 TODO；不能仅凭 `mappingsChannel` 推断这类转换。
- [ ] 扩充成员描述符/重载数据和更完整的 Mixin 注入形态后再放宽保守限制。

### 1.2 `pack.mcmeta` 的 `min_format` / `max_format`

**2026-10-04 已实现保守子集**：`PackMetadataPass` 使用 `resourcePackFormat` / `dataPackFormat` 的 `[主, 次]` 与 `packMetadataStyle` 能力。新版写精确 `min_format/max_format`，旧版写整数 `pack_format` 并删除新字段；`supported_formats` 收窄到单目标版本。只根据 pack.mcmeta 同目录 assets/data 判定类型，混合包两种格式不同或类型未知时保留并 TODO。

含 overlays 升到 range 格式时，只迁移源版本激活的入口，保留目录内容，移除不激活的入口并提示；重跑幂等。带 overlays 降级不猜目录合并，保留整个元数据并 TODO。格式更新不代表包内资源/数据schema自动兼容。

**问题**：
- 1.21.9 起资源包格式号变成「主.次」形式，`pack.mcmeta` 改为写 `min_format` / `max_format`；旧字段 `pack_format` 只在兼容旧客户端时保留。
- 资源包和数据包的格式号也不一样了，例如 26.x 的数据包格式带次版本号（101.1、107.1）。
- 旧实现只使用 `packFormat` 主版本号；现已保留旧字段同时增加精确主次格式（如 1.21.11 数据包 94.1、26.2 数据包 107.1），避免次版本丢失。

**待办**：
- [x] 新增 `resourcePackFormat` / `dataPackFormat` 严格双整数数组，保留 `packFormat`；复制、别名、overlay继承与Python校验同步。
- [x] `AssetJsonPass` 委托 `PackMetadataPass` 按数据中的目标能力决定写法（引擎不写死版本号）：
  - 1.21.9 之前：写 `pack_format`。
  - 1.21.9 及以后：写 `min_format` / `max_format`。
  - 降级时反向转换，删掉新字段。
  - 原文件里已有的 `supported_formats` / overlays 要一并处理。
- [x] 按 pack.mcmeta 同目录 `assets/` / `data/` 选对应格式；无法区分或混合且格式不同则保留 TODO。
- [ ] 混合包双格式表达、overlay降级扁平化与复杂范围兼容策略仍待实现。
- [x] 1.20.6、1.21.3、1.21.8、1.21.10 已从各自官方客户端 `version.json` 核实，详见 `mappings/sources/resource-formats.md`；不再仅假设热修复不变。

### 1.3 1.21.4 起的客户端物品模型定义 `assets/<ns>/items/*.json`

**2026-10-04 已实现基础迁移**：`ItemModelDefinitionPass` 在写出前处理完整输出列表；升级为 `models/item/*.json` 生成同前缀的 `items/*.json`，不覆盖已有文件；降级仅对纯 `minecraft:model`、本地有效且父链可确认的模型生成 parent 桥接。复杂定义原样保留并 TODO，绝不删除整个 items 目录。新旧版本能力相同则不动。

**待办**：
- [x] 升级跨过能力边界时为每个 `models/item/<name>.json` 生成同资源根 `items/<name>.json`，内容为 `{"model":{"type":"minecraft:model","model":"<ns>:item/<name>"}}`。
- [x] overrides/tints/tintindex/loader/render_type、源码颜色注册引用保留并提示；不宣称动态属性或着色自动迁移。
- [x] 降级纯 model 引用生成必要的旧模型桥接，不覆盖已有模型；复杂定义保留原文件供人工迁移。
- [x] `itemModelDefinitions` 数据驱动，按源/目标能力比较，不在引擎中硬编码1.21.4。
- [x] `range_dispatch` 与旧 overrides 的 `custom_model_data` 严格子集已接入（`ItemOverrideRules` + `ItemModelDefinitionPass`；阈值须有限非负且严格递增，模型默认 minecraft 命名空间，失败原子保留 + TODO）。
- [x] **range_dispatch 严格子集静态安全闭合（P1）**：已使用实际 fallback，升级先构造完整 generatedDef；降级对 fallback/每个 entry 核对本地文件、重复路径、合法 JSON、parent 链循环和桥接自引用，失败整条保留原文件 + TODO。已补回归源码，未编译/未运行，不能宣称运行验收通过。
- [ ] 其余 `range_dispatch/select/condition`、tint 规则和注册物品精确识别尚未实现。models/item 中辅助模型也可能生成冗余定义，需要用户核对注册对应关系。

### 1.4 需要改注解属性的规则

**2026-10-04 现状（本轮收尾）**：`AnnotationMigrationPass` 通过独立 `annotations.json` 按 IR/属性执行改名、删除与枚举值迁移；显式 import、FQCN 与 `Outer.Inner` 已处理。P0 `NameExpr`、P1 稳定 `gameBus` 与 `valueClass` 引用校验已静态修复。此轮又增加了同名本地类型/冲突 import 的保守检测、共用外层 import 检查；目标枚举宿主或常量变更时用完整 FQCN，无变化时保留原表达式。NeoForge 1.20.1 的官方 FML sources 已证实内部 Bus.FORGE/MOD，类/成员/annotation 数据已补。回归源码已补，**未编译/未运行 Java**；复杂导入的真实样例运行验证仍需做。

| 版本 | loader | 变化 |
|---|---|---|
| NeoForge 1.20.5 | neoforge | `@Mod.EventBusSubscriber` → 顶层 `@EventBusSubscriber`；`bus = Bus.FORGE` → `Bus.GAME` |
| NeoForge 1.21.6 | neoforge | `@EventBusSubscriber` 去掉 `bus` 属性（总线改由事件类型自动判定） |
| 1.20.5 起 | neoforge | 写成 `Mod.EventBusSubscriber` 这种经外层类的限定引用，不会被改写成顶层 `EventBusSubscriber`，因为类解析只处理 import 和简单名 |

**待办**：
- [x] 独立 `annotations.json` 的 `AnnotationForm/AnnotationAttributeForm` 已接入；值本身走 `valueClass` + members IR（如 `Bus.FORGE` → `Bus.GAME`），稳定 `gameBus` IR 已合入 NeoForge 版本数据；Java 正反向测试源码已补但未运行。
- [x] `Outer.Inner` 限定引用已由 `AnnotationMigrationPass` 处理；回归覆盖外层类/限定名/FQCN/自定义同名注解，尚未编译运行。

### 1.5 需要改调用接收者的规则

**现状（本轮收尾）**：成员映射支持方法/字段名改写及方法↔字段互转；`members.json` 可声明 `receiver: {kind: static|chain, owner, path}`。`SafeMemberPass` 已对 static 源 owner 使用 import/词法绑定解析后的 FQCN 精确匹配，对 chain 源路径逐段匹配；`@Override` 的 receiver 变化和字段 receiver 变化保守拒绝，方法转字段在 receiver 同时变化时拒绝，避免部分改写。新增回归源码但尚未编译/执行。当前真实 receiver 映射数据仍为 0：上述修复仅为保守安全准备，不代表 26.x/NeoForge 具体 receiver 迁移已经可用；完整双向真实数据与运行验证仍待补。

| 版本 | 变化 |
|---|---|
| 26.2 | `Minecraft#setScreen` / `screen` / `getToastManager` 等 → `minecraft.gui.setScreen(…)` 等（界面管理移到 `Gui`） |
| 26.2 | `Gui` 的 HUD 方法（如 `setOverlayMessage`） → `Hud` |
| NeoForge 1.21.7 | `PacketDistributor.sendToServer(…)` → `ClientPacketDistributor.sendToServer(…)` |
| 1.21.6 / 1.21.9 | `ServerPlayer.server` 字段私有化：1.21.6–1.21.8 改用 `getServer()`，1.21.9 起改用 `level().getServer()` |
| 1.21.9 | `Entity#getServer` 移除 → `level().getServer()` |
| 26.1 | `Player#displayClientMessage(msg, true)` → `sendOverlayMessage` |

**待办**：
- [x] `members.json` 已加入保守 `receiver: {kind: static|chain, owner, path}` schema；Java 加载与 validator 已同步。
- [x] **P1 receiver 启用前保守逻辑修复（静态验收）**：owner/path 匹配、静态scope识别、Override拒绝与方法转字段原子性已按当前源码核对；未知接收者保留 TODO，新增未运行的回归源码。尚未通过编译/Java运行验证，不视为完整验收。
- [ ] **真实 receiver 迁移落地**：真实映射数据仍为 0；补明确 owner/path 的正反向真实用例及运行验证后，才可加入 PacketDistributor 等已核实数据。不要把 schema/静态修复宣称为具体迁移已实现。

---

## 2. 没有自动改名的「名字太通用」的改名

**2026-10-04 安全修复**：旧的裸名改写已移除，改由 `SafeMemberPass` 在 import/类型改名之前运行。通过显式 import、词法变量/参数/字段声明、直接父类/接口确认源 owner；未知接收者保留并 TODO。`@Override` 校验直接父类，调用同步按 AST 类身份隔离，不跨嵌套/相邻/匿名类；no-op 候选保留参与消歧。字段赋值/自增不会盲目改成 getter。

**剩余限制**：没有 MC jar/完整类型求解，传递继承、方法返回值、复杂泛型、部分流作用域保守跳过；成员表没有完整重载描述符，带 note 的语义变化不自动改写。下面的数据仍只有 note/guidance，本轮未增加这些通用名映射，不能声称已自动迁移：

| 版本 | 改名 | 不自动做的具体原因 |
|---|---|---|
| 1.21.2 | `Registry#get` / `getHolder` / `getOrThrow` 等的签名与名字调整 | `get` 在 `Map`、`List`、`Optional`、`Supplier`、`DeferredHolder` 等几乎所有容器上都有，按名匹配必然误改 |
| 26.1 | `Screen#render` → `extractRenderState` | `render` 在渲染器、组件、实体渲染、用户类上普遍存在；只有 Screen 子类的覆写才该改 |
| 26.1 | `GuiGraphics#drawString` → `text` | `drawString` 也存在于 Font 等类和第三方 GUI 库，目标名 `text` 又极其通用，降级时反查会把所有 `text()` 改回去 |
| 26.1 | `GuiGraphics#renderItem` → `item` | 目标名 `item` 极通用；降级时会把 `ItemStack`、`ItemEntity`、记录访问器等的 `item()` 全改成 `renderItem` |
| 26.1 | `renderTooltip` → `tooltip` | 同名方法在 Screen 和 GuiGraphics 上的新名字不同，引擎会判为歧义；`tooltip` 也是通用名 |
| 26.1 | `Level#getDayTime` → 世界时钟 API（`getOverworldClockTime` 等） | 不只是改名，语义也可能变了：官方没有说明二者是否等价，所以只打 TODO |
| 26.2 | `Overlay#isPauseScreen` → `isPausing` | `Screen#isPauseScreen` 在 26.2 **仍然保留**，按名改会把 Screen 子类的覆写改坏 |
| 1.19.3 | 部件的 `x` / `y` 字段 → `getX()` / `getY()` | `x`、`y` 是 `Vec3`、`BlockPos`、用户坐标类等的常见字段，字段转 getter 会大面积误改 |
| 1.17 | `TextureManager.bind` → `RenderSystem.setShaderTexture` | 不只是改名，接收者和参数也变了（`setShaderTexture(0, tex)`）；Yarn 中两边都叫 `bindTexture` |

**解决方向**（做完后这些条目可以转成自动映射）：
- [ ] 在 JavaSourcePass 中引入 JavaParser SymbolSolver，配合目标版本 MC jar 或 stub 做接收者类型推断，再把成员映射改为「owner 类 + 名字」精确匹配。
- [x] `@Override` 已检查可确认的直接父类/接口，未知继承链保留；完整传递继承仍待实现。
- [x] 全部自动成员改写默认要求明确 owner，不再需要可选 `requireOwner` 标志。

---

## 3. 映射数据：已知遗漏和待补

### 3.1 参考知识库中还没进映射的条目
- [x] **26.2 四项谓词类映射（静态数据闭合）**：`FoodPredicate`、`InputPredicate`、`DataComponentMatchers` 已在 Fabric/NeoForge 26.2 映射到 `advancements.predicates`，`SheepPredicate` 映射到 `advancements.predicates.entity`；官方 26.2 client.jar 一手核实，旧版本补全160条保守 guidance，全量 validator ERROR 0/WARN 0。**FoodPredicate 的旧包路径未证实**，反向仅提示人工核对，不能写死旧 FQCN；其余三项旧 `advancements.criterion` 有 Parchment 证据，但旧版本没有自动反向映射。未覆盖成员级迁移或包中其它未点名谓词；证据与范围见 `shared/modporter/20261004-merge-closure/predicates/REPORT.md`。
- [x] **Fabric 流体渲染类 IR 身份及保守指导**：已由39个配置的官方 Fabric API 精确发布 POM→流体模块sources.jar核实，新增 `fabric.client.FluidRenderHandler`、旧 `FluidRenderHandlerRegistry` 与新 `FluidRenderingRegistry` 三个独立IR，覆盖所有有效Fabric版本并清理9个重复的旧Registry库存，映射validator ERROR 0/WARN 0。**只登记类身份**：旧版 `INSTANCE`、新版静态注册与新增模型参数不等价，跨26.x只给 guidance，不自动替换类/成员/实现体。证据见 `shared/modporter/20261005-next12/data/REPORT.md`。
- [ ] 参考知识库的**方法改名**还没有逐条与我方 `members.json` 比对；目前只粗扫过类名，126 个完整类名里 118 个已对应。
- [ ] 26.2：`SlimePredicate` → `CubeMobPredicate` 之外的 Speleothem 系列、`ChatFormatting` 被移除方法的替代 API、`valueLookupBuilder` 移除、`BlockIds` / `ItemIds` 拆分，目前只有 guidance。
- [ ] 1.21.11：GameRules 迁包、实体与模型类的大规模子包重排、`criterion` 包中未列出的其余类、环境属性 API，目前只有 guidance。
- [x] 1.21.2 `EntityAttributes` 字段改名已落地（2026-10-06）：新类 IR `mc.world.entity.ai.attributes.Attributes`；Fabric 1.21.1 写 31 个 Yarn 旧名、1.21.2 写去前缀新名并新增 `TEMPT_RANGE`（1.21.4–1.21.11 继承），成员条目不带 note（带 note 会让引擎保留原文不改名）；26.1 与 Forge/NeoForge 用官方名、成员走 IR 名默认；其余版本补 guidance。证据为 Yarn v2 tiny 与官方映射。更早 Fabric 版本 Yarn 名不单调（如 1.17.1–1.20.4 `HORSE_JUMP_STRENGTH`），只给 guidance，未自动改。Java 未编译未运行。
- [x] **1.19 / 1.19.1 聊天回调差异已核实并补 guidance**：官方 Fabric API 发布源码证实 ClientReceiveMessageEvents 直到 1.19.3 才出现；1.19/1.19.1 服务端 ServerMessageEvents 的聊天消息包装/ChatType 参数以及 game message 的 server/overlay 参数有差异。1.19.2 数据集（含别名 1.19/1.19.1）的 ServerChatEvent guidance 已说明，实际 lambda 适配仍需人工处理。
- [x] **Fabric API 事件参数和引入版本已核实并补 guidance**：ItemTooltipCallback 1.15.2–1.20.4 为三参，1.20.5 起四参（26.x 官方名）；ServerLivingEntityEvents 自 1.19.2 起有 ALLOW_DAMAGE/ALLOW_DEATH/AFTER_DEATH，MOB_CONVERSION 1.20.1 起、AFTER_DAMAGE 1.21.1 起；10 个数据集共 15 条 guidance 已按证据更新。未自动改写回调 lambda。
- [x] **Fabric 26.1 ResourceLoader / AttachmentRegistry 方法名已核实**：官方 Fabric API 模块源码证实 ResourceLoader 的 26.x 名字是 `registerReloadListener` 与 `addListenerOrdering`，guidance 已修；AttachmentRegistry 1.21.11→26.1 方法名不变，26.x 多一个 syncWith 三参重载，guidance 已补准确方法名与官方 Identifier 工厂。注意 1.21.2 的模块版本线回落，不能按 MC 版本单调推断可用重载；参数/回调语义变化仍需人工核对。
- [ ] `ChunkPos` 构造器 → 静态工厂（26.1）：**引擎与数据已就绪**（2026-10-06）——`idioms.json` 支持 `argTypes`；`rewriteIdioms` 按位置互斥、内层优先；参数类型证明不了就保留原代码并标 TODO。另修正：源/目标两侧类名不同（Fabric Yarn `net.minecraft.util.math.BlockPos` → 官方 `net.minecraft.core.BlockPos`）时按类映射换算后再比对。`chunkpos.containing`/`chunkpos.unpack` 已写入 Fabric 9 个旧版本 + 26.1、NeoForge 1.20.1/1.20.4/1.21.1 + 26.1（官方 26.1/26.2 client.jar 证实只剩 `(int,int)` 构造器、新增 `containing(BlockPos)`/`unpack(long)`）。**未验收**：Java 未编译、`IdiomArgTypesTest` 未运行。

### 3.2 待核实（目前写成 guidance，没有写死映射）
- [x] Forge 1.17.1：`TierSortingRegistry` 存在（官方 1.17.x 源码）；`IEntityAdditionalSpawnData` 位于 `net.minecraftforge.fmllegacy.common.registry`（1.17.1-37.1.1 sources.jar），guidance 已修正。
- [x] Forge 1.14.4 / 1.15.2：`VillagerTradesEvent`/`WandererTradesEvent` 均已存在（官方 1.14.x/1.15.x 分支），现有映射正确。
- [x] Forge 1.16.5：`ForgeSpawnEggItem` 已存在（36.2.39 sources.jar 证实），2026-10-06 已补类映射、删去“本版本没有”的旧 guidance；同目录别名 1.16.4（Forge 35.1.37）没有此类，映射 note 已注明。model generators 由 `neoforge.client.BlockStateProvider`/`ItemModelProvider` 覆盖。
- [x] Forge 1.21.1：官方 52.1.0 sources.jar 中 `RenderGuiEvent`/`RenderGuiOverlayEvent`/`RegisterGuiOverlaysEvent`/`IGuiOverlay`/`VanillaGuiOverlay` 文件只剩注释（已移除）；`TierSortingRegistry` 不存在；Forge 1.20.6–1.21.4 也没有 `AddGuiOverlayLayersEvent`/`ForgeLayeredDraw`（NeoForge API），2026-10-06 已改正 16 处误导 guidance 并删去两条误登 removed 库存。`EntityItemPickupEvent`、`ConfigScreenHandler` 仍在原包。
- [x] 1.17.1–1.20.5 官方类名确为 `LighthingBoltPredicate`（拼写错误），1.21 起为 `LightningBoltPredicate`；现有 guidance 与证据一致。
- [x] NeoForge：`GameTypePredicate`/`MovementPredicate` 均在 1.21 正式版引入（1.20.6 官方映射无），guidance 已修正。
- [x] Fabric 1.16 / 1.16.1：项目所用 Fabric API 0.42.0+1.16 已含 `TradeOfferHelper`（首次出现于 0.21.0+build.407-1.16），现有 guidance 无需改。
- [ ] NeoForge 1.20.3 的构建模板：官方没有 MDK，现在沿用 NeoGradle 7.0.116，兼容性未核实。
- [ ] NeoForge 1.20.1 的构建模板：原 MDK 已被替换，Gradle 8.8 配 NeoGradle 6 的写法未核实。
- [ ] Fabric 1.21.9 `SkinTextures` 的 `body().texturePath()`、`KeyInput` / `Click` 的记录组件名：Yarn 中没有命名，未逐字核实。
- [ ] Forge IR 回补按要求没有逐条大规模核实，详见 `mappings/sources/forge-ir-backfill.md`。

### 3.3 回补引入的退化：1.12 生命周期事件不再自动改名
- [x] **Forge 1.12 生命周期事件重复 IR 退化已静态恢复**：7个 Forge 版本为 `forge.lifecycle.commonSetup`/`forge.event.PlayerEvent` 标记唯一 `primary:true`，恢复旧 `forge.lifecycle.init`/`forge.event.PlayerEventFml` 同FQCN标注；validator ERROR 0、WARN 0、INFO 24。未编译/未运行 Java。详见本轮第3类交付报告。
  - 解决：引擎允许多个 IR 合法地映射到同一个 FQCN（`VersionMappings.irByFqcn` 改为确定性的优先级，比如在 classes.json 里加 `"primary": true`），然后恢复这两条映射；或者修改 README 的描述。

### 3.4 其它数据层待办
- [x] `templates/settings.gradle` 支持已接入（`SettingsGradlePass` + 1.20.1/1.20.3/1.20.4 模板 + `DefaultPortEngine` 集成，缺失模板不生成，Kotlin DSL 不生成冲突 Groovy）。
- [x] **Settings 仓库迁移静态安全闭合（P1）**：需求取自目标 settings 模板的真实仓库声明，缺模板为 UNKNOWN（原样保留/不生成 + TODO）；已有文件在 pluginManagement/repositories 范围识别 Portal 与 NeoForge Maven，缺项补齐，注释不算仓库。已补回归源码；Groovy 非完整语法解析，等价 URL/复杂写法和 Java 运行验证仍未覆盖。
- [x] 部分 `neoforge.*` id 在 Forge 中有同名类，已经一并映射：规则已写在 `NEOFORGE-IR-CONTRACT.md` 2.2 节（9 个复用 id 与清单一致）；2026-10-06 起 `validate_mappings.py` 的 `E-neoforgeIr` 封闭清单校验也覆盖 Forge 数据集（构造越界 id 已验证能报错）。
- [ ] Forge 侧这 9 个复用条目没有 note（契约 2.2 第 3 条）。**不要直接给 classes.json 加 note**：引擎会把目标类的 note 在每个相关 import 处输出成 TODO，“与 NeoForge 共用”这类说明会变成大量无用 TODO。若要满足契约，应改成只供文档/校验使用的字段，或在契约里注明 Forge 侧免 note。2026-10-06 已修正 1.17.1 `IEntityAdditionalSpawnData` 的 guidance（官方 sources.jar 证实位于 `net.minecraftforge.fmllegacy.common.registry`）并更新 `forge-ir-backfill.md` 第 3 节；该节其余 4 条仍待核实。

---

## 4. 其它引擎限制（按影响从大到小）

- [x] **JavaParser 语言级别升级（2026-10-04）**：依赖升级至已核实的 3.28.2，Java 8–26 使用精确语言级别，未知版本原样保留并报 ERROR；不再依赖 `CURRENT`。上游 Java 25 validator 中 compact class / flexible constructor bodies 仍标为 WIP，不能宣称完整支持；解析失败保持原文。新增现代语法回归用例，本轮未编译或执行 Java 测试。
- [x] **removed 判定已改为精确存在/同 FQCN removed 记录**：不再仅凭 concept 支持判定类存在；但目标 guidance 说类仍可用而 classes/removed 未列时，仍会报 REMOVED，需要补精确存在数据或 UNKNOWN/TODO 语义。
- [x] **一个源类对应多个 IR**：已改为重复 FQCN 组必须恰有一个 `primary: true`，否则反查 UNKNOWN + TODO；validator 已同步。
- [x] **no-op 歧义候选**：成员索引保留源/目标名相同的候选，按 owner 消歧后再决定是否跳过无变化项。
- [x] **Java 22+ `_` 未命名变量/模式**：不再按普通标识符自动改名，向低版本迁移时提示 TODO（包括 Java 8）；旧版普通 `_` 自动改名避开文件内已占用名称。另已增加 record/switch 模式与部分 Java 25 语法的降级诊断。
- [x] **NeoForge/Fabric 包下未映射类警告**：`JavaSourcePass.rewriteImports` 已扩展到 `net.neoforged.` 与 `net.fabricmc.`；真实样例编译/运行验证仍未做。
- [ ] **测试验证**：当前有11个不依赖JUnit的Java回归入口（`src/test/java`），分别注册于 `gradle safetyRegression`（5个）和 `gradle resourceRegression`（6个），只能在允许编译后执行；该命令会编译，本轮未执行。当前仅完成源码语法检查、上游 API 文本核对和 Python 映射校验，不等同测试通过。真实样例工程快照/编译/运行验证仍待补。
