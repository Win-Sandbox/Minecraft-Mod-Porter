using System.Text.Json.Serialization;

namespace ModPorter.WinUI.Models;

/// <summary>后端 capabilities 协议（schemaVersion 1）。前端只依赖这份契约做动态渲染。</summary>
public sealed class Capabilities
{
    [JsonPropertyName("schemaVersion")] public int SchemaVersion { get; set; }
    [JsonPropertyName("backend")] public BackendInfo? Backend { get; set; }
    [JsonPropertyName("loaders")] public List<LoaderInfo> Loaders { get; set; } = new();
    [JsonPropertyName("actions")] public List<ActionInfo> Actions { get; set; } = new();
}

public sealed class BackendInfo
{
    [JsonPropertyName("name")] public string Name { get; set; } = "";
    [JsonPropertyName("version")] public string Version { get; set; } = "";
}

public sealed class LoaderInfo
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("versions")] public List<string> Versions { get; set; } = new();
}

public sealed class ActionInfo
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("description")] public string Description { get; set; } = "";
    [JsonPropertyName("available")] public bool Available { get; set; }
    [JsonPropertyName("builtin")] public bool Builtin { get; set; }
    [JsonPropertyName("params")] public List<ParamInfo> Params { get; set; } = new();
}

public sealed class ParamInfo
{
    /// <summary>类型：path / pathList / bool / string / enum:loaders / enum:versions</summary>
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("type")] public string Type { get; set; } = "string";
    [JsonPropertyName("title")] public string Title { get; set; } = "";
    [JsonPropertyName("required")] public bool Required { get; set; }
}

/// <summary>port --progress-json 的 NDJSON 事件。</summary>
public sealed class ProgressEvent
{
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("text")] public string? Text { get; set; }
    [JsonPropertyName("path")] public string? Path { get; set; }
    [JsonPropertyName("index")] public int Index { get; set; }
    [JsonPropertyName("total")] public int Total { get; set; }
    [JsonPropertyName("todos")] public int Todos { get; set; }
    [JsonPropertyName("status")] public string? Status { get; set; }
    [JsonPropertyName("info")] public long Info { get; set; }
    [JsonPropertyName("warn")] public long Warn { get; set; }
    [JsonPropertyName("todo")] public long Todo { get; set; }
    [JsonPropertyName("error")] public long Error { get; set; }
    [JsonPropertyName("reportJson")] public string? ReportJson { get; set; }
    [JsonPropertyName("reportMd")] public string? ReportMd { get; set; }
    [JsonPropertyName("todoReport")] public string? TodoReport { get; set; }
}

/// <summary>modporter-report.json 的结构。</summary>
public sealed class ReportFile
{
    [JsonPropertyName("sourceVersion")] public string SourceVersion { get; set; } = "";
    [JsonPropertyName("targetVersion")] public string TargetVersion { get; set; } = "";
    [JsonPropertyName("infoCount")] public long InfoCount { get; set; }
    [JsonPropertyName("warnCount")] public long WarnCount { get; set; }
    [JsonPropertyName("todoCount")] public long TodoCount { get; set; }
    [JsonPropertyName("errorCount")] public long ErrorCount { get; set; }
    [JsonPropertyName("entries")] public List<ReportEntry> Entries { get; set; } = new();
}

public sealed class ReportEntry
{
    [JsonPropertyName("severity")] public string Severity { get; set; } = "";
    [JsonPropertyName("file")] public string? File { get; set; }
    [JsonPropertyName("line")] public int? Line { get; set; }
    [JsonPropertyName("category")] public string Category { get; set; } = "";
    [JsonPropertyName("message")] public string Message { get; set; } = "";

    [JsonIgnore] public string Location =>
        File == null ? "" : Line == null ? File : $"{File}:{Line}";
}
