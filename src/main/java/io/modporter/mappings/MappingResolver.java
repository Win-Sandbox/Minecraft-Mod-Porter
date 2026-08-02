package io.modporter.mappings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 双向解析：源版本符号 → IR → 目标版本符号，一步完成，任意版本对通用。
 */
public final class MappingResolver {

    private final VersionMappings source;
    private final VersionMappings target;

    /** 类解析结果。 */
    public static final class ClassResolution {
        public enum Kind { MAPPED, REMOVED, UNKNOWN }

        public final Kind kind;
        public final String targetFqcn;   // MAPPED 时有效
        public final String note;         // 目标侧附带的迁移提示，可为 null
        public final String guidance;     // REMOVED 时的指导文字，可为 null

        private ClassResolution(Kind kind, String targetFqcn, String note, String guidance) {
            this.kind = kind;
            this.targetFqcn = targetFqcn;
            this.note = note;
            this.guidance = guidance;
        }

        static ClassResolution mapped(String fqcn, String note) {
            return new ClassResolution(Kind.MAPPED, fqcn, note, null);
        }
        static ClassResolution removed(String guidance) {
            return new ClassResolution(Kind.REMOVED, null, null, guidance);
        }
        static final ClassResolution UNKNOWN = new ClassResolution(Kind.UNKNOWN, null, null, null);
    }

    /** 成员解析候选：源版本某个名字对应到目标版本的一种改写。 */
    public static final class MemberCandidate {
        public final String classIr;
        public final String memberIr;
        public final String sourceName;
        public final String sourceKind;
        public final String targetName;
        public final String targetKind;
        public final String note;

        MemberCandidate(String classIr, String memberIr, String sourceName, String sourceKind,
                        String targetName, String targetKind, String note) {
            this.classIr = classIr;
            this.memberIr = memberIr;
            this.sourceName = sourceName;
            this.sourceKind = sourceKind;
            this.targetName = targetName;
            this.targetKind = targetKind;
            this.note = note;
        }

        public boolean isNoop() {
            return sourceName.equals(targetName) && sourceKind.equals(targetKind) && note == null;
        }
    }

    /** 源版本成员名 -> 所有候选改写 */
    private final Map<String, List<MemberCandidate>> membersBySourceName = new HashMap<>();

    public MappingResolver(VersionMappings source, VersionMappings target) {
        this.source = source;
        this.target = target;
        buildMemberIndex();
    }

    public VersionMappings source() { return source; }
    public VersionMappings target() { return target; }

    // ---- classes ----

    /**
     * 解析一个源版本 FQCN。支持内部类：若整体无映射，会尝试逐级去掉尾部段
     * （如 a.b.Mod.EventBusSubscriber 先试整体，再试 a.b.Mod 并保留 .EventBusSubscriber 后缀）。
     */
    public ClassResolution resolveClass(String sourceFqcn) {
        String suffix = "";
        String candidate = sourceFqcn;
        while (true) {
            String ir = source.irByFqcn.get(candidate);
            if (ir != null) {
                VersionMappings.ClassEntry targetEntry = target.classesByIr.get(ir);
                if (targetEntry != null) {
                    return ClassResolution.mapped(targetEntry.fqcn + suffix, targetEntry.note);
                }
                // 源版本认识这个类，但目标版本没有对应映射 → 该概念在目标版本缺失；
                // 目标版本可在 idioms.json guidance 中直接以 IR id 为键提供迁移指导
                return ClassResolution.removed(target.guidanceFor(ir));
            }
            VersionMappings.RemovedEntry removed = source.removedClasses.get(candidate);
            if (removed != null) {
                // 概念在目标版本仍原样存在（如 setRegistryName 之于 1.16/1.18）：类保持不变，无需迁移
                if (target.supports(removed.concept)) {
                    return ClassResolution.mapped(candidate + suffix, null);
                }
                String guidance = removed.concept != null ? target.guidanceFor(removed.concept) : null;
                if (guidance == null) guidance = removed.message;
                return ClassResolution.removed(guidance);
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) return ClassResolution.UNKNOWN;
            // 只有当被截掉的尾段首字母大写时才可能是内部类，否则直接放弃
            String tail = candidate.substring(dot + 1);
            if (tail.isEmpty() || !Character.isUpperCase(tail.charAt(0))) return ClassResolution.UNKNOWN;
            suffix = "." + tail + suffix;
            candidate = candidate.substring(0, dot);
        }
    }

