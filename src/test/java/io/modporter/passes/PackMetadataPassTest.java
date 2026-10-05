package io.modporter.passes;

import com.google.gson.*;
import io.modporter.core.*;
import io.modporter.engine.*;
import io.modporter.mappings.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Opt-in main regression. Added but NOT compiled/executed during source-only update. */
public final class PackMetadataPassTest {
    public static void main(String[] args) {
        String legacy = "{\"pack\":{\"pack_format\":34,\"description\":{\"text\":\"hello\"},\"supported_formats\":[32,34]},\"filter\":{\"block\":[]}}";
        PortContext ctx = context(false);
        PackMetadataPass pass = new PackMetadataPass(ctx);
        pass.setProjectPaths(List.of("src/main/resources/assets/test/models/a.json"));
        OutputFile out = pass.transform("src/main/resources/pack.mcmeta", legacy);
        JsonObject root = json(out), pack = root.getAsJsonObject("pack");
        check(pack.getAsJsonArray("min_format").toString().equals("[69,0]"), "resource format choice");
        check(pack.getAsJsonArray("max_format").toString().equals("[69,0]"), "resource max format");
        check(!pack.has("pack_format") && !pack.has("supported_formats"), "legacy fields left in new-only pack");
        check(root.has("filter") && pack.get("description").isJsonObject(), "unrelated fields lost");
        check(pass.transform("src/main/resources/pack.mcmeta", text(out)) == null, "not idempotent");
        pass.setProjectPaths(List.of("data/test/functions/a.mcfunction"));
        pack = json(pass.transform("pack.mcmeta", legacy)).getAsJsonObject("pack");
        check(pack.getAsJsonArray("min_format").toString().equals("[88,1]"), "data minor was lost");
        pass.setProjectPaths(List.of("assets/test/a.json", "data/test/a.json"));
        check(pass.transform("pack.mcmeta", legacy) == null, "mixed formats guessed");
        pass.setProjectPaths(List.of("elsewhere/assets/test/a.json"));
        check(pass.transform("pack.mcmeta", legacy) == null, "directory boundary ignored");
        pass.setProjectPaths(List.of("assets/test/a.json"));
        for (String bad : List.of("[]", "{\"pack\":[]}", "{\"pack\":{\"pack_format\":34.5}}", "{\"pack\":{\"min_format\":[69,-1],\"max_format\":70}}"))
            check(pass.transform("pack.mcmeta", bad) == null, "bad schema changed");

        String overlays = "{\"pack\":{\"pack_format\":34},\"overlays\":{\"entries\":[{\"directory\":\"current\",\"formats\":[32,34]},{\"directory\":\"future\",\"formats\":40}]}}";
        root = json(pass.transform("pack.mcmeta", overlays));
        JsonArray entries = root.getAsJsonObject("overlays").getAsJsonArray("entries");
        check(entries.size() == 1, "inactive overlay not filtered");
        JsonObject entry = entries.get(0).getAsJsonObject();
        check(!entry.has("formats") && entry.getAsJsonArray("max_format").toString().equals("[69,0]"), "overlay range not migrated");
        check(pass.transform("pack.mcmeta", root.toString()) == null, "overlay rerun not idempotent");
        check(pass.transform("pack.mcmeta", overlays.replace("current", "../escape")) == null, "unsafe overlay directory accepted");

        PackMetadataPass down = new PackMetadataPass(context(true)); down.setProjectPaths(List.of("assets/test/a.json"));
        out = down.transform("pack.mcmeta", "{\"pack\":{\"min_format\":[69,0],\"max_format\":69,\"description\":\"x\"}}");
        pack = json(out).getAsJsonObject("pack");
        check(pack.get("pack_format").getAsInt() == 34 && !pack.has("min_format") && !pack.has("max_format"), "downgrade schema");
        check(down.transform("pack.mcmeta", root.toString()) == null, "overlay downgrade guessed without flattening");
        check(ctx.report.count(Report.Severity.TODO) > 0, "conservative failures unreported");
        System.out.println("PackMetadataPassTest: ALL PASSED");
    }
    private static PortContext context(boolean reverse) {
        return new PortContext(new PortRequest(Path.of("in"), Path.of("out"), "fabric", "a", "b", true),
                new MappingResolver(mapping(reverse), mapping(!reverse)), new Report());
    }
    private static VersionMappings mapping(boolean modern) {
        var info = new VersionMappings.VersionInfo(); info.javaVersion = 17;
        info.resourcePackFormat = new PackFormat(modern ? 69 : 34, 0);
        info.dataPackFormat = new PackFormat(modern ? 88 : 48, modern ? 1 : 0);
        info.packMetadataStyle = modern ? "range" : "legacy";
        return new VersionMappings(Path.of("."), List.of(), info, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }
    private static String text(OutputFile out) { check(out != null, "expected changed output"); return new String(out.content, StandardCharsets.UTF_8); }
    private static JsonObject json(OutputFile out) { return JsonParser.parseString(text(out)).getAsJsonObject(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
