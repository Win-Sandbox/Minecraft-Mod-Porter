# 来源登记：Fabric（Yarn）1.20.5 – 1.21.11

范围：`versions/fabric/{1.20.5(+别名 1.20.6), 1.21.2(+别名 1.21.3), 1.21.4, 1.21.5, 1.21.6(+别名 1.21.7/1.21.8), 1.21.9, 1.21.10, 1.21.11}`。
线索来自参考仓库 reqsery/mc-mod-porter（下称 ref），只取事实；所有写入的条目都经过下列一手来源核实。
note / message / guidance 均为自撰中文，未复制 ref 或其它第三方文字。

## 一、通用一手来源

| 用途 | URL |
|---|---|
| Yarn 映射（按 intermediary 比对各版本类名/成员名，本批次所有 Yarn 名的依据） | `https://github.com/FabricMC/yarn/tree/<版本>/mappings`（分支 1.20.4、1.20.5、1.20.6、1.21、1.21.1、1.21.2、1.21.4、1.21.5、1.21.6、1.21.8、1.21.9、1.21.10、1.21.11） |
| Yarn 构建号 | https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml |
| Fabric API 版本、各版本 `fabric.mod.json` 的 loader/minecraft 依赖 | https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml ；`https://github.com/FabricMC/fabric-api/tree/<tag>`（tag 形如 `0.128.2+1.21.6`） |
| Fabric Loader 版本 | https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml |
| Loom 版本 | https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/maven-metadata.xml |
| Loom 与 Gradle 的配套（各 Loom 分支的 gradle-wrapper） | `https://github.com/FabricMC/fabric-loom/blob/dev/<1.6…1.14>/gradle/wrapper/gradle-wrapper.properties` |
| Gradle 发行包存在性 | `https://services.gradle.org/distributions/gradle-<版本>-bin.zip` |
| Java 版本（全部为 21） | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json 及各版本 JSON 的 `javaVersion` |
| 资源包版本（packFormat） | `https://www.minecraft.net/en-us/article/minecraft-java-edition-<1-20-5 … 1-21-11>` |
| 官方名交叉核对（Mojang 名，仅用于推导 IR id 与确认改动存在） | `https://github.com/neoforged/.github/tree/main/primers/<1.20.5 … 1.21.11>` |
| Fabric 版本公告 | https://fabricmc.net/2024/04/19/1205.html ，https://fabricmc.net/2024/05/31/121.html ，https://fabricmc.net/2024/10/14/1212.html ，https://fabricmc.net/2024/12/02/1214.html ，https://fabricmc.net/2025/03/24/1215.html ，https://fabricmc.net/2025/06/15/1216.html ，https://fabricmc.net/2025/09/23/1219.html ，https://fabricmc.net/2025/12/05/12111.html |

## 二、version.json 版本号（全部在官方 Maven 核实存在）

| MC | 形式 | packFormat | Yarn | Fabric API | Loader（范围） | Loom | Gradle |
|---|---|---|---|---|---|---|---|
| 1.20.5 | overlay | 32 | 1.20.5+build.1 | 0.97.8+1.20.5 | 0.15.11（>=0.15.10） | 1.6-SNAPSHOT | 8.6 |
| 1.20.6 | 1.20.5 的 alias | 32 | 1.20.6+build.3 | 0.100.8+1.20.6 | 同上 | 同上 | 同上 |
| 1.21.2 | overlay | 42 | 1.21.2+build.1 | 0.106.1+1.21.2 | 0.16.7（>=0.16.7） | 1.8-SNAPSHOT | 8.10 |
| 1.21.3 | 1.21.2 的 alias | 42 | 1.21.3+build.2 | 0.114.1+1.21.3 | 0.16.8（>=0.16.8） | 同上 | 同上 |
| 1.21.4 | overlay | 46 | 1.21.4+build.8 | 0.119.4+1.21.4 | 0.16.9（>=0.16.9） | 1.9-SNAPSHOT | 8.11 |
| 1.21.5 | overlay | 55 | 1.21.5+build.1 | 0.128.2+1.21.5 | 0.16.10（>=0.16.10） | 1.10-SNAPSHOT | 8.12 |
| 1.21.6 | overlay | 63 | 1.21.6+build.1 | 0.128.2+1.21.6 | 0.16.14（>=0.16.13） | 1.10-SNAPSHOT | 8.12 |
| 1.21.7 | 1.21.6 的 alias | 64 | 1.21.7+build.8 | 0.129.0+1.21.7 | 同上 | 同上 | 同上 |
| 1.21.8 | 1.21.6 的 alias | 64 | 1.21.8+build.1 | 0.136.1+1.21.8 | 同上 | 同上 | 同上 |
| 1.21.9 | overlay | 69 | 1.21.9+build.1 | 0.134.1+1.21.9 | 0.17.2（>=0.17.0） | 1.11-SNAPSHOT | 8.14 |
| 1.21.10 | overlay | 69 | 1.21.10+build.3 | 0.138.4+1.21.10 | 0.17.2（>=0.17.0） | 1.11-SNAPSHOT | 8.14 |
| 1.21.11 | overlay | 75 | 1.21.11+build.6 | 0.141.6+1.21.11 | 0.18.1（>=0.17.3） | 1.14-SNAPSHOT | 9.2.0 |

