using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using ModPorter.WinUI.Models;
using ModPorter.WinUI.Services;

namespace ModPorter.WinUI.Pages;

public sealed partial class ReportPage : Page
{
    private ReportFile? _report;

    public ReportPage()
    {
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await LoadReportAsync();
    }

    private async Task LoadReportAsync()
    {
        if (AppState.LastReportJsonPath == null)
        {
            SubtitleText.Text = "还没有转换报告。先在「版本转换」页完成一次转换。";
            return;
        }
        _report = await BackendService.ReadReportAsync(AppState.LastReportJsonPath);
        if (_report == null)
        {
            SubtitleText.Text = "报告文件不存在或无法解析：" + AppState.LastReportJsonPath;
            return;
        }
        SubtitleText.Text = $"{_report.SourceVersion} → {_report.TargetVersion}";
        InfoCount.Text = _report.InfoCount.ToString();
        WarnCount.Text = _report.WarnCount.ToString();
        TodoCount.Text = _report.TodoCount.ToString();
        ErrorCount.Text = _report.ErrorCount.ToString();
        ApplyFilter();
    }

    private void SeverityFilter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        if (_report == null) return;
        string tag = (SeverityFilter.SelectedItem as ComboBoxItem)?.Tag as string ?? "ALL";
        var entries = tag == "ALL"
            ? _report.Entries
            : _report.Entries.Where(en => en.Severity == tag).ToList();
        EntryList.ItemsSource = entries;
    }

    private void OpenOutput_Click(object sender, RoutedEventArgs e)
    {
        if (AppState.LastOutputDir != null && Directory.Exists(AppState.LastOutputDir))
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = AppState.LastOutputDir,
                UseShellExecute = true,
            });
        }
    }

    private void OpenTodo_Click(object sender, RoutedEventArgs e)
    {
        if (AppState.LastTodoReportPath != null && File.Exists(AppState.LastTodoReportPath))
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = AppState.LastTodoReportPath,
                UseShellExecute = true,
            });
        }
    }

    private void OpenMd_Click(object sender, RoutedEventArgs e)
    {
        if (AppState.LastReportMdPath != null && File.Exists(AppState.LastReportMdPath))
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = AppState.LastReportMdPath,
                UseShellExecute = true,
            });
        }
    }
}
