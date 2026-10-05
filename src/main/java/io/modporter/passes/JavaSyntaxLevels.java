package io.modporter.passes;

import com.github.javaparser.ParserConfiguration;

/**
 * Java 源版本 -> JavaParser LanguageLevel 的保守映射。
 *
 * 依赖 JavaParser 3.28.2（上游 pom 的 java.version=1.8，库自身可在 Java 17 运行）。
 * 枚举值已对照上游 3.28.2 的 ParserConfiguration 源码核实：
 * JAVA_8/11/16/17/21/22/23/24/25 均存在（CURRENT=JAVA_26）。
 *
 * 本工具运行在 Java 17，只是引用这些枚举常量，不需要运行在 Java 21/25。
 */
final class JavaSyntaxLevels {

    private JavaSyntaxLevels() {
    }

    /** 精确选择已核实的正式级别；未知级别由调用方诊断并原样保留源码。 */
    static ParserConfiguration.LanguageLevel forVersion(int javaVersion) {
        if (!supported(javaVersion)) return ParserConfiguration.LanguageLevel.JAVA_25;
        return ParserConfiguration.LanguageLevel.valueOf("JAVA_" + javaVersion);
    }

    static boolean supported(int javaVersion) {
        return javaVersion >= 8 && javaVersion <= 26;
    }
}
