using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using BiliClassic.Danmaku;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class VideoPlayerPage : PhoneApplicationPage
    {
        private static readonly string[] Platforms = new string[] { "html5", "pc" };

        private readonly List<string> _candidates = new List<string>();

        private readonly List<string> _allCandidates = new List<string>();

        private int _downloadIndex;

        private IsolatedStorageFileStream _playbackStream;

        private string _bvid = "";
        private string _cid = "";
        private bool _loaded;
        private bool _directMode;

        private bool _offlineForced;

        private bool OfflineMode
        {
            get { return AppSettings.OfflinePlayback || _offlineForced; }
        }

        private string _aid = "";

        private int _seasonType;

        private string _bangumiTitle = "";

        private int _reportedSeconds;

        private string _mediaFileName = "";

        private int _platformIndex;
        private int _candidateIndex;
        private int _failureCount;

        private string _tryingUrl = "";
        private string _lastFailedUrl = "";

        private DispatcherTimer _statusTimer;

        private DispatcherTimer _watchdog;

        private DateTime _sourceSetAt;
        private bool _opened;

        private double _lastBufferProgress = -1;

        private string _tryingPlatform = "";

        private int _streamAttempts;

        private string _lastFailure = "";

        private string _lastFetchError = "";

        private bool _fromCache;

        private int _cacheRetries;

        private bool _dashMode;
        private readonly List<string> _dashVideo = new List<string>();
        private readonly List<string> _dashAudio = new List<string>();
        private int _dashVideoIndex;
        private int _dashAudioIndex;

        private int _dashQuality;

        private string _dashNote = "";

        private string _dashDetail = "";

        private readonly List<bool> _dashPlans = new List<bool>();
        private int _dashPlanIndex;

        private string _dashProxyNote = "";

        private bool _dashActive;

        private DashSource _dashSource;

        private const int DashOpenSeconds = 15;

        private const int DashStallSeconds = 8;

        private const int DashStallMaxSeconds = 30;

        private const int DashDeadSeconds = 3;

        private const int DashPartialTimeoutSeconds = 4;

        private double _dashLastPosition = -1;
        private double _dashAudioPosition = -1;
        private double _dashProgress = -1;
        private DateTime _dashAdvancedAt;
        private DateTime _dashProgressAt;
        private bool _videoOpened;
        private bool _audioOpened;
        private DispatcherTimer _syncTimer;

        private double _syncLastPosition = -1;

        private readonly DanmakuContext _danmakuContext = new DanmakuContext();
        private DanmakuView _danmaku;

        private bool _danmakuRequested;

        private BitmapImage[] _loadingFrames;
        private DispatcherTimer _loadingTimer;
        private int _loadingFrameIndex;

        private bool _controlsVisible = true;

        private bool _updatingSeekBar;

        private const int OpenTimeoutSeconds = 8;

        private const int MaxStreamAttempts = 6;

        private const int MaxDashAttempts = 3;

        private const int DashOpenTimeoutSeconds = 8;

        public VideoPlayerPage()
        {
            InitializeComponent();

            UpdatePlayPauseIcon();

            _danmakuContext.Enabled = AppSettings.DanmakuEnabled;
            _danmaku = new DanmakuView(DanmakuLayer, _danmakuContext);
            _danmaku.PositionProvider = delegate { return Player.Position.TotalMilliseconds; };
            UpdateDanmakuIcon();
            UpdateQualityButtonText();
            BuildQualityMenu();

            BuildLoadingFrames();
        }

        private double _resumeSeconds;

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            HideQBars.Begin();

            string direct;
            if (NavigationContext.QueryString.TryGetValue("src", out direct) && direct.Length > 0)
            {
                PlayDirect(direct);
                return;
            }

            string value;
            if (NavigationContext.QueryString.TryGetValue("bvid", out value))
            {
                _bvid = value;
            }
            if (NavigationContext.QueryString.TryGetValue("cid", out value))
            {
                _cid = value;
            }
            if (NavigationContext.QueryString.TryGetValue("aid", out value))
            {
                _aid = value;
            }
            if (NavigationContext.QueryString.TryGetValue("season", out value))
            {
                int.TryParse(value, out _seasonType);
            }
            if (NavigationContext.QueryString.TryGetValue("title", out value))
            {
                _bangumiTitle = value;
            }
            if (NavigationContext.QueryString.TryGetValue("offline", out value))
            {
                _offlineForced = value == "1";
            }
            if (_bangumiTitle.Length > 0)
            {
                TitleText.Text = _bangumiTitle;
            }
            else
            {
                TitleText.Text = _bvid;
                LoadTitle();
            }

            string cacheKey = _bvid.Length > 0 ? _bvid : _aid;
            _mediaFileName = MediaCache.FileNameFor(cacheKey, _cid);

            ShowLoading("正在获取视频信息…");

            _danmaku.Start();
            if (_danmakuContext.Enabled)
            {
                LoadDanmaku();
            }

            if (_loaded)
            {
                return;
            }
            _loaded = true;

            if (OfflineMode)
            {
                CachedMedia cached = MediaCache.Existing(_mediaFileName);
                if (cached != null)
                {
                    PlayCached(cached);
                    return;
                }
            }

            if (_seasonType > 0 && !OfflineMode)
            {
                FetchBangumi();
                return;
            }

            if (AppSettings.DashPlayback && AppSettings.DashSupported && !OfflineMode)
            {
                FetchDash();
                return;
            }

            _platformIndex = 0;
            FetchForPlatform();
        }

        // 公告里的直链视频：不走接口，直接把地址丢给播放器
        private void PlayDirect(string url)
        {
            _directMode = true;

            string title;
            if (NavigationContext.QueryString.TryGetValue("title", out title) && title.Length > 0)
            {
                TitleText.Text = title;
            }

            _loaded = true;
            _candidates.Clear();
            _allCandidates.Clear();
            _allCandidates.Add(url);
            _candidates.Add(url);
            _candidateIndex = 0;
            _platformIndex = 0;
            _streamAttempts = 0;

            ShowLoading("正在加载…");
            TryNextCandidate();
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);

            DisarmWatchdog();
            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }
            _danmaku.Stop();
            HideLoading();

            if (_opened)
            {
                ReportProgress((int)Player.Position.TotalSeconds, true);
            }

            Player.Stop();
            AudioPlayer.Stop();
            StopSyncTimer();
            StopDash();
