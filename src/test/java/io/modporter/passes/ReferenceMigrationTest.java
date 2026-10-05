package io.modporter.passes;

import com.github.javaparser.JavaParser;
import io.modporter.core.*;
import io.modporter.engine.*;
import io.modporter.mappings.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source-only regression entry point: run later with gradle safetyRegression (compiles). */
public final class ReferenceMigrationTest {
    public static void main(String[] args) {
        check(MixinDescriptors.parseMemberRef("run()V") != null, "zero-arg method rejected");
        check(MixinDescriptors.validMethod("([I[[Lgame/Old;)V"), "arrays rejected");
        for (String d : List.of("(V)V", "()[V", "()VV", "(I)", "(L;)V", "(I)Vjunk"))
            check(!MixinDescriptors.validMethod(d), "invalid descriptor accepted: " + d);
        check(!MixinDescriptors.validField("V") && !MixinDescriptors.validField("II"), "invalid field descriptor");
        check(MixinDescriptors.rewriteTypeDescriptor("(Lgame/Old;)V", n -> null) == null, "partial descriptor rewrite");
        check("(Lgame/New;)V".equals(MixinDescriptors.rewriteTypeDescriptor("(Lgame/Old;)V", n -> "game/New")), "descriptor rename");
        check(MixinDescriptors.fqcnRoundTripOk("game/Old$Inner"), "nested class roundtrip");

        String imports = "import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.Inject; import org.spongepowered.asm.mixin.injection.At; ";
        String code = imports + "import game.Old; @Mixin(Old.class) class M { @Inject(method=\"before()V\", at=@At(value=\"INVOKE\",target=\"Lgame/Old;before()V\")) void hook() {} }";
        PortContext ctx = context(false);
        String out = mixin(ctx, code);
        check(out.contains("after()V") && out.contains("Lgame/New;after()V"), "mixin known owner mapping");
        out = mixin(context(false), imports + "@Mixin(targets=\"game.Old\") class M { @Inject(method=\"before()V\",at=@At(\"HEAD\")) void hook() {} }");
        check(out.contains("game.New") && out.contains("after()V"), "source owner snapshot lost");
        out = mixin(context(true), imports + "@Mixin(targets=\"game.New\") class M { @Inject(method=\"after()V\",at=@At(\"HEAD\")) void hook() {} }");
        check(out.contains("game.Old") && out.contains("before()V"), "reverse mixin");
        String user = "@interface Mixin { String targets(); } @Mixin(targets=\"game.Old\") class M {}";
        check(mixin(context(false), user).contains("game.Old"), "custom Mixin annotation changed");
        out = mixin(context(false), imports + "@Mixin(targets=\"game.Old\",remap=false) class M { @Inject(method=\"before()V\",at=@At(value=\"INVOKE\",target=\"Lgame/Old;before()V\")) void hook() {} }");
        check(out.contains("game.Old") && out.contains("before()V") && !out.contains("after()V"), "remap=false ignored");
        ctx = context(false);
        out = mixin(ctx, imports + "@Mixin(targets=\"game.Old\") class M { @Inject(method=\"before(Lunknown/X;)V\",at=@At(\"HEAD\")) void hook() {} }");
        check(out.contains("before(Lunknown/X;)V") && ctx.report.count(Report.Severity.TODO) > 0, "unknown descriptor changed partially");

        String aw = "# header\r\naccessWidener v2 named # note\r\n\ttransitive-accessible method game/Old before ()V # keep\r\n";
        ctx = context(false);
        OutputFile migrated = new AccessWidenerPass(ctx).transform("x.accesswidener", aw);
        check(migrated != null, "AW not migrated");
        String text = new String(migrated.content, StandardCharsets.UTF_8);
        check(text.equals(aw.replace("game/Old before", "game/New after")), "AW whitespace/comment/newline loss");
        String bad = "accessWidener v2 named\naccessible method game/Old before (Lunknown/X;)V\n";
        ctx = context(false);
        check(new AccessWidenerPass(ctx).transform("x.accesswidener", bad) == null, "unknown AW descriptor changed");
        check(ctx.report.count(Report.Severity.TODO) > 0, "AW missing TODO");
        check(new AccessWidenerPass(context(false)).transform("x.accesswidener", aw.replace("named", "intermediary")) == null, "runtime namespace guessed");
        String official = aw.replace("named", "official");
        ctx = contextWithChannel(false, "official");
        check(new AccessWidenerPass(ctx).transform("x.accesswidener", official) != null, "official same-namespace AW rejected");
        check(new AccessWidenerPass(context(false)).transform("x.accesswidener", official) == null, "official header accepted for Yarn data");
        check(new AccessWidenerPass(context(false)).transform("x.accesswidener", aw.replace("v2", "v1")) == null, "v1 transitive accepted");
        System.out.println("ReferenceMigrationTest: ALL PASSED");
    }
    private static String mixin(PortContext ctx, String code) {
        var parsed = new JavaParser().parse(code);
        check(parsed.isSuccessful(), "test fixture parse failed");
        var cu = parsed.getResult().orElseThrow();
        new MixinReferencePass(ctx).transform("M.java", cu);
        return cu.toString();
    }
    private static PortContext context(boolean reverse) {
        return new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "fabric", "a", "b", true),
                new MappingResolver(mapping(reverse), mapping(!reverse)), new Report());
    }
    private static PortContext contextWithChannel(boolean reverse, String channel) {
        return new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "fabric", "a", "b", true),
                new MappingResolver(mapping(reverse, channel), mapping(!reverse, channel)), new Report());
    }
    private static VersionMappings mapping(boolean target) { return mapping(target, "yarn"); }
    private static VersionMappings mapping(boolean target, String channel) {
        var info = new VersionMappings.VersionInfo(); info.javaVersion = 17; info.loader = "fabric"; info.mappingsChannel = channel;
        return new VersionMappings(Path.of("."), List.of(), info,
                Map.of("owner", new VersionMappings.ClassEntry(target ? "game.New" : "game.Old", null)),
                Map.of("owner", Map.of("operation", new VersionMappings.MemberEntry(target ? "after" : "before", "method", null))),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
