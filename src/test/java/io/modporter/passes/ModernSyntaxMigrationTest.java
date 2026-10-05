package io.modporter.passes;

import io.modporter.core.PortRequest;
import io.modporter.core.Report;
import io.modporter.engine.PortContext;
import io.modporter.mappings.JavaPlatform;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain Java regression entry point; added but not executed (no compilation requested). */
public final class ModernSyntaxMigrationTest {
    public static void main(String[] args) {
        String unnamed = "class C { void f() { int _ = 1; int _ = 2; } }";
        for (int target : new int[] {8, 17, 21}) {
            PortContext ctx = context(25, target);
            String result = new JavaSourcePass(ctx).transform("C.java", unnamed);
            check(result.contains("int _ = 1") && result.contains("int _ = 2"), "unnamed bindings changed");
            check(ctx.report.count(Report.Severity.TODO) > 0, "missing downgrade TODO for Java " + target);
        }
        PortContext same = context(25, 25);
        String result = new JavaSourcePass(same).transform("C.java", unnamed);
        check(!result.contains("_renamed"), "modern underscore renamed");
        check(same.report.count(Report.Severity.ERROR) == 0, "modern source parse failed");
        check(same.report.count(Report.Severity.TODO) == 0, "same-version TODO noise");

        PortContext old = context(8, 17);
        result = new JavaSourcePass(old).transform("C.java",
                "class C { int f() { int _renamed = 4; int _ = 1; return _ + _renamed; } }");
        check(result.contains("int _renamed_ = 1"), "renaming must avoid occupied identifier");
        check(result.contains("return _renamed_ + _renamed"), "renamed reference mismatch");

        PortContext pattern = context(21, 17);
        new JavaSourcePass(pattern).transform("C.java",
                "class C { record P(int x) {} int f(Object o) { return switch(o) { case P(int x) -> x; default -> 0; }; } }");
        check(pattern.report.count(Report.Severity.ERROR) == 0, "record pattern parsing failed");
        check(pattern.report.count(Report.Severity.TODO) >= 2, "record/switch downgrade undiagnosed");

        PortContext unknown = context(99, 17);
        String raw = "class C { }";
        check(raw.equals(new JavaSourcePass(unknown).transform("C.java", raw)), "unknown level changed source");
        check(unknown.report.count(Report.Severity.ERROR) == 1, "unknown level missing diagnostic");
        System.out.println("ModernSyntaxMigrationTest: ALL PASSED");
    }
    private static PortContext context(int source, int target) {
        PortContext ctx = new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "forge",
                "source", "target", true), new MappingResolver(mapping(source), mapping(target)), new Report());
        ctx.targetJava = new JavaPlatform(target, target >= 9 ? Set.of("_") : Set.of(), Set.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        return ctx;
    }
    private static VersionMappings mapping(int javaVersion) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = javaVersion;
        return new VersionMappings(Path.of("."), List.of(), info, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Set.of());
    }
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