- 线索：ref `knowledge-base/loaders/fabric/versions.md`、`auto-porter/templates/<ver>/{template-info.json,gradle.properties}`、各 `knowledge-base/minecraft/*.md` 末尾的版本号。
- Loader 范围取对应 Fabric API `fabric.mod.json` 的 `depends.fabricloader`（1.20.5 取公告要求的 0.15.10）；Loader 版本取公告所述当时稳定版。
- Loom 取公告中的最低版本；Gradle 取该 Loom 分支自带的 wrapper 版本（1.21.5 公告亦明确 Loom 1.10 需 Gradle 8.12）。
- packFormat：1.20.5/1.21.2/1.21.4/1.21.5/1.21.6/1.21.7/1.21.9/1.21.11 取 minecraft.net 公告原文数字；1.20.6/1.21.3/1.21.8/1.21.10 为热修复版，公告未提及变化，按不变处理（**未能逐字核实**）。1.21.9 起官方写作 69.0 / 75.0，这里取整数主版本。
- 别名依据：NeoForged primer 1.20.6 / 1.21.7 / 1.21.8 只有零星新增；Fabric API 0.114.1+1.21.3 声明兼容 1.21.2–1.21.3；Yarn 1.20.5 与 1.20.6 在涉及的类上逐成员比对无差异。1.21.10 没有做成 1.21.9 的别名，因为其后续 Fabric API（0.138.x）重新加入了 WorldRenderEvents（见下）。

## 三、按版本的条目与核实来源

### 1.20.5 / 1.20.6（basedOn 1.21.1）
选择 basedOn 1.21.1：数据组件、Java 21、payload 网络、RegistryWrapper 参数、StatusEffect 的 RegistryEntry 化等都与 1.21.1 相同，差异只有 Identifier 构造器、附魔数据驱动、loot v2、HUD 回调参数、若干 Yarn 包名，覆盖层远小于从 1.20.4 叠加。

