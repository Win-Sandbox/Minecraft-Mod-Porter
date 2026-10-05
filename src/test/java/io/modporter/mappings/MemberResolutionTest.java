package io.modporter.mappings;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** No-op candidates must participate in ambiguity decisions, including reverse conversion. */
public final class MemberResolutionTest {
    public static void main(String[] args) {
        MappingResolver r = new MappingResolver(mapping(false), mapping(true));
        if (r.resolveMember("getWorld").size() != 2) throw new AssertionError("no-op candidate was lost");
        if (MappingResolver.unambiguous(r.resolveMember("getWorld"))) throw new AssertionError("rename/no-op ambiguity lost");
        var own = r.resolveMemberForOwner("game.A", "getWorld");
        if (own.size() != 1 || !own.get(0).targetName.equals("level")) throw new AssertionError("owner lookup failed");
        if (!r.resolveMemberForOwner("third.A", "getWorld").isEmpty()) throw new AssertionError("simple-name owner fallback");
        var unchanged = r.resolveMemberForOwner("game.B", "getWorld");
        if (unchanged.size() != 1 || !unchanged.get(0).isNoop()) throw new AssertionError("no-op not retained");
        MappingResolver reverse = new MappingResolver(mapping(true), mapping(false));
        if (!reverse.resolveMemberForOwner("game.A", "level").get(0).targetName.equals("getWorld")) throw new AssertionError("reverse failed");
        System.out.println("MemberResolutionTest: ALL PASSED");
    }
    private static VersionMappings mapping(boolean target) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo(); info.javaVersion = 17;
        return new VersionMappings(Path.of("."), List.of(), info,
                Map.of("a", new VersionMappings.ClassEntry("game.A", null), "b", new VersionMappings.ClassEntry("game.B", null)),
                Map.of("a", Map.of("world", new VersionMappings.MemberEntry(target ? "level" : "getWorld", "method", null)),
                       "b", Map.of("getWorld", new VersionMappings.MemberEntry("getWorld", "method", null))),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
    }
}
