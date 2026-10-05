# 来源登记：NeoForge 1.21 – 1.21.11、26.1 – 26.2

范围内数据集：`versions/neoforge/1.21.1`（全量，别名 1.21）及覆盖层
`1.21.2`（别名 1.21.3）、`1.21.4`、`1.21.5`、`1.21.6`、`1.21.7`（别名 1.21.8）、`1.21.9`、`1.21.10`、
`1.21.11`、`26.1`（别名 26.1.1）、`26.1.2`、`26.2`。

线索来源（参考仓库 reqsery/mc-mod-porter，只取事实、未复制任何文字）记为 **ref:文件**；
核实用一手来源列在每组之后。下文「官方映射」指 Mojang 版本清单中各版本 `client_mappings`
（1.21.x）或 26.x 官方未混淆客户端 jar 的类/成员表；「NeoForge jar」指 NeoForged Maven 上的
`neoforge-<ver>-universal.jar`、`fancymodloader/loader`、`bus`、`mergetool:api` 构件中的类/方法表。
所有类名、成员名均逐版本用上述构件机器比对确认存在。

## 通用一手来源

- Mojang 版本清单：https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
  （各版本 JSON 的 `client_mappings`、`javaVersion`；客户端 jar 内 `version.json` 的 `pack_version`）
- NeoForge Maven 元数据：https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
- NeoForge 构件：https://maven.neoforged.net/releases/net/neoforged/neoforge/<ver>/（`-universal.jar`、`.pom`）
- FML：https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/maven-metadata.xml
- 事件总线：https://maven.neoforged.net/releases/net/neoforged/bus/8.0.5/
- Dist/OnlyIn：https://maven.neoforged.net/releases/net/neoforged/mergetool/2.0.0/ 与 2.0.7/（`-api.jar`）
- ModDevGradle：https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml
- 官方 MDK：https://github.com/NeoForgeMDKs/MDK-<mc>-ModDevGradle （`gradle.properties`、`build.gradle`、
  `settings.gradle`、`gradle/wrapper/gradle-wrapper.properties`、`src/main/templates/META-INF/neoforge.mods.toml`）
- Gradle 发行版：https://services.gradle.org/distributions/gradle-9.2.1-bin.zip
- NeoForged 移植指南（primers）：https://github.com/neoforged/.github/tree/main/primers/<mc>
- NeoForged 新闻：https://neoforged.net/news/21.0release/ 、/21.2release/ 、/21.4release/ 、/21.5release/ 、
  /21.6release/ 、/21.9release/ 、/21.9-transfer-rework/ 、/21.11release/ 、/26.1release/

## version.json 版本号（全部来自 Maven 元数据 + MDK + Mojang 清单）

| MC | NeoForge | FML（pom 依赖）→ loaderVersionRange | 资源包格式（jar 内 pack_version） | Java |
|---|---|---|---|---|
| 1.21 | 21.0.167 | 4.0.23 → [4,) | 34 | 21 |
| 1.21.1 | 21.1.252 | 4.0.44 → [4,) | 34 | 21 |
| 1.21.2 | 21.2.1-beta | 4.0.29 → [4,) | 42 | 21 |
| 1.21.3 | 21.3.97 | 5.0.8 → [5,) | 42 | 21 |
| 1.21.4 | 21.4.158 | 6.0.18 → [6,) | 46 | 21 |
| 1.21.5 | 21.5.98 | 7.0.13 → [7,) | 55 | 21 |
| 1.21.6 | 21.6.20-beta | 9.0.2 → [9,) | 63 | 21 |
| 1.21.7 | 21.7.25-beta | 9.0.14 → [9,) | 64 | 21 |
| 1.21.8 | 21.8.54 | 9.0.18 → [9,) | 64 | 21 |
| 1.21.9 | 21.9.16-beta | 10.0.14 → [10,) | 69.0（主.次） | 21 |
| 1.21.10 | 21.10.64 | 10.0.32 → [10,) | 69.0 | 21 |
| 1.21.11 | 21.11.45 | 10.0.36 → [10,) | 75.0 | 21 |
| 26.1 | 26.1.0.19-beta | 11.0.5 → [11,) | 84.0 | 25 |
| 26.1.1 | 26.1.1.15-beta | 11.0.5 → [11,) | 84.0 | 25 |
| 26.1.2 | 26.1.2.112 | 11.0.15 → [11,) | 84.0 | 25 |
| 26.2 | 26.2.0.88 | 11.0.16 → [11,) | 88.0 | 25 |