| 条目 | ref 线索 | 核实 |
|---|---|---|
| Identifier 构造器仍公开，`of(ns,path)` 为宽松工厂；idioms 改回 constructor | ApiChangeRule（1.20.6↔1.21 `new Identifier(` 规则） | Yarn 1.20.5 / 1.21.1 `net/minecraft/util/Identifier.mapping`（method_43902 在 1.21 改名 tryParse，method_60654/60655 为 1.21 新增的 of） |
| 组件类型接口 Yarn 名 DataComponentType（1.21 改名 ComponentType），新 id `mc.core.component.DataComponentType` | —（ref 未覆盖） | Yarn 1.20.5/1.20.6/1.21 目录对比；primer 1.20.5 |
| TooltipType 包名 `net.minecraft.client.item`（1.21 移到 `item.tooltip`），新 id `mc.world.item.TooltipFlag` | — | Yarn 1.20.5/1.21 目录对比 |
| `ItemStack.areItemsAndComponentsEqual`（修正基版本的 areEqual） | — | Yarn ItemStack method_31577 |
| `ItemStack.damage` 1.20.5 两种签名 | — | Yarn ItemStack method_7970 / method_7956 描述符；1.20.5 公告 |
| 方块 onUse 无 Hand、onUseWithItem 返回 ItemActionResult | — | Yarn AbstractBlock method_55765/55766；primer 1.20.5 “ItemInteractionResult” |
| 附魔：Enchantment(Properties)、常量为实例、getLevel(Enchantment, ItemStack) | — | Yarn Enchantment / EnchantmentHelper 1.20.5；1.20.5 公告 |
| FabricItemSettings 删除、FabricBlockSettings 废弃、FabricEntityTypeBuilder 废弃、Item.Settings 接口注入 | ref 无 | 1.20.5 公告；fabric-api tag 0.97.8+1.20.5（`FabricItem.java`、fabric.mod.json 的 injected_interfaces） |
| loot 仍为 v2 且首参 RegistryKey | — | fabric-api 1.20.6 分支 `fabric-loot-api-v2/.../LootTableEvents.java` |
| HudRenderCallback 第二参为 float | ApiChangeRule（DeltaTracker 1.20.6↔1.21） | fabric-api tag 0.100.8+1.20.6 `HudRenderCallback.java` |
| FabricDimensions 仍存在 | ref 1.20.6_to_1.21.md | fabric-api 1.20.6 分支存在、1.21.1 分支不存在 |
| 配方/标签目录复数 | ref 1.20.6_to_1.21.md | 1.21 公告 |
| RegistryByteBuf（Yarn）/PacketByteBuf 仍在 | ref 1.20.4_to_1.20.5.md、ApiChangeRule | Yarn 1.20.5 目录；1.20.5 公告 |
| SkinTextures（新 id `mc.client.resources.PlayerSkin`） | ref patterns/class-moves.md | Yarn `client/util/SkinTextures` |
| Screen#repositionElements = initTabNavigation | — | Yarn Screen method_48640 |
| 工具/盔甲子类、ArmorMaterial 注册表对象 | — | Yarn 1.20.5 `SwordItem/MiningToolItem/ArmorItem/ArmorMaterial` 映射 |

### 1.21.2 / 1.21.3（basedOn 1.21.1）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| Item/Block Settings 必须 registryKey，useBlockPrefixedTranslationKey | ref 1.21.1_to_1.21.2.md 记为“无重大变化”（错误，已补） | 1.21.2 公告；Yarn Item$Settings / AbstractBlock$Settings |
| TypedActionResult、ItemActionResult 删除，ActionResult 密封接口与常量 | — | Yarn `util/ActionResult` 1.21.2；primer 1.21.2 “Interaction Results”；fabric-api tag 0.106.1+1.21.2 UseItemCallback |
| Entity#damage(ServerWorld,…)、clientDamage、kill(ServerWorld) | — | Yarn Entity method_64397 / method_5643 / method_5768 |
| EntityType.Builder#build(RegistryKey)、create(World, SpawnReason) | — | Yarn EntityType 1.21.2 |
| encode → toNbt | — | 1.21.2 公告 Yarn 改名表；Yarn ItemStack method_57358 |
| Item#getTranslationKey(ItemStack) 删除 | — | Yarn Item 1.21.2 |
| Registry/WrapperLookup 方法改名 | — | 1.21.2 公告；Yarn Registry 映射比对 |
| Ingredient 改为条目集合 | — | Yarn Ingredient 1.21.2 |
| 渲染状态 EntityRenderState | — | Yarn EntityRenderer 1.21.2；1.21.2 公告 |
| DrawContext 贴图首参 RenderLayer::getGuiTextured | ApiChangeRule（1.21.4↔1.21.10 blit 规则） | Yarn DrawContext 1.21.2、RenderLayer#getGuiTextured |
| refreshWidgetPositions | — | 1.21.2 公告 Yarn 改名；Yarn Screen method_48640 |
| FabricBlockSettings 删除、FabricBlockEntityTypeBuilder 恢复为推荐、FuelRegistryEvents | — | 1.21.2 公告；fabric-api tag 0.114.1+1.21.3 |
| ConsumableComponent、FoodComponent 精简 | — | Yarn FoodComponent/ConsumableComponent 1.21.2 |
| ToolMaterial record、equipment.ArmorMaterial | — | Yarn ToolMaterial / item/equipment 1.21.2 |
| 配方 RegistryKey、ServerRecipeManager、getRecipeGenerator | — | 1.21.2 公告；fabric-api `FabricRecipeProvider.java`（0.106.1+1.21.2） |

