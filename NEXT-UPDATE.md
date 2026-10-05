# 2026-10-04 本轮变更（next-update）

以下为本轮集成负责人完成的保守实现，**全部未编译、未运行任何测试**（用户禁止编译）：

## 核心（补全 partial-integrated）
- `MemberCandidate` 增加 `sourceReceiver/targetReceiver`；构造调用全部补全；`isNoop()` 含 receiver 相等；新增 `receiverChanged()`。
- `SafeMemberPass`：源 receiver 形态不匹配一律 TODO；仅目标 receiver 为唯一精确 static owner（owner == 目标类 FQCN）时自动改写调用 scope；chain / owner 不匹配 / 目标缺 receiver 全部保留 TODO；字段访问遇任何 receiver 变化保留 TODO。
- `ReferenceMapper`（Mixin/AW）：候选带 receiver 变化即拒绝，整条引用保留。
- `VersionMappings`：`ClassEntry.primary`、重名 FQCN 反查需恰一个 primary、`ambiguousClassNames`；`MemberEntry.receiver`（static 精确 owner / chain 路径）与严格校验；`annotations.json` 的 `AnnotationForm/AnnotationAttributeForm`（兼容构造器保留）。
- `MappingRepository`：`parseReceiver`、primary 布尔校验、annotations.json 合并（含 `!remove` / `annoIr#attrIr`）。
- `MappingResolver.resolveClass`：歧义 FQCN → UNKNOWN+note；removed 类仅当目标显式同 FQCN/同 concept 或 classes.json 含该 FQCN 才保持，不再仅凭 concept 支持。
- `JavaSourcePass`：NeoForge/Fabric 未映射前缀警告、歧义类 TODO note；接入 `AnnotationMigrationPass`。

## 子任务合并
- annotations：`AnnotationMigrationPass`（显式 import/FQCN，不裸名猜测）、annotations.json 模型、4 个 NeoForge 版本数据（Bus 枚举含 FORGE→GAME 改名）、main 回归测试；为无 Bus 映射版本补 guidance 保持 ERROR 0。
- build：`SettingsGradlePass`、3 个 settings.gradle 模板、main 回归测试；`DefaultPortEngine` 最小集成；build.gradle 注册回归入口。
- data：仅合并文档；4 个 26.2 谓词迁包因会引入 ERROR 320/WARN 4（guidance 未补齐、IR id 未决）按子代理结论不合并。
- itemrules：`ItemOverrideRules` 严格子集 helper、main 测试；已由集成负责人接入 `ItemModelDefinitionPass` 升级/降级路径。

## 静态检查
- tree-sitter Java：43 files, 0 syntax errors。
- `validate_mappings.py`：ERROR 0 / WARN 0。
- 未运行任何 Java/Gradle 测试（禁止编译）。

## 审查后修正
- `unambiguous()` 比较 classIr + targetName + targetKind + targetReceiver。
- `SafeMemberPass` 源版本未声明 receiver 时不再自动移动调用（TODO）。
- `AnnotationMigrationPass` 枚举宿主直接 `resolver.targetClass(candidate.classIr)`，不再二次 FQCN 反查。
- `MemberCandidate.note` 保留 source-only note，避免 no-op 吞掉提示。
- `mergeAnnotations()` 严格 schema 校验（未知字段/类型/!remove 格式）。
- `ItemModelDefinitionPass` 无效 overrides + tints 合并 TODO 报告。
- `validate_mappings.py` 新增 annotations/primary/receiver 语义校验（E-annotation/E-receiver；重复 FQCN 改为恰一个 primary，否则 E-dupFqcn），已用临时坏数据验证能报错。

## 未完成
- 尚无真实 receiver 映射数据实例；NeoForge 1.21.7 `PacketDistributor.sendToServer → ClientPacketDistributor` 需一手来源核实后才可写入。
- 无生命周期（Forge init → commonSetup）自动迁移；不凭 concept 生成逆映射。
- 4 个 26.2 谓词迁包、其余 item 模型复杂 schema、跨 loader 迁移仍待后续核实。
- resolveClass removed“目标同 FQCN 同 concept removed 记录视为保持”按移交要求保守保留；其存在性证明强度限制已记录。build.gradle.kts 不在本轮范围。

