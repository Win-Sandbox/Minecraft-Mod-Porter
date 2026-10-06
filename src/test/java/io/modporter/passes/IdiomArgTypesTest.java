package io.modporter.passes;

import io.modporter.core.PortRequest;
import io.modporter.core.Report;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Idiom argTypes（按参数类型匹配重载）回归入口：gradle safetyRegression 之一（编译后运行）。
 *
 * 场景来自 TODO 3.1 ChunkPos（26.1 构造器 -> 静态工厂）：
 *  - new ChunkPos(BlockPos)  -> ChunkPos.containing(pos)
 *  - new ChunkPos(long)      -> ChunkPos.unpack(l)
 * 两者都是单参构造器，arity 无法区分，必须按参数类型匹配。
 * 本测试用 game.Pos / game.Chunk 模拟该模式，不绑定真实 MC 版本数据。
 */
public final class IdiomArgTypesTest {
    public static void main(String[] args) {
        // 升级方向：两个同 arity 构造器按实参类型分流到不同静态工厂。
        // long l 是词法绑定的基本类型参数（ChunkPos(l) 的主要真实形态）。
        String code = "import game.Pos; import game.Chunk; class C {"
                + " void f(Pos p, long l) { game.Chunk a = new Chunk(p); game.Chunk b = new Chunk(l); } }";
        String out = transform(code, false);
        check(out.contains("Chunk.containing(p)"), "BlockPos-shaped arg not dispatched to containing");
        check(out.contains("Chunk.unpack(l)"), "long-bound arg not dispatched to unpack");
        check(!out.contains("new Chunk("), "constructor form left behind");
        // long 字面量同样分流（l 传参的另一种形态）。
        out = transform("import game.Chunk; class C { void f() { game.Chunk a = new Chunk(0L); } }", false);
        check(out.contains("Chunk.unpack(0L)"), "long literal not dispatched to unpack");

        // 词法绑定证明：变量书面类型即匹配来源；本类/父类字段同样可证明。
        out = transform("import game.Pos; import game.Chunk; class C { Pos field; void f() {"
                + " game.Chunk a = new Chunk(field); } }", false);
        check(out.contains("Chunk.containing(field)"), "field binding type not proved");

        // 未导入简单名（java.lang/同包/通配 import）不可证明 -> 保留 + TODO，不改写。
        Report report2 = new Report();
        out = transformWithReport("import game.Chunk; class C { Object o; void f() { game.Chunk a = new Chunk(o); } }",
                false, report2);
        check(out.contains("new Chunk(o)"), "unknown expression type was rewritten");
        check(report2.count(Report.Severity.TODO) > 0, "unproved arg type missing TODO");

        // long 字面量与 int 字面量分流：int 不匹配 long 形态 -> 保留 + TODO。
        Report report3 = new Report();
        out = transformWithReport("import game.Chunk; class C { void f() { game.Chunk a = new Chunk(5); } }",
                false, report3);
        check(out.contains("new Chunk(5)"), "int literal matched long argType");
        check(report3.count(Report.Severity.TODO) > 0, "int/long mismatch missing TODO");

        // 一元负号字面量仍按其字面量类型证明。
        out = transform("import game.Chunk; class C { void f() { game.Chunk a = new Chunk(-3L); } }", false);
        check(out.contains("Chunk.unpack(-3L)"), "negative long literal not proved");

        // 对象创建实参的书面类型即匹配来源。
        out = transform("import game.Pos; import game.Chunk; class C { void f() {"
                + " game.Chunk a = new Chunk(new game.Pos()); } }", false);
        check(out.contains("Chunk.containing(new game.Pos())"), "object-creation arg type not proved");

        // 降级方向：staticCall 源形态（containing/unpack）-> 目标双构造器形态。
        // 目标为 constructor 时 argTypes 不参与目标改写，只用于源侧识别。
        String down = "import game.Pos; import game.Chunk; class C { void f(Pos p) {"
                + " Object a = game.Chunk.containing(p); } }";
        String downOut = transform(down, true);
        check(downOut.contains("new Chunk(p)"), "downgrade containing -> constructor failed");

        // 同 arity 但类型都不明：保持原表达式。
        Report report4 = new Report();
        String both = transformWithReport("import game.Chunk; class C { void f(int x) {"
                + " game.Chunk a = new Chunk(x); } }", false, report4);
        check(both.contains("new Chunk(x)"), "unproved int variable rewritten");
        check(report4.count(Report.Severity.TODO) > 0, "unproved variable missing TODO");

        // 回归：两个不同惯用法嵌套（new Outer(new Inner(x))，Outer/Inner 各自命中惯用法）时，
        // 内外层都必须被改写。旧的“先收集全部位置再统一改写”实现若先处理外层 Outer，
        // emitIdiom 会 clone 实参（含内层 new Inner(x) 原节点）替换外层节点，此后内层原节点
        // 已脱离 CompilationUnit，对它的改写只作用于脱离的旧树、不反映到真实输出，
        // 导致内层未被改写却仍可能误记成功日志。正确实现应按内层优先（后序/深度降序）
        // 确定性顺序处理，使内层先在真实树中替换，外层随后 clone 的实参已含替换结果。
        String nested = "import game.Outer; import game.Inner; class C { void f(int x) {"
                + " Object a = new Outer(new Inner(x)); } }";
        String nestedOut = transform(nested, false);
        check(nestedOut.contains("Outer.wrap(Inner.wrap(x))"),
                "nested idioms not both rewritten (outer-first clone regression): " + nestedOut);
        check(!nestedOut.contains("new Outer(") && !nestedOut.contains("new Inner("),
                "nested idioms left constructor form behind: " + nestedOut);

        // 回归（引擎缺陷修复）：argTypes 是点分源版本 FQCN，且源/目标两侧类名不同
        // （如 Yarn 的 net.minecraft.util.math.BlockPos -> 官方 net.minecraft.core.BlockPos，
        // 这里用 game.OldPos -> game.NewPos 模拟，类映射 IR 相同）。
        // transform() 内 rewriteImports/rewriteSimpleNames 先于 rewriteIdioms 运行，
        // 到 rewriteIdioms 时实参的书面类型已经是目标类名 game.NewPos，但
        // sourceForm.argTypes 记录的是源类名 game.OldPos，直接字符串比较会失配。
        // 必须再用 resolver.resolveClass("game.OldPos") 查出 MAPPED 且
        // targetFqcn == "game.NewPos" 时也判定匹配，new Chunk(pos) 才会被正确改写为
        // 目标静态工厂。
        String renamedClassCode = "import game.OldPos; import game.Chunk; class C {"
                + " void f(OldPos p) { game.Chunk a = new Chunk(p); } }";
        String renamedClassOut = transformRenamedPos(renamedClassCode);
        check(renamedClassOut.contains("Chunk.containing(p)"),
                "renamed-class argType (source FQCN != target FQCN) not dispatched to containing: "
                        + renamedClassOut);
        check(!renamedClassOut.contains("new Chunk("),
                "renamed-class argType left constructor form behind: " + renamedClassOut);

        System.out.println("IdiomArgTypesTest: ALL PASSED");
    }

