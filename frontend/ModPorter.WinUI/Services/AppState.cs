using ModPorter.WinUI.Models;

namespace ModPorter.WinUI.Services;

/// <summary>页面间共享的会话状态。</summary>
public static class AppState
{
    public static Capabilities? Capabilities { get; set; }

    /// <summary>最近一次转换产生的报告 JSON 路径，报告页读取它。</summary>
    public static string? LastReportJsonPath { get; set; }
    public static string? LastReportMdPath { get; set; }
    public static string? LastTodoReportPath { get; set; }
    public static string? LastOutputDir { get; set; }
}
