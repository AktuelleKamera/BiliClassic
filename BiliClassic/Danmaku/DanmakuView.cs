using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;

namespace BiliClassic.Danmaku
{
    public sealed class DanmakuView
    {
        private const int TickMs = 33;

        private const int LateToleranceMs = 1200;

        private sealed class Live
        {
            public DanmakuItem Item;
            public FrameworkElement Element;
            public TranslateTransform Transform;
            public double Width;

            public double StartMs;
            public double EndMs;

            public double FromX;
            public double ToX;

            public Storyboard Story;
        }

        private readonly Canvas _canvas;
        private readonly DanmakuContext _context;
        private readonly List<DanmakuItem> _items = new List<DanmakuItem>();
        private readonly List<Live> _live = new List<Live>();

        private double[] _lineFreeAtMs = new double[0];

        private readonly List<FrameworkElement> _pool = new List<FrameworkElement>();

        private readonly Dictionary<Color, SolidColorBrush> _brushes =
            new Dictionary<Color, SolidColorBrush>();

        private readonly Dictionary<string, double> _widthCache =
            new Dictionary<string, double>();

        private DispatcherTimer _timer;
        private int _cursor;
        private double _lastMs = -1;
        private bool _playing;

        private double _posMs;
        private int _lastTick;
        private int _lastSyncTick;
        private bool _clockReady;

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

        public int TotalCount
        {
            get { return _items.Count; }
        }

        public void Load(List<DanmakuItem> items)
        {
            _items.Clear();
            if (items != null)
            {
                _items.AddRange(items);
            }
            Clear();
        }

        public void Clear()
        {
            _lastMs = -1;
            _cursor = 0;
            RemoveAllLive();
        }

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

        public void SetPlaying(bool playing)
        {
            bool resume = playing && !_playing;
            _playing = playing;

            for (int i = 0; i < _live.Count; i++)
            {
                Storyboard story = _live[i].Story;
                if (story == null)
                {
                    continue;
                }
                try
                {
                    if (playing)
                    {
                        story.Resume();
                    }
                    else
                    {
                        story.Pause();
                    }
                }
                catch (Exception)
                {
                }
            }

            if (resume)
            {
                ResetClock(SafePosition());
            }
        }

        public void Seek(double nowMs)
        {
            RemoveAllLive();
            _cursor = FirstIndexAtOrAfter(nowMs);
            _lastMs = nowMs;
            ResetClock(nowMs);
        }

        private void ResetClock(double ms)
        {
            int tick = Environment.TickCount;
            _posMs = ms;
            _lastTick = tick;
            _lastSyncTick = tick;
            _clockReady = true;
        }

        private double NowMs()
        {
            int tick = Environment.TickCount;
            if (!_clockReady)
            {
                ResetClock(SafePosition());
                return _posMs;
            }

            _posMs += (uint)(tick - _lastTick);
            _lastTick = tick;

            if ((uint)(tick - _lastSyncTick) >= 1000)
            {
                _lastSyncTick = tick;
                double real = SafePosition();
                if (real - _posMs > 1000 || _posMs - real > 1000)
                {
                    _posMs = real;
                }
            }
            return _posMs;
        }

        private void OnTick(object sender, EventArgs e)
        {
            if (!_context.Enabled || !_playing || PositionProvider == null)
            {
                return;
            }

            double now = NowMs();
            double canvasWidth = _canvas.ActualWidth;
            if (canvasWidth <= 0 || now < 0)
            {
                _lastMs = now;
                return;
            }

            Spawn(now, canvasWidth);
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
                    continue;
                }

                Add(item, line, now, canvasWidth);
            }
        }

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

            TextBlock main;
            TextBlock shadow;
            FrameworkElement root = Rent(out main, out shadow);

            main.Text = item.Text;
            main.FontSize = fontSize;
            main.Foreground = BrushFor(item.Color);
            if (shadow != null)
            {
                shadow.Text = item.Text;
                shadow.FontSize = fontSize;
            }
            root.Opacity = _context.Opacity;

            TranslateTransform transform = root.RenderTransform as TranslateTransform;
            if (transform == null)
            {
                transform = new TranslateTransform();
                root.RenderTransform = transform;
            }

            string key = item.TextSize + "|" + item.Text;
            double width;
            if (!_widthCache.TryGetValue(key, out width))
            {
                root.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
                width = root.DesiredSize.Width;
                if (_widthCache.Count < 3000)
                {
                    _widthCache[key] = width;
                }
            }

            Canvas.SetLeft(root, 0);
            Canvas.SetTop(root, 0);
            transform.Y = line * _context.LineHeight;

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

            if (moving)
            {
                DoubleAnimation move = new DoubleAnimation();
                move.From = fromX;
                move.To = toX;
                move.Duration = new Duration(TimeSpan.FromMilliseconds(duration));
                Storyboard.SetTarget(move, transform);
                Storyboard.SetTargetProperty(move, new PropertyPath("X"));

                Storyboard story = new Storyboard();
                story.Children.Add(move);
                live.Story = story;
                story.Begin();
            }

            _lineFreeAtMs[line] = now + OccupiedMs(live);
        }

        private FrameworkElement Rent(out TextBlock main, out TextBlock shadow)
        {
            if (_pool.Count > 0)
            {
                FrameworkElement root = _pool[_pool.Count - 1];
                _pool.RemoveAt(_pool.Count - 1);

                Grid grid = root as Grid;
                if (grid != null && grid.Children.Count >= 2)
                {
                    shadow = (TextBlock)grid.Children[0];
                    main = (TextBlock)grid.Children[1];
                }
                else
                {
                    main = (TextBlock)root;
                    shadow = null;
                }
                return root;
            }

            return CreateRoot(out main, out shadow);
        }

        private FrameworkElement CreateRoot(out TextBlock main, out TextBlock shadow)
        {
            main = new TextBlock();
            if (!_context.StrokeEnabled)
            {
                shadow = null;
                return main;
            }

            Grid grid = new Grid();
            shadow = new TextBlock();
            shadow.Foreground = BrushFor(Colors.Black);
            shadow.Margin = new Thickness(1, 1, 0, 0);
            grid.Children.Add(shadow);
            grid.Children.Add(main);
            return grid;
        }

        private SolidColorBrush BrushFor(Color color)
        {
            SolidColorBrush brush;
            if (_brushes.TryGetValue(color, out brush))
            {
                return brush;
            }
            brush = new SolidColorBrush(color);
            _brushes[color] = brush;
            return brush;
        }

        private void Recycle(FrameworkElement element)
        {
            _canvas.Children.Remove(element);
            if (_pool.Count < 48)
            {
                _pool.Add(element);
            }
        }

        private static void StopStory(Live live)
        {
            if (live.Story != null)
            {
                try
                {
                    live.Story.Stop();
                }
                catch (Exception)
                {
                }
            }
        }

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

        private void Retire(double now)
        {
            for (int i = _live.Count - 1; i >= 0; i--)
            {
                if (now >= _live[i].EndMs)
                {
                    StopStory(_live[i]);
                    Recycle(_live[i].Element);
                    _live.RemoveAt(i);
                }
            }
        }

        private void RemoveAllLive()
        {
            for (int i = 0; i < _live.Count; i++)
            {
                StopStory(_live[i]);
                Recycle(_live[i].Element);
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
