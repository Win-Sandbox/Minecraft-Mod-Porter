package io.modporter.mappings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 mappings 根目录加载各版本映射数据。
 * 目录结构：mappings/versions/&lt;loader&gt;/&lt;mcVersion&gt;/{version,classes,members,removed,idioms}.json + templates/
 *
 * 两个复用机制（都在 version.json 中声明，引擎不含任何版本知识）：
 *  - "aliases": {"1.19.1": {"forgeVersion": "42.0.9"}, ...}
 *      完全互通的补丁版本：同一套映射数据直接服务多个版本，仅按别名覆盖
 *      forgeVersion / packFormat 等元信息（影响 build.gradle / mods.toml 生成）。
 *  - "basedOn": "1.19.4"
 *      细微差别的版本：本目录只写与基版本的差异；加载时先载入基版本再叠加。
 *      classes/members/removed/idioms 均为「覆盖合并」，可用顶层 "!remove" 数组删除基版本条目
 *      （classes: IR id；members: "classIr#memberIr" 或整个 "classIr"；
 *       removed: {"classes": [...], "members": [...]} 直接以对象形式写在 "!remove" 键下）。
 *      templates/ 逐层回退：先查本目录，再查基版本。
 */
public final class MappingRepository {

    private final Path root;
    private final Map<String, VersionMappings> cache = new HashMap<>();

    public MappingRepository(Path root) {
        this.root = root;
    }

    public Path root() { return root; }

    /** 列出所有 (loader, version)，包含别名版本。 */
    public List<String[]> listVersions() throws IOException {
        List<String[]> result = new ArrayList<>();
        Path versions = root.resolve("versions");
        if (!Files.isDirectory(versions)) return result;
        try (DirectoryStream<Path> loaders = Files.newDirectoryStream(versions)) {
            for (Path loaderDir : loaders) {
                if (!Files.isDirectory(loaderDir)) continue;
                String loader = loaderDir.getFileName().toString();
                Set<String> names = new LinkedHashSet<>();
                try (DirectoryStream<Path> vers = Files.newDirectoryStream(loaderDir)) {
                    for (Path verDir : vers) {
                        Path versionJson = verDir.resolve("version.json");
                        if (!Files.isRegularFile(versionJson)) continue;
                        names.add(verDir.getFileName().toString());
                        try {
                            JsonObject o = readJson(versionJson).getAsJsonObject();
                            if (o.has("aliases")) {
                                names.addAll(o.getAsJsonObject("aliases").keySet());
                            }
                        } catch (Exception ignored) {
                            // 别名解析失败不影响主版本可见
                        }
                    }
                }
                for (String name : names) {
                    result.add(new String[]{loader, name});
                }
            }
        }
        return result;
    }

    public VersionMappings load(String loader, String mcVersion) throws IOException {
        return load(loader, mcVersion, new HashSet<>());
    }

