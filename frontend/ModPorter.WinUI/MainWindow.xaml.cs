using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using ModPorter.WinUI.Pages;

namespace ModPorter.WinUI;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        // Fluent：Mica 背景 + 内容扩展到标题栏
        SystemBackdrop = new MicaBackdrop();
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);

        ContentFrame.Navigate(typeof(ConvertPage));
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.IsSettingsSelected)
        {
            ContentFrame.Navigate(typeof(SettingsPage));
            return;
        }
        if (args.SelectedItem is NavigationViewItem item)
        {
            switch (item.Tag as string)
            {
                case "convert": ContentFrame.Navigate(typeof(ConvertPage)); break;
                case "report": ContentFrame.Navigate(typeof(ReportPage)); break;
                case "extensions": ContentFrame.Navigate(typeof(ExtensionsPage)); break;
            }
        }
    }
}
