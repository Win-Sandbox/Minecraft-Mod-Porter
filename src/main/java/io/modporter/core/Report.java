package io.modporter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 转换报告：所有自动改写记录、TODO、警告和错误的结构化集合。
 */
public final class Report {

    public enum Severity { INFO, WARN, TODO, ERROR }

    public static final class Entry {
        public final Severity severity;
        public final String file;      // 相对路径，可为 null（引擎级消息）
        public final Integer line;     // 可为 null
        public final String category;  // 如 "class-mapping" / "member-mapping" / "idiom" / "removed-api"
        public final String message;

        public Entry(Severity severity, String file, Integer line, String category, String message) {
            this.severity = severity;
            this.file = file;
            this.line = line;
            this.category = category;
            this.message = message;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private String sourceVersion;
    private String targetVersion;

    public void setVersions(String source, String target) {
        this.sourceVersion = source;
        this.targetVersion = target;
    }

    public String sourceVersion() { return sourceVersion; }
    public String targetVersion() { return targetVersion; }

    public synchronized void add(Severity severity, String file, Integer line, String category, String message) {
        entries.add(new Entry(severity, file, line, category, message));
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public long count(Severity severity) {
        return entries.stream().filter(e -> e.severity == severity).count();
    }
}
