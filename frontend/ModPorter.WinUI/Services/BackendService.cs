using System.Diagnostics;
using System.Text;
using System.Text.Json;
using ModPorter.WinUI.Models;

namespace ModPorter.WinUI.Services;

/// <summary>
/// 后端进程封装。前端与后端之间只有三条通道，全部是版本化的 JSON 协议：
///   capabilities（能力清单）/ run &lt;action&gt;（通用动作 + NDJSON 进度）/ 报告文件。
/// 后端新增功能只会体现在 capabilities.actions 里，前端零改动。
/// </summary>
public sealed class BackendService
{
    private static readonly JsonSerializerOptions JsonOpts = new() { PropertyNameCaseInsensitive = true };

    private readonly AppSettings _settings;

    public BackendService(AppSettings settings)
    {
        _settings = settings;
    }

    private ProcessStartInfo CreateStartInfo(params string[] args)
    {
        var psi = new ProcessStartInfo
        {
            FileName = string.IsNullOrWhiteSpace(_settings.JavaPath) ? "java" : _settings.JavaPath,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        psi.ArgumentList.Add("-Dfile.encoding=UTF-8");
        psi.ArgumentList.Add("-jar");
        psi.ArgumentList.Add(_settings.BackendJarPath);
        foreach (var a in args) psi.ArgumentList.Add(a);
        if (!string.IsNullOrWhiteSpace(_settings.MappingsDir))
        {
            psi.ArgumentList.Add("--mappings");
            psi.ArgumentList.Add(_settings.MappingsDir);
        }
        return psi;
    }

    /// <summary>拉取后端能力清单（版本列表 + 动作列表）。</summary>
    public async Task<Capabilities> GetCapabilitiesAsync(CancellationToken ct = default)
    {
        var psi = CreateStartInfo("capabilities");
        using var process = Process.Start(psi)
            ?? throw new InvalidOperationException("无法启动后端进程，请在设置中检查 Java 与 modporter.jar 路径");
        string stdout = await process.StandardOutput.ReadToEndAsync(ct);
        string stderr = await process.StandardError.ReadToEndAsync(ct);
        await process.WaitForExitAsync(ct);
        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException("后端 capabilities 调用失败: " + stderr);
        }
        return JsonSerializer.Deserialize<Capabilities>(stdout, JsonOpts)
            ?? throw new InvalidOperationException("capabilities 输出无法解析");
    }

    /// <summary>
    /// 通用动作调用：run &lt;actionId&gt; --params &lt;json&gt;，逐行解析 NDJSON 进度。
    /// 内置的 port 和未来所有后端动作都走这一个入口。
    /// </summary>
    public async Task<ProgressEvent?> RunActionAsync(
        string actionId,
        Dictionary<string, object> parameters,
        IProgress<ProgressEvent> progress,
        CancellationToken ct = default)
    {
        string paramsJson = JsonSerializer.Serialize(parameters);
        var psi = CreateStartInfo("run", actionId, "--params", paramsJson);
        using var process = Process.Start(psi)
            ?? throw new InvalidOperationException("无法启动后端进程");

        ProgressEvent? result = null;
        var stderrTask = process.StandardError.ReadToEndAsync(ct);

        while (await process.StandardOutput.ReadLineAsync(ct) is { } line)
        {
            if (string.IsNullOrWhiteSpace(line)) continue;
            ProgressEvent? evt = null;
            try
            {
                evt = JsonSerializer.Deserialize<ProgressEvent>(line, JsonOpts);
            }
            catch
            {
                // 非 JSON 行按普通消息展示
                evt = new ProgressEvent { Type = "message", Text = line };
            }
            if (evt == null) continue;
            if (evt.Type == "result") result = evt;
            progress.Report(evt);
        }

        await process.WaitForExitAsync(ct);
        string stderr = await stderrTask;
        if (result == null && process.ExitCode != 0)
        {
            progress.Report(new ProgressEvent
            {
                Type = "fatal",
                Text = string.IsNullOrWhiteSpace(stderr) ? "后端进程异常退出 (" + process.ExitCode + ")" : stderr
            });
        }
        return result;
    }

    /// <summary>读取转换报告 JSON。</summary>
    public static async Task<ReportFile?> ReadReportAsync(string path)
    {
        if (!File.Exists(path)) return null;
        await using var stream = File.OpenRead(path);
        return await JsonSerializer.DeserializeAsync<ReportFile>(stream, JsonOpts);
    }
}
