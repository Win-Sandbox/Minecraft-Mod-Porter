# 来源登记：Fabric 26.1 / 26.1.1 / 26.1.2 / 26.2（官方名）与 Java 25 平台数据

范围：`versions/fabric/26.1/`（full，含别名 26.1.1、26.1.2）、`versions/fabric/26.2/`（overlay，basedOn 26.1）、
`java/25.json`、`java/features.json`（仅新增条目）。

线索来源（只取事实，未复制任何文字/代码）：参考仓库 reqsery/mc-mod-porter（下称 ref）。
核实方式以一手来源为准，按优先级：

1. **Mojang 官方二进制**：版本清单 `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`，
   各版本 JSON 中的 `client.jar`（26.1 / 26.1.2 / 26.2，未混淆）与 `client_mappings`（1.21.1、1.21.2、1.21.4、1.21.5、
   1.21.6、1.21.9、1.21.10、1.21.11 的官方 ProGuard 映射）。在本地临时目录用脚本解析 class 文件常量池与映射文本，
   逐一比对类是否存在、成员名/描述符/访问标志（未编译、未运行任何 Java 程序）。
2. **FabricMC 官方**：博客、docs、官方 Maven 元数据、meta API、GitHub 上游源码（FabricMC/fabric 各分支、
   fabric-loader、fabric-example-mod、fabric-docs 中的官方迁移映射表）。
3. **NeoForged 官方新闻**（交叉核对 26.1 GUI 改名与 ChunkPos 变化）。
4. **OpenJDK / Java SE 规范**（JEP 页面、Java SE 22–25 平台规范的「APIs removed / proposed for removal」章节）。

---

## 1. version.json（26.1 / 26.1.1 / 26.1.2 / 26.2）

| 条目 | 取值 | 线索 | 核实 |
|---|---|---|---|
| Java 版本 | 25 | ref `knowledge-base/minecraft/1.21.11_to_26.1.md`、`java/java25.md` | Mojang 版本 JSON `javaVersion.majorVersion = 25`（26.1、26.1.1、26.1.2、26.2 均是），client.jar 内 `version.json` 的 `java_version: 25` |
| packFormat | 26.1.x = 84，26.2 = 88 | ref `26.1.2_to_26.2.md`（88.0） | 26.1 / 26.1.2 / 26.2 client.jar 内 `version.json` 的 `pack_version.resource_major`（84 / 84 / 88；数据包 101.1 / 107.1） |
| Fabric Loader | 26.1.x：0.18.4；26.2：0.19.3 | ref `templates/26.1/template-info.json`（0.18.4）、`templates/26.2`（0.19.3） | https://fabricmc.net/2026/03/14/261.html（26.1 最新稳定 0.18.4）、https://fabricmc.net/2026/06/15/262.html（0.19.3）；`https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml` 两者均存在 |
| loaderVersionRange | `>=0.18.4` | — | FabricMC/fabric 标签 `0.145.1+26.1`、`0.145.4+26.1.1`、`0.155.3+26.1.2`、`0.161.0+26.2` 的 gradle.properties 均为 `loader_version=0.18.4`（Fabric API 自身要求） |
| Fabric API | 26.1 = 0.145.1+26.1；26.1.1 = 0.145.4+26.1.1；26.1.2 = 0.155.3+26.1.2；26.2 = 0.161.0+26.2 | ref templates（0.145.0+26.1、0.145.3+26.1.1、0.154.2+26.1.2、0.154.2+26.2） | `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`：取各 MC 版本的最新构建 |
| Loom | 26.1.x：1.15-SNAPSHOT；26.2：1.17-SNAPSHOT | ref 26.1 模板 `1.15`、26.2 模板 `1.17.13` | 26.1 博客「Loom 1.15」，26.2 博客「Loom 1.17」；`https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml` 中 1.15-SNAPSHOT、1.17-SNAPSHOT 均存在 |
| Gradle | 26.1.x：9.4.0；26.2：9.5.1 | ref `1.21.11_to_26.1.md` | 26.1 / 26.2 Fabric 博客；`https://services.gradle.org/distributions/gradle-9.4.0-bin.zip`、`gradle-9.5.1-bin.zip` 返回 200 |
| mappingsChannel | `official` | FABRIC-IR-CONTRACT 第 0 节 | 26.1 博客（不再混淆、Yarn 不再提供）；`https://meta.fabricmc.net/v2/versions/yarn/26.1` 返回空列表 |
| 26.1.1 / 26.1.2 做 alias | — | ref `26.1_to_26.1.1.md`、`26.1.1_to_26.1.2.md`（热修复） | 解析 26.1 与 26.1.2 client.jar：类集合与全部 public 成员签名完全一致（差异 0） |

