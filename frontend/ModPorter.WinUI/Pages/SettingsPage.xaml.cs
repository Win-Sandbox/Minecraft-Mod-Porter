using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ModPorter.WinUI.Services;
using Windows.Storage.Pickers;

namespace ModPorter.WinUI.Pages;

public sealed partial class SettingsPage : Page
{
    public SettingsPage()
    {
        InitializeComponent();
        var s = SettingsService.Load();
        JavaPathBox.Text = s.JavaPath;
        JarPathBox.Text = s.BackendJarPath;
        MappingsBox.Text = s.MappingsDir;
    }

    private AppSettings Collect() => new()
    {
        JavaPath = string.IsNullOrWhiteSpace(JavaPathBox.Text) ? "java" : JavaPathBox.Text.Trim(),
        BackendJarPath = JarPathBox.Text.Trim(),
        MappingsDir = MappingsBox.Text.Trim(),
    };

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        SettingsService.Save(Collect());
        AppState.Capabilities = null; // 设置变了，能力清单需要重新拉取
        Show(InfoBarSeverity.Success, "设置已保存");
    }

    private async void Test_Click(object sender, RoutedEventArgs e)
    {
        SettingsService.Save(Collect());
        try
        {
            var caps = await new BackendService(SettingsService.Load()).GetCapabilitiesAsync();
            AppState.Capabilities = caps;
            int versionCount = caps.Loaders.Sum(l => l.Versions.Count);
            Show(InfoBarSeverity.Success,
                $"后端连接成功：{caps.Backend?.Name} {caps.Backend?.Version}，" +
                $"{caps.Loaders.Count} 个加载器 / {versionCount} 个版本 / {caps.Actions.Count} 个动作");
        }
        catch (Exception ex)
        {
            Show(InfoBarSeverity.Error, "后端连接失败：" + ex.Message);
        }
    }

    private async void BrowseJar_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".jar");
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        var file = await picker.PickSingleFileAsync();
        if (file != null) JarPathBox.Text = file.Path;
    }

    private async void BrowseMappings_Click(object sender, RoutedEventArgs e)
    {
        var path = await ConvertPage.PickFolderAsync();
        if (path != null) MappingsBox.Text = path;
    }

    private void Show(InfoBarSeverity severity, string message)
    {
        StatusBar.Severity = severity;
        StatusBar.Message = message;
        StatusBar.IsOpen = true;
    }
}
