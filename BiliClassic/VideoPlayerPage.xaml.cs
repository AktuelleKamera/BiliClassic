using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using BiliClassic.Danmaku;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    /// <summary>
    /// 播放页。导航参数只有bvid+cid
    /// 标题转义后带%XX，Navigate会抛异常
    ///
    /// 两种播放方式，无自动降级链：
    ///
    ///   在线：MediaElement逐个试候选地址，html5优先
    ///     成不了就显示原因，不偷偷转下载
    ///   离线：先下到隔离存储，再SetSource播本地
    ///     第二次看直接用缓存，不再联网
    ///
    /// 设Source前必须把AutoPlay置true
    /// 否则停在Paused、Position恒为0
    ///
    /// 打不开地址不一定报MediaFailed
    /// 会永远停在Opening，必须有看门狗超时
    ///
    /// 失败原因都记进_streamLog并显示在诊断面板
    /// 自动重试成功后失败页不出现，不留记录证据会被埋掉
    ///
    /// 底部播放控制条点画面显隐
    /// 状态条只放取地址、下载、失败这类一次性消息
    /// </summary>
    public partial class VideoPlayerPage : PhoneApplicationPage
    {
        /// <summary>
        /// 依次尝试的platform，html5排第一
        ///
        /// pc的流有referer鉴权，html5无鉴权
        /// WP7没法给MediaElement加请求头
        /// 所以html5才有机会，pc只作后备
        /// html5只有MP4，正合需要
        /// </summary>
        private static readonly string[] Platforms = new string[] { "html5", "pc" };

        private readonly List<string> _candidates = new List<string>();

        /// <summary>
        /// 所有platform取到的地址
        /// MediaElement拉流全失败后
        /// 改用HttpWebRequest按这份清单下载
        /// </summary>
        private readonly List<string> _allCandidates = new List<string>();

        private int _downloadIndex;

        /// <summary>正在播放的缓存流，播放期间不能关</summary>
        private IsolatedStorageFileStream _playbackStream;

        private string _bvid = "";
        private string _cid = "";
        private bool _loaded;

        /// <summary>aid，上报历史要用，取标题时顺手拿来</summary>
        private string _aid = "";

        /// <summary>已经上报到第几秒</summary>
        private int _reportedSeconds;

        /// <summary>离线缓存文件名，按bvid+cid区分</summary>
        private string _mediaFileName = "";

        private int _platformIndex;
        private int _candidateIndex;
        private int _failureCount;

        /// <summary>正在试的地址与上次判失败的地址，用于去重</summary>
        private string _tryingUrl = "";
        private string _lastFailedUrl = "";

        /// <summary>每秒刷新播放进度</summary>
        private DispatcherTimer _statusTimer;

        /// <summary>
        /// 看门狗
        /// 打不开地址时不一定报MediaFailed
        /// 会永远停在Opening，界面全黑且无报错
        /// 超时即判此地址不行，换下一个
        /// </summary>
        private DispatcherTimer _watchdog;

        private DateTime _sourceSetAt;
        private bool _opened;
        private TimeSpan _lastPosition;
        private int _stalledTicks;

        /// <summary>
        /// 每次失败的记录（platform+主机+原因）
        ///
        /// 必须单独攒一份
        /// 自动转下载能成功，失败页不会出现
        /// 状态栏每秒被StatusTick重写，证据全被埋掉
        /// </summary>
        private readonly List<string> _streamLog = new List<string>();

        /// <summary>当前尝试用的platform，用于标注失败来源</summary>
        private string _tryingPlatform = "";

        /// <summary>流式阶段试过的地址数，不含下载阶段</summary>
        private int _streamAttempts;

        /// <summary>
        /// 当前是否在播本地缓存
        ///
        /// 必须有：缓存播不出来时不能换地址试流式
        /// 那等于离线模式下偷偷切回在线
        /// </summary>
        private bool _fromCache;

        /// <summary>缓存重下次数，只给一次，否则无限重下</summary>
        private int _cacheRetries;

        /// <summary>MediaFailed次数与第一次的错误</summary>
        private int _mediaFailedCount;
        private string _firstMediaFailed = "";

        /// <summary>弹幕渲染层与配置</summary>
        private readonly DanmakuContext _danmakuContext = new DanmakuContext();
        private DanmakuView _danmaku;

        /// <summary>已发过弹幕请求，避免反复拉</summary>
        private bool _danmakuRequested;

        /// <summary>加载层小电视帧动画，缺图则看不到动画</summary>
        private BitmapImage[] _loadingFrames;
        private DispatcherTimer _loadingTimer;
        private int _loadingFrameIndex;

        /// <summary>控制条是否可见，点画面切换</summary>
        private bool _controlsVisible = true;

        /// <summary>
        /// 正在用代码刷新进度条
        ///
        /// 赋值Slider.Value也会触发ValueChanged
        /// 不挡住等于每秒seek一次
        /// </summary>
        private bool _updatingSeekBar;

        /// <summary>设置Source后等这么久未打开就换地址</summary>
        private const int OpenTimeoutSeconds = 8;

        /// <summary>已打开但位置不动这么多秒即判卡死</summary>
        private const int StallTimeoutSeconds = 10;

        /// <summary>
        /// 在线播放最多试这么多地址
        ///
        /// 不设上限要干等地址数×超时，十几条就一两分钟
        /// </summary>
        private const int MaxStreamAttempts = 6;

        public VideoPlayerPage()
        {
            InitializeComponent();

            // 初始显示"播放"，此时CurrentState还是Closed
            UpdatePlayPauseText();

            _danmakuContext.Enabled = AppSettings.DanmakuEnabled;
            _danmaku = new DanmakuView(DanmakuLayer, _danmakuContext);
            // 弹幕不自己计时，以播放器位置为准
            _danmaku.PositionProvider = delegate { return Player.Position.TotalMilliseconds; };
            UpdateDanmakuButtonText();

            BuildLoadingFrames();
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (NavigationContext.QueryString.TryGetValue("bvid", out value))
            {
                _bvid = value;
            }
            if (NavigationContext.QueryString.TryGetValue("cid", out value))
            {
                _cid = value;
            }

            // 先用BV号占位，标题取回后替换
            TitleText.Text = _bvid;
            LoadTitle();

            _mediaFileName = MediaCache.FileNameFor(_bvid, _cid);

            // 取地址期间摆出加载层，比黑屏好
            ShowLoading("正在获取播放地址…");

            // 弹幕只要cid，拉不到不影响播放
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

            // 离线播放：本机已有文件直接播，不取地址
            if (AppSettings.OfflinePlayback)
            {
                CachedMedia cached = MediaCache.Existing(_mediaFileName);
                if (cached != null)
                {
                    PlayCached(cached);
                    return;
                }
            }

            _platformIndex = 0;
            FetchForPlatform();
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

            // 离开前把最后进度报上去
            if (_opened)
            {
                ReportProgress((int)Player.Position.TotalSeconds, true);
            }

            // 离开页面必须让出媒体，否则缓存流占用、文件删不掉
            Player.Stop();
            ClosePlaybackStream();
        }

        /// <summary>
        /// 播放进度上报，安卓版同接口
        /// 首次到5秒报一次，之后每15秒一次，跳转时立即报
        /// </summary>
        private void ReportProgress(int seconds, bool force)
        {
            if (!_opened || seconds <= 0)
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

        /// <summary>用当前platform取一批候选地址</summary>
        private void FetchForPlatform()
        {
            if (_platformIndex >= Platforms.Length)
            {
                // 无自动降级链，在线失败即失败并显示原因
                ShowGivingUp();
                return;
            }

            string platform = Platforms[_platformIndex];
            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在取播放地址（" + platform + "）…");

            PlayUrlService.Fetch(_bvid, _cid, platform, delegate(PlayUrl play)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (!play.Ok)
                    {
                        // 这个platform拿不到地址，换下一个
                        _platformIndex++;
                        FetchForPlatform();
                        return;
                    }

                    _candidates.Clear();
                    _candidates.AddRange(play.Urls);
                    _candidateIndex = 0;

                    // 同时记进总清单，留给下载阶段
                    foreach (string candidate in play.Urls)
                    {
                        if (!_allCandidates.Contains(candidate))
                        {
                            _allCandidates.Add(candidate);
                        }
                    }

                    // 离线播放不让MediaElement拉流，拿到地址就下载
                    if (AppSettings.OfflinePlayback)
                    {
                        StartDownloadPhase();
                        return;
                    }

                    TryNextCandidate();
                }));
            });
        }

        /// <summary>试下一个候选地址，用完则换platform</summary>
        private void TryNextCandidate()
        {
            // 试够个数就放弃，避免干等一两分钟
            if (_streamAttempts >= MaxStreamAttempts)
            {
                ShowGivingUp();
                return;
            }

            if (_candidateIndex >= _candidates.Count)
            {
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
                // 地址不合法直接跳过，不必等报错
                ShowStatus("跳过不合法的地址：" + JsonText.Head(url, 120));
                TryNextCandidate();
                return;
            }

            _opened = false;
            _lastPosition = TimeSpan.Zero;
            _stalledTicks = 0;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus("正在尝试第 " + (_failureCount + 1) + " 个地址（"
                + Platforms[_platformIndex] + "），共 " + _candidates.Count + " 个…");

            // 换地址前先停掉上一个，免得旧流仍在加载
            Player.Stop();

            // AutoPlay必须置true，XAML里写的是"False"
            // MediaOpened之前调的Play()不一定生效
            // 元素会停在Paused、Position恒为0，即打开却无一帧
            // 播隔离存储文件本来就强制要求AutoPlay置true
            Player.AutoPlay = true;

            Player.Source = uri;
            Player.Play();
        }

        private void Player_MediaOpened(object sender, RoutedEventArgs e)
        {
            _opened = true;
            _lastPosition = TimeSpan.Zero;
            _stalledTicks = 0;

            // 兜底：MediaOpened之后调的Play()一定有效
            if (Player.CurrentState != System.Windows.Media.MediaElementState.Playing)
            {
                Player.Play();
            }

            // 开播后收掉一次性提示
            HideLoading();
            StatusPanel.Visibility = Visibility.Collapsed;
            UpdatePlayPauseText();
            _danmaku.SetPlaying(true);

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
                if (!_opened)
                {
                    if ((DateTime.Now - _sourceSetAt).TotalSeconds >= OpenTimeoutSeconds)
                    {
                        AbandonCandidate("等了 " + OpenTimeoutSeconds + " 秒也没打开");
                    }
                    return;
                }

                TimeSpan position = Player.Position;
                if (position > _lastPosition)
                {
                    _lastPosition = position;
                    _stalledTicks = 0;
                    return;
                }

                _stalledTicks++;
                if (_stalledTicks >= StallTimeoutSeconds)
                {
                    // 记下CurrentState：Paused是没被启动
                    // Buffering是数据没下来
                    AbandonCandidate("打开后卡在 " + FormatTime(position)
                        + " 不动（状态 " + Player.CurrentState + "）");
                }
            }
            catch (Exception)
            {
            }
        }

        /// <summary>放弃当前地址：在线换下一个，离线重下缓存</summary>
        private void AbandonCandidate(string reason)
        {
            DisarmWatchdog();

            if (_fromCache)
            {
                // 在播本地缓存，不能试流式地址
                DiscardCacheAndRedownload(reason);
                return;
            }

            // 先记为已失败，免得MediaElement稍后补报MediaFailed
            // 被当成第二次失败，白跳一个候选
            _lastFailedUrl = _tryingUrl;
            _failureCount++;
            _streamAttempts++;
            _streamLog.Add(_tryingPlatform + " " + HostOf(_tryingUrl) + " → " + reason);

            // 停状态条，否则被StatusTick覆盖
            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            TryNextCandidate();
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

        /// <summary>
        /// 每秒刷新进度条和时间
        ///
        /// 不碰StatusPanel：状态条只放一次性消息
        /// 进度交给控制条滑块
        /// </summary>
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

                    // 代码赋值也会触发ValueChanged，必须挡掉
                    _updatingSeekBar = true;
                    SeekBar.Maximum = duration.TotalSeconds > 0 ? duration.TotalSeconds : 1;
                    SeekBar.Value = position.TotalSeconds;
                    _updatingSeekBar = false;
                }

                PositionText.Text = FormatTime(position) + " / " + total;

                ReportProgress((int)position.TotalSeconds, false);
            }
            catch (Exception)
            {
            }
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
            // 同一Source会重复报失败，忽略重复那次
            // 去重只对在线有效：缓存时_tryingUrl恒为空串
            // 拿它去重会空==空命中，把失败整个吞掉
            if (!_fromCache && _tryingUrl == _lastFailedUrl)
            {
                return;
            }

            string detail = e.ErrorException == null ? "未知" : e.ErrorException.Message;
            _mediaFailedCount++;
            if (_firstMediaFailed.Length == 0)
            {
                // 第一次的原始错误最有价值，能说明卡在哪一步
                _firstMediaFailed = detail;
            }

            AbandonCandidate("MediaFailed：" + JsonText.Head(detail, 80));
        }

        private void Player_CurrentStateChanged(object sender, RoutedEventArgs e)
        {
            // 暂停/播放靠按钮文案体现，不写状态条
            UpdatePlayPauseText();
            // 弹幕层跟着停，它按播放位置推
            _danmaku.SetPlaying(
                Player.CurrentState == System.Windows.Media.MediaElementState.Playing);
        }

        /// <summary>
        /// 点画面显隐控制条
        ///
        /// 暂停已有专门按钮，点画面只用来收起控制条
        /// </summary>
        private void TapLayer_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            SetControlsVisible(!_controlsVisible);
        }

        private void PlayPauseButton_Click(object sender, RoutedEventArgs e)
        {
            if (Player.CurrentState == System.Windows.Media.MediaElementState.Playing)
            {
                Player.Pause();
            }
            else
            {
                Player.Play();
            }
            UpdatePlayPauseText();
        }

        private void SeekBar_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            // 代码刷新进度时不seek，否则每秒打断播放
            if (_updatingSeekBar)
            {
                return;
            }

            try
            {
                if (Player.CanSeek)
                {
                    Player.Position = TimeSpan.FromSeconds(SeekBar.Value);
                    // 弹幕层立刻跟着跳
                    _danmaku.Seek(SeekBar.Value * 1000.0);
                    // 跳转后立刻报一次，别等下一轮
                    ReportProgress((int)SeekBar.Value, true);
                }
            }
            catch (Exception)
            {
            }
        }

        /// <summary>
        /// 点画面显隐控制条和标题
        ///
        /// 标题就是压画面的一块字，一起收掉
        /// </summary>
        private void SetControlsVisible(bool visible)
        {
            _controlsVisible = visible;
            ControlPanel.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
            TitleText.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
        }

        private void UpdatePlayPauseText()
        {
            PlayPauseButton.Content =
                Player.CurrentState == System.Windows.Media.MediaElementState.Playing
                    ? "暂停"
                    : "播放";
        }

        private void DanmakuButton_Click(object sender, RoutedEventArgs e)
        {
            bool enabled = !_danmakuContext.Enabled;
            AppSettings.DanmakuEnabled = enabled;

            _danmaku.SetEnabled(enabled);
            UpdateDanmakuButtonText();

            // 之前关着没拉过，打开才去拉
            if (enabled && !_danmakuRequested)
            {
                LoadDanmaku();
            }
        }

        private void UpdateDanmakuButtonText()
        {
            DanmakuButton.Content = _danmakuContext.Enabled ? "弹幕开" : "弹幕关";
        }

        /// <summary>
        /// 拉弹幕，失败不影响播放
        /// 没有弹幕是正常情况，只记账，绝不弹诊断面板
        /// </summary>
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
                        return;
                    }

                    // 只留下证据，供真的播不出来时看
                    _streamLog.Add("弹幕 → " + (string.IsNullOrEmpty(error) ? "这个视频没有弹幕" : error));
                }));
            });
        }

        /// <summary>
        /// 离线播放：HttpWebRequest下到隔离存储
        /// 下完再SetSource播本地文件
        ///
        /// 只在设置页打开离线播放时走到这里
        /// 不是在线播放的降级兜底
        /// HttpWebRequest能带Referer/Cookie
        /// MediaElement设不了请求头
        /// </summary>
        private void StartDownloadPhase()
        {
            if (_downloadIndex >= _allCandidates.Count)
            {
                ShowGivingUp();
                return;
            }

            string url = _allCandidates[_downloadIndex];
            _downloadIndex++;

            // 加载层会挡住下载进度，先收掉
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
                        _streamLog.Add("下载 " + HostOf(url) + " → " + media.Error);
                        StartDownloadPhase();
                        return;
                    }

                    PlayCached(media);
                }));
            });
        }

        /// <summary>下载进度，在后台线程，碰控件前须切回UI线程</summary>
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

        /// <summary>播放刚下好的缓存文件</summary>
        private void PlayCached(CachedMedia media)
        {
            DisarmWatchdog();
            ClosePlaybackStream();

            // 此后在播本地缓存，失败走缓存恢复路径
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
            _lastPosition = TimeSpan.Zero;
            _stalledTicks = 0;
            _sourceSetAt = DateTime.Now;
            ArmWatchdog();

            StatusPanel.Visibility = Visibility.Visible;
            ShowStatus(media.Size > 0
                ? "本机已有 " + MediaCache.FormatSize(media.Size) + "，正在播放…"
                : "正在播放本机缓存…");

            Player.Stop();

            // 先清网络地址，再换本地流
            Player.Source = null;

            // WP7硬性要求：播隔离存储文件必须AutoPlay置true
            Player.AutoPlay = true;

            try
            {
                Player.SetSource(_playbackStream);
                Player.Play();
            }
            catch (Exception ex)
            {
                // SetSource只接受IsolatedStorageFileStream
                // 失败交给AbandonCandidate按_fromCache恢复
                AbandonCandidate("SetSource 失败：" + ex.Message);
            }
        }

        /// <summary>
        /// 本地缓存放不出来（不完整/坏了）
        ///
        /// 丢掉重下，不去试流式地址
        /// 离线模式下不能偷偷切回在线
        /// 重下只给一次，坏文件再下也一样
        /// </summary>
        private void DiscardCacheAndRedownload(string reason)
        {
            DisarmWatchdog();
            if (_statusTimer != null)
            {
                _statusTimer.Stop();
            }

            _streamLog.Add("缓存 → " + reason);

            ClosePlaybackStream();
            MediaCache.Delete(_mediaFileName);

            if (_cacheRetries >= 1)
            {
                _failureCount++;
                ShowGivingUp();
                return;
            }
            _cacheRetries++;

            // 整条链重来：重新取地址再下载
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
            // 逐条记录比一句总结有用，先摆出来
            HideLoading();
            ShowDiagnostics();

            StatusPanel.Visibility = Visibility.Visible;

            // 逐条原因在诊断面板，这里只给总览
            string extra = _mediaFailedCount > 0
                ? "MediaElement 报 MediaFailed " + _mediaFailedCount + " 次，第一次："
                    + JsonText.Head(_firstMediaFailed.Length == 0 ? "未知" : _firstMediaFailed, 200)
                : "";
            ShowStatus("试过 " + _failureCount + " 个地址都没成。"
                + (extra.Length > 0 ? "\n" + extra : ""));
        }

        /// <summary>
        /// 显示这一路的失败记录
        ///
        /// 自动转下载成功时失败页不出现，证据会被埋掉
        /// 所以不管成不成都要留着，失败时直接摆出来
        /// 面板可点掉，不挡画面
        /// </summary>
        private void ShowDiagnostics()
        {
            if (_streamLog.Count == 0)
            {
                return;
            }

            DiagnosticsText.Text = "自动重试的失败记录（点此关闭）\n"
                + string.Join("\n", _streamLog.ToArray());
            DiagnosticsPanel.Visibility = Visibility.Visible;
        }

        private void DiagnosticsPanel_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            DiagnosticsPanel.Visibility = Visibility.Collapsed;
        }

        // 本页是横屏专用（XAML里SupportedOrientations="Landscape"）
        // 页面本身固定横屏，全屏开关已删
        //
        // 若日后要支持竖屏：
        // SupportedOrientations改回PortraitOrLandscape
        // 并按允许的方向集合收窄
        // 只设Orientation压不住重力感应，会被传感器纠正回去

        /// <summary>取主机名，失败记录留主机即可</summary>
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

        // ---------------- 取地址时的加载层 ----------------

        /// <summary>
        /// 预载小电视5帧
        ///
        /// 帧文件不存在时BitmapImage不抛异常，只走ImageFailed
        /// 所以不用try，缺图只是看不到动画
        /// </summary>
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

        /// <summary>100ms一帧</summary>
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

        /// <summary>
        /// 取视频标题
        /// 不走导航参数：标题含中文，转义后带%XX会让Navigate抛异常
        /// </summary>
        private void LoadTitle()
        {
            Http.GetText("https://api.bilibili.com/x/web-interface/view?bvid=" + _bvid,
                Http.Referer, delegate(HttpResult http)
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        return;
                    }

                    // 顺手拿aid，上报历史要用
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
            // 状态条只放一次性消息，每次都显出来
            // 开播时由Player_MediaOpened收掉
            StatusPanel.Visibility = Visibility.Visible;
        }
    }
}