## 2. templates/

| 条目 | 线索 | 核实 |
|---|---|---|
| 插件 id `net.fabricmc.fabric-loom`、删除 `mappings` 行、`modImplementation` -> `implementation`、`modCompileOnly` -> `compileOnly`、`remapJar` -> `jar` | ref `1.21.11_to_26.1.md` | 26.1 博客 Build Script 段；FabricMC/fabric 26.1 分支根 build.gradle（`id "net.fabricmc.fabric-loom"`） |
| fabric.mod.json 结构 / mixin `compatibilityLevel: JAVA_25` | — | https://github.com/FabricMC/fabric-example-mod （当前 HEAD 的 fabric.mod.json 与 modid.mixins.json） |

## 3. classes.json：原版类（mc.*）

逐一检查 Forge 1.20.1 classes.json 中全部 mc.* id 的官方 FQCN 是否存在于 26.1 / 26.2 client.jar：

- 仅两处类级变化：`net.minecraft.resources.ResourceLocation` 不存在（1.21.11 起为 `net.minecraft.resources.Identifier`，映射 1.21.10 中无 Identifier、1.21.11 中有）；`net.minecraft.world.InteractionResultHolder` 不存在（1.21.2 映射中已无）。
  - 线索：ref `ApiChangeRule.java`（1.21.10 -> 1.21.11 ResourceLocation -> Identifier）。
- 新增 IR id `mc.client.gui.GuiGraphics`（按 DATA-RULES 第 1 条由官方名推导）：1.21.11 映射有 `net.minecraft.client.gui.GuiGraphics`，26.1 jar 仅有 `GuiGraphicsExtractor`。
  - 线索：ref `1.21.11_to_26.1.md`、`MixinTargetResolver.java`；交叉核实：https://neoforged.net/news/26.1release/
- 新增 IR id `mc.world.entity.EntityTypes`（仅 26.2）：26.2 jar 新增该类，EntityType 上的实体常量全部移走（jar 比对）。
- 各 note 中的版本号与签名全部来自 jar/映射比对，例如：ArmorItem/SwordItem 在 1.21.4 映射存在、1.21.5 不存在；ValueInput/ChunkSectionLayer 在 1.21.6 首次出现；KeyEvent、KeyMapping$Category、BlockEntityRenderState 在 1.21.9 首次出现；ClickEvent$OpenUrl、TooltipDisplay 在 1.21.5 首次出现。

## 4. classes.json / members.json：Fabric API（fabric.*）

