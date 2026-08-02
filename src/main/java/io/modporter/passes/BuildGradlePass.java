package io.modporter.passes;

import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 构建脚本处理：不做逐行改写，而是从目标版本模板重新生成 build.gradle，
 * 只从原脚本中提取 group / archivesBaseName 等工程标识。
 */
public final class BuildGradlePass {

    private final PortContext ctx;
    private final MetadataPass metadataPass;

    public BuildGradlePass(PortContext ctx, MetadataPass metadataPass) {
        this.ctx = ctx;
        this.metadataPass = metadataPass;
    }

    /** 从原 build.gradle 提取工程标识，填入 ModMeta。 */
    public void parse(String content) {
        Matcher group = Pattern.compile("(?m)^\\s*group\\s*=?\\s*['\"]([^'\"]+)['\"]").matcher(content);
        if (group.find()) {
            ctx.modMeta.group = group.group(1);
        }
        Matcher version = Pattern.compile("(?m)^\\s*version\\s*=?\\s*['\"]([^'\"]+)['\"]").matcher(content);
        if (version.find() && ctx.modMeta.version == null) {
            ctx.modMeta.version = version.group(1);
        }
    }

    public OutputFile generate(String relPath) {
        String template = metadataPass.readTemplate("build.gradle");
        if (template == null) {
            ctx.todo(relPath, null, "build-script",
                    "目标版本缺少 templates/build.gradle 模板，构建脚本原样复制，需要人工升级 ForgeGradle 配置");
            return null;
        }
        String rendered = metadataPass.renderTemplate(template);
        ctx.info(relPath, null, "build-script", "build.gradle 已按目标版本模板重新生成（原脚本中的自定义逻辑不会保留，见报告）");
        ctx.todo(relPath, null, "build-script",
                "build.gradle 为模板重新生成：若原脚本包含自定义任务、依赖或仓库，请从原工程手动搬运");
        return new OutputFile(relPath, rendered.getBytes(StandardCharsets.UTF_8));
    }
}
