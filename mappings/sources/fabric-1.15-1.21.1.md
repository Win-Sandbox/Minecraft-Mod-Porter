# 来源登记：Fabric（Yarn）1.15.2 – 1.21.1

范围：`mappings/versions/fabric/` 下 1.15.2、1.16、1.16.1、1.16.4（别名 1.16.2 / 1.16.3）、1.16.5、1.17.1、
1.18.1（别名 1.18）、1.18.2（别名无）、1.19.2（别名 1.19 / 1.19.1）、1.19.3、1.19.4、1.20.1（别名 1.20）、
1.20.4（别名 1.20.2 / 1.20.3）、1.21.1（别名 1.21）。

线索来源（第三方，只取事实，文字未复制）：`ref/knowledge-base/minecraft/*.md`（1.16→1.21.1 各跳跃）、
`ref/patterns/{class-moves,method-renames,signatures}.md`、`ref/knowledge-base/loaders/fabric/versions.md`、
`ref/auto-porter/templates/<ver>/{template-info.json,gradle.properties}`、
`ref/auto-porter/src/main/java/com/autoporter/ApiChangeRule.java`、`ref/java/java17.md`、`ref/java/java21.md`。

## 0. 一手来源与核实方法（所有版本通用）

| 用途 | 一手来源 |
|---|---|
| 版本清单、Java 版本、客户端/官方映射下载地址 | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json 及其中各版本 JSON（`javaVersion`、`downloads.client_mappings`） |
| packFormat | 各版本客户端 jar 内 `version.json` 的 `pack_version`（按 HTTP Range 只读取该文件） |
| Mojang 名 → 混淆名 | 各版本 `client_mappings`（client.txt） |
| 混淆名 → intermediary | https://maven.fabricmc.net/net/fabricmc/intermediary/<ver>/intermediary-<ver>-v2.jar |
| intermediary → Yarn | https://maven.fabricmc.net/net/fabricmc/yarn/<ver>+build.<n>/yarn-<ver>+build.<n>-v2.jar |
| 继承关系（成员在父类/接口上声明时） | 各版本客户端 jar 的 class 文件头（super / interfaces） |
| Yarn 构建号 | https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml（取每个 MC 版本的最大 build） |
| Fabric API 版本、模块与类 | https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml；各版本 `fabric-api-<ver>.jar`（`fabric.mod.json` 的 `depends.minecraft`、`id`、`provides`；嵌套模块 jar 的类清单与 access widener）；旧版本为 `fabric-api-<ver>.pom` 列出的各模块 jar |
| Fabric Loader | https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml |
| Loom | https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml、`fabric-loom/1.17-SNAPSHOT/maven-metadata.xml`、`fabric-loom-1.17.13.module`（`org.gradle.plugin.api-version`=9.5.0、JVM 21）、插件标记 `fabric-loom/fabric-loom.gradle.plugin/maven-metadata.xml`；Loom 源码 https://github.com/FabricMC/fabric-loom （dev/1.2、1.5、1.6、1.7 的 `LoomGradlePluginBootstrap` 最低 Gradle 版本） |
| Gradle | https://services.gradle.org/versions/all |
| 官方工具链组合 | https://github.com/FabricMC/fabric-example-mod 各版本分支（1.15.2 … 1.21.1）的 gradle.properties / gradle-wrapper.properties / fabric.mod.json 及 1.16.5 分支提交历史（2026-09-05 的部署：loom 1.17-SNAPSHOT + Gradle 9.5.1 + loader 0.19.5） |
| 1.21 变更（Identifier 工厂、数据包目录单数化、FabricDimensions 移除） | https://fabricmc.net/2024/05/31/121.html ；并用 1.21 客户端 jar 目录（`data/minecraft/recipe` 等）与 API jar 类清单复核 |

