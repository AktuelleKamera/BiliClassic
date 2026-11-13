using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

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

        private readonly List<ViewerItem> _items = new List<ViewerItem>();
        private bool _loaded;

        public ImageViewerPage()
        {
            InitializeComponent();
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
    }
}
