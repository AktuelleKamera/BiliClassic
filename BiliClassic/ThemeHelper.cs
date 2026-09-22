using System;
using System.Collections.Generic;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using BiliClassic.Api;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;

namespace BiliClassic
{
    // 【重要】白主题只跟随系统深浅；页面/框架/状态栏底色一律交回系统，别再自己刷，喵
    public static class ThemeHelper
    {
        public static readonly Color BackgroundColor = Color.FromArgb(0xFF, 0xDC, 0xDC, 0xDC);
        public static readonly Color PivotColor = Color.FromArgb(0xFF, 0xD2, 0x65, 0x85);

        private static bool _saved;
        private static Color _foreground;
        private static Color _subtle;
        private static Color _inactive;
        private static Color _disabled;
        private static Color _textBox;
        private static Color _background;

        public static bool SystemIsLight()
        {
            try
            {
                object value = Application.Current.Resources["PhoneLightThemeVisibility"];
                return value is Visibility && (Visibility)value == Visibility.Visible;
            }
            catch (Exception)
            {
                return false;
            }
        }

        public static void AutoApply()
        {
            AppSettings.WhiteTheme = SystemIsLight();

            bool white = AppSettings.WhiteTheme;
            ApplyBrushes(white);
            ApplySystemTray(white);
            ApplyFrameBackground(white);
        }

        public static void ApplyPage(PhoneApplicationPage page)
        {
            if (page is VideoPlayerPage)
            {
                return;
            }

            bool white = AppSettings.WhiteTheme;
            ApplyBrushes(white);

            if (page == null)
            {
                ApplySystemTray(white);
                ApplyFrameBackground(white);
                return;
            }

            ApplySystemTray(white);
            EnsureAppBar(page, white);
            ApplyFrameBackground(white);

            page.ClearValue(Control.BackgroundProperty);

            List<Pivot> pivots = new List<Pivot>();
            CollectPivots(page, pivots);
            for (int i = 0; i < pivots.Count; i++)
            {
                ApplyPivot(pivots[i], white);
            }

            List<TextBlock> titles = new List<TextBlock>();
            CollectTitles(page, titles);
            for (int i = 0; i < titles.Count; i++)
            {
                if (white)
                {
                    titles[i].Foreground = new SolidColorBrush(PivotColor);
                }
                else
                {
                    titles[i].ClearValue(TextBlock.ForegroundProperty);
                }
            }

            ApplyHeader(page, white);
        }


        private const string HeaderTag = "theme_header_logo";
        private const string AppName = "哔哩经典";

        private static void ApplyHeader(PhoneApplicationPage page, bool white)
        {
            List<TextBlock> names = new List<TextBlock>();
            CollectAppNames(page, names);

            if (white)
            {
                for (int i = 0; i < names.Count; i++)
                {
                    TextBlock name = names[i];
                    name.Visibility = Visibility.Collapsed;

                    Panel parent = name.Parent as Panel;
                    if (parent == null || HasChildTag(parent, HeaderTag))
                    {
                        continue;
                    }
                    int index = parent.Children.IndexOf(name);
                    parent.Children.Insert(index + 1, BuildTopLogo());
                }
            }
            else
            {
                RemoveByTag(page, HeaderTag);
                for (int i = 0; i < names.Count; i++)
                {
                    names[i].Visibility = Visibility.Visible;
                }
            }
        }

        private static Image BuildTopLogo()
        {
            Image logo = new Image();
            logo.Tag = HeaderTag;
            logo.Source = LoadImage("/Res/Icon/toplogo.png");
            logo.Width = 172;
            logo.Stretch = Stretch.Uniform;
            logo.HorizontalAlignment = HorizontalAlignment.Left;
            logo.Margin = new Thickness(-12, 0, 0, 6);
            return logo;
        }

        private static BitmapImage LoadImage(string path)
        {
            BitmapImage image = new BitmapImage();
            image.UriSource = new Uri(path, UriKind.Relative);
            return image;
        }

        private static bool HasChildTag(Panel parent, string tag)
        {
            for (int i = 0; i < parent.Children.Count; i++)
            {
                FrameworkElement element = parent.Children[i] as FrameworkElement;
                if (element != null && (element.Tag as string) == tag)
                {
                    return true;
                }
            }
            return false;
        }

        private static void RemoveByTag(DependencyObject node, string tag)
        {
            if (node == null)
            {
                return;
            }

            Panel panel = node as Panel;
            if (panel != null)
            {
                for (int i = panel.Children.Count - 1; i >= 0; i--)
                {
                    FrameworkElement element = panel.Children[i] as FrameworkElement;
                    if (element != null && (element.Tag as string) == tag)
                    {
                        panel.Children.RemoveAt(i);
                    }
                }
            }

            int count = VisualTreeHelper.GetChildrenCount(node);
            for (int i = 0; i < count; i++)
            {
                RemoveByTag(VisualTreeHelper.GetChild(node, i), tag);
            }
        }

