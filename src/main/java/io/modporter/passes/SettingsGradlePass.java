package io.modporter.passes;

import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * settings.gradle 处理：
 * - 目标版本 templates/settings.gradle 允许缺失（缺失时不生成新文件）。
 * - 已有 settings.gradle 绝不整体模板化覆盖，只在目标 build.gradle 明确需要
 *   NeoGradle 插件仓库时，安全插入缺失的 pluginManagement 仓库；无法安全插入则保留 + TODO。
 * - 已有 settings.gradle.kts 时不生成相冲突的 Groovy 文件。
 * - 缺失 settings.gradle 时，按 build.gradle 所在目录生成（多模块时保守处理并 TODO）。
 */
public final class SettingsGradlePass {

    private static final String SETTINGS_GRADLE = "settings.gradle";
    private static final String NEOFORGED_REPO_URL = "https://maven.neoforged.net/releases";
    private static final String PLUGIN_MANAGEMENT_PREFIX =
            "pluginManagement {\n"
                    + "    repositories {\n"
                    + "        gradlePluginPortal()\n"
                    + "        maven { url = '" + NEOFORGED_REPO_URL + "' }\n"
                    + "    }\n"
                    + "}\n\n";

    private final PortContext ctx;
    private final MetadataPass metadataPass;
    private final Set<String> buildGradlePaths = new LinkedHashSet<>();
    private final Set<String> settingsGradlePaths = new LinkedHashSet<>();
    private final Set<String> settingsGradleKtsPaths = new LinkedHashSet<>();

    public SettingsGradlePass(PortContext ctx, MetadataPass metadataPass) {
        this.ctx = ctx;
        this.metadataPass = metadataPass;
    }

    /** 记录 build.gradle 的相对路径，作为缺失 settings.gradle 时的生成锚点。 */
    public void noteBuildGradle(String relPath) {
        if (relPath != null && !relPath.isBlank()) buildGradlePaths.add(relPath);
    }

    /** 记录 settings.gradle.kts；存在 Kotlin DSL 时绝不生成冲突的 Groovy 文件。 */
    public void noteSettingsGradleKts(String relPath) {
        if (relPath == null || relPath.isBlank()) return;
        settingsGradleKtsPaths.add(relPath);
        RepositoryRequirement requirement = repositoryRequirement();
        if (requirement == RepositoryRequirement.REQUIRED) {
            ctx.todo(relPath, null, "build-script",
                    "检测到 settings.gradle.kts，为避免 Groovy/Kotlin DSL 冲突未自动生成 settings.gradle；"
                            + "目标版本需要 NeoGradle 插件仓库，请在 Kotlin DSL 中手动加入 "
                            + NEOFORGED_REPO_URL);
        } else if (requirement == RepositoryRequirement.UNKNOWN) {
            ctx.todo(relPath, null, "build-script",
                    "检测到 settings.gradle.kts，为避免 Groovy/Kotlin DSL 冲突未自动生成 settings.gradle；"
                            + "目标版本缺少 templates/settings.gradle，无法确认是否需要 NeoGradle 插件仓库，请人工核对");
        }
    }

