using System.Text.Json;

namespace ModPorter.WinUI.Services;

/// <summary>本地设置（未打包应用，存 %LOCALAPPDATA%\ModPorter\settings.json）。</summary>
public sealed class AppSettings
{
    public string JavaPath { get; set; } = "java";
    public string BackendJarPath { get; set; } = "";
    public string MappingsDir { get; set; } = "";
}

public static class SettingsService
{
    private static readonly string Dir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ModPorter");
    private static readonly string File = Path.Combine(Dir, "settings.json");

    public static AppSettings Load()
    {
        try
        {
            if (System.IO.File.Exists(File))
            {
                return JsonSerializer.Deserialize<AppSettings>(System.IO.File.ReadAllText(File)) ?? new AppSettings();
            }
        }
        catch
        {
            // 设置损坏时回落默认值
        }
        return new AppSettings();
    }

    public static void Save(AppSettings settings)
    {
        Directory.CreateDirectory(Dir);
        System.IO.File.WriteAllText(File,
            JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true }));
    }
}
