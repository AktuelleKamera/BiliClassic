using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using Microsoft.Devices;
using Microsoft.Phone.Controls;
using Microsoft.Phone.Shell;
using Microsoft.Phone.Tasks;
using System.Windows.Shapes;

namespace BiliClassic
{
    public partial class MainPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<VideoItem> _items = new ObservableCollection<VideoItem>();

        private int _page = 1;
        private bool _hasMore;
        private bool _loading;

        private bool _mineLoaded;

        private bool _updateChecked;

        private bool _loggedIn;

        private bool _arrangeMode;

        private Border _selectedTile;

        private bool _longPressFired;
        private Point _pressPoint;
        private DispatcherTimer _longPressTimer;

        private bool _tilePressed;

        private bool _tileDragged;

        private const double TapSlop = 16;

        private static readonly Brush TileNormal =
            new SolidColorBrush(Color.FromArgb(0, 255, 255, 255));
        private static readonly Brush TileSelected =
            new SolidColorBrush(Color.FromArgb(0, 255, 255, 255));

        public MainPage()
        {
            InitializeComponent();
            RecommendList.ItemsSource = _items;
            Loaded += OnLoaded;

            BottomAutoLoader.Attach(RecommendList, delegate
            {
                MoreButton_Click(null, null);
            });

            _loggedIn = BiliSession.IsLoggedIn;
            BuildAppBar();
            ApplyTileOrder();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            RestoreDefaultTransitions();

            bool now = BiliSession.IsLoggedIn;
            if (now != _loggedIn)
            {
                _loggedIn = now;
                BuildAppBar();
                RefreshAfterLoginChange();
            }
            else if (HomePivot.SelectedIndex == 0 && !_mineLoaded)
            {
                _mineLoaded = true;
                LoadMineCard();
            }

            if (_notices != null && _noticeIndex < _notices.Count
                && NoticeOverlay.Visibility != Visibility.Visible)
            {
                ShowNextNotice();
            }
        }


        private int _logoClicks;
        private DispatcherTimer _logoResetTimer;

        private void TitlePanel_Tap(object sender, MouseButtonEventArgs e)
        {
            _logoClicks++;
            if (_logoClicks == 1)
            {
                if (_logoResetTimer == null)
                {
                    _logoResetTimer = new DispatcherTimer();
                    _logoResetTimer.Interval = TimeSpan.FromSeconds(2);
                    _logoResetTimer.Tick += delegate
                    {
                        _logoResetTimer.Stop();
                        _logoClicks = 0;
                    };
                }
                _logoResetTimer.Stop();
                _logoResetTimer.Start();
            }
            else if (_logoClicks >= 5)
            {
                _logoClicks = 0;
                if (_logoResetTimer != null)
                {
                    _logoResetTimer.Stop();
                }
                TriggerSpaceQuake();
            }
        }

        private void TriggerSpaceQuake()
        {
            try
            {
                VibrateController.Default.Start(TimeSpan.FromMilliseconds(500));
            }
            catch (Exception)
            {
            }

            QuakeOverlay.Visibility = Visibility.Visible;

            DispatcherTimer timer = new DispatcherTimer();
            timer.Interval = TimeSpan.FromMilliseconds(600);
            timer.Tick += delegate
            {
                timer.Stop();
                QuakeOverlay.Visibility = Visibility.Collapsed;
            };
            timer.Start();
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            if (!_launchAnimated)
            {
                _launchAnimated = true;
                PlayLaunchAnimation();
            }

            if (_items.Count == 0 && !_loading)
            {
                Refresh();
                CheckUpdate();
            }
            CheckAnnouncements();
        }

        private bool _launchAnimated;

