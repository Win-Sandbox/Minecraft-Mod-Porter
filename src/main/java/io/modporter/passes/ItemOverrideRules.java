package io.modporter.passes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 旧模型 overrides 与 1.21.4+ items range_dispatch 的严格子集转换 helper。
 *
 * 只处理：
 *  - predicate 仅含 custom_model_data / minecraft:custom_model_data
 *  - 阈值为单个非负 JSON number
 *  - override / entry 顺序严格递增
 *  - 目标模型为 minecraft:model
 *  - fallback 为调用方传入的 baseRef
 *
 * 不处理多 predicate、tints、loader、render_type、select、condition、重复阈值、
 * 负数、NaN/Infinity、跨文件 parent/override 循环检测等。
 */
public final class ItemOverrideRules {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern RESOURCE_PATH = Pattern.compile("[a-z0-9_./-]+");

    private static final Set<String> LEGACY_OVERRIDE_KEYS = Set.of("predicate", "model");
    private static final Set<String> PREDICATE_KEYS = Set.of("custom_model_data", "minecraft:custom_model_data");
    private static final Set<String> RANGE_DISPATCH_KEYS =
            Set.of("type", "property", "index", "scale", "entries", "fallback");
    private static final Set<String> RANGE_ENTRY_KEYS = Set.of("threshold", "model");
    private static final Set<String> PLAIN_MODEL_KEYS = Set.of("type", "model");

    private ItemOverrideRules() {
    }

    public static Result upgradeLegacyOverrides(JsonObject legacyModel, String namespace, String baseRef) {
        String nsError = validateNamespace(namespace);
        if (nsError != null) return Result.failure(nsError);

        String base = normalizeModelRef(baseRef);
        if (base == null) {
            return Result.failure("baseRef 不是合法模型 ID，或缺少可默认到 minecraft 的路径");
        }
        if (legacyModel == null) {
            return Result.failure("legacy model 不能为 null");
        }

        JsonElement overridesElement = legacyModel.get("overrides");
        if (overridesElement == null || !overridesElement.isJsonArray()) {
            return Result.failure("legacy model 缺少 overrides 数组");
        }
        JsonArray overrides = overridesElement.getAsJsonArray();
        if (overrides.size() == 0) {
            return Result.failure("legacy model overrides 为空，不属于本严格子集");
        }

        JsonArray entries = new JsonArray();
        BigDecimal previous = null;

        for (JsonElement element : overrides) {
            if (element == null || !element.isJsonObject()) {
                return Result.failure("overrides 中存在非对象条目");
            }
            JsonObject override = element.getAsJsonObject();
            if (!override.keySet().equals(LEGACY_OVERRIDE_KEYS)) {
                return Result.failure("override 条目字段必须恰好为 predicate + model");
            }

            JsonElement predicateElement = override.get("predicate");
            if (predicateElement == null || !predicateElement.isJsonObject()) {
                return Result.failure("override predicate 必须是对象");
            }
            JsonObject predicate = predicateElement.getAsJsonObject();
            if (predicate.size() != 1) {
                return Result.failure("predicate 必须只含一个条件字段");
            }
            String predicateKey = predicate.keySet().iterator().next();
            if (!PREDICATE_KEYS.contains(predicateKey)) {
                return Result.failure("predicate 只支持 custom_model_data，不支持 " + predicateKey);
            }

            JsonElement thresholdElement = predicate.get(predicateKey);
            BigDecimal threshold = asFiniteNumber(thresholdElement);
            if (threshold == null) {
                return Result.failure("custom_model_data 阈值必须是单个有限 JSON number");
            }
            if (threshold.compareTo(BigDecimal.ZERO) < 0) {
                return Result.failure("custom_model_data 阈值必须非负");
            }
            if (previous != null && threshold.compareTo(previous) <= 0) {
                return Result.failure("custom_model_data 阈值必须严格递增，不能重复或倒序");
            }
            previous = threshold;

            JsonElement modelElement = override.get("model");
            String modelRef = normalizeModelRef(asString(modelElement));
            if (modelRef == null) {
                return Result.failure("override model 必须是合法模型 ID");
            }
            if (modelRef.equals(base)) {
                return Result.failure("override model 不能等于 baseRef，避免生成自循环引用");
            }

            JsonObject entryModel = plainModel(modelRef);
            JsonObject entry = new JsonObject();
            entry.add("threshold", thresholdElement.deepCopy());
            entry.add("model", entryModel);
            entries.add(entry);
        }

        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        dispatch.addProperty("scale", 1.0);
        dispatch.add("entries", entries);
        dispatch.add("fallback", plainModel(base));

        JsonObject result = new JsonObject();
        result.add("model", dispatch);
        return Result.success(result);
    }

