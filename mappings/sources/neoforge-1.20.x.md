# 来源登记：NeoForge 1.20.x（neoforge/1.20.1 – 1.20.6）

范围：`mappings/versions/neoforge/{1.20.1,1.20.2,1.20.3,1.20.4,1.20.5,1.20.6}`。
线索来源（第三方，只取事实）：`ref/`（reqsery/mc-mod-porter）。所有写入数据的事实都经下列一手来源核实；
note / message / guidance 均为自行撰写，未复制 ref 或任何第三方文字。

## 0. 通用一手来源

| 类别 | URL |
|---|---|
| NeoForge 版本列表 | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml |
| NeoForge 各版本类清单（universal jar）、源码（sources jar）、依赖（pom） | https://maven.neoforged.net/releases/net/neoforged/neoforge/{20.2.93,20.3.8-beta,20.4.251,20.5.21-beta,20.6.141}/ |
| FML（FancyModLoader）类清单与源码 | https://maven.neoforged.net/releases/net/neoforged/fancymodloader/{loader,core,events,language-java}/{1.0.16,2.0.17,3.0.18,3.0.45,47.1.56}/ |
| 事件总线 | https://maven.neoforged.net/releases/net/neoforged/bus/{7.2.0,8.0.1}/ ；Forge 1.20.1 侧 https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.0.5/ |
| Dist / OnlyIn | https://maven.neoforged.net/releases/net/neoforged/mergetool/2.0.2/（api 分类器） |
| NeoForge 1.20.1 分支 | https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml ，https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/ |
| 构建插件 | https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml ，https://plugins.gradle.org/m2/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/ ，https://maven.neoforged.net/releases/net/neoforged/gradle/userdev/maven-metadata.xml ，https://maven.neoforged.net/releases/net/neoforged/gradle/net.neoforged.gradle.gradle.plugin/maven-metadata.xml ，https://maven.neoforged.net/releases/net/neoforged/NeoGradle/maven-metadata.xml |
| Gradle 发行版 | https://services.gradle.org/distributions/gradle-{8.8,8.14.2,8.14.5,9.2.1}-bin.zip |
| 官方 MDK | https://github.com/NeoForgeMDKs/MDK-1.20.2-NeoGradle ，…/MDK-1.20.4-ModDevGradle ，…/MDK-1.20.4-NeoGradle ，…/MDK-1.20.5-ModDevGradle ，…/MDK-1.20.6-ModDevGradle ，https://github.com/neoforged/MDK/tree/1.20.1-legacy |
| Mojang 版本清单 / 官方映射 / pack_version | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json （各版本 client_mappings 与 server.jar 内 version.json） |
| NeoForged 官方新闻 | https://neoforged.net/news/theproject/ ，/news/20.2release/ ，/news/20.2eventbus-changes/ ，/news/20.2registry-rework/ ，/news/20.3release/ ，/news/20.3capability-rework/ ，/news/20.4networking-rework/ ，/news/20.5release/ ，/news/2024-retrospection/ |
| 20.2 类改名清单（官方 20.2 发布文章链接的迁移脚本） | https://gist.github.com/Technici4n/facbcdf18ce1a556b76e6027180c32ce （neoforge_renames.tsrg） |
| ModDevGradle 旧版插件说明 | https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md |

## 1. 版本号与构建（version.json / templates）