        private static void CollectAppNames(DependencyObject node, List<TextBlock> result)
        {
            if (node == null)
            {
                return;
            }

            TextBlock block = node as TextBlock;
            if (block != null && block.Text == AppName)
            {
                result.Add(block);
            }

            int count = VisualTreeHelper.GetChildrenCount(node);
            for (int i = 0; i < count; i++)
            {
                CollectAppNames(VisualTreeHelper.GetChild(node, i), result);
            }
        }

        private static void ApplySystemTray(bool white)
        {
#if WP8
            try
            {
                SystemTray.IsVisible = true;
            }
            catch (Exception)
            {
            }
#else
            SetStaticProperty(typeof(SystemTray), "IsVisible", (object)true);
#endif
        }


        private static bool _barSaved;
        private static Color _barBackground;
        private static Color _barForeground;

        public static void ApplyAppBar(PhoneApplicationPage page)
        {
            ApplyAppBar(page, AppSettings.WhiteTheme);
        }

        // 【重要】无栏页补个空栏盖住系统那片黑底板；透明会露黑，删了转场就黑一截，喵
        private static void EnsureAppBar(PhoneApplicationPage page, bool white)
        {
            if (page.ApplicationBar == null)
            {
                ApplicationBar bar = new ApplicationBar();
                bar.IsVisible = true;
                bar.IsMenuEnabled = false;

                page.ApplicationBar = bar;
                ApplyEmptyBarColor(bar, white);
                return;
            }

            ApplyAppBar(page, white);
        }

        private static void ApplyEmptyBarColor(IApplicationBar bar, bool white)
        {
            SetInstanceProperty(bar, "Opacity", 0.0);
            SetInstanceProperty(bar, "BackgroundColor", Colors.Transparent);
            SetInstanceProperty(bar, "ForegroundColor", Colors.Transparent);
        }



        private static void ApplyAppBar(PhoneApplicationPage page, bool white)
        {
            IApplicationBar bar = page.ApplicationBar;
            if (bar == null)
            {
                return;
            }

            if (!_barSaved)
            {
                object bg = GetInstanceProperty(bar, "BackgroundColor");
                object fg = GetInstanceProperty(bar, "ForegroundColor");
                if (bg is Color)
                {
                    _barBackground = (Color)bg;
                }
                if (fg is Color)
                {
                    _barForeground = (Color)fg;
                }
                _barSaved = true;
            }

            if (IsEmptyBar(bar))
            {
                ApplyEmptyBarColor(bar, white);
                return;
            }

            if (white)
            {
                SetInstanceProperty(bar, "BackgroundColor",
                    Color.FromArgb(0xFF, 0x61, 0x61, 0x61));
                SetInstanceProperty(bar, "ForegroundColor", Colors.White);
            }
            else
            {
                SetInstanceProperty(bar, "BackgroundColor", _barBackground);
                SetInstanceProperty(bar, "ForegroundColor", _barForeground);
            }
        }