        private void PlayLaunchAnimation()
        {
            try
            {
                TranslateTransform transform = new TranslateTransform();
                LayoutRoot.RenderTransform = transform;
                double distance = ActualHeight > 0 ? ActualHeight : 800;

                DoubleAnimation slide = new DoubleAnimation
                {
                    From = distance,
                    To = 0,
                    Duration = TimeSpan.FromMilliseconds(450),
                    EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                };
                Storyboard.SetTarget(slide, transform);
                Storyboard.SetTargetProperty(slide, new PropertyPath("Y"));

                DoubleAnimation fade = new DoubleAnimation
                {
                    From = 0,
                    To = 1,
                    Duration = TimeSpan.FromMilliseconds(450)
                };
                Storyboard.SetTarget(fade, LayoutRoot);
                Storyboard.SetTargetProperty(fade, new PropertyPath("Opacity"));

                Storyboard storyboard = new Storyboard();
                storyboard.Children.Add(slide);
                storyboard.Children.Add(fade);
                storyboard.Begin();
            }
            catch (Exception)
            {
            }
        }

        private static bool _noticeChecked;
        private List<Announcement> _notices;
        private int _noticeIndex;

        private void CheckAnnouncements()
        {
            if (_noticeChecked)
            {
                return;
            }
            _noticeChecked = true;

            AnnouncementService.Fetch(delegate(List<Announcement> list)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _notices = list;
                    _noticeIndex = 0;
                    ShowNextNotice();
                }));
            });
        }

        private Announcement CurrentNotice()
        {
            if (_notices == null || _noticeIndex >= _notices.Count)
            {
                return null;
            }
            return _notices[_noticeIndex];
        }

        private void ShowNextNotice()
        {
            Announcement a = CurrentNotice();
            if (a == null)
            {
                NoticeOverlay.Visibility = Visibility.Collapsed;
                return;
            }

            NoticeTitle.Text = a.Title;
            NoticeContent.Text = a.Content;

            NoticeVideoButton.Visibility =
                a.VideoUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            NoticeVideoButton.Content = a.VideoText.Length > 0 ? a.VideoText : "观看视频";

            NoticeAudioButton.Visibility =
                a.AudioUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            NoticeAudioButton.Content = a.AudioText.Length > 0 ? a.AudioText : "播放音频";

            NoticeLinkButton.Visibility =
                a.LinkUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            NoticeLinkButton.Content = a.LinkText.Length > 0 ? a.LinkText : "查看链接";

            NoticeDetailButton.Visibility =
                a.Url.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            NoticeNeverButton.Visibility =
                a.ForceShow ? Visibility.Collapsed : Visibility.Visible;

            NoticeDismissButton.Content = a.ButtonText.Length > 0 ? a.ButtonText : "知道了";

            NoticeOverlay.Visibility = Visibility.Visible;
        }

        private void NoticeVideo_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a == null)
            {
                return;
            }

            NoticeOverlay.Visibility = Visibility.Collapsed;
            AnnouncementService.MarkShown(a.Id);
            _noticeIndex++;

            string bvid = BiliId.ToBvid(a.VideoUrl);
            if (bvid.Length > 0)
            {
                NavigationService.Navigate(new Uri(
                    "/VideoDetailPage.xaml?bvid=" + bvid, UriKind.Relative));
                return;
            }

            Uri uri;
            if (!Uri.TryCreate(a.VideoUrl, UriKind.Absolute, out uri))
            {
                return;
            }

            NavigationService.Navigate(new Uri(
                "/VideoPlayerPage.xaml?src=" + Uri.EscapeDataString(a.VideoUrl)
                + "&title=" + Uri.EscapeDataString(a.Title), UriKind.Relative));
        }

        private void NoticeAudio_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a == null)
            {
                return;
            }

            Uri uri;
            if (!Uri.TryCreate(a.AudioUrl, UriKind.Absolute, out uri))
            {
                return;
            }

            NoticeOverlay.Visibility = Visibility.Collapsed;
            AnnouncementService.MarkShown(a.Id);
            _noticeIndex++;

            NavigationService.Navigate(new Uri(
                "/AudioPlayerPage.xaml?src=" + Uri.EscapeDataString(a.AudioUrl)
                + "&text=" + Uri.EscapeDataString(a.Title)
                + "&dis=" + Uri.EscapeDataString(a.AudioDisText), UriKind.Relative));
        }

        private void NoticeDismiss_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a != null && a.ShowOnce)
            {
                AnnouncementService.MarkShown(a.Id);
            }
            _noticeIndex++;
            ShowNextNotice();
        }

        private void NoticeDetail_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a == null || a.Url.Length == 0)
            {
                return;
            }

            WebBrowserTask task = new WebBrowserTask();
