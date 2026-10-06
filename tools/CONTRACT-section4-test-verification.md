# 第4类静态回归接线契约

此文件记录在禁止编译条件下可执行的最低静态检查：`tools/verify_regression_wiring.py` 检查 build.gradle 中回归入口对应的 Java 文件是否存在，以及是否具有 main 入口。它不能替代 Java 编译或运行测试。

本轮未运行 Gradle、javac、Java 回归或构建测试。
