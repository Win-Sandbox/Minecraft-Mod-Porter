package io.modporter.passes;

import io.modporter.core.PortRequest;
import io.modporter.core.Report;
import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * SettingsGradlePass 的 main 式回归测试（无 JUnit）。
 * 运行：java -cp <classes> io.modporter.passes.SettingsGradlePassMainTest（期望 ALL PASS）。
 *
 * 覆盖：缺失生成（与 build.gradle 同目录）、模板缺失不生成、已有文件幂等、
 * 只插入缺失 pluginManagement 仓库、不覆盖 custom 逻辑、无法安全插入时保留+TODO、
 * settings.gradle.kts 冲突防护、无锚点/多模块保守处理、dryRun 语义由引擎集成保证
 * （generateMissing 在 dryRun 下也会被调用并产生报告，本测试直接调用同等路径）。
 */
public final class SettingsGradlePassMainTest {

    private static int failures = 0;
    private static final String NEOFORGE_TEST_URL = "https://maven.neoforged.net/releases";

    public static void main(String[] args) throws IOException {
        testMissingGeneratesNextToBuildGradle();
        testMissingNestedBuildGradleAnchor();
        testMissingTemplateAllowedNoGeneration();
        testMissingTemplateButRepositoryRequiredTodo();
        testExistingWithRepositoryIsIdempotent();
        testExistingNoPluginManagementGetsPrefixAndKeepsCustom();
        testExistingPluginManagementRepositoriesGetsInsertion();
        testExistingPluginManagementWithoutRepositoriesGetsBlock();
        testExistingRepositoryRequiredIgnoresCommentOnlyTemplate();
        testRepositoryRequiredIgnoresCommentAndStringInsideSettings();
        testRepositoryRequiredIgnoresUrlOutsideRepositoriesBlock();
        testRepositoryRequiredRecognizesUrlCallAndDoubleQuotedForms();
        testReal1203TemplateRequiresRepositoryAndInsertsIntoExisting();
        testExistingMalformedKeptWithTodo();
        testExistingKtsPreventsGroovyGeneration();
        testNoBuildGradleAnchorNoGeneration();
        testMultipleShallowBuildGradleNoGeneration();
        testTargetNotNeoforgeKeepsExisting();
        testRenderedTemplateUsesModid();
        reportDone();
    }