    private static JsonObject plainModel(String modelRef) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelRef);
        return model;
    }

    public static Result downgradeItemDefinition(JsonObject itemDefinition, String namespace, String baseRef) {
        String nsError = validateNamespace(namespace);
        if (nsError != null) return Result.failure(nsError);

        String base = normalizeModelRef(baseRef);
        if (base == null) {
            return Result.failure("baseRef 不是合法模型 ID，或缺少可默认到 minecraft 的路径");
        }
        if (itemDefinition == null) {
            return Result.failure("item definition 不能为 null");
        }
        if (itemDefinition.size() != 1 || !itemDefinition.has("model")) {
            return Result.failure("items 顶层必须只含 model 字段");
        }

        JsonElement dispatchElement = itemDefinition.get("model");
        if (dispatchElement == null || !dispatchElement.isJsonObject()) {
            return Result.failure("items model 必须是对象");
        }
        JsonObject dispatch = dispatchElement.getAsJsonObject();
        if (!dispatch.keySet().equals(RANGE_DISPATCH_KEYS)) {
            return Result.failure("range_dispatch 字段必须恰好为 type/property/index/scale/entries/fallback");
        }

        if (!"minecraft:range_dispatch".equals(asString(dispatch.get("type")))) {
            return Result.failure("items model type 必须是 minecraft:range_dispatch");
        }
        if (!"minecraft:custom_model_data".equals(asString(dispatch.get("property")))) {
            return Result.failure("range_dispatch property 必须是 minecraft:custom_model_data");
        }

        BigDecimal index = integralNumber(dispatch.get("index"));
        if (index == null || index.compareTo(BigDecimal.ZERO) != 0) {
            return Result.failure("range_dispatch index 必须是整数 0");
        }
        BigDecimal scale = asFiniteNumber(dispatch.get("scale"));
        if (scale == null || scale.compareTo(BigDecimal.ONE) != 0) {
            return Result.failure("range_dispatch scale 必须是 1");
        }

        JsonElement fallbackElement = dispatch.get("fallback");
        if (fallbackElement == null || !fallbackElement.isJsonObject()) {
            return Result.failure("range_dispatch fallback 必须是对象");
        }
        JsonObject fallback = fallbackElement.getAsJsonObject();
        if (!fallback.keySet().equals(PLAIN_MODEL_KEYS)) {
            return Result.failure("fallback 字段必须恰好为 type + model");
        }
        if (!"minecraft:model".equals(asString(fallback.get("type")))) {
            return Result.failure("fallback type 必须是 minecraft:model");
        }
        String fallbackRef = normalizeModelRef(asString(fallback.get("model")));
        if (fallbackRef == null) {
            return Result.failure("fallback model 必须是合法模型 ID");
        }
        if (!fallbackRef.equals(base)) {
            return Result.failure("fallback model 必须与传入 baseRef 一致");
        }

        JsonElement entriesElement = dispatch.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            return Result.failure("range_dispatch entries 必须是数组");
        }
        JsonArray entries = entriesElement.getAsJsonArray();
        if (entries.size() == 0) {
            return Result.failure("range_dispatch entries 为空，不属于本严格子集");
        }

        JsonArray overrides = new JsonArray();
        BigDecimal previous = null;

        for (JsonElement element : entries) {
            if (element == null || !element.isJsonObject()) {
                return Result.failure("entries 中存在非对象条目");
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.keySet().equals(RANGE_ENTRY_KEYS)) {
                return Result.failure("range_dispatch entry 字段必须恰好为 threshold + model");
            }

            JsonElement thresholdElement = entry.get("threshold");
            BigDecimal threshold = asFiniteNumber(thresholdElement);
            if (threshold == null) {
                return Result.failure("range_dispatch threshold 必须是单个有限 JSON number");
            }
            if (threshold.compareTo(BigDecimal.ZERO) < 0) {
                return Result.failure("range_dispatch threshold 必须非负");
            }
            if (previous != null && threshold.compareTo(previous) <= 0) {
                return Result.failure("range_dispatch threshold 必须严格递增，不能重复或倒序");
            }
            previous = threshold;

            JsonElement modelElement = entry.get("model");
            if (modelElement == null || !modelElement.isJsonObject()) {
                return Result.failure("range_dispatch entry model 必须是对象");
            }
            JsonObject entryModel = modelElement.getAsJsonObject();
            if (!entryModel.keySet().equals(PLAIN_MODEL_KEYS)) {
                return Result.failure("range_dispatch entry model 字段必须恰好为 type + model");
            }
            if (!"minecraft:model".equals(asString(entryModel.get("type")))) {
                return Result.failure("range_dispatch entry model type 必须是 minecraft:model");
            }
            String modelRef = normalizeModelRef(asString(entryModel.get("model")));
            if (modelRef == null) {
                return Result.failure("range_dispatch entry model 必须是合法模型 ID");
            }
            if (modelRef.equals(base)) {
                return Result.failure("entry model 不能等于 baseRef，避免反向桥接自循环");
            }

            JsonObject predicate = new JsonObject();
            predicate.add("custom_model_data", thresholdElement.deepCopy());
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", modelRef);
            overrides.add(override);
        }

        JsonObject legacy = new JsonObject();
        legacy.addProperty("parent", base);
        legacy.add("overrides", overrides);
        return Result.success(legacy);
    }

    private static String validateNamespace(String namespace) {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            return "namespace 必须是合法小写资源命名空间";
        }
        return null;
    }

    private static String asString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) return null;
        String value = primitive.getAsString();
        return value == null || value.isEmpty() ? null : value;
    }

    private static BigDecimal asFiniteNumber(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) return null;
        try {
            return primitive.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return null;
        }
    }

    private static BigDecimal integralNumber(JsonElement element) {
        BigDecimal value = asFiniteNumber(element);
        if (value == null) return null;
        try {
            BigDecimal normalized = value.stripTrailingZeros();
            return normalized.scale() <= 0 ? value : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static String normalizeModelRef(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String namespace;
        String path;
        int colon = raw.indexOf(':');
        if (colon < 0) {
            namespace = "minecraft";
            path = raw;
        } else if (colon == raw.length() - 1 || raw.indexOf(':', colon + 1) >= 0) {
            return null;
        } else {
            namespace = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        }
        if (!NAMESPACE.matcher(namespace).matches() || !RESOURCE_PATH.matcher(path).matches()) {
            return null;
        }
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")
                || path.contains("/./") || path.contains("/../") || path.equals(".") || path.equals("..")) {
            return null;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return null;
        }
        return namespace + ":" + path;
    }

    public static final class Result {
        private final JsonObject value;
        private final String failure;

        private Result(JsonObject value, String failure) {
            this.value = value;
            this.failure = failure;
        }

        public boolean isSuccess() {
            return value != null;
        }

        public JsonObject value() {
            return value;
        }

        public String failure() {
            return failure;
        }

        static Result success(JsonObject value) {
            return new Result(value, null);
        }

        static Result failure(String reason) {
            return new Result(null, reason);
        }
    }
}
