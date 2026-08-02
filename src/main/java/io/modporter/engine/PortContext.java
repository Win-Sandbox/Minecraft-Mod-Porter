package io.modporter.engine;

import io.modporter.core.PortRequest;
import io.modporter.core.Report;
import io.modporter.mappings.JavaPlatform;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;

import java.util.Map;

/**
 * 一次转换过程中各 Pass 共享的上下文。
 */
public final class PortContext {

    public final PortRequest request;
    public final MappingResolver resolver;
    public final Report report;
    /** 元数据 Pass 先行解析出的模组元数据，供 lang 键推断、build.gradle 生成等使用。 */
    public ModMeta modMeta = new ModMeta();

    /** 源/目标 MC 版本对应的 Java 平台数据（mappings/java/，可为 null = 无数据时跳过平台检查）。 */
    public JavaPlatform sourceJava;
    public JavaPlatform targetJava;
    /** 语法特性 -> 引入的 Java 版本。 */
    public Map<String, Integer> javaFeatures = Map.of();

    public PortContext(PortRequest request, MappingResolver resolver, Report report) {
        this.request = request;
        this.resolver = resolver;
        this.report = report;
    }

    public VersionMappings source() { return resolver.source(); }
    public VersionMappings target() { return resolver.target(); }

    public void info(String file, Integer line, String category, String message) {
        report.add(Report.Severity.INFO, file, line, category, message);
    }

    public void warn(String file, Integer line, String category, String message) {
        report.add(Report.Severity.WARN, file, line, category, message);
    }

    public void todo(String file, Integer line, String category, String message) {
        report.add(Report.Severity.TODO, file, line, category, message);
    }

    public void error(String file, Integer line, String category, String message) {
        report.add(Report.Severity.ERROR, file, line, category, message);
    }
}