        private static bool IsEmptyBar(IApplicationBar bar)
        {
            try
            {
                return bar.Buttons.Count == 0 && bar.MenuItems.Count == 0;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private static PropertyInfo FindProperty(object target, string name)
        {
            try
            {
                PropertyInfo property = target.GetType().GetProperty(name);
                if (property != null)
                {
                    return property;
                }

                Type[] interfaces = target.GetType().GetInterfaces();
                for (int i = 0; i < interfaces.Length; i++)
                {
                    property = interfaces[i].GetProperty(name);
                    if (property != null)
                    {
                        return property;
                    }
                }
            }
            catch (Exception)
            {
            }
            return null;
        }

        private static object GetInstanceProperty(object target, string name)
        {
            try
            {
                PropertyInfo property = FindProperty(target, name);
                return property == null ? null : property.GetValue(target, null);
            }
            catch (Exception)
            {
                return null;
            }
        }

        private static void SetInstanceProperty(object target, string name, object value)
        {
            try
            {
                PropertyInfo property = FindProperty(target, name);
                if (property != null && property.CanWrite)
                {
                    property.SetValue(target, value, null);
                }
            }
            catch (Exception)
            {
            }
        }

        public static void ApplyFrameBackground(bool white)
        {
            try
            {
                PhoneApplicationFrame frame = Application.Current.RootVisual as PhoneApplicationFrame;
                if (frame == null)
                {
                    return;
                }
                frame.ClearValue(Control.BackgroundProperty);
            }
            catch (Exception)
            {
            }
        }

        private static void SetStaticProperty(Type type, string name, object value)
        {
            try
            {
                PropertyInfo property = type.GetProperty(name);
                if (property != null && property.CanWrite)
                {
                    property.SetValue(null, value, null);
                }
            }
            catch (Exception)
            {
            }
        }


        private static void ApplyBrushes(bool white)
        {
            SaveBrushes();

            SetBrushColor("PhoneForegroundBrush", white ? Colors.Black : _foreground);
            SetBrushColor("PhoneSubtleBrush",
                white ? Color.FromArgb(0xFF, 0x40, 0x40, 0x40) : _subtle);
            SetBrushColor("PhoneInactiveBrush",
                white ? Color.FromArgb(0xFF, 0x60, 0x60, 0x60) : _inactive);
            SetBrushColor("PhoneDisabledBrush",
                white ? Color.FromArgb(0xFF, 0x90, 0x90, 0x90) : _disabled);
            SetBrushColor("PhoneTextBoxBrush",
                white ? Color.FromArgb(0xFF, 0xF2, 0xF2, 0xF2) : _textBox);
        }

        private static Color GetColorResource(string key, Color fallback)
        {
            try
            {
                object value = Application.Current.Resources[key];
                if (value is Color)
                {
                    return (Color)value;
                }
            }
            catch (Exception)
            {
            }
            return fallback;
        }

        private static void SaveBrushes()
        {
            if (_saved)
            {
                return;
            }
            _foreground = ReadColor("PhoneForegroundBrush", Colors.White);
            _subtle = ReadColor("PhoneSubtleBrush", Colors.Gray);
            _inactive = ReadColor("PhoneInactiveBrush", Colors.Gray);
            _disabled = ReadColor("PhoneDisabledBrush", Colors.Gray);
            _textBox = ReadColor("PhoneTextBoxBrush", Colors.White);
            _background = ReadColor("PhoneBackgroundBrush", Colors.Black);
            _saved = true;
        }

        private static Color ReadColor(string key, Color fallback)
        {
            SolidColorBrush brush = GetBrush(key);
            return brush == null ? fallback : brush.Color;
        }

        private static void SetBrushColor(string key, Color color)
        {
            SolidColorBrush brush = GetBrush(key);
            if (brush == null)
            {
                return;
            }
            try
            {
                brush.Color = color;
            }
            catch (Exception)
            {
            }
        }

        private static SolidColorBrush GetBrush(string key)
        {
            try
            {
                return Application.Current.Resources[key] as SolidColorBrush;
            }
            catch (Exception)
            {
                return null;
            }
        }


        private static bool CanTheme(Brush brush)
        {
            SolidColorBrush solid = brush as SolidColorBrush;
            if (solid == null)
            {
                return false;
            }
            return solid.Color == Colors.Transparent || solid.Color == BackgroundColor;
        }

        private static void ApplyPivot(Pivot pivot, bool white)
        {
            if (pivot == null)
            {
                return;
            }

            if (white)
            {
                pivot.Background = new SolidColorBrush(Colors.Transparent);
            }
            else
            {
                pivot.ClearValue(Control.BackgroundProperty);
            }

            for (int i = 0; i < pivot.Items.Count; i++)
            {
                PivotItem item = pivot.Items[i] as PivotItem;
                if (item == null)
                {
                    continue;
                }

                if (white)
                {
                    item.Background = new SolidColorBrush(Colors.Transparent);
                }
                else
                {
                    item.ClearValue(Control.BackgroundProperty);
                }


                string text = item.Header as string;
                if (text != null)
                {
                    TextBlock block = new TextBlock();
                    block.Text = text;
                    item.Header = block;
                }
                TextBlock header = item.Header as TextBlock;
                if (header != null)
                {
                    header.FontWeight = FontWeights.Black;
                    header.FontSize = 50;
                    if (white)
                    {
                        header.Foreground = new SolidColorBrush(PivotColor);
                    }
                    else
                    {
                        header.ClearValue(TextBlock.ForegroundProperty);
                    }
                }
            }

            ReduceHeaderHeight(pivot);
        }

        private static void ReduceHeaderHeight(DependencyObject node)
        {
            if (node == null)
            {
                return;
            }

            if (node.GetType().Name == "PivotHeaderItem")
            {
                Control control = node as Control;
                if (control != null)
                {
                    control.MinHeight = 72;
                }
            }

            int count = VisualTreeHelper.GetChildrenCount(node);
            for (int i = 0; i < count; i++)
            {
                ReduceHeaderHeight(VisualTreeHelper.GetChild(node, i));
            }
        }

        private static void CollectTitles(DependencyObject node, List<TextBlock> result)
        {
            if (node == null)
            {
                return;
            }

            TextBlock block = node as TextBlock;
            if (block != null && IsTitleStyle(block.Style))
            {
                result.Add(block);
            }

            int count = VisualTreeHelper.GetChildrenCount(node);
            for (int i = 0; i < count; i++)
            {
                CollectTitles(VisualTreeHelper.GetChild(node, i), result);
            }
        }

        private static bool IsTitleStyle(Style style)
        {
            if (style == null)
            {
                return false;
            }
            return style == GetStyle("PhoneTextTitle1Style")
                || style == GetStyle("PhoneTextTitle2Style");
        }

        private static Style GetStyle(string key)
        {
            try
            {
                return Application.Current.Resources[key] as Style;
            }
            catch (Exception)
            {
                return null;
            }
        }

        private static void CollectPivots(DependencyObject node, List<Pivot> result)
        {
            if (node == null)
            {
                return;
            }

            Pivot pivot = node as Pivot;
            if (pivot != null)
            {
                result.Add(pivot);
            }

            int count = VisualTreeHelper.GetChildrenCount(node);
            for (int i = 0; i < count; i++)
            {
                CollectPivots(VisualTreeHelper.GetChild(node, i), result);
            }
        }
    }
}