### 1.21.4（basedOn 1.21.2）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| 物品模型定义 assets/<ns>/items/*.json、tints | ref 1.21.3_to_1.21.4.md 记为“无重大变化”（错误，已补） | 1.21.4 公告；primer 1.21.4 “Client Items” |
| ColorProviderRegistry.ITEM 删除 | — | fabric-api tag 0.119.4+1.21.4 `ColorProviderRegistry.java` |
| 数据生成包 net.minecraft.client.data | — | 1.21.4 公告；`FabricModelProvider.java` 导入 |
| 数据附件 AttachmentRegistry.create(id, builder) | — | 1.21.4 公告；fabric-api `AttachmentRegistry.java` |

### 1.21.5（basedOn 1.21.4）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| NbtCompound 取值 Optional、getCompoundOrEmpty/getListOrEmpty、UUID 与 contains(key,type) 删除 | ref 1.21.4_to_1.21.5.md 记为“无重大变化”（错误，已补） | 1.21.5 公告；Yarn NbtCompound/NbtList 1.21.4 vs 1.21.5；Yarn `util/Uuids` |
| SwordItem/MiningToolItem/PickaxeItem/ArmorItem 删除，Settings.sword/pickaxe/axe/shovel/hoe/tool/armor | — | Yarn 目录 1.21.4 vs 1.21.5；Yarn Item$Settings；primer 1.21.5 |
| ClickEvent/HoverEvent 拆分为 record | — | Yarn text/ClickEvent、HoverEvent 1.21.4 vs 1.21.5 |
| Item#appendTooltip 新签名并废弃 | — | Yarn Item method_67187；primer 1.21.5 |
| BER render 增加 Vec3d | — | Yarn BlockEntityRenderer method_3569 |
| RenderTickCounter getTickDelta → getTickProgress | ref 1.20.6_to_1.21.md（DeltaTracker） | Yarn RenderTickCounter method_60637 |
| HudRenderCallback 废弃、HudLayerRegistrationCallback | — | 1.21.5 公告；fabric-api tag 0.128.2+1.21.5 |
| TradeOfferHelper 改用 RegistryKey | — | 1.21.5 公告；fabric-api `TradeOfferHelper.java` |

### 1.21.6 / 1.21.7 / 1.21.8（basedOn 1.21.5）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| ReadView/WriteView，readData/writeData | —（ref 未覆盖） | 1.21.6 公告；Yarn `storage/ReadView|WriteView`、BlockEntity method_11014/11007、Entity readCustomData |
| GUI 首参 RenderPipeline、Matrix3x2fStack、drawText 无返回值 | ref 1.21.5_to_1.21.6.md、ApiChangeRule 1.21.5↔1.21.6 blit 规则（已修正，见四） | Yarn DrawContext 1.21.5 vs 1.21.6；Yarn `client/gl/RenderPipelines` |
| BlockRenderLayerMap 迁包 + 静态 + BlockRenderLayer | ref 1.21.5_to_1.21.6.md、ApiChangeRule | 1.21.6 公告；fabric-api tag 0.128.2+1.21.6 `BlockRenderLayerMap.java` |
| ServerPlayerEntity.server 私有 | ref 1.21.5_to_1.21.6.md、ApiChangeRule | primer 1.21.6 “Server Player Changes”（Yarn 映射不含访问修饰符） |
| Entity#getEntityWorld（getCommandSenderWorld）删除 | — | Yarn Entity method_5770 |
| ItemStack toNbt/fromNbt 删除 | — | Yarn ItemStack 1.21.5 vs 1.21.6 |
| HudElementRegistry，HudLayerRegistrationCallback 删除 | — | 1.21.6 公告；fabric-api tag 0.128.2+1.21.6 |
| LayeredDrawer 删除（基版本新 id `mc.client.gui.LayeredDraw` 在此 !remove） | — | Yarn 目录 1.21.2 vs 1.21.6 |
| 标签提供器 valueLookupBuilder | — | 1.21.6 公告；fabric-api `FabricTagProvider.java` |
| 1.21.7/1.21.8 为别名 | ref 1.21.6_to_1.21.7.md、1.21.7_to_1.21.8.md | primer 1.21.7 / 1.21.8 |

