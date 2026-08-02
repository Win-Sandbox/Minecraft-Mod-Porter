package io.modporter.core;

import java.nio.file.Path;

/**
 * 一次转换任务的全部输入参数。
 */
public final class PortRequest {

    private final Path inputRoot;
    private final Path outputRoot;
    private final String loader;
    private final String sourceVersion;
    private final String targetVersion;
    private final boolean dryRun;

    public PortRequest(Path inputRoot, Path outputRoot, String loader,
                       String sourceVersion, String targetVersion, boolean dryRun) {
        this.inputRoot = inputRoot;
        this.outputRoot = outputRoot;
        this.loader = loader;
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.dryRun = dryRun;
    }

    public Path inputRoot() { return inputRoot; }
    public Path outputRoot() { return outputRoot; }
    public String loader() { return loader; }
    public String sourceVersion() { return sourceVersion; }
    public String targetVersion() { return targetVersion; }
    public boolean dryRun() { return dryRun; }
}
