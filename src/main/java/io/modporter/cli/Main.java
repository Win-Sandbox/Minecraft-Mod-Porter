package io.modporter.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modporter.core.PortEngine;
import io.modporter.core.PortRequest;
import io.modporter.core.PortResult;
import io.modporter.core.ProgressListener;
import io.modporter.core.Report;
import io.modporter.engine.DefaultPortEngine;
import io.modporter.mappings.MappingRepository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI 入口，同时是前端（WinUI 等）对接的机器可读协议层：
 *  - capabilities --json : 后端能力清单（加载器/版本/动作），前端据此动态渲染
 *  - versions [--json]   : 版本列表
 *  - port [--progress-json] : 转换（NDJSON 进度流）
 *  - run <actionId>      : 通用动作入口，后端新增功能无需前端改动
 */
@Command(name = "modporter",
        mixinStandardHelpOptions = true,
        version = "modporter 0.1.0",
        description = "Minecraft 模组源码跨版本转换器（数据驱动，IR/pivot 架构）",
        subcommands = {
                Main.PortCommand.class,
                Main.VersionsCommand.class,
                Main.CapabilitiesCommand.class,
                Main.RunCommand.class
        })
public final class Main implements Callable<Integer> {

    static final String BACKEND_VERSION = "0.1.0";
    static final int PROTOCOL_VERSION = 1;
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    static Path resolveMappingsDir(Path option) {
        if (option != null) return option;
        String env = System.getenv("MODPORTER_MAPPINGS");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of("mappings");
    }

    /** 按加载器分组的版本清单。 */
    static Map<String, List<String>> groupedVersions(PortEngine engine) {
        Map<String, List<String>> byLoader = new LinkedHashMap<>();
        for (PortEngine.VersionDescriptor v : engine.supportedVersions()) {
            byLoader.computeIfAbsent(v.loader(), k -> new java.util.ArrayList<>()).add(v.mcVersion());
        }
        return byLoader;
    }

    // ================= versions =================

    @Command(name = "versions", description = "列出映射数据目录中支持的所有版本")
    static final class VersionsCommand implements Callable<Integer> {

        @Option(names = {"-m", "--mappings"}, description = "映射数据根目录（默认 ./mappings 或环境变量 MODPORTER_MAPPINGS）")
        Path mappingsDir;

        @Option(names = {"--json"}, description = "以 JSON 输出")
        boolean json;

        @Override
        public Integer call() {
            PortEngine engine = new DefaultPortEngine(new MappingRepository(resolveMappingsDir(mappingsDir)));
            Map<String, List<String>> byLoader = groupedVersions(engine);
            if (json) {
                System.out.println(GSON.toJson(byLoader));
                return byLoader.isEmpty() ? 1 : 0;
            }
            if (byLoader.isEmpty()) {
                System.err.println("未找到任何版本映射数据，请检查 --mappings 目录");
                return 1;
            }
            System.out.println("支持的版本:");
            byLoader.forEach((loader, versions) ->
                    versions.forEach(v -> System.out.printf("  %-8s %s%n", loader, v)));
            return 0;
        }
    }

    // ================= capabilities =================

    /**
     * 后端能力清单。actions 中 available=false 的条目是「已规划、后端尚未实现」的入口，
     * 前端渲染为禁用项；后端实现后把 available 改为 true（或经 mappings/actions.json 覆盖），
     * 前端零改动即可启用。
     */
    @Command(name = "capabilities", description = "输出后端能力清单 JSON（供前端动态渲染）")
    static final class CapabilitiesCommand implements Callable<Integer> {

        @Option(names = {"-m", "--mappings"}, description = "映射数据根目录")
        Path mappingsDir;

