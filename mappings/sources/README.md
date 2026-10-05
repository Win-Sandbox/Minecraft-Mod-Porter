# 映射数据来源索引

`mappings/versions/` 下每条映射都要能追溯到来源（规则见 [../DATA-RULES.md](../DATA-RULES.md) 第 3 节）。
本目录按「加载器 + 版本范围」登记核实所用的一手来源。新增事实时，把来源追加到对应文件；没有合适的文件就按
`<loader>-<范围>.md` 新建，并在下表补一行。

| 文件 | 覆盖范围 |
|---|---|
| [fabric-1.15-1.21.1.md](fabric-1.15-1.21.1.md) | Fabric（Yarn）1.15.2 – 1.21.1 全部数据集与别名 |
| [fabric-1.20.5-1.21.11.md](fabric-1.20.5-1.21.11.md) | Fabric（Yarn）1.20.5 – 1.21.11 覆盖层 |
| [fabric-26.x-java25.md](fabric-26.x-java25.md) | Fabric 26.1 – 26.2（官方名），`java/25.json` 与 `java/features.json` 的新增项 |
| [neoforge-1.20.x.md](neoforge-1.20.x.md) | NeoForge 1.20.1 – 1.20.6 |
| [neoforge-1.21-26.x.md](neoforge-1.21-26.x.md) | NeoForge 1.21 – 1.21.11、26.1 – 26.2 |

Forge 数据集（1.12.2 – 1.21.1）所参考的迁移资料列在仓库根目录 README 的「Mapping Sources」一节。

## 一手来源类别

- Mojang：版本清单与各版本 JSON、客户端 jar、官方映射（client_mappings）
- FabricMC：官方博客与文档、Maven 元数据（Yarn、intermediary、Fabric API、Loader、Loom）、GitHub 上游源码
- NeoForged：官方新闻与文档、Maven 元数据、GitHub 上游源码
- Gradle 发行版列表；OpenJDK / Java SE 规范（Java 平台数据）

## 第三方线索的使用声明

部分事实线索来自第三方迁移知识库 `reqsery/mc-mod-porter`。
使用方式仅限于从中取得事实线索（哪些类、成员、版本号发生了变化），没有复制其中的任何文字、代码或注释，
也没有把其内容翻译后放入本仓库。每一条写入的事实都已用上面列出的一手来源核实；与一手来源不符的线索已修正或丢弃，
各文件中分别记录了处理情况。本仓库 `mappings/` 下的 `note`、`message`、`guidance` 都是自行撰写的中文。

## 校验

`python3 mappings/tools/validate_mappings.py` 会检查 JSON 合法性、basedOn / 别名、`!remove` 目标、必需 IR、
version.json 字段、模板与占位符、id 一致性、guidance 覆盖，以及同一加载器内任意两个版本互转时的可达性。
