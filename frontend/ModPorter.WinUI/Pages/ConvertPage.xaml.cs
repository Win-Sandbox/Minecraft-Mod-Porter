using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using ModPorter.WinUI.Models;
using ModPorter.WinUI.Services;
using Windows.Storage.Pickers;

namespace ModPorter.WinUI.Pages;

public sealed partial class ConvertPage : Page
{
    private Capabilities? _capabilities;
    private bool _running;

    public ConvertPage()
    {
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await LoadCapabilitiesAsync();
    }

    /// <summary>从后端自动发现加载器与版本，填充下拉框。</summary>
    private async Task LoadCapabilitiesAsync()
    {
        BackendBar.IsOpen = false;
        try
        {
            var backend = new BackendService(SettingsService.Load());
            _capabilities = await backend.GetCapabilitiesAsync();
            AppState.Capabilities = _capabilities;

            LoaderCombo.ItemsSource = _capabilities.Loaders.Select(l => l.Id).ToList();
            if (LoaderCombo.Items.Count > 0)
            {
                LoaderCombo.SelectedIndex = 0; // 触发版本填充
            }
            else
            {
                ShowBackendError("映射数据库中没有任何版本，请检查设置中的映射数据目录");
            }
        }
        catch (Exception ex)
        {
            ShowBackendError("无法连接后端：" + ex.Message + "（请到「设置」配置 Java 与 modporter.jar 路径）");
        }
    }

    private void ShowBackendError(string message)
    {
        BackendBar.Severity = InfoBarSeverity.Error;
        BackendBar.Message = message;
        BackendBar.IsOpen = true;
    }

    private void LoaderCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_capabilities == null || LoaderCombo.SelectedItem is not string loaderId) return;
        var loader = _capabilities.Loaders.FirstOrDefault(l => l.Id == loaderId);
        var versions = loader?.Versions ?? new List<string>();
        FromCombo.ItemsSource = versions;
        ToCombo.ItemsSource = versions;
        if (versions.Count > 0)
        {
            FromCombo.SelectedIndex = 0;
            ToCombo.SelectedIndex = versions.Count - 1;
        }
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e)
    {
        await LoadCapabilitiesAsync();
    }

    private async void BrowseInput_Click(object sender, RoutedEventArgs e)
    {
        var path = await PickFolderAsync();
        if (path != null) InputBox.Text = path;
    }

    private async void BrowseOutput_Click(object sender, RoutedEventArgs e)
    {
        var path = await PickFolderAsync();
        if (path != null) OutputBox.Text = path;
    }

    internal static async Task<string?> PickFolderAsync()
    {
        var picker = new FolderPicker();
        picker.FileTypeFilter.Add("*");
        // WinUI 3 桌面应用需要手动关联窗口句柄
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        var folder = await picker.PickSingleFolderAsync();
        return folder?.Path;
    }

    private async void Convert_Click(object sender, RoutedEventArgs e)
    {
        if (_running) return;
        if (LoaderCombo.SelectedItem is not string loader
            || FromCombo.SelectedItem is not string from
            || ToCombo.SelectedItem is not string to)
        {
            ShowResult(InfoBarSeverity.Warning, "请先选择加载器与版本");
            return;
        }
        if (from == to)
        {
            ShowResult(InfoBarSeverity.Warning, "源版本与目标版本相同");
            return;
        }
        if (string.IsNullOrWhiteSpace(InputBox.Text) || string.IsNullOrWhiteSpace(OutputBox.Text))
        {
            ShowResult(InfoBarSeverity.Warning, "请先选择输入与输出目录");
            return;
        }

        _running = true;
        ConvertButton.IsEnabled = false;
        ResultBar.IsOpen = false;
        Progress.Visibility = Visibility.Visible;
        Progress.IsIndeterminate = true;
        ProgressText.Visibility = Visibility.Visible;
        ProgressText.Text = "启动后端…";
        LogExpander.Visibility = Visibility.Visible;
        LogList.Items.Clear();

        var parameters = new Dictionary<string, object>
        {
            ["loader"] = loader,
            ["from"] = from,
            ["to"] = to,
            ["input"] = InputBox.Text.Trim(),
            ["output"] = OutputBox.Text.Trim(),
            ["dryRun"] = DryRunToggle.IsOn,
        };

        var progress = new Progress<ProgressEvent>(OnProgress);
        try
        {
            var backend = new BackendService(SettingsService.Load());
            var result = await Task.Run(() => backend.RunActionAsync("port", parameters, progress));
            if (result != null)
            {
                AppState.LastReportJsonPath = result.ReportJson;
                AppState.LastReportMdPath = result.ReportMd;
                AppState.LastTodoReportPath = result.TodoReport;
                AppState.LastOutputDir = OutputBox.Text.Trim();
                var severity = result.Error > 0 ? InfoBarSeverity.Error
                    : result.Todo > 0 ? InfoBarSeverity.Warning
                    : InfoBarSeverity.Success;
                ShowResult(severity,
                    $"转换完成：自动改写 {result.Info} 处，警告 {result.Warn} 处，待人工 {result.Todo} 处，错误 {result.Error} 处",
                    showReport: result.ReportJson != null);
            }
        }
        catch (Exception ex)
        {
            ShowResult(InfoBarSeverity.Error, "转换失败：" + ex.Message);
        }
        finally
        {
            _running = false;
            ConvertButton.IsEnabled = true;
            Progress.Visibility = Visibility.Collapsed;
            ProgressText.Visibility = Visibility.Collapsed;
        }
    }

    private void OnProgress(ProgressEvent evt)
    {
        switch (evt.Type)
        {
            case "file":
                Progress.IsIndeterminate = false;
                if (evt.Total > 0)
                {
                    Progress.Value = 100.0 * evt.Index / evt.Total;
                }
                ProgressText.Text = $"[{evt.Index + 1}/{evt.Total}] {evt.Path}";
                break;
            case "fileDone":
                if (evt.Todos > 0)
                {
                    LogList.Items.Add($"{evt.Path}   [TODO x{evt.Todos}]");
                }
                break;
            case "message":
                LogList.Items.Add(evt.Text ?? "");
                break;
            case "fatal":
                ShowResult(InfoBarSeverity.Error, evt.Text ?? "后端报告了致命错误");
                break;
        }
    }

    private void ShowResult(InfoBarSeverity severity, string message, bool showReport = false)
    {
        ResultBar.Severity = severity;
        ResultBar.Message = message;
        ViewReportButton.Visibility = showReport ? Visibility.Visible : Visibility.Collapsed;
        ResultBar.IsOpen = true;
    }

    private void ViewReport_Click(object sender, RoutedEventArgs e)
    {
        Frame.Navigate(typeof(ReportPage));
    }
}