| 条目 | 线索 | 核实 |
|---|---|---|
| ItemGroupEvents -> creativetab.v1.CreativeModeTabEvents；FabricItemGroup -> FabricCreativeModeTab；FabricItemGroupEntries -> FabricCreativeModeTabOutput；KeyBindingHelper -> keymapping.v1.KeyMappingHelper；PacketByteBufs -> FriendlyByteBufs；ExtendedScreenHandlerFactory -> menu.v1.ExtendedMenuProvider；FabricDataOutput -> FabricPackOutput；WorldRenderEvents -> level.LevelRenderEvents；以及 removed.json 中提到的其它改名 | ref `ApiChangeRule.java`（仅 ItemGroupEvents、ColorProviderRegistry、HudRenderCallback 三项） | 官方迁移映射表 https://github.com/FabricMC/fabric-docs/blob/main/public/assets/develop/porting/fabric-api-26-1-migration-map.xml ；并对 FabricMC/fabric 分支 `1.21.11`、`26.1`、`26.2` 的源码树逐类比对 |
| 成员改名：modifyEntriesEvent -> modifyOutputEvent，MODIFY_ENTRIES_ALL -> MODIFY_OUTPUT_ALL，registerKeyBinding -> registerKeyMapping，START/END_WORLD_TICK -> START/END_LEVEL_TICK（Server/ClientTickEvents），FabricRegistryBuilder.createSimple -> create，createS2CPacket -> createClientboundPacket，createC2SPacket -> createServerboundPacket | 无（ref 未覆盖） | 比对上述两个分支中对应源文件的 public 成员 |
| HudRenderCallback、BlockRenderLayerMap、ColorProviderRegistry、TradeOfferHelper、loot v2、convention-tags v1 在 26.1 删除；FabricEntityTypeBuilder 在 26.2 删除 | ref `1.21.11_to_26.1.md` | 26.1 博客；源码树比对 |
| ColorProviderRegistry -> BlockColorRegistry（仅方块，register(List<BlockTintSource>, Block...)） | ref | 26.1 博客；26.1 分支 BlockColorRegistry.java |
| HudElement 方法 extractRenderState(GuiGraphicsExtractor, DeltaTracker)、ExtendedMenuProvider#getScreenOpeningData(ServerPlayer)、LootTableEvents v3 回调签名、CommandRegistrationCallback 签名、UseItemCallback 返回 InteractionResult | — | 26.1 分支对应源文件 |
| MenuScreens.register 由 Fabric API 传递式放宽访问；访问放宽文件在 26.1 称 classtweaker、头部命名空间 official | — | 26.1 分支 `fabric-transitive-access-wideners-v1/.../*.classtweaker`、`fabric-menu-api-v1/.../*.classtweaker` |
| 入口类 net.fabricmc.api.*、FabricLoader、ModContainer 未变 | — | https://github.com/FabricMC/fabric-loader 源码树 |

## 5. members.json：原版成员（26.1 相对 IR 规范名）

全部由「Forge/Fabric 现有数据集中出现过的 IR 成员名」对 26.1 jar（含父类/接口链）逐一检索得出，并用 1.21.x 各映射确定引入/移除版本：

| 变化 | 线索 | 核实 |
|---|---|---|
| Level#isClientSide 字段私有化、只剩方法 | — | 26.1 jar 访问标志（字段 private final，方法 public） |
| Level#getDayTime 移除（世界时钟） | — | 1.21.10/1.21.11 映射有、26.1 jar 无；jar 中的 `net.minecraft.world.clock.*` 与 Level#getOverworldClockTime/getDefaultClockTime/clockManager |
| Player#displayClientMessage 移除，新增 sendOverlayMessage | — | 1.21.11 映射有、26.1 jar 无 |
| ServerPlayer#serverLevel、Entity#getCommandSenderWorld、ItemStack#save 在 1.21.6 移除 | ref `ApiChangeRule.java`（ServerPlayer.server 私有化） | 1.21.5 映射有、1.21.6 映射无；26.1 jar |
| CompoundTag getter 返回 Optional、putUUID/getUUID/hasUUID 移除（1.21.5） | — | 1.21.4 映射有 getUUID、1.21.5 无；26.1 jar 描述符 |
| BlockEntity loadAdditional(ValueInput) / saveAdditional(ValueOutput) | — | 26.1 jar 描述符；1.21.6 映射首次出现 ValueInput |
| Entity#hurt 变为 final void，hurtServer/hurtOrSimulate | — | 26.1 jar 访问标志与描述符 |
| Item#appendHoverText 五参签名、Item#use 返回 InteractionResult、Block#use 拆分为 useWithoutItem/useItemOn、Item#getDescriptionId(ItemStack) 移除 | — | 26.1 jar |
| Screen/AbstractContainerScreen/GuiGraphics 的 render* -> extract*/简名 | ref `1.21.11_to_26.1.md`、`MixinTargetResolver.java`（其中 renderSlot、renderItem 标为推测） | 1.21.11 映射与 26.1 jar 的成员集合差分（两项推测均被证实）；NeoForge 26.1 新闻 |
| ChunkPos.containing / pack / unpack，(BlockPos)、(long) 构造器移除 | ref `1.21.11_to_26.1.md`、`patterns/method-renames.md` | 26.1 jar；NeoForge 26.1 新闻 |
| 26.2：Minecraft#setScreen 等移到 Gui、Gui 的 HUD 方法移到 Hud | ref `26.1.2_to_26.2.md`、`ApiChangeRule.java` | 26.2 jar（Gui#setScreen/screen()/toastManager()/chatListener()/openChatScreen、Hud#setOverlayMessage）；26.2 Fabric 博客 |
| 26.2：MobEffect 拼写修正、ChunkPos.MAX_COORDINATE_VALUE -> ChunkPyramid、BlockPos#getCenter 移除、ColorCollection/WeatheringCopperCollection、BlockIds/ItemIds/BlockItemIds | ref `ApiChangeRule.java`、`26.1.2_to_26.2.md` | 26.1 与 26.2 jar 的 IR 类 public 成员差分 |

