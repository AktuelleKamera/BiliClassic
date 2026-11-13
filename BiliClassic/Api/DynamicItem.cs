using System.Collections.Generic;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public sealed class DynamicItem : INotifyPropertyChanged
    {
        private BitmapImage _avatar;
        private BitmapImage _cardCover;
        private BitmapImage _forwardCardCover;

        public event PropertyChangedEventHandler PropertyChanged;

        public string DynamicId { get; set; }

        public string Type { get; set; }

        public string Mid { get; set; }

        public string Author { get; set; }

        public string AvatarUrl { get; set; }

        public string PubTime { get; set; }

        public string Content { get; set; }

        public string CommentId { get; set; }

        public int CommentType { get; set; }

        public List<PictureItem> Pics { get; private set; }

        public string CardLabel { get; set; }

        public string CardTitle { get; set; }

        public string CardInfo { get; set; }

        public string CardCoverUrl { get; set; }

        public string CardBvid { get; set; }

        public string CardAid { get; set; }

        public string CardEpid { get; set; }

        public string CardRoomId { get; set; }

        public string CardArticleId { get; set; }

        public DynamicItem Forward { get; set; }

        public int LikeCount { get; set; }

        public bool Liked { get; set; }

        public DynamicItem()
        {
            Pics = new List<PictureItem>();
            DynamicId = "";
            Type = "";
            Mid = "";
            Author = "";
            AvatarUrl = "";
            PubTime = "";
            Content = "";
            CommentId = "";
            CommentType = 17;
            CardLabel = "";
            CardTitle = "";
            CardInfo = "";
            CardCoverUrl = "";
            CardBvid = "";
            CardAid = "";
            CardEpid = "";
            CardRoomId = "";
            CardArticleId = "";
        }

        public BitmapImage Avatar
        {
            get { return _avatar; }
            set
            {
                if (ReferenceEquals(_avatar, value))
                {
                    return;
                }
                _avatar = value;
                Raise("Avatar");
            }
        }

        public BitmapImage CardCover
        {
            get { return _cardCover; }
            set
            {
                if (ReferenceEquals(_cardCover, value))
                {
                    return;
                }
                _cardCover = value;
                Raise("CardCover");
            }
        }

        public BitmapImage ForwardCardCover
        {
            get { return _forwardCardCover; }
            set
            {
                if (ReferenceEquals(_forwardCardCover, value))
                {
                    return;
                }
                _forwardCardCover = value;
                Raise("ForwardCardCover");
            }
        }

        public Visibility ContentVisibility
        {
            get { return string.IsNullOrEmpty(Content) ? Visibility.Collapsed : Visibility.Visible; }
        }

        public Visibility PicsVisibility
        {
            get { return Pics.Count == 0 ? Visibility.Collapsed : Visibility.Visible; }
        }

        public Visibility CardVisibility
        {
            get
            {
                bool empty = string.IsNullOrEmpty(CardTitle) && string.IsNullOrEmpty(CardCoverUrl);
                return empty ? Visibility.Collapsed : Visibility.Visible;
            }
        }

        public Visibility ForwardVisibility
        {
            get { return Forward == null ? Visibility.Collapsed : Visibility.Visible; }
        }

        public string ForwardAuthorLine
        {
            get { return Forward == null ? "" : "@" + Forward.Author; }
        }

        public string FooterText
        {
            get { return LikeCount > 0 ? "赞 " + LikeCount : ""; }
        }

        public Visibility FooterVisibility
        {
            get { return LikeCount > 0 ? Visibility.Visible : Visibility.Collapsed; }
        }

        private void Raise(string name)
        {
            PropertyChangedEventHandler handler = PropertyChanged;
            if (handler != null)
            {
                handler(this, new PropertyChangedEventArgs(name));
            }
        }
    }
}
