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
        static ClassResolution unknown(String guidance) {
            return new ClassResolution(Kind.UNKNOWN, null, guidance, null);
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
        public final VersionMappings.Receiver sourceReceiver;
        public final VersionMappings.Receiver targetReceiver;

        MemberCandidate(String classIr, String memberIr, String sourceName, String sourceKind,
                        String targetName, String targetKind, String note,
                        VersionMappings.Receiver sourceReceiver, VersionMappings.Receiver targetReceiver) {
            this.classIr = classIr;
            this.memberIr = memberIr;
            this.sourceName = sourceName;
            this.sourceKind = sourceKind;
            this.targetName = targetName;
            this.targetKind = targetKind;
            this.note = note;
            this.sourceReceiver = sourceReceiver;
            this.targetReceiver = targetReceiver;
        }

        public boolean isNoop() {
            return sourceName.equals(targetName) && sourceKind.equals(targetKind)
                    && note == null && java.util.Objects.equals(sourceReceiver, targetReceiver);
        }

        /** 是否描述了接收者形态变化（static/chain 位置改变）。 */
        public boolean receiverChanged() {
            return !java.util.Objects.equals(sourceReceiver, targetReceiver);
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
            if (source.ambiguousClassNames.contains(candidate)) {
                return new ClassResolution(ClassResolution.Kind.UNKNOWN, null,
                        "源类对应多个 IR 且未指定唯一 primary，保留并人工确认：" + candidate, null);
            }
            String ir = source.irByFqcn.get(candidate);
            if (ir != null) {
                VersionMappings.ClassEntry targetEntry = target.classesByIr.get(ir);
                if (targetEntry != null) {
                    return ClassResolution.mapped(targetEntry.fqcn + suffix, targetEntry.note);
                }
                // 源版本认识这个类，但目标版本没有对应映射。缺少目标类条目不是移除证明：
                // 目标可能只是尚未收录（典型是平台/第三方 API），必须保守 UNKNOWN 并把指导交给调用方。
                return ClassResolution.unknown(target.guidanceFor(ir));
            }
            VersionMappings.RemovedEntry removed = source.removedClasses.get(candidate);
            if (removed != null) {
                // removed.json is an inventory of unmapped concepts, not proof of removal.
                // Preserve only if the exact target FQCN is explicitly present; never equate concept support with class identity.
                // 目标已有同 FQCN 的正式类记录，说明该类仍可用；removed 库存不应遮蔽它。
                if (target.irByFqcn.containsKey(candidate)) return ClassResolution.mapped(candidate + suffix, null);
                VersionMappings.RemovedEntry targetRemoved = target.removedClasses.get(candidate);
                if (targetRemoved != null && java.util.Objects.equals(targetRemoved.concept, removed.concept)) {
                    // 同一精确 FQCN 且 same concept：两侧都把它列为库存，保留原样可用。
                    return ClassResolution.mapped(candidate + suffix, null);
                }
                // 不同 concept 或目标无记录都只是证据不足，不能把库存当删除证明。
                String guidance = targetRemoved != null && targetRemoved.concept != null
                        ? target.guidanceFor(targetRemoved.concept) : null;
                if (guidance == null && removed.concept != null) guidance = target.guidanceFor(removed.concept);
                return ClassResolution.unknown(guidance);
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
                // source-only note 也必须参与 no-op/TODO 判定，不能被 target 视角吞掉
                String note = tgt != null && tgt.note != null ? tgt.note : src.note;
                VersionMappings.Receiver tgtReceiver = tgt != null ? tgt.receiver : src.receiver;
                MemberCandidate c = new MemberCandidate(classIr, memberIr, src.name, src.kind,
                        targetName, targetKind, note, src.receiver, tgtReceiver);
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
                        tgt.name, tgt.kind, tgt.note, null, tgt.receiver);
                membersBySourceName.computeIfAbsent(memberIr, k -> new ArrayList<>()).add(c);
            }
        }
    }

    /**
     * 按源版本成员名查找改写候选。返回空列表 = 无需改写；
     * 返回多个且目标不一致 = 歧义，调用方应打 TODO 而不是猜。
     *
     * 注意：本方法返回全部候选（含 no-op）。no-op 候选（源名 = 目标名）表示
     * 「该名字在已知 owner 上不改名」——调用方应保留它参与消歧，不能过滤，
     * 否则会把本应报告的歧义漏掉（如 getWorld）。
     */
    public List<MemberCandidate> resolveMember(String sourceName) {
        List<MemberCandidate> list = membersBySourceName.get(sourceName);
        return list != null ? list : List.of();
    }

    /**
     * 按源 owner FQCN + 成员名精确解析候选。owner 匹配规则（保守）：
     * - owner FQCN 完全等于候选的源版本类 FQCN，返回该类自己的候选；
     * - 否则返回空列表（不做继承猜测）。
     * 用于 JavaSourcePass 在能确定接收者类型时过滤候选。
     * 返回值同样包含 no-op 候选，语义与 {@link #resolveMember(String)} 一致。
     */
    public List<MemberCandidate> resolveMemberForOwner(String sourceFqcn, String sourceName) {
        if (sourceFqcn == null || sourceFqcn.isEmpty()) return List.of();
        List<MemberCandidate> all = resolveMember(sourceName);
        if (all.isEmpty()) return List.of();
        List<MemberCandidate> out = new ArrayList<>();
        for (MemberCandidate c : all) {
            String src = sourceClass(c.classIr);
            if (src != null && src.equals(sourceFqcn)) out.add(c);
        }
        return out;
    }

    /** 判断源 owner FQCN 是否等于某个 IR 类对应的源版本类（用于 @Override 父类校验）。 */
    public boolean ownerMatchesIrSource(String sourceFqcn, String classIr) {
        if (sourceFqcn == null || classIr == null) return false;
        String src = sourceClass(classIr);
        return src != null && src.equals(sourceFqcn);
    }

    /** 多个候选是否指向同一种改写（目标类 IR、名称、形态与接收者一致），是则可安全应用。 */
    public static boolean unambiguous(List<MemberCandidate> candidates) {
        if (candidates.size() <= 1) return true;
        MemberCandidate first = candidates.get(0);
        for (MemberCandidate c : candidates) {
            if (!c.classIr.equals(first.classIr)
                    || !c.targetName.equals(first.targetName)
                    || !c.targetKind.equals(first.targetKind)
                    || !java.util.Objects.equals(c.targetReceiver, first.targetReceiver)) {
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