- ModDevGradle 2.0.148、Gradle 9.2.1：所有上述 MDK 统一使用；2.0.148 为 Maven 最新 release。
- 模板：1.21.1 起 `data()` 运行；1.21.4 起 MDK 改为 `clientData()`（亦见 21.4 新闻）；1.21.5 起 MDK 的
  neoforge.mods.toml 不再写 `modLoader`/`loaderVersion`；26.x MDK 去掉 parchment（本模板本就未用）。
- 线索：ref:knowledge-base/loaders/neoforge/versions.md、ref:auto-porter/templates/<ver>/{template-info.json,gradle.properties}。

## 1.21 / 1.21.1（全量基准）

- 加载器类（forge.* 复用）：Mod、EventBusSubscriber（顶层类）、SubscribeEvent/Event/EventPriority/ICancellableEvent（bus）、
  NeoForge.EVENT_BUS、Dist/OnlyIn、DeferredRegister/DeferredHolder/DeferredItem/DeferredBlock、ModConfigSpec、ModContainer 注入、
  Capabilities/ICapabilityProvider/RegisterCapabilitiesEvent、tick 事件 Pre/Post、RenderGuiLayerEvent/RegisterGuiLayersEvent/VanillaGuiLayers、
  RegisterPayloadHandlersEvent/PayloadRegistrar/PacketDistributor、AttachmentType、LivingIncomingDamageEvent/LivingDamageEvent、
  FMLJavaModLoadingContext 与 DistExecutor 已不存在。
  核实：NeoForge jar 21.1.252；/news/21.0release/。线索：ref:knowledge-base/minecraft/1.20.4_to_1.20.5.md、1.20.1_to_1.20.2.md、
  ref:ApiChangeRule（RegisterGuiOverlaysEvent→RegisterGuiLayersEvent、TickEvent→ClientTickEvent）。
- 原版：ResourceLocation 工厂方法、数据组件、Holder 化效果/附魔、DeltaTracker、PlayerSkin（client.resources）。
  核实：官方映射 1.21 / 1.21.1；/news/21.0release/。线索：ref:1.20.6_to_1.21.md。
- 与 NeoForge 1.20.x 数据集对齐的 id（由该数据集首建，本数据集沿用并核实 1.21.1 中存在）。

## 1.21.2（别名 1.21.3）

- Item.Properties/BlockBehaviour.Properties#setId、useBlockDescriptionPrefix；BlockEntityType.Builder 移除；
  EntityType.Builder#build(ResourceKey)；InteractionResult 密封化（SUCCESS_SERVER、TRY_WITH_EMPTY_HAND、heldItemTransformedTo），
  InteractionResultHolder / ItemInteractionResult 移除；Tier/Tiers/TieredItem → ToolMaterial；ArmorMaterial/ArmorMaterials/ArmorType 移入
  equipment 包（常量 CHAINMAIL/ARMADILLO_SCUTE/TURTLE_SCUTE）；Sword/Digger/ArmorItem 构造签名；EntityRenderState；
  GuiGraphics#blit 首参 Function<ResourceLocation, RenderType>；Entity#hurtServer/hurtOrSimulate、kill(ServerLevel)、
  changeDimension→teleport(TeleportTransition)；RegistryAccess#registryOrThrow→lookupOrThrow 等 Registry 改名；
  Level#getGameRules 仅在 ServerLevel；Consumable 组件；DeferredSpawnEggItem 在 21.3 移除。
  核实：官方映射 1.21.1/1.21.2；NeoForge jar 21.2.1-beta/21.3.97；/news/21.2release/；primers/1.21.2。
  线索：ref:1.21.1_to_1.21.2.md（ref 称「无破坏性变化」，与事实不符，已按一手来源补全）。