| 版本 | NeoForge | FML（loaderVersion） | 插件 | Gradle | Java | pack | 线索 | 核实 |
|---|---|---|---|---|---|---|---|---|
| 1.20.1 | 47.1.106（net.neoforged:forge） | [47,) | NeoGradle 6.0.21（`net.neoforged.gradle`） | 8.8 | 17 | 15 | ref 无（template-info `hasNeoForge=false`） | neoforged forge maven-metadata；NeoGradle maven-metadata；pack 取自 server.jar version.json |
| 1.20.2 | 20.2.93 | [1,)（FML 1.0.16） | NeoGradle userdev 7.0.116 | 8.14.5 | 17 | 18 | ref `auto-porter/templates/1.20.2/template-info.json`（20.2.59-beta） | neoforge maven-metadata；MDK-1.20.2-NeoGradle（gradle.properties、wrapper、build.gradle） |
| 1.20.3 | 20.3.8-beta | [1,)（FML 1.0.16） | NeoGradle userdev 7.0.116 | 8.14.5 | 17 | 22 | ref template-info 1.20.3（20.3.8-beta） | neoforge maven-metadata（20.3 只有 beta）；pom 依赖 FML 1.0.16 |
| 1.20.4 | 20.4.251 | [1,)（FML 2.0.17） | ModDevGradle 2.0.148 | 9.2.1 | 17 | 22 | ref template-info 1.20.4（20.4.80-beta） | MDK-1.20.4-ModDevGradle；plugins.gradle.org 插件 POM |
| 1.20.5 | 20.5.21-beta | [1,)（FML 3.0.18） | ModDevGradle 2.0.92 | 8.14.2 | 21 | 32 | ref template-info 1.20.5、`ref/java/java21.md` | MDK-1.20.5-ModDevGradle；Mojang 清单 javaVersion=21 |
| 1.20.6 | 20.6.141 | [1,)（FML 3.0.45） | ModDevGradle 2.0.148 | 9.2.1 | 21 | 32 | ref template-info 1.20.6（20.6.99-beta） | MDK-1.20.6-ModDevGradle |

- mods.toml 依赖写法：FML 1.x 源码 ModInfo 只读 `mandatory`（1.20.2/1.20.3 模板用 `mandatory=true`）；FML 2.0.17 起读 `type`（1.20.4+ 用 `type="required"`，与 MDK 1.20.4/1.20.5/1.20.6 一致）。依赖 modId 为 `neoforge`（20.2 发布文章）。
- 1.20.5 起元数据文件名 `neoforge.mods.toml`：/news/20.5release/ 与 MDK-1.20.5 目录结构。
- loaderVersion 取 MDK 的 `loader_version_range=[1,)`。
- 1.20.3 与 1.20.4 的 Mojang client_mappings 为同一文件（URL 相同），原版层完全一致。

## 2. 加载器层（forge.* / neoforge.*）

