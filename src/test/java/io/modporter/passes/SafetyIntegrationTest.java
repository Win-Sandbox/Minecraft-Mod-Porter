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

/** Cross-component safety checks. Source-only delivery; not compiled or run in this change. */
public final class SafetyIntegrationTest {
    public static void main(String[] args) {
        checkRemovedResolutionIsConservative();
        String out = transform("import game.Old; class C { void f(Old x, third.Peer y) { x.before(); y.before(); } }", false);
        check(out.contains("x.after()"), "known owner was not renamed");
        check(out.contains("y.before()"), "third-party method was renamed");
        out = transform("import game.New; class C { void f(New x, third.Peer y) { x.after(); y.after(); } }", true);
        check(out.contains("x.before()") && out.contains("y.after()"), "reverse owner constraint broken");
        out = transform("import game.Old; class C { Old x; void f(third.Peer x) { x.before(); this.x.before(); } }", false);
        check(out.contains("x.before()"), "parameter did not shadow field");
        check(out.contains("this.x.after()"), "explicit field owner not resolved");
        out = transform("import game.Old; class C { void f(Old x) { x.before(); } void g(third.Peer x) { x.before(); } }", false);
        check(out.contains("x.after()") && out.contains("x.before()"), "cross-method variable leakage");
        out = transform("import game.Old; class C { void f(Old x) { { third.Peer y = null; y.before(); } x.before(); } }", false);
        check(out.contains("y.before()") && out.contains("x.after()"), "block scope leakage");
        out = transform("import game.Old; class C { void f(Old Old) { Old.before(); } }", false);
        check(out.contains("Old.after()"), "uppercase variable mistaken for a type");
        out = transform("import game.Old; class C { void f(third.Peer Old) { Old.before(); } }", false);
        check(out.contains("Old.before()"), "uppercase variable rewritten through import");
        out = transform("class C { void f() { factory().before(); } Object factory() { return null; } }", false);
        check(out.contains("factory().before()"), "unknown chained receiver guessed");
        out = transform("import game.Old; class C extends Old { @Override public void before() {} void f() { before(); this.before(); super.before(); } }", false);
        check(out.contains("void after()") && out.contains("super.after()"), "override migration missing");
        out = transform("import game.Old; class C extends Old { @Override public void before() {} class Inner extends third.Peer { @Override public void before() {} void f() { before(); } } }", false);
        check(out.contains("void after()") && out.contains("void before()"), "override rename crossed nested class boundary");
        out = transform("import game.Old; class C { void f(Old x) { x.before(); } }", false);
        check(out.contains("x.before()"), "static receiver matched a value name");
        out = transformWithReceiver("import game.Old; class C { void f() { Old.before(); } }", false,
                new VersionMappings.Receiver("static", "game.Old", List.of()));
        check(out.contains("Old.after()"), "imported static owner did not match exactly");
        out = transformWithReceiver("import game.Old; class C { void f() { factory().before(); } }", false,
                new VersionMappings.Receiver("chain", null, List.of("factory()")));
        check(out.contains("factory().before()"), "unknown chain owner was guessed");
        out = transformWithReceiver("import game.Old; class C { void f() { other().before(); } }", false,
                new VersionMappings.Receiver("chain", null, List.of("factory()")));
        check(out.contains("other().before()"), "mismatched chain receiver was rewritten");
        out = transform("import game.Old; class C { void f() { Old.before(); } }", false);
        check(out.contains("Old.after()"), "ordinary owner mapping regressed");
        System.out.println("SafetyIntegrationTest: ALL PASSED");
    }
    private static void checkRemovedResolutionIsConservative() {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = 17;
        VersionMappings.RemovedEntry sourceInventory = new VersionMappings.RemovedEntry(
                "platform.logging", "source inventory only");
        VersionMappings source = new VersionMappings(Path.of("."), List.of(), info,
                Map.of(), Map.of(), Map.of("org.slf4j.Logger", sourceInventory), Map.of(),
                Map.of(), Map.of(), Set.of());

        VersionMappings.RemovedEntry sameConcept = new VersionMappings.RemovedEntry(
                "platform.logging", "same inventory concept");
        VersionMappings targetSameConcept = new VersionMappings(Path.of("."), List.of(), info,
                Map.of(), Map.of(), Map.of("org.slf4j.Logger", sameConcept), Map.of(),
                Map.of(), Map.of(), Set.of());
        MappingResolver.ClassResolution mapped = new MappingResolver(source, targetSameConcept)
                .resolveClass("org.slf4j.Logger");
        check(mapped.kind == MappingResolver.ClassResolution.Kind.MAPPED,
                "same FQCN and same concept inventory must remain MAPPED");

        VersionMappings.RemovedEntry differentConcept = new VersionMappings.RemovedEntry(
                "different.concept", "different inventory concept");
        VersionMappings targetDifferentConcept = new VersionMappings(Path.of("."), List.of(), info,
                Map.of(), Map.of(), Map.of("org.slf4j.Logger", differentConcept), Map.of(),
                Map.of(), Map.of("different.concept", "different guidance"), Set.of());
        MappingResolver.ClassResolution different = new MappingResolver(source, targetDifferentConcept)
                .resolveClass("org.slf4j.Logger");
        check(different.kind == MappingResolver.ClassResolution.Kind.UNKNOWN,
                "different concept inventory must remain UNKNOWN");
        check("different guidance".equals(different.note),
                "different concept guidance was not preserved");

        VersionMappings targetWithoutEvidence = new VersionMappings(Path.of("."), List.of(), info,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("platform.logging", "目标侧指导"), Set.of());
        MappingResolver.ClassResolution unknown = new MappingResolver(source, targetWithoutEvidence)
                .resolveClass("org.slf4j.Logger");
        check(unknown.kind == MappingResolver.ClassResolution.Kind.UNKNOWN,
                "target inventory absence must remain UNKNOWN");
        check("目标侧指导".equals(unknown.note), "UNKNOWN did not preserve target guidance");
    }

    private static String transform(String code, boolean reverse) {
        VersionMappings a = mapping(false), b = mapping(true);
        Report report = new Report();
        PortContext ctx = new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "forge", "a", "b", true),
                new MappingResolver(reverse ? b : a, reverse ? a : b), report);
        String result = new JavaSourcePass(ctx).transform("C.java", code);
        check(report.count(Report.Severity.ERROR) == 0, "source parse failed: " + code);
        return result;
    }
    private static String transformWithReceiver(String code, boolean reverse, VersionMappings.Receiver receiver) {
        VersionMappings a = mapping(false, receiver), b = mapping(true, receiver);
        Report report = new Report();
        PortContext ctx = new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "forge", "a", "b", true),
                new MappingResolver(reverse ? b : a, reverse ? a : b), report);
        String result = new JavaSourcePass(ctx).transform("C.java", code);
        check(report.count(Report.Severity.ERROR) == 0, "source parse failed: " + code);
        return result;
    }
    private static VersionMappings mapping(boolean target) {
        return mapping(target, null);
    }
    private static VersionMappings mapping(boolean target, VersionMappings.Receiver receiver) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = 17;
        return new VersionMappings(Path.of("."), List.of(), info,
                Map.of("mc.owner", new VersionMappings.ClassEntry(target ? "game.New" : "game.Old", null)),
                Map.of("mc.owner", Map.of(
                        "operation", new VersionMappings.MemberEntry(target ? "after" : "before", "method", null, receiver),
                        "count", new VersionMappings.MemberEntry(target ? "size" : "count", target ? "method" : "field", null))),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