    /** 用 IR id 直接取目标版本 FQCN（引擎内部生成代码时用，如 SubscribeEvent）。 */
    public String targetClass(String irId) {
        VersionMappings.ClassEntry e = target.classesByIr.get(irId);
        return e != null ? e.fqcn : null;
    }

    /** 用 IR id 取源版本 FQCN。 */
    public String sourceClass(String irId) {
        VersionMappings.ClassEntry e = source.classesByIr.get(irId);
        return e != null ? e.fqcn : null;
    }

    // ---- members ----

    private void buildMemberIndex() {
        // 遍历源版本 members.json：源名 -> (IR -> 目标名)
        for (Map.Entry<String, Map<String, VersionMappings.MemberEntry>> cls : source.members.entrySet()) {
            String classIr = cls.getKey();
            Map<String, VersionMappings.MemberEntry> targetMembers = target.members.get(classIr);
            for (Map.Entry<String, VersionMappings.MemberEntry> m : cls.getValue().entrySet()) {
                String memberIr = m.getKey();
                VersionMappings.MemberEntry src = m.getValue();
                VersionMappings.MemberEntry tgt = targetMembers != null ? targetMembers.get(memberIr) : null;
                // 目标版本未显式给出时，约定目标名 = IR 规范名、形态与源相同
                String targetName = tgt != null ? tgt.name : memberIr;
                String targetKind = tgt != null ? tgt.kind : src.kind;
                String note = tgt != null ? tgt.note : null;
                MemberCandidate c = new MemberCandidate(classIr, memberIr, src.name, src.kind,
                        targetName, targetKind, note);
                if (c.isNoop()) continue;
                membersBySourceName.computeIfAbsent(src.name, k -> new ArrayList<>()).add(c);
            }
        }
        // 目标版本存在、源版本用 IR 规范名的成员（源 members.json 未列出 = 源名即 IR 名）
        for (Map.Entry<String, Map<String, VersionMappings.MemberEntry>> cls : target.members.entrySet()) {
            String classIr = cls.getKey();
            Map<String, VersionMappings.MemberEntry> sourceMembers = source.members.get(classIr);
            for (Map.Entry<String, VersionMappings.MemberEntry> m : cls.getValue().entrySet()) {
                String memberIr = m.getKey();
                if (sourceMembers != null && sourceMembers.containsKey(memberIr)) continue; // 已在上面处理
                VersionMappings.MemberEntry tgt = m.getValue();
                MemberCandidate c = new MemberCandidate(classIr, memberIr, memberIr, tgt.kind,
                        tgt.name, tgt.kind, tgt.note);
                if (c.isNoop()) continue;
                membersBySourceName.computeIfAbsent(memberIr, k -> new ArrayList<>()).add(c);
            }
        }
    }

    /**
     * 按源版本成员名查找改写候选。返回空列表 = 无需改写；
     * 返回多个且目标不一致 = 歧义，调用方应打 TODO 而不是猜。
     */
    public List<MemberCandidate> resolveMember(String sourceName) {
        List<MemberCandidate> list = membersBySourceName.get(sourceName);
        return list != null ? list : List.of();
    }

    /** 多个候选是否指向同一种改写（目标名与形态一致），是则可安全应用。 */
    public static boolean unambiguous(List<MemberCandidate> candidates) {
        if (candidates.size() <= 1) return true;
        MemberCandidate first = candidates.get(0);
        for (MemberCandidate c : candidates) {
            if (!c.targetName.equals(first.targetName) || !c.targetKind.equals(first.targetKind)) {
                return false;
            }
        }
        return true;
    }

    /** 按裸名查询源版本「已移除概念」成员（如 setRegistryName）。概念在目标版本仍可用时返回 null。 */
    public String removedMemberGuidance(String memberName) {
        VersionMappings.RemovedEntry e = source.removedMembers.get(memberName);
        if (e == null) return null;
        if (target.supports(e.concept)) return null;
        String guidance = e.concept != null ? target.guidanceFor(e.concept) : null;
        return guidance != null ? guidance : e.message;
    }

    // ---- idioms ----

    /** 惯用法 id -> 源版本形态（用于识别）。 */
    public Map<String, VersionMappings.IdiomForm> sourceIdioms() {
        return source.idioms;
    }

    /** 目标版本某惯用法的形态（用于生成），可为 null。 */
    public VersionMappings.IdiomForm targetIdiom(String idiomId) {
        return target.idioms.get(idiomId);
    }
}