成员映射的核实方式：对每个 (IR 类, IR 成员) 取该版本的 Mojang 名，沿 Mojang→混淆→intermediary→Yarn 链解析，
在类层级中向上查找声明处，23 个版本逐一比对（脚本结果 0 处不一致）。类映射同理，Fabric API 类以 API jar 实际类清单为准。

## 1. version.json（全部版本）

| 版本 | Java | packFormat | Yarn | Fabric API | 依据 |
|---|---|---|---|---|---|
| 1.15.2 | 8 | 5 | 1.15.2+build.17 | 0.28.5+1.15 | API depends `~1.15-alpha` |
| 1.16 | 8 | 5 | 1.16+build.4 | 0.14.2+build.373-1.16 | 最后一个声明兼容 1.16.0 的 API（`~1.16-alpha.20.14.a`）；0.14.3 起要求 ≥1.16.2 快照 |
| 1.16.1 | 8 | 5 | 1.16.1+build.21 | 0.18.0+build.387-1.16.1 | depends `1.16.1` |
| 1.16.2 / 1.16.3 / 1.16.4 | 8 | 6 | build.47 / build.47 / build.9 | 0.42.0+1.16 | depends `~1.16.2-alpha.20.28.a` |
| 1.16.5 | 8 | 6 | 1.16.5+build.10 | 0.42.0+1.16 | 同上 |
| 1.17.1 | 16 | 7 | 1.17.1+build.65 | 0.46.1+1.17 | depends `~1.17.1-rc` |
| 1.18 | 17 | 8 | 1.18+build.1 | 0.46.3+1.18 | 0.46.4 起 depends 精确为 `1.18.1`，1.18.0 只能用 ≤0.46.3 |
| 1.18.1 | 17 | 8 | 1.18.1+build.22 | 0.46.6+1.18 | depends `1.18.1` |
| 1.18.2 | 17 | 8 | 1.18.2+build.4 | 0.77.0+1.18.2 | |
| 1.19 / 1.19.1 / 1.19.2 | 17 | 9 | build.4 / build.6 / build.28 | 0.58.0 / 0.58.5 / 0.77.0 | |
| 1.19.3 | 17 | 12 | 1.19.3+build.5 | 0.76.1+1.19.3 | |
| 1.19.4 | 17 | 13 | 1.19.4+build.2 | 0.87.2+1.19.4 | |
| 1.20 / 1.20.1 | 17 | 15 | build.1 / build.10 | 0.83.0+1.20 / 0.92.12+1.20.1 | |
| 1.20.2 | 17 | 18 | 1.20.2+build.4 | 0.91.6+1.20.2 | |
| 1.20.3 / 1.20.4 | 17 | 22 | build.1 / build.3 | 0.91.1+1.20.3 / 0.97.3+1.20.4 | |
| 1.21 / 1.21.1 | 21 | 34 | build.9 / build.3 | 0.102.0+1.21 / 0.116.17+1.21.1 | |

全部版本统一：loom `1.17-SNAPSHOT`、fabric-loader `0.19.5`、Gradle `9.5.1`、`loaderVersionRange` `>=0.19.5`。
依据：官方示例模组各分支在 2026-09-05 的部署即为此组合；Loom 1.17 的模块元数据要求 Gradle 插件 API 9.5.0、JVM 21，
Loom 1.x 支持 1.15.2 起的 Yarn 映射。原数据中 1.20.1 的「loom 1.6 + Gradle 8.1.1」不成立（Loom 1.6 最低 Gradle 8.6），已修正。

fabric.mod.json 依赖键：Fabric API 0.77.0+1.18.2 / 0.77.0+1.19.2 及 1.19.3 起的版本 `id` 为 `fabric-api` 且 `provides: ["fabric"]`，
更早版本 `id` 为 `fabric`。因此 1.15.2–1.19.2 系模板写 `"fabric": "*"`（对 1.18/1.18.1/1.19/1.19.1 也成立），1.19.3 起写 `"fabric-api": "*"`。
原 1.17.1 / 1.18.2 / 1.19.2 模板写 `fabric-api` 会在 0.46.x / 0.58.x 上无法解析依赖，已修正。
build.gradle 模板统一改为 `plugins {}` + `base { archivesName }` + `options.release`（Gradle 9 已移除 `archivesBaseName` 约定属性）。

