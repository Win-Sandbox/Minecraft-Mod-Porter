# Fabric 映射数据的 IR 契约

本文件是**多人/多 agent 并行编写 Fabric 映射数据时的强制约定**。
所有 `mappings/versions/fabric/<version>/` 数据集必须严格遵守，否则版本之间无法互转。

## 0. 基本前提

- Fabric 数据集使用 **Yarn 映射**（Fabric 官方文档与绝大多数 Fabric 模组的默认选择）。
  例：`net.minecraft.entity.player.PlayerEntity`、`net.minecraft.util.Identifier`、`net.minecraft.nbt.NbtCompound`。
  若将来需要支持 Mojmap 版 Fabric，另建 `versions/fabric-mojmap/`，不要混入本目录。
- **例外：MC 26.1 起游戏不再混淆，Yarn 停止更新**，Fabric 工具链直接使用 Mojang 官方名。
  因此 `versions/fabric/26.*` 数据集使用官方名（`mappingsChannel: "official"`），类名与 NeoForge 同期数据集一致。
  由于每个数据集只描述「本版本 ↔ IR」，Yarn 版本与 26.x 之间照常经 IR 互转，无需特殊处理。
  （上文 `fabric-mojmap/` 的约定仅适用于「同一 MC 版本同时存在 Yarn 与官方名两种写法」的情况。）
- 通用的 id 命名、来源登记与合规要求见 [DATA-RULES.md](DATA-RULES.md)。
- Fabric 从 MC 1.14 起才存在，因此没有 1.12.x 数据集。
- 文件结构、`basedOn`、`aliases`、`!remove` 等机制与 Forge 数据集完全一致，见 [README](../README.md)。

## 1. `mc.*` IR id —— 与 Forge 共用，一个都不许改名

原版 Minecraft 类的 IR id **直接取自现有 Forge 数据集**（以 `versions/forge/1.20.1/classes.json` 的键集为准）。
Fabric 数据集要做的只是把同一个 IR id 映射到该版本的 **Yarn 名**。

对照示例（IR id → Forge/Mojmap → Fabric/Yarn）：

| IR id | Forge (Mojmap) | Fabric (Yarn) |
|---|---|---|
| `mc.entity.player.Player` | `net.minecraft.world.entity.player.Player` | `net.minecraft.entity.player.PlayerEntity` |
| `mc.world.Level` | `net.minecraft.world.level.Level` | `net.minecraft.world.World` |
| `mc.resources.ResourceLocation` | `net.minecraft.resources.ResourceLocation` | `net.minecraft.util.Identifier` |
| `mc.nbt.CompoundTag` | `net.minecraft.nbt.CompoundTag` | `net.minecraft.nbt.NbtCompound` |
| `mc.chat.Component` | `net.minecraft.network.chat.Component` | `net.minecraft.text.Text` |
| `mc.blockentity.BlockEntity` | `...block.entity.BlockEntity` | `net.minecraft.block.entity.BlockEntity` |

**成员（members.json）同理**：IR 成员名沿用 Forge 数据集里的规范名，值填该版本 Yarn 的成员名与形态（method/field）。
例：IR `mc.world.Level#isClientSide` → Fabric 为 `{"name": "isClient", "kind": "field"}`。

> 这样做的收益：`mc.*` 部分在 Forge 与 Fabric 之间天然对齐，未来实现跨加载器转换时不必重做。

## 2. `fabric.*` IR id —— 以下清单为**封闭集合**

新增任何一条都必须在最终报告中列出并说明理由，不得擅自造新 id。
某版本若不存在对应类，就**不写进 classes.json**（其语义由 concept + guidance 兜底）。

### 入口与核心
```
fabric.ModInitializer                 net.fabricmc.api.ModInitializer
fabric.ClientModInitializer           net.fabricmc.api.ClientModInitializer
fabric.DedicatedServerModInitializer  net.fabricmc.api.DedicatedServerModInitializer
fabric.Environment                    net.fabricmc.api.Environment
fabric.EnvType                        net.fabricmc.api.EnvType
fabric.FabricLoader                   net.fabricmc.loader.api.FabricLoader
fabric.ModContainer                   net.fabricmc.loader.api.ModContainer
```

### 注册与内容 API
```
fabric.item.FabricItemSettings
fabric.block.FabricBlockSettings
fabric.itemgroup.FabricItemGroup
fabric.itemgroup.ItemGroupEvents
fabric.itemgroup.FabricItemGroupEntries
fabric.registry.FabricRegistryBuilder
fabric.biome.BiomeModifications
fabric.biome.BiomeSelectors
fabric.loot.LootTableEvents
```

### 事件（Fabric API）
```
fabric.event.ServerLifecycleEvents
fabric.event.ServerTickEvents
fabric.event.ClientTickEvents
fabric.event.ClientLifecycleEvents
fabric.event.ServerEntityEvents
fabric.event.ServerPlayConnectionEvents
fabric.event.UseBlockCallback
fabric.event.UseItemCallback
fabric.event.UseEntityCallback
fabric.event.AttackBlockCallback
fabric.event.PlayerBlockBreakEvents
fabric.event.CommandRegistrationCallback
fabric.event.HudRenderCallback
fabric.event.WorldRenderEvents
```

### 网络
```
fabric.network.ServerPlayNetworking
fabric.network.ClientPlayNetworking
fabric.network.PacketByteBufs
```

### 界面与客户端
```
fabric.screen.ExtendedScreenHandlerFactory
fabric.screen.HandledScreens
fabric.client.KeyBindingHelper
fabric.client.BlockRenderLayerMap
fabric.client.EntityRendererRegistry
fabric.client.ColorProviderRegistry
fabric.client.HudElementRegistry      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry（1.21.6+）
fabric.client.HudElement              net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement（1.21.6+）
fabric.client.VanillaHudElements      net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements（1.21.6+）
```