| 条目组 | ref 线索 | 核实依据 |
|---|---|---|
| 包名 net.minecraftforge → net.neoforged.{neoforge,fml,bus,api.distmarker}；MinecraftForge→NeoForge、ForgeConfigSpec→ModConfigSpec、ForgeFlowingFluid→BaseFlowingFluid、ForgeSpawnEggItem→DeferredSpawnEggItem、ForgeHooks→CommonHooks、ForgeEventFactory→EventHooks、ForgeHooksClient→ClientHooks、IForgeMenuType→IMenuTypeExtension、ForgeCapabilities→Capabilities（20.2） | `knowledge-base/minecraft/1.20.1_to_1.20.2.md`、`patterns/class-moves.md`、`ApiChangeRule.java`（1.20.1→1.20.2） | 20.2release 文章 + 改名 gist（tsrg 与类改名列表）+ 各版本 universal jar 类清单 |
| RegistryObject→DeferredHolder、DeferredItem/DeferredBlock、ForgeRegistries/IForgeRegistry 删除、NeoForgeRegistries | 无（ref 未覆盖） | /news/20.2registry-rework/（20.2.59-beta）；20.2.93 类清单无 RegistryObject/ForgeRegistries |
| 事件总线 net.neoforged.bus、@Cancelable→ICancellableEvent、post 返回事件、@SubscribeEvent 新规则 | 无 | /news/20.2eventbus-changes/；bus 7.2.0 api 类清单无 Cancelable |
| @Mod 构造器注入 IEventBus/ModContainer/Dist | 无 | FML 1.0.16 源码 FMLModContainer（allowedConstructorArgs） |
| FMLJavaModLoadingContext 弃用（20.4 起 forRemoval） | 无 | FML 1.0.16 源码无弃用注解；FML 2.0.17/3.0.18 源码 `@Deprecated(forRemoval = true)` |
| DistExecutor：1.20.x 仍存在但已弃用 | 无 | FML 1.0.16–3.0.45 类清单均含 DistExecutor，源码标 forRemoval |
| capability 重写（20.3）：Capabilities 按宿主分组、BlockCapability/EntityCapability/ItemCapability、RegisterCapabilitiesEvent 新语义、LazyOptional/Capability/CapabilityManager/AttachCapabilitiesEvent 删除、数据附件 AttachmentType | 无 | /news/20.3capability-rework/；20.2.93 与 20.3.8-beta 类清单差异；20.4.251 源码（BlockCapability.createSided、ItemCapability.createVoid、ILevelExtension#getCapability、AttachmentType.builder/serializable） |
| 网络 20.4 重构：SimpleChannel/NetworkRegistry/NetworkHooks/NetworkEvent 删除，RegisterPayloadHandlerEvent、IPayloadRegistrar.play/configuration/common、PlayPayloadContext、PacketDistributor 仅发 payload、openMenu、IEntityAdditionalSpawnData→IEntityWithComplexSpawn、RegisterMenuScreensEvent | 无 | /news/20.4networking-rework/（20.4.70-beta 起）；20.3.8-beta 与 20.4.251 类清单差异；20.4.251 源码 IPayloadRegistrar、PacketDistributor、IPlayerExtension#openMenu |
| 20.2/20.3 网络沿用 SimpleChannel（newSimpleChannel、messageBuilder…consumerMainThread、NetworkEvent.Context、PacketDistributor.with(Supplier)） | 无 | 20.2.93 源码 |
| 20.5 网络再变：RegisterPayloadHandlersEvent（registrar(版本)）、PayloadRegistrar.playToClient/playToServer/playBidirectional、IPayloadContext 扁平化、PacketDistributor 静态 sendToX | `ApiChangeRule.java`、`1.20.4_to_1.20.5.md`（仅 FriendlyByteBuf 线索） | /news/20.5release/；20.5.21-beta 源码 PayloadRegistrar、PacketDistributor、IPayloadContext |
| TickEvent 拆分（20.5）：client.event.ClientTickEvent、event.tick.{Server,Level,Player,Entity}TickEvent 的 Pre/Post，RenderTickEvent→RenderFrameEvent，LivingTickEvent→EntityTickEvent | `1.20.4_to_1.20.5.md`、`ApiChangeRule.java`（TickEvent→ClientTickEvent） | 20.4.251 与 20.5.21-beta 类清单差异 |
| HUD（20.5）：RegisterGuiOverlaysEvent→RegisterGuiLayersEvent、RenderGuiOverlayEvent→RenderGuiLayerEvent、VanillaGuiOverlay→VanillaGuiLayers、IGuiOverlay→LayeredDraw.Layer(GuiGraphics,float) | `1.20.4_to_1.20.5.md`、`ApiChangeRule.java`（NeoForge HUD） | /news/20.5release/；20.5.21-beta 源码 RegisterGuiLayersEvent、RenderGuiLayerEvent；Mojang 1.20.5 映射 LayeredDraw$Layer.render(GuiGraphics,float)；20.4.251 源码 IGuiOverlay.render(ExtendedGui,…) |
| @Mod.EventBusSubscriber→顶层 @EventBusSubscriber、Bus.FORGE→Bus.GAME（20.5） | 无 | /news/20.5release/；FML 3.0.18 源码 EventBusSubscriber |
| 配置注册改到 ModContainer#registerConfig（20.5） | 无 | FML 3.0.18 源码（ModLoadingContext#registerConfig forRemoval） |
| ConfigScreenHandler→IConfigScreenFactory、TierSortingRegistry 删除、NBTIngredient→DataComponentIngredient、INBTSerializable 带 Provider、IEntityWithComplexSpawn 用 RegistryFriendlyByteBuf（20.5） | 无 | 20.4.251 与 20.5.21-beta 类清单差异及源码 |
| 通用标签 forge: → c:（20.5） | 无 | /news/20.5release/；20.4.251 / 20.5.21-beta 源码 Tags |
| 生物群系修改器目录 data/<ns>/neoforge/biome_modifier | 无 | 20.4.251 源码 NeoForgeRegistries.Keys（neoforge 命名空间） |
| 全局战利品修改器登记文件 data/neoforge/loot_modifiers/global_loot_modifiers.json | 无 | 20.4.251 源码 LootModifierManager |
| 20.6：bus 8 删除 Event.Result/HasResult/GenericEvent；EntityItemPickupEvent→ItemEntityPickupEvent.Pre/Post；PlayerSleepInBedEvent→CanPlayerSleepEvent | 无 | bus 8.0.1 类清单；20.5.21-beta 与 20.6.141 类清单差异及源码 |
| NeoForge 1.20.1 是否支持 | `knowledge-base/loaders/neoforge/versions.md`、template-info 1.20.1 | /news/2024-retrospection/（官方已放弃 1.20.1 分支）；neoforged/MDK 1.20.1-legacy 现面向 MinecraftForge；net.neoforged:forge 1.20.1-47.1.106 仍在 Maven |