        @Override
        public Integer call() {
            Path root = resolveMappingsDir(mappingsDir);
            PortEngine engine = new DefaultPortEngine(new MappingRepository(root));

            JsonObject out = new JsonObject();
            out.addProperty("schemaVersion", PROTOCOL_VERSION);
            JsonObject backend = new JsonObject();
            backend.addProperty("name", "modporter");
            backend.addProperty("version", BACKEND_VERSION);
            out.add("backend", backend);

            JsonArray loaders = new JsonArray();
            groupedVersions(engine).forEach((loader, versions) -> {
                JsonObject l = new JsonObject();
                l.addProperty("id", loader);
                JsonArray vs = new JsonArray();
                versions.forEach(vs::add);
                l.add("versions", vs);
                loaders.add(l);
            });
            out.add("loaders", loaders);

            JsonArray actions = defaultActions();
            mergeExternalActions(actions, root.resolve("actions.json"));
            out.add("actions", actions);

            System.out.println(GSON_PRETTY.toJson(out));
            return 0;
        }

        /** 内置动作 + 已规划未实现的预留入口。 */
        private static JsonArray defaultActions() {
            JsonArray actions = new JsonArray();
            actions.add(action("port", "版本转换",
                    "将模组源码工程从一个 MC 版本转换到另一个版本（升级或降级）", true, true,
                    param("loader", "enum:loaders", "加载器", true),
                    param("from", "enum:versions", "源版本", true),
                    param("to", "enum:versions", "目标版本", true),
                    param("input", "path", "输入工程目录", true),
                    param("output", "path", "输出工程目录", true),
                    param("dryRun", "bool", "只分析不写出", false)));
            actions.add(action("crossloader-port", "跨加载器转换",
                    "在不同加载器之间转换模组源码（Forge/Fabric/Quilt/NeoForge）", false, false,
                    param("fromLoader", "enum:loaders", "源加载器", true),
                    param("toLoader", "enum:loaders", "目标加载器", true),
                    param("from", "enum:versions", "源版本", true),
                    param("to", "enum:versions", "目标版本", true),
                    param("input", "path", "输入工程目录", true),
                    param("output", "path", "输出工程目录", true)));
            actions.add(action("batch-port", "批量转换",
                    "一次转换多个模组工程，或把一个工程同时输出为多个目标版本", false, false,
                    param("inputs", "pathList", "输入工程目录列表", true),
                    param("to", "enum:versions", "目标版本", true),
                    param("outputRoot", "path", "输出根目录", true)));
            actions.add(action("mappings-import", "映射数据包导入/更新",
                    "从文件或 URL 导入新的版本映射数据包，扩充可转换的版本", false, false,
                    param("source", "path", "映射数据包路径", true)));
            return actions;
        }

        static JsonObject action(String id, String title, String description,
                                 boolean available, boolean builtin, JsonObject... params) {
            JsonObject a = new JsonObject();
            a.addProperty("id", id);
            a.addProperty("title", title);
            a.addProperty("description", description);
            a.addProperty("available", available);
            a.addProperty("builtin", builtin);
            JsonArray ps = new JsonArray();
            for (JsonObject p : params) ps.add(p);
            a.add("params", ps);
            return a;
        }

        static JsonObject param(String id, String type, String title, boolean required) {
            JsonObject p = new JsonObject();
            p.addProperty("id", id);
            p.addProperty("type", type);
            p.addProperty("title", title);
            p.addProperty("required", required);
            return p;
        }

        /** mappings/actions.json 可追加/覆盖动作（按 id 匹配覆盖），后端功能可随数据包分发。 */
        private static void mergeExternalActions(JsonArray actions, Path file) {
            if (!Files.isRegularFile(file)) return;
            try {
                JsonArray external = JsonParser
                        .parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonArray();
                for (var e : external) {
                    JsonObject ext = e.getAsJsonObject();
                    String id = ext.get("id").getAsString();
                    int existing = -1;
                    for (int i = 0; i < actions.size(); i++) {
                        if (actions.get(i).getAsJsonObject().get("id").getAsString().equals(id)) {
                            existing = i;
                            break;
                        }
                    }
                    if (existing >= 0) {
                        actions.set(existing, ext);
                    } else {
                        actions.add(ext);
                    }
                }
            } catch (Exception ex) {
                // 外部 actions.json 损坏时忽略，不影响内置能力
            }
        }
    }

