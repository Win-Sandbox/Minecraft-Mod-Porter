package io.modporter.passes;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
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
 * AnnotationMigrationPass 的 main 式回归测试（无 JUnit）。
 * 运行：java -cp <classes> io.modporter.passes.AnnotationMigrationPassMainTest（期望 ALL PASS）。
 * 本文件只做静态检查，不随构建自动运行。
 */
public final class AnnotationMigrationPassMainTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testForwardClassAndBusRename();
        testDeleteBusWhenTargetMissing();
        testReverseMissingBusTodo();
        testSameNameCustomAnnotationIgnored();
        testUnknownBusValueKept();
        testFullyQualifiedAnnotationAndValue();
        testSameFormsNoOp();
        testSharedOuterImportIsPreserved();
        testSameNameDeclarationIsConservative();
        testConflictingTargetImportIsConservative();
        testNestedOuterExpressionIsConservative();
        reportDone();
    }

    static void testForwardClassAndBusRename() {
        String source = "import net.neoforged.fml.common.Mod;\n\n"
                + "@Mod.EventBusSubscriber(modid = \"demo\", bus = Mod.EventBusSubscriber.Bus.FORGE)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("@EventBusSubscriber"), "forward: 注解名改为顶层");
        assertTrue(out.contains("net.neoforged.fml.common.EventBusSubscriber.Bus.GAME"),
                "forward: 枚举目标宿主必须使用 FQCN，不能依赖嵌套简单名 import");
        assertTrue(out.contains("import net.neoforged.fml.common.EventBusSubscriber;"),
                "forward: 新增顶层注解 import");
        assertTrue(!out.contains("@Mod.EventBusSubscriber"), "forward: 旧限定名已删除");
    }

    static void testDeleteBusWhenTargetMissing() {
        String source = "import net.neoforged.fml.common.EventBusSubscriber;\n\n"
                + "@EventBusSubscriber(modid = \"demo\", bus = EventBusSubscriber.Bus.GAME)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.TOP_LEVEL, Style.NO_BUS);
        String out = transform(ctx, source);
        assertTrue(!out.contains("bus ="), "delete: bus 属性已删除");
        assertTrue(out.contains("@EventBusSubscriber"), "delete: 注解本身保留");
        assertTrue(!out.contains("Bus.GAME"), "delete: 旧 bus 值已删除");
    }

    static void testReverseMissingBusTodo() {
        String source = "import net.neoforged.fml.common.EventBusSubscriber;\n\n"
                + "@EventBusSubscriber(modid = \"demo\")\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.NO_BUS, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(!out.contains("bus ="), "reverse: 不默认补 bus");
        assertTrue(hasTodo(ctx), "reverse: 缺 bus 应有 TODO");
    }

    static void testSameNameCustomAnnotationIgnored() {
        String source = "import com.example.EventBusSubscriber;\n\n"
                + "@EventBusSubscriber(modid = \"demo\", bus = EventBusSubscriber.Bus.FORGE)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.TOP_LEVEL, Style.NO_BUS);
        String out = transform(ctx, source);
        assertTrue(out.contains("com.example.EventBusSubscriber"), "custom: 自定义同名注解 import 保留");
        assertTrue(out.contains("bus ="), "custom: 自定义注解属性不被删");
        assertTrue(!hasTodo(ctx), "custom: 不产生误报");
    }

    static void testUnknownBusValueKept() {
        String source = "import net.neoforged.fml.common.EventBusSubscriber;\n"
                + "import net.neoforged.fml.common.EventBusSubscriber.Bus;\n\n"
                + "@EventBusSubscriber(modid = \"demo\", bus = Bus.OTHER)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.TOP_LEVEL, Style.NO_BUS);
        String out = transform(ctx, source);
        assertTrue(out.contains("bus = Bus.OTHER"), "unknown: 未核实 bus 值保留");
        assertTrue(hasTodo(ctx), "unknown: 未核实 bus 值应有 TODO");
    }

    static void testFullyQualifiedAnnotationAndValue() {
        String source = "@net.neoforged.fml.common.Mod.EventBusSubscriber(modid = \"demo\",\n"
                + "        bus = net.neoforged.fml.common.Mod.EventBusSubscriber.Bus.FORGE)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("@net.neoforged.fml.common.EventBusSubscriber"),
                "fqcn: 注解改写为顶层 FQCN");
        assertTrue(out.contains("net.neoforged.fml.common.EventBusSubscriber.Bus.GAME"),
                "fqcn: FORGE 改为 GAME");
    }

    static void testSameFormsNoOp() {
        String source = "import net.neoforged.fml.common.EventBusSubscriber;\n\n"
                + "@EventBusSubscriber(modid = \"demo\", bus = EventBusSubscriber.Bus.GAME)\n"
                + "class Demo {}\n";
        PortContext ctx = context(Style.TOP_LEVEL, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("@EventBusSubscriber(modid = \"demo\", bus = EventBusSubscriber.Bus.GAME)"),
                "noop: 形态相同不改写");
        assertTrue(!out.contains("net.neoforged.fml.common.EventBusSubscriber.Bus.GAME"),
                "noop: 源/目标枚举宿主与常量相同，不应无谓改成 FQCN");
        assertTrue(!hasTodo(ctx), "noop: 不产生 TODO");
    }

    static void testSharedOuterImportIsPreserved() {
        String source = "import net.neoforged.fml.common.Mod;\n\n"
                + "@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)\n"
                + "@Mod(\"demo\") class Demo {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("import net.neoforged.fml.common.Mod;"),
                "shared-import: Mod import must remain for the other annotation");
        assertTrue(out.contains("net.neoforged.fml.common.EventBusSubscriber.Bus.GAME"),
                "shared-import: enum target expression must be FQCN");
        assertTrue(!out.contains("bus = EventBusSubscriber.Bus.GAME"),
                "shared-import: must not rely on old Mod/simple outer import");
    }

    static void testSameNameDeclarationIsConservative() {
        String source = "import net.neoforged.fml.common.Mod;\n\n"
                + "class EventBusSubscriber {}\n"
                + "@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE) class Demo {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("import net.neoforged.fml.common.Mod;"),
                "same-name: outer import remains available");
        assertTrue(out.contains("@net.neoforged.fml.common.EventBusSubscriber"),
                "same-name: target annotation uses FQCN rather than shadowed simple name");
    }

    static void testConflictingTargetImportIsConservative() {
        String source = "import net.neoforged.fml.common.Mod;\n"
                + "import com.example.EventBusSubscriber;\n\n"
                + "@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE) class Demo {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("@net.neoforged.fml.common.EventBusSubscriber"),
                "conflict-import: target annotation uses FQCN");
        assertTrue(out.contains("net.neoforged.fml.common.EventBusSubscriber.Bus.GAME"),
                "conflict-import: enum target expression uses FQCN");
        assertTrue(out.contains("import com.example.EventBusSubscriber;"),
                "conflict-import: unrelated import remains untouched");
    }
    static void testNestedOuterExpressionIsConservative() {
        String source = "import net.neoforged.fml.common.Mod;\n\n"
                + "class Demo { Object value = Mod.Nested.value; }\n"
                + "@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE) class Marker {}\n";
        PortContext ctx = context(Style.OLD_INNER, Style.TOP_LEVEL);
        String out = transform(ctx, source);
        assertTrue(out.contains("Mod.Nested.value"),
                "nested-expression: unrelated outer expression is preserved");
        assertTrue(out.contains("import net.neoforged.fml.common.Mod;"),
                "nested-expression: outer import is preserved");
    }

    private static String transform(PortContext ctx, String source) {
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new AssertionError("测试源码解析失败: " + result.getProblems());
        }
        CompilationUnit cu = result.getResult().get();
        new AnnotationMigrationPass(ctx).transform("Demo.java", cu);
        return cu.toString();
    }

    private static boolean hasTodo(PortContext ctx) {
        return ctx.report.count(Report.Severity.TODO) > 0;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + message);
        }
    }

    private static void reportDone() {
        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    private enum Style { OLD_INNER, TOP_LEVEL, NO_BUS }

    private static PortContext context(Style sourceStyle, Style targetStyle) {
        VersionMappings source = mappings(sourceStyle);
        VersionMappings target = mappings(targetStyle);
        MappingResolver resolver = new MappingResolver(source, target);
        PortRequest request = new PortRequest(Path.of("."), Path.of("."),
                "neoforge", sourceStyle.name(), targetStyle.name(), false);
        Report report = new Report();
        return new PortContext(request, resolver, report);
    }

    private static VersionMappings mappings(Style style) {
        String annoFqcn = style == Style.OLD_INNER
                ? "net.neoforged.fml.common.Mod.EventBusSubscriber"
                : "net.neoforged.fml.common.EventBusSubscriber";
        String busFqcn = style == Style.OLD_INNER
                ? "net.neoforged.fml.common.Mod.EventBusSubscriber.Bus"
                : "net.neoforged.fml.common.EventBusSubscriber.Bus";

        Map<String, VersionMappings.ClassEntry> classes = Map.of(
                "forge.Mod.EventBusSubscriber", new VersionMappings.ClassEntry(annoFqcn, null),
                "forge.Mod.EventBusSubscriber.Bus", new VersionMappings.ClassEntry(busFqcn, null));

        Map<String, VersionMappings.MemberEntry> busMembers =
                style == Style.OLD_INNER
                        ? Map.of(
                        "FORGE", new VersionMappings.MemberEntry("FORGE", "field", null),
                        "MOD", new VersionMappings.MemberEntry("MOD", "field", null))
                        : Map.of(
                        "GAME", new VersionMappings.MemberEntry("GAME", "field", null),
                        "MOD", new VersionMappings.MemberEntry("MOD", "field", null),
                        "FORGE", new VersionMappings.MemberEntry("GAME", "field", null));
        Map<String, Map<String, VersionMappings.MemberEntry>> members =
                style == Style.NO_BUS
                        ? Map.of()
                        : Map.of("forge.Mod.EventBusSubscriber.Bus", busMembers);

        Map<String, VersionMappings.AnnotationAttributeForm> attrs =
                style == Style.NO_BUS
                        ? Map.of()
                        : Map.of("bus", new VersionMappings.AnnotationAttributeForm(
                        "bus", "bus", "forge.Mod.EventBusSubscriber.Bus"));
        Map<String, VersionMappings.AnnotationForm> annotationForms = Map.of(
                "forge.Mod.EventBusSubscriber",
                new VersionMappings.AnnotationForm(annoFqcn, attrs));

        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.mcVersion = style.name();
        info.loader = "neoforge";
        info.javaVersion = 17;

        return new VersionMappings(Path.of("."), List.of(), info,
                classes, members, Map.of(), Map.of(), Map.of(), Map.of(), Set.of(),
                annotationForms);
    }
}