## 6. removed.json / guidance 中引用的其它 API

所有出现在 note / message / guidance 里的官方类名与方法名（如 Commands.hasPermission、FeatureFlags.VANILLA_SET、
StreamCodec.composite、TagKey.create、DataComponentType.Builder#persistent/networkSynchronized、BaseEntityBlock#createTickerHelper、
InteractionResult.Success#heldItemTransformedTo、KeyMapping.Category.register、DamageSource 构造器等）均已在 26.1 jar 中检索确认存在；
Fabric API 名称（ServerLivingEntityEvents.ALLOW_DAMAGE/ALLOW_DEATH/AFTER_DEATH、ServerPlayerEvents.COPY_FROM、ContainerStorage、
FluidStorageUtil、SingleVariantStorage、ScreenMouseEvents、AttachmentRegistry、DimensionEvents 等）均在 FabricMC/fabric 26.1 分支源码中确认。

## 7. Java 25 平台数据（java/25.json、features.json）

| 条目 | 线索 | 核实 |
|---|---|---|
| Thread.countStackFrames 移除（22） | ref `java/java25.md`（未覆盖） | https://cr.openjdk.org/~iris/se/22/latestSpec/ 第 8 节 |
| MLet 系列、Thread.suspend/resume、ThreadGroup.suspend/resume/stop 移除（23） | — | https://cr.openjdk.org/~iris/se/23/latestSpec/ 第 8 节 |
| Java SE 24 / 25 移除项均为 AWT/Swing/JNDI 细项，与模组无关，未收录 | — | https://cr.openjdk.org/~iris/se/24/latestSpec/ 、https://cr.openjdk.org/~iris/se/25/latestSpec/ |
| SecurityManager 永久停用、getSecurityManager 恒为 null、doPrivileged 直接执行、Subject.getSubject 抛异常、启用参数致启动失败 | ref `java/java21.md`/`java25.md`（仅提到 Java 25 要求） | https://openjdk.org/jeps/486 |
| Runtime/System.runFinalization、Subject.doAs、System.getSecurityManager 待移除 | — | Java SE 25 规范第 9 节 |
| sun.misc.Unsafe 内存访问弃用（23）/运行期警告（24）、FFM 正式版（22） | — | https://openjdk.org/jeps/471 、https://openjdk.org/jeps/498 、https://openjdk.org/jeps/454 |
| features：unnamed-variables 22（JEP 456）；record-patterns 21（JEP 440）；pattern-switch 21（JEP 441）；module-imports 25（JEP 511）；flexible-constructor-bodies 25（JEP 513）；compact-source-files 25（JEP 512） | — | https://openjdk.org/projects/jdk/22/ 、https://openjdk.org/projects/jdk/25/ （JDK 21 两项见 https://openjdk.org/projects/jdk/21/） |

---

## 8. 被修正或丢弃的 ref 条目