## 2. 类映射（classes.json）

- 全部 `mc.*`：按上面的 Mojang→Yarn 链逐版本生成并与数据集比对。修正：1.15.2 `mc.entity.PathfinderMob` 应为
  `MobEntityWithAi`（1.16.1 起才叫 PathAwareEntity；1.16 仍是 MobEntityWithAi）。
- 1.16–1.16.4 与 1.16.5 的 Yarn 差异（逐版本验证）：NBT 类 `CompoundTag/ListTag/Tag`（1.16.5+build.10 才回填为 `NbtCompound/NbtList/NbtElement`）、
  `net.minecraft.client.options.KeyBinding`（1.16.5 build.10 起 `client.option`）、`AbstractButtonWidget`（1.16.5 起 `ClickableWidget`）。
- `fabric.*`：以 API jar 类清单为准。修正 1.17.1–1.19.4 的四处错误包名：`ClientTickEvents` / `ClientLifecycleEvents` 应在
  `net.fabricmc.fabric.api.client.event.lifecycle.v1`，`ClientPlayNetworking` 应在 `...client.networking.v1`，
  `ServerEntityEvents`（1.17.1、1.18.2）应在 `...event.lifecycle.v1`（不是 entity.event.v1）。
- `fabric.screen.HandledScreens`：1.16–1.18.1 原版 `HandledScreens.register` 为私有且 API 未开放 → 映射 Fabric 的 `ScreenRegistry`；
  1.18.2（API 0.77.0）起 `fabric-transitive-access-wideners-v1` 开放了 `class_3929.method_17542` 与 `ScreenHandlerType` 构造器 → 映射原版类。依据：API jar 内 `.accesswidener`。
- 1.16 / 1.16.1 的 API（0.14.2 / 0.18.0）没有 networking v1、FabricItemSettings、fabric-biome-api-v1、WorldRenderEvents；1.16 另无 PlayerBlockBreakEvents；1.18/1.18.1 无 loot v2。
- `fabric.block.FabricBlockSettings` 在 1.21.1（API 0.116.17）中仍存在（已弃用），补映射；0.106.1+1.21.2 中已无此类。`FabricItemSettings` 在 0.97.8+1.20.5 起已删除。
- 新增 IR（官方名推导）：`mc.core.Registry`、`mc.client.gui.GuiGraphics`（DrawContext，1.20+）、`mc.client.gui.GuiComponent`（DrawableHelper，≤1.19.4）、
  `mc.client.gui.components.Button/EditBox/AbstractWidget`、`mc.network.FriendlyByteBuf`、`mc.network.RegistryFriendlyByteBuf`（1.21）、
  `mc.client.player.AbstractClientPlayer`、`mc.client.resources.PlayerSkin`（SkinTextures，1.20.2+）、`mc.client.DeltaTracker`（RenderTickCounter，1.21）、
  `mc.network.chat.MutableComponent`（MutableText，1.16+）。并按其它数据集已建立的 id 补映射：CreativeModeTabs、PotionUtils、FoodProperties、
  CustomPacketPayload（1.20.2+）、StreamCodec、ByteBufCodecs、DataComponents、DataComponentType、CustomData、PotionContents、LayeredDraw、
  ItemInteractionResult、TooltipFlag（≤1.20.4 为 `client.item.TooltipContext`，1.21 为 `item.tooltip.TooltipType`）。

## 3. 成员映射（members.json）

