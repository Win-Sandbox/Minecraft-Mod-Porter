package io.modporter.core;

/**
 * 一次转换的结果。UI 可直接消费 Report 里的结构化条目。
 */
public final class PortResult {

    public enum Status { SUCCESS, SUCCESS_WITH_TODOS, FAILED }

    private final Status status;
    private final Report report;

    public PortResult(Status status, Report report) {
        this.status = status;
        this.report = report;
    }

    public Status status() { return status; }
    public Report report() { return report; }
}
