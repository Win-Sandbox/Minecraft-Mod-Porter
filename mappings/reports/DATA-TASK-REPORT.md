# TASK-REPORT：26.2 谓词迁包与契约规则（data 子任务）

## 交付范围

- 已核实 TODO 3.1 点名的 4 个 26.2 进度谓词迁包。
- 已补 `NEOFORGE-IR-CONTRACT.md` 中 `neoforge.*` id 在 Forge 数据集复用的契约规则。
- 已导出 `DATA-AUDIT.md` 与 `mappings/sources/26.2-predicates.md`。
- 未修改 `README.md`、`TODO.md`、`build.gradle`、`idioms.json`、`version.json`、Java 源码或 `validate_mappings.py`。
- 未编译，未运行 Java / Gradle / javac / Maven / dotnet / CMake。

## 已完成

1. **核实 4 个 26.2 谓词迁包**：
   - `FoodPredicate` → `net.minecraft.advancements.predicates.FoodPredicate`
   - `InputPredicate` → `net.minecraft.advancements.predicates.InputPredicate`
   - `SheepPredicate` → `net.minecraft.advancements.predicates.entity.SheepPredicate`
   - `DataComponentMatchers` → `net.minecraft.advancements.predicates.DataComponentMatchers`
   - 证据来自 `ref/auto-porter/src/main/java/com/autoporter/ApiChangeRule.java` 与 `ref/knowledge-base/minecraft/26.1.2_to_26.2.md`。
2. **验证后判定不能直接合并**：
   - 曾临时补入 `fabric/26.2/classes.json` 与 `neoforge/26.2/classes.json`。
   - `validate_mappings.py` 从 `ERROR 0 / WARN 0` 变为 `ERROR 320 / WARN 4`，原因是新 IR 在旧版本缺 guidance、且 id 命名不符合当前官方 FQCN 规则。
   - 已回滚，并复跑校验恢复 `ERROR 0 / WARN 0`。
3. **补契约规则**：
   - 明确 `neoforge.*` id 默认不跨 Forge；只有同一加载器概念连续演化、封闭清单内、每个版本可闭合到真实 FQCN 时才可复用。
   - 列出当前 9 个已复用 id，并说明差异必须写 `note`。
   - 强调清单封闭，新增或跨数据集复用必须同步清单、交付报告和校验范围。

## 未完成 / 待主代理决策

1. 4 个谓词迁包需要先决定 IR id（建议 `mc.advancements.predicates.*` / `mc.advancements.predicates.entity.*`），再一次性补齐所有版本 guidance；本子任务不能改 `idioms.json`。
2. Fabric `FluidRenderHandler` / `FluidRenderHandlerRegistry` 尚未核实到足够的一手来源，本轮未加入映射。
3. TODO 3.1 / 3.2 / 3.4 中其它未列出的条目未逐条核实，不能宣称完成。
4. NeoGradle / settings.gradle 支持仍属引擎或模板能力，不在本子任务修改范围。

## 验证

- 基线 `python3 mappings/tools/validate_mappings.py`：`ERROR 0 / WARN 0`。
- 临时补入 4 条后：`ERROR 320 / WARN 4`，详见 `DATA-AUDIT.md`。
- 回滚并复验：`ERROR 0 / WARN 0`。
- 仅使用 Python 做只读校验与 JSON 格式检查；未编译、未运行 Java。