全部条目由 Mojang 名逐版本解析核实。主要修正：
- 1.17.1 / 1.18.2 原来缺少约 70 条（hurt、spawnEntity、putUuid 等），会导致从其它版本转入时被错误改回 Mojang 名，已补齐；各版本成员集合现在一致。
- 1.16.5：`ItemStack#save`、`BlockEntity#saveAdditional` 在 1.16.5+build.10 为 `writeNbt`（不是 toTag）；1.16–1.16.4 才是 `toTag`，`getUpdateTag` 在 1.16.4 及更早为 `toInitialChunkDataTag`。
- `Level#getEntitiesOfClass`：1.16 / 1.16.1 为 `getEntities`，1.16.2 起 `getEntitiesByClass`。
- `Entity#getLevel`：≤1.17.1 为 `getEntityWorld`（1.17.1 的 Entity 尚无 getWorld），1.18.2–1.19.4 为 `getWorld`（原 1.19.x 写 getEntityWorld 有误）。
- `ServerPlayer#getLevel`：≤1.17.1 `getServerWorld`，1.18–1.19.4 `getWorld`，1.20+ `getServerWorld`。
- `ItemStack#sameItem`：≤1.19.2 为 `isItemEqualIgnoreDamage`（原 1.19.2 写 isItemEqual 有误），1.19.4 `isItemEqual`，1.20+ 静态 `areItemsEqual`。
- `ItemStack#isSameItemSameTags`：1.17–1.20.4 为 `canCombine`（原 1.19.x 写 areEqual 有误），1.21 为 `areItemsAndComponentsEqual`（原 1.21.1 写 areEqual 有误，areEqual 会比较数量）。
- `Component#getString`：1.15.2 确为 `asString`（已核实 Text 无 getString）。
- `Block#use`（1.21）：`useWithoutItem` → `onUse(state, world, pos, player, hit)`，带物品交互为 `onUseWithItem`（note 已改）。
- 新增：`Screen#addRenderableWidget`（≤1.16.5 `addButton`，1.17+ `addDrawableChild`）、`Screen#addWidget`、`Screen#rebuildWidgets`（1.19+ `clearAndInit`）、
  `Screen#repositionElements`（1.19.4+ `initTabNavigation`）、`AbstractClientPlayer#getSkinTextureLocation/getSkin`、`Button#builder`、`EditBox#setHint`（1.19.3+ `setPlaceholder`）、
  `DeltaTracker#getGameTimeDeltaPartialTick`（`getTickDelta`）、`Biome#hasPrecipitation/getPrecipitationAt` 在 ≤1.19.3 的守护条目。

## 4. removed.json

- 删除「类已在同一数据集 classes.json 中映射」的死条目，以及 1.17.1 / 1.18.2 中四个不存在的类名（见 §2 的错误包名）。
- 其余条目按「该版本 API/Yarn 中确实存在且未映射」筛选；新增 ScreenRegistry、ClientTickCallback、KeyBindingRegistry、TagFactory、约定标签 v2、
  ServerMessageEvents、ClientReceiveMessageEvents、ServerConfigurationNetworking、DataGeneratorEntrypoint、FabricDefaultAttributeRegistry、FluidRenderHandlerRegistry。
- loot v2：API 字节码显示 `LootTableEvents.Modify` 在 0.97.3+1.20.4 为 (ResourceManager, LootManager, Identifier, …)，0.100.8+1.20.6 已为 (RegistryKey, …)，
  0.102.0+1.21 另有 loot v3（多一个 WrapperLookup 参数），据此改写说明。

## 5. idioms.json

- 新增惯用法 `soundevent.createVariableRangeEvent`：≤1.19.2 为公开构造器 `new SoundEvent(Identifier)`，1.19.3 起为 `SoundEvent.of(Identifier)`（Mojmap createVariableRangeEvent）。
- guidance：为每个版本补齐 Forge 1.20.1 全部 `forge.*`、目标侧缺失的 `fabric.*` / `mc.*`，以及当前可见的 neoforge 数据集引入的 `forge.*` / `neoforge.*` id。
  文字按该版本 API 实际具备的类（Transfer/Lookup、networking v1、loot v1/v2、ScreenEvents 等）分别撰写；`EventFactory.createWithPhases` 的有无以 API 字节码为准（0.42.0 起有，0.14.2/0.18.0 没有）。
