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
            c.forgeVersion = forgeVersion;
            c.loaderVersionRange = loaderVersionRange;
            c.mappingsChannel = mappingsChannel;
            c.gradleVersion = gradleVersion;
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
        public int packFormat;
        public String forgeVersion;
        public String loaderVersionRange;
        public String mappingsChannel;         // 仅供 build.gradle 模板参考
        public String gradleVersion;           // 本版本 ForgeGradle 对应的 Gradle wrapper 版本
    }

    /** classes.json 中一个条目：IR id -> 该版本的 FQCN（可附迁移提示）。 */
    public static final class ClassEntry {
        public final String fqcn;
        public final String note; // 迁移到本版本时需要人工注意的事项，可为 null

        public ClassEntry(String fqcn, String note) {
            this.fqcn = fqcn;
            this.note = note;
        }
    }

    /** members.json 中一个条目：某 IR 类下，IR 成员名 -> 该版本的成员名与形态。 */
    public static final class MemberEntry {
        public final String name;
        public final String kind; // "method" | "field"
        public final String note;

        public MemberEntry(String name, String kind, String note) {
            this.name = name;
            this.kind = kind;
            this.note = note;
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
    /** 本版本 FQCN -> IR id（由 classesByIr 反向构建，重复值保留先出现者） */
    public final Map<String, String> irByFqcn;
    /** IR 类 id -> (IR 成员名 -> 本版本成员) */
    public final Map<String, Map<String, MemberEntry>> members;
    /** 本版本已知的、无 IR 对应的类 FQCN -> 概念 */
    public final Map<String, RemovedEntry> removedClasses;
    /** 本版本已知的、无 IR 对应的成员名 -> 概念（按裸名匹配） */
    public final Map<String, RemovedEntry> removedMembers;
    /** 惯用法 id -> 本版本形态 */
    public final Map<String, IdiomForm> idioms;
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
        this.dir = dir;
        this.templateDirs = java.util.List.copyOf(templateDirs);
        this.info = info;
        this.classesByIr = Collections.unmodifiableMap(classesByIr);
        this.members = Collections.unmodifiableMap(members);
        this.removedClasses = Collections.unmodifiableMap(removedClasses);
        this.removedMembers = Collections.unmodifiableMap(removedMembers);
        this.idioms = Collections.unmodifiableMap(idioms);
        this.guidance = Collections.unmodifiableMap(guidance);
        this.supportedConcepts = Collections.unmodifiableSet(supportedConcepts);

        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, ClassEntry> e : classesByIr.entrySet()) {
            reverse.putIfAbsent(e.getValue().fqcn, e.getKey());
        }
        this.irByFqcn = Collections.unmodifiableMap(reverse);
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
                new java.util.HashSet<>(supportedConcepts));
    }
}
