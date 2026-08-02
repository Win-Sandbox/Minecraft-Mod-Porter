package io.modporter.core;

/**
 * 转换进度回调，供 CLI 打印进度、供未来 UI 更新进度条。
 */
public interface ProgressListener {

    ProgressListener NOOP = new ProgressListener() {};

    /** 开始处理一个文件。index 从 0 开始，total 为总文件数。 */
    default void onFileStart(String relativePath, int index, int total) {}

    /** 一个文件处理完成。 */
    default void onFileDone(String relativePath, int todoCount) {}

    /** 引擎级别的阶段性消息（加载映射、写报告等）。 */
    default void onMessage(String message) {}
}
