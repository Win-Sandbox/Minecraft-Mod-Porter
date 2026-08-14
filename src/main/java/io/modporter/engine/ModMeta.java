package io.modporter.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组元数据的规范中间表示（IR）。
 * mcmod.info / mods.toml 都先解析成它，再按目标版本格式输出。
 */
public final class ModMeta {
    public String modid = "examplemod";
    public String name = "Example Mod";
    public String version = "1.0.0";
    public String description = "";
    public List<String> authors = new ArrayList<>();
    public String url = "";
    public String logoFile = "";
    public String credits = "";
    public String group = "com.example";       // 从 build.gradle 提取，用于生成新 build.gradle

    // ---- Fabric 专有：这些内容与 MC 版本无关，转换时必须原样保留 ----
    /** fabric.mod.json 原文（仅当源工程是 Fabric 时非 null），用于同格式转换时按字段打补丁而非重新生成。 */
    public String rawMetadataJson;
    /** entrypoints 对象的完整 JSON（如 {"main":["com.example.Mod"]}），模板占位符 ${entrypointsJson}。 */
    public String entrypointsJson = "{}";
    /** mixins 数组的完整 JSON（如 ["mod.mixins.json"]），模板占位符 ${mixinsJson}。 */
    public String mixinsJson = "[]";

    public String authorsJoined() {
        return String.join(", ", authors);
    }
}
