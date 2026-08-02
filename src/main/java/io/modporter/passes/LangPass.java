package io.modporter.passes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语言文件转换：.lang <-> .json，并按 version.json 中的键模式做本地化键迁移
 * （如 tile.{modid}.{name}.name -> block.{modid}.{name}）。
 */
public final class LangPass {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final PortContext ctx;

    public LangPass(PortContext ctx) {
        this.ctx = ctx;
    }

    public OutputFile transform(String relPath, String content) {
        Map<String, String> entries = "lang".equals(ctx.source().info.langFormat)
                ? parseLang(content)
                : parseJson(relPath, content);
        if (entries == null) return null;

        Map<String, String> out = new LinkedHashMap<>();
        int migrated = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String newKey = migrateKey(e.getKey());
            if (!newKey.equals(e.getKey())) migrated++;
            out.put(newKey, e.getValue());
        }
        ctx.info(relPath, null, "lang", "语言条目 " + out.size() + " 个，其中 " + migrated + " 个键已按目标版本格式迁移");

        String targetFormat = ctx.target().info.langFormat;
        String outPath = replaceExtension(relPath, "lang".equals(targetFormat) ? ".lang" : ".json");
        String rendered = "lang".equals(targetFormat) ? renderLang(out) : renderJson(out);
        return new OutputFile(outPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    // ---- 键迁移：源模式 -> (kind, modid, name) -> 目标模式 ----

    private String migrateKey(String key) {
        Map<String, String> sourceKeys = ctx.source().info.langKeys;
        Map<String, String> targetKeys = ctx.target().info.langKeys;
        for (Map.Entry<String, String> pattern : sourceKeys.entrySet()) {
            String kind = pattern.getKey();
            Matcher m = patternToRegex(pattern.getValue()).matcher(key);
            if (!m.matches()) continue;
            String targetPattern = targetKeys.get(kind);
            if (targetPattern == null) return key;
            String modid = groupOrNull(m, "modid");
            if (modid == null) modid = ctx.modMeta.modid;
            String name = groupOrNull(m, "name");
            return targetPattern
                    .replace("{modid}", modid)
                    .replace("{name}", name == null ? "" : name);
        }
        return key;
    }

    private static Pattern patternToRegex(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < pattern.length()) {
            if (pattern.startsWith("{modid}", i)) {
                sb.append("(?<modid>[a-z0-9_-]+)");
                i += 7;
            } else if (pattern.startsWith("{name}", i)) {
                sb.append("(?<name>.+?)");
                i += 6;
            } else {
                sb.append(Pattern.quote(String.valueOf(pattern.charAt(i))));
                i++;
            }
        }
        return Pattern.compile(sb.append("$").toString());
    }

    private static String groupOrNull(Matcher m, String group) {
        try {
            return m.group(group);
        } catch (IllegalArgumentException e) {
            return null; // 模式中没有该分组
        }
    }

    // ---- 解析 / 输出 ----

    private static Map<String, String> parseLang(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : content.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            result.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
        }
        return result;
    }

    private Map<String, String> parseJson(String relPath, String content) {
        try {
            JsonObject o = JsonParser.parseString(content).getAsJsonObject();
            Map<String, String> result = new LinkedHashMap<>();
            o.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
            return result;
        } catch (Exception e) {
            ctx.error(relPath, null, "lang", "语言 JSON 解析失败: " + e.getMessage());
            return null;
        }
    }

    private static String renderLang(Map<String, String> entries) {
        StringBuilder sb = new StringBuilder();
        entries.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        return sb.toString();
    }

    private static String renderJson(Map<String, String> entries) {
        JsonObject o = new JsonObject();
        entries.forEach(o::addProperty);
        return GSON.toJson(o) + "\n";
    }

    private static String replaceExtension(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        return (dot >= 0 ? path.substring(0, dot) : path) + newExt;
    }
}