## 3. 原版层（mc.*）

| 条目 | ref 线索 | 核实依据 |
|---|---|---|
| PlayerSkin（1.20.2）、getSkinTextureLocation→getSkin().texture() | `1.20.1_to_1.20.2.md`、`ApiChangeRule.java` | Mojang 1.20.1/1.20.2 client_mappings |
| Screen#renderBackground 签名（1.20.2） | 无 | Mojang 1.20.1/1.20.2 映射 |
| Block codec() / simpleCodec、Properties.copy→ofFullCopy/ofLegacyCopy（1.20.3） | 无 | Mojang 1.20.2/1.20.4 映射 |
| CustomPacketPayload（1.20.2） | 无 | Mojang 1.20.1/1.20.2 映射 |
| 数据组件（DataComponents/DataComponentType/CustomData/PotionContents）、ItemStack NBT API 删除、isSameItemSameTags→isSameItemSameComponents、save(Provider)/parseOptional、setHoverName 删除、hurtAndBreak 新签名、BlockEntity loadAdditional/saveAdditional/getUpdateTag 带 Provider、MobEffect Holder、appendHoverText(TooltipContext)、Block#use→useWithoutItem/useItemOn、defineSynchedData(Builder)、FoodProperties 记录化与 Builder 改名、Enchantment(EnchantmentDefinition)、ArmorMaterial 注册表化、Tier#getIncorrectBlocksForDrops、SwordItem.createAttributes、EntityType#spawn Consumer（1.20.5） | `1.20.4_to_1.20.5.md`（仅网络部分） | Mojang 1.20.4/1.20.5 映射；/news/20.5release/ |
| RegistryFriendlyByteBuf(ByteBuf, RegistryAccess)、StreamCodec/ByteBufCodecs（1.20.5） | `1.20.4_to_1.20.5.md`、`patterns/*.md`、`ApiChangeRule.java` | Mojang 1.20.5 映射；/news/20.5release/ |
| Java 21（1.20.5） | `ref/java/java21.md` | Mojang 版本清单 javaVersion.majorVersion |
| 1.20.3 与 1.20.4 原版一致；1.20.5 与 1.20.6 原版差异不影响现有 IR | `1.20.3_to_1.20.4.md`、`1.20.5_to_1.20.6.md` | 映射文件比对 |

## 4. 被修正或丢弃的 ref 条目

