package io.modporter.passes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 资源 JSON 转换：blockstates、models、pack.mcmeta。
 * 纹理路径前缀（blocks/ vs block/ 等）与 pack_format 均来自版本映射数据。
 */
public final class AssetJsonPass {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final PortContext ctx;

    public AssetJsonPass(PortContext ctx) {
        this.ctx = ctx;
    }

    public OutputFile transformBlockstate(String relPath, String content) {
        JsonElement root = parse(relPath, content);
        if (root == null) return null;
        JsonObject o = root.getAsJsonObject();

        if (o.has("forge_marker")) {
            ctx.todo(relPath, null, "blockstate",
                    "该 blockstate 使用 Forge 专有格式（forge_marker），目标版本不支持，需要人工重写为原版格式");
        }
        // "normal" 变体在扁平化后写作 ""
        if (o.has("variants")) {
            JsonObject variants = o.getAsJsonObject("variants");
            if (variants.has("normal") && !variants.has("")) {
                JsonElement v = variants.remove("normal");
                variants.add("", v);
                ctx.info(relPath, null, "blockstate", "变体 \"normal\" 已改名为 \"\"");
            }
        }
        rewriteTexturePaths(o);
        return new OutputFile(relPath, render(o));
    }

    public OutputFile transformModel(String relPath, String content) {
        JsonElement root = parse(relPath, content);
        if (root == null) return null;
        JsonObject o = root.getAsJsonObject();
        rewriteTexturePaths(o);
        return new OutputFile(relPath, render(o));
    }

    public OutputFile transformPackMcmeta(String relPath, String content) {
        JsonElement root = parse(relPath, content);
        if (root == null) return null;
        JsonObject o = root.getAsJsonObject();
        int targetFormat = ctx.target().info.packFormat;
        if (targetFormat > 0 && o.has("pack")) {
            o.getAsJsonObject("pack").addProperty("pack_format", targetFormat);
            ctx.info(relPath, null, "pack-mcmeta", "pack_format 已更新为 " + targetFormat);
        }
        return new OutputFile(relPath, render(o));
    }

    /**
     * 递归替换 JSON 中所有字符串值里的纹理路径前缀。
     * 只处理形如 "modid:blocks/xxx" 或 "blocks/xxx" 的资源路径字符串。
     */
    private void rewriteTexturePaths(JsonObject obj) {
        Map<String, String> sourcePrefixes = ctx.source().info.texturePrefixes;
        Map<String, String> targetPrefixes = ctx.target().info.texturePrefixes;
        if (sourcePrefixes.isEmpty() || targetPrefixes.isEmpty()) return;
        rewriteElement(obj, sourcePrefixes, targetPrefixes);
    }

    private void rewriteElement(JsonElement el, Map<String, String> src, Map<String, String> tgt) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String rewritten = rewritePath(v.getAsString(), src, tgt);
                    if (rewritten != null) {
                        o.add(e.getKey(), new JsonPrimitive(rewritten));
                    }
                } else {
                    rewriteElement(v, src, tgt);
                }
            }
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement v = arr.get(i);
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String rewritten = rewritePath(v.getAsString(), src, tgt);
                    if (rewritten != null) {
                        arr.set(i, new JsonPrimitive(rewritten));
                    }
                } else {
                    rewriteElement(v, src, tgt);
                }
            }
        }
    }

    /** 返回改写后的路径；无需改写返回 null。 */
    private String rewritePath(String value, Map<String, String> src, Map<String, String> tgt) {
        int colon = value.indexOf(':');
        String prefix = colon >= 0 ? value.substring(0, colon + 1) : "";
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        for (Map.Entry<String, String> e : src.entrySet()) {
            String sourcePrefix = e.getValue();
            String targetPrefix = tgt.get(e.getKey());
            if (targetPrefix == null || sourcePrefix.equals(targetPrefix)) continue;
            if (path.startsWith(sourcePrefix)) {
                return prefix + targetPrefix + path.substring(sourcePrefix.length());
            }
        }
        return null;
    }

    private JsonElement parse(String relPath, String content) {
        try {
            return JsonParser.parseString(content);
        } catch (Exception e) {
            ctx.error(relPath, null, "asset-json", "JSON 解析失败，文件原样复制: " + e.getMessage());
            return null;
        }
    }

    private static byte[] render(JsonElement el) {
        return (GSON.toJson(el) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