    static void testMissingGeneratesNextToBuildGradle() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("build.gradle");
        OutputFile out = pass.generateMissing();
        check(out != null, "missing: 应生成 settings.gradle");
        if (out != null) {
            assertEquals("settings.gradle", out.relativePath, "missing: 输出路径");
            String content = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(content.contains("maven.neoforged.net/releases"), "missing: NeoGradle 仓库");
            assertTrue(content.contains("rootProject.name = 'mymod'"), "missing: modid 渲染");
            assertTrue(content.contains("gradlePluginPortal()"), "missing: Plugin Portal 保留");
        }
    }

    static void testMissingNestedBuildGradleAnchor() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("subproject/build.gradle");
        OutputFile out = pass.generateMissing();
        check(out != null, "nested: 应生成 settings.gradle");
        if (out != null) {
            assertEquals("subproject/settings.gradle", out.relativePath, "nested: 与 build.gradle 同目录");
        }
    }

    static void testMissingTemplateAllowedNoGeneration() throws IOException {
        PortContext ctx = ctx(false, false);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("build.gradle");
        OutputFile out = pass.generateMissing();
        check(out == null, "no-template: 不生成");
        check(hasInfo(ctx, null, "无 settings.gradle 模板"), "no-template: INFO 报告");
        check(!hasTodoAnywhere(ctx), "no-template: 无需 TODO");
    }

    static void testMissingTemplateButRepositoryRequiredTodo() throws IOException {
        // NeoForge + settings 模板缺失：UNKNOWN 保守 TODO，不生成、不猜测插入。
        PortContext ctx = ctx(true, false);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("build.gradle");
        OutputFile out = pass.generateMissing();
        check(out == null, "required-no-template: 不生成");
        check(hasTodo(ctx, "build.gradle"), "required-no-template: TODO 报告");

        PortContext ctx2 = ctx(true, false);
        SettingsGradlePass pass2 = new SettingsGradlePass(ctx2, new MetadataPass(ctx2));
        String content = "pluginManagement {\n    repositories {\n        gradlePluginPortal()\n    }\n}\n"
                + "rootProject.name = 'custom'\n";
        OutputFile out2 = pass2.transformExisting("settings.gradle", content);
        check(out2 != null && content.equals(new String(out2.content, StandardCharsets.UTF_8)),
                "unknown-template: 已有 settings 原样保留");
        check(hasTodo(ctx2, "settings.gradle"), "unknown-template: TODO 不猜测插入");
    }

    static void testExistingWithRepositoryIsIdempotent() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "pluginManagement {\n    repositories {\n"
                + "        gradlePluginPortal()\n"
                + "        maven { url = 'https://maven.neoforged.net/releases' }\n"
                + "    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null && content.equals(new String(out.content, StandardCharsets.UTF_8)),
                "idempotent: 已有仓库时内容不变");
        check(hasInfo(ctx, "settings.gradle", "幂等"), "idempotent: INFO 报告");
        check(!hasTodo(ctx, "settings.gradle"), "idempotent: 无 TODO");
    }

    static void testExistingNoPluginManagementGetsPrefixAndKeepsCustom() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "rootProject.name = 'custom'\ninclude 'sub'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null, "prefix: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(s.startsWith("pluginManagement {"), "prefix: pluginManagement 在最前");
            assertTrue(s.contains("maven.neoforged.net/releases"), "prefix: 插入仓库");
            assertTrue(s.contains("rootProject.name = 'custom'\ninclude 'sub'\n"), "prefix: custom 保留");
        }
    }

    static void testExistingPluginManagementRepositoriesGetsInsertion() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "pluginManagement {\n    repositories {\n"
                + "        gradlePluginPortal()\n    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null, "insert: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(s.indexOf("gradlePluginPortal()") < s.indexOf("maven.neoforged.net/releases"),
                    "insert: 仓库插入 repositories 内");
            assertTrue(!s.contains("\\n"), "insert: 实际换行而非字面 \\n");
            assertTrue(s.contains("rootProject.name = 'custom'"), "insert: custom 保留");
            // 幂等重跑：第一遍结果再处理应原样返回。
            OutputFile rerun = pass.transformExisting("settings.gradle", s);
            check(rerun != null && s.equals(new String(rerun.content, StandardCharsets.UTF_8)),
                    "insert: 重跑幂等");
            check(!hasTodo(ctx, "settings.gradle"), "insert: 无 TODO");
        }
    }

    static void testExistingPluginManagementWithoutRepositoriesGetsBlock() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "pluginManagement {\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null, "block: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(s.contains("repositories {"), "block: 生成 repositories");
            assertTrue(s.contains("maven.neoforged.net/releases"), "block: 插入仓库");
            assertTrue(s.contains("gradlePluginPortal()"), "block: 插入 Plugin Portal");
            assertTrue(!s.contains("\\n"), "block: 实际换行而非字面 \\n");
            assertTrue(s.endsWith("rootProject.name = 'custom'\n"), "block: custom 保留");
        }
    }

    static void testExistingRepositoryRequiredIgnoresCommentOnlyTemplate() throws IOException {
        // build 模板 URL 只在注释中：不应触发仓库注入，settings.gradle 原样保留。
        PortContext ctx = ctx(true, false);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "pluginManagement {\n    repositories {\n"
                + "        gradlePluginPortal()\n    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null && content.equals(new String(out.content, StandardCharsets.UTF_8)),
                "comment-only-template: 原样保留");
        check(hasInfo(ctx, "settings.gradle", "原样保留"), "comment-only-template: INFO 报告");
        check(!hasTodoAnywhere(ctx), "comment-only-template: 无 TODO");
    }

    static void testRepositoryRequiredIgnoresCommentAndStringInsideSettings() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        // URL/Portal 只出现在 repositories 块的注释和字符串里，不算真实存在。
        String content = "pluginManagement {\n    repositories {\n"
                + "        // maven { url = 'https://maven.neoforged.net/releases' }\n"
                + "        /* gradlePluginPortal() */\n"
                + "        def note = \"https://maven.neoforged.net/releases gradlePluginPortal()\"\n"
                + "    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null, "comment-string: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            String body = "pluginManagement {\n    repositories {\n";
            int reposOpenEnd = s.indexOf(body) + body.length();
            String reposBody = s.substring(reposOpenEnd, s.indexOf("    }\n}", reposOpenEnd));
            assertTrue(reposBody.contains("\n        gradlePluginPortal()")
                    && reposBody.contains("\n        maven { url = 'https://maven.neoforged.net/releases' }"),
                    "comment-string: 只补缺失两项且在 repositories 内");
            assertTrue(s.contains("rootProject.name = 'custom'"), "comment-string: custom 保留");
        }
    }

    static void testRepositoryRequiredIgnoresUrlOutsideRepositoriesBlock() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        // URL 在 pluginManagement 其他子块，Portal 在 repositories：只应补 URL 进 repositories。
        String content = "pluginManagement {\n    plugins {\n"
                + "        id 'other' version '1.0' // https://maven.neoforged.net/releases\n"
                + "    }\n    repositories {\n        gradlePluginPortal()\n    }\n}\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null, "outside-block: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            String reposBody = s.substring(s.indexOf("    repositories {"), s.indexOf("}\n}", s.indexOf("    repositories {")));
            assertTrue(reposBody.contains("maven { url = 'https://maven.neoforged.net/releases' }"),
                    "outside-block: URL 插入 repositories 内");
            assertTrue(s.indexOf("maven.neoforged.net/releases'}") == -1
                    && s.indexOf("'https://maven.neoforged.net/releases' }") > s.indexOf("    repositories {"),
                    "outside-block: 未按全 pluginManagement contains 误判");
        }
    }

    static void testRepositoryRequiredRecognizesUrlCallAndDoubleQuotedForms() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        // 目标仓库存在等价写法：url('...') 与 url = "..."，不应重复插入。
        String content = "pluginManagement {\n    repositories {\n"
                + "        gradlePluginPortal()\n"
                + "        url('" + NEOFORGE_TEST_URL + "')\n"
                + "    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null && content.equals(new String(out.content, StandardCharsets.UTF_8)),
                "url-call: 等价写法幂等");

        PortContext ctx2 = ctx(true, true);
        SettingsGradlePass pass2 = new SettingsGradlePass(ctx2, new MetadataPass(ctx2));
        String content2 = "pluginManagement {\n    repositories {\n"
                + "        gradlePluginPortal()\n"
                + "        maven { url = \"" + NEOFORGE_TEST_URL + "\" }\n"
                + "    }\n}\nrootProject.name = 'custom'\n";
        OutputFile out2 = pass2.transformExisting("settings.gradle", content2);
        check(out2 != null && content2.equals(new String(out2.content, StandardCharsets.UTF_8)),
                "double-quote: 等价写法幂等");
    }

    static void testReal1203TemplateRequiresRepositoryAndInsertsIntoExisting() throws IOException {
        // 最小等价于真实 NeoForge 1.20.3：build.gradle 仅在说明注释提到 URL，
        // settings.gradle 模板含 pluginManagement/repositories 的真实 Maven 声明。
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String existing = "pluginManagement {\n    repositories {\n        gradlePluginPortal()\n    }\n}\n"
                + "rootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", existing);
        check(out != null, "real-1203: 输出存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(s.contains("maven { url = 'https://maven.neoforged.net/releases' }"),
                    "real-1203: 已有 settings 补目标仓库");
            assertTrue(s.contains("rootProject.name = 'custom'"), "real-1203: custom 保留");
            check(!hasTodo(ctx, "settings.gradle"), "real-1203: 无 TODO");
        }
    }

    static void testExistingMalformedKeptWithTodo() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "pluginManagement {\n    repositories {\n        gradlePluginPortal()\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null && content.equals(new String(out.content, StandardCharsets.UTF_8)),
                "malformed: 原样保留");
        check(hasTodo(ctx, "settings.gradle"), "malformed: TODO");
    }

    static void testExistingKtsPreventsGroovyGeneration() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("build.gradle");
        pass.noteSettingsGradleKts("settings.gradle.kts");
        OutputFile out = pass.generateMissing();
        check(out == null, "kts: 不生成冲突 Groovy 文件");
        check(hasTodo(ctx, "settings.gradle.kts"), "kts: TODO 提示手动处理 Kotlin DSL");
    }

    static void testNoBuildGradleAnchorNoGeneration() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        OutputFile out = pass.generateMissing();
        check(out == null, "no-anchor: 不生成");
        check(hasInfo(ctx, null, "未发现 build.gradle"), "no-anchor: INFO 报告");
    }

    static void testMultipleShallowBuildGradleNoGeneration() throws IOException {
        PortContext ctx = ctx(true, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        pass.noteBuildGradle("a/build.gradle");
        pass.noteBuildGradle("b/build.gradle");
        OutputFile out = pass.generateMissing();
        check(out == null, "multi: 同层并列不生成");
        check(hasTodoAnywhere(ctx), "multi: TODO 报告");
    }

    static void testTargetNotNeoforgeKeepsExisting() throws IOException {
        PortContext ctx = ctx(false, true);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, new MetadataPass(ctx));
        String content = "rootProject.name = 'custom'\n";
        OutputFile out = pass.transformExisting("settings.gradle", content);
        check(out != null && content.equals(new String(out.content, StandardCharsets.UTF_8)),
                "not-neoforge: 原样保留");
        check(!hasTodoAnywhere(ctx), "not-neoforge: 无 TODO");
    }

    static void testRenderedTemplateUsesModid() throws IOException {
        PortContext ctx = ctx(true, true);
        MetadataPass metadata = new MetadataPass(ctx);
        SettingsGradlePass pass = new SettingsGradlePass(ctx, metadata);
        pass.noteBuildGradle("build.gradle");
        OutputFile out = pass.generateMissing();
        check(out != null, "render: 生成存在");
        if (out != null) {
            String s = new String(out.content, StandardCharsets.UTF_8);
            assertTrue(!s.contains("${modid}"), "render: 占位符已替换");
            assertTrue(s.contains("'mymod'"), "render: modid 值");
        }
    }

    // ---------------------------------------------------------- 构造与断言

    /**
     * @param repositoryRequired true 时目标 settings.gradle 模板含 NeoGradle 仓库声明，
     *                            false 表示 ModDevGradle/其他 loader 无需额外仓库。
     * @param settingsTemplate    true 时写 settings.gradle 模板。
     */
    static PortContext ctx(boolean repositoryRequired, boolean settingsTemplate) throws IOException {
        Path templates = Files.createTempDirectory("modporter-settings-templates");
        // build.gradle 的说明注释不得作为需求来源；真实需求看 settings 模板的仓库声明。
        String build = repositoryRequired
                ? "// NeoGradle 发布在 maven.neoforged.net，需要 settings 仓库\n"
                        + "plugins { id 'net.neoforged.gradle.userdev' }\n"
                : "// ModDevGradle 从 Gradle Plugin Portal 解析，无需额外仓库\n"
                        + "plugins { id 'net.neoforged.moddev' }\n";
        Files.writeString(templates.resolve("build.gradle"), build, StandardCharsets.UTF_8);
        if (settingsTemplate) {
            Files.writeString(templates.resolve("settings.gradle"),
                    repositoryRequired
                            ? "pluginManagement {\n    repositories {\n        gradlePluginPortal()\n"
                                    + "        maven { url = 'https://maven.neoforged.net/releases' }\n    }\n}\n\n"
                                    + "rootProject.name = '${modid}'\n"
                            : "pluginManagement {\n    repositories {\n        gradlePluginPortal()\n    }\n}\n\n"
                                    + "rootProject.name = '${modid}'\n",
                    StandardCharsets.UTF_8);
        }
        VersionMappings src = mappings("fabric", List.of());
        VersionMappings dst = mappings(repositoryRequired ? "neoforge" : "fabric", List.of(templates));
        MappingResolver resolver = new MappingResolver(src, dst);
        Report report = new Report();
        PortRequest request = new PortRequest(Path.of("in"), Path.of("out"),
                "fabric", "1.20.1", "1.20.4", false);
        PortContext context = new PortContext(request, resolver, report);
        context.modMeta.modid = "mymod";
        return context;
    }

    static VersionMappings mappings(String loader, List<Path> templateDirs) {
        VersionMappings.VersionInfo info = new VersionMappings.VersionInfo();
        info.mcVersion = "test";
        info.loader = loader;
        info.javaVersion = 17;
        info.metadataFormat = "mods.toml";
        info.metadataPath = "META-INF/mods.toml";
        info.langFormat = "json";
        info.packFormat = 1;
        return new VersionMappings(Path.of("mappings/none"), templateDirs, info,
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashSet<>());
    }

    static boolean hasTodo(PortContext ctx, String file) {
        for (Report.Entry e : ctx.report.entries()) {
            if (e.severity == Report.Severity.TODO && file.equals(e.file)) return true;
        }
        return false;
    }

    static boolean hasTodoAnywhere(PortContext ctx) {
        for (Report.Entry e : ctx.report.entries()) {
            if (e.severity == Report.Severity.TODO) return true;
        }
        return false;
    }

    static boolean hasInfo(PortContext ctx, String file, String keyword) {
        for (Report.Entry e : ctx.report.entries()) {
            if (e.severity == Report.Severity.INFO
                    && (file == null || file.equals(e.file))
                    && e.message.contains(keyword)) return true;
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

    private SettingsGradlePassMainTest() {}
}