- 1.18.2 `gui.menu` 改为 HandledScreens.register + ExtendedScreenHandlerType（ScreenHandlerRegistry 已弃用）；1.19.3、1.18.1、1.16.x 覆盖层分别改写伤害类型、ArmorItem、配方、标签、网络、物品属性等与基版本不同的说明。

## 5.1 日志库

- `logging.slf4j`（新增 concept）：依据各版本 JSON 的 libraries（https://piston-meta.mojang.com 下各版本 JSON）：1.16.5 只有 log4j-api/core 2.8.1；1.17.1、1.18.2 增加 slf4j-api 1.8.0-beta4 与 log4j-slf4j18-impl；1.21.1 为 slf4j-api 2.0.9 + log4j-slf4j2-impl。

## 6. ref 条目的处理

| ref 条目（出处） | 处理 | 理由 |
|---|---|---|
| KeyBinding → KeyMapping（1.16_to_1.17.md、method-renames.md、ApiChangeRule） | 丢弃 | 映射体系混淆：Yarn 在全部版本都叫 KeyBinding，Mojang 名在全部版本都叫 KeyMapping |
| World→Level、PlayerEntity→Player（1.17.1_to_1.18.md、method-renames.md、ApiChangeRule） | 丢弃 | 同上，不是版本间改名 |
| Screen 包 `gui.screen` → `gui.screens`（class-moves.md） | 丢弃 | Yarn 与 Mojmap 的包名差异，非版本变化 |
| DrawableHelper → GuiGraphics（class-moves.md） | 修正 | Yarn 中 1.20 起是 DrawContext，DrawableHelper（Mojmap GuiComponent）在 1.20 移除；以两个 IR 表达 |
| PacketByteBuf → RegistryFriendlyByteBuf（1.20.4_to_1.20.5.md、class-moves.md） | 修正 | PacketByteBuf 在 1.20.5+ 仍存在；新增的是其子类 RegistryByteBuf |
| addButton → addRenderableWidget（1.16_to_1.17.md） | 保留并换成 Yarn 名 | 1.16.5 `addButton`，1.17 `addDrawableChild` |
| Log4j → SLF4J（1.16_to_1.17.md、java17.md、ApiChangeRule） | 修正后写入 | 版本 JSON 显示 1.17.1 起原版自带 slf4j-api（1.17.1 为 1.8.0-beta4，1.21.1 为 2.0.9），且 Log4j 一直保留（1.21.1 仍有 log4j-api 2.22.1），所以不是「1.17 必须改用 SLF4J」，而是「降级到 1.16.5 及更早时 SLF4J 不可用」。以新 concept `logging.slf4j` 表达：1.17.1 起的数据集把 org.slf4j.Logger / LoggerFactory 列入 removed 并声明 supported，≤1.16.5 的 guidance 给出改回 Log4j 的写法 |
| TextureManager.bind → RenderSystem.setShaderTexture（ApiChangeRule） | 不写入 | Yarn 中 1.16.5 与 1.17+ 方法名都叫 bindTexture（语义变化），只能人工处理，未设自动映射 |
| Component.literal ↔ new TextComponent（ApiChangeRule） | 保留（已有惯用法） | Yarn 为 LiteralText/TranslatableText ↔ Text.literal/translatable |
| rebuildWidgets ↔ init(mc, w, h)（1.18.2_to_1.19.md、ApiChangeRule） | 保留 | 1.19+ Yarn `clearAndInit`；旧版本为守护条目 |
| Button.builder、getX()/getY()、setHint、setFocused（1.19.2_to_1.19.3.md） | 部分保留 | builder / setHint 以成员守护与类 note 表达；x/y 字段 ↔ getX()/getY() 不做自动映射（名字过于通用，会误改 Vec3d.x 等），写入类 note；setFocused 为私有细节，未写入 |
| HUD 回调 (PoseStack, float) → (GuiGraphics, DeltaTracker) 在 1.20.1（ApiChangeRule） | 修正 | 1.20 首参变 DrawContext，第二参数到 1.21 才变 RenderTickCounter（Mojmap DeltaTracker） |
| PlayerSkin 1.20.2（1.20.1_to_1.20.2.md） | 保留 | Yarn SkinTextures，`getSkinTextures().texture()` |
| DeltaTracker、Identifier.of、数据包目录单数化、FabricDimensions 移除（1.20.6_to_1.21.md） | 保留 | 已由 Yarn、客户端 jar 目录与 API jar 复核 |
| GuiMessageTag.systemSinglePlayer / addMessage mixin 描述符（ApiChangeRule 1.19.1/1.19.2） | 不写入 | 属于 Mixin 目标描述符细节，引擎无对应数据形态 |
| 聊天签名导致 ClientReceiveMessageEvents 签名变化（1.19_to_1.19.1.md） | 部分保留 | 以 removed 条目提示需逐个核对签名；具体签名差异未逐一核实 |
| 各版本 loader/API/loom 号（loaders/fabric/versions.md、templates/*） | 修正 | 如 1.16 / 1.16.1 用 0.42.0+1.16 不兼容（要求 ≥1.16.2）、loader 写成 `0.11.7+build.2` 这种不存在的格式、1.19.4 配 loader 0.15.11 + loom 0.12 等；全部改为 Maven 实际存在且依赖声明相容的组合 |
| NeoForge 相关条目（1.20.1_to_1.20.2.md、1.20.4_to_1.20.5.md） | 不适用 | 不属于 Fabric 数据集 |

## 7. 未能核实、未写入

- 1.19 / 1.19.1 与 1.19.2 之间聊天签名 API（message-api-v1 客户端事件）的具体参数差异，未写成成员映射。
- `ServerLivingEntityEvents` 中 ALLOW_DEATH / AFTER_DEATH 各常量的首发版本只核实到「类存在」（1.19.2 起），guidance 用了「以本版本 API 为准」的措辞。
- `ItemTooltipCallback` 各版本的回调参数没有逐个核实，guidance 只提示需按签名调整。
- 其它 agent 近期新增的 concept（client.renderState、commands.permissions、fabric.resourceLoader、gui.screenManager、input.events、interaction.result、
  item.materials、item.propertiesId、item.stackTemplate、item.toolComponents、nbt.access、nbt.valueIo（原 nbt.readWriteView 已并入）、neoforge.attachment、
  neoforge.transfer、render.pipeline、villager.trades、world.clock）以及大量新 `mc.*` id（如 advancements 断言类）未在本范围的 guidance 中覆盖，交由汇总步骤处理。

## 8. 汇总步骤补写的 guidance（2026-10）

- 为 1.15.2–1.21.1 全部数据集补齐上一节列出的 concept，以及 `fabric.client.HudElement*`、`fabric.resource.ResourceLoader`、
  `mc.client.gui.Hud`、`mc.client.input.*`、`mc.client.renderer.RenderPipelines`、`mc.world.level.storage.Value*` 的反向 guidance。
- 文字中的类名/成员名直接取自同一数据集已核实的 classes.json / members.json（如 1.15.2–1.16.4 的 `CompoundTag`、
  1.15.2/1.16.x 的 `openScreen`、`fromTag`/`toTag`），首次出现版本取自本仓库各数据集已登记的映射；不引入新的映射条目。
- 1.21.1 的 concept 说明沿用 1.20.5 覆盖层中已按「1.21.1 及以前」写好的文字。
- `villager.trades`：TradeOfferHelper 位置核实——https://github.com/FabricMC/fabric/tree/1.16/fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/api/object/builder/v1/trade
  （1.16 分支存在 TradeOfferHelper.java）；1.15 分支同一路径存在 `trade` 目录。早期 1.16 / 1.16.1 API 构建是否已包含该类未逐个核实，guidance 中注明需先确认。