    /**
     * 升级方向 transform：class 映射把 game.OldPos（源）改名为 game.NewPos（目标）。
     * 每一侧的惯用法 argTypes 写该侧版本自己的 Pos 类名：源侧是 game.OldPos，
     * 目标侧是 game.NewPos（与真实 idioms.json 的记录方式一致）。
     */
    private static String transformRenamedPos(String code) {
        Report report = new Report();
        VersionMappings sourceMappings = renamedPosMapping(false);
        VersionMappings targetMappings = renamedPosMapping(true);
        PortContext ctx = new PortContext(
                new PortRequest(Path.of("in"), Path.of("out"), "fabric", "a", "b", true),
                new MappingResolver(sourceMappings, targetMappings), report);
        String out = new JavaSourcePass(ctx).transform("C.java", code);
        check(report.count(Report.Severity.ERROR) == 0, "source parse failed: " + code);
        return out;
    }

    private static VersionMappings renamedPosMapping(boolean modernSide) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = 21;
        // 源侧（modernSide=false）：class="game.OldPos"；目标侧（modernSide=true）：class="game.NewPos"。
        // chunkpos.containing2 的 argTypes 始终写"该侧自身"的 Pos 类名，这与真实 idioms.json
        // 的记录方式一致（每个版本的 idioms.json 里 argTypes 写的是该版本自己的类名）。
        String posFqcn = modernSide ? "game.NewPos" : "game.OldPos";
        VersionMappings.IdiomForm containing = modernSide
                ? new VersionMappings.IdiomForm("staticCall", "game.Chunk", "containing", 1, List.of(posFqcn))
                : new VersionMappings.IdiomForm("constructor", "game.Chunk", null, 1, List.of(posFqcn));
        return new VersionMappings(Path.of("."), List.of(), info,
                Map.of("game.pos", new VersionMappings.ClassEntry(posFqcn, null),
                        "mc.chunk", new VersionMappings.ClassEntry("game.Chunk", null)),
                Map.of(), Map.of(), Map.of(),
                Map.of("chunkpos.containing2", containing),
                Map.of(), Set.of());
    }

    private static String transform(String code, boolean reverse) {
        Report report = new Report();
        return transformWithReport(code, reverse, report);
    }

    private static String transformWithReport(String code, boolean reverse, Report report) {
        PortContext ctx = new PortContext(
                new PortRequest(Path.of("in"), Path.of("out"), "fabric", "a", "b", true),
                new MappingResolver(mapping(reverse), mapping(!reverse)), report);
        String out = new JavaSourcePass(ctx).transform("C.java", code);
        check(report.count(Report.Severity.ERROR) == 0, "source parse failed: " + code);
        return out;
    }

    private static VersionMappings mapping(boolean modern) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = 21;
        VersionMappings.IdiomForm containing = modern
                ? new VersionMappings.IdiomForm("staticCall", "game.Chunk", "containing", 1, List.of("game.Pos"))
                : new VersionMappings.IdiomForm("constructor", "game.Chunk", null, 1, List.of("game.Pos"));
        VersionMappings.IdiomForm unpack = modern
                ? new VersionMappings.IdiomForm("staticCall", "game.Chunk", "unpack", 1, List.of("long"))
                : new VersionMappings.IdiomForm("constructor", "game.Chunk", null, 1, List.of("long"));
        VersionMappings.IdiomForm outerWrap = modern
                ? new VersionMappings.IdiomForm("staticCall", "game.Outer", "wrap", 1)
                : new VersionMappings.IdiomForm("constructor", "game.Outer", null, 1);
        VersionMappings.IdiomForm innerWrap = modern
                ? new VersionMappings.IdiomForm("staticCall", "game.Inner", "wrap", 1)
                : new VersionMappings.IdiomForm("constructor", "game.Inner", null, 1);
        return new VersionMappings(Path.of("."), List.of(), info,
                Map.of("mc.pos", new VersionMappings.ClassEntry(modern ? "game.Pos2" : "game.Pos", null),
                        "mc.chunk", new VersionMappings.ClassEntry("game.Chunk", null)),
                Map.of(), Map.of(), Map.of(),
                Map.of("chunkpos.containing", containing, "chunkpos.unpack", unpack,
                        "outer.wrap", outerWrap, "inner.wrap", innerWrap),
                Map.of(), Set.of());
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
