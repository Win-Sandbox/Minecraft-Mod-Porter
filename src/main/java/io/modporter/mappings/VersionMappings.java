package io.modporter.mappings;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 单个 MC 版本的全部映射数据：该版本 ↔ 规范 IR。
 * 所有数据从 mappings/versions/&lt;loader&gt;/&lt;version&gt;/ 下的 JSON 文件加载，代码中不含任何对照表。
 */
public final class VersionMappings {

    /** version.json 的内容。 */
    public static final class VersionInfo {
        public VersionInfo copy() {
            VersionInfo c = new VersionInfo();
            c.mcVersion = mcVersion;
            c.loader = loader;
            c.javaVersion = javaVersion;
            c.metadataFormat = metadataFormat;
            c.metadataPath = metadataPath;
            c.langFormat = langFormat;
            c.langKeys = new HashMap<>(langKeys);
            c.texturePrefixes = new HashMap<>(texturePrefixes);
            c.modAnnotationStyle = modAnnotationStyle;
            c.lifecycleStyle = lifecycleStyle;
            c.packFormat = packFormat;
            c.resourcePackFormat = resourcePackFormat;
            c.dataPackFormat = dataPackFormat;
            c.packMetadataStyle = packMetadataStyle;
            c.itemModelDefinitions = itemModelDefinitions;
            c.forgeVersion = forgeVersion;
            c.loaderVersionRange = loaderVersionRange;
            c.mappingsChannel = mappingsChannel;
            c.gradleVersion = gradleVersion;
            c.extras = new HashMap<>(extras);
            return c;
        }

        public String mcVersion;
        public String loader;
        public int javaVersion;
        public String metadataFormat;          // "mcmod.info" | "mods.toml"
        public String metadataPath;            // 元数据文件在工程内的相对路径
        public String langFormat;              // "lang" | "json"
        public Map<String, String> langKeys = new HashMap<>();       // kind -> 键模式，如 "tile.{modid}.{name}.name"
        public Map<String, String> texturePrefixes = new HashMap<>();// "block" -> "blocks/" 等
        public String modAnnotationStyle;      // "attributes" | "value"
        public String lifecycleStyle;          // "eventHandler" | "modBus"
        public int packFormat; // Legacy resource-format major, retained for old data packages.
        public PackFormat resourcePackFormat;
        public PackFormat dataPackFormat;
        /** null = old/incomplete dataset; otherwise legacy or range. */
        public String packMetadataStyle;
        public boolean itemModelDefinitions;
        public String forgeVersion;
        public String loaderVersionRange;
        public String mappingsChannel;         // 仅供 build.gradle 模板参考
        public String gradleVersion;           // 本版本构建插件对应的 Gradle wrapper 版本
        /** 版本专有的模板变量（如 Fabric 的 yarnVersion / loomVersion），以 ${键名} 形式在模板中替换。 */
        public Map<String, String> extras = new HashMap<>();
    }

    /** classes.json 中一个条目：IR id -> 该版本的 FQCN（可附迁移提示）。 */
    public static final class ClassEntry {
        public final String fqcn;
        public final String note; // 迁移到本版本时需要人工注意的事项，可为 null
        public final boolean primary;

        public ClassEntry(String fqcn, String note) { this(fqcn, note, false); }
        public ClassEntry(String fqcn, String note, boolean primary) {
            this.fqcn = fqcn;
            this.note = note;
            this.primary = primary;
        }
    }

    /** members.json 中一个条目：某 IR 类下，IR 成员名 -> 该版本的成员名与形态。 */
    public static final class MemberEntry {
        public final String name;
        public final String kind; // "method" | "field"
        public final String note;
        public final Receiver receiver;

        public MemberEntry(String name, String kind, String note) { this(name, kind, note, null); }
        public MemberEntry(String name, String kind, String note, Receiver receiver) {
            this.name = name;
            this.kind = kind;
            this.note = note;
            this.receiver = receiver;
        }
    }

