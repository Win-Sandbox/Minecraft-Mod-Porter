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

    public String authorsJoined() {
        return String.join(", ", authors);
    }
}
