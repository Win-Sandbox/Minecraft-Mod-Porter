# NeoForge 映射数据的 IR 契约

所有 `mappings/versions/neoforge/<version>/` 数据集必须遵守本文件与 [DATA-RULES.md](DATA-RULES.md)。

## 0. 基本前提

- 加载器目录名：`neoforge`（`--loader neoforge`）。
- NeoForge 使用 Mojang 官方映射，原版类名与同期 Forge 数据集一致。
- 文件结构、`basedOn`、`aliases`、`!remove` 与 Forge 数据集完全相同。

## 1. `mc.*` IR id —— 与 Forge / Fabric 共用

直接复用现有数据集的 `mc.*` id（以 `versions/forge/1.20.1/classes.json` 为基准），新增按 DATA-RULES 第 1 条。

## 2. 加载器类复用 `forge.*` IR id

NeoForge 是 Forge 的分支，二者的加载器概念一一对应，因此 **NeoForge 数据集把 NeoForge 的类映射到同一批 `forge.*` IR id**：

| IR id | Forge 1.20.1 | NeoForge（示例，以各版本实际为准） |
|---|---|---|
| `forge.Mod` | `net.minecraftforge.fml.common.Mod` | `net.neoforged.fml.common.Mod` |
| `forge.SubscribeEvent` | `net.minecraftforge.eventbus.api.SubscribeEvent` | `net.neoforged.bus.api.SubscribeEvent` |
| `forge.MinecraftForge` | `net.minecraftforge.common.MinecraftForge` | `net.neoforged.neoforge.common.NeoForge` |
| `forge.DeferredRegister` | `net.minecraftforge.registries.DeferredRegister` | `net.neoforged.neoforge.registries.DeferredRegister` |

- 引擎的生命周期改写按 IR id `forge.SubscribeEvent` 取目标类，因此该 id **必须**在每个 NeoForge 数据集中映射。
- 语义已发生实质变化的（如 `forge.RegistryObject` → `DeferredHolder`、capability 体系、网络 `SimpleChannel` → payload），
  映射时必须附 `note` 说明差异；完全没有对应物的不写入 classes.json，由 concept + guidance 兜底。
- NeoForge 独有、Forge 侧没有对应概念的类，新建 `neoforge.<领域>.<SimpleName>` id（如 `neoforge.registries.DeferredHolder`），
  必须在交付报告中列出。
- 这样做的收益：将来实现 Forge ↔ NeoForge 跨加载器转换时，加载器层的 IR 天然对齐。

### 2.1 `neoforge.*` id 清单（封闭集合）

以下为当前全部 `neoforge.*` 类 IR id（括号内为映射到该 FQCN 的版本范围，含别名）。新增任何一条都必须在交付报告中列出，
并同步补进本清单；`mappings/tools/validate_mappings.py` 会检查 NeoForge 数据集只使用清单内的 id（E-neoforgeIr）。
某版本没有对应类时不写进 classes.json，由 guidance 说明。

```
neoforge.attachment.AttachmentType            net.neoforged.neoforge.attachment.AttachmentType（1.20.3–26.2）
neoforge.bus.ICancellableEvent                net.neoforged.bus.api.ICancellableEvent（1.20.2–26.2）
neoforge.capabilities.BlockCapability         net.neoforged.neoforge.capabilities.BlockCapability（1.20.3–26.2）
neoforge.capabilities.EntityCapability        net.neoforged.neoforge.capabilities.EntityCapability（1.20.3–26.2）
neoforge.capabilities.ItemCapability          net.neoforged.neoforge.capabilities.ItemCapability（1.20.3–26.2）
neoforge.client.BlockStateProvider            net.minecraftforge.client.model.generators.BlockStateProvider（1.20.1）；net.neoforged.neoforge.client.model.generators.BlockStateProvider（1.20.2–1.21.3）
neoforge.client.ItemModelProvider             net.minecraftforge.client.model.generators.ItemModelProvider（1.20.1）；net.neoforged.neoforge.client.model.generators.ItemModelProvider（1.20.2–1.21.3）
neoforge.client.RegisterClientReloadListenersEvent net.minecraftforge.client.event.RegisterClientReloadListenersEvent（1.20.1）；net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent（1.20.2–1.21.3）；net.neoforged.neoforge.client.event.AddClientReloadListenersEvent（1.21.4–26.2）
neoforge.client.RegisterDimensionSpecialEffectsEvent net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent（1.20.1）；net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent（1.20.2–1.21.10）
neoforge.client.RegisterMenuScreensEvent      net.neoforged.neoforge.client.event.RegisterMenuScreensEvent（1.20.4–26.2）
neoforge.client.RegisterShadersEvent          net.minecraftforge.client.event.RegisterShadersEvent（1.20.1）；net.neoforged.neoforge.client.event.RegisterShadersEvent（1.20.2–1.21.4）
neoforge.client.RenderHighlightEvent          net.minecraftforge.client.event.RenderHighlightEvent（1.20.1）；net.neoforged.neoforge.client.event.RenderHighlightEvent（1.20.2–1.21.8）
neoforge.crafting.DataComponentIngredient     net.neoforged.neoforge.common.crafting.DataComponentIngredient（1.20.5–26.2）
neoforge.event.AddReloadListenerEvent         net.minecraftforge.event.AddReloadListenerEvent（1.20.1）；net.neoforged.neoforge.event.AddReloadListenerEvent（1.20.2–1.21.3）；net.neoforged.neoforge.event.AddServerReloadListenersEvent（1.21.4–26.2）
neoforge.event.VillagerTradesEvent            net.minecraftforge.event.village.VillagerTradesEvent（1.20.1）；net.neoforged.neoforge.event.village.VillagerTradesEvent（1.20.2–1.21.11）
neoforge.event.WandererTradesEvent            net.minecraftforge.event.village.WandererTradesEvent（1.20.1）；net.neoforged.neoforge.event.village.WandererTradesEvent（1.20.2–1.21.11）
neoforge.network.IPayloadContext              net.neoforged.neoforge.network.handling.IPayloadContext（1.20.4–26.2）
neoforge.network.NeoForgeStreamCodecs         net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs（1.20.5–26.2）
neoforge.network.PayloadRegistrar             net.neoforged.neoforge.network.registration.IPayloadRegistrar（1.20.4）；net.neoforged.neoforge.network.registration.PayloadRegistrar（1.20.5–26.2）
neoforge.network.RegisterPayloadHandlersEvent net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent（1.20.4）；net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent（1.20.5–26.2）
neoforge.registries.DeferredBlock             net.neoforged.neoforge.registries.DeferredBlock（1.20.2–26.2）
neoforge.registries.DeferredItem              net.neoforged.neoforge.registries.DeferredItem（1.20.2–26.2）
neoforge.registries.NeoForgeRegistries        net.neoforged.neoforge.registries.NeoForgeRegistries（1.20.2–26.2）
```
### 2.2 `neoforge.*` id 在 Forge 数据集中复用的规则

