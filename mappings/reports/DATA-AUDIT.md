# 26.2 进度谓词迁包：审计结果（不合并映射）

范围：`TODO 3.1` 中明确点名的 4 个 26.2 谓词迁包。本文只记录已核实结果与不能合并的原因；本轮没有把这 4 条写入 `classes.json` / `members.json` / `removed.json`。

## 1. 已核实的迁包结果

| 旧 FQCN | 26.2 新 FQCN | 结论 |
|---|---|---|
| `net.minecraft.advancements.criterion.FoodPredicate` | `net.minecraft.advancements.predicates.FoodPredicate` | 已确认，纯迁包 |
| `net.minecraft.advancements.criterion.InputPredicate` | `net.minecraft.advancements.predicates.InputPredicate` | 已确认，纯迁包 |
| `net.minecraft.advancements.criterion.SheepPredicate` | `net.minecraft.advancements.predicates.entity.SheepPredicate` | 已确认，迁入 `predicates.entity` 子包 |
| `net.minecraft.advancements.criterion.DataComponentMatchers` | `net.minecraft.advancements.predicates.DataComponentMatchers` | 已确认，纯迁包 |

证据来源：

1. `ref/auto-porter/src/main/java/com/autoporter/ApiChangeRule.java`
   - 第 797 行：`DataComponentMatchers` → `net.minecraft.advancements.predicates.DataComponentMatchers`
   - 第 808 行：`FoodPredicate` → `net.minecraft.advancements.predicates.FoodPredicate`
   - 第 810 行：`InputPredicate` → `net.minecraft.advancements.predicates.InputPredicate`
   - 第 821 行：`SheepPredicate` → `net.minecraft.advancements.predicates.entity.SheepPredicate`
2. `ref/knowledge-base/minecraft/26.1.2_to_26.2.md`
   - 记录 26.2 的进度谓词包从 `advancements` / `advancements.criterion` 重排为 `advancements.predicates` 与 `advancements.predicates.entity`；本文只取其中的类名与包名事实，未复制描述文字。
3. 本地旧版证据：
   - `ref/knowledge-base/fetched/parchment/data/net/minecraft/advancements/criterion/{FoodPredicate,InputPredicate,SheepPredicate,DataComponentMatchers}.mapping` 证明旧包名存在。
   - `PlayerPredicate.mapping` / `BlockPredicate.mapping` / `ItemPredicate.mapping` 中的方法签名也引用了 `InputPredicate` 和 `DataComponentMatchers`，进一步说明这 4 个类是进度谓词体系的一部分。

## 2. 为什么本轮不写入映射

我曾把这 4 条分别补入 `versions/fabric/26.2/classes.json` 与 `versions/neoforge/26.2/classes.json`，随后按只读方式运行 `python3 mappings/tools/validate_mappings.py`，出现：

```text
E-cross        160  fabric:92, neoforge:68
E-guidance    160  fabric:92, neoforge:68
W-mcId          4
合计：ERROR 320，WARN 4
```

具体原因：

1. 这些类在现有数据集中还没有既定 `mc.*` IR id；新增后所有旧版本都缺 `guidance`，触发 `E-guidance` 与 `E-cross`。
2. 用户明确禁止本轮修改 `idioms.json`，因此无法为 39 个 Fabric 版本、22 个 NeoForge 版本补齐全部反向 guidance。
3. 若改成严格按新官方名新建 `mc.advancements.predicates.*` / `mc.advancements.predicates.entity.*` id，同样会因缺少全版本 guidance 而无法闭合；且这会引入新 IR，超出本轮授权。
4. `validate_mappings.py` 对新增 id 报 `W-mcId`，说明即使按旧 `mc.advancements.critereon.*` 命名也不符合「`mc.` + 当前官方 FQCN」的确定性规则。

因此结论是：**4 个迁包结果可靠，但不能以本轮修改的形式直接合并**。应交由主代理统一评估：
- 是否把 4 个 id 命名为 `mc.advancements.predicates.*` / `mc.advancements.predicates.entity.*`；
- 是否允许一次性补齐所有相关版本的 `guidance`；
- 是否把这 4 条与现有 26.2 进度谓词迁移一起整理成同批新增 IR。

## 3. 已核验的验证输出

基线（未修改前）：

```text
== 数据集统计 ==
  fabric    可选版本  39（full 10, overlay 14, alias 15），同 loader 有向转换路径 1482
  forge     可选版本  20（full 8, overlay 5, alias 7），同 loader 有向转换路径 380
  neoforge  可选版本  22（full 3, overlay 15, alias 4），同 loader 有向转换路径 462
  全局 concept 65 个，类 IR 381 个
== 问题汇总 ==
  无问题
== 合计：ERROR 0，WARN 0 ==
```

临时补入 4 条后的失败摘要：

```text
== 数据集统计 ==
  全局 concept 65 个，类 IR 385 个
== 问题汇总 ==
  ERROR E-cross           160  fabric:92, neoforge:68
  ERROR E-guidance        160  fabric:92, neoforge:68
  WARN  W-mcId              4
      mc.advancements.critereon.DataComponentMatchers
      mc.advancements.critereon.FoodPredicate
      mc.advancements.critereon.InputPredicate
      mc.advancements.critereon.SheepPredicate
== 合计：ERROR 320，WARN 4 ==
```

已回滚到基线，并复跑校验，恢复 `ERROR 0 / WARN 0`。

## 4. 不合并声明

本轮导出的是审计与建议，不是可直接合并的映射数据。请主代理不要把本文中的旧 FQCN→新 FQCN 当成自动改名规则使用；在补齐 guidance 与确定 IR id 之前，这 4 个类应继续由 TODO / guidance 说明。