    /**
     * 迁移已有 settings.gradle。
     * 模板明确不需要仓库或非 NeoForge：原样保留（INFO）。
     * NeoForge 但模板缺失（UNKNOWN）：原样保留并 TODO，不猜测插入。
     * 模板明确需要仓库：只插入缺失仓库，不覆盖任何自定义逻辑。
     */
    public OutputFile transformExisting(String relPath, String content) {
        if (relPath != null && !relPath.isBlank()) settingsGradlePaths.add(relPath);

        RepositoryRequirement requirement = repositoryRequirement();
        if (requirement == RepositoryRequirement.NOT_REQUIRED) {
            ctx.info(relPath, null, "build-script",
                    "settings.gradle 原样保留（目标版本插件可从 Gradle Plugin Portal 解析，无需 NeoGradle 仓库）");
            return new OutputFile(relPath, content.getBytes(StandardCharsets.UTF_8));
        }
        if (requirement == RepositoryRequirement.UNKNOWN) {
            ctx.todo(relPath, null, "build-script",
                    "NeoForge 目标缺少 templates/settings.gradle，无法确认是否需要 NeoGradle 插件仓库；"
                            + "settings.gradle 已原样保留，请人工核对是否需要 "
                            + NEOFORGED_REPO_URL);
            return new OutputFile(relPath, content.getBytes(StandardCharsets.UTF_8));
        }
        String updated = insertNeoGradleRepository(content);
        if (updated == null) {
            ctx.todo(relPath, null, "build-script",
                    "settings.gradle 需要 pluginManagement 仓库 " + NEOFORGED_REPO_URL
                            + "，但无法安全插入（结构不明确），已原样保留，请手动添加");
            return new OutputFile(relPath, content.getBytes(StandardCharsets.UTF_8));
        }
        ctx.info(relPath, null, "build-script",
                "settings.gradle 已插入缺失的 NeoGradle 插件仓库；其余自定义逻辑原样保留");
        return new OutputFile(relPath, updated.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 若没有 settings.gradle / settings.gradle.kts，则按模板生成 settings.gradle。
     * 返回 null 表示不生成（模板缺失、DSL 冲突或多模块无法确定锚点等）。
     */
    public OutputFile generateMissing() {
        if (!settingsGradlePaths.isEmpty()) return null;

        if (!settingsGradleKtsPaths.isEmpty()) {
            // noteSettingsGradleKts 已在需要仓库时记录 TODO；这里只说明不生成。
            ctx.info(null, null, "build-script",
                    "检测到 settings.gradle.kts，未生成 settings.gradle，避免 Groovy/Kotlin DSL 冲突");
            return null;
        }

        String anchor = uniqueBuildGradleAnchor();
        if (anchor == null) {
            if (!buildGradlePaths.isEmpty()) {
                ctx.todo(null, null, "build-script",
                        "发现多个 build.gradle，无法唯一确定 settings.gradle 的生成目录，未生成（多模块请人工放置）");
            } else {
                ctx.info(null, null, "build-script",
                        "未发现 build.gradle 锚点，未生成 settings.gradle");
            }
            return null;
        }

        String template = metadataPass.readTemplate(SETTINGS_GRADLE);
        if (template == null) {
            RepositoryRequirement requirement = repositoryRequirement();
            if (requirement == RepositoryRequirement.NOT_REQUIRED) {
                ctx.info(anchor, null, "build-script",
                        "目标版本无 settings.gradle 模板，按约定不生成（允许缺失）");
            } else if (requirement == RepositoryRequirement.UNKNOWN) {
                ctx.todo(anchor, null, "build-script",
                        "目标版本缺少 templates/settings.gradle，无法确认是否需要 NeoGradle 插件仓库，未生成；请人工核对");
            }
            return null;
        }

        String rendered = metadataPass.renderTemplate(template);
        String parent = parentDirectory(anchor);
        String outPath = parent.isEmpty() ? SETTINGS_GRADLE : parent + "/" + SETTINGS_GRADLE;
        ctx.info(anchor, null, "build-script", "已生成 " + outPath + "（与 build.gradle 同目录）");
        return new OutputFile(outPath, rendered.getBytes(StandardCharsets.UTF_8));
    }

    /** 唯一锚点：多个 build.gradle 时取最浅层；同层并列多个则放弃并返回 null。 */
    private String uniqueBuildGradleAnchor() {
        if (buildGradlePaths.isEmpty()) return null;
        if (buildGradlePaths.size() == 1) return buildGradlePaths.iterator().next();

        int minDepth = Integer.MAX_VALUE;
        for (String p : buildGradlePaths) minDepth = Math.min(minDepth, depth(p));
        String candidate = null;
        int count = 0;
        for (String p : buildGradlePaths) {
            if (depth(p) == minDepth) {
                candidate = p;
                count++;
            }
        }
        return count == 1 ? candidate : null;
    }

    private static int depth(String relPath) {
        int count = 0;
        for (int i = 0; i < relPath.length(); i++) {
            if (relPath.charAt(i) == '/') count++;
        }
        return count;
    }

    private static String parentDirectory(String relPath) {
        int idx = relPath.lastIndexOf('/');
        return idx < 0 ? "" : relPath.substring(0, idx);
    }

    /**
     * 目标对 NeoGradle 插件仓库的需求状态。
     * 判定依据是目标 settings.gradle 模板中 pluginManagement/repositories 的
     * NeoGradle Maven 仓库声明（1.20.1/1.20.2/1.20.3 明确需要；1.20.4+ 的
     * ModDevGradle 只用 Plugin Portal，不需要）。
     * 不从 build.gradle 的说明注释猜测需求。
     */
    private enum RepositoryRequirement {
        /** 非 NeoForge，或模板明确声明不需要额外仓库。 */
        NOT_REQUIRED,
        /** 目标 settings 模板明确声明 NeoGradle Maven 仓库。 */
        REQUIRED,
        /** NeoForge 但目标 settings 模板缺失，需求无法证实。 */
        UNKNOWN
    }

    private RepositoryRequirement repositoryRequirement() {
        if (!"neoforge".equals(ctx.target().info.loader)) return RepositoryRequirement.NOT_REQUIRED;
        String settingsTemplate = metadataPass.readTemplate(SETTINGS_GRADLE);
        if (settingsTemplate == null) return RepositoryRequirement.UNKNOWN;
        return containsNeoGradleRepository(settingsTemplate)
                ? RepositoryRequirement.REQUIRED
                : RepositoryRequirement.NOT_REQUIRED;
    }

    /**
     * 判断文本中是否存在声明目标 NeoGradle 仓库的代码：
     * URL 必须作为 maven 仓库 URL 字面量出现，而不是任意注释或普通字符串。
     * 支持常见 Groovy 写法：maven { url = '...' / "..." }、url('...')、url("...")、
     * maven('...') / maven("...")、以及 legacy mavenRepo urls: '...'。
     */
    private static boolean containsNeoGradleRepository(String s) {
        if (s == null) return false;
        String url = Pattern.quote(NEOFORGED_REPO_URL);
        String single = "'" + url + "'";
        String dbl = "\"" + url + "\"";
        String repoLiteral = "(?:" + single + "|" + dbl + ")";
        // 与仓库声明相关的 URL 使用形式；其它普通字符串中的 URL 不触发。
        String[] patterns = {
                "maven\\s*\\{[^{}]*url\\s*=\\s*" + repoLiteral,
                "\\burl\\s*\\(\\s*" + repoLiteral,
                "\\bmaven\\s*\\(\\s*" + repoLiteral,
                "\\bmavenRepo\\s*\\{?\\s*urls?\\s*:?\\s*" + repoLiteral
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.DOTALL).matcher(s);
            while (m.find()) {
                if (!isInsideComment(s, m.start())) return true;
            }
        }
        return false;
    }

    /**
     * 判断文本中是否存在 gradlePluginPortal() 调用；注释与字符串中的调用不算。
     */
    private static boolean hasGradlePluginPortalCall(String s) {
        if (s == null) return false;
        Matcher m = Pattern.compile("(?m)^[ \\t]*gradlePluginPortal\\s*\\(\\s*\\)").matcher(s);
        while (m.find()) {
            if (!isInsideComment(s, m.start())) return true;
        }
        return false;
    }

    /**
     * 判断 index 位置是否位于行注释或块注释中；字符串内容不算注释。
     * 用于排除注释中的仓库写法。调用方传入的 start 位于匹配串起点（如 maven/url 关键字处）。
     */
    private static boolean isInsideComment(String s, int start) {
        if (s == null || start <= 0) return false;
        boolean single = false, dbl = false, lineComment = false, blockComment = false;
        for (int i = 0; i < start; i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (single) {
                if (c == '\\') i++;
                else if (c == '\'') single = false;
                continue;
            }
            if (dbl) {
                if (c == '\\') i++;
                else if (c == '"') dbl = false;
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'') single = true;
            else if (c == '"') dbl = true;
        }
        return lineComment || blockComment;
    }

    /** 只插入缺失仓库；结构不明确时返回 null（由调用方保留原文 + TODO）。 */
    private static String insertNeoGradleRepository(String content) {
        if (content == null || content.isBlank()) return PLUGIN_MANAGEMENT_PREFIX;

        Matcher pm = Pattern.compile("(?m)^(\\s*)pluginManagement\\s*\\{").matcher(content);
        if (!pm.find()) {
            return PLUGIN_MANAGEMENT_PREFIX + content;
        }
        int pmOpen = pm.end() - 1; // 指向 pluginManagement 的 '{'
        int pmClose = matchBrace(content, pmOpen);
        if (pmClose < 0) return null;

        String inner = content.substring(pmOpen + 1, pmClose);
        Matcher repos = Pattern.compile("(?m)^(\\s*)repositories\\s*\\{").matcher(inner);
        if (!repos.find()) {
            // 已有 pluginManagement 但无 repositories：两项都缺失，一次补齐 Portal 与目标 Maven。
            String insert = "\n" + pm.group(1) + "    repositories {\n"
                    + pm.group(1) + "        gradlePluginPortal()\n"
                    + pm.group(1) + "        maven { url = '" + NEOFORGED_REPO_URL + "' }\n"
                    + pm.group(1) + "    }\n";
            return content.substring(0, pmOpen + 1) + insert + content.substring(pmOpen + 1);
        }

        int reposOpenInInner = repos.end() - 1;
        int reposCloseInInner = matchBrace(inner, reposOpenInInner);
        if (reposCloseInInner < 0) return null;
        // 只在 repositories 块自身中判断已有仓库：URL 必须作为 maven 仓库 URL 字面量出现，
        // 注释、普通字符串或 pluginManagement 其他层级中的 URL/Portal 不算已存在。
        String reposBody = inner.substring(reposOpenInInner + 1, reposCloseInInner);
        boolean hasNeo = containsNeoGradleRepository(reposBody);
        boolean hasPortal = hasGradlePluginPortalCall(reposBody);
        if (hasNeo && hasPortal) return content;
        StringBuilder additions = new StringBuilder();
        if (!hasPortal) additions.append("\n").append(repos.group(1)).append("    gradlePluginPortal()");
        if (!hasNeo) additions.append("\n").append(repos.group(1)).append("    maven { url = '").append(NEOFORGED_REPO_URL).append("' }");
        String insert = additions.toString();
        int globalOpen = pmOpen + 1 + reposOpenInInner;
        return content.substring(0, globalOpen + 1) + insert + content.substring(globalOpen + 1);
    }

    /** 跨字符串/注释的括号匹配；不匹配返回 -1。 */
    private static int matchBrace(String s, int openIndex) {
        if (s == null || openIndex < 0 || openIndex >= s.length() || s.charAt(openIndex) != '{') return -1;
        int depth = 0;
        boolean single = false, dbl = false, lineComment = false, blockComment = false;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (single) {
                if (c == '\\') i++;
                else if (c == '\'') single = false;
                continue;
            }
            if (dbl) {
                if (c == '\\') i++;
                else if (c == '"') dbl = false;
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'') single = true;
            else if (c == '"') dbl = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
