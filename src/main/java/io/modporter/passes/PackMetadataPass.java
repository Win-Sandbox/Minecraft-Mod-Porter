package io.modporter.passes;

import com.google.gson.*;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;
import io.modporter.mappings.PackFormat;
import io.modporter.mappings.VersionMappings.VersionInfo;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Single-target pack metadata migration. Does not promise content/schema conversion. */
public final class PackMetadataPass {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final PortContext ctx;
    private Set<String> paths = Set.of();
    public PackMetadataPass(PortContext ctx) { this.ctx = ctx; }
    public void setProjectPaths(Collection<String> paths) { this.paths = Set.copyOf(paths); }
    private enum Kind { RESOURCE, DATA, MIXED, UNKNOWN }
    private record Range(PackFormat min, PackFormat max) {
        Range { if (min.compareTo(max) > 0) throw new IllegalArgumentException("倒置版本区间"); }
        boolean contains(PackFormat value) { return min.compareTo(value) <= 0 && max.compareTo(value) >= 0; }
    }
    public OutputFile transform(String path, String content) {
        try {
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("根节点不是对象");
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("pack") || !root.get("pack").isJsonObject()) throw new IllegalArgumentException("缺少 pack 对象");
            Kind kind = kind(path);
            if (kind == Kind.UNKNOWN) return keep(path, "无法从同目录 assets/ 或 data/ 确定包类型，原文件保留");
            PackFormat target = format(ctx.target().info, kind);
            PackFormat source = format(ctx.source().info, kind);
            if (target == null) return keep(path, "目标包类型格式缺失，或同时含 assets/data 且二者格式不同，原文件保留");
            String style = ctx.target().info.packMetadataStyle;
            if (style == null) {
                // Old mappings only have a resource integer. No silent downgrade of a range schema.
                if (root.getAsJsonObject("pack").has("min_format") || root.getAsJsonObject("pack").has("max_format"))
                    return keep(path, "目标映射未声明 packMetadataStyle，不能猜测新版区间格式");
                style = "legacy";
            }
            if (!style.equals("range") && !style.equals("legacy")) return keep(path, "未知目标 packMetadataStyle");
            if (style.equals("legacy") && target.minor() != 0) return keep(path, "legacy 格式不能表达非零次版本，原文件保留");
            JsonObject pack = root.getAsJsonObject("pack");
            // Validate existing version fields before touching any of them.
            if (pack.has("pack_format")) PackFormat.integer(pack.get("pack_format"));
            if (pack.has("supported_formats")) legacyRange(pack.get("supported_formats"));
            if (pack.has("min_format") || pack.has("max_format")) modernRange(pack);
            JsonObject migrated = root.deepCopy();
            List<String> notes = new ArrayList<>();
            if (root.has("overlays")) {
                if (!root.get("overlays").isJsonObject()) throw new IllegalArgumentException("overlays 不是对象");
                JsonObject overlays = root.getAsJsonObject("overlays");
                if (!overlays.has("entries") || !overlays.get("entries").isJsonArray()) throw new IllegalArgumentException("overlays.entries 不是数组");
                JsonArray entries = overlays.getAsJsonArray("entries");
                if (entries.size() > 0 && (style.equals("legacy") || source == null))
                    return keep(path, "含 overlays：旧目标或未知源格式无法安全选择/扁平化覆盖内容，整个元数据保留");
                PackFormat overlaySource = source;
                // A second pass over already migrated metadata must not drop its active overlays.
                if (style.equals("range") && pack.has("min_format") && pack.has("max_format")) {
                    Range declared = modernRange(pack);
                    if (declared.min().equals(target) && declared.max().equals(target)) overlaySource = target;
                }
                JsonArray selected = new JsonArray();
                Set<String> directories = new HashSet<>();
                for (JsonElement entry : entries) {
                    if (!entry.isJsonObject()) throw new IllegalArgumentException("overlay entry 不是对象");
                    JsonObject object = entry.getAsJsonObject();
                    if (!object.has("directory") || !object.get("directory").isJsonPrimitive()
                            || !object.getAsJsonPrimitive("directory").isString()) throw new IllegalArgumentException("overlay 缺少 directory");
                    String directory = object.get("directory").getAsString();
                    if (!directory.matches("[a-zA-Z0-9_-]+") || !directories.add(directory))
                        throw new IllegalArgumentException("overlay directory 非法/重复，无法安全迁移");
                    Range range = object.has("min_format") || object.has("max_format")
                            ? modernRange(object) : legacyRange(object.get("formats"));
                    if (object.has("formats")) legacyRange(object.get("formats"));
                    if (range.contains(overlaySource)) {
                        JsonObject changed = object.deepCopy();
                        changed.remove("formats"); setRange(changed, target); selected.add(changed);
                    } else notes.add("未激活 overlay " + directory + " 的入口已移除；目录内容保留供人工核对");
                }
                if (selected.isEmpty()) migrated.remove("overlays");
                else migrated.getAsJsonObject("overlays").add("entries", selected);
                if (entries.size() > 0) notes.add("只保留源版本激活的 overlays，并改为目标单版本；未验证其资源内容是否兼容");
            }
            JsonObject targetPack = migrated.getAsJsonObject("pack");
            targetPack.remove("supported_formats");
            if (style.equals("range")) {
                targetPack.remove("pack_format"); setRange(targetPack, target);
            } else {
                targetPack.remove("min_format"); targetPack.remove("max_format"); targetPack.addProperty("pack_format", target.major());
                if (migrated.has("overlays") && migrated.getAsJsonObject("overlays").getAsJsonArray("entries").isEmpty()) migrated.remove("overlays");
            }
            if (migrated.equals(root)) return null;
            ctx.info(path, null, "pack-mcmeta", "包格式按 " + kind + " 更新为 " + target.major() + "." + target.minor() + " (" + style + ")");
            if (pack.has("supported_formats") || pack.has("min_format")) notes.add("兼容范围已收窄到单个目标版本，不再声明跨版本内容兼容");
            for (String note : notes) ctx.todo(path, null, "pack-mcmeta", note);
            return new OutputFile(path, (GSON.toJson(migrated) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            return keep(path, "pack.mcmeta 结构/格式不受支持，原文件保留：" + ex.getMessage());
        }
    }
    private Kind kind(String path) {
        String parent = path.substring(0, path.length() - "pack.mcmeta".length());
        boolean assets = false, data = false;
        for (String file : paths) {
            if (file.startsWith(parent + "assets/")) assets = true;
            if (file.startsWith(parent + "data/")) data = true;
        }
        return assets ? (data ? Kind.MIXED : Kind.RESOURCE) : (data ? Kind.DATA : Kind.UNKNOWN);
    }
    private static PackFormat format(VersionInfo info, Kind kind) {
        PackFormat resource = info.resourcePackFormat;
        if (resource == null && info.packFormat > 0 && !"range".equals(info.packMetadataStyle)) resource = new PackFormat(info.packFormat, 0);
        if (kind == Kind.RESOURCE) return resource;
        if (kind == Kind.DATA) return info.dataPackFormat;
        if (kind == Kind.MIXED && resource != null && resource.equals(info.dataPackFormat)) return resource;
        return null;
    }
    private static PackFormat bound(JsonElement element, boolean upper) {
        if (element == null) throw new IllegalArgumentException("缺少区间端点");
        if (element.isJsonPrimitive()) return new PackFormat(PackFormat.integer(element), upper ? Integer.MAX_VALUE : 0);
        if (!element.isJsonArray()) throw new IllegalArgumentException("区间端点必须为整数或数组");
        JsonArray a = element.getAsJsonArray();
        if (a.size() == 1) return new PackFormat(PackFormat.integer(a.get(0)), upper ? Integer.MAX_VALUE : 0);
        if (a.size() == 2) return new PackFormat(PackFormat.integer(a.get(0)), PackFormat.integer(a.get(1)));
        throw new IllegalArgumentException("区间端点数组只能有1或2个整数");
    }
    private static Range modernRange(JsonObject object) { return new Range(bound(object.get("min_format"), false), bound(object.get("max_format"), true)); }
    private static Range legacyRange(JsonElement value) {
        if (value == null) throw new IllegalArgumentException("缺少 formats");
        int min, max;
        if (value.isJsonPrimitive()) min = max = PackFormat.integer(value);
        else if (value.isJsonArray() && value.getAsJsonArray().size() == 2) {
            min = PackFormat.integer(value.getAsJsonArray().get(0)); max = PackFormat.integer(value.getAsJsonArray().get(1));
        } else if (value.isJsonObject()) {
            min = PackFormat.integer(value.getAsJsonObject().get("min_inclusive"));
            max = PackFormat.integer(value.getAsJsonObject().get("max_inclusive"));
        } else throw new IllegalArgumentException("无效旧版格式区间");
        return new Range(new PackFormat(min, 0), new PackFormat(max, Integer.MAX_VALUE));
    }
    private static void setRange(JsonObject object, PackFormat version) {
        object.add("min_format", version.toJson()); object.add("max_format", version.toJson());
    }
    private OutputFile keep(String path, String why) { ctx.todo(path, null, "pack-mcmeta", why); return null; }
}
