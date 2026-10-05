# 安全迁移更新（2026-10-04）

本轮处理：①成员/Override误改，②Java21/25解析兼容，③Mixin与Access Widener引用迁移。
**严格未编译，未执行Java回归测试，也未启动项目或转换真实Mod工程。**

## 实现

### 成员与 Override
- 新增 `SafeMemberPass`、`OwnerResolver`，在类名/import改写前以源类型解析。
- 显式import、词法局部变量/参数/字段、cast/new、直接继承可用于限定源owner；未知接收者保留并TODO。
- 参数遮蔽字段、块级声明顺序、大写变量、嵌套/相邻/匿名类边界有保守保护。
- Override仅在直接父类/接口可确认时修改，调用同步以AST声明类身份隔离；同名重载保留。
- 成员索引保留no-op候选，owner查找不把内部类误当外部类。
- 语义note、方法返回值链、传递继承、复杂泛型等不猜；字段写入/自增/自减不改成getter。
- 方法/字段访问列表预先快照，生成节点不参与二次迁移。

### Java
- JavaParser 3.25.8 → 3.28.2；从Maven发布源码核对语言枚举、validator、API，库目标字节码保持Java8兼容，项目仍要求Java17。
- 精确Java8–26级别，未知版本诊断并保留源码。
- Java22+未命名`_`不自动改名，低版本（包括Java8）诊断；Java8普通`_`升级改名避开现有名称。
- record/switch模式、未命名模式及部分Java25语法降级诊断。
- 上游Java25 validator把compact class/flexible constructor body标为WIP，因此不保证全部Java25语法。

上游参考：
- https://repo.maven.apache.org/maven2/com/github/javaparser/javaparser-core/3.28.2/javaparser-core-3.28.2-sources.jar
- https://github.com/javaparser/javaparser/tree/javaparser-parent-3.28.2

### Mixin / Access Widener
- 严格确认Sponge注解身份；不依据文件名或注解简单名猜测。
- Mixin源target先快照，支持字符串/类字面量、method数组、多target一致结果、完整`@At(INVOKE/INVOKE_ASSIGN/FIELD)`引用。
- JVM字段/方法描述符验证包括空参数、数组、返回void、内部类；未知类/成员/语义note/形态变化时整条保留。
- `remap=false`的注解字符串保留。refmap、MixinExtras、Accessor/Invoker/NEW等未覆盖形态不自动迁移。
- AW支持v1/v2、v2 transitive、class/field/method，逐行原子改写，保持注释/空白/换行。
- AW仅自动处理Fabric Yarn `named → named`。`official/intermediary`或跨命名空间整文件保留并TODO；不能把Mojang mappingsChannel误当AW运行期namespace。

## 验证范围

- tree-sitter Java **仅解析语法**：main+test共31个Java文件，无语法错误。
- JavaParser显式import与发布源码路径核对通过；关键API人工对照上游源码。
- Python mappings校验：ERROR 0 / WARN 0，81可选版本；映射JSON未改动。
- 新增5个无JUnit依赖的回归入口，命令为`gradle safetyRegression`。**该命令会编译，本轮没有执行**。普通`gradle test`不会自动执行这些main入口。
- 静态检查不能发现全部Java类型检查/重载/API兼容问题；也不能证明转换产物可编译或运行。

## 剩余限制

- 没有完整SymbolSolver/MC继承模型，也没有成员重载描述符schema；安全修复减少误改，会增加保守保留/TODO。
- 本輪不增加guide-only的通用成员映射、不处理新pack.mcmeta或物品资源格式（原问题4），不改前端。
- 复杂类型名遮蔽、其他既存class/idiom重写逻辑仍可能需后续完善，不宣称全引擎语义安全。
- 内部类FQCN→JVM名依赖包小写/类大写惯例并做源往返校验；不符合惯例时保留。

## 并行与审核

实际派发3个独立子会话（最初2×Sonnet5、1×GLM5.3）。Sonnet任务停滞后改由GLM接手；没有更改全局模型配置。子会话只修改各自隔离副本。原始产物单独归档，不能直接当作最终补丁使用。

主代理审查发现未知owner仍被改、no-op只改文档未改索引、Override作用域错误、描述符验证错误、namespace猜测等问题；因此只采纳可用API/集成思路，其余实现修订或重写后统一交付。没有将子代理完成报告等同于测试通过。

原项目备份和最终补丁位于共享目录 `modporter/20261004-safety-update/`。