## 1.21.4

- 客户端物品定义 assets/<ns>/items/*.json；ItemModelProvider/BlockStateProvider 移除（改原版 ModelProvider）；
  RegisterItemModelsEvent；GatherDataEvent.Client/Server；AddReloadListenerEvent→AddServerReloadListenersEvent；
  RegisterClientReloadListenersEvent→AddClientReloadListenersEvent；BlockModelGenerators（id 于本版新建）。
  核实：NeoForge jar 21.3.97 vs 21.4.158 类表；/news/21.4release/；primers/1.21.4；MDK 1.21.4 build.gradle。
  线索：ref:1.21.3_to_1.21.4.md（ref 称无变化，已补全）。

## 1.21.5

- SwordItem/DiggerItem/ArmorItem 移除 → Item.Properties#sword/pickaxe/axe/shovel/hoe/humanoidArmor；CompoundTag 取值 Optional 化、
  get*Or、putUUID/getUUID 移除、store/read；ClickEvent/HoverEvent record 化；appendHoverText/inventoryTick 新签名；
  BlockEntityRenderer#render 新增 Vec3；RegisterShadersEvent 移除（RenderPipeline）。
  核实：官方映射 1.21.4/1.21.5；NeoForge jar 21.4.158/21.5.98；/news/21.5release/；primers/1.21.5。
  线索：ref:1.21.4_to_1.21.5.md（ref 称无变化，已补全）。

## 1.21.6

- ValueInput/ValueOutput 与 BlockEntity#loadAdditional/saveAdditional 新签名；INBTSerializable→ValueIOSerializable，
  ItemStackHandler/EnergyStorage/FluidTank 的 serialize/deserialize；GUI：RenderPipeline 首参、Matrix3x2fStack、
  setTooltipForNextFrame、LayeredDraw 移除（NeoForge GuiLayer）；ServerPlayer.server 字段私有（字节码访问标志核对）、
  serverLevel() 移除、Entity#getCommandSenderWorld 移除、ItemStack#save/parse 移除；EventBusSubscriber 去掉 bus；
  RenderLevelStageEvent 拆为 AfterXxx 子事件；contextualbar 包（id 于本版新建）。
  核实：官方映射 1.21.5/1.21.6、客户端 jar 字段访问标志；NeoForge jar 21.5.98/21.6.20-beta；/news/21.6release/；primers/1.21.6。
  线索：ref:1.21.5_to_1.21.6.md、ref:ApiChangeRule（1.21.5→1.21.6 规则）。

## 1.21.7（别名 1.21.8）

- PacketDistributor#sendToServer 移到 ClientPacketDistributor。核实：NeoForge jar 21.6.20-beta/21.7.25-beta/21.8.54。
  线索：无（ref 未覆盖，一手补充）。

## 1.21.9

- 输入：KeyEvent/MouseButtonEvent/CharacterEvent 与 Screen/GuiEventListener 回调新签名；KeyMapping.Category；
  PlayerSkin 移至 world.entity.player、texture()→body().texturePath()；Level.isClientSide 字段私有（字节码核对）；
  Entity#getServer 移除；BlockEntityRenderer/EntityRenderer#submit + 渲染状态；FMLEnvironment.dist/production→getDist()/isProduction()；
  转移重构（ResourceHandler/ItemResource/Transaction/EnergyHandler，Capabilities.Item/Fluid/Energy）；RenderHighlightEvent 移除；
  PlayerSpawnFinder（id 于本版新建）。
  核实：官方映射 1.21.8/1.21.9、客户端 jar 字段访问标志；NeoForge/FML jar 21.8.54 与 21.9.16-beta（FML 9.0.18 vs 10.0.14）；
  /news/21.9release/、/news/21.9-transfer-rework/；primers/1.21.9。
  线索：ref:1.21.8_to_1.21.9.md、1.21.9_to_1.21.10.md、ref:patterns/{class-moves,method-renames,signatures}.md、ref:ApiChangeRule。

## 1.21.10

- NeoForge 移除 CustomizeGuiOverlayEvent.DebugText、ContainerScreenEvent.Render.Background（仅 guidance）。
  核实：NeoForge jar 21.9.16-beta/21.10.64；官方映射 1.21.9 与 1.21.10 类/方法表一致。线索：ref:1.21.9_to_1.21.10.md（多为 Fabric/Yarn 内容，已丢弃）。

## 1.21.11

- ResourceLocation→Identifier（含惯用法 forms）；Util→net.minecraft.util.Util；RenderType→rendertype 包 + RenderTypes；
  GameRules→gamerules 包；advancements.critereon→criterion；命令权限 PermissionSet（Commands.hasPermission(PermissionCheck)）；
  RegisterDimensionSpecialEffectsEvent 移除。
  核实：官方映射 1.21.10/1.21.11；NeoForge jar 21.10.64/21.11.45；/news/21.11release/；primers/1.21.11。
  线索：ref:1.21.10_to_1.21.11.md、ref:ApiChangeRule。

## 26.1（别名 26.1.1）

- Java 25；GuiGraphics→GuiGraphicsExtractor 及其方法改名（按描述符与命名规律逐一比对）；Screen/AbstractContainerScreen/AbstractWidget
  的 render*→extract*；ChunkPos 构造器移除（containing/unpack）、asLong/toLong→pack；Level.random 改 protected（字节码核对）、
  getDayTime→世界时钟；Player#displayClientMessage 移除（sendOverlayMessage）；ItemStackTemplate；ItemBlockRenderTypes 移除；
  VillagerTradesEvent/WandererTradesEvent 移除（交易数据包化）；FluidStack 变化（FluidStackTemplate、typeHolder）；
  ServerboundSpectateEntityPacket（id 于本版新建）。
  核实：26.1 官方客户端 jar；NeoForge jar 21.11.45/26.1.0.19-beta；/news/26.1release/；primers/26.1。
  线索：ref:1.21.11_to_26.1.md、ref:MixinTargetResolver（全部 6 条 26.1 规则均已核实）、ref:ApiChangeRule、ref:java/java25.md。

## 26.1.2

- BlockEvent.BreakEvent → event.level.block.BreakBlockEvent。核实：NeoForge jar 26.1.1.15-beta/26.1.2.112；官方 jar 26.1.1/26.1.2 一致。
  线索：ref:26.1.1_to_26.1.2.md（ref 称无变化；NeoForge 侧有变化，已补充）。

## 26.2

- Minecraft→Gui 的界面管理迁移、Gui→Hud；Slime/MagmaCube→monster.cubemob；Bucketable→world.entity；Dripstone*→Speleothem*；
  InstantenousMobEffect 拼写修正；ColorArgument→TeamColorArgument；ServerboundSpectateEntityPacket→ServerboundSpectatorActionPacket；
  contextualbar *Renderer→*；进度谓词/触发器移至 predicates / predicates.entity / triggers（SlimePredicate→CubeMobPredicate）；
  各成员/常量改名（markPosForPostProcessing、getLightDampeningInto、createTestWorldDimensions、getLevelRespawnPos、
  applyInstantaneousEffect/isInstantaneous、createForGrayscaleTexture、TEXT_GRAYSCALE*、SPELEOTHEM*、CAN_PLACE_BELOW_TREE_TRUNKS、
  SERVERBOUND_SPECTATOR_ACTION、handleSpectatorAction、createSpeleothem*）；ChunkPos.MAX_COORDINATE_VALUE→ChunkPyramid；
  BlockIds/BlockItemIds/ItemIds（guidance）；NeoForge 移除 ContainerScreenEvent、PlayerInteractEvent.EntityInteractSpecific。
  核实：官方 jar 26.1.2/26.2；NeoForge jar 26.1.2.112/26.2.0.88；primers/26.2。
  线索：ref:26.1.2_to_26.2.md、ref:ApiChangeRule（26.1.2→26.2 全部规则）。

## 被修正或丢弃的 ref 事实

1. NeoForge 版本号：21.0.168-beta、21.2.38-beta、21.6.25-beta、21.8.15-beta 在 Maven 上不存在（分别改用 21.0.167、21.2.1-beta、
   21.6.20-beta、21.8.54）；26.1.1 标记为无 NeoForge 不符（26.1.1.x 存在）；其余旧版本号更新为 MDK 当前值。
2. FMLEnvironment：ref 的 1.21.8→1.21.9 知识文件把方向写反（称 getDist() 变 dist），versions.md 称发生于 21.10、规则注释称 21.4——
   实际为 FML 10（NeoForge 21.9）起 dist/production 字段变为 getDist()/isProduction()。
3. FriendlyByteBuf→RegistryFriendlyByteBuf「改名」：丢弃，二者并存（后者是子类）；PacketByteBuf 为 Yarn 名，丢弃。
4. KeyMapping.Category、KeyEvent/MouseButtonEvent、PlayerSkin 迁移：ref 标注为 1.21.10 或「NeoForge 1.21.9 / Fabric 1.21.10」，
   实际均为原版 1.21.9 的变化。
5. 1.21.6 blit：ref 称去掉 RenderType 参数改用小数 UV；实际主形式是首参 RenderPipeline（RenderPipelines.GUI_TEXTURED），
   小数 UV 只是另一个重载；RenderType 函数首参是 1.21.2 引入而非 1.21.4。
6. ServerPlayer.server：私有化时间（1.21.6）属实，但替代写法在 1.21.6–1.21.8 为 getServer()，1.21.9 起才需 level().getServer()（无需强转）。
7. 26.2 Overlay#isPauseScreen→isPausing：ref 的全局文本替换会误伤仍保留 isPauseScreen 的 Screen，未写入自动映射。
8. 26.2 进度谓词旧路径：ref 写作 net.minecraft.advancements.X，实际旧位置是 advancements.criterion.X（1.21.11 起；更早为 critereon）。
9. 26.2 Bucketable 旧路径应为 world.entity.animal.Bucketable；ChunkPyramid 实际在 world.level.chunk.status 包。
10. 1.21.2/1.21.3/1.21.4/1.21.5 与 26.1.2 被 ref 标为「无 API 变化」，与一手来源不符，已补全。
11. Yarn / Fabric 专属条目（Entity#getWorld、MinecraftClient.IS_SYSTEM_MAC、Identifier.of、HudRenderCallback、ItemGroupEvents、
    ColorProviderRegistry、BlockRenderLayerMap、Loom 配置等）不属于 NeoForge 范围，丢弃。
12. ref:java/java25.md 称 Java 25 需 Gradle 9.4.0+：NeoForge 26.1 新闻要求 9.1.0+，官方 MDK 使用 9.2.1，按一手来源写 9.2.1；
    其「Java 21 编译的模组不能在 Java 25 上运行」与 JVM 向后兼容事实不符，丢弃（26.x 需重编译是因为 MC 本身变化）。
13. ref:MixinTargetResolver 中「推断」的 renderSlot→extractSlot、renderItem→item 已用 26.1 jar 核实属实；
    但 renderItem→item 目标名过于通用，只以 note 提示、不自动改名。

## 未能核实 / 未写入的条目

- 1.21.11 实体与模型类的大规模子包重排、critereon 包中 ref 未列出的其余类：未逐一建 id（仅在 guidance 中说明）。
- 26.2 移除 valueLookupBuilder、原版 BlockIds/ItemIds 拆分的具体用法：只写 guidance，未做成员映射。
- Registry#get/getHolder/getOrThrow 等 1.21.2 改名：名称过于通用，按「不猜」原则只写 guidance。
- Screen#render→extractRenderState、GuiGraphics#drawString→text、renderItem→item、renderTooltip→tooltip：只以 note 提示。
- DeferredSpawnEggItem：21.2.1-beta 中仍存在、21.3 移除，因 1.21.3 为别名共享数据，按 21.3 状态写入 1.21.2 覆盖层。
