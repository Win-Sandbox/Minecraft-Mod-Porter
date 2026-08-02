package io.modporter.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.modporter.core.PortEngine;
import io.modporter.core.PortRequest;
import io.modporter.core.PortResult;
import io.modporter.core.ProgressListener;
import io.modporter.core.Report;
import io.modporter.mappings.MappingRepository;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;
import io.modporter.passes.AssetJsonPass;
import io.modporter.passes.BuildGradlePass;
import io.modporter.passes.JavaSourcePass;
import io.modporter.passes.LangPass;
import io.modporter.passes.MetadataPass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认转换引擎实现：扫描输入工程，按文件类型分派到各 Pass，写出目标工程和报告。
 */
public final class DefaultPortEngine implements PortEngine {

    private static final Set<String> SKIP_DIRS = Set.of(
            "build", ".gradle", ".git", ".idea", "run", "out", "bin", "eclipse", ".settings");

    private final MappingRepository repository;

    public DefaultPortEngine(MappingRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<VersionDescriptor> supportedVersions() {
        try {
            return repository.listVersions().stream()
                    .map(pair -> new VersionDescriptor(pair[0], pair[1]))
                    .sorted(Comparator.comparing(VersionDescriptor::loader)
                            .thenComparing(VersionDescriptor::mcVersion))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public PortResult port(PortRequest request, ProgressListener listener) {
        if (listener == null) listener = ProgressListener.NOOP;
        Report report = new Report();
        report.setVersions(request.sourceVersion(), request.targetVersion());

        VersionMappings source;
        VersionMappings target;
        try {
            listener.onMessage("加载映射数据: " + request.loader() + " " + request.sourceVersion()
                    + " / " + request.targetVersion());
            source = repository.load(request.loader(), request.sourceVersion());
            target = repository.load(request.loader(), request.targetVersion());
        } catch (IOException e) {
            report.add(Report.Severity.ERROR, null, null, "mappings", e.getMessage());
            return new PortResult(PortResult.Status.FAILED, report);
        }

        PortContext ctx = new PortContext(request, new MappingResolver(source, target), report);
        try {
            ctx.sourceJava = repository.loadJavaPlatform(source.info.javaVersion);
            ctx.targetJava = repository.loadJavaPlatform(target.info.javaVersion);
            ctx.javaFeatures = repository.javaFeatures();
            if (ctx.targetJava == null) {
                report.add(Report.Severity.WARN, null, null, "java-platform",
                        "缺少 Java " + target.info.javaVersion + " 的平台数据（mappings/java/），跳过 Java 语法/类库检查");
            }
        } catch (IOException e) {
            report.add(Report.Severity.WARN, null, null, "java-platform", "Java 平台数据加载失败: " + e.getMessage());
        }
        MetadataPass metadataPass = new MetadataPass(ctx);
        BuildGradlePass buildGradlePass = new BuildGradlePass(ctx, metadataPass);
        JavaSourcePass javaPass = new JavaSourcePass(ctx);
        LangPass langPass = new LangPass(ctx);
        AssetJsonPass assetPass = new AssetJsonPass(ctx);

        List<Path> files;
        try (Stream<Path> walk = Files.walk(request.inputRoot())) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(request.inputRoot().relativize(p)))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            report.add(Report.Severity.ERROR, null, null, "io", "扫描输入目录失败: " + e.getMessage());
            return new PortResult(PortResult.Status.FAILED, report);
        }

        // 第一阶段：先解析元数据与构建脚本，供其他 Pass 使用（modid、group 等）
        for (Path file : files) {
            String rel = relPath(request.inputRoot(), file);
            String name = file.getFileName().toString();
            try {
                if (isMetadataFile(rel, name, source)) {
                    metadataPass.parse(rel, Files.readString(file, StandardCharsets.UTF_8));
                } else if (name.equals("build.gradle")) {
                    buildGradlePass.parse(Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                ctx.error(rel, null, "io", "读取失败: " + e.getMessage());
            }
        }

        // 第二阶段：逐文件转换
        List<OutputFile> outputs = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String rel = relPath(request.inputRoot(), file);
            String name = file.getFileName().toString();
            listener.onFileStart(rel, index++, files.size());
            long todosBefore = report.count(Report.Severity.TODO);
            try {
                OutputFile out = transformOne(ctx, rel, name, file, source,
                        metadataPass, buildGradlePass, javaPass, langPass, assetPass);
                if (out != null) outputs.add(out);
            } catch (Exception e) {
                ctx.error(rel, null, "internal", "转换过程异常，文件原样复制: " + e);
                OutputFile fallback = copyVerbatim(rel, file, ctx);
                if (fallback != null) outputs.add(fallback);
            }
            listener.onFileDone(rel, (int) (report.count(Report.Severity.TODO) - todosBefore));
        }

        // 写出
        if (!request.dryRun()) {
            listener.onMessage("写出目标工程: " + request.outputRoot());
            try {
                for (OutputFile out : outputs) {
                    Path dest = request.outputRoot().resolve(out.relativePath);
                    Files.createDirectories(dest.getParent());
                    Files.write(dest, out.content);
                }
                writeReports(request.outputRoot(), report);
                int todoCount = writeTodoReport(request.outputRoot(), outputs);
                if (todoCount > 0) {
                    listener.onMessage("TODO 清单（共 " + todoCount + " 处）: "
                            + request.outputRoot().resolve(TODO_REPORT_NAME));
                }
            } catch (IOException e) {
                report.add(Report.Severity.ERROR, null, null, "io", "写出失败: " + e.getMessage());
                return new PortResult(PortResult.Status.FAILED, report);
            }
        }

        PortResult.Status status = report.count(Report.Severity.TODO) > 0
                || report.count(Report.Severity.ERROR) > 0
                ? PortResult.Status.SUCCESS_WITH_TODOS
                : PortResult.Status.SUCCESS;
        return new PortResult(status, report);
    }

    private OutputFile transformOne(PortContext ctx, String rel, String name, Path file,
                                    VersionMappings source,
                                    MetadataPass metadataPass, BuildGradlePass buildGradlePass,
                                    JavaSourcePass javaPass, LangPass langPass,
                                    AssetJsonPass assetPass) throws IOException {
        if (isMetadataFile(rel, name, source)) {
            return metadataPass.generate(rel); // 内容已在第一阶段解析
        }
        if (name.equals("build.gradle")) {
            OutputFile generated = buildGradlePass.generate(rel);
            return generated != null ? generated : copyVerbatim(rel, file, ctx);
        }
        if (name.endsWith(".java")) {
            String out = javaPass.transform(rel, Files.readString(file, StandardCharsets.UTF_8));
            return new OutputFile(rel, out.getBytes(StandardCharsets.UTF_8));
        }
        if (isUnder(rel, "lang") && (name.endsWith(".lang") || name.endsWith(".json"))) {
            OutputFile out = langPass.transform(rel, Files.readString(file, StandardCharsets.UTF_8));
            return out != null ? out : copyVerbatim(rel, file, ctx);
        }
        if (isUnder(rel, "blockstates") && name.endsWith(".json")) {
            OutputFile out = assetPass.transformBlockstate(rel, Files.readString(file, StandardCharsets.UTF_8));
            return out != null ? out : copyVerbatim(rel, file, ctx);
        }
        if (isUnder(rel, "models") && name.endsWith(".json")) {
            OutputFile out = assetPass.transformModel(rel, Files.readString(file, StandardCharsets.UTF_8));
            return out != null ? out : copyVerbatim(rel, file, ctx);
        }
        if (name.equals("gradle-wrapper.properties")) {
            String gradleVersion = ctx.target().info.gradleVersion;
            if (gradleVersion != null && !gradleVersion.isBlank()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String rewritten = content.replaceAll(
                        "(?m)^distributionUrl=.*$",
                        "distributionUrl=https\\\\://services.gradle.org/distributions/gradle-"
                                + gradleVersion + "-bin.zip");
                ctx.info(rel, null, "build-script",
                        "Gradle wrapper 已改为目标版本 ForgeGradle 兼容的 Gradle " + gradleVersion
                                + "（gradle-wrapper.jar/gradlew 脚本原样保留，建议之后执行 gradle wrapper 刷新）");
                return new OutputFile(rel, rewritten.getBytes(StandardCharsets.UTF_8));
            }
            ctx.todo(rel, null, "build-script",
                    "Gradle wrapper 原样复制，其版本可能与目标版本 ForgeGradle 不兼容，请按目标版本 MDK 更新");
            return copyVerbatim(rel, file, ctx);
        }
        if (name.equals("pack.mcmeta")) {
            OutputFile out = assetPass.transformPackMcmeta(rel, Files.readString(file, StandardCharsets.UTF_8));
            return out != null ? out : copyVerbatim(rel, file, ctx);
        }
        return copyVerbatim(rel, file, ctx);
    }

    /** 判断相对路径是否位于 assets/<ns>/<dirName>/ 之下。 */
    private static boolean isUnder(String rel, String dirName) {
        return rel.contains("/assets/") && rel.contains("/" + dirName + "/")
                || rel.startsWith("assets/") && rel.contains("/" + dirName + "/");
    }

    private static boolean isMetadataFile(String rel, String name, VersionMappings source) {
        if (rel.equals(source.info.metadataPath)) return true;
        return name.equals("mcmod.info") || name.equals("mods.toml");
    }

    private static OutputFile copyVerbatim(String rel, Path file, PortContext ctx) {
        try {
            return new OutputFile(rel, Files.readAllBytes(file));
        } catch (IOException e) {
            ctx.error(rel, null, "io", "读取失败: " + e.getMessage());
            return null;
        }
    }

    private static String relPath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isSkipped(Path relative) {
        for (Path segment : relative) {
            if (SKIP_DIRS.contains(segment.toString())) return true;
        }
        return false;
    }

    // ---- TODO 清单 ----

    public static final String TODO_REPORT_NAME = "MODPORTER-TODOS.md";

    /** 能出现注释 TODO 的文本文件类型。 */
    private static final Set<String> TODO_SCAN_EXTENSIONS = Set.of(".java", ".gradle", ".toml", ".cfg", ".mcmeta");

    /** 匹配注释中的 TODO/FIXME（// … todo、/* … todo、块注释续行 * … todo、# … todo）。 */
    private static final java.util.regex.Pattern TODO_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(?://|/\\*|^\\s*\\*|#).*\\b(todo|fixme)\\b");

    /**
     * 扫描转换后工程的全部文本产物，把所有注释 TODO（包括转换器插入的 TODO [modporter]
     * 和模组作者原有的 TODO）连同文件与行号写进独立的 TODO 清单文件。返回 TODO 总数。
     */
    private int writeTodoReport(Path outputRoot, List<OutputFile> outputs) throws IOException {
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        int total = 0;
        for (OutputFile out : outputs) {
            String name = out.relativePath.toLowerCase();
            if (TODO_SCAN_EXTENSIONS.stream().noneMatch(name::endsWith)) continue;
            String text = new String(out.content, StandardCharsets.UTF_8);
            String[] lines = text.split("\r?\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (TODO_PATTERN.matcher(lines[i]).find()) {
                    byFile.computeIfAbsent(out.relativePath, k -> new ArrayList<>())
                            .add("- 第 " + (i + 1) + " 行: `" + lines[i].trim() + "`");
                    total++;
                }
            }
        }
        if (total == 0) return 0;

        StringBuilder sb = new StringBuilder();
        sb.append("# TODO 清单\n\n");
        sb.append("转换后的工程中共有 ").append(total).append(" 处 TODO/FIXME，来自 ")
                .append(byFile.size()).append(" 个文件。")
                .append("其中 `TODO [modporter]` 为转换器插入的待人工迁移项，其余为源码中原有的 TODO。\n\n");
        for (Map.Entry<String, List<String>> e : byFile.entrySet()) {
            sb.append("## ").append(e.getKey()).append("\n\n");
            e.getValue().forEach(line -> sb.append(line).append('\n'));
            sb.append('\n');
        }
        Files.writeString(outputRoot.resolve(TODO_REPORT_NAME), sb.toString(), StandardCharsets.UTF_8);
        return total;
    }

    // ---- 报告输出 ----

    private void writeReports(Path outputRoot, Report report) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(outputRoot.resolve("modporter-report.json"),
                gson.toJson(new ReportJson(report)), StandardCharsets.UTF_8);
        Files.writeString(outputRoot.resolve("MODPORTER-REPORT.md"),
                renderMarkdown(report), StandardCharsets.UTF_8);
    }

    /** 报告的 JSON 视图（供未来 UI 直接消费）。 */
    private static final class ReportJson {
        final String sourceVersion;
        final String targetVersion;
        final long infoCount;
        final long warnCount;
        final long todoCount;
        final long errorCount;
        final List<Report.Entry> entries;

        ReportJson(Report report) {
            this.sourceVersion = report.sourceVersion();
            this.targetVersion = report.targetVersion();
            this.infoCount = report.count(Report.Severity.INFO);
            this.warnCount = report.count(Report.Severity.WARN);
            this.todoCount = report.count(Report.Severity.TODO);
            this.errorCount = report.count(Report.Severity.ERROR);
            this.entries = report.entries();
        }
    }

    private static String renderMarkdown(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ModPorter 转换报告\n\n");
        sb.append("- 源版本: ").append(report.sourceVersion()).append('\n');
        sb.append("- 目标版本: ").append(report.targetVersion()).append('\n');
        sb.append("- 自动改写: ").append(report.count(Report.Severity.INFO)).append(" 处\n");
        sb.append("- 警告: ").append(report.count(Report.Severity.WARN)).append(" 处\n");
        sb.append("- 待人工处理 (TODO): ").append(report.count(Report.Severity.TODO)).append(" 处\n");
        sb.append("- 错误: ").append(report.count(Report.Severity.ERROR)).append(" 处\n\n");
        appendSection(sb, report, Report.Severity.ERROR, "## 错误");
        appendSection(sb, report, Report.Severity.TODO, "## 待人工处理");
        appendSection(sb, report, Report.Severity.WARN, "## 警告");
        appendSection(sb, report, Report.Severity.INFO, "## 自动改写明细");
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, Report report, Report.Severity severity, String title) {
        List<Report.Entry> entries = report.entries().stream()
                .filter(e -> e.severity == severity)
                .collect(Collectors.toList());
        if (entries.isEmpty()) return;
        sb.append(title).append("\n\n");
        for (Report.Entry e : entries) {
            sb.append("- ");
            if (e.file != null) {
                sb.append('`').append(e.file);
                if (e.line != null) sb.append(':').append(e.line);
                sb.append("` ");
            }
            sb.append('[').append(e.category).append("] ").append(e.message).append('\n');
        }
        sb.append('\n');
    }
}
