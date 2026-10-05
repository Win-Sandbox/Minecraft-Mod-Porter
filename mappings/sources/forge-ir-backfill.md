# 来源登记：Forge 数据集 IR 回补（forge/1.12.2 – 1.21.1）

范围：`mappings/versions/forge/*` 的 classes / removed / idioms。目标是让 Forge 数据集认识 Fabric、NeoForge 数据集新增的
`mc.*`、`forge.*`、`neoforge.*`、`fabric.*`、`mixin.*` 类 IR 与 concept。

- 本批按用户要求**未逐条做大规模核实**：只写入有合理把握的映射，没把握的改写 guidance；存疑条目见文末「待核实」。
- 所有 note / guidance 均为自行撰写的中文，没有复制或翻译任何第三方文字。类名、包名、版本号之间的对应属于事实。
- 同 MC 版本的原版官方名对照了本仓库 `neoforge/1.20.1`、`neoforge/1.21.1` 数据集（来源见 `neoforge-1.20.x.md`、`neoforge-1.21-26.x.md`）。

## 0. 核实用一手来源

| 类别 | URL |
|---|---|
| Forge 版本 / universal、sources jar（类清单与包名） | https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml ，https://maven.minecraftforge.net/net/minecraftforge/forge/{版本}/ |
| Forge 源码（各分支） | https://github.com/MinecraftForge/MinecraftForge/tree/{1.12.x,1.14.x,1.15.x,1.16.x,1.17.x,1.18.x,1.19.x,1.20.1,1.21.x} |
| EventBus（IEventBus 等） | https://maven.minecraftforge.net/net/minecraftforge/eventbus/ |
| Mojang 官方映射（1.17+） | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json（各版本 client_mappings） |
| MCP 映射（1.12.2 stable_39；1.14.4–1.16.5 snapshot） | https://maven.minecraftforge.net/de/oceanlabs/mcp/ （mcp_stable / mcp_snapshot 的 methods/fields/classes 导出） |
| Forge 官方文档（按版本） | https://docs.minecraftforge.net/en/{1.12.x,1.14.x,1.15.x,1.16.x,1.17.x,1.18.x,1.19.x,1.20.x,1.21.x}/ |

## 1. 按版本的条目组

