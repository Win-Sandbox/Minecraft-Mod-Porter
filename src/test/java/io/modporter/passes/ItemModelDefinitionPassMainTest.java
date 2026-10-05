package io.modporter.passes;

import io.modporter.core.PortRequest;
import io.modporter.core.Report;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * ItemModelDefinitionPass 的 main 式回归测试（无 JUnit）。
 * 前提：VersionInfo.itemModelDefinitions boolean 字段已由主代理加入 mappings。
 * 运行：java -cp <classes> io.modporter.passes.ItemModelDefinitionPassMainTest（期望 ALL PASS）。
 *
 * 覆盖：upgrade / downgrade、同前缀生成、已存在不覆盖（精确路径）、未知 schema（顶层与 model 内）、
 * 目录边界（../、非法 id、myassets 段）、invalid JSON、幂等、overrides/tint/loader TODO、
 * duplicate outputs、no-namespace 默认 minecraft、引用文件必须为 JSON 对象、parent 循环、
 * Java tint 提醒、新→新与旧→旧 no-op。
 */
public final class ItemModelDefinitionPassMainTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testUpgradeGeneratesItemsDefinition();
        testUpgradeProjectPrefixPreserved();
        testUpgradeDoesNotOverwriteExistingItems();
        testUpgradeIdempotent();
        testUpgradeInvalidJson();
        testUpgradeUnsafePathSkipped();
        testUpgradeNonItemModelIgnored();
        testUpgradeOverridesTintTodoButGenerates();
        testUpgradeCustomLoaderTodo();
        testUpgradeDuplicatesKeptWithTodo();
        testDowngradePureModelGeneratesBridge();
        testDowngradeComplexDefinitionKeptWithTodo();
        testDowngradeTopLevelUnknownSchemaKept();
        testDowngradeNoNamespaceDefaultsToMinecraft();
        testDowngradeMissingLocalModelSkipped();
        testDowngradeRefNotJsonObjectSkipped();
        testDowngradeParentCycleSkipped();
        testDowngradeNoOverwriteExistingBridge();
        testDowngradeUnknownSchemaKept();
        testDowngradeRefAtLegacyLocationNoBridge();
        testDowngradeDuplicatesKeptWithTodo();
        testDowngradeRangeDispatchBridgeSuccess();
        testDowngradeRangeDispatchMissingFallbackSkipped();
        testDowngradeRangeDispatchMissingEntrySkipped();
        testDowngradeRangeDispatchCyclicFallbackSkipped();
        testDowngradeRangeDispatchCyclicEntrySkipped();
        testDowngradeRangeDispatchEntryNotJsonObjectSkipped();
        testDowngradeRangeDispatchBridgePathDuplicateSkipped();
        testDowngradeRangeDispatchBridgeIdempotent();
        testJavaTintReminder();
        testSameCapabilityNoOp();
        testAdditionalSafetyBoundaries();
        reportDone();
    }

    // ---------------------------------------------------------- upgrade

    static void testUpgradeGeneratesItemsDefinition() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "upgrade: 输出数量");
        OutputFile items = find(out, "assets/mymod/items/sword.json");
        check(items != null, "upgrade: 应生成 items 定义");
        if (items != null) {
            assertTrue(new String(items.content, StandardCharsets.UTF_8).contains("\"type\": \"minecraft:model\""), "upgrade: type");
            assertTrue(new String(items.content, StandardCharsets.UTF_8).contains("\"model\": \"mymod:item/sword\""), "upgrade: model 引用");
        }
    }

    static void testUpgradeProjectPrefixPreserved() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("src/main/resources/assets/mymod/models/item/tools/iron/axe.json", "{\"parent\":\"minecraft:item/handheld\"}")));
        OutputFile items = find(out, "src/main/resources/assets/mymod/items/tools/iron/axe.json");
        check(items != null, "upgrade: 工程前缀必须保留且生成同前缀 items 定义");
        if (items != null) {
            assertTrue(new String(items.content, StandardCharsets.UTF_8).contains("\"mymod:item/tools/iron/axe\""), "upgrade: 嵌套 model 引用");
        }
        check(find(out, "assets/mymod/items/tools/iron/axe.json") == null, "upgrade: 不应生成丢前缀路径");

        // myassets 不是 assets，不允许匹配。
        PortContext ctx2 = ctx(false, true);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("src/main/resources/myassets/mymod/models/item/x.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(1, out2.size(), "upgrade: myassets 不算 assets");
    }

    static void testUpgradeDoesNotOverwriteExistingItems() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/items/sword.json", "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\"}}")));
        assertEquals(2, out.size(), "upgrade: 已有 items 不新增");
        check(findDuplicatePaths(out).isEmpty(), "upgrade: 无重复路径");
        OutputFile items = find(out, "assets/mymod/items/sword.json");
        assertTrue(new String(items.content, StandardCharsets.UTF_8).contains("range_dispatch"), "upgrade: 已有 items 内容保持不变");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "upgrade: 冲突应有 TODO");

        // 精确路径：前缀不同的 items 不算“已存在”，允许生成同前缀 items。
        PortContext ctx2 = ctx(false, true);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("src/main/resources/assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/items/sword.json", "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\"}}")));
        OutputFile prefixed = find(out2, "src/main/resources/assets/mymod/items/sword.json");
        check(prefixed != null, "upgrade: 精确路径查已存在（不同前缀不冲突，生成同前缀 items）");
    }

    static void testUpgradeIdempotent() {
        PortContext ctx = ctx(false, true);
        ItemModelDefinitionPass pass = new ItemModelDefinitionPass(ctx);
        List<OutputFile> first = pass.transform(List.of(
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}")));
        List<OutputFile> second = pass.transform(first);
        assertEquals(first.size(), second.size(), "upgrade 幂等: 数量不变");
        for (OutputFile o : first) {
            OutputFile match = find(second, o.relativePath);
            check(match != null, "upgrade 幂等: " + o.relativePath + " 仍存在");
            check(match != null && eq(match, o), "upgrade 幂等: 内容不变 " + o.relativePath);
        }
        check(findDuplicatePaths(second).isEmpty(), "upgrade 幂等: 无重复路径");
    }

    static void testUpgradeInvalidJson() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/broken.json", "{invalid")));
        assertEquals(1, out.size(), "upgrade invalid JSON: 不新增");
        check(hasTodo(ctx, "assets/mymod/models/item/broken.json"), "upgrade invalid JSON: TODO");
    }

    static void testUpgradeUnsafePathSkipped() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/../escape.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(1, out.size(), "upgrade ../: 不新增");
        check(hasTodo(ctx, "assets/mymod/models/item/../escape.json"), "upgrade ../: TODO");

        PortContext ctx2 = ctx(false, true);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("assets/MyMod/models/item/Sword.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(1, out2.size(), "upgrade 大写命名空间/路径: 不新增");
        check(hasTodo(ctx2, "assets/MyMod/models/item/Sword.json"), "upgrade 大写命名空间/路径: TODO");
    }

    static void testUpgradeNonItemModelIgnored() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/block/stone.json", "{\"parent\":\"minecraft:block/cube_all\"}"),
                of("assets/mymod/blockstates/stone.json", "{\"variants\":{}}")));
        assertEquals(2, out.size(), "upgrade: 非物品模型不动");
    }

    static void testUpgradeOverridesTintTodoButGenerates() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/bow.json",
                        "{\"parent\":\"minecraft:item/generated\",\"overrides\":[{\"predicate\":{\"custom_model_data\":1},\"model\":\"mymod:item/bow_pulled\"}]}")));
        OutputFile items = find(out, "assets/mymod/items/bow.json");
        check(items != null, "upgrade overrides: 仍生成基础 items 定义");
        check(hasTodo(ctx, "assets/mymod/models/item/bow.json"), "upgrade overrides: 复杂特性 TODO");
    }

    static void testUpgradeCustomLoaderTodo() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/loader.json", "{\"loader\":\"forge:obj\",\"model\":\"mymod:models/obj/sword.obj\"}"),
                of("assets/mymod/models/item/render.json", "{\"parent\":\"minecraft:item/generated\",\"render_type\":\"minecraft:translucent\"}")));
        check(find(out, "assets/mymod/items/loader.json") != null, "upgrade loader: 仍生成 items 定义");
        check(find(out, "assets/mymod/items/render.json") != null, "upgrade render_type: 仍生成 items 定义");
        check(hasTodo(ctx, "assets/mymod/models/item/loader.json"), "upgrade loader: TODO");
        check(hasTodo(ctx, "assets/mymod/models/item/render.json"), "upgrade render_type: TODO");
    }

    static void testUpgradeDuplicatesKeptWithTodo() {
        PortContext ctx = ctx(false, true);
        List<OutputFile> in = new ArrayList<>();
        in.add(of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}"));
        in.add(of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/handheld\"}"));
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(in);
        assertEquals(2, out.size(), "upgrade duplicates: 全部保留不丢任何一个");
        check(hasTodo(ctx, "assets/mymod/models/item/sword.json"), "upgrade duplicates: TODO");
        check(find(out, "assets/mymod/items/sword.json") == null, "upgrade duplicates: 重复源不生成 items");
    }

    // ---------------------------------------------------------- downgrade

    static void testDowngradePureModelGeneratesBridge() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}"),
                of("assets/mymod/models/item/sword_model.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(3, out.size(), "downgrade: 新增桥接");
        OutputFile bridge = find(out, "assets/mymod/models/item/sword.json");
        check(bridge != null, "downgrade: 生成桥接模型");
        if (bridge != null) {
            assertTrue(new String(bridge.content, StandardCharsets.UTF_8).contains("\"parent\": \"mymod:item/sword_model\""), "downgrade: parent 引用");
        }
        check(find(out, "assets/mymod/items/sword.json") != null, "downgrade: items 原样保留");
    }

    static void testDowngradeComplexDefinitionKeptWithTodo() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/complex.json",
                        "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\",\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/base\"}}}"),
                of("assets/mymod/models/item/base.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "downgrade complex: 不新增");
        check(hasTodo(ctx, "assets/mymod/items/complex.json"), "downgrade complex: TODO");

        PortContext ctx2 = ctx(true, false);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("assets/mymod/items/tinted.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/base\",\"tints\":[{\"type\":\"minecraft:tint_item\"}]}}"),
                of("assets/mymod/models/item/base.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out2.size(), "downgrade tint: 不新增");
        check(hasTodo(ctx2, "assets/mymod/items/tinted.json"), "downgrade tint: TODO");
    }

    static void testDowngradeTopLevelUnknownSchemaKept() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/extra.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/base\"},\"some_loader\":{}}"),
                of("assets/mymod/models/item/base.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "downgrade 顶层未知字段: 不新增");
        check(hasTodo(ctx, "assets/mymod/items/extra.json"), "downgrade 顶层未知字段: TODO");
    }

    static void testDowngradeNoNamespaceDefaultsToMinecraft() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/standard.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"item/generated\"}}"),
                // 注意：本输出里只有 mymod 的本地模型，minecraft:item/generated 不在输出中。
                of("assets/mymod/models/item/local.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "downgrade 无ns默认minecraft: 引用不本地可用时不生成");
        check(hasTodo(ctx, "assets/mymod/items/standard.json"), "downgrade 无ns默认minecraft: TODO");
    }

    static void testDowngradeMissingLocalModelSkipped() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/orphan.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/does_not_exist\"}}")));
        assertEquals(1, out.size(), "downgrade 引用缺失: 不新增");
        check(hasTodo(ctx, "assets/mymod/items/orphan.json"), "downgrade 引用缺失: TODO");
    }

    static void testDowngradeRefNotJsonObjectSkipped() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/badref.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/badfile\"}}"),
                of("assets/mymod/models/item/badfile.json", "[1, 2, 3]")));
        assertEquals(2, out.size(), "downgrade 引用非JSON对象: 不新增");
        check(hasTodo(ctx, "assets/mymod/items/badref.json"), "downgrade 引用非JSON对象: TODO");
    }

    static void testDowngradeParentCycleSkipped() {
        // sword_model 的 parent 指回桥接位置 mymod:item/sword → 循环，拒绝生成。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}"),
                of("assets/mymod/models/item/sword_model.json", "{\"parent\":\"mymod:item/sword\"}")));
        assertEquals(2, out.size(), "downgrade parent 自循环: 不新增");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "downgrade parent 自循环: TODO");

        // 间接循环：sword_model -> mid_model -> 桥接位置。
        PortContext ctx2 = ctx(true, false);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}"),
                of("assets/mymod/models/item/sword_model.json", "{\"parent\":\"mymod:item/mid_model\"}"),
                of("assets/mymod/models/item/mid_model.json", "{\"parent\":\"mymod:item/sword\"}")));
        assertEquals(3, out2.size(), "downgrade parent 间接循环: 不新增");
        check(hasTodo(ctx2, "assets/mymod/items/sword.json"), "downgrade parent 间接循环: TODO");
    }

    static void testDowngradeNoOverwriteExistingBridge() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}"),
                of("assets/mymod/models/item/sword_model.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"mymod:item/sword_model\",\"textures\":{\"layer0\":\"mymod:item/custom\"}}")));
        assertEquals(3, out.size(), "downgrade 已有桥接: 不新增");
        OutputFile bridge = find(out, "assets/mymod/models/item/sword.json");
        assertTrue(new String(bridge.content, StandardCharsets.UTF_8).contains("layer0"), "downgrade 已有桥接: 既有内容不被覆盖");
        check(hasTodo(ctx, "assets/mymod/models/item/sword.json"), "downgrade 已有桥接: 冲突 TODO");
    }

    static void testDowngradeUnknownSchemaKept() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/custom_loader.json", "{\"some_loader\":{\"format\":\"custom\"}}")));
        assertEquals(1, out.size(), "downgrade 未知 schema: 不新增不删除");
        check(hasTodo(ctx, "assets/mymod/items/custom_loader.json"), "downgrade 未知 schema: TODO");
    }

    static void testDowngradeRefAtLegacyLocationNoBridge() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword\"}}"),
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "downgrade 引用已在legacy位置: 不新增");
        OutputFile legacy = find(out, "assets/mymod/models/item/sword.json");
        assertTrue(new String(legacy.content, StandardCharsets.UTF_8).contains("minecraft:item/generated"), "downgrade 引用已在legacy位置: 既有模型不被改写");
    }

    static void testDowngradeDuplicatesKeptWithTodo() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> in = new ArrayList<>();
        in.add(of("assets/mymod/items/sword.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}"));
        in.add(of("assets/mymod/items/sword.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/other\"}}"));
        in.add(of("assets/mymod/models/item/sword_model.json", "{\"parent\":\"minecraft:item/generated\"}"));
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(in);
        assertEquals(3, out.size(), "downgrade duplicates: 全部保留");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "downgrade duplicates: TODO");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "downgrade duplicates: 不生成桥接");
    }

    // ---------------------------------------------------------- range_dispatch 降级（P1）

    static String rangeDispatchJson(String fallbackRef, String... entryRefsWithThreshold) {
        // entryRefsWithThreshold: 交替 threshold,modelRef
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i + 1 < entryRefsWithThreshold.length; i += 2) {
            if (entries.length() > 0) entries.append(',');
            entries.append("{\"threshold\":").append(entryRefsWithThreshold[i])
                    .append(",\"model\":{\"type\":\"minecraft:model\",\"model\":\"")
                    .append(entryRefsWithThreshold[i + 1]).append("\"}}");
        }
        return "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\","
                + "\"index\":0,\"scale\":1.0,\"entries\":[" + entries + "],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"" + fallbackRef + "\"}}}";
    }

    static void testDowngradeRangeDispatchBridgeSuccess() {
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(4, out.size(), "range_dispatch 降级: 新增桥接");
        OutputFile bridge = find(out, "assets/mymod/models/item/sword.json");
        check(bridge != null, "range_dispatch 降级: 生成桥接模型");
        if (bridge != null) {
            String content = new String(bridge.content, StandardCharsets.UTF_8);
            assertTrue(content.contains("\"parent\": \"mymod:item/sword_base\""), "range_dispatch 降级: parent 指向 fallback");
            assertTrue(content.contains("\"mymod:item/sword_glow\""), "range_dispatch 降级: overrides 含 entry 引用");
            assertTrue(!content.contains("minecraft:item/generated"), "range_dispatch 降级: 不再硬编码 minecraft:item/generated");
        }
        check(find(out, "assets/mymod/items/sword.json") != null, "range_dispatch 降级: items 原样保留");
    }

    static void testDowngradeRangeDispatchMissingFallbackSkipped() {
        // fallback 引用的模型不在输出中本地可用：整条降级放弃，不生成任何文件。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/missing_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "range_dispatch fallback 缺失: 不新增");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "range_dispatch fallback 缺失: 不生成桥接");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch fallback 缺失: TODO");
    }

    static void testDowngradeRangeDispatchMissingEntrySkipped() {
        // fallback 本地可用，但某个 entry 引用缺失：原子放弃，不能只生成部分桥接。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/missing_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(2, out.size(), "range_dispatch entry 缺失: 不新增");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "range_dispatch entry 缺失: 不生成桥接（原子放弃，不部分写入）");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch entry 缺失: TODO");
    }

    static void testDowngradeRangeDispatchCyclicFallbackSkipped() {
        // fallback 的 parent 链指回桥接位置（自循环）。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"mymod:item/sword\"}"),
                of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(3, out.size(), "range_dispatch fallback 循环: 不新增");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "range_dispatch fallback 循环: 不生成桥接");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch fallback 循环: TODO");
    }

    static void testDowngradeRangeDispatchCyclicEntrySkipped() {
        // entry 引用的模型 parent 链指回桥接位置（间接循环）。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"mymod:item/sword\"}")));
        assertEquals(3, out.size(), "range_dispatch entry 循环: 不新增");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "range_dispatch entry 循环: 不生成桥接");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch entry 循环: TODO");
    }

    static void testDowngradeRangeDispatchEntryNotJsonObjectSkipped() {
        // entry 引用的模型文件存在但不是合法 JSON 对象。
        PortContext ctx = ctx(true, false);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/models/item/sword_glow.json", "[1,2,3]")));
        assertEquals(3, out.size(), "range_dispatch entry 非JSON对象: 不新增");
        check(find(out, "assets/mymod/models/item/sword.json") == null, "range_dispatch entry 非JSON对象: 不生成桥接");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch entry 非JSON对象: TODO");
    }

    static void testDowngradeRangeDispatchBridgePathDuplicateSkipped() {
        // 桥接目标路径本身在输出中有重复条目（duplicates 集合），绝不写入。
        PortContext ctx = ctx(true, false);
        List<OutputFile> in = new ArrayList<>();
        in.add(of("assets/mymod/items/sword.json",
                rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")));
        in.add(of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}"));
        in.add(of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/handheld\"}"));
        in.add(of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}"));
        in.add(of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"minecraft:item/generated\"}"));
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(in);
        assertEquals(5, out.size(), "range_dispatch 桥接路径重复: 全部保留不新增");
        check(hasTodo(ctx, "assets/mymod/items/sword.json"), "range_dispatch 桥接路径重复: TODO");
    }

    static void testDowngradeRangeDispatchBridgeIdempotent() {
        PortContext ctx = ctx(true, false);
        ItemModelDefinitionPass pass = new ItemModelDefinitionPass(ctx);
        List<OutputFile> first = pass.transform(List.of(
                of("assets/mymod/items/sword.json",
                        rangeDispatchJson("mymod:item/sword_base", "1", "mymod:item/sword_glow")),
                of("assets/mymod/models/item/sword_base.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("assets/mymod/models/item/sword_glow.json", "{\"parent\":\"minecraft:item/generated\"}")));
        List<OutputFile> second = pass.transform(first);
        assertEquals(first.size(), second.size(), "range_dispatch 桥接幂等: 数量不变");
        for (OutputFile o : first) {
            OutputFile match = find(second, o.relativePath);
            check(match != null && eq(match, o), "range_dispatch 桥接幂等: 内容不变 " + o.relativePath);
        }
    }

    // ---------------------------------------------------------- 其他

    static void testJavaTintReminder() {
        PortContext ctx = ctx(false, true);
        new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}"),
                of("src/main/java/com/example/MyColorProvider.java", "class MyColorProvider implements ColorProvider {}")));
        check(hasTodo(ctx, "src/main/java/com/example/MyColorProvider.java"), "Java tint 提醒 TODO");
    }

    static void testSameCapabilityNoOp() {
        PortContext ctx = ctx(true, true);
        List<OutputFile> out = new ItemModelDefinitionPass(ctx).transform(List.of(
                of("assets/mymod/items/sword.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/sword_model\"}}")));
        assertEquals(1, out.size(), "新→新: 不动");

        PortContext ctx2 = ctx(false, false);
        List<OutputFile> out2 = new ItemModelDefinitionPass(ctx2).transform(List.of(
                of("assets/mymod/models/item/sword.json", "{\"parent\":\"minecraft:item/generated\"}")));
        assertEquals(1, out2.size(), "旧→旧: 不动");
    }

    static void testAdditionalSafetyBoundaries() {
        var up = ctx(false, true);
        var out = new ItemModelDefinitionPass(up).transform(List.of(
                of("src/main/resources/assets/mymod/models/item/x.json", "{}"),
                of("src/main/resources/assets/mymod/models/item/note.txt", "{}")));
        check(find(out, "src/main/resources/assets/mymod/items/x.json") != null, "仅去掉一个json后缀");
        check(find(out, "src/main/resources/assets/mymod/items/x.json.json") == null, "不生成重复json后缀");
        check(out.size() == 3, "非JSON模型文件不参与生成");
        var down = ctx(true, false);
        out = new ItemModelDefinitionPass(down).transform(List.of(
                of("assets/mymod/items/x.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mymod:item/a\"}}"),
                of("assets/mymod/models/item/a.json", "{\"parent\":\"mymod:item/b\"}"),
                of("assets/mymod/models/item/b.json", "{\"parent\":\"mymod:item/a\"}")));
        check(out.size() == 3 && hasTodo(down, "assets/mymod/items/x.json"), "不涉及桥接路径的循环也必须拒绝");
        up = ctx(false, true);
        out = new ItemModelDefinitionPass(up).transform(List.of(of("../assets/mymod/models/item/a.json", "{}")));
        check(out.size() == 1 && hasTodo(up, "../assets/mymod/models/item/a.json"), "工程前缀也要防目录穿越");
    }

    // ---------------------------------------------------------- 构造与断言

    static PortContext ctx(boolean sourceItems, boolean targetItems) {
        VersionMappings src = mappings(sourceItems);
        VersionMappings dst = mappings(targetItems);
        MappingResolver resolver = new MappingResolver(src, dst);
        Report report = new Report();
        PortRequest request = new PortRequest(Path.of("in"), Path.of("out"),
                "fabric", "1.20.1", "1.21.4", false);
        return new PortContext(request, resolver, report);
    }

    static VersionMappings mappings(boolean itemModelDefinitions) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.mcVersion = "test";
        info.loader = "fabric";
        info.metadataFormat = "mods.toml";
        info.metadataPath = "META-INF/mods.toml";
        info.langFormat = "json";
        info.packFormat = 1;
        info.itemModelDefinitions = itemModelDefinitions; // 主代理已添加的 boolean 字段
        return new VersionMappings(Path.of("mappings/none"), List.of(), info,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashSet<>());
    }

    static OutputFile of(String path, String content) {
        return new OutputFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    static OutputFile find(List<OutputFile> list, String path) {
        for (OutputFile o : list) {
            if (o.relativePath.equals(path)) return o;
        }
        return null;
    }

    static List<String> findDuplicatePaths(List<OutputFile> list) {
        List<String> seen = new ArrayList<>();
        List<String> dup = new ArrayList<>();
        for (OutputFile o : list) {
            if (seen.contains(o.relativePath)) dup.add(o.relativePath);
            else seen.add(o.relativePath);
        }
        return dup;
    }

    static boolean eq(OutputFile a, OutputFile b) {
        return java.util.Arrays.equals(a.content, b.content);
    }

    static boolean hasTodo(PortContext ctx, String file) {
        for (Report.Entry e : ctx.report.entries()) {
            if (e.severity == Report.Severity.TODO && file.equals(e.file)) return true;
        }
        return false;
    }

    static void assertEquals(Object expected, Object actual, String what) {
        check(expected.equals(actual), what + "（期望 " + expected + "，实际 " + actual + "）");
    }

    static void assertTrue(boolean cond, String what) {
        check(cond, what);
    }

    static void check(boolean cond, String what) {
        if (!cond) {
            failures++;
            System.out.println("FAIL: " + what);
        } else {
            System.out.println("PASS: " + what);
        }
    }

    static void reportDone() {
        System.out.println(failures == 0 ? "ALL PASS" : ("FAILURES: " + failures));
        if (failures > 0) System.exit(1);
    }

    private ItemModelDefinitionPassMainTest() {}
}
