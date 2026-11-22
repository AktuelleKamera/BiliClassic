using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;
using Microsoft.Xna.Framework.Media;
using System.IO;
using System.Windows.Controls;
using Microsoft.Phone.Shell;

namespace BiliClassic
{
    public sealed class ViewerItem : INotifyPropertyChanged
    {
        private BitmapImage _image;

        public event PropertyChangedEventHandler PropertyChanged;

        public string Url { get; set; }

        public string Caption { get; set; }

        public BitmapImage Image
        {
            get { return _image; }
            set
            {
                if (ReferenceEquals(_image, value))
                {
                    return;
                }
                _image = value;

                PropertyChangedEventHandler handler = PropertyChanged;
                if (handler != null)
                {
                    handler(this, new PropertyChangedEventArgs("Image"));
                }
            }
        }
    }

    public partial class ImageViewerPage : PhoneApplicationPage
    {
        private static List<PictureItem> _pendingImages;
        private static int _pendingIndex;
        private static int _nowIndex;

        private readonly List<ViewerItem> _items = new List<ViewerItem>();
        private bool _loaded;

        public ImageViewerPage()
        {
            InitializeComponent();
            this.Loaded += ImageViewerPage_Loaded;
        }

        private void ImageViewerPage_Loaded(object sender, RoutedEventArgs e)
        {
            SystemTray.IsVisible = false; 
            this.ApplicationBar.BackgroundColor = System.Windows.Media.Colors.Black;
            Dispatcher.BeginInvoke(() =>
            {
                SystemTray.IsVisible = false;
                this.ApplicationBar.BackgroundColor = System.Windows.Media.Colors.Black;
            });
        }

        public static void Show(List<PictureItem> images, int index)
        {
            if (images == null || images.Count == 0)
            {
                return;
            }

            _pendingImages = images;
            _pendingIndex = index;

            PhoneApplicationFrame frame = Application.Current.RootVisual as PhoneApplicationFrame;
            if (frame != null)
            {
                frame.Navigate(new Uri("/ImageViewerPage.xaml", UriKind.Relative));
            }
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            SystemTray.IsVisible = false;
            this.ApplicationBar.BackgroundColor = System.Windows.Media.Colors.Black;
            Dispatcher.BeginInvoke(() =>
            {
                SystemTray.IsVisible = false;
                this.ApplicationBar.BackgroundColor = System.Windows.Media.Colors.Black;
            });

            if (_loaded)
            {
                return;
            }
            _loaded = true;

            List<PictureItem> source = _pendingImages;
            _pendingImages = null;
            int index = _pendingIndex;
            _pendingIndex = 0;

            if (source == null || source.Count == 0)
            {
                return;
            }

            for (int i = 0; i < source.Count; i++)
            {
                ViewerItem item = new ViewerItem();
                item.Url = source[i].Url;
                item.Caption = "第 " + (i + 1) + "/" + source.Count + " 张";
                _items.Add(item);

                ImageLoader.Load(VideoItem.BuildThumbUrl(item.Url, 1280, 1280),
                    delegate(BitmapImage bitmap) { item.Image = bitmap; });
            }

            ViewerPivot.ItemsSource = _items;

            if (index < 0)
            {
                index = 0;
            }
            if (index >= _items.Count)
            {
                index = _items.Count - 1;
            }
            ViewerPivot.SelectedIndex = index;
        }

        private void SaveImage_Click(object sender, EventArgs e)
        {
            try
            {
                WriteableBitmap wb = new WriteableBitmap(_items[_nowIndex].Image);
                System.Diagnostics.Debug.WriteLine(_nowIndex);
                string path_temp = "biliclassic_" + DateTime.Now.ToString("yyyy_MM_dd") + "_" + DateTime.Now.ToString("HH_mm_ss") + ".jpg";
                using (MemoryStream ms = new MemoryStream())
                {
                    wb.SaveJpeg(ms, wb.PixelWidth, wb.PixelHeight, 0, 85);
                    ms.Position = 0;
                    MediaLibrary library = new MediaLibrary();
                    library.SavePicture(path_temp, ms);
                }
                MessageBox.Show("已保存到" + path_temp, "保存成功", MessageBoxButton.OK);
            }
            catch (Exception ex)
            {
                MessageBox.Show("错误：" + ex.Message, "保存失败", MessageBoxButton.OK);
            }
        }

        private void ViewerPivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            Pivot pivot = sender as Pivot;
            if (pivot != null)
            {
                _nowIndex = pivot.SelectedIndex;
            }
        }
    }
}