| 版本 | 新增映射要点 | 核实来源 |
|---|---|---|
| 1.12.2 | `forge.IEventBus`→`fml.common.eventhandler.EventBus`（附 note）、`ModContainer`（`fml.common`）、ICapabilitySerializable、ForgeHooks(Client)、EntityItemPickup/ItemTooltip/PlayerSleepInBed/ServerChat、`fml.common.gameevent.TickEvent.*`、`fml.common.registry.IEntityAdditionalSpawnData`、ItemHandlerHelper、SlotItemHandler；MCP 原版名 GuiIngame、Gui、GuiButton、GuiTextField、Render、PacketBuffer、INetHandlerPlayServer、EntitySlime/EntityMagmaCube、ItemArmor(.ArmorMaterial)、ItemTool、ItemSword、Item.ToolMaterial、ITooltipFlag、PotionUtils、GameRules、CriteriaTriggers、ICriterionTrigger | Forge 1.12.x 分支源码；MCP stable_39 |
| 1.14.4 / 1.15.2 / 1.16.5 | `net.minecraftforge.fml.*`（FMLEnvironment、ModLoadingContext、ModContainer、config.ModConfig、`ModConfig.ModConfigEvent`、FMLClientSetupEvent、`fml.event.lifecycle.GatherDataEvent`、`fml.network.*`）、`eventbus.api.IEventBus`、common.Tags、LazyOptional、IForgeContainerType、ForgeFlowingFluid、VillagerTradesEvent；1.16.5 另有 EntityAttributeCreationEvent、RegisterCommandsEvent、AddReloadListenerEvent、model.generators；MCP 原版名 IngameGui、AbstractGui、Widget、Button、TextFieldWidget、RenderTypeLookup/RenderType（1.15+）、TileEntityType、BlockStateProperties、Food、IItemTier/ItemTier、IArmorMaterial/ArmorMaterial、ToolItem、Feature、Util、IFormattableTextComponent（1.16.5） | Forge 1.14.x/1.15.x/1.16.x 分支源码；MCP snapshot 导出 |
| 1.17.1 | `fmllegacy.network.*`、CapabilityToken、RegisterCapabilitiesEvent、TierSortingRegistry、IForgeMenuType、`fml.event.config.ModConfigEvent`、`forge.event.lifecycle.GatherDataEvent`、EntityRenderersEvent、ForgeSpawnEggItem、RegisterShadersEvent；Mojang 官方名原版类 | Forge 1.17.x 分支源码；1.17.1 client_mappings |
| 1.18.1 / 1.18.2 | `net.minecraftforge.network.*`、`net.minecraftforge.entity.IEntityAdditionalSpawnData`；其余同 1.17.1 | Forge 1.18.x 分支源码；1.18.2 client_mappings |
| 1.19.1 – 1.19.4 | ConfigScreenHandler、`client.gui.overlay.IGuiOverlay`/VanillaGuiOverlay、RenderGuiEvent、RegisterGuiOverlaysEvent、RegisterKeyMappingsEvent、`data.event.GatherDataEvent`、TickEvent.LevelTickEvent、LivingEvent.LivingTickEvent、RenderHighlightEvent、RegisterDimensionSpecialEffectsEvent、RegisterClientReloadListenersEvent、FluidType（1.19.2+）、WorldPresets、EntitySubPredicate、ArmorItem.Type（1.19.4）、CreativeModeTabs（1.19.3+） | Forge 1.19.x 分支源码；1.19.2 / 1.19.4 client_mappings |
| 1.20.1 | 加载器类同 NeoForge 1.20.1 分支（`net.minecraftforge.*`）；原版新增 GuiGraphics、LightEngine、ContextAwarePredicate 等 | Forge 1.20.1 分支源码；1.20.1 client_mappings；本仓库 neoforge/1.20.1 |
| 1.21.1 | `network.SimpleChannel` 新包；DataComponents、DataComponentType、CustomData、PotionContents、FoodProperties、StreamCodec、ByteBufCodecs、RegistryFriendlyByteBuf、CustomPacketPayload、DimensionTransition、LayeredDraw、DeltaTracker、PlayerSkin、ItemInteractionResult 等；!remove：IGuiOverlay、VanillaGuiOverlay、RenderGuiEvent、RegisterGuiOverlaysEvent、TierSortingRegistry、NetworkEvent、NetworkRegistry、PotionUtils | Forge 1.21.x 分支源码；1.21.1 client_mappings；本仓库 neoforge/1.21.1 |

## 2. 结构性调整（事实依据）

- `forge.lifecycle.init` 与 `forge.event.PlayerEventFml`：1.13 起 FML 合并了初始化阶段与玩家事件，二者在 1.14.4+ 与
  `forge.lifecycle.commonSetup`、`forge.event.PlayerEvent` 指向同一类；改为在 1.14.4+ 不映射，用 guidance 说明。
- `mc.core.registries.Registries` / `BuiltInRegistries`：1.19.3 才从 `Registry` 拆出；1.14.4–1.19.2 改为映射 `mc.core.Registry`，两者写 guidance。
- `logging.slf4j`：1.17.1+ 数据集 `supported`。

## 3. 待核实

- 1.17.1：TierSortingRegistry 是否已存在；IEntityAdditionalSpawnData 的包（写成 guidance）。
- 1.14.4 / 1.15.2：VillagerTradesEvent、WandererTradesEvent 是否已存在。
- 1.16.5：ForgeSpawnEggItem 是否已存在（目前写 guidance）；model.generators 生成器类。
- 1.21.1：RenderGuiEvent、TierSortingRegistry 是否已移除；EntityItemPickupEvent、IEntityAdditionalSpawnData、ConfigScreenHandler、ForgeSpawnEggItem 是否仍在原包。
- 1.17.1–1.20.1：闪电谓词官方类名是否拼作 `LighthingBoltPredicate`（目前只在 1.21.1 映射）。
