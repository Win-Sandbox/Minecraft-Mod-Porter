using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using ModPorter.WinUI.Models;
using ModPorter.WinUI.Services;

namespace ModPorter.WinUI.Pages;

/// <summary>
/// 通用扩展功能页：把 capabilities.actions 中所有非内置动作渲染为卡片，
/// 参数表单按 ParamInfo.Type 动态生成，调用统一走 run &lt;actionId&gt;。
/// 后端新增/启用动作时本页自动变化，前端零改动。
/// </summary>
public sealed partial class ExtensionsPage : Page
{
    private Capabilities? _capabilities;

    public ExtensionsPage()
    {
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        try
        {
            _capabilities = AppState.Capabilities
                ?? await new BackendService(SettingsService.Load()).GetCapabilitiesAsync();
            AppState.Capabilities = _capabilities;
            BuildActionCards();
        }
        catch (Exception ex)
        {
            BackendBar.Message = "无法获取后端能力清单：" + ex.Message;
            BackendBar.IsOpen = true;
        }
    }

    private void BuildActionCards()
    {
        ActionsPanel.Children.Clear();
        if (_capabilities == null) return;

        foreach (var action in _capabilities.Actions.Where(a => !a.Builtin))
        {
            ActionsPanel.Children.Add(CreateActionCard(action));
        }
        if (ActionsPanel.Children.Count == 0)
        {
            ActionsPanel.Children.Add(new TextBlock
            {
                Text = "后端没有声明任何扩展动作。",
                Style = (Style)Application.Current.Resources["BodyTextBlockStyle"],
            });
        }
    }

    private UIElement CreateActionCard(ActionInfo action)
    {
        var content = new StackPanel { Spacing = 8 };

        var header = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        header.Children.Add(new TextBlock
        {
            Text = action.Title,
            Style = (Style)Application.Current.Resources["SubtitleTextBlockStyle"],
        });
        if (!action.Available)
        {
            header.Children.Add(new Border
            {
                Background = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["SystemFillColorCautionBackgroundBrush"],
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(8, 2, 8, 2),
                VerticalAlignment = VerticalAlignment.Center,
                Child = new TextBlock
                {
                    Text = "待后端实现",
                    Style = (Style)Application.Current.Resources["CaptionTextBlockStyle"],
                },
            });
        }
        content.Children.Add(header);

        content.Children.Add(new TextBlock
        {
            Text = action.Description,
            TextWrapping = TextWrapping.Wrap,
            Style = (Style)Application.Current.Resources["BodyTextBlockStyle"],
        });

        // 参数表单（动态生成；不可用的动作只展示参数说明，控件禁用）
        var inputs = new Dictionary<string, FrameworkElement>();
        var form = new StackPanel { Spacing = 8 };
        foreach (var p in action.Params)
        {
            FrameworkElement control = CreateParamControl(p);
            control.IsHitTestVisible = action.Available;
            if (control is Control c) c.IsEnabled = action.Available;
            inputs[p.Id] = control;
            form.Children.Add(control);
        }
        content.Children.Add(form);

        var resultBar = new InfoBar { IsOpen = false, IsClosable = true };
        var runButton = new Button
        {
            Content = action.Available ? "执行" : "执行（后端未实现）",
            Style = (Style)Application.Current.Resources["AccentButtonStyle"],
            IsEnabled = action.Available,
        };
        runButton.Click += async (_, _) => await RunActionAsync(action, inputs, resultBar, runButton);
        content.Children.Add(runButton);
        content.Children.Add(resultBar);

        return new Expander
        {
            Header = action.Available ? action.Title : action.Title + "（即将支持）",
            Content = content,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
        };
    }

    private FrameworkElement CreateParamControl(ParamInfo p)
    {
        string header = p.Title + (p.Required ? " *" : "");
        if (p.Type == "bool")
        {
            return new ToggleSwitch { Header = header, Tag = p };
        }
        if (p.Type.StartsWith("enum:"))
        {
            var combo = new ComboBox { Header = header, Tag = p, MinWidth = 200 };
            string source = p.Type.Substring("enum:".Length);
            if (_capabilities != null)
            {
                combo.ItemsSource = source switch
                {
                    "loaders" => _capabilities.Loaders.Select(l => l.Id).ToList(),
                    "versions" => _capabilities.Loaders.SelectMany(l => l.Versions).Distinct().ToList(),
                    _ => new List<string>(),
                };
            }
            return combo;
        }
        // path / pathList / string 一律文本框；path 类型附浏览按钮
        var box = new TextBox { Header = header, Tag = p };
        if (p.Type == "path")
        {
            var grid = new Grid { ColumnSpacing = 8, Tag = p };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var browse = new Button { Content = "浏览…", VerticalAlignment = VerticalAlignment.Bottom };
            browse.Click += async (_, _) =>
            {
                var path = await ConvertPage.PickFolderAsync();
                if (path != null) box.Text = path;
            };
            Grid.SetColumn(box, 0);
            Grid.SetColumn(browse, 1);
            grid.Children.Add(box);
            grid.Children.Add(browse);
            return grid;
        }
        if (p.Type == "pathList")
        {
            box.PlaceholderText = "多个路径用分号 ; 分隔";
        }
        return box;
    }

    private async Task RunActionAsync(ActionInfo action, Dictionary<string, FrameworkElement> inputs,
                                      InfoBar resultBar, Button runButton)
    {
        var parameters = new Dictionary<string, object>();
        foreach (var (id, element) in inputs)
        {
            object? value = ReadValue(element);
            if (value != null) parameters[id] = value;
        }

        runButton.IsEnabled = false;
        resultBar.IsOpen = false;
        try
        {
            var backend = new BackendService(SettingsService.Load());
            string log = "";
            var progress = new Progress<ProgressEvent>(evt =>
            {
                if (evt.Type is "message" or "fatal") log = evt.Text ?? log;
            });
            var result = await Task.Run(() => backend.RunActionAsync(action.Id, parameters, progress));
            if (result != null)
            {
                resultBar.Severity = result.Error > 0 ? InfoBarSeverity.Error : InfoBarSeverity.Success;
                resultBar.Message = $"完成：状态 {result.Status}，待人工 {result.Todo} 处";
            }
            else
            {
                resultBar.Severity = InfoBarSeverity.Error;
                resultBar.Message = string.IsNullOrEmpty(log) ? "后端未返回结果" : log;
            }
            resultBar.IsOpen = true;
        }
        catch (Exception ex)
        {
            resultBar.Severity = InfoBarSeverity.Error;
            resultBar.Message = ex.Message;
            resultBar.IsOpen = true;
        }
        finally
        {
            runButton.IsEnabled = true;
        }
    }

    private static object? ReadValue(FrameworkElement element)
    {
        return element switch
        {
            ToggleSwitch t => t.IsOn,
            ComboBox c => c.SelectedItem as string,
            TextBox b when (b.Tag as ParamInfo)?.Type == "pathList" =>
                b.Text.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries),
            TextBox b => string.IsNullOrWhiteSpace(b.Text) ? null : b.Text.Trim(),
            Grid g => g.Children.OfType<TextBox>().FirstOrDefault() is { } inner
                ? (string.IsNullOrWhiteSpace(inner.Text) ? null : inner.Text.Trim())
                : null,
            _ => null,
        };
    }
}