    private VersionMappings load(String loader, String mcVersion, Set<String> visiting) throws IOException {
        String key = loader + "/" + mcVersion;
        VersionMappings cached = cache.get(key);
        if (cached != null) return cached;
        if (!visiting.add(key)) {
            throw new IOException("版本映射存在 basedOn 循环引用: " + key);
        }

        Path dir = root.resolve("versions").resolve(loader).resolve(mcVersion);
        VersionMappings vm;
        if (Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("version.json"))) {
            vm = loadDir(loader, dir, visiting);
        } else {
            vm = loadAlias(loader, mcVersion, visiting);
            if (vm == null) {
                throw new IOException("找不到版本映射: versions/" + key
                        + "（既没有同名目录，也没有任何版本把它声明为 alias）");
            }
        }
        cache.put(key, vm);
        return vm;
    }

    /** 在同 loader 的各版本 version.json 里查找把 mcVersion 声明为别名的目录。 */
    private VersionMappings loadAlias(String loader, String mcVersion, Set<String> visiting) throws IOException {
        Path loaderDir = root.resolve("versions").resolve(loader);
        if (!Files.isDirectory(loaderDir)) return null;
        try (DirectoryStream<Path> vers = Files.newDirectoryStream(loaderDir)) {
            for (Path verDir : vers) {
                Path versionJson = verDir.resolve("version.json");
                if (!Files.isRegularFile(versionJson)) continue;
                JsonObject o;
                try {
                    o = readJson(versionJson).getAsJsonObject();
                } catch (Exception e) {
                    continue;
                }
                if (!o.has("aliases")) continue;
                JsonObject aliases = o.getAsJsonObject("aliases");
                if (!aliases.has(mcVersion)) continue;

                VersionMappings base = load(loader, verDir.getFileName().toString(), visiting);
                VersionMappings.VersionInfo info = base.info.copy();
                info.mcVersion = mcVersion;
                JsonElement overrides = aliases.get(mcVersion);
                if (overrides.isJsonObject()) {
                    applyInfoOverrides(info, overrides.getAsJsonObject());
                }
                return base.withInfo(info);
            }
        }
        return null;
    }

    private VersionMappings loadDir(String loader, Path dir, Set<String> visiting) throws IOException {
        JsonObject versionObj = readJson(dir.resolve("version.json")).getAsJsonObject();
        String basedOn = optString(versionObj, "basedOn");

        Map<String, VersionMappings.ClassEntry> classes;
        Map<String, Map<String, VersionMappings.MemberEntry>> members;
        Map<String, VersionMappings.RemovedEntry> removedClasses = new HashMap<>();
        Map<String, VersionMappings.RemovedEntry> removedMembers = new HashMap<>();
        Map<String, VersionMappings.IdiomForm> idioms = new HashMap<>();
        Map<String, String> guidance = new HashMap<>();
        Set<String> supported = new HashSet<>();
        VersionMappings.VersionInfo info;
        List<Path> templateDirs = new ArrayList<>();

        if (basedOn != null) {
            VersionMappings base = load(loader, basedOn, visiting);
            info = base.info.copy();
            info.mcVersion = dir.getFileName().toString();
            applyInfoOverrides(info, versionObj);
            classes = new HashMap<>(base.classesByIr);
            members = deepCopyMembers(base.members);
            removedClasses.putAll(base.removedClasses);
            removedMembers.putAll(base.removedMembers);
            idioms.putAll(base.idioms);
            guidance.putAll(base.guidance);
            supported.addAll(base.supportedConcepts);
            templateDirs.add(dir.resolve("templates"));
            templateDirs.addAll(base.templateDirs);
        } else {
            info = parseVersionInfo(versionObj);
            classes = new HashMap<>();
            members = new HashMap<>();
            templateDirs.add(dir.resolve("templates"));
        }

        mergeClasses(classes, dir.resolve("classes.json"));
        mergeMembers(members, dir.resolve("members.json"));
        mergeRemoved(removedClasses, removedMembers, dir.resolve("removed.json"));
        mergeIdioms(idioms, guidance, supported, dir.resolve("idioms.json"));

        return new VersionMappings(dir, templateDirs, info, classes, members,
                removedClasses, removedMembers, idioms, guidance, supported);
    }

    private static Map<String, Map<String, VersionMappings.MemberEntry>> deepCopyMembers(
            Map<String, Map<String, VersionMappings.MemberEntry>> src) {
        Map<String, Map<String, VersionMappings.MemberEntry>> copy = new HashMap<>();
        src.forEach((k, v) -> copy.put(k, new HashMap<>(v)));
        return copy;
    }

    // ---- parsing / merging helpers ----

    private static JsonElement readJson(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(text);
    }

    private static String optString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()
                ? o.get(key).getAsString() : null;
    }

    private static VersionMappings.VersionInfo parseVersionInfo(JsonObject o) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.javaVersion = 17;
        applyInfoOverrides(info, o);
        return info;
    }

    /** 只把 JSON 中出现的字段覆盖到 info 上（供别名/覆盖层复用）。 */
    private static void applyInfoOverrides(VersionMappings.VersionInfo info, JsonObject o) {
        if (o.has("mcVersion")) info.mcVersion = optString(o, "mcVersion");
        if (o.has("loader")) info.loader = optString(o, "loader");
        if (o.has("javaVersion")) info.javaVersion = o.get("javaVersion").getAsInt();
        if (o.has("metadataFormat")) info.metadataFormat = optString(o, "metadataFormat");
        if (o.has("metadataPath")) info.metadataPath = optString(o, "metadataPath");
        if (o.has("langFormat")) info.langFormat = optString(o, "langFormat");
        if (o.has("modAnnotationStyle")) info.modAnnotationStyle = optString(o, "modAnnotationStyle");
        if (o.has("lifecycleStyle")) info.lifecycleStyle = optString(o, "lifecycleStyle");
        if (o.has("packFormat")) info.packFormat = o.get("packFormat").getAsInt();
        if (o.has("forgeVersion")) info.forgeVersion = optString(o, "forgeVersion");
        if (o.has("loaderVersionRange")) info.loaderVersionRange = optString(o, "loaderVersionRange");
        if (o.has("mappingsChannel")) info.mappingsChannel = optString(o, "mappingsChannel");
        if (o.has("gradleVersion")) info.gradleVersion = optString(o, "gradleVersion");
        if (o.has("langKeys")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("langKeys").entrySet()) {
                info.langKeys.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("texturePrefixes")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("texturePrefixes").entrySet()) {
                info.texturePrefixes.put(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    /** classes.json 的值可为字符串（仅 FQCN）或对象 {"name": fqcn, "note": "..."}；"!remove" 为待删 IR id 数组。 */
    private static void mergeClasses(Map<String, VersionMappings.ClassEntry> target, Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        JsonObject o = readJson(file).getAsJsonObject();
        applyKeyRemovals(target, o);
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().equals("!remove")) continue;
            if (e.getValue().isJsonObject()) {
                JsonObject v = e.getValue().getAsJsonObject();
                target.put(e.getKey(), new VersionMappings.ClassEntry(
                        v.get("name").getAsString(), optString(v, "note")));
            } else {
                target.put(e.getKey(), new VersionMappings.ClassEntry(e.getValue().getAsString(), null));
            }
        }
    }

    /**
     * members.json: { classIr: { irMemberName: {"name":..,"kind":..,"note":..} 或 字符串 } }。
     * "!remove" 数组元素为 "classIr#memberIr"（删单个成员）或 "classIr"（删整类）。
     */
    private static void mergeMembers(Map<String, Map<String, VersionMappings.MemberEntry>> target,
                                     Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        JsonObject o = readJson(file).getAsJsonObject();
        if (o.has("!remove")) {
            for (JsonElement r : o.getAsJsonArray("!remove")) {
                String spec = r.getAsString();
                int hash = spec.indexOf('#');
                if (hash < 0) {
                    target.remove(spec);
                } else {
                    Map<String, VersionMappings.MemberEntry> cls = target.get(spec.substring(0, hash));
                    if (cls != null) cls.remove(spec.substring(hash + 1));
                }
            }
        }
        for (Map.Entry<String, JsonElement> cls : o.entrySet()) {
            if (cls.getKey().equals("!remove")) continue;
            Map<String, VersionMappings.MemberEntry> memberMap =
                    target.computeIfAbsent(cls.getKey(), k -> new HashMap<>());
            for (Map.Entry<String, JsonElement> m : cls.getValue().getAsJsonObject().entrySet()) {
                if (m.getValue().isJsonObject()) {
                    JsonObject v = m.getValue().getAsJsonObject();
                    memberMap.put(m.getKey(), new VersionMappings.MemberEntry(
                            v.has("name") ? v.get("name").getAsString() : m.getKey(),
                            v.has("kind") ? v.get("kind").getAsString() : "method",
                            optString(v, "note")));
                } else {
                    memberMap.put(m.getKey(), new VersionMappings.MemberEntry(
                            m.getValue().getAsString(), "method", null));
                }
            }
        }
    }

    /**
     * removed.json: {"classes": {fqcn: {"concept":..,"message":..}}, "members": {name: {...}},
     *                "!remove": {"classes": [fqcn...], "members": [name...]}}
     */
    private static void mergeRemoved(Map<String, VersionMappings.RemovedEntry> classes,
                                     Map<String, VersionMappings.RemovedEntry> members,
                                     Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        JsonObject o = readJson(file).getAsJsonObject();
        if (o.has("!remove") && o.get("!remove").isJsonObject()) {
            JsonObject rem = o.getAsJsonObject("!remove");
            if (rem.has("classes")) rem.getAsJsonArray("classes").forEach(e -> classes.remove(e.getAsString()));
            if (rem.has("members")) rem.getAsJsonArray("members").forEach(e -> members.remove(e.getAsString()));
        }
        if (o.has("classes")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("classes").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                classes.put(e.getKey(), new VersionMappings.RemovedEntry(
                        optString(v, "concept"), optString(v, "message")));
            }
        }
        if (o.has("members")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("members").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                members.put(e.getKey(), new VersionMappings.RemovedEntry(
                        optString(v, "concept"), optString(v, "message")));
            }
        }
    }

    /**
     * idioms.json: {"forms": {idiomId: {"type":..,"class":..,"method":..}},
     *               "guidance": {concept: text}, "supported": [conceptId, ...],
     *               "!removeSupported": [conceptId, ...]}
     */
    private static void mergeIdioms(Map<String, VersionMappings.IdiomForm> idioms,
                                    Map<String, String> guidance,
                                    Set<String> supported,
                                    Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        JsonObject o = readJson(file).getAsJsonObject();
        if (o.has("forms")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("forms").entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                idioms.put(e.getKey(), new VersionMappings.IdiomForm(
                        v.get("type").getAsString(),
                        v.get("class").getAsString(),
                        optString(v, "method"),
                        v.has("arity") ? v.get("arity").getAsInt() : null));
            }
        }
        if (o.has("guidance")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("guidance").entrySet()) {
                guidance.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("supported")) {
            for (JsonElement e : o.getAsJsonArray("supported")) {
                supported.add(e.getAsString());
            }
        }
        if (o.has("!removeSupported")) {
            for (JsonElement e : o.getAsJsonArray("!removeSupported")) {
                supported.remove(e.getAsString());
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void applyKeyRemovals(Map<String, ?> target, JsonObject o) {
        if (o.has("!remove")) {
            o.getAsJsonArray("!remove").forEach(e -> target.remove(e.getAsString()));
        }
    }

    // ---- Java 平台数据（mappings/java/）----

    private final Map<Integer, JavaPlatform> javaCache = new HashMap<>();
    private Map<String, Integer> javaFeatures;

    /**
     * 加载某 Java 版本的平台数据：优先精确文件 java/&lt;v&gt;.json，
     * 否则找 "aliases" 数组包含该版本号的文件（互通版本一个文件覆盖）。无数据返回 null。
     */
    public JavaPlatform loadJavaPlatform(int javaVersion) throws IOException {
        if (javaCache.containsKey(javaVersion)) return javaCache.get(javaVersion);
        Path javaDir = root.resolve("java");
        JavaPlatform platform = null;
        Path exact = javaDir.resolve(javaVersion + ".json");
        if (Files.isRegularFile(exact)) {
            platform = parseJavaPlatform(javaVersion, readJson(exact).getAsJsonObject());
        } else if (Files.isDirectory(javaDir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(javaDir, "*.json")) {
                for (Path file : files) {
                    if (file.getFileName().toString().equals("features.json")) continue;
                    JsonObject o = readJson(file).getAsJsonObject();
                    if (o.has("aliases")) {
                        for (JsonElement a : o.getAsJsonArray("aliases")) {
                            if (a.getAsInt() == javaVersion) {
                                platform = parseJavaPlatform(javaVersion, o);
                                break;
                            }
                        }
                    }
                    if (platform != null) break;
                }
            }
        }
        javaCache.put(javaVersion, platform);
        return platform;
    }

    /** 语法特性 -> 引入的 Java 版本（mappings/java/features.json，全平台共享一份）。 */
    public Map<String, Integer> javaFeatures() throws IOException {
        if (javaFeatures != null) return javaFeatures;
        Map<String, Integer> result = new HashMap<>();
        Path file = root.resolve("java").resolve("features.json");
        if (Files.isRegularFile(file)) {
            for (Map.Entry<String, JsonElement> e : readJson(file).getAsJsonObject().entrySet()) {
                result.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        javaFeatures = result;
        return result;
    }

    private static JavaPlatform parseJavaPlatform(int version, JsonObject o) {
        Set<String> illegal = new HashSet<>();
        Set<String> restricted = new HashSet<>();
        Map<String, String> removed = new HashMap<>();
        Map<String, String> encapsulated = new HashMap<>();
        Map<String, List<JavaPlatform.MethodIssue>> methods = new HashMap<>();
        Map<String, List<JavaPlatform.ArgumentIssue>> arguments = new HashMap<>();
        List<JavaPlatform.ReflectiveLookup> reflective = new ArrayList<>();

        if (o.has("illegalIdentifiers")) {
            o.getAsJsonArray("illegalIdentifiers").forEach(e -> illegal.add(e.getAsString()));
        }
        if (o.has("restrictedTypeNames")) {
            o.getAsJsonArray("restrictedTypeNames").forEach(e -> restricted.add(e.getAsString()));
        }
        if (o.has("removedClasses")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("removedClasses").entrySet()) {
                removed.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("encapsulatedPackages")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("encapsulatedPackages").entrySet()) {
                encapsulated.put(e.getKey(), e.getValue().getAsString());
            }
        }
        // "removedMethods": { "Thread#stop": "指导" | {"message": ..., "anyReceiver": true} }
        if (o.has("removedMethods")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("removedMethods").entrySet()) {
                String[] spec = splitMemberSpec(e.getKey());
                String message;
                boolean anyReceiver = false;
                if (e.getValue().isJsonObject()) {
                    JsonObject v = e.getValue().getAsJsonObject();
                    message = optString(v, "message");
                    anyReceiver = v.has("anyReceiver") && v.get("anyReceiver").getAsBoolean();
                } else {
                    message = e.getValue().getAsString();
                }
                methods.computeIfAbsent(spec[1], k -> new ArrayList<>())
                        .add(new JavaPlatform.MethodIssue(spec[0], spec[1], message, anyReceiver));
            }
        }
        // "argumentIssues": { "ScriptEngineManager#getEngineByName": {"values": [...], "message": ...} }
        if (o.has("argumentIssues")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("argumentIssues").entrySet()) {
                String[] spec = splitMemberSpec(e.getKey());
                JsonObject v = e.getValue().getAsJsonObject();
                List<String> values = new ArrayList<>();
                if (v.has("values")) v.getAsJsonArray("values").forEach(x -> values.add(x.getAsString()));
                arguments.computeIfAbsent(spec[1], k -> new ArrayList<>())
                        .add(new JavaPlatform.ArgumentIssue(spec[0], spec[1], values, optString(v, "message")));
            }
        }
        // "reflectiveLookups": ["Class#forName", "ClassLoader#loadClass"]
        if (o.has("reflectiveLookups")) {
            for (JsonElement e : o.getAsJsonArray("reflectiveLookups")) {
                String[] spec = splitMemberSpec(e.getAsString());
                reflective.add(new JavaPlatform.ReflectiveLookup(spec[0], spec[1]));
            }
        }
        return new JavaPlatform(version, illegal, restricted, removed, encapsulated,
                methods, arguments, reflective);
    }

    /** "Type#method" -> [Type, method]；"method" -> [null, method]。 */
    private static String[] splitMemberSpec(String spec) {
        int hash = spec.indexOf('#');
        return hash < 0 ? new String[]{null, spec}
                : new String[]{spec.substring(0, hash), spec.substring(hash + 1)};
    }
}