#if WP8
            task.Uri = new Uri(a.Url);
#else
            task.URL = a.Url;
#endif
            task.Show();
        }

        private void NoticeLink_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a == null || a.LinkUrl.Length == 0)
            {
                return;
            }

            WebBrowserTask task = new WebBrowserTask();
#if WP8
            task.Uri = new Uri(a.LinkUrl);
#else
            task.URL = a.LinkUrl;
#endif
            task.Show();
        }

        private void NoticeNever_Click(object sender, RoutedEventArgs e)
        {
            Announcement a = CurrentNotice();
            if (a != null)
            {
                AnnouncementService.MarkDismissed(a.Id);
            }
            _noticeIndex++;
            ShowNextNotice();
        }

        private void CheckUpdate()
        {
            if (_updateChecked || !AppSettings.AutoCheckUpdate)
            {
                return;
            }
            _updateChecked = true;

            UpdateChecker.Check(true, null);
        }

        private void SearchBarButton_Click(object sender, EventArgs e)
        {
            NavigationOutTransition outTransition = new NavigationOutTransition();
            outTransition.Backward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.BackwardOut
            };
            outTransition.Forward = new SlideTransition
            {
                Mode = SlideTransitionMode.SlideDownFadeOut
            };
            TransitionService.SetNavigationOutTransition(this, outTransition);

            NavigationInTransition inTransition = new NavigationInTransition();
            inTransition.Backward = new SwivelTransition
            {
                Mode = SwivelTransitionMode.BackwardIn
            };
            inTransition.Forward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.ForwardIn
            };
            TransitionService.SetNavigationInTransition(this, inTransition);

            NavigationService.Navigate(new Uri("/SearchPage.xaml", UriKind.Relative));
        }

        private void NavigateWithSwivel(string page)
        {
            NavigationOutTransition outTransition = new NavigationOutTransition();
            outTransition.Backward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.BackwardOut
            };
            outTransition.Forward = new SwivelTransition
            {
                Mode = SwivelTransitionMode.ForwardOut
            };
            TransitionService.SetNavigationOutTransition(this, outTransition);

            NavigationInTransition inTransition = new NavigationInTransition();
            inTransition.Backward = new SwivelTransition
            {
                Mode = SwivelTransitionMode.BackwardIn
            };
            inTransition.Forward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.ForwardIn
            };
            TransitionService.SetNavigationInTransition(this, inTransition);

            NavigationService.Navigate(new Uri(page, UriKind.Relative));
        }

        private void RestoreDefaultTransitions()
        {
            NavigationOutTransition outTransition = new NavigationOutTransition();
            outTransition.Backward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.BackwardOut
            };
            outTransition.Forward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.ForwardOut
            };
            TransitionService.SetNavigationOutTransition(this, outTransition);

            NavigationInTransition inTransition = new NavigationInTransition();
            inTransition.Backward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.BackwardIn
            };
            inTransition.Forward = new TurnstileTransition
            {
                Mode = TurnstileTransitionMode.ForwardIn
            };
            TransitionService.SetNavigationInTransition(this, inTransition);
        }

        private void BuildAppBar()
        {
            ApplicationBar bar = new ApplicationBar();
            bar.IsVisible = true;
            bar.IsMenuEnabled = true;

            AddIcon(bar, "/Res/Icon/search.png", "搜索", SearchBarButton_Click);
            AddIcon(bar, "/Res/Icon/refresh.png", "刷新", RefreshMenuItem_Click);
            AddIcon(bar, "/Res/Icon/settings.png", "设置", SettingsMenuItem_Click);

            bool loggedIn = BiliSession.IsLoggedIn;
            AddIcon(bar, loggedIn ? "/Res/Icon/logout.png" : "/Res/Icon/login.png",
                loggedIn ? "登出" : "登录", LoginButton_Click);

            bar.MenuItems.Add(MakeMenu("回声洞", EchoHoleMenuItem_Click));
            bar.MenuItems.Add(MakeMenu("关于", AboutMenuItem_Click));

            ApplicationBar = bar;
            ThemeHelper.ApplyPage(this);
        }

        private static void AddIcon(ApplicationBar bar, string icon, string text, EventHandler handler)
        {
            ApplicationBarIconButton button =
                new ApplicationBarIconButton(new Uri(icon, UriKind.Relative));
            button.Text = text;
            button.Click += handler;
            bar.Buttons.Add(button);
        }

        private static ApplicationBarMenuItem MakeMenu(string text, EventHandler handler)
        {
            ApplicationBarMenuItem item = new ApplicationBarMenuItem(text);
            item.Click += handler;
            return item;
        }

        private void LoginButton_Click(object sender, EventArgs e)
        {
            if (!BiliSession.IsLoggedIn)
            {
                NavigationService.Navigate(new Uri("/LoginPage.xaml", UriKind.Relative));
                return;
            }

            MessageBoxResult answer = MessageBox.Show(
                "要退出登录吗？", "退出登录", MessageBoxButton.OKCancel);
            if (answer != MessageBoxResult.OK)
            {
                return;
            }

            BiliSession.Clear();
            _loggedIn = false;
            BuildAppBar();
            RefreshAfterLoginChange();
        }

        private void RefreshAfterLoginChange()
        {
            _mineLoaded = false;
            Refresh();
            if (HomePivot.SelectedIndex == 0)
            {
                _mineLoaded = true;
                LoadMineCard();
            }
        }

        private void SettingsMenuItem_Click(object sender, EventArgs e)
        {
            NavigateWithSwivel("/SettingsPage.xaml");
        }

        private void EchoHoleMenuItem_Click(object sender, EventArgs e)
        {
            EchoHoleService.FetchRandom(delegate(string message, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    PlayEchoSound();
                    if (!string.IsNullOrEmpty(message))
                    {
                        MessageBox.Show(message, "回声洞", MessageBoxButton.OK);
                        return;
                    }
                    MessageBox.Show(
                        string.IsNullOrEmpty(error) ? "回声洞暂无内容" : error,
                        "回声洞", MessageBoxButton.OK);
                }));
            });
        }

        private void PlayEchoSound()
        {
#if WP8
            try
            {
                if (EchoSoundPlayer.Source == null)
                {
                    EchoSoundPlayer.Source = new Uri("Res/echo.wav", UriKind.Relative);
                }
                EchoSoundPlayer.Stop();
                EchoSoundPlayer.Play();
            }
            catch (Exception)
            {
            }
#endif
        }

        private void AboutMenuItem_Click(object sender, EventArgs e)
        {
            NavigateWithSwivel("/AboutPage.xaml");
        }

        private void HomePivot_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (HomePivot.SelectedIndex == 0 && !_mineLoaded)
            {
                _mineLoaded = true;
                LoadMineCard();
            }
        }

        private void LoadMineCard()
        {
            if (!BiliSession.IsLoggedIn)
            {
                ShowMineLoggedOut();
                return;
            }

            MineStatus.Text = "正在加载…";

            MineService.FetchCard(delegate(MineCard card, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!card.LoggedIn)
                    {
                        ShowMineLoggedOut();
                        return;
                    }

                    MineName.Text = card.Name;
                    MineStatus.Text = "";

                    if (!string.IsNullOrEmpty(card.Face))
                    {
                        UserItem self = new UserItem();
                        self.AvatarUrl = card.Face;
                        self.PropertyChanged += delegate
                        {
                            AvatarImage.Source = self.Avatar;
                        };
                        AvatarLoader.Retry(self);
                    }
                }));
            });
        }

        private void ShowMineLoggedOut()
        {
            MineName.Text = "未登录";
            MineStatus.Text = "";
            //AvatarImage.Source = null;
            AvatarImage.Source = new BitmapImage(new Uri("/BiliClassic;component/Res/Tile/user.png", UriKind.Relative)); ;
        }


        private void ApplyTileOrder()
        {
            string saved = LocalStore.Get("mine_order2");
            if (string.IsNullOrEmpty(saved))
            {
                return;
            }

            List<Border> ordered = new List<Border>();
            string[] tags = saved.Split(',');
            for (int i = 0; i < tags.Length; i++)
            {
                Border tile = FindTileByTag(tags[i].Trim());
                if (tile != null && !ordered.Contains(tile))
                {
                    ordered.Add(tile);
                }
            }

            for (int i = 0; i < MineTiles.Children.Count; i++)
            {
                Border tile = MineTiles.Children[i] as Border;
                if (tile != null && !ordered.Contains(tile))
                {
                    ordered.Add(tile);
                }
            }

            MineTiles.Children.Clear();
            for (int i = 0; i < ordered.Count; i++)
            {
                MineTiles.Children.Add(ordered[i]);
            }
        }

        private void SaveTileOrder()
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MineTiles.Children.Count; i++)
            {
                Border tile = MineTiles.Children[i] as Border;
                if (tile == null)
                {
                    continue;
                }
                if (sb.Length > 0)
                {
                    sb.Append(',');
                }
                sb.Append(tile.Tag as string);
            }
            LocalStore.Set("mine_order2", sb.ToString());
        }

        private Border FindTileByTag(string tag)
        {
            for (int i = 0; i < MineTiles.Children.Count; i++)
            {
                Border tile = MineTiles.Children[i] as Border;
                if (tile != null && (tile.Tag as string) == tag)
                {
                    return tile;
                }
            }
            return null;
        }

        private Border FindTile(DependencyObject source)
        {
            while (source != null && source != MineTiles)
            {
                Border tile = source as Border;
                if (tile != null && tile.Parent == MineTiles)
                {
                    return tile;
                }
                source = VisualTreeHelper.GetParent(source);
            }
            return null;
        }

        private void MineTiles_Down(object sender, MouseButtonEventArgs e)
        {
            Border tile = FindTile(e.OriginalSource as DependencyObject);
            if (tile == null)
            {
                return;
            }

            if (_arrangeMode)
            {
                ArrangeTap(tile);
                return;
            }

            _longPressFired = false;
            _tilePressed = true;
            _tileDragged = false;
            _pressPoint = e.GetPosition(null);
            if (_longPressTimer == null)
            {
                _longPressTimer = new DispatcherTimer();
                _longPressTimer.Interval = TimeSpan.FromMilliseconds(650);
                _longPressTimer.Tick += LongPressTick;
            }
            _longPressTimer.Stop();
            _longPressTimer.Start();
        }

        private void MineTiles_Move(object sender, MouseEventArgs e)
        {
            if (!_tilePressed)
            {
                return;
            }

            Point now = e.GetPosition(null);
            if (Math.Abs(now.X - _pressPoint.X) > TapSlop
                || Math.Abs(now.Y - _pressPoint.Y) > TapSlop)
            {
                _tileDragged = true;
                if (_longPressTimer != null)
                {
                    _longPressTimer.Stop();
                }
            }
        }

        private void LongPressTick(object sender, EventArgs e)
        {
            _longPressTimer.Stop();
            if (_arrangeMode)
            {
                return;
            }
            _longPressFired = true;
            EnterArrangeMode();
        }

        private void MineTiles_Up(object sender, MouseButtonEventArgs e)
        {
            if (_longPressTimer != null)
            {
                _longPressTimer.Stop();
            }
            _tilePressed = false;

            if (_arrangeMode)
            {
                return;
            }

            if (_longPressFired)
            {
                _longPressFired = false;
                _tileDragged = false;
                return;
            }

            Point up = e.GetPosition(null);
            bool moved = Math.Abs(up.X - _pressPoint.X) > TapSlop
                || Math.Abs(up.Y - _pressPoint.Y) > TapSlop;
            if (_tileDragged || moved)
            {
                _tileDragged = false;
                return;
            }
            _tileDragged = false;

            Border tile = FindTile(e.OriginalSource as DependencyObject);
            if (tile != null)
            {
                NavigateTile(tile.Tag as string);
            }
        }

        private void EnterArrangeMode()
        {
            _arrangeMode = true;
            _selectedTile = null;
            MineArrangeHint.Visibility = Visibility.Visible;
        }

        private void ExitArrangeMode()
        {
            _arrangeMode = false;
            if (_selectedTile != null)
            {
                //_selectedTile.Background = TileNormal;
                ChangeTileSelected(_selectedTile, false);
                _selectedTile = null;
            }
            MineArrangeHint.Visibility = Visibility.Collapsed;
            SaveTileOrder();
        }

        private void MineArrangeHint_Tap(object sender, MouseButtonEventArgs e)
        {
            ExitArrangeMode();
        }

        private void ChangeTileSelected(Border tile, bool HideOrShow)
        {
            var grid = tile.Child as Grid;
            if (grid == null) return;

            foreach (var child in grid.Children)
            {
                if (child is Polygon)
                {
                    Polygon polygon = (Polygon)child;
                    if (!HideOrShow)//Hide
                    {
                        polygon.Visibility = Visibility.Collapsed;
                    }
                    else
                    {
                        polygon.Visibility = Visibility.Visible;
                    }
                    break;
                }
            }
        }

        private void ArrangeTap(Border tile)
        {
            if (_selectedTile == null)
            {
                _selectedTile = tile;
                //tile.Background = TileSelected;
                ChangeTileSelected(tile, true);
                ChangeTileSelected(_selectedTile, true);
                return;
            }
            if (_selectedTile == tile)
            {
                //tile.Background = TileNormal;
                ChangeTileSelected(tile, false);
                ChangeTileSelected(_selectedTile, false);
                _selectedTile = null;
                return;
            }

            SwapTiles(_selectedTile, tile);
            //_selectedTile.Background = TileNormal;
            ChangeTileSelected(tile, false);
            ChangeTileSelected(_selectedTile, false);
            _selectedTile = null;
            SaveTileOrder();
        }

        private void SwapTiles(Border a, Border b)
        {
            int indexA = MineTiles.Children.IndexOf(a);
            int indexB = MineTiles.Children.IndexOf(b);
            if (indexA < 0 || indexB < 0)
            {
                return;
            }

            int first = Math.Min(indexA, indexB);
            int second = Math.Max(indexA, indexB);
            MineTiles.Children.Remove(a);
            MineTiles.Children.Remove(b);
            MineTiles.Children.Insert(first, b);
            MineTiles.Children.Insert(second, a);
        }

        private void NavigateTile(string tag)
        {
            if (string.IsNullOrEmpty(tag))
            {
                return;
            }

            if (tag == "avatar")
            {
                if (!BiliSession.IsLoggedIn)
                {
                    NavigationService.Navigate(new Uri("/LoginPage.xaml", UriKind.Relative));
                    return;
                }
                if (string.IsNullOrEmpty(BiliSession.Mid))
                {
                    return;
                }
                NavigationService.Navigate(new Uri(
                    "/UserProfilePage.xaml?mid=" + BiliSession.Mid, UriKind.Relative));
                return;
            }
            if (tag == "private")
            {
                NavigationService.Navigate(new Uri("/PrivateMsgPage.xaml", UriKind.Relative));
                return;
            }
            if (tag == "watchlater")
            {
                NavigationService.Navigate(new Uri("/WatchLaterPage.xaml", UriKind.Relative));
                return;
            }
            if (tag == "local")
            {
                NavigationService.Navigate(new Uri("/OfflinePage.xaml", UriKind.Relative));
                return;
            }
            if (tag == "follow")
            {
                NavigationService.Navigate(new Uri("/FollowDynamicPage.xaml", UriKind.Relative));
                return;
            }
            if (tag == "random")
            {
                OpenRandomVideo();
                return;
            }
            if (tag == "rank")
            {
                NavigationService.Navigate(new Uri("/RankingPage.xaml", UriKind.Relative));
                return;
            }

            GoMine(tag);
        }

        private void OpenRandomVideo()
        {
            MineStatus.Text = "正在抽一个视频…";

            RecommendService.Fetch(1, delegate(RecommendResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (result == null || result.Items.Count == 0)
                    {
                        MineStatus.Text = (result == null || string.IsNullOrEmpty(result.Error))
                            ? "这次没抽到，再试试吧"
                            : result.Error;
                        return;
                    }

                    Random random = new Random();
                    VideoItem item = result.Items[random.Next(result.Items.Count)];
                    MineStatus.Text = "";

                    if (!string.IsNullOrEmpty(item.Bvid))
                    {
                        NavigationService.Navigate(new Uri(
                            "/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
                    }
                }));
            });
        }

        private void GoMine(string mode)
        {
            NavigationService.Navigate(new Uri(
                "/MineListPage.xaml?mode=" + mode, UriKind.Relative));
        }

        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            if (HomePivot.SelectedIndex == 0)
            {
                LoadMineCard();
                return;
            }

            Refresh();
        }

        private void MoreButton_Click(object sender, RoutedEventArgs e)
        {
            if (_loading || !_hasMore)
            {
                return;
            }
            _page++;
            Fetch();
        }

        private void Refresh()
        {
            LoadPage(1);
        }

        private void LoadPage(int page)
        {
            if (_loading)
            {
                return;
            }

            _page = page < 1 ? 1 : page;
            _hasMore = false;
            _items.Clear();
            MoreButton.Visibility = Visibility.Collapsed;
            Fetch();
        }

        private void Fetch()
        {
            _loading = true;
            MoreButton.IsEnabled = false;
            ShowStatus("正在加载推荐…");

            int page = _page;
            RecommendService.Fetch(page, delegate(RecommendResult result)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;
                    MoreButton.IsEnabled = true;
                    ApplyResult(result);
                }));
            });
        }

        private void ApplyResult(RecommendResult result)
        {
            int added = 0;
            for (int i = 0; i < result.Items.Count; i++)
            {
                VideoItem item = result.Items[i];
                if (ContainsBvid(item.Bvid))
                {
                    continue;
                }
                _items.Add(item);
                added++;

                CoverLoader.Request(item);
            }

            _hasMore = result.HasMore && added > 0;

            if (!string.IsNullOrEmpty(result.Error))
            {
                ShowStatus(result.Error);
            }
            else if (_items.Count == 0)
            {
                ShowStatus("没有拿到推荐内容");
            }
            else
            {
                StatusText.Visibility = Visibility.Collapsed;
            }

            MoreButton.Visibility = Visibility.Collapsed;
        }

        private bool ContainsBvid(string bvid)
        {
            for (int i = 0; i < _items.Count; i++)
            {
                if (_items[i].Bvid == bvid)
                {
                    return true;
                }
            }
            return false;
        }

        private void RecommendList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            VideoItem item = RecommendList.SelectedItem as VideoItem;
            if (item == null)
            {
                return;
            }
            RecommendList.SelectedIndex = -1;

            if (string.IsNullOrEmpty(item.Bvid))
            {
                return;
            }
            NavigationService.Navigate(new Uri("/VideoDetailPage.xaml?bvid=" + item.Bvid, UriKind.Relative));
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
