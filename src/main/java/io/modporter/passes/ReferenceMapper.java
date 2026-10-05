package io.modporter.passes;

import io.modporter.mappings.MappingResolver;
import java.util.*;

/** Shared atomic owner/name/descriptor mapping; intentionally rejects incomplete mapping evidence. */
final class ReferenceMapper {
    record Mapped(String owner, String name, String descriptor) {}
    private final MappingResolver resolver;
    ReferenceMapper(MappingResolver resolver) { this.resolver = resolver; }
    String mapClass(String internal) {
        if (!MixinDescriptors.fqcnRoundTripOk(internal)) return null;
        String dotted = MixinDescriptors.internalToFqcnLookup(internal);
        // Stable JDK types need no Minecraft mapping; all other unknowns block the reference.
        if (internal.startsWith("java/") || internal.startsWith("javax/")) return internal;
        String ir = resolver.source().irByFqcn.get(dotted);
        if (ir == null) return null; // do not treat an outer class as a nested class's owner
        if (resolver.source().classesByIr.get(ir).note != null) return null;
        var target = resolver.target().classesByIr.get(ir);
        if (target == null || target.note != null) return null;
        if (target.fqcn.equals(dotted)) return internal;
        return MixinDescriptors.fqcnToInternal(target.fqcn);
    }
    Mapped member(String owner, String name, String desc, boolean method) {
        String mappedOwner = mapClass(owner);
        if (mappedOwner == null) return null;
        String kind = method ? "method" : "field";
        if (!desc.isEmpty() && !(method ? MixinDescriptors.validMethod(desc) : MixinDescriptors.validField(desc))) return null;
        List<MappingResolver.MemberCandidate> all = resolver.resolveMemberForOwner(
                MixinDescriptors.internalToFqcnLookup(owner), name).stream().filter(c -> c.sourceKind.equals(kind)).toList();
        // No member entry is not proof of unchanged descriptor/overload. Keep the entire reference.
        if (all.isEmpty() || !MappingResolver.unambiguous(all)) return null;
        var c = all.get(0);
        for (var candidate : all) {
            if (!kind.equals(candidate.targetKind) || candidate.note != null || candidate.receiverChanged()) return null;
            var srcMembers = resolver.source().members.get(candidate.classIr);
            var src = srcMembers != null ? srcMembers.get(candidate.memberIr) : null;
            if (src != null && src.note != null) return null;
        }
        String mappedDesc = desc.isEmpty() ? "" : MixinDescriptors.rewriteTypeDescriptor(desc, this::mapClass);
        return mappedDesc == null ? null : new Mapped(mappedOwner, c.targetName, mappedDesc);
    }
}
