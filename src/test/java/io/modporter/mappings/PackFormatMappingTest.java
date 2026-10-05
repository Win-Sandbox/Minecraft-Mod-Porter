package io.modporter.mappings;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Schema/copy/overlay/alias regression. Not compiled or executed in this update. */
public final class PackFormatMappingTest {
    public static void main(String[] args) throws Exception {
        check(new PackFormat(101, 1).toJson().toString().equals("[101,1]"), "minor lost");
        for (String bad : List.of("1.2", "[1]", "[-1,0]", "[1,0.5]", "[true,0]", "[\"1\",0]")) {
            try { PackFormat.fromMapping(JsonParser.parseString(bad)); throw new AssertionError("accepted " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        Path temp = Files.createTempDirectory("modporter-pack-mapping-");
        try {
            write(temp, "base", "{\"mcVersion\":\"base\",\"packFormat\":69,\"resourcePackFormat\":[69,0],\"dataPackFormat\":[88,1],\"packMetadataStyle\":\"range\",\"itemModelDefinitions\":true,\"aliases\":{\"hotfix\":{\"resourcePackFormat\":[70,0],\"dataPackFormat\":[89,2]}}}");
            write(temp, "old", "{\"mcVersion\":\"old\",\"basedOn\":\"base\",\"packFormat\":34,\"resourcePackFormat\":[34,0],\"dataPackFormat\":[48,0],\"packMetadataStyle\":\"legacy\",\"itemModelDefinitions\":false}");
            write(temp, "oldExternal", "{\"mcVersion\":\"oldExternal\",\"basedOn\":\"base\",\"packFormat\":32}");
            MappingRepository repository = new MappingRepository(temp);
            var old = repository.load("fabric", "old").info;
            check(!old.itemModelDefinitions && old.dataPackFormat.equals(new PackFormat(48, 0)), "reverse overlay leaked capability");
            var alias = repository.load("fabric", "hotfix").info;
            check(alias.itemModelDefinitions && alias.dataPackFormat.equals(new PackFormat(89, 2)), "alias lost minor");
            var base = repository.load("fabric", "base").info;
            check(base.resourcePackFormat.equals(new PackFormat(69, 0)), "alias mutated base");
            check(base.copy().dataPackFormat.equals(base.dataPackFormat), "copy lost format");
            var external = repository.load("fabric", "oldExternal").info;
            check(external.packMetadataStyle == null && external.resourcePackFormat == null && external.dataPackFormat == null, "legacy-only override retained stale split versions");
            write(temp, "bad", "{\"mcVersion\":\"bad\",\"resourcePackFormat\":[1,-1]}");
            try { repository.load("fabric", "bad"); throw new AssertionError("invalid mapping accepted"); }
            catch (IOException expected) { }
        } finally {
            try (var files = Files.walk(temp)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("PackFormatMappingTest: ALL PASSED");
    }
    private static void write(Path root, String version, String content) throws IOException {
        Path dir = root.resolve("versions/fabric/" + version); Files.createDirectories(dir);
        Files.writeString(dir.resolve("version.json"), content);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