| ref 条目 | 处理 | 理由 |
|---|---|---|
| `ColorProviderRegistry.ITEM.register(` -> `ItemColorRegistry.register(`（ApiChangeRule、1.21.11_to_26.1.md 第 9 条） | 丢弃 | Fabric API 26.1/26.2 源码中不存在 ItemColorRegistry；博客说明 ColorProviderRegistry 自 1.21.4 起只用于方块，物品着色由物品模型 tints 负责 |
| `ItemGroupEvents.modifyEntriesEvent(` -> `CreativeModeTabEvents.modify(` | 修正为 `modifyOutputEvent` | 26.1 分支 CreativeModeTabEvents.java 中的方法名 |
| `ItemGroupEvents` 新包 `net.fabricmc.fabric.api.item.v1` | 修正为 `net.fabricmc.fabric.api.creativetab.v1` | 官方迁移映射表与源码树 |
| Fabric Loader 版本（versions.md 表中 26.1/26.1.1 = 0.19.2，与 26.1 模板 0.18.4 自相矛盾） | 采用 0.18.4（26.1.x） | 26.1 博客与 Fabric API 各标签的 loader_version |
| Fabric API 版本 0.145.0+26.1 / 0.145.3+26.1.1 / 0.154.2+26.1.2 / 0.154.2+26.2 | 更新为各版本最新构建 | Maven 元数据（旧值存在但非最新） |
| versions.md 所列 Loom「1.16-SNAPSHOT」用于 26.1 | 修正为 1.15-SNAPSHOT | 26.1 博客 |
| 「HudRenderCallback -> HudElementRegistry」以 import 改写方式处理 | 改为类缺失 + guidance | 两者模型不同（注册图层而非订阅回调），不能按类改名 |
| 「BlockRenderLayerMap」在迁移映射表中对应 ChunkSectionLayerMap | 未采用，改为已删除 + guidance | 26.1、26.2 正式源码树中均无 ChunkSectionLayerMap（映射表条目与正式版不符） |
| ApiChangeRule 中 `.getEntityWorld()` <-> `.getWorld()`、`MinecraftClient.IS_SYSTEM_MAC`、`new KeyBinding(` -> `new KeyMapping(`、`client.gui.screen.Screen` -> `client.gui.screens.Screen` 等 | 不适用于本数据集 | 这些是 Yarn 名与官方名混用或映射体系差异，并非版本间改名；官方名数据集中不存在该变化 |
| 26.2 的 `SlimePredicate` -> `CubeMobPredicate`、advancements 包迁移、Speleothem 系列改名、Bucketable 迁移等 | 未写入 | 均不在现有 IR 覆盖范围内（无对应 IR id），且无需 concept 兜底；可由后续扩充 IR 时再录入 |
| 「Gradle 9.4.0+ required for Java 25」 | 未写成规则，仅用于 gradleVersion | NeoForge 新闻给出的下限为 9.1.0，二者不矛盾；本数据集按 Fabric 博客取 9.4.0 |

## 9. 未能核实 / 暂未写入

- Mixin `@At` 描述符串中的类/方法名改写（MixinTargetResolver 的内容）：引擎目前只改导入与代码中的名字，不解析 mixin 目标字符串；事实已核实，但无数据格式可承载，未写入。
- `ChunkPos` 构造器 -> 静态工厂的 idiom：(BlockPos) 与 (long) 两个构造器同为单参，引擎按参数个数匹配无法区分，未写 forms，只写在 note 中。
- ChatFormatting 在 26.2 移除的 getColor/getName 等的具体替代 API：只确认了移除，替代方式写为「需人工改写」。
- Level#getDayTime 与 getOverworldClockTime/getDefaultClockTime 的语义等价性未从官方文档确认，因此只加 TODO 提示，不做自动改名。

## 汇总步骤补写的 guidance（2026-10）

- 26.1 / 26.2 补齐 concept 与 `mc.client.gui.GuiComponent/LayeredDraw/Hud`、`PotionUtils`、`ItemInteractionResult` 的 guidance。
  原版事实取自 neoforge/26.1、26.2 数据集已核实的说明（两者同为官方名、同一原版 jar）；Fabric API 部分依据本文件第 4 节
  （TradeOfferHelper 在 26.1 删除、AttachmentRegistry 与 Transfer API 仍在 26.1 分支）。
- 待核实：26.1 Fabric API 中 `ResourceLoader#registerReloader` 的参数形式与 `AttachmentRegistry` 附件读写方法名是否随 26.1 迁移表改名。
