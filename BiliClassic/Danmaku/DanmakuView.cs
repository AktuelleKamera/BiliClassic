using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace BiliClassic.Danmaku
{
    /// <summary>
    /// 弹幕渲染层
    /// </summary>
    public sealed class DanmakuView
    {
        /// <summary>刷新间隔</summary>
        private const int TickMs = 33;

        /// <summary>
        /// 弹幕丢弃时间
        /// </summary>
        private const int LateToleranceMs = 1200;

        /// <summary>
        /// 播放位置跳变
        /// </summary>
        private const double JumpBackMs = 400;
        private const double JumpAheadMs = 2000;

        /// <summary>一条正在显示的弹幕的运行时状态外观</summary>
        private sealed class Live
        {
            public DanmakuItem Item;
            public FrameworkElement Element;
            public TranslateTransform Transform;
            public double Width;

            /// <summary>出现时刻与消失时刻</summary>
            public double StartMs;
            public double EndMs;

            public double FromX;
            public double ToX;
        }

        private readonly Canvas _canvas;
        private readonly DanmakuContext _context;
        private readonly List<DanmakuItem> _items = new List<DanmakuItem>();
        private readonly List<Live> _live = new List<Live>();

        /// <summary>每条轨道最早可以放下一条的时刻</summary>
        private double[] _lineFreeAtMs = new double[0];

        private DispatcherTimer _timer;
        private int _cursor;
        private double _lastMs = -1;
        private bool _playing;

        /// <summary>
        /// 取当前播放位置（毫秒）
        /// </summary>
        public Func<double> PositionProvider;

        public DanmakuView(Canvas canvas, DanmakuContext context)
        {
            _canvas = canvas;
            _context = context;
            _canvas.IsHitTestVisible = false;
            _canvas.SizeChanged += delegate
            {
                Seek(SafePosition());
            };
        }

        public DanmakuContext Context
        {
            get { return _context; }
        }

        /// <summary>已载入多少条弹幕</summary>
        public int TotalCount
        {
            get { return _items.Count; }
        }

        /// <summary>载入弹幕</summary>
        public void Load(List<DanmakuItem> items)
        {
            _items.Clear();
            if (items != null)
            {
                _items.AddRange(items);
            }
            Clear();
        }

        /// <summary>清空</summary>
        public void Clear()
        {
            _lastMs = -1;
            _cursor = 0;
            RemoveAllLive();
        }

        /// <summary>
        /// 开关
        /// </summary>
        public void SetEnabled(bool enabled)
        {
            _context.Enabled = enabled;
            if (enabled)
            {
                Seek(SafePosition());
            }
            else
            {
                RemoveAllLive();
            }
        }

        public void Start()
        {
            if (_timer == null)
            {
                _timer = new DispatcherTimer();
                _timer.Interval = TimeSpan.FromMilliseconds(TickMs);
                _timer.Tick += OnTick;
            }
            _timer.Start();
        }

        public void Stop()
        {
            if (_timer != null)
            {
                _timer.Stop();
            }
        }

        /// <summary>
        /// 播放 / 暂停
        /// </summary>
        public void SetPlaying(bool playing)
        {
            _playing = playing;
        }

        /// <summary>seek</summary>
        public void Seek(double nowMs)
        {
            RemoveAllLive();
            _cursor = FirstIndexAtOrAfter(nowMs);
            _lastMs = nowMs;
        }

        private void OnTick(object sender, EventArgs e)
        {
            if (!_context.Enabled || !_playing || PositionProvider == null)
            {
                return;
            }

            double now = SafePosition();
            double canvasWidth = _canvas.ActualWidth;
            if (canvasWidth <= 0 || now < 0)
            {
                _lastMs = now;
                return;
            }

            // 拖动进度条
            if (_lastMs >= 0
                && (now < _lastMs - JumpBackMs || now > _lastMs + JumpAheadMs))
            {
                Seek(now);
                return;
            }

            Spawn(now, canvasWidth);
            Move(now);
            Retire(now);

            _lastMs = now;
        }

        private double SafePosition()
        {
            try
            {
                return PositionProvider == null ? 0 : PositionProvider();
            }
            catch (Exception)
            {
                return 0;
            }
        }

        // 弹幕SPAWN！！

        private void Spawn(double now, double canvasWidth)
        {
            while (_cursor < _items.Count && _items[_cursor].TimeMs <= now)
            {
                DanmakuItem item = _items[_cursor];
                _cursor++;

                if (!_context.IsModeEnabled(item.Mode))
                {
                    continue;
                }
                if (_live.Count >= _context.MaxConcurrent)
                {
                    continue;
                }
                if (now - item.TimeMs > LateToleranceMs)
                {
                    continue;
                }

                int line = AllocateLine(item, now);
                if (line < 0)
                {
                    // 丢弃无空轨道的
                    continue;
                }

                Add(item, line, now, canvasWidth);
            }
        }

        /// <summary>寻找空轨道</summary>
        private int AllocateLine(DanmakuItem item, double now)
        {
            int lines = _lineFreeAtMs.Length;
            if (lines <= 0)
            {
                return -1;
            }

            bool fromBottom = item.Mode == DanmakuMode.Bottom;
            for (int i = 0; i < lines; i++)
            {
                int line = fromBottom ? (lines - 1 - i) : i;
                if (_lineFreeAtMs[line] <= now)
                {
                    return line;
                }
            }
            return -1;
        }

        private void Add(DanmakuItem item, int line, double now, double canvasWidth)
        {
            double fontSize = _context.FontSizeFor(item.TextSize);

            TextBlock text = new TextBlock();
            text.Text = item.Text;
            text.FontSize = fontSize;
            text.Foreground = new SolidColorBrush(item.Color);

            FrameworkElement root;
            if (_context.StrokeEnabled)
            {
                // 文字描边
                Grid grid = new Grid();

                TextBlock shadow = new TextBlock();
                shadow.Text = item.Text;
                shadow.FontSize = fontSize;
                shadow.Foreground = new SolidColorBrush(Colors.Black);
                shadow.Margin = new Thickness(1, 1, 0, 0);

                grid.Children.Add(shadow);
                grid.Children.Add(text);
                root = grid;
            }
            else
            {
                root = text;
            }

            TranslateTransform transform = new TranslateTransform();
            root.RenderTransform = transform;
            root.Opacity = _context.Opacity;

            root.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
            double width = root.DesiredSize.Width;

            Canvas.SetLeft(root, 0);
            Canvas.SetTop(root, line * _context.LineHeight);

            bool moving = item.Mode.IsMoving();
            double duration;
            double fromX;
            double toX;

            if (moving)
            {
                duration = _context.ScrollDurationMs;
                if (item.Mode == DanmakuMode.Reverse)
                {
                    fromX = -width;
                    toX = canvasWidth;
                }
                else
                {
                    fromX = canvasWidth;
                    toX = -width;
                }
            }
            else
            {
                // 顶部/底部弹幕居中不动，靠 EndMs 到点消失
                duration = _context.TopBottomDurationMs;
                fromX = (canvasWidth - width) / 2.0;
                toX = fromX;
            }

            Live live = new Live();
            live.Item = item;
            live.Element = root;
            live.Transform = transform;
            live.Width = width;
            live.StartMs = now;
            live.EndMs = now + duration;
            live.FromX = fromX;
            live.ToX = toX;

            transform.X = fromX;
            _canvas.Children.Add(root);
            _live.Add(live);

            _lineFreeAtMs[line] = now + OccupiedMs(live);
        }

        /// <summary>
        /// 存活时间
        /// </summary>
        private static double OccupiedMs(Live live)
        {
            double duration = live.EndMs - live.StartMs;
            if (!live.Item.Mode.IsMoving())
            {
                return duration;
            }

            double span = Math.Abs(live.ToX - live.FromX);
            if (span <= 0)
            {
                return duration;
            }
            return duration * (live.Width / span);
        }

        // 移动与回收

        private void Move(double now)
        {
            for (int i = 0; i < _live.Count; i++)
            {
                Live live = _live[i];
                double duration = live.EndMs - live.StartMs;
                if (duration <= 0)
                {
                    continue;
                }

                double progress = (now - live.StartMs) / duration;
                if (progress < 0)
                {
                    progress = 0;
                }
                else if (progress > 1)
                {
                    progress = 1;
                }

                live.Transform.X = live.FromX + (live.ToX - live.FromX) * progress;
            }
        }

        private void Retire(double now)
        {
            for (int i = _live.Count - 1; i >= 0; i--)
            {
                if (now >= _live[i].EndMs)
                {
                    _canvas.Children.Remove(_live[i].Element);
                    _live.RemoveAt(i);
                }
            }
        }

        private void RemoveAllLive()
        {
            for (int i = 0; i < _live.Count; i++)
            {
                _canvas.Children.Remove(_live[i].Element);
            }
            _live.Clear();
            ResetLines();
        }

        private void ResetLines()
        {
            int lines = _context.MaxLines;
            if (lines < 0)
            {
                lines = 0;
            }
            if (_lineFreeAtMs == null || _lineFreeAtMs.Length != lines)
            {
                _lineFreeAtMs = new double[lines];
            }
            for (int i = 0; i < lines; i++)
            {
                _lineFreeAtMs[i] = double.MinValue;
            }
        }

        /// <summary>第一条时间 &gt;= ms 的弹幕下标</summary>
        private int FirstIndexAtOrAfter(double ms)
        {
            int lo = 0;
            int hi = _items.Count;
            while (lo < hi)
            {
                int mid = (lo + hi) / 2;
                if (_items[mid].TimeMs < ms)
                {
                    lo = mid + 1;
                }
                else
                {
                    hi = mid;
                }
            }
            return lo;
        }
    }
}