#if WP81
            LocalProxy.Stop();
#endif
            ClosePlaybackStream();
        }

        private void ReportProgress(int seconds, bool force)
        {
            if (_directMode || !_opened || seconds <= 0)
            {
                return;
            }

            int step = force ? 0 : (_reportedSeconds == 0 ? 5 : 15);
            if (seconds < _reportedSeconds + step)
            {
                return;
            }

            _reportedSeconds = seconds;
            HistoryService.Report(_aid, _cid, seconds);
        }

        private void FetchForPlatform()
        {
            TransportText.Text = "";

            if (_platformIndex >= Platforms.Length)
            {
                ShowGivingUp();
                return;
            }

            string platform = Platforms[_platformIndex];
            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");
            ShowLoading("正在获取视频信息…");

            PlayUrlService.Fetch(_bvid, _cid, platform, delegate(PlayUrl play)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!play.Ok)
                    {
                        _lastFetchError = platform + " 取址失败："
                            + JsonText.Head(play.Error, 80);
                        _platformIndex++;
                        FetchForPlatform();
                        return;
                    }

                    SetAvailableQualities(play.AcceptQuality);

                    _candidates.Clear();
                    _candidates.AddRange(play.Urls);
                    _candidateIndex = 0;

                    foreach (string candidate in play.Urls)
                    {
                        if (!_allCandidates.Contains(candidate))
                        {
                            _allCandidates.Add(candidate);
                        }
                    }

                    if (OfflineMode)
                    {
                        StartDownloadPhase();
                        return;
                    }

                    TryNextCandidate();
                }));
            });
        }

        private void TryNextCandidate()
        {
            if (_streamAttempts >= MaxStreamAttempts)
            {
                ShowGivingUp();
                return;
            }

            if (_candidateIndex >= _candidates.Count)
            {
                if (_directMode || _seasonType > 0)
                {
                    ShowGivingUp();
                    return;
                }
                _platformIndex++;
                FetchForPlatform();
                return;
            }

            string url = _candidates[_candidateIndex];
            _candidateIndex++;
            _tryingUrl = url;
            _tryingPlatform = Platforms[_platformIndex];
            _fromCache = false;

            Uri uri;
            if (!Uri.TryCreate(url, UriKind.Absolute, out uri))
            {
                ShowStatus("跳过不合法的地址：" + JsonText.Head(url, 120));
                TryNextCandidate();
                return;
            }

            _opened = false;
            _lastBufferProgress = -1;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");

            Player.Stop();

            Player.AutoPlay = true;

            Player.Source = uri;
            Player.Play();
        }

        private void Player_MediaOpened(object sender, RoutedEventArgs e)
        {
            if (_dashMode)
            {
                _videoOpened = true;
                TryFinishDashOpen();
                return;
            }

            _opened = true;
            _lastBufferProgress = -1;

            if (_fromCache)
            {
                TransportText.Text = "离线";
            }
            else if (_dashActive)
            {
                TransportText.Text = _dashQuality > 0
                    ? "DASH " + PlayUrlService.Describe(_dashQuality)
                    : "DASH";
            }
            else
            {
                TransportText.Text = _dashNote.Length > 0 ? "MP4（DASH" + _dashNote + "）" : "MP4";
            }

            if (Player.CurrentState != System.Windows.Media.MediaElementState.Playing)
            {
                Player.Play();
            }

            HideLoading();
            StatusPanel.Visibility = Visibility.Collapsed;
            UpdatePlayPauseIcon();
            _danmaku.SetPlaying(true);

            if (_resumeSeconds > 1 && Player.CanSeek)
            {
                try
                {
                    Player.Position = TimeSpan.FromSeconds(_resumeSeconds);
                    _danmaku.Seek(_resumeSeconds * 1000.0);
                }
                catch (Exception)
                {
                }
            }
            _resumeSeconds = 0;

            StartStatusTimer();
        }

        private void ArmWatchdog()
        {
            if (_watchdog == null)
            {
                _watchdog = new DispatcherTimer();
                _watchdog.Interval = TimeSpan.FromSeconds(1);
                _watchdog.Tick += WatchdogTick;
            }
            _watchdog.Start();
        }

        private void DisarmWatchdog()
        {
            if (_watchdog != null)
            {
                _watchdog.Stop();
            }
        }

        private void WatchdogTick(object sender, EventArgs e)
        {
            try
            {
                if (_opened)
                {
                    return;
                }

                if (_dashActive)
                {
                    if (Player.BufferingProgress > _lastBufferProgress)
                    {
                        _lastBufferProgress = Player.BufferingProgress;
                        _sourceSetAt = DateTime.Now;
                    }

                    ShowStatus("正在获取视频信息…");

                    if ((DateTime.Now - _sourceSetAt).TotalSeconds >= DashOpenSeconds)
                    {
                        _dashNote = "DASH没打开 "
                            + JsonText.Head(_dashSource == null ? "" : _dashSource.StatusText, 24);
                        StopDash();
                        FallbackToMp4();
                    }
                    return;
                }

                if (_dashMode)
                {
                    double waited = (DateTime.Now - _sourceSetAt).TotalSeconds;

                    if (waited >= DashPartialTimeoutSeconds && (_videoOpened || _audioOpened))
                    {
                        AbandonDashPair();
                        return;
                    }

                    if (waited >= DashOpenTimeoutSeconds)
                    {
                        AbandonDashPair();
                    }
                    return;
                }

                if (Player.BufferingProgress > _lastBufferProgress)
                {
                    _lastBufferProgress = Player.BufferingProgress;
                    _sourceSetAt = DateTime.Now;
                }

                if ((DateTime.Now - _sourceSetAt).TotalSeconds >= OpenTimeoutSeconds)
                {
                    AbandonCandidate("等了 " + OpenTimeoutSeconds + " 秒也没打开");
                }
            }
            catch (Exception)
            {
            }
        }

        private void AbandonCandidate(string reason)
        {
            DisarmWatchdog();

            _lastFailure = _fromCache
                ? reason
                : _tryingPlatform + " " + HostOf(_tryingUrl) + "：" + reason;

            if (_fromCache)
            {
                DiscardCacheAndRedownload(reason);
                return;
            }

            _lastFailedUrl = _tryingUrl;
            _failureCount++;
            _streamAttempts++;

            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            TryNextCandidate();
        }

        private void FetchDash()
        {
            TransportText.Text = "";
            _dashNote = "";
            _dashDetail = "";

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");
            ShowLoading("正在获取视频信息…");

            PlayUrlService.FetchDash(_bvid, _cid, delegate(PlayUrl play)
            {
                Dispatcher.BeginInvoke(new Action(delegate { OnDashUrl(play); }));
            });
        }

        private void FetchBangumi()
        {
            TransportText.Text = "";
            _dashNote = "";
            _dashDetail = "";

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");
            ShowLoading("正在获取视频信息…");

            long aid;
            long cid;
            long.TryParse(_aid, out aid);
            long.TryParse(_cid, out cid);

            PlayUrlService.FetchBangumi(aid, cid, _seasonType, delegate(PlayUrl play)
            {
                Dispatcher.BeginInvoke(new Action(delegate { OnBangumiUrl(play); }));
            });
        }

        private void OnBangumiUrl(PlayUrl play)
        {
            if (play.DashOk)
            {
                OnDashUrl(play);
                return;
            }

            if (play.Ok)
            {
                SetAvailableQualities(play.AcceptQuality);
                _candidates.Clear();
                _candidates.AddRange(play.Urls);
                _candidateIndex = 0;

                foreach (string candidate in play.Urls)
                {
                    if (!_allCandidates.Contains(candidate))
                    {
                        _allCandidates.Add(candidate);
                    }
                }

                TryNextCandidate();
                return;
            }

            ShowStatus(string.IsNullOrEmpty(play.Error) ? "番剧取址失败" : play.Error);
        }

        private void OnDashUrl(PlayUrl play)
        {
            if (!play.DashOk)
            {
                _dashNote = "取址失败";
                if (!string.IsNullOrEmpty(play.Error))
                {
                    _dashNote += "：" + JsonText.Head(play.Error, 18);
                }

                if (_seasonType > 0)
                {
                    ShowStatus(_dashNote);
                    return;
                }

                FallbackToMp4();
                return;
            }

            _dashMode = false;
            _dashActive = true;
            _dashQuality = play.Quality;
            _dashNote = "";
            SetAvailableQualities(play.AcceptQuality);

            _dashVideo.Clear();
            _dashVideo.AddRange(play.DashVideoUrls);
            _dashAudio.Clear();
            _dashAudio.AddRange(play.DashAudioUrls);

            string videoUrl = _dashVideo[0];
            string audioUrl = _dashAudio[0];
            _tryingUrl = videoUrl;
            _tryingPlatform = "dash";
            _fromCache = false;

            DashSource source = new DashSource(videoUrl, audioUrl, play.DurationSeconds);
            source.Opened = delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (ok || !_dashActive)
                    {
                        return;
                    }

                    _dashNote = "DASH起不来：" + JsonText.Head(error, 20);
                    StopDash();
                    FallbackToMp4();
                }));
            };

            _dashSource = source;

            _opened = false;
            _lastBufferProgress = -1;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");
            ShowLoading("正在获取视频信息…");

            Player.Stop();
            AudioPlayer.Stop();

            Player.AutoPlay = true;
            Player.SetSource(source);
            Player.Play();
        }

        // 【重要】DASH 起不来会按计划回退普通 MP4，别在这上面死磕，喵
        private void PlanDash(bool proxyOk)
        {
            _dashPlans.Clear();
#if WP81
            if (proxyOk)
            {
                _dashPlans.Add(true);
            }
#endif
            _dashPlans.Add(false);

            _dashPlanIndex = 0;
            _dashVideoIndex = 0;
            _dashAudioIndex = 0;
            TryNextDashPair();
        }

        private void TryNextDashPair()
        {
            if (_streamAttempts >= MaxDashAttempts
                || _dashVideoIndex >= _dashVideo.Count
                || _dashAudioIndex >= _dashAudio.Count)
            {
                if (_dashPlanIndex + 1 < _dashPlans.Count)
                {
                    _dashPlanIndex++;
                    _dashVideoIndex = 0;
                    _dashAudioIndex = 0;
                    _streamAttempts = 0;
                    TryNextDashPair();
                    return;
                }

                _dashNote = "播放失败";
                if (_dashDetail.Length > 0)
                {
                    _dashNote += "：" + _dashDetail;
                }
                FallbackToMp4();
                return;
            }

            string videoUrl = _dashVideo[_dashVideoIndex];
            _dashVideoIndex++;
            string audioUrl = _dashAudio[_dashAudioIndex];
            _dashAudioIndex++;

            _tryingUrl = videoUrl;
            _tryingPlatform = "dash";
            _fromCache = false;

            Uri videoUri = null;
            Uri audioUri = null;
#if WP81
            if (_dashPlans[_dashPlanIndex])
            {
                LocalProxy.Referer = Http.Referer;
                LocalProxy.SetTargets(videoUrl, audioUrl);
                try
                {
                    videoUri = new Uri(LocalProxy.VideoSource(_streamAttempts));
                    audioUri = new Uri(LocalProxy.AudioSource(_streamAttempts));
                }
                catch (Exception)
                {
                    videoUri = null;
                    audioUri = null;
                }
            }
#endif
            if (videoUri == null)
            {
                if (!Uri.TryCreate(videoUrl, UriKind.Absolute, out videoUri)
                    || !Uri.TryCreate(audioUrl, UriKind.Absolute, out audioUri))
                {
                    ShowStatus("跳过不合法的DASH地址");
                    TryNextDashPair();
                    return;
                }
            }

            _opened = false;
            _videoOpened = false;
            _audioOpened = false;
            _lastBufferProgress = -1;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在获取视频信息…");
            ShowLoading("正在获取视频信息…");

            Player.Stop();
            AudioPlayer.Stop();

            Player.AutoPlay = true;
            AudioPlayer.AutoPlay = true;

            try
            {
                Player.Source = videoUri;
                AudioPlayer.Source = audioUri;
            }
            catch (Exception)
            {
                AbandonDashPair();
                return;
            }

            Player.Play();
            AudioPlayer.Play();
        }

        private void TryFinishDashOpen()
        {
            if (_opened || !_videoOpened || !_audioOpened)
            {
                return;
            }

            _opened = true;
            _lastBufferProgress = -1;

            if (Player.CurrentState != System.Windows.Media.MediaElementState.Playing)
            {
                Player.Play();
            }
            if (AudioPlayer.CurrentState != System.Windows.Media.MediaElementState.Playing)
            {
                AudioPlayer.Play();
            }

            HideLoading();
            StatusPanel.Visibility = Visibility.Collapsed;
            UpdatePlayPauseIcon();
            _danmaku.SetPlaying(true);

            TransportText.Text = _dashQuality > 0
                ? "DASH " + PlayUrlService.Describe(_dashQuality)
                : "DASH";

            if (_resumeSeconds > 1 && Player.CanSeek)
            {
                try
                {
                    Player.Position = TimeSpan.FromSeconds(_resumeSeconds);
                    if (AudioPlayer.CanSeek)
                    {
                        AudioPlayer.Position = TimeSpan.FromSeconds(_resumeSeconds);
                    }
                    _danmaku.Seek(_resumeSeconds * 1000.0);
                }
                catch (Exception)
                {
                }
            }
            _resumeSeconds = 0;

            _dashLastPosition = -1;
            _dashAudioPosition = -1;
            _dashProgress = -1;
            _dashAdvancedAt = DateTime.Now;
            _dashProgressAt = DateTime.Now;
            StartStatusTimer();
            StartSyncTimer();
        }

        private void AbandonDashPair()
        {
            DisarmWatchdog();
            StopSyncTimer();

            if (_videoOpened)
            {
                _dashDetail = "只视频轨开了";
            }
            else if (_audioOpened)
            {
                _dashDetail = "只音频轨开了";
            }
            else
            {
                bool flowing = Player.BufferingProgress > 0 || AudioPlayer.BufferingProgress > 0;
                _dashDetail = flowing ? "两轨都没开有数据" : "两轨都没开无数据";
            }

            string source = _dashPlans.Count > _dashPlanIndex && _dashPlans[_dashPlanIndex]
                ? "代理"
                : "直连";
            _dashDetail += _dashProxyNote.Length > 0
                ? "（" + source + " " + JsonText.Head(_dashProxyNote, 24) + "）"
                : "（" + source + "）";

            _lastFailedUrl = _tryingUrl;
            _failureCount++;
            _streamAttempts++;

            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            TryNextDashPair();
        }

        private void FallbackToMp4()
        {
            DisarmWatchdog();
            StopSyncTimer();
            _dashMode = false;
            StopDash();

#if WP81
            LocalProxy.Stop();
#endif

            Player.Stop();
            AudioPlayer.Stop();
            Player.Source = null;
            AudioPlayer.Source = null;

            _streamAttempts = 0;
            _failureCount = 0;
            _platformIndex = 0;
            _candidateIndex = 0;
            _candidates.Clear();
            _lastFailedUrl = "";

            if (_seasonType > 0)
            {
                ShowStatus(string.IsNullOrEmpty(_dashNote) ? "番剧播放失败" : _dashNote);
                return;
            }

            FetchForPlatform();
        }

        private void AudioPlayer_MediaOpened(object sender, RoutedEventArgs e)
        {
            _audioOpened = true;
            TryFinishDashOpen();
        }

        private void AudioPlayer_MediaFailed(object sender, ExceptionRoutedEventArgs e)
        {
            if (!_dashMode || _tryingUrl == _lastFailedUrl)
            {
                return;
            }
            AbandonDashPair();
        }

        private void StopDash()
        {
            DashSource source = _dashSource;
            _dashSource = null;
            _dashActive = false;
            if (source != null)
            {
                try { source.Stop(); }
                catch (Exception) { }
            }
        }

        private void StartSyncTimer()
        {
            if (_syncTimer == null)
            {
                _syncTimer = new DispatcherTimer();
                _syncTimer.Interval = TimeSpan.FromSeconds(1);
                _syncTimer.Tick += SyncTick;
            }
            _syncLastPosition = -1;
            _syncTimer.Start();
        }

        private void StopSyncTimer()
        {
            if (_syncTimer != null)
            {
                _syncTimer.Stop();
            }
        }

        private void SyncTick(object sender, EventArgs e)
        {
            if (!_dashMode || !_opened)
            {
                return;
            }

            try
            {
                if (!AudioPlayer.CanSeek || !Player.CanSeek)
                {
                    return;
                }

                double video = Player.Position.TotalSeconds;

                if (video <= _syncLastPosition)
                {
                    return;
                }
                _syncLastPosition = video;

                double drift = video - AudioPlayer.Position.TotalSeconds;
                if (drift > 0.3 || drift < -0.3)
                {
                    AudioPlayer.Position = Player.Position;
                }
            }
            catch (Exception)
            {
            }
        }

        private void StartStatusTimer()
        {
            if (_statusTimer == null)
            {
                _statusTimer = new DispatcherTimer();
                _statusTimer.Interval = TimeSpan.FromSeconds(1);
                _statusTimer.Tick += StatusTick;
            }
            _statusTimer.Start();
            StatusTick(null, null);
        }

        private void StatusTick(object sender, EventArgs e)
        {
            try
            {
                TimeSpan position = Player.Position;

                string total = "--:--";
                if (Player.NaturalDuration.HasTimeSpan)
                {
                    TimeSpan duration = Player.NaturalDuration.TimeSpan;
                    total = FormatTime(duration);

                    _updatingSeekBar = true;
                    SeekBar.Maximum = duration.TotalSeconds > 0 ? duration.TotalSeconds : 1;
                    SeekBar.Value = position.TotalSeconds;
                    _updatingSeekBar = false;
                }

                PositionText.Text = FormatTime(position) + " / " + total;

                ReportProgress((int)position.TotalSeconds, false);
                CheckDashStall(position);
            }
            catch (Exception)
            {
            }
        }

        private void CheckDashStall(TimeSpan position)
        {
            if ((!_dashMode && !_dashActive) || !_opened)
            {
                return;
            }

            if (_dashActive)
            {
                _dashAdvancedAt = DateTime.Now;
                return;
            }

            double video = position.TotalSeconds;
            double audio = AudioPlayer.Position.TotalSeconds;
            double progress = Player.DownloadProgress + Player.BufferingProgress;
            DateTime now = DateTime.Now;

            if (progress > _dashProgress + 0.005)
            {
                _dashProgress = progress;
                _dashProgressAt = now;
            }

            if (video > _dashLastPosition + 0.5 || audio <= _dashAudioPosition + 0.5)
            {
                _dashLastPosition = video;
                _dashAudioPosition = audio;
                _dashProgress = progress;
                _dashAdvancedAt = now;
                _dashProgressAt = now;
                return;
            }

            double frozen = (now - _dashAdvancedAt).TotalSeconds;
            double idle = (now - _dashProgressAt).TotalSeconds;

            if (Player.DownloadProgress >= 0.999)
            {
                if (frozen < DashDeadSeconds)
                {
                    return;
                }
            }
            else if (frozen < DashStallSeconds
                || (idle < DashStallSeconds && frozen < DashStallMaxSeconds))
            {
                return;
            }

            _resumeSeconds = video;
#if WP81
            _dashNote = "卡住 载" + (int)(Player.DownloadProgress * 100) + "% "
                + LocalProxy.Summary();
#else
            _dashNote = "卡住 载" + (int)(Player.DownloadProgress * 100) + "%";
#endif
            FallbackToMp4();
        }

        private static string FormatTime(TimeSpan value)
        {
            if (value.TotalHours >= 1)
            {
                return (int)value.TotalHours + ":" + value.Minutes.ToString("D2")
                    + ":" + value.Seconds.ToString("D2");
            }
            return value.Minutes.ToString("D2") + ":" + value.Seconds.ToString("D2");
        }

        private void Player_MediaFailed(object sender, ExceptionRoutedEventArgs e)
        {
            if (_dashActive)
            {
                string why = _dashSource == null ? "" : _dashSource.StatusText;
                if (e != null && e.ErrorException != null
                    && !string.IsNullOrEmpty(e.ErrorException.Message))
                {
                    why += " " + e.ErrorException.Message;
                }

                _dashNote = why.Length > 0
                    ? "DASH失败：" + JsonText.Head(why, 40)
                    : "DASH失败";
                StopDash();
                FallbackToMp4();
                return;
            }

            if (_dashMode)
            {
                if (_tryingUrl == _lastFailedUrl)
                {
                    return;
                }
                AbandonDashPair();
                return;
            }

            if (!_fromCache && _tryingUrl == _lastFailedUrl)
            {
                return;
            }

            string detail = e.ErrorException == null ? "未知" : e.ErrorException.Message;
            AbandonCandidate("MediaFailed：" + JsonText.Head(detail, 80));
        }

        private void Player_CurrentStateChanged(object sender, RoutedEventArgs e)
        {
            UpdatePlayPauseIcon();
            _danmaku.SetPlaying(
                Player.CurrentState == System.Windows.Media.MediaElementState.Playing);
        }

        private void TapLayer_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            if (_qualityMenuOpen)
            {
                CloseQualityMenu();
            }
            else
            {
                SetControlsVisible(!_controlsVisible);
            }
        }

        private void PlayPauseButton_Click(object sender, RoutedEventArgs e)
        {
            if (Player.CurrentState == System.Windows.Media.MediaElementState.Playing)
            {
                Player.Pause();
                if (_dashMode)
                {
                    AudioPlayer.Pause();
                }
            }
            else
            {
                Player.Play();
                if (_dashMode)
                {
                    AudioPlayer.Play();
                }
            }
            UpdatePlayPauseIcon();
        }

        private void SeekBar_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_updatingSeekBar)
            {
                return;
            }

            try
            {
                if (Player.CanSeek)
                {
                    Player.Position = TimeSpan.FromSeconds(SeekBar.Value);
                    if (_dashMode && AudioPlayer.CanSeek)
                    {
                        AudioPlayer.Position = TimeSpan.FromSeconds(SeekBar.Value);
                    }
                    _danmaku.Seek(SeekBar.Value * 1000.0);
                    _dashAdvancedAt = DateTime.Now;
                    ReportProgress((int)SeekBar.Value, true);
                }
            }
            catch (Exception)
            {
            }
        }

        private void SetControlsVisible(bool visible)
        {
            if (!visible)
            {
                HideBars.Begin();
                //CloseQualityMenu();
            }
            else
            {
                ShowBars.Begin();
            }
            _controlsVisible = visible;
            //ControlPanel.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
            //TitleText.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;

        }

        private void UpdatePlayPauseIcon()
        {
            PlayPauseMask.ImageSource = new BitmapImage(new Uri(
                Player.CurrentState == System.Windows.Media.MediaElementState.Playing
                    ? "/Res/Icon/pause.png"
                    : "/Res/Icon/play.png", UriKind.Relative));
        }

        private void DanmakuButton_Click(object sender, RoutedEventArgs e)
        {
            bool enabled = !_danmakuContext.Enabled;
            AppSettings.DanmakuEnabled = enabled;

            _danmaku.SetEnabled(enabled);
            UpdateDanmakuIcon();

            if (enabled && !_danmakuRequested)
            {
                LoadDanmaku();
            }
        }

        private void UpdateDanmakuIcon()
        {
            bool on = _danmakuContext.Enabled;
            DanmakuMask.ImageSource = new BitmapImage(new Uri(
                on ? "/Res/Icon/danmuon.png" : "/Res/Icon/danmuoff.png",
                UriKind.Relative));
            DanmakuIcon.Fill = on
                ? new SolidColorBrush(Colors.White)
                : new SolidColorBrush(Color.FromArgb(0x55, 0xFF, 0xFF, 0xFF));
        }

        private static readonly int[] DefaultQualities = new int[] { 16, 32, 64, 80 };

        private readonly List<int> _availableQualities = new List<int>();

        private bool _qualityMenuOpen;

        private void BuildQualityMenu()
        {
            QualityList.Children.Clear();

            List<int> qualities = _availableQualities.Count > 0
                ? _availableQualities
                : new List<int>(DefaultQualities);

            for (int i = 0; i < qualities.Count; i++)
            {
                Button button = new Button();
                button.Tag = qualities[i];
                button.MinWidth = 0;
                //button.Padding = new Thickness(12, 2, 12, 2);
                button.Click += QualityOption_Click;
                QualityList.Children.Add(button);
            }

            RefreshQualityMenu();
        }

        private void SetAvailableQualities(List<int> qualities)
        {
            if (qualities == null || qualities.Count == 0)
            {
                return;
            }

            _availableQualities.Clear();
            _availableQualities.AddRange(qualities);
            BuildQualityMenu();
        }

        private void RefreshQualityMenu()
        {
            for (int i = 0; i < QualityList.Children.Count; i++)
            {
                Button button = QualityList.Children[i] as Button;
                if (button == null)
                {
                    continue;
                }

                int quality = (int)button.Tag;
                //修改一下~
                //button.BorderBrush = new SolidColorBrush(Colors.Transparent);
                button.BorderThickness = new Thickness(0);
                button.FontSize = 20;



                button.Content = (quality == AppSettings.PlayQuality ? "● " : "    ")
                    + PlayUrlService.Describe(quality);
            }
        }

        private void CloseQualityMenu()
        {
            _qualityMenuOpen = false;
            //QualityPanel.Visibility = Visibility.Collapsed;
            HideQBars.Begin();
        }

        private void QualityButton_Click(object sender, RoutedEventArgs e)
        {
            _qualityMenuOpen = !_qualityMenuOpen;
            if (_qualityMenuOpen)
            {
                RefreshQualityMenu();
                //QualityPanel.Visibility = Visibility.Visible;
                ShowQBars.Begin();
                return;
            }
            HideQBars.Begin();
            //QualityPanel.Visibility = Visibility.Collapsed;
        }

        private void QualityOption_Click(object sender, RoutedEventArgs e)
        {
            Button button = sender as Button;
            if (button == null)
            {
                return;
            }

            CloseQualityMenu();

            int quality = (int)button.Tag;
            if (quality == AppSettings.PlayQuality)
            {
                return;
            }

            AppSettings.PlayQuality = quality;
            UpdateQualityButtonText();

            if (OfflineMode)
            {
                ShowStatus("清晰度已设为 " + PlayUrlService.Describe(quality)
                    + "，重新下载后生效");
                return;
            }

            ReloadForQuality();
        }

        private void UpdateQualityButtonText()
        {
            QualityButton.Content = PlayUrlService.Describe(AppSettings.PlayQuality);
        }

        private void ReloadForQuality()
        {
            if (!_opened)
            {
                return;
            }

            _resumeSeconds = Player.Position.TotalSeconds;

            DisarmWatchdog();
            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            _platformIndex = 0;
            _candidateIndex = 0;
            _streamAttempts = 0;
            _failureCount = 0;
            _candidates.Clear();
            _allCandidates.Clear();
            _fromCache = false;
            _dashNote = "";

            ShowLoading("正在切换到 "
                + PlayUrlService.Describe(AppSettings.PlayQuality) + "…");

            if (_seasonType > 0)
            {
                _dashMode = false;
                _dashVideoIndex = 0;
                _dashAudioIndex = 0;
                FetchBangumi();
                return;
            }

            if (AppSettings.DashPlayback && AppSettings.DashSupported)
            {
                _dashMode = false;
                _dashVideoIndex = 0;
                _dashAudioIndex = 0;
                FetchDash();
                return;
            }

            FetchForPlatform();
        }

        private void LoadDanmaku()
        {
            if (_danmakuRequested)
            {
                return;
            }
            _danmakuRequested = true;

            DanmakuLoader.Fetch(_cid, delegate(List<DanmakuItem> items, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (items != null && string.IsNullOrEmpty(error))
                    {
                        _danmaku.Load(items);
                    }
                }));
            });
        }

        private void StartDownloadPhase()
        {
            if (_downloadIndex >= _allCandidates.Count)
            {
                ShowGivingUp();
                return;
            }

            string url = _allCandidates[_downloadIndex];
            _downloadIndex++;

            HideLoading();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("离线播放：正在下载（第 " + _downloadIndex
                + " / " + _allCandidates.Count + " 个地址）…");

            MediaCache.Download(url, _mediaFileName, OnDownloadProgress, delegate(CachedMedia media)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!media.Ok)
                    {
                        _failureCount++;
                        StartDownloadPhase();
                        return;
                    }

                    PlayCached(media);
                }));
            });
        }

        private void OnDownloadProgress(long received, long total)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                string text = "正在下载 " + MediaCache.FormatSize(received);
                if (total > 0)
                {
                    text += " / " + MediaCache.FormatSize(total)
                        + "（" + (received * 100 / total) + "%）";
                }
                ShowStatus(text);
            }));
        }

        private void PlayCached(CachedMedia media)
        {
            DisarmWatchdog();
            ClosePlaybackStream();

            _fromCache = true;

            try
            {
                _playbackStream = MediaCache.Open(media.FileName);
            }
            catch (Exception ex)
            {
                DiscardCacheAndRedownload("打不开缓存文件: " + ex.Message);
                return;
            }

            _opened = false;
            _lastBufferProgress = -1;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus(media.Size > 0
                ? "本机已有 " + MediaCache.FormatSize(media.Size) + "，正在播放…"
                : "正在播放本机缓存…");

            Player.Stop();

            Player.Source = null;

            Player.AutoPlay = true;

            try
            {
                Player.SetSource(_playbackStream);
                Player.Play();
            }
            catch (Exception ex)
            {
                AbandonCandidate("SetSource 失败：" + ex.Message);
            }
        }

        private void DiscardCacheAndRedownload(string reason)
        {
            DisarmWatchdog();
            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            ClosePlaybackStream();
            MediaCache.Delete(_mediaFileName);

            if (_cacheRetries >= 1)
            {
                _failureCount++;
                ShowGivingUp();
                return;
            }
            _cacheRetries++;

            _fromCache = false;
            _downloadIndex = 0;
            _platformIndex = 0;
            _candidateIndex = 0;
            _allCandidates.Clear();
            FetchForPlatform();
        }

        private void ClosePlaybackStream()
        {
            if (_playbackStream == null)
            {
                return;
            }
            try
            {
                _playbackStream.Close();
            }
            catch (Exception)
            {
            }
            _playbackStream = null;
        }

        private void ShowGivingUp()
        {
            HideLoading();
            StatusPanel.Visibility = Visibility.Visible;

            if (_failureCount == 0)
            {
                ShowStatus(_lastFetchError.Length > 0
                    ? _lastFetchError
                    : "没能取到播放地址。");
                return;
            }

            ShowStatus("试过 " + _failureCount + " 个地址都没能播放。"
                + (_lastFailure.Length > 0 ? "（最后：" + _lastFailure + "）" : ""));
        }


        private static string HostOf(string url)
        {
            try
            {
                return new Uri(url, UriKind.Absolute).Host;
            }
            catch (Exception)
            {
                return JsonText.Head(url, 40);
            }
        }


        private void BuildLoadingFrames()
        {
            string[] names = new string[]
            {
                "/Res/bili_anim_tv_chan_1.png",
                "/Res/bili_anim_tv_chan_3.png",
                "/Res/bili_anim_tv_chan_5.png",
                "/Res/bili_anim_tv_chan_7.png",
                "/Res/bili_anim_tv_chan_9.png"
            };

            _loadingFrames = new BitmapImage[names.Length];
            for (int i = 0; i < names.Length; i++)
            {
                BitmapImage image = new BitmapImage();
                image.UriSource = new Uri(names[i], UriKind.Relative);
                _loadingFrames[i] = image;
            }
        }

        private void ShowLoading(string text)
        {
            LoadingText.Text = text;
            LoadingPanel.Visibility = Visibility.Visible;
            StartLoadingAnimation();
        }

        private void HideLoading()
        {
            LoadingPanel.Visibility = Visibility.Collapsed;
            if (_loadingTimer != null)
            {
                _loadingTimer.Stop();
            }
        }

        private void StartLoadingAnimation()
        {
            if (_loadingFrames == null || _loadingFrames.Length == 0)
            {
                return;
            }

            if (_loadingTimer == null)
            {
                _loadingTimer = new DispatcherTimer();
                _loadingTimer.Interval = TimeSpan.FromMilliseconds(100);
                _loadingTimer.Tick += OnLoadingTick;
            }

            _loadingFrameIndex = 0;
            LoadingTv.Source = _loadingFrames[0];
            _loadingTimer.Start();
        }

        private void OnLoadingTick(object sender, EventArgs e)
        {
            if (_loadingFrames == null || _loadingFrames.Length == 0)
            {
                return;
            }

            _loadingFrameIndex = (_loadingFrameIndex + 1) % _loadingFrames.Length;
            LoadingTv.Source = _loadingFrames[_loadingFrameIndex];
        }

        private void LoadTitle()
        {
            if (string.IsNullOrEmpty(_bvid))
            {
                return;
            }

            Http.GetText("https://api.bilibili.com/x/web-interface/view?bvid=" + _bvid,
                Http.Referer, delegate(HttpResult http)
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        return;
                    }

                    Match aid = Regex.Match(http.Body, @"""aid"":\s*(\d+)");
                    if (aid.Success)
                    {
                        _aid = aid.Groups[1].Value;
                    }

                    string title = JsonText.ReadString(http.Body, "title");
                    if (title.Length == 0)
                    {
                        return;
                    }

                    Dispatcher.BeginInvoke(new Action(delegate
                    {
                        TitleText.Text = title;
                    }));
                });
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusPanel.Visibility = Visibility.Visible;
        }
    }
}