    /** Receiver paths describe WHERE the IR operation lives, not arbitrary Java snippets. */
    public record Receiver(String kind, String owner, java.util.List<String> path) {
        public Receiver {
            path = java.util.List.copyOf(path);
            if (!java.util.Set.of("static", "chain").contains(kind)) throw new IllegalArgumentException("receiver kind");
            if ("static".equals(kind) && (owner == null || !owner.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+") || !path.isEmpty()))
                throw new IllegalArgumentException("static receiver needs owner FQCN only");
            if ("chain".equals(kind) && (owner != null || path.isEmpty())) throw new IllegalArgumentException("chain receiver needs path only");
            for (String step : path) if (!step.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(\\(\\))?")) throw new IllegalArgumentException("receiver path step");
        }
    }

    /** removed.json 中一个条目：本版本存在、但没有 IR 类/成员映射的符号，指向一个「概念 id」。 */
    public static final class RemovedEntry {
        public final String concept;
        public final String message; // 通用说明，目标版本没有 guidance 时兜底

        public RemovedEntry(String concept, String message) {
            this.concept = concept;
            this.message = message;
        }
    }

    /** annotations.json 中一个注解在本版本的具体形态。 */
    public static final class AnnotationForm {
        public final String className;
        /** 属性 IR id -> 本版本属性形态。 */
        public final Map<String, AnnotationAttributeForm> attributes;

        public AnnotationForm(String className, Map<String, AnnotationAttributeForm> attributes) {
            this.className = className;
            this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
        }
    }

    /** annotations.json 中注解属性在本版本的具体形态。 */
    public static final class AnnotationAttributeForm {
        /** 稳定属性 id（跨版本比较用，如 bus）。 */
        public final String ir;
        /** 本版本 Java 源里的属性名。 */
        public final String name;
        /** 属性值为枚举常量时的宿主类 IR id，可为 null（普通字符串/数组值不迁移）。 */
        public final String valueClassIr;

        public AnnotationAttributeForm(String ir, String name, String valueClassIr) {
            this.ir = ir;
            this.name = name;
            this.valueClassIr = valueClassIr;
        }
    }

    /** idioms.json 中一个惯用法在本版本的具体形态。 */
    public static final class IdiomForm {
        public final String type;   // "constructor" | "staticCall"
        public final String className;
        public final String method; // staticCall 时的方法名
        /** 参数个数约束；null = 任意。用于区分同一构造器的不同参数形态（如 ResourceLocation 单参/双参）。 */
        public final Integer arity;

        public IdiomForm(String type, String className, String method, Integer arity) {
            this.type = type;
            this.className = className;
            this.method = method;
            this.arity = arity;
        }
    }

    public final Path dir;
    /** 模板查找目录链（覆盖层在前、基版本在后），MetadataPass 逐个回退。 */
    public final java.util.List<Path> templateDirs;
    public final VersionInfo info;
    /** IR id -> 本版本类 */
    public final Map<String, ClassEntry> classesByIr;
    /** 本版本 FQCN -> IR；重复组必须恰有一个 primary，否则保持为歧义。 */
    public final Map<String, String> irByFqcn;
    public final java.util.Set<String> ambiguousClassNames;
    /** IR 类 id -> (IR 成员名 -> 本版本成员) */
    public final Map<String, Map<String, MemberEntry>> members;
    /** 本版本已知的、无 IR 对应的类 FQCN -> 概念 */
    public final Map<String, RemovedEntry> removedClasses;
    /** 本版本已知的、无 IR 对应的成员名 -> 概念（按裸名匹配） */
    public final Map<String, RemovedEntry> removedMembers;
    /** 惯用法 id -> 本版本形态 */
    public final Map<String, IdiomForm> idioms;
    /** 注解类 IR id -> 本版本注解形态（annotations.json）。 */
    public final Map<String, AnnotationForm> annotationForms;
    /** 概念 id -> 迁移到本版本时的指导文字 */
    public final Map<String, String> guidance;
    /** 在本版本中仍然原样可用的概念（源版本标记为 removed 的符号若属于这些概念，则无需迁移） */
    public final java.util.Set<String> supportedConcepts;

    public VersionMappings(Path dir, java.util.List<Path> templateDirs, VersionInfo info,
                           Map<String, ClassEntry> classesByIr,
                           Map<String, Map<String, MemberEntry>> members,
                           Map<String, RemovedEntry> removedClasses,
                           Map<String, RemovedEntry> removedMembers,
                           Map<String, IdiomForm> idioms,
                           Map<String, String> guidance,
                           java.util.Set<String> supportedConcepts) {
        this(dir, templateDirs, info, classesByIr, members, removedClasses, removedMembers,
                idioms, guidance, supportedConcepts, Map.of());
    }

    public VersionMappings(Path dir, java.util.List<Path> templateDirs, VersionInfo info,
                           Map<String, ClassEntry> classesByIr,
                           Map<String, Map<String, MemberEntry>> members,
                           Map<String, RemovedEntry> removedClasses,
                           Map<String, RemovedEntry> removedMembers,
                           Map<String, IdiomForm> idioms,
                           Map<String, String> guidance,
                           java.util.Set<String> supportedConcepts,
                           Map<String, AnnotationForm> annotationForms) {
        this.dir = dir;
        this.templateDirs = java.util.List.copyOf(templateDirs);
        this.info = info;
        this.classesByIr = Collections.unmodifiableMap(classesByIr);
        this.members = Collections.unmodifiableMap(members);
        this.removedClasses = Collections.unmodifiableMap(removedClasses);
        this.removedMembers = Collections.unmodifiableMap(removedMembers);
        this.idioms = Collections.unmodifiableMap(idioms);
        this.annotationForms = Collections.unmodifiableMap(annotationForms);
        this.guidance = Collections.unmodifiableMap(guidance);
        this.supportedConcepts = Collections.unmodifiableSet(supportedConcepts);

        Map<String, java.util.List<String>> groups = new java.util.TreeMap<>();
        classesByIr.forEach((ir, entry) -> groups.computeIfAbsent(entry.fqcn, k -> new java.util.ArrayList<>()).add(ir));
        Map<String, String> reverse = new HashMap<>();
        java.util.Set<String> ambiguous = new java.util.HashSet<>();
        groups.forEach((fqcn, ids) -> {
            java.util.List<String> primary = ids.stream().filter(id -> classesByIr.get(id).primary).toList();
            if (ids.size() == 1) reverse.put(fqcn, ids.get(0));
            else if (primary.size() == 1) reverse.put(fqcn, primary.get(0));
            else ambiguous.add(fqcn);
        });
        this.irByFqcn = Collections.unmodifiableMap(reverse);
        this.ambiguousClassNames = Collections.unmodifiableSet(ambiguous);
    }

    public String guidanceFor(String concept) {
        return guidance.get(concept);
    }

    /** 该概念在本版本是否仍原样可用（无需迁移）。 */
    public boolean supports(String concept) {
        return concept != null && supportedConcepts.contains(concept);
    }

    /** 生成一个共享全部映射数据、但版本信息不同的视图（用于版本别名，如 1.19.1 复用 1.19.2）。 */
    public VersionMappings withInfo(VersionInfo newInfo) {
        return new VersionMappings(dir, templateDirs, newInfo,
                new HashMap<>(classesByIr),
                new HashMap<>(members),
                new HashMap<>(removedClasses),
                new HashMap<>(removedMembers),
                new HashMap<>(idioms),
                new HashMap<>(guidance),
                new java.util.HashSet<>(supportedConcepts),
                new HashMap<>(annotationForms));
    }
}