    // ================= port =================

    @Command(name = "port", description = "将模组源码工程从一个版本转换到另一个版本")
    static final class PortCommand implements Callable<Integer> {

        @Option(names = {"-i", "--input"}, required = true, description = "输入工程根目录")
        Path input;

        @Option(names = {"-o", "--output"}, required = true, description = "输出工程根目录")
        Path output;

        @Option(names = {"-f", "--from"}, required = true, description = "源 MC 版本，如 1.12.2")
        String from;

        @Option(names = {"-t", "--to"}, required = true, description = "目标 MC 版本，如 1.19.2")
        String to;

        @Option(names = {"-l", "--loader"}, defaultValue = "forge", description = "加载器（默认 forge）")
        String loader;

        @Option(names = {"-m", "--mappings"}, description = "映射数据根目录")
        Path mappingsDir;

        @Option(names = {"--dry-run"}, description = "只分析并打印报告，不写出任何文件")
        boolean dryRun;

        @Option(names = {"-q", "--quiet"}, description = "不打印逐文件进度")
        boolean quiet;

        @Option(names = {"--progress-json"}, description = "以 NDJSON 逐行输出进度与结果（供前端解析）")
        boolean progressJson;

        @Override
        public Integer call() {
            if (!Files.isDirectory(input)) {
                return fail("输入目录不存在: " + input);
            }
            if (!dryRun && output.toAbsolutePath().normalize()
                    .startsWith(input.toAbsolutePath().normalize())) {
                return fail("输出目录不能位于输入目录内部");
            }

            PortEngine engine = new DefaultPortEngine(new MappingRepository(resolveMappingsDir(mappingsDir)));
            PortRequest request = new PortRequest(input, output, loader, from, to, dryRun);

            if (progressJson) {
                emit(json("type", "begin", "from", from, "to", to, "loader", loader));
            }

            ProgressListener listener = new ProgressListener() {
                @Override
                public void onFileStart(String relativePath, int index, int total) {
                    if (progressJson) {
                        JsonObject o = json("type", "file", "path", relativePath);
                        o.addProperty("index", index);
                        o.addProperty("total", total);
                        emit(o);
                    }
                }

                @Override
                public void onFileDone(String relativePath, int todoCount) {
                    if (progressJson) {
                        JsonObject o = json("type", "fileDone", "path", relativePath);
                        o.addProperty("todos", todoCount);
                        emit(o);
                    } else if (!quiet) {
                        System.out.println("  " + relativePath
                                + (todoCount > 0 ? "   [TODO x" + todoCount + "]" : ""));
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (progressJson) {
                        emit(json("type", "message", "text", message));
                    } else {
                        System.out.println(message);
                    }
                }
            };

            PortResult result = engine.port(request, listener);
            Report report = result.report();

            if (progressJson) {
                JsonObject o = new JsonObject();
                o.addProperty("type", "result");
                o.addProperty("status", result.status().name());
                o.addProperty("info", report.count(Report.Severity.INFO));
                o.addProperty("warn", report.count(Report.Severity.WARN));
                o.addProperty("todo", report.count(Report.Severity.TODO));
                o.addProperty("error", report.count(Report.Severity.ERROR));
                if (!dryRun) {
                    o.addProperty("reportJson", output.resolve("modporter-report.json").toString());
                    o.addProperty("reportMd", output.resolve("MODPORTER-REPORT.md").toString());
                    Path todoReport = output.resolve(io.modporter.engine.DefaultPortEngine.TODO_REPORT_NAME);
                    if (Files.isRegularFile(todoReport)) {
                        o.addProperty("todoReport", todoReport.toString());
                    }
                }
                emit(o);
            } else {
                System.out.println();
                System.out.println("==== 转换完成: " + from + " -> " + to + " (" + loader + ") ====");
                System.out.println("自动改写: " + report.count(Report.Severity.INFO) + " 处");
                System.out.println("警告:     " + report.count(Report.Severity.WARN) + " 处");
                System.out.println("待人工:   " + report.count(Report.Severity.TODO) + " 处");
                System.out.println("错误:     " + report.count(Report.Severity.ERROR) + " 处");
                if (!dryRun && result.status() != PortResult.Status.FAILED) {
                    System.out.println();
                    System.out.println("报告: " + output.resolve("MODPORTER-REPORT.md"));
                    Path todoReport = output.resolve(io.modporter.engine.DefaultPortEngine.TODO_REPORT_NAME);
                    if (Files.isRegularFile(todoReport)) {
                        System.out.println("TODO 清单: " + todoReport);
                    }
                }
            }

            if (result.status() == PortResult.Status.FAILED) {
                if (!progressJson) {
                    report.entries().stream()
                            .filter(e -> e.severity == Report.Severity.ERROR)
                            .forEach(e -> System.err.println("[ERROR] " + e.message));
                }
                return 2;
            }
            return 0;
        }

        private int fail(String message) {
            if (progressJson) {
                emit(json("type", "fatal", "text", message));
            } else {
                System.err.println(message);
            }
            return 1;
        }

        private static JsonObject json(String... kv) {
            JsonObject o = new JsonObject();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                o.addProperty(kv[i], kv[i + 1]);
            }
            return o;
        }