### 资源重载
```
fabric.resource.ResourceLoader        net.fabricmc.fabric.api.resource.v1.ResourceLoader（1.21.9+）
```

> 以上四个 id 为汇总步骤补入：它们在 1.21.6 / 1.21.9 首次出现后，直到 26.2 的 FQCN 都没有变化；
> 不登记 IR 时，26.x 数据集只能把它们写进 removed.json，从 26.x 转回 1.21.6–1.21.11 会被误报为「已移除」。
> 它们与旧 API（`HudRenderCallback`、`ResourceManagerHelper`）的用法不同，因此不复用旧 id，
> 在没有该类的版本里由 guidance 说明旧写法。

### 数据生成
```
fabric.datagen.FabricDataGenerator
fabric.datagen.FabricDataOutput
```

### Mixin（各版本 FQCN 相同，登记是为了避免引擎误报「未映射类」）
```
mixin.Mixin           org.spongepowered.asm.mixin.Mixin
mixin.Shadow          org.spongepowered.asm.mixin.Shadow
mixin.Unique          org.spongepowered.asm.mixin.Unique
mixin.Final           org.spongepowered.asm.mixin.Final
mixin.Inject          org.spongepowered.asm.mixin.injection.Inject
mixin.At              org.spongepowered.asm.mixin.injection.At
mixin.Redirect        org.spongepowered.asm.mixin.injection.Redirect
mixin.ModifyVariable  org.spongepowered.asm.mixin.injection.ModifyVariable
mixin.CallbackInfo    org.spongepowered.asm.mixin.injection.callback.CallbackInfo
mixin.CallbackInfoReturnable  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
mixin.Accessor        org.spongepowered.asm.mixin.gen.Accessor
mixin.Invoker         org.spongepowered.asm.mixin.gen.Invoker
```

## 3. concept id —— 与 Forge 共用语义相同的部分

`removed.json` 里的 `concept` 必须优先复用 Forge 数据集里已有的 id
（`registry.imperative`、`item.propertiesBuilder`、`block.propertiesBuilder`、`item.components`、
`enchantment.datadriven`、`resourcelocation.factory`、`tags.oredict`、`gui.menu`、`network.channel`、
`worldgen`、`dimension`、`item.food`、`item.durability`、`creative.fill`、`registry.builtin`、
`damage.types`、`entity.registration`、`commands.brigadier`、`config`、`blockentity.ticker` 等）。

Fabric 特有且 Forge 侧无对应概念时，可新增 `fabric.` 前缀的 concept id（如 `fabric.entrypoint`、`fabric.mixin`），
**必须在报告中列全**，我会补齐其它版本的反向 guidance。

**硬性要求**：本数据集的 `idioms.json.guidance` 必须覆盖
「Forge 全部 concept id + 本数据集自己产生的 concept id」的并集，一条都不能缺。

## 4. version.json 的 Fabric 约定

```jsonc
{
  "mcVersion": "1.20.1",
  "loader": "fabric",
  "javaVersion": 17,
  "metadataFormat": "fabric.mod.json",
  "metadataPath": "src/main/resources/fabric.mod.json",
  "langFormat": "json",
  "langKeys":  { "block": "block.{modid}.{name}", "item": "item.{modid}.{name}", "group": "itemGroup.{name}" },
  "texturePrefixes": { "block": "block/", "item": "item/" },
  "lifecycleStyle": "entrypoint",     // Fabric 没有 @Mod 注解，故不写 modAnnotationStyle
  "packFormat": 15,
  "loaderVersionRange": ">=0.14.21",  // 写进 fabric.mod.json 的 depends.fabricloader
  "mappingsChannel": "yarn",
  "gradleVersion": "8.1.1",
  "extras": {                          // 供 build.gradle / fabric.mod.json 模板占位符使用
    "yarnVersion": "1.20.1+build.10",
    "loomVersion": "1.6-SNAPSHOT",
    "fabricLoaderVersion": "0.15.11",
    "fabricApiVersion": "0.92.2+1.20.1"
  }
}
```

- `modAnnotationStyle` **不要写**（Fabric 无 @Mod，引擎遇 null 会跳过该改写）。
- `lifecycleStyle` 全部 Fabric 版本统一写 `"entrypoint"`（同加载器内相同即不触发迁移逻辑）。
- `extras` 里的键会以 `${键名}` 形式在模板中替换。

## 5. templates/

- `templates/fabric.mod.json` —— 可用占位符：`${modid} ${name} ${version} ${description} ${authors}
  ${url} ${logoFile} ${credits} ${mcVersion} ${javaVersion} ${loaderVersionRange}` 及 `extras` 中的键。
  另有两个由引擎注入的特殊占位符：**`${entrypointsJson}`**（原工程的 entrypoints 对象体）与
  **`${mixinsJson}`**（原工程的 mixins 数组体），务必使用它们，否则会丢掉模组的入口类声明。
- `templates/build.gradle` —— Loom 构建脚本，用 `${extras}` 里的 loom/yarn/loader/api 版本占位符。

## 6. 自检清单（交付前必做）

1. 全部 JSON 通过 `python3 -m json.tool`
2. Forge 1.20.1 `classes.json` 的每个 `mc.*` IR id 都已给出结论（映射 / 明确说明为何不适用）
3. `fabric.*` IR id 全部出自本文第 2 节清单
4. guidance 覆盖第 3 节要求的 concept 并集
5. `version.json` 字段齐全，`extras` 中版本号真实存在（须联网核实）
