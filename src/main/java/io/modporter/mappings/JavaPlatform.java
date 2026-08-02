package io.modporter.mappings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个 Java 平台版本的差异数据（mappings/java/&lt;version&gt;.json）。
 * 与 MC 版本解耦：MC 版本经 version.json 的 javaVersion 关联到这里；
 * 互通的 Java 版本可用文件内 "aliases" 覆盖，不重复建档；
 * 注意 16 与 17 并不互通（16 无 sealed/permits 类型名限制、java.rmi.activation 仍在），故 16.json 独立建档。
 */
public final class JavaPlatform {

    /**
     * 方法级问题：某个（可选归属类型的）方法在本 Java 版本已移除或行为破坏。
     * owner 为 null 表示任意接收者（仅用于极具辨识度的方法名）。
     */
    public static final class MethodIssue {
        public final String owner;   // 归属类型的简单名，可为 null
        public final String method;
        public final String message;
        /** true = 接收者类型无法确定时也报告（默认 false，只在能确定接收者时报告，避免误伤同名方法）。 */
        public final boolean anyReceiver;

        public MethodIssue(String owner, String method, String message, boolean anyReceiver) {
            this.owner = owner;
            this.method = method;
            this.message = message;
            this.anyReceiver = anyReceiver;
        }
    }

    /** 参数值问题：方法本身还在，但某些字符串实参在本版本已失效（如 getEngineByName("nashorn")）。 */
    public static final class ArgumentIssue {
        public final String owner;
        public final String method;
        public final List<String> values;   // 命中的字符串实参（不区分大小写）
        public final String message;

        public ArgumentIssue(String owner, String method, List<String> values, String message) {
            this.owner = owner;
            this.method = method;
            this.values = List.copyOf(values);
            this.message = message;
        }
    }

    /** 反射查表：这些方法的字符串实参是类名，需按 removedClasses/encapsulatedPackages 检查。 */
    public static final class ReflectiveLookup {
        public final String owner;
        public final String method;

        public ReflectiveLookup(String owner, String method) {
            this.owner = owner;
            this.method = method;
        }
    }

    public final int version;
    /** 在本 Java 版本中已完全非法的标识符（如 9+ 的 "_"），引擎可自动改名。 */
    public final Set<String> illegalIdentifiers;
    /** 受限类型名（var/record/sealed/permits/yield 等不能再作类型名），引擎打 TODO。 */
    public final Set<String> restrictedTypeNames;
    /** 已移除/不可用的类或包前缀（前缀以 .* 结尾）-> 迁移指导。 */
    public final Map<String, String> removedClasses;
    /** 被模块系统封锁的内部包前缀 -> 指导（--add-opens / 替代 API）。 */
    public final Map<String, String> encapsulatedPackages;
    /** 方法级问题，按方法名索引（同名可能有多个归属类型）。 */
    public final Map<String, List<MethodIssue>> removedMethods;
    /** 参数值问题，按方法名索引。 */
    public final Map<String, List<ArgumentIssue>> argumentIssues;
    /** 反射查表方法。 */
    public final List<ReflectiveLookup> reflectiveLookups;

    public JavaPlatform(int version,
                        Set<String> illegalIdentifiers,
                        Set<String> restrictedTypeNames,
                        Map<String, String> removedClasses,
                        Map<String, String> encapsulatedPackages,
                        Map<String, List<MethodIssue>> removedMethods,
                        Map<String, List<ArgumentIssue>> argumentIssues,
                        List<ReflectiveLookup> reflectiveLookups) {
        this.version = version;
        this.illegalIdentifiers = Collections.unmodifiableSet(illegalIdentifiers);
        this.restrictedTypeNames = Collections.unmodifiableSet(restrictedTypeNames);
        this.removedClasses = Collections.unmodifiableMap(removedClasses);
        this.encapsulatedPackages = Collections.unmodifiableMap(encapsulatedPackages);
        this.removedMethods = Collections.unmodifiableMap(removedMethods);
        this.argumentIssues = Collections.unmodifiableMap(argumentIssues);
        this.reflectiveLookups = List.copyOf(reflectiveLookups);
    }

    /** 查找某导入/类名对应的移除或封锁指导；无命中返回 null。 */
    public String lookupImportIssue(String fqcnOrPackage) {
        for (Map.Entry<String, String> e : removedClasses.entrySet()) {
            if (matches(e.getKey(), fqcnOrPackage)) return e.getValue();
        }
        for (Map.Entry<String, String> e : encapsulatedPackages.entrySet()) {
            if (matches(e.getKey(), fqcnOrPackage)) return e.getValue();
        }
        return null;
    }

    private static boolean matches(String pattern, String name) {
        if (pattern.endsWith(".*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }
}