### 1.21.9（basedOn 1.21.6）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| Entity#getWorld → getEntityWorld、getPos → getEntityPos（HeldItemContext）、Entity#getServer 删除 | ref 1.21.9_to_1.21.10.md（记在 1.21.10，实为 1.21.9）、ApiChangeRule | 1.21.9 公告；Yarn `util/HeldItemContext`、Entity 1.21.8 vs 1.21.9 |
| World#isClient 改用方法 | — | primer 1.21.9 “Level#isClientSide now private”；Yarn WorldView method_8608 |
| KeyInput/CharInput/Click/MouseInput，keyPressed/mouseClicked/charTyped 新签名 | ref 1.21.8_to_1.21.9.md、patterns/signatures.md、ApiChangeRule | Yarn `client/input/*`、`client/gui/Click`、Element 1.21.8 vs 1.21.9 |
| KeyBinding.Category.create(Identifier) | ref（称 Fabric 在 1.21.10 才改，已修正） | 1.21.9 公告；Yarn KeyBinding 1.21.9 |
| SkinTextures 迁包、body().texturePath()、getSkin | ref 1.21.8_to_1.21.9.md、ApiChangeRule | Yarn 目录 1.21.8 vs 1.21.9、ClientPlayerLikeEntity |
| SystemKeycodes.IS_MAC_OS | ref 1.21.9_to_1.21.10.md、ApiChangeRule | 1.21.9 公告；Yarn MinecraftClient 1.21.8 vs 1.21.9、`client/input/SystemKeycodes` |
| BER/实体渲染器提交式、OrderedRenderCommandQueue | ref 1.21.9_to_1.21.10.md | 1.21.9 公告；Yarn BlockEntityRenderer/EntityRenderer 1.21.9、`render/command/*` |
| ResourceLoader（v1） | ref 1.21.9_to_1.21.10.md、ApiChangeRule | 1.21.9 公告；fabric-api tag 0.134.1+1.21.9 |
| WorldRenderEvents 删除（!remove） | — | 1.21.9 公告；fabric-api tag 0.134.1+1.21.9（v1 包下不存在） |
| EntityRendererRegistry 废弃、EntityRendererFactories | — | fabric-api tag 0.134.1+1.21.9；Yarn 目录 |

### 1.21.10（basedOn 1.21.9）
| 条目 | 核实 |
|---|---|
| WorldRenderEvents 以新包 `...rendering.v1.world` 回归 | 1.21.11 公告（“为 1.21.10+ 重新引入”）；fabric-api tag 0.134.1+1.21.10（不存在）与 0.138.4+1.21.10（存在）对比 |

### 1.21.11（basedOn 1.21.10）
| 条目 | ref 线索 | 核实 |
|---|---|---|
| 权限：PermissionPredicate、DefaultPermissions、CommandManager.requirePermissionLevel(GAMEMASTERS_CHECK)，hasPermissionLevel 删除 | — | Yarn 1.21.10 vs 1.21.11 `command/permission/*`、CommandManager、ServerCommandSource；primer 1.21.11 “Permission Overhaul” |
| ResourceManagerHelper（v0）删除 | — | fabric-api 1.21.11 分支/tag 0.141.6+1.21.11 |
| Yarn 最后版本 | — | 1.21.11 公告 |

## 四、被修正或丢弃的 ref 事实

