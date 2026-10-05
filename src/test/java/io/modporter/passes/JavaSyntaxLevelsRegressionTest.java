package io.modporter.passes;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

/**
 * JavaSourcePass 语言级别升级（JavaParser 3.28.2）的回归测试。
 *
 * 本测试不依赖 JUnit，可用如下方式在后续补齐 test 任务后运行：
 *   java -cp <classes+deps> io.modporter.passes.JavaSyntaxLevelsRegressionTest
 * （当前按约定未修改 build.gradle 的测试依赖/任务，本轮也未执行任何编译。）
 *
 * 覆盖：
 * 1. 普通 Java 8 / Java 17 源码仍可解析；
 * 2. Java 8 的普通标识符 "_" 在 Java 17 级别下应解析失败（避免误当合法）；
 * 3. Java 21：record pattern / switch pattern 可解析，且在 Java 17 级别下应失败；
 * 4. Java 22：未命名变量 "_"（JEP 456）可解析，且在 Java 21 级别下应失败。
 */
public final class JavaSyntaxLevelsRegressionTest {

    private JavaSyntaxLevelsRegressionTest() {
    }

    public static void main(String[] args) {
        languageLevelMapping();
        java8OrdinarySourceParses();
        java17OrdinarySourceParses();
        underscoreIllegalAtJava17();
        recordAndSwitchPatternsAtJava21();
        unnamedVariableAtJava22();
        System.out.println("JavaSyntaxLevelsRegressionTest: ALL PASSED");
    }

    private static void languageLevelMapping() {
        assertEquals(ParserConfiguration.LanguageLevel.JAVA_8, JavaSyntaxLevels.forVersion(8));
        assertEquals(ParserConfiguration.LanguageLevel.JAVA_17, JavaSyntaxLevels.forVersion(17));
        assertEquals(ParserConfiguration.LanguageLevel.JAVA_21, JavaSyntaxLevels.forVersion(21));
        assertEquals(ParserConfiguration.LanguageLevel.JAVA_25, JavaSyntaxLevels.forVersion(25));
    }

    private static void java8OrdinarySourceParses() {
        String source = "class A { int f() { int x = 1; return x; } }";
        assertTrue(parse(source, 8).isSuccessful(), "Java 8 普通源码应可解析");
    }

    private static void java17OrdinarySourceParses() {
        String source = "class A { int f(Object o) { "
                + "if (o instanceof String s) { return s.length(); } return 0; } }";
        assertTrue(parse(source, 17).isSuccessful(), "Java 17 普通源码应可解析");
    }

    private static void underscoreIllegalAtJava17() {
        // Java 8：_ 是合法普通标识符；Java 9+ 被保留为关键字，普通用法非法。
        String java8NamedUnderscore = "class A { int f() { int _ = 1; return _; } }";
        assertTrue(parse(java8NamedUnderscore, 8).isSuccessful(), "Java 8 中 _ 应是合法标识符");
        assertFalse(parse(java8NamedUnderscore, 17).isSuccessful(), "Java 17 中普通标识符 _ 应解析失败");
    }

    private static void recordAndSwitchPatternsAtJava21() {
        String source = """
                class A {
                    record Point(int x, int y) {}
                    int f(Object o) {
                        if (o instanceof Point(int x, int y)) { return x + y; }
                        return g(o);
                    }
                    int g(Object o) {
                        return switch (o) {
                            case Integer i -> i;
                            case Point(int x, int y) -> x + y;
                            default -> 0;
                        };
                    }
                }
                """;
        assertTrue(parse(source, 21).isSuccessful(), "Java 21 record/switch pattern 应可解析");
        assertFalse(parse(source, 17).isSuccessful(), "Java 17 级别下 record/switch pattern 应解析失败");
    }

    private static void unnamedVariableAtJava22() {
        String source = """
                class A {
                    int f() {
                        var _ = sideEffect();
                        try {
                            return 1;
                        } catch (Exception _) {
                            return 0;
                        }
                    }
                    int sideEffect() { return 0; }
                }
                """;
        assertTrue(parse(source, 22).isSuccessful(), "Java 22 未命名变量 _ 应可解析");
        assertFalse(parse(source, 21).isSuccessful(), "Java 21 级别下未命名变量 _ 应解析失败");
    }

    private static ParseResult<CompilationUnit> parse(String source, int javaVersion) {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(JavaSyntaxLevels.forVersion(javaVersion));
        return new JavaParser(config).parse(source);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }
}
