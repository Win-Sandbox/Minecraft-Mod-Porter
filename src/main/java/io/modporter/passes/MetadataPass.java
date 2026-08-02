package io.modporter.passes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modporter.engine.ModMeta;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模组元数据转换：mcmod.info / mods.toml -> ModMeta(IR) -> 目标格式。
 * 目标格式由目标版本 templates/ 下的模板生成，键值来自 IR。
 */
public final class MetadataPass {

    private final PortContext ctx;

    public MetadataPass(PortContext ctx) {
        this.ctx = ctx;
    }

    /** 解析源元数据文件填充 ctx.modMeta。relPath 仅用于报告。 */
    public void parse(String relPath, String content) {
        String format = ctx.source().info.metadataFormat;
        try {
            if ("mcmod.info".equals(format)) {
                parseMcmodInfo(content);
            } else if ("mods.toml".equals(format)) {
                parseModsToml(content);
            } else {
                ctx.warn(relPath, null, "metadata", "未知元数据格式: " + format);
                return;
            }
            ctx.info(relPath, null, "metadata",
                    "已解析元数据: modid=" + ctx.modMeta.modid + ", version=" + ctx.modMeta.version);
        } catch (Exception e) {
            ctx.error(relPath, null, "metadata", "元数据解析失败: " + e.getMessage());
        }
    }

    /** 按目标版本格式生成元数据文件。 */
    public OutputFile generate(String sourceRelPath) {
        String targetPath = ctx.target().info.metadataPath;
        String targetFormat = ctx.target().info.metadataFormat;
        String template = readTemplate(metadataTemplateName(targetFormat));
        if (template == null) {
            ctx.error(sourceRelPath, null, "metadata",
                    "目标版本缺少元数据模板 templates/" + metadataTemplateName(targetFormat) + "，无法生成 " + targetPath);
            return null;
        }
        String rendered = renderTemplate(template);
        ctx.info(sourceRelPath, null, "metadata", "已生成 " + targetPath);
        return new OutputFile(targetPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    private static String metadataTemplateName(String format) {
        return "mcmod.info".equals(format) ? "mcmod.info" : "mods.toml";
    }

    private void parseMcmodInfo(String content) {
        JsonElement root = JsonParser.parseString(content);
        JsonArray arr;
        if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().has("modList")) {
            arr = root.getAsJsonObject().getAsJsonArray("modList");
        } else {
            throw new IllegalArgumentException("mcmod.info 结构无法识别");
        }
        if (arr.isEmpty()) throw new IllegalArgumentException("mcmod.info 为空");
        JsonObject mod = arr.get(0).getAsJsonObject();
        ModMeta meta = ctx.modMeta;
        if (mod.has("modid")) meta.modid = mod.get("modid").getAsString();
        if (mod.has("name")) meta.name = mod.get("name").getAsString();
        if (mod.has("version")) meta.version = mod.get("version").getAsString();
        if (mod.has("description")) meta.description = mod.get("description").getAsString().trim();
        if (mod.has("url")) meta.url = mod.get("url").getAsString();
        if (mod.has("logoFile")) meta.logoFile = mod.get("logoFile").getAsString();
        if (mod.has("credits")) meta.credits = mod.get("credits").getAsString();
        if (mod.has("authorList")) {
            for (JsonElement a : mod.getAsJsonArray("authorList")) {
                meta.authors.add(a.getAsString());
            }
        }
        if (arr.size() > 1) {
            ctx.todo(null, null, "metadata",
                    "mcmod.info 中声明了 " + arr.size() + " 个 mod，仅迁移第一个，其余需人工处理");
        }
    }

    /** 极简 mods.toml 解析：只取 [[mods]] 段内和全局的 key="value" / key='''...'''。 */
    private void parseModsToml(String content) {
        ModMeta meta = ctx.modMeta;
        meta.modid = tomlValue(content, "modId", meta.modid);
        meta.name = tomlValue(content, "displayName", meta.name);
        meta.version = tomlValue(content, "version", meta.version);
        meta.url = tomlValue(content, "displayURL", meta.url);
        meta.logoFile = tomlValue(content, "logoFile", meta.logoFile);
        meta.credits = tomlValue(content, "credits", meta.credits);
        String authors = tomlValue(content, "authors", null);
        if (authors != null) {
            for (String a : authors.split(",")) {
                if (!a.isBlank()) meta.authors.add(a.trim());
            }
        }
        Matcher desc = Pattern.compile("description\\s*=\\s*'''(.*?)'''", Pattern.DOTALL).matcher(content);
        if (desc.find()) {
            meta.description = desc.group(1).trim();
        } else {
            meta.description = tomlValue(content, "description", meta.description);
        }
        if ("${file.jarVersion}".equals(meta.version)) {
            meta.version = "1.0.0";
            ctx.todo(null, null, "metadata", "mods.toml 的 version 使用 ${file.jarVersion} 占位符，已回退为 1.0.0，请确认");
        }
    }

    private static String tomlValue(String content, String key, String fallback) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"").matcher(content);
        return m.find() ? m.group(1) : fallback;
    }

    // ---- 模板 ----

    String readTemplate(String name) {
        // 覆盖层版本沿模板目录链回退（自身 -> basedOn 基版本）
        for (Path dir : ctx.target().templateDirs) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 用 ModMeta + 目标版本信息填充 ${...} 占位符。 */
    String renderTemplate(String template) {
        ModMeta m = ctx.modMeta;
        var info = ctx.target().info;
        return template
                .replace("${modid}", m.modid)
                .replace("${name}", m.name)
                .replace("${version}", m.version)
                .replace("${description}", m.description == null ? "" : m.description)
                .replace("${authors}", m.authorsJoined())
                .replace("${url}", m.url == null ? "" : m.url)
                .replace("${logoFile}", m.logoFile == null ? "" : m.logoFile)
                .replace("${credits}", m.credits == null ? "" : m.credits)
                .replace("${group}", m.group)
                .replace("${mcVersion}", info.mcVersion == null ? "" : info.mcVersion)
                .replace("${forgeVersion}", info.forgeVersion == null ? "" : info.forgeVersion)
                .replace("${loaderVersionRange}", info.loaderVersionRange == null ? "" : info.loaderVersionRange)
                .replace("${mappingsChannel}", info.mappingsChannel == null ? "" : info.mappingsChannel)
                .replace("${javaVersion}", String.valueOf(info.javaVersion));
    }
}
