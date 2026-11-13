using System.Collections.Generic;
using System.ComponentModel;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public sealed class PictureItem : INotifyPropertyChanged
    {
        private BitmapImage _image;

        public event PropertyChangedEventHandler PropertyChanged;

        public string Url { get; set; }

        /// <summary>归属的图片组，点开查看时用</summary>
        public List<PictureItem> Group { get; set; }

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
}