1. **“PacketByteBuf（Fabric/intermediary 名）→ RegistryFriendlyByteBuf”**（1.20.4_to_1.20.5.md、patterns/*）：混用 Yarn 与 Mojang 名。Yarn 中 PacketByteBuf 未改名，1.20.5 新增子类 RegistryByteBuf；已按此写入 guidance，丢弃“改名”说法。
2. **ApiChangeRule “FriendlyByteBuf → RegistryFriendlyByteBuf” 与构造器规则**：是 Mojang 名的文本替换，Yarn 数据集不适用；构造方式改写为 `new RegistryByteBuf(buf, 注册表管理器)` 的说明。
3. **1.21.6 blit“改为 0–1 浮点 UV、去掉 RenderType”**：不准确。Yarn 1.21.6 像素 UV 重载仍在，只是首参由 `RenderLayer::getGuiTextured` 换成 `RenderPipelines.GUI_TEXTURED`；丢弃浮点 UV 换算规则。
4. **KeyEvent/MouseButtonEvent/KeyMapping.Category“1.21.10 引入 / Fabric 1.21.10 才改”**（patterns/*、1.21.9_to_1.21.10.md、ApiChangeRule 1.21.4↔1.21.10 规则）：实为 1.21.9 原版改动，与加载器无关；Yarn 名为 KeyInput / Click / KeyBinding.Category.create。
5. **Entity#getWorld → getEntityWorld 记在 1.21.10**：实为 1.21.9（公告与 Yarn 均证实）。
6. **“1.21.10→1.21.11 ResourceLocation 改名 Identifier”**：只属于 Mojang 名；Yarn 一直叫 Identifier，Fabric Yarn 数据集无需改动，丢弃。
7. **“Screen 移到 screens 包”“KeyBinding → KeyMapping”（ApiChangeRule 1.20.1↔1.21.10、1.16.5↔1.21.10）及 patterns 中 World→Level、PlayerEntity→Player**：把映射体系差异当成版本改名，丢弃。
8. **1.21.1→1.21.2、1.21.3→1.21.4、1.21.4→1.21.5 “无重大 API 变化”**：错误，已按公告与 Yarn 补齐。
9. **ref 版本表**：Loader 一律 0.19.2、Loom 一律 1.16-SNAPSHOT（时代错置）丢弃；templates 中 `fabricLoader "0.15.11+build.1"` 不是合法 Loader 版本；Fabric API `0.110.0+1.21.2`、`0.110.6+1.21.3`、`0.136.0+1.21.9` 在 Maven 不存在；1.21.6–1.21.8 模板的 Loom 1.9 低于公告最低要求 1.10。均未采用。
10. **DeltaTracker / getGameTimeDeltaPartialTick**：Mojang 名，Yarn 为 RenderTickCounter#getTickDelta（1.21.5 起 getTickProgress），已换算。
11. **FabricBlockSettings“1.21 移除”**（基版本 1.21.1 的 note/guidance 写法）：实为 1.21.2 删除，1.21/1.21.1 仍存在（已废弃）。
12. MixinTargetResolver 的全部规则都在 1.21.11↔26.1，不在本范围；ApiChangeRule 中 NeoForge（FMLEnvironment、RegisterGuiLayersEvent、ClientTickEvent）规则不适用于 Fabric。

## 五、未能核实 / 暂未写入

- 1.20.6、1.21.3、1.21.8、1.21.10 的 packFormat 公告未写明，按“热修复不变”处理。
- KeyInput 的记录组件（key/scancode/modifiers）与 Click 的 x/y 访问器：Yarn 映射文件未列出，未写具体名称，只写了已核实的 `getKeycode()`、`button()`。
- SkinTextures 1.21.9 的 `body().texturePath()`：记录组件在 Yarn 中未命名，按官方名推断，与 ref 一致但未能在 Yarn 文件中逐字确认。
- 未写入：GameRules 1.21.11 迁包（`net.minecraft.world.GameRules` → `net.minecraft.world.rule.GameRules`，无对应 IR id）；Fabric FuelRegistry / ResourceLoader(v1) / HudElementRegistry / ReadView 以外的新 Fabric 类的 IR 化（`fabric.*` 为封闭集合）；1.21.2 EntityAttributes 去掉 GENERIC_ 前缀（常量未被 Yarn 单独映射，只在报告中提及）；1.21.11 环境属性与游戏规则 API。