        private static void emit(JsonObject o) {
            System.out.println(GSON.toJson(o));
            System.out.flush();
        }
    }

    // ================= run（通用动作入口） =================

    /**
     * 通用动作入口：前端对非内置动作一律调用 run <actionId> --params <json>。
     * 后端未来实现新动作时在这里分发即可，前端无需改动。
     */
    @Command(name = "run", description = "以通用参数调用一个后端动作（预留扩展入口）")
    static final class RunCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "动作 id（见 capabilities 输出）")
        String actionId;

        @Option(names = {"--params"}, description = "动作参数（JSON 字符串或 JSON 文件路径）")
        String params;

        @Option(names = {"-m", "--mappings"}, description = "映射数据根目录")
        Path mappingsDir;

        @Override
        public Integer call() throws Exception {
            JsonObject p = parseParams();
            switch (actionId) {
                case "port" -> {
                    PortCommand port = new PortCommand();
                    port.loader = str(p, "loader", "forge");
                    port.from = str(p, "from", null);
                    port.to = str(p, "to", null);
                    port.input = p.has("input") ? Path.of(p.get("input").getAsString()) : null;
                    port.output = p.has("output") ? Path.of(p.get("output").getAsString()) : null;
                    port.dryRun = p.has("dryRun") && p.get("dryRun").getAsBoolean();
                    port.mappingsDir = mappingsDir;
                    port.progressJson = true;
                    if (port.from == null || port.to == null || port.input == null || port.output == null) {
                        System.out.println(GSON.toJson(error("missing-params",
                                "port 需要 from/to/input/output 参数")));
                        return 1;
                    }
                    return port.call();
                }
                default -> {
                    System.out.println(GSON.toJson(error("unknown-action",
                            "后端尚未实现动作: " + actionId + "（见 capabilities 中 available 字段）")));
                    return 3;
                }
            }
        }

        private JsonObject parseParams() throws Exception {
            if (params == null || params.isBlank()) return new JsonObject();
            String text = params.trim();
            if (!text.startsWith("{")) {
                text = Files.readString(Path.of(text), StandardCharsets.UTF_8);
            }
            return JsonParser.parseString(text).getAsJsonObject();
        }

        private static String str(JsonObject o, String key, String def) {
            return o.has(key) ? o.get(key).getAsString() : def;
        }

        private static JsonObject error(String code, String message) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "fatal");
            o.addProperty("code", code);
            o.addProperty("text", message);
            return o;
        }
    }
}
