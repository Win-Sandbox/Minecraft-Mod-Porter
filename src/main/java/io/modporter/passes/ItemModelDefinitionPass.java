package io.modporter.passes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 新旧客户端物品模型定义（assets/&lt;ns&gt;/items/*.json，1.21.4+）的保守迁移。
 *
 * 升级（源不支持 items 定义 → 目标支持）：
 *  - 识别 assets/&lt;ns&gt;/models/item/&lt;path&gt;.json（允许 src/main/resources 工程前缀，按完整路径段匹配），
 *    生成同前缀的 assets/&lt;ns&gt;/items/&lt;path&gt;.json：
 *    { "model": { "type": "minecraft:model", "model": "&lt;ns&gt;:&lt;itemPath&gt;" } }
 *  - 原模型文件全部保留；目标 items 文件已存在（精确路径）时不覆盖（幂等/冲突 TODO）。
 *
 * 降级（源支持 → 目标不支持）：
 *  - 仅当 items 定义是纯 minecraft:model（顶层仅 model 字段、model 内仅 type+model，无 tints/when 等）
 *    且引用的模型在输出中本地存在且为合法 JSON 对象、parent 链无自/间接循环时，
 *    生成必要 legacy 桥接模型（parent 引用）；已存在同名模型不覆盖。
 *  - 复杂 range_dispatch/select/condition/tints、未知 schema、引用缺失/循环：原样保留 + TODO，
 *    绝不删除 items 目录。
 *
 * 版本能力直接读取 VersionInfo.itemModelDefinitions（主代理已添加 boolean 字段）。
 */
public final class ItemModelDefinitionPass {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");

    private static final String CATEGORY = "item-model-definition";
    /** parent 链循环检测的最大深度。 */
    private static final int MAX_PARENT_DEPTH = 32;

    private final PortContext ctx;