1. **「1.20.1→1.20.2 把 net.minecraftforge 整体替换为 net.neoforged、把所有 minecraftforge 字符串替换为 neoforged」**（`1.20.1_to_1.20.2.md`、`ApiChangeRule.java`）——修正：实际目标包分为 net.neoforged.neoforge.*（NeoForge 本体）、net.neoforged.fml.*、net.neoforged.bus.*、net.neoforged.api.distmarker.*，另有十余个类同时改名；mods.toml 的依赖 modId 是 `neoforge` 而非 `neoforged`。已按类逐一映射。
2. **「NeoForge 从 1.20.2 开始分叉」「20.2.59-beta 是第一个 NeoForge 版本」**（`loaders/neoforge/versions.md`、`1.20.1_to_1.20.2.md`）——修正：分叉始于 1.20.1（net.neoforged:forge 47.1.x）；20.2 的首个版本是 20.2.3-beta，20.2.59-beta 是注册表重写版本。
3. **「FriendlyByteBuf 在 1.20.5 改名为 RegistryFriendlyByteBuf」**（`1.20.4_to_1.20.5.md`、`patterns/class-moves.md`、`method-renames.md`、`ApiChangeRule.java`）——修正：FriendlyByteBuf 仍存在，RegistryFriendlyByteBuf 是新增子类；作为两个独立 IR（mc.network.FriendlyByteBuf / mc.network.RegistryFriendlyByteBuf）写入，不做改名映射。
4. **「PacketByteBuf → RegistryFriendlyByteBuf」**——丢弃：PacketByteBuf 是 Yarn 名，与 Mojang 名混用，不属于版本间改名。
5. **「TickEvent → client.event.ClientTickEvent」**（`ApiChangeRule.java`）——修正：只有客户端 tick 移到 client.event；服务端/世界/玩家/实体 tick 在 net.neoforged.neoforge.event.tick 包，且都拆为 Pre/Post。
6. **ref 模板中的 NeoForge 版本号**（20.2.59-beta、20.4.80-beta、20.6.99-beta）——在 Maven 中存在，但改用各系列最新构建（20.2.93、20.4.251、20.6.141，与官方 MDK 一致）；20.3.8-beta、20.5.21-beta 与 ref 相同（两系列均为最后构建）。
7. `patterns/class-moves.md` 的 `client.gui.screen.Screen → screens.Screen` 与 DrawableHelper/MatrixStack 条目——丢弃：Yarn 名与 Mojang 名的差异，且不在本范围。

## 5. 未能核实、暂未写入 / 仅标注的内容

- **1.20.3 的构建插件**：官方没有 1.20.3 MDK（20.3 发布文章已建议迁往 20.4）；模板沿用 1.20.2 MDK 的 NeoGradle 7.0.116 与 Gradle 8.14.5，未在官方材料中逐字核实其对 20.3.8-beta 的兼容性。
- **NeoForge 1.20.1 的构建模板**：原 NeoForged 1.20.1 MDK 已被 MinecraftForge 版本替换，无法取得原文；插件 `net.neoforged.gradle` 6.0.21 与 `net.neoforged:forge:1.20.1-47.1.106`（含 userdev jar）在 Maven 中核实存在，Gradle 8.8 与 FG6 式 DSL 的搭配未能在官方 MDK 中核实。
- 20.3.8-beta 的网络 API 按 20.2.93 源码与 20.3/20.4 类清单差异推断未变（未逐文件比对 20.3 源码）。
- 未为 Fabric 专属类 IR（fabric.ModInitializer 等）编写 NeoForge 侧 guidance；未把全部 NeoForge 类建成 IR，只覆盖 Forge 1.20.1 IR、常用加载器类与 1.20.x 内发生变化的类。

## 汇总步骤补写的 guidance（2026-10）

- 为 1.20.1、1.20.2（覆盖）、1.20.4、1.20.5（覆盖）补齐跨仓库 concept 与本 loader 内缺失类 IR 的反向 guidance；
  1.21.1 补 `logging.slf4j`。文字基于本仓库已核实的数据：neoforge/1.21.1 的同名说明（替换版本前缀）、本文件第 51 行登记的
  20.3 能力/数据附件重写、1.20.5 classes.json 中 `StreamCodec`、`DataComponentIngredient` 等首次出现的版本。
- 待核实：1.20.1 官方名中 `LighthingBoltPredicate` 的拼写（guidance 已按此写，未对 1.20.1 client_mappings 复核）；
  `GameTypePredicate` / `MovementPredicate` 的确切引入版本（只依据本仓库 1.20.6 无、1.21.1 有）。
