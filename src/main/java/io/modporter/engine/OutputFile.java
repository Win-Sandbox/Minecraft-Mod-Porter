package io.modporter.engine;

/**
 * 一个 Pass 的产物：输出到目标工程的一个文件（路径可能与输入不同，如 mcmod.info -> META-INF/mods.toml）。
 */
public final class OutputFile {
    public final String relativePath;
    public final byte[] content;

    public OutputFile(String relativePath, byte[] content) {
        this.relativePath = relativePath;
        this.content = content;
    }
}
