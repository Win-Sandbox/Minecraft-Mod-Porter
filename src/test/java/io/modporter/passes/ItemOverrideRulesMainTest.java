package io.modporter.passes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

/**
 * ItemOverrideRules 的 main 式回归测试（无 JUnit）。
 *
 * 只做静态源码级回归说明，不在本任务中执行。
 * 如主代理后续要运行，需先编译（本子任务禁止）。
 */
public final class ItemOverrideRulesMainTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testUpgradeBasic();
        testUpgradeDefaultMinecraftNamespace();
        testUpgradeNamespacedPredicate();
        testUpgradeAscendingThresholds();
        testUpgradePreservesThresholdLiteral();
        testUpgradeRejectsDescendingThresholds();
        testUpgradeRejectsDuplicateThresholds();
        testUpgradeRejectsNegativeThreshold();
        testUpgradeRejectsMultiplePredicateKeys();
        testUpgradeRejectsOtherPredicate();
        testUpgradeRejectsOverrideModelEqualsBase();
        testUpgradeRejectsMissingOverrides();
        testUpgradeRejectsNonObjectOverride();
        testUpgradeRejectsInvalidBaseRef();
        testDowngradeBasic();
        testDowngradeDefaultMinecraftNamespace();
        testDowngradeRejectsWrongProperty();
        testDowngradeRejectsWrongIndex();
        testDowngradeRejectsWrongScale();
        testDowngradeRejectsExtraTopLevelField();
        testDowngradeRejectsExtraRangeDispatchField();
        testDowngradeRejectsMissingFallback();
        testDowngradeRejectsFallbackMismatch();
        testDowngradeRejectsEntryModelEqualsBase();
        testDowngradeRejectsDescendingThresholds();
        testDowngradeRejectsDuplicateThresholds();
        testDowngradeRejectsNonPlainEntryModel();
        testDowngradeRejectsUnknownEntryField();
        testDowngradeRejectsEmptyEntries();
        testDowngradePreservesThresholdLiteral();
        testDowngradeOutputsParentBridge();
        testBothDirectionsPreserveBaseModel();
        reportDone();
    }

    private static void testUpgradeBasic() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/foo"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(result.isSuccess(), "upgrade basic: 应成功");
        if (result.isSuccess()) {
            JsonObject model = result.value().getAsJsonObject("model");
            check("minecraft:range_dispatch".equals(string(model, "type")), "upgrade basic: type");
            check("minecraft:custom_model_data".equals(string(model, "property")), "upgrade basic: property");
            check(number(model, "index").equals("0"), "upgrade basic: index");
            check(number(model, "scale").equals("1.0"), "upgrade basic: scale");
            check(model.getAsJsonArray("entries").size() == 1, "upgrade basic: entries 数量");
            JsonObject entry = model.getAsJsonArray("entries").get(0).getAsJsonObject();
            check(number(entry, "threshold").equals("1"), "upgrade basic: threshold");
            check("mymod:item/foo".equals(string(entry.getAsJsonObject("model"), "model")), "upgrade basic: entry model");
            check("mymod:item/base".equals(string(model.getAsJsonObject("fallback"), "model")), "upgrade basic: fallback");
        }
    }

    private static void testUpgradeDefaultMinecraftNamespace() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":2},"model":"item/foo"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "item/base");
        check(result.isSuccess(), "upgrade default namespace: 应成功");
        if (result.isSuccess()) {
            JsonObject model = result.value().getAsJsonObject("model");
            JsonObject entry = model.getAsJsonArray("entries").get(0).getAsJsonObject();
            check("minecraft:item/foo".equals(string(entry.getAsJsonObject("model"), "model")), "upgrade default namespace: entry 默认 minecraft");
            check("minecraft:item/base".equals(string(model.getAsJsonObject("fallback"), "model")), "upgrade default namespace: fallback 默认 minecraft");
        }
    }

    private static void testUpgradeNamespacedPredicate() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"minecraft:custom_model_data":3},"model":"mymod:item/foo"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(result.isSuccess(), "upgrade namespaced predicate: 应成功");
    }

    private static void testUpgradeAscendingThresholds() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":0},"model":"mymod:item/a"},
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/b"},
                  {"predicate":{"custom_model_data":2},"model":"mymod:item/c"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(result.isSuccess(), "upgrade ascending thresholds: 应成功");
        if (result.isSuccess()) {
            JsonObject model = result.value().getAsJsonObject("model");
            check(model.getAsJsonArray("entries").size() == 3, "upgrade ascending thresholds: 数量");
        }
    }

    private static void testUpgradePreservesThresholdLiteral() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1.50},"model":"mymod:item/foo"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(result.isSuccess(), "upgrade preserves threshold literal: 应成功");
        if (result.isSuccess()) {
            JsonObject model = result.value().getAsJsonObject("model");
            JsonObject entry = model.getAsJsonArray("entries").get(0).getAsJsonObject();
            check(number(entry, "threshold").equals("1.50"), "upgrade preserves threshold literal: 保留 1.50");
        }
    }

    private static void testUpgradeRejectsDescendingThresholds() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":2},"model":"mymod:item/a"},
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/b"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade descending thresholds: 应失败");
        check(result.failure() != null, "upgrade descending thresholds: 失败原因非空");
    }

    private static void testUpgradeRejectsDuplicateThresholds() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/a"},
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/b"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade duplicate thresholds: 应失败");
    }

    private static void testUpgradeRejectsNegativeThreshold() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":-1},"model":"mymod:item/a"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade negative threshold: 应失败");
    }

    private static void testUpgradeRejectsMultiplePredicateKeys() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1,"custom_model_data2":1},"model":"mymod:item/a"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade multiple predicate keys: 应失败");
    }

    private static void testUpgradeRejectsOtherPredicate() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"damage":1},"model":"mymod:item/a"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade other predicate: 应失败");
    }

    private static void testUpgradeRejectsOverrideModelEqualsBase() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/base"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade override model equals base: 应失败");
    }

    private static void testUpgradeRejectsMissingOverrides() {
        JsonObject legacy = parseObject("{}");
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade missing overrides: 应失败");
    }

    private static void testUpgradeRejectsNonObjectOverride() {
        JsonObject legacy = parseObject("{\"overrides\":[1]}");
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "upgrade non-object override: 应失败");
    }

    private static void testUpgradeRejectsInvalidBaseRef() {
        JsonObject legacy = parseObject("""
                {"overrides":[
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/a"}
                ]}
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "bad/../id");
        check(!result.isSuccess(), "upgrade invalid baseRef: 应失败");
    }

    private static void testDowngradeBasic() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1.0,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(result.isSuccess(), "downgrade basic: 应成功");
        if (result.isSuccess()) {
            JsonObject legacy = result.value();
            check("mymod:item/base".equals(string(legacy, "parent")), "downgrade basic: parent");
            check(legacy.getAsJsonArray("overrides").size() == 1, "downgrade basic: overrides 数量");
            JsonObject override = legacy.getAsJsonArray("overrides").get(0).getAsJsonObject();
            check(number(override.getAsJsonObject("predicate"), "custom_model_data").equals("1"), "downgrade basic: threshold");
            check("mymod:item/foo".equals(string(override, "model")), "downgrade basic: override model");
        }
    }

    private static void testDowngradeDefaultMinecraftNamespace() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "item/base");
        check(result.isSuccess(), "downgrade default namespace: 应成功");
        if (result.isSuccess()) {
            check("minecraft:item/base".equals(string(result.value(), "parent")), "downgrade default namespace: parent 默认 minecraft");
            JsonObject override = result.value().getAsJsonArray("overrides").get(0).getAsJsonObject();
            check("minecraft:item/foo".equals(string(override, "model")), "downgrade default namespace: override 默认 minecraft");
        }
    }

    private static void testDowngradeRejectsWrongProperty() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:damage",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade wrong property: 应失败");
    }

    private static void testDowngradeRejectsWrongIndex() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 1,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade wrong index: 应失败");
    }

    private static void testDowngradeRejectsWrongScale() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 2,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade wrong scale: 应失败");
    }

    private static void testDowngradeRejectsExtraTopLevelField() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  },
                  "extra": {}
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade extra top-level field: 应失败");
    }

    private static void testDowngradeRejectsExtraRangeDispatchField() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"},
                    "extra": {}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade extra range_dispatch field: 应失败");
    }

    private static void testDowngradeRejectsMissingFallback() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ]
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade missing fallback: 应失败");
    }

    private static void testDowngradeRejectsFallbackMismatch() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/other"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade fallback mismatch: 应失败");
    }

    private static void testDowngradeRejectsEntryModelEqualsBase() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/base"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade entry model equals base: 应失败");
    }

    private static void testDowngradeRejectsDescendingThresholds() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":2,"model":{"type":"minecraft:model","model":"mymod:item/a"}},
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/b"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade descending thresholds: 应失败");
    }

    private static void testDowngradeRejectsDuplicateThresholds() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/a"}},
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/b"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade duplicate thresholds: 应失败");
    }

    private static void testDowngradeRejectsNonPlainEntryModel() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:condition","property":"minecraft:damaged"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade non-plain entry model: 应失败");
    }

    private static void testDowngradeRejectsUnknownEntryField() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/a"},"extra":{}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade unknown entry field: 应失败");
    }

    private static void testDowngradeRejectsEmptyEntries() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(!result.isSuccess(), "downgrade empty entries: 应失败");
    }

    private static void testDowngradePreservesThresholdLiteral() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1.0,
                    "entries": [
                      {"threshold":1.50,"model":{"type":"minecraft:model","model":"mymod:item/foo"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(result.isSuccess(), "downgrade preserves threshold literal: 应成功");
        if (result.isSuccess()) {
            JsonObject override = result.value().getAsJsonArray("overrides").get(0).getAsJsonObject();
            check(number(override.getAsJsonObject("predicate"), "custom_model_data").equals("1.50"),
                    "downgrade preserves threshold literal: 保留 1.50");
        }
    }

    private static void testDowngradeOutputsParentBridge() {
        JsonObject definition = parseObject("""
                {
                  "model": {
                    "type": "minecraft:range_dispatch",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "scale": 1,
                    "entries": [
                      {"threshold":1,"model":{"type":"minecraft:model","model":"mymod:item/a"}},
                      {"threshold":2,"model":{"type":"minecraft:model","model":"mymod:item/b"}}
                    ],
                    "fallback": {"type":"minecraft:model","model":"mymod:item/base"}
                  }
                }
                """);
        ItemOverrideRules.Result result = ItemOverrideRules.downgradeItemDefinition(definition, "mymod", "mymod:item/base");
        check(result.isSuccess(), "downgrade parent bridge: 应成功");
        if (result.isSuccess()) {
            JsonObject legacy = result.value();
            check(legacy.keySet().equals(java.util.Set.of("parent", "overrides")), "downgrade parent bridge: 顶层字段");
            check("mymod:item/base".equals(string(legacy, "parent")), "downgrade parent bridge: parent 保留基础模型");
            check(legacy.getAsJsonArray("overrides").size() == 2, "downgrade parent bridge: overrides 数量");
        }
    }

    private static void testBothDirectionsPreserveBaseModel() {
        JsonObject legacy = parseObject("""
                {"parent":"minecraft:item/generated","textures":{"layer0":"mymod:item/base"},"overrides":[
                  {"predicate":{"custom_model_data":1},"model":"mymod:item/a"},
                  {"predicate":{"custom_model_data":2},"model":"mymod:item/b"}
                ]}
                """);
        ItemOverrideRules.Result upgraded = ItemOverrideRules.upgradeLegacyOverrides(legacy, "mymod", "mymod:item/base");
        check(upgraded.isSuccess(), "both directions: 升级应成功");
        if (!upgraded.isSuccess()) return;
        ItemOverrideRules.Result downgraded = ItemOverrideRules.downgradeItemDefinition(upgraded.value(), "mymod", "mymod:item/base");
        check(downgraded.isSuccess(), "both directions: 降级应成功");
        if (downgraded.isSuccess()) {
            JsonObject bridge = downgraded.value();
            check("mymod:item/base".equals(string(bridge, "parent")), "both directions: 桥接 parent 指向基础模型");
            check(bridge.getAsJsonArray("overrides").size() == 2, "both directions: 桥接 overrides 数量");
            JsonObject first = bridge.getAsJsonArray("overrides").get(0).getAsJsonObject();
            check("mymod:item/a".equals(string(first, "model")), "both directions: 第一条 override");
        }
    }

    private static void reportDone() {
        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("测试输入不是 JSON object");
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static String number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.toString();
    }
}