    public ItemModelDefinitionPass(PortContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 主代理在 DefaultPortEngine 写出前调用：返回包含原有输出与（可能）新增 items 定义 /
     * legacy 桥接模型的新的输出列表。本 Pass 不修改任何传入的 OutputFile。
     */
    public List<OutputFile> transform(List<OutputFile> outputs) {
        if (outputs == null) return new ArrayList<>();
        List<OutputFile> result = new ArrayList<>(outputs);
        if (result.isEmpty()) return result;

        boolean sourceSupports = ctx.source().info.itemModelDefinitions;
        boolean targetSupports = ctx.target().info.itemModelDefinitions;
        if (sourceSupports == targetSupports) return result;

        // 精确路径（仅统一反斜杠为斜杠，不改大小写、不丢工程前缀）-> OutputFile。
        Map<String, OutputFile> byPath = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (OutputFile o : result) {
            if (o == null || o.relativePath == null) continue;
            String key = normalize(o.relativePath);
            if (byPath.containsKey(key)) {
                // 同路径重复输出：不取最后一个，整组保留 + TODO，需人工消歧。
                duplicates.add(key);
            } else {
                byPath.put(key, o);
            }
        }
        for (String dup : duplicates) {
            ctx.todo(dup, null, CATEGORY,
                    "输出列表中存在多条同路径文件（" + dup + "），无法安全判定保留哪份，"
                            + "本 Pass 对其全部跳过并原样保留，需人工消歧");
        }

        scanJavaTintReminders(result);

        if (!sourceSupports && targetSupports) {
            upgrade(result, byPath, duplicates);
        } else {
            downgrade(result, byPath, duplicates);
        }
        return result;
    }

    // ---------------------------------------------------------------- 升级

    private void upgrade(List<OutputFile> result, Map<String, OutputFile> byPath, Set<String> duplicates) {
        for (OutputFile o : new ArrayList<>(result)) { // 快照：生成过程中会 add
            if (o == null || o.relativePath == null) continue;
            String key = normalize(o.relativePath);
            if (duplicates.contains(key)) continue;
            if (unsafePath(key)) {
                ctx.todo(o.relativePath, null, CATEGORY, "路径含空段/..或绝对路径，拒绝生成任何派生文件");
                continue;
            } // 重复组已整组 TODO
            Location loc = locate(key, "models/item");
            if (loc == null) continue;

            if (!validNamespace(loc.namespace) || !validResourcePath(loc.itemPath) || unsafePath(loc.itemPath)) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "legacy 物品模型路径不是安全/合法的资源位置，无法生成 items 定义，需人工迁移");
                continue;
            }

            JsonElement root = parse(new String(o.content, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "legacy 物品模型 JSON 无效或非对象，无法生成 items 定义，需人工迁移");
                continue;
            }
            JsonObject legacy = root.getAsJsonObject();
            if (legacy.has("loader") || legacy.has("render_type")) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "legacy 物品模型含 loader/render_type 等自定义加载器或渲染特性，"
                                + "items 定义已生成（model 指向原模型），但这些特性需人工核对");
            }
            ItemOverrideRules.Result overrideRules =
                    ItemOverrideRules.upgradeLegacyOverrides(legacy, loc.namespace,
                            loc.namespace + ":item/" + loc.itemPath);
            if (overrideRules.isSuccess()) {
                // 严格子集：旧 overrides 的 custom_model_data 阈值可等价转换为新 range_dispatch。
                if (legacy.has("tints") || hasTintIndex(legacy)) {
                    ctx.todo(o.relativePath, null, CATEGORY,
                            "legacy 物品模型含 tints/tintindex，range_dispatch 仅覆盖 custom_model_data 阈值，"
                                    + "颜色逻辑需人工核对");
                }
            } else if (legacy.has("overrides") || legacy.has("tints") || hasTintIndex(legacy)) {
                String reason = legacy.has("overrides")
                        ? "legacy overrides 不属于可自动转换的 custom_model_data 严格子集（"
                        + overrideRules.failure() + "），overrides 需人工迁移"
                        : "legacy 物品模型含 tints/tintindex，需人工核对新版物品模型定义中的承载方式";
                if (legacy.has("overrides") && (legacy.has("tints") || hasTintIndex(legacy))) {
                    reason += "；且含 tints/tintindex，需一并人工核对";
                }
                ctx.todo(o.relativePath, null, CATEGORY, reason + "；items 定义已生成（model 指向原模型）");
            }

            // 生成的 items 定义必须与源模型保持相同工程前缀（如 src/main/resources/）。
            String itemsRel = loc.prefix + "assets/" + loc.namespace + "/items/" + loc.itemPath + ".json";
            String itemsKey = normalize(itemsRel);
            JsonObject generatedDef = overrideRules.isSuccess()
                    ? overrideRules.value()
                    : buildItemsDefinition(loc.namespace, loc.itemPath);
            String generatedContent = render(generatedDef);
            OutputFile existing = byPath.get(itemsKey);
            if (existing != null) {
                if (!sameContent(existing, generatedContent)) {
                    ctx.todo(existing.relativePath, null, CATEGORY,
                            "目标 items 定义 " + itemsRel + " 已存在且与可生成内容不同，保留既有文件，需人工合并");
                } else {
                    ctx.info(existing.relativePath, null, CATEGORY, "items 定义已存在且内容一致（幂等跳过）");
                }
                continue;
            }
            OutputFile generated = new OutputFile(itemsRel,
                    generatedContent.getBytes(StandardCharsets.UTF_8));
            result.add(generated);
            byPath.put(itemsKey, generated);
            ctx.info(o.relativePath, null, CATEGORY,
                    "已生成 items 定义 " + itemsRel + "（"
                            + (overrideRules.isSuccess()
                            ? "range_dispatch 严格子集，fallback 指向 legacy 模型 "
                            : "model 指向保留的 legacy 模型 ")
                            + loc.namespace + ":item/" + loc.itemPath + "）");
        }
    }

    private static boolean hasTintIndex(JsonElement value) {
        if (value.isJsonObject()) {
            if (value.getAsJsonObject().has("tintindex")) return true;
            for (var entry : value.getAsJsonObject().entrySet()) if (hasTintIndex(entry.getValue())) return true;
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasTintIndex(child)) return true;
        }
        return false;
    }

    private JsonObject buildItemsDefinition(String namespace, String itemPath) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", namespace + ":item/" + itemPath);
        JsonObject def = new JsonObject();
        def.add("model", model);
        return def;
    }

    // ---------------------------------------------------------------- 降级

    private void downgrade(List<OutputFile> result, Map<String, OutputFile> byPath, Set<String> duplicates) {
        for (OutputFile o : new ArrayList<>(result)) { // 快照：生成过程中会 add
            if (o == null || o.relativePath == null) continue;
            String key = normalize(o.relativePath);
            if (duplicates.contains(key)) continue;
            if (unsafePath(key)) {
                ctx.todo(o.relativePath, null, CATEGORY, "路径含空段/..或绝对路径，拒绝生成任何派生文件");
                continue;
            }
            Location loc = locate(key, "items");
            if (loc == null) continue;

            if (!validNamespace(loc.namespace) || !validResourcePath(loc.itemPath) || unsafePath(loc.itemPath)) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义路径不安全/不合法，旧版本需人工迁移；items 文件原样保留");
                continue;
            }

            JsonElement root = parse(new String(o.content, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义 JSON 无效或非对象，需人工迁移；items 文件原样保留");
                continue;
            }
            JsonObject def = root.getAsJsonObject();
            // 顶层只接受 {"model": {...}}，出现任何其他/多余顶层字段即视为未知 schema。
            if (def.size() != 1 || !def.has("model") || !def.get("model").isJsonObject()) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义顶层是未知 schema（仅支持单顶层 model 字段），"
                                + "需人工迁移；items 文件原样保留");
                continue;
            }
            JsonObject model = def.getAsJsonObject("model");
            boolean pureModel = "minecraft:model".equals(stringOrNull(model, "type"))
                    && stringOrNull(model, "model") != null
                    && model.size() == 2;
            if (!pureModel) {
                // 严格子集：items 的 range_dispatch(custom_model_data) 可降级为 legacy overrides 桥接。
                String rangeBaseRef = rangeDispatchFallbackRef(def, loc.namespace);
                if (rangeBaseRef == null) {
                    ctx.todo(o.relativePath, null, CATEGORY,
                            "range_dispatch fallback 不是可验证的纯 minecraft:model 本地引用，items 文件原样保留");
                    continue;
                }
                ItemOverrideRules.Result bridged = ItemOverrideRules.downgradeItemDefinition(
                        def, loc.namespace, rangeBaseRef);
                if (bridged.isSuccess()) {
                    String bridgeRel = loc.prefix + "assets/" + loc.namespace
                            + "/models/item/" + loc.itemPath + ".json";
                    String bridgeKey = normalize(bridgeRel);
                    String bridgeId = loc.namespace + ":item/" + loc.itemPath;

                    if (duplicates.contains(bridgeKey)) {
                        ctx.todo(o.relativePath, null, CATEGORY,
                                "range_dispatch 降级目标路径 " + bridgeRel
                                        + " 在输出中存在多条同路径文件，无法安全写入桥接模型，需人工迁移；items 文件原样保留");
                        continue;
                    }

                    // 原子化安全闭合：fallback 与每个 entry 引用都必须复用 pureModel 分支同一套
                    // byPath/duplicates/合法 JSON/parentChainReaches 检查；任意一个失败则整条降级
                    // 放弃，不生成任何文件，items 文件原样保留 + TODO（绝不部分写入）。
                    String unsafeReason = referenceUnsafe(rangeBaseRef, bridgeId, bridgeKey,
                            loc.prefix, byPath, duplicates, "range_dispatch fallback");
                    if (unsafeReason == null) {
                        JsonArray overridesArr = bridged.value().getAsJsonArray("overrides");
                        for (JsonElement overrideEl : overridesArr) {
                            String entryRef = overrideEl.getAsJsonObject().get("model").getAsString();
                            unsafeReason = referenceUnsafe(entryRef, bridgeId, bridgeKey,
                                    loc.prefix, byPath, duplicates, "range_dispatch entry");
                            if (unsafeReason != null) break;
                        }
                    }
                    if (unsafeReason != null) {
                        ctx.todo(o.relativePath, null, CATEGORY,
                                "range_dispatch 降级的 " + unsafeReason + "，无法确认安全降级为 legacy 桥接模型，"
                                        + "需人工迁移；items 文件原样保留（未生成任何文件）");
                        continue;
                    }

                    OutputFile existingBridge = byPath.get(bridgeKey);
                    String bridgeContent = render(bridged.value());
                    if (existingBridge != null) {
                        if (!sameContent(existingBridge, bridgeContent)) {
                            ctx.todo(existingBridge.relativePath, null, CATEGORY,
                                    "legacy 桥接模型 " + bridgeRel + " 已存在且与可生成内容不同，保留既有文件，需人工合并");
                        } else {
                            ctx.info(existingBridge.relativePath, null, CATEGORY,
                                    "range_dispatch 降级桥接已存在且内容一致（幂等跳过）");
                        }
                        continue;
                    }
                    OutputFile generated = new OutputFile(bridgeRel, bridgeContent.getBytes(StandardCharsets.UTF_8));
                    result.add(generated);
                    byPath.put(bridgeKey, generated);
                    ctx.info(o.relativePath, null, CATEGORY,
                            "已将 range_dispatch 严格子集降级为 legacy 桥接模型 " + bridgeRel
                                    + "（parent 指向 " + rangeBaseRef + "，含 custom_model_data overrides）；"
                                    + "items 文件原样保留作为人工参考");
                    continue;
                }
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义是复杂模型类型（range_dispatch/select/condition/tints 或未知字段，"
                                + "或超出可自动降级子集：" + bridged.failure() + "），"
                                + "无法自动降级为 legacy 物品模型，需人工迁移；items 文件原样保留");
                continue;
            }
            String modelRef = model.get("model").getAsString();
            // 无命名空间的引用默认 minecraft，而不是 items 文件自己的命名空间。
            String[] ref = splitModelRef(modelRef, "minecraft");
            if (ref == null || !validNamespace(ref[0]) || !validResourcePath(ref[1]) || unsafePath(ref[1])) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义引用的模型 id 不安全/不合法（" + modelRef + "），需人工迁移；items 文件原样保留");
                continue;
            }

            // 引用的模型必须在输出中本地存在、且是合法 JSON 对象（仅“存在”不够）。
            String refModelRel = loc.prefix + "assets/" + ref[0] + "/models/" + ref[1] + ".json";
            String refModelKey = normalize(refModelRel);
            OutputFile refModelFile = byPath.get(refModelKey);
            if (refModelFile == null || duplicates.contains(refModelKey)) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义引用的模型 " + modelRef + " 不在输出中本地可用，"
                                + "无法生成 legacy 桥接模型，需人工迁移；items 文件原样保留");
                continue;
            }
            JsonElement refRoot = parse(new String(refModelFile.content, StandardCharsets.UTF_8));
            if (refRoot == null || !refRoot.isJsonObject()) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义引用的模型文件 " + refModelRel + " 不是合法 JSON 对象，"
                                + "无法生成 legacy 桥接模型，需人工迁移；items 文件原样保留");
                continue;
            }

            // 桥接模型：assets/<ns>/models/item/<itemsPath>.json，parent 指向被引用模型。
            String bridgeRel = loc.prefix + "assets/" + loc.namespace + "/models/item/" + loc.itemPath + ".json";
            String bridgeKey = normalize(bridgeRel);
            if (bridgeKey.equals(refModelKey)) {
                if (parentChainReaches(refModelFile, modelRef, "", loc.prefix, byPath, duplicates)) {
                    ctx.todo(o.relativePath, null, CATEGORY, "已在旧路径的模型含循环/未知父引用或自定义格式，保留但不能确认降级兼容");
                    continue;
                }
                // 引用的模型本身已位于 legacy 位置（如 items/sword.json -> ns:item/sword），
                // legacy 客户端直接加载它即可，无需再生成桥接。
                ctx.info(o.relativePath, null, CATEGORY,
                        "items 定义引用的模型 " + modelRef + " 已位于 legacy 模型路径 " + bridgeRel
                                + "，无需生成桥接；items 文件原样保留作为人工参考（旧客户端会忽略）");
                continue;
            }

            // parent 链自循环/间接循环检测：沿被引用模型的 parent 链走，若回到桥接位置则拒绝。
            String bridgeId = loc.namespace + ":item/" + loc.itemPath;
            if (parentChainReaches(refModelFile, modelRef, bridgeId, loc.prefix, byPath, duplicates)) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "items 定义引用的模型 " + modelRef + " 的 parent 链循环/缺失/自定义或无法确认（桥接 "
                                + bridgeId + "），需人工迁移；items 文件原样保留");
                continue;
            }

            String bridgeContent = render(buildLegacyBridge(modelRef));
            OutputFile existing = byPath.get(bridgeKey);
            if (existing != null) {
                if (!sameContent(existing, bridgeContent)) {
                    ctx.todo(existing.relativePath, null, CATEGORY,
                            "legacy 桥接模型 " + bridgeRel + " 已存在且与可生成内容不同，"
                                    + "保留既有文件，需人工合并");
                } else {
                    ctx.info(existing.relativePath, null, CATEGORY, "legacy 桥接模型已存在且内容一致（幂等跳过）");
                }
                continue;
            }
            OutputFile generated = new OutputFile(bridgeRel, bridgeContent.getBytes(StandardCharsets.UTF_8));
            result.add(generated);
            byPath.put(bridgeKey, generated);
            ctx.info(o.relativePath, null, CATEGORY,
                    "已生成 legacy 桥接模型 " + bridgeRel + "（parent 指向 " + modelRef + "）；"
                            + "items 定义原样保留作为人工参考（旧客户端会忽略）");
        }
    }

    private String rangeDispatchFallbackRef(JsonObject def, String defaultNamespace) {
        if (def == null || !def.has("model") || !def.get("model").isJsonObject()) return null;
        JsonObject model = def.getAsJsonObject("model");
        if (!"minecraft:range_dispatch".equals(stringOrNull(model, "type"))) return null;
        JsonElement fallback = model.get("fallback");
        if (fallback == null || !fallback.isJsonObject()) return null;
        JsonObject f = fallback.getAsJsonObject();
        if (f.size() != 2 || !"minecraft:model".equals(stringOrNull(f, "type"))) return null;
        String ref = stringOrNull(f, "model");
        String[] parts = splitModelRef(ref, defaultNamespace);
        return parts == null || !validNamespace(parts[0]) || !validResourcePath(parts[1])
                || unsafePath(parts[1]) ? null : parts[0] + ":" + parts[1];
    }

    /**
     * 对 range_dispatch 降级涉及的单个模型引用（fallback 或某个 entry）复用与 pureModel 分支完全
     * 相同的安全检查：命名空间/路径合法、本地 byPath 存在（且不在 duplicates 中）、内容是合法 JSON
     * 对象、parent 链不自循环/不指向桥接自身/不缺失非内置基础模型。
     * 返回 null 表示安全；否则返回供 TODO 使用的失败原因（不含调用方前缀标签）。
     */
    private String referenceUnsafe(String modelRef, String bridgeId, String bridgeKey, String prefix,
                                    Map<String, OutputFile> byPath, Set<String> duplicates, String label) {
        String[] ref = splitModelRef(modelRef, "minecraft");
        if (ref == null || !validNamespace(ref[0]) || !validResourcePath(ref[1]) || unsafePath(ref[1])) {
            return label + " 引用的模型 id 不安全/不合法（" + modelRef + "）";
        }
        String refModelRel = prefix + "assets/" + ref[0] + "/models/" + ref[1] + ".json";
        String refModelKey = normalize(refModelRel);
        if (refModelKey.equals(bridgeKey)) {
            return label + " 引用 " + modelRef + " 与待生成的桥接模型路径相同（自循环）";
        }
        OutputFile refModelFile = byPath.get(refModelKey);
        if (refModelFile == null || duplicates.contains(refModelKey)) {
            return label + " 引用的模型 " + modelRef + " 不在输出中本地可用";
        }
        JsonElement refRoot = parse(new String(refModelFile.content, StandardCharsets.UTF_8));
        if (refRoot == null || !refRoot.isJsonObject()) {
            return label + " 引用的模型文件 " + refModelRel + " 不是合法 JSON 对象";
        }
        if (parentChainReaches(refModelFile, modelRef, bridgeId, prefix, byPath, duplicates)) {
            return label + " 引用的模型 " + modelRef + " 的 parent 链循环/缺失/自定义或无法确认（桥接 " + bridgeId + "）";
        }
        return null;
    }

    /** Reject cyclic, ambiguous or malformed local parent chains before adding a bridge. */
    private boolean parentChainReaches(OutputFile start, String startRef, String bridgeId,
                                       String prefix, Map<String, OutputFile> byPath, Set<String> duplicates) {
        Set<String> visited = new HashSet<>();
        String current = startRef;
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            if (current == null) return false;
            String[] id = splitModelRef(current, "minecraft");
            if (id == null || !validNamespace(id[0]) || !validResourcePath(id[1]) || unsafePath(id[1])) return true;
            String canonical = id[0] + ":" + id[1];
            if (canonical.equals(bridgeId) || !visited.add(canonical)) return true;
            String fileKey = normalize(prefix + "assets/" + id[0] + "/models/" + id[1] + ".json");
            if (duplicates.contains(fileKey)) return true;
            OutputFile file = byPath.get(fileKey);
            if (file == null) {
                // Built-in base models can terminate a local chain; missing mod models cannot.
                return !Set.of("minecraft:item/generated", "minecraft:item/handheld", "minecraft:builtin/generated").contains(canonical);
            }
            JsonElement root = parse(new String(file.content, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) return true;
            JsonObject model = root.getAsJsonObject();
            if (model.has("loader") || model.has("render_type")) return true;
            if (model.has("parent") && stringOrNull(model, "parent") == null) return true;
            current = stringOrNull(model, "parent");
        }
        return true; // Deep/unknown chain is not proof of safety.
    }

    private JsonObject buildLegacyBridge(String modelRef) {
        JsonObject bridge = new JsonObject();
        bridge.addProperty("parent", modelRef);
        return bridge;
    }

    // ---------------------------------------------------------------- Java 提醒

    private void scanJavaTintReminders(List<OutputFile> outputs) {
        for (OutputFile o : outputs) {
            if (o == null || o.relativePath == null || !o.relativePath.endsWith(".java")) continue;
            String text = new String(o.content, StandardCharsets.UTF_8);
            if (text.contains("ColorProvider") || text.contains("ColorResolver")
                    || text.contains("ItemColorProvider") || text.contains("tintIndex")) {
                ctx.todo(o.relativePath, null, CATEGORY,
                        "源码引用了 ColorProvider/tintIndex 等颜色逻辑；物品模型 tint 在 items 定义格式下"
                                + "的承载方式与旧版不同，本 Pass 不推断其迁移，需人工核对");
            }
        }
    }

    // ---------------------------------------------------------------- 路径定位

    /** 一个被识别的物品模型 / items 定义的位置：工程前缀（保留，含末尾斜杠）、命名空间、物品路径。 */
    private static final class Location {
        final String prefix;     // 如 "src/main/resources/" 或 ""
        final String namespace;
        final String itemPath;

        Location(String prefix, String namespace, String itemPath) {
            this.prefix = prefix;
            this.namespace = namespace;
            this.itemPath = itemPath;
        }
    }

    /**
     * 按完整路径段匹配 assets/&lt;ns&gt;/models/item/... 或 assets/&lt;ns&gt;/items/...
     * （"assets" 必须是独立段，myassets/my-assets 不算；kind 传 "models/item" 或 "items"）。
     */
    private static Location locate(String path, String kind) {
        if (!path.endsWith(".json")) return null;
        String[] parts = path.substring(0, path.length() - 5).split("/", -1);
        int firstAssets = -1;
        for (int i = 0; i < parts.length; i++) if (parts[i].equals("assets")) { firstAssets = i; break; }
        if (firstAssets < 0) return null;
        if (kind.equals("models/item")) {
            for (int i = firstAssets; i == firstAssets && i + 4 < parts.length; i++) {
                if (parts[i].equals("assets") && parts[i + 2].equals("models")
                        && parts[i + 3].equals("item")) {
                    return new Location(prefixOf(parts, i), parts[i + 1], joinFrom(parts, i + 4));
                }
            }
        } else {
            for (int i = firstAssets; i == firstAssets && i + 3 < parts.length; i++) {
                if (parts[i].equals("assets") && parts[i + 2].equals("items")) {
                    return new Location(prefixOf(parts, i), parts[i + 1], joinFrom(parts, i + 3));
                }
            }
        }
        return null;
    }

    private static String prefixOf(String[] parts, int assetsIdx) {
        if (assetsIdx == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < assetsIdx; i++) {
            if (sb.length() > 0) sb.append('/');
            sb.append(parts[i]);
        }
        return sb.append('/').toString();
    }

    private static String joinFrom(String[] parts, int from) {
        return String.join("/", java.util.Arrays.copyOfRange(parts, from, parts.length));
    }

    // ---------------------------------------------------------------- 工具

    private static JsonElement parse(String content) {
        try {
            return JsonParser.parseString(content);
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static String render(JsonObject o) {
        return GSON.toJson(o);
    }

    /** 仅统一反斜杠为斜杠；不改大小写、不丢工程前缀。 */
    private static String normalize(String p) {
        return p.replace('\\', '/');
    }

    private static boolean validNamespace(String ns) {
        return ns != null && !ns.equals(".") && !ns.equals("..") && NAMESPACE.matcher(ns).matches();
    }

    private static boolean validResourcePath(String p) {
        return p != null && RESOURCE_PATH.matcher(p).matches();
    }

    /** 防御 ..、空段、前导/尾随斜杠。 */
    private static boolean unsafePath(String p) {
        if (p.isEmpty() || p.startsWith("/") || p.endsWith("/")) return true;
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return true;
        }
        return false;
    }

    /** 拆分 "ns:path"（缺命名空间按 defaultNs），不合法返回 null。 */
    private static String[] splitModelRef(String ref, String defaultNs) {
        if (ref == null) return null;
        int c = ref.indexOf(':');
        if (c < 0) return new String[]{defaultNs, ref};
        return new String[]{ref.substring(0, c), ref.substring(c + 1)};
    }

    private static String stringOrNull(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isString()
                ? o.get(key).getAsString() : null;
    }

    private static boolean sameContent(OutputFile o, String content) {
        JsonElement actual = parse(new String(o.content, StandardCharsets.UTF_8));
        JsonElement expected = parse(content);
        return actual != null && actual.equals(expected);
    }
}