- `neoforge.*` id 默认只属于 NeoForge；**不得**因为名字相似就自动用于 Forge。
- 只有同时满足以下条件时，才允许在 Forge 数据集里复用同一个 `neoforge.*` id：
  1. Forge 侧存在同名或直接对应的类，且两者是同一加载器概念的连续演化，而不是只靠文本相似猜测；
  2. 该 id 已在上方封闭清单中列出，并且 Forge / NeoForge 各版本都映射到真实存在的 FQCN；
  3. 两侧类名或包名确有差异时，必须在对应 `classes.json` 条目附 `note`，说明差异或 API 语义变化。
- 当前已按此规则复用的 id 包括：
  `neoforge.client.BlockStateProvider`、`neoforge.client.ItemModelProvider`、
  `neoforge.client.RegisterClientReloadListenersEvent`、`neoforge.client.RegisterDimensionSpecialEffectsEvent`、
  `neoforge.client.RegisterShadersEvent`、`neoforge.client.RenderHighlightEvent`、
  `neoforge.event.AddReloadListenerEvent`、`neoforge.event.VillagerTradesEvent`、
  `neoforge.event.WandererTradesEvent`。
- 若 Forge 侧没有对应物，或对应关系不能闭合到每个版本的真实类，继续只保留在 NeoForge 数据集；Forge 侧由 `guidance` 说明，而不是把 id 映射到猜测的 FQCN。

### 2.3 `neoforge.*` id 清单的封闭性

`neoforge.*` id 清单是封闭集合；新增、删除或让某个 id 跨到 Forge 数据集，都必须同时更新本清单、交付报告和 `validate_mappings.py` 的校验范围。仅在个别版本里出现的临时 id 不允许悄悄扩展清单。

## 3. version.json 约定

```jsonc
{
  "mcVersion": "1.21.1",
  "loader": "neoforge",
  "javaVersion": 21,
  "metadataFormat": "mods.toml",                                   // 解析/生成器沿用 mods.toml 格式
  "metadataPath": "src/main/resources/META-INF/neoforge.mods.toml", // 1.20.5 起文件名为 neoforge.mods.toml；更早为 mods.toml
  "langFormat": "json",
  "langKeys": { "block": "block.{modid}.{name}", "item": "item.{modid}.{name}", "group": "itemGroup.{name}" },
  "texturePrefixes": { "block": "block/", "item": "item/" },
  "modAnnotationStyle": "value",
  "lifecycleStyle": "modBus",
  "packFormat": 34,
  "forgeVersion": "21.1.x",          // 此字段在 NeoForge 数据集中存放 NeoForge 版本号（模板占位符 ${forgeVersion}）
  "loaderVersionRange": "[4,)",      // 写入 mods.toml 的 loaderVersion（FML 版本范围，按官方 MDK 核实）
  "mappingsChannel": "official",
  "gradleVersion": "8.x",
  "extras": { }                      // 如 ModDevGradle / NeoGradle 插件版本、neoforge 版本范围，按模板需要自定
}
```

## 4. templates/

- `templates/mods.toml`：NeoForge 元数据模板（`metadataFormat` 为 `mods.toml` 时引擎读取此文件名，
  输出路径由 `metadataPath` 决定）。依赖项 `modId="neoforge"`；按版本核实 `[[dependencies]]` 的
  `type="required"` / `mandatory=true` 写法。
- `templates/build.gradle`：按该版本官方 MDK 的插件（ModDevGradle 或 NeoGradle）编写。
- 可用占位符同 Forge：`${modid} ${name} ${version} ${description} ${authors} ${url} ${logoFile} ${credits}
  ${group} ${mcVersion} ${forgeVersion} ${loaderVersionRange} ${mappingsChannel} ${gradleVersion} ${javaVersion}`
  及 `extras` 中的键。

## 5. 自检

DATA-RULES 第 4 条，另加：`forge.SubscribeEvent`、`forge.Mod` 在每个数据集中都已映射。
