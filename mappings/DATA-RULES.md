# 映射数据通用规则（所有加载器、所有数据集强制遵守）

加载器专有约定见 [FABRIC-IR-CONTRACT.md](FABRIC-IR-CONTRACT.md)、[NEOFORGE-IR-CONTRACT.md](NEOFORGE-IR-CONTRACT.md)。
本文件规定跨加载器通用的 id 命名、来源与交付要求。

## 1. 新增 `mc.*` IR id 的命名（确定性规则）

多人/多 agent 并行时，同一个原版类必须得到同一个 id，因此新 id 不允许自由命名：

1. **先查重**：在 `versions/*/*/classes.json` 中搜索该类的 Mojang 官方名 FQCN
   （Forge / NeoForge / Fabric 26.x 数据集用的都是官方名）。已有 id 则直接复用。
2. **没有才新建**，id = `mc.` + 官方名 FQCN 去掉 `net.minecraft.` 前缀。
   例：`net.minecraft.core.component.DataComponents` → `mc.core.component.DataComponents`。
   Yarn 数据集新增 id 时同样按**官方名**推导（不是按 Yarn 名），保证各加载器对齐。
3. 已有 id 不得改名，即使它不符合第 2 条（历史 id 如 `mc.item.Item`、`mc.world.Level` 保持原样）。
4. 新增的 id 必须在交付报告中列出。

## 2. 新增 concept id

- 优先复用现有 concept（见各数据集 `idioms.json` 的 `guidance` 键）。
- 新建时用 `<领域>.<概念>` 的小写点分形式（如 `item.dataComponents`、`network.payload`），
  加载器特有概念加前缀（`fabric.` / `neoforge.`）。
- 每个数据集的 `guidance` 必须覆盖「全部已知 concept id ∪ 本数据集引用到的 IR 类 id 中目标侧缺失者」。
  新增的 concept 必须在交付报告中列出，由汇总步骤补齐其它数据集的反向 guidance。

## 3. 来源与合规

- 每条映射都必须能追溯到来源。可以用第三方迁移知识库作为事实线索（只取其中的事实），
  但要用**一手来源**核实、纠错、补全：Mojang 官方（版本清单、官方映射、更新日志）、
  FabricMC / NeoForged 官方博客与文档、官方 Maven 元数据、上游 GitHub 仓库的提交/源码、
  Yarn / Parchment 映射文件。线索来源与核实用的一手来源都要登记。
- **不得复制**任何第三方的文字、代码或注释（包括翻译后放入）。`note` / `message` / `guidance`
  一律用自己的话以中文撰写。API 名称、版本号、类名/成员名之间的对应关系属于事实，可直接使用。
- 每个交付范围在 `mappings/sources/<loader>-<范围>.md` 中登记来源：按版本、按条目组列出核实用的 URL。
- 不确定的对应关系**不写入**映射；改为 `removed.json` + guidance，或在报告中列为待核实。
  「宁缺毋滥」与引擎的「不猜」原则一致。

## 4. 交付自检

1. 所有 JSON 通过 `python3 -m json.tool`（这是格式校验，不是编译；**不要运行 gradle / javac / java**）。
2. `basedOn` 指向的版本目录存在或由同批次交付创建；`aliases` 不与现有目录或其它别名重名。
3. `version.json` 中的加载器、映射、构建插件版本号均已在官方 Maven 元数据中核实存在。
4. 覆盖层（`basedOn`）只写与基版本的差异；需要删除基版本条目时使用 `!remove`。
