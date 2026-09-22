using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;

namespace BiliClassic.Api
{
    internal sealed class DashTrackReader
    {
        private const int ReadAheadSeconds = 20;

        private const int ResumeSeconds = 8;

        private const int MaxBufferBytes = 4 * 1024 * 1024;

        private const int MaxFragmentBytes = 3 * 1024 * 1024;

        private const int MaxInitBytes = 512 * 1024;

        private static readonly Dictionary<MediaSampleAttributeKeys, string> KeyframeAttributes =
            CreateKeyframeAttributes();
        private static readonly Dictionary<MediaSampleAttributeKeys, string> PlainAttributes =
            new Dictionary<MediaSampleAttributeKeys, string>();

        private static Dictionary<MediaSampleAttributeKeys, string> CreateKeyframeAttributes()
        {
            Dictionary<MediaSampleAttributeKeys, string> attributes =
                new Dictionary<MediaSampleAttributeKeys, string>();
            attributes[MediaSampleAttributeKeys.KeyFrameFlag] = "true";
            return attributes;
        }

        private readonly string _url;
        private readonly bool _isVideo;
        private readonly DashTrack _track = new DashTrack();

        private byte[] _buffer = new byte[256 * 1024];
        private int _length;
        private long _baseOffset;

        private HttpWebRequest _request;
        private Stream _stream;
        private bool _paused;
        private bool _closed;

        private long _skipBytes;

        private bool _initDone;
        private bool _closedByServer;

        private readonly List<DashSample> _all = new List<DashSample>();

        private readonly List<CacheFragment> _cache = new List<CacheFragment>();

        private sealed class CacheFragment
        {
            public long Base;
            public int Length;
            public byte[] Data;
        }

        private readonly object _lock = new object();

        private readonly string _fileName;
        private IsolatedStorageFile _store;
        private IsolatedStorageFileStream _file;
        private long _fileEnd;

        private int _cursor;

        private long _pendingSeekTicks = -1;

        private long _firstTimestamp = -1;
        private double _parsedSeconds;

        public bool SkipToKeyframe;

        public string Status = "";

        public Action OnInit;

        public DashTrack Track
        {
            get { return _track; }
        }

        public bool IsVideo
        {
            get { return _isVideo; }
        }

        public bool InitDone
        {
            get { return _initDone; }
        }

        public bool Queued
        {
            get { lock (_lock) { return _cursor < _all.Count; } }
        }

        public bool HasQueued
        {
            get { lock (_lock) { return _cursor < _all.Count; } }
        }

        public bool Ended
        {
            get { lock (_lock) { return _closedByServer && _cursor >= _all.Count; } }
        }

        public bool HasPendingSeek
        {
            get { return _pendingSeekTicks >= 0; }
        }

        public double CurrentSeconds
        {
            get
            {
                lock (_lock)
                {
                    if (_cursor < 0 || _cursor >= _all.Count)
                    {
                        return -1;
                    }
                    long first = _firstTimestamp > 0 ? _firstTimestamp : 0;
                    return (_all[_cursor].Timestamp - first) * _track.TickToSeconds;
                }
            }
        }

        public bool Paused
        {
            get { return _paused; }
        }

        public int QueuedSeconds
        {
            get
            {
                lock (_lock)
                {
                    if (_cursor >= _all.Count)
                    {
                        return 0;
                    }
                    DashSample last = _all[_all.Count - 1];
                    return (int)((last.Timestamp + last.Duration - _all[_cursor].Timestamp)
                        * _track.TickToSeconds);
                }
            }
        }

        public DashTrackReader(string url, bool isVideo)
        {
            _url = url;
            _isVideo = isVideo;
            _fileName = isVideo ? "dashv.tmp" : "dasha.tmp";
        }

        public void Start(long fromByte)
        {
            Abort();
            Clear();
            OpenFile();

            _closed = false;
            _closedByServer = false;
            _baseOffset = fromByte;
            _skipBytes = fromByte;
            _length = 0;
            _parsedSeconds = 0;

            Open();
        }

        public void Stop()
        {
            _closed = true;
            Abort();
            Clear();
            CloseFile();
        }

        private void Clear()
        {
            lock (_lock)
            {
                _all.Clear();
                _cache.Clear();
                _cursor = 0;
                _pendingSeekTicks = -1;
            }
        }

        private void OpenFile()
        {
            try
            {
                _store = IsolatedStorageFile.GetUserStoreForApplication();
                if (_store.FileExists(_fileName))
                {
                    _store.DeleteFile(_fileName);
                }
                _file = _store.CreateFile(_fileName);
                _fileEnd = 0;
            }
            catch (Exception ex)
            {
                Status = "临时文件失败 " + ex.Message;
            }
        }

        private void CloseFile()
        {
            lock (_lock)
            {
                try
                {
                    if (_file != null)
                    {
                        _file.Dispose();
                    }
                }
                catch (Exception)
                {
                }
                _file = null;

                try
                {
                    if (_store != null)
                    {
                        if (_store.FileExists(_fileName))
                        {
                            _store.DeleteFile(_fileName);
                        }
                        _store.Dispose();
                    }
                }
                catch (Exception)
                {
                }
                _store = null;
                _fileEnd = 0;
            }
        }

        private void Abort()
        {
            _paused = false;
            try
            {
                if (_request != null)
                {
                    _request.Abort();
                }
            }
            catch (Exception)
            {
            }

            _request = null;
            _stream = null;
        }

        private void Open()
        {
            try
            {
                HttpWebRequest request = (HttpWebRequest)WebRequest.Create(new Uri(_url));
                request.Method = "GET";
                request.UserAgent = Http.UserAgent;
                request.AllowReadStreamBuffering = false;

                try
                {
                    request.Headers["Referer"] = Http.Referer;
                }
                catch (Exception)
                {
                }

                try
                {
                    request.CookieContainer = BiliSession.GetContainer();
                }
                catch (Exception)
                {
                }

                if (_skipBytes > 0)
                {
                    try
                    {
                        request.Headers["Range"] = "bytes=" + _skipBytes + "-";
                    }
                    catch (Exception)
                    {
                    }
                }

                _request = request;
                request.BeginGetResponse(OnResponse, request);
            }
            catch (Exception ex)
            {
                Status = "起请求失败 " + ex.Message;
            }
        }

        private void OnResponse(IAsyncResult ar)
        {
            if (_closed)
            {
                return;
            }

            try
            {
                HttpWebResponse response = (HttpWebResponse)((HttpWebRequest)ar.AsyncState).EndGetResponse(ar);
                if ((int)response.StatusCode != 206 && _skipBytes > 0)
                {
                    Status = "上游不支持Range，丢"
                        + (_skipBytes / 1024) + "K";
                }
                else
                {
                    _skipBytes = 0;
                }

                _stream = response.GetResponseStream();
                BeginRead();
            }
            catch (Exception ex)
            {
                Status = "响应失败 " + ex.Message;
            }
        }

        private void BeginRead()
        {
            if (_closed || _stream == null)
            {
                return;
            }

            EnsureRoom();
            if (_buffer.Length - _length < 8192)
            {
                _paused = true;
                return;
            }

            try
            {
                _stream.BeginRead(_buffer, _length, _buffer.Length - _length, OnRead, null);
            }
            catch (Exception ex)
            {
                Status = "读失败 " + ex.Message;
            }
        }

        private void EnsureRoom()
        {
            if (_buffer.Length - _length >= 64 * 1024)
            {
                return;
            }

            int want = _buffer.Length * 2;
            if (want > MaxBufferBytes)
            {
                want = MaxBufferBytes;
            }
            if (want <= _buffer.Length)
            {
                return;
            }

            byte[] bigger = new byte[want];
            Array.Copy(_buffer, 0, bigger, 0, _length);
            _buffer = bigger;
        }

        private void OnRead(IAsyncResult ar)
        {
            if (_closed || _stream == null)
            {
                return;
            }

            int read;
            try
            {
                read = _stream.EndRead(ar);
            }
            catch (Exception ex)
            {
                Status = "读中断 " + ex.Message;
                return;
            }

            if (read <= 0)
            {
                _closedByServer = true;
                Status = "读完";
                return;
            }

            if (_skipBytes > 0)
            {
                int drop = (int)Math.Min(_skipBytes, read);
                _skipBytes -= drop;
                if (drop < read)
                {
                    Array.Copy(_buffer, _length + drop, _buffer, _length, read - drop);
                    _length += read - drop;
                }
            }
            else
            {
                _length += read;
            }

            if (!_initDone)
            {
                try
                {
                    ParseInit();
                }
                catch (Exception ex)
                {
                    Status = "解init出错 " + ex.Message;
                }
            }

            if (_initDone)
            {
                try
                {
                    ParseFragments();
                }
                catch (Exception ex)
                {
                    Status = "解分片出错 " + ex.Message;
                }
            }

            EnsureRoom();
            bool seekPending = _pendingSeekTicks >= 0;
            if ((!seekPending && QueuedSeconds >= ReadAheadSeconds)
                || _length >= MaxBufferBytes
                || _buffer.Length - _length < 8192)
            {
                _paused = true;
            }

            if (!_paused)
            {
                BeginRead();
            }
        }

        public void ResumeIfNeeded()
        {
            if (_closed || !_paused)
            {
                return;
            }
            if (_closedByServer)
            {
                return;
            }

            if (QueuedSeconds <= ResumeSeconds)
            {
                _paused = false;
                BeginRead();
            }
        }

        private void NotifyInit()
        {
            _initDone = true;
            if (OnInit != null)
            {
                OnInit();
            }
        }

        private void ParseInit()
        {
            int moov = DashMp4.FindBox(_buffer, 0, _length, "moov");
            if (moov < 0)
            {
                if (_length >= MaxInitBytes)
                {
                    Status = "init里没有moov";
                    NotifyInit();
                }
                return;
            }

            int moovEnd = DashMp4.BoxEnd(_buffer, moov);
            if (moovEnd <= moov || moovEnd > _length)
            {
                if (_length >= MaxInitBytes)
                {
                    Status = "init里没有moov";
                    NotifyInit();
                }
                return;
            }

            int sidx = DashMp4.FindBox(_buffer, moovEnd, _length, "sidx");
            if (sidx < 0 && _length < moovEnd + 65536 && _length < MaxInitBytes)
            {
                return;
            }

            string error;
            if (!DashMp4.ParseInit(_buffer, _length, _track, out error))
            {
                Status = "init解析失败 " + error;
                NotifyInit();
                return;
            }

            int from = DashMp4.FindBox(_buffer, 0, _length, "moof");
            int consumed = from >= 0 ? from : moovEnd;

            _baseOffset += consumed;
            Array.Copy(_buffer, consumed, _buffer, 0, _length - consumed);
            _length -= consumed;

            Status = "init好了";
            NotifyInit();
        }

        private void ParseFragments()
        {
            while (_length > 16)
            {
                int moof = DashMp4.FindBox(_buffer, 0, _length, "moof");
                if (moof < 0)
                {
                    long size = DashMp4.BoxSize(_buffer, 0);
                    if (size < 8)
                    {
                        _baseOffset += 1;
                        Array.Copy(_buffer, 1, _buffer, 0, _length - 1);
                        _length -= 1;
                        continue;
                    }
                    if (size > _length)
                    {
                        return;
                    }

                    _baseOffset += (int)size;
                    Array.Copy(_buffer, (int)size, _buffer, 0, _length - (int)size);
                    _length -= (int)size;
                    continue;
                }

                if (moof > 0)
                {
                    _baseOffset += moof;
                    Array.Copy(_buffer, moof, _buffer, 0, _length - moof);
                    _length -= moof;
                }

                int moofEnd = DashMp4.BoxEnd(_buffer, 0);
                if (moofEnd <= 0 || moofEnd > _length)
                {
                    return;
                }

                if (!DashMp4.BoxAt(_buffer, moofEnd, "mdat"))
                {
                    if (_length > moofEnd + 8)
                    {
                        _baseOffset += moofEnd;
                        Array.Copy(_buffer, moofEnd, _buffer, 0, _length - moofEnd);
                        _length -= moofEnd;
                        continue;
                    }
                    return;
                }

                int mdatEnd = DashMp4.BoxEnd(_buffer, moofEnd);
                if (mdatEnd <= moofEnd || mdatEnd > _length)
                {
                    return;
                }

                if (mdatEnd > MaxFragmentBytes)
                {
                    _baseOffset += mdatEnd;
                    Array.Copy(_buffer, mdatEnd, _buffer, 0, _length - mdatEnd);
                    _length -= mdatEnd;
                    continue;
                }

                MemoryStream fragment = new MemoryStream(mdatEnd);
                fragment.Write(_buffer, 0, mdatEnd);
                fragment.Position = 0;

                List<DashSample> samples = new List<DashSample>();
                if (DashMp4.ParseFragment(fragment.GetBuffer(), 0, mdatEnd, _track, _baseOffset, samples))
                {
                    MemoryStream store = fragment;
                    if (_isVideo)
                    {
                        MemoryStream annex = ConvertFragment(fragment.GetBuffer(), samples);
                        if (annex != null)
                        {
                            store = annex;
                            fragment.Dispose();
                        }
                    }

                    lock (_lock)
                    {
                        long baseOffset = _fileEnd;
                        byte[] raw = store.GetBuffer();
                        int count = (int)store.Length;
                        if (_file != null)
                        {
                            _file.Seek(_fileEnd, SeekOrigin.Begin);
                            _file.Write(raw, 0, count);
                            _file.Flush();
                            _fileEnd += count;
                        }

                        CacheFragment cached = new CacheFragment();
                        cached.Base = baseOffset;
                        cached.Length = count;
                        cached.Data = raw;
                        _cache.Add(cached);

                        for (int i = 0; i < samples.Count; i++)
                        {
                            if (_firstTimestamp < 0)
                            {
                                _firstTimestamp = samples[i].Timestamp;
                            }

                            samples[i].FileOffset = baseOffset + samples[i].Offset;
                            _all.Add(samples[i]);
                        }

                        double end = (samples[samples.Count - 1].Timestamp
                            + samples[samples.Count - 1].Duration) * _track.TickToSeconds;
                        if (end > _parsedSeconds)
                        {
                            _parsedSeconds = end;
                        }

                        if (_pendingSeekTicks >= 0
                            && samples[samples.Count - 1].Timestamp >= _pendingSeekTicks)
                        {
                            ApplyPendingSeek();
                        }
                    }

                    store.Dispose();
                }
                else
                {
                    fragment.Dispose();
                }

                _baseOffset += mdatEnd;
                Array.Copy(_buffer, mdatEnd, _buffer, 0, _length - mdatEnd);
                _length -= mdatEnd;

                if (_paused && QueuedSeconds <= ResumeSeconds)
                {
                    _paused = false;
                    BeginRead();
                }
            }
        }

        public bool RequestSeek(double seconds)
        {
            long ticks = (long)(seconds * _track.Timescale)
                + (_firstTimestamp > 0 ? _firstTimestamp : 0);
            if (ticks < 0)
            {
                ticks = 0;
            }

            lock (_lock)
            {
                if (_all.Count == 0
                    || (ticks > _all[_all.Count - 1].Timestamp && !_closedByServer))
                {
                    _pendingSeekTicks = ticks;
                    if (_paused)
                    {
                        _paused = false;
                        BeginRead();
                    }
                    return false;
                }

                SeekToTicks(ticks);
                _pendingSeekTicks = -1;
                return true;
            }
        }

        private void ApplyPendingSeek()
        {
            lock (_lock)
            {
                long ticks = _pendingSeekTicks;
                _pendingSeekTicks = -1;
                SeekToTicks(ticks);
            }
        }

        private void SeekToTicks(long ticks)
        {
            int i = IndexAtOrAfter(ticks);
            if (i >= _all.Count)
            {
                i = _all.Count - 1;
            }
            if (i < 0)
            {
                _cursor = 0;
                return;
            }

            if (_isVideo)
            {
                while (i > 0 && !_all[i].Keyframe)
                {
                    i--;
                }
                if (!_all[i].Keyframe)
                {
                    _cursor = _all.Count;
                    return;
                }
            }
            _cursor = i;
        }

        private int IndexAtOrAfter(long ticks)
        {
            int lo = 0;
            int hi = _all.Count;
            while (lo < hi)
            {
                int mid = (lo + hi) / 2;
                if (_all[mid].Timestamp < ticks)
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

        public MediaStreamSample TakeSample(MediaStreamDescription description)
        {
            while (true)
            {
                DashSample sample;
                lock (_lock)
                {
                    if (_cursor >= _all.Count)
                    {
                        break;
                    }
                    sample = _all[_cursor];
                    _cursor++;
                    TrimCache(sample.FileOffset);
                }

                if (_isVideo && SkipToKeyframe && !sample.Keyframe)
                {
                    continue;
                }
                if (_isVideo)
                {
                    SkipToKeyframe = false;
                }

                Dictionary<MediaSampleAttributeKeys, string> attributes =
                    (_isVideo && sample.Keyframe) ? KeyframeAttributes : PlainAttributes;

                ResumeIfNeeded();

                long timestamp = Ticks(sample.Timestamp - (_firstTimestamp > 0 ? _firstTimestamp : 0));

                byte[] array = null;
                int inArray = 0;
                byte[] buf = null;
                lock (_lock)
                {
                    for (int i = 0; i < _cache.Count; i++)
                    {
                        CacheFragment cf = _cache[i];
                        if (sample.FileOffset >= cf.Base
                            && sample.FileOffset < cf.Base + cf.Length)
                        {
                            array = cf.Data;
                            inArray = (int)(sample.FileOffset - cf.Base);
                            break;
                        }
                    }

                    if (array == null)
                    {
                        if (_file == null)
                        {
                            return null;
                        }
                        buf = new byte[sample.Size];
                        _file.Seek(sample.FileOffset, SeekOrigin.Begin);
                        int got = 0;
                        while (got < buf.Length)
                        {
                            int read = _file.Read(buf, got, buf.Length - got);
                            if (read <= 0)
                            {
                                break;
                            }
                            got += read;
                        }
                        if (got < buf.Length)
                        {
                            return null;
                        }
                    }
                }

                if (array != null)
                {
                    MemoryStream view = new MemoryStream(array);
                    return new MediaStreamSample(description, view, inArray, sample.Size,
                        timestamp, attributes);
                }

                MemoryStream ms = new MemoryStream(buf, 0, buf.Length);
                return new MediaStreamSample(description, ms, 0, buf.Length,
                    timestamp, attributes);
            }

            return null;
        }

        private void TrimCache(long fileOffset)
        {
            for (int i = _cache.Count - 1; i >= 0; i--)
            {
                CacheFragment cf = _cache[i];
                if (cf.Base + cf.Length <= fileOffset)
                {
                    _cache.RemoveAt(i);
                }
            }
        }

        private MemoryStream ConvertFragment(byte[] src, List<DashSample> samples)
        {
            int n = _track.NalLengthSize;
            if (n < 1 || n > 4)
            {
                return null;
            }

            MemoryStream ms = new MemoryStream(src.Length);
            for (int i = 0; i < samples.Count; i++)
            {
                DashSample sample = samples[i];
                int end = sample.Offset + sample.Size;
                int newOffset = (int)ms.Position;
                int p = sample.Offset;

                while (p + n <= end)
                {
                    int len = 0;
                    for (int k = 0; k < n; k++)
                    {
                        len = (len << 8) | src[p + k];
                    }
                    p += n;
                    if (len <= 0 || p + len > end)
                    {
                        break;
                    }

                    ms.WriteByte(0);
                    ms.WriteByte(0);
                    ms.WriteByte(0);
                    ms.WriteByte(1);
                    ms.Write(src, p, len);
                    p += len;
                }

                sample.Offset = newOffset;
                sample.Size = (int)ms.Position - newOffset;
            }

            ms.Position = 0;
            return ms;
        }

        public MediaStreamSample TakeAudioDiscontinuity(MediaStreamDescription description)
        {
            MediaStreamSample sample = TakeSample(description);
            if (sample == null)
            {
                return null;
            }

            Dictionary<MediaSampleAttributeKeys, string> attributes =
                new Dictionary<MediaSampleAttributeKeys, string>();
            attributes[MediaSampleAttributeKeys.KeyFrameFlag] = "1";

            return new MediaStreamSample(description, sample.Stream, sample.Offset, sample.Count,
                sample.Timestamp, attributes);
        }

        private long Ticks(long value)
        {
            if (value < 0)
            {
                value = 0;
            }
            if (_track.Timescale <= 0)
            {
                return value;
            }
            return value * 10000000L / _track.Timescale;
        }

        public long BufferedBytes
        {
            get { return _length + _fileEnd; }
        }
    }

    public sealed class DashSource : MediaStreamSource
    {
        private readonly DashTrackReader _video;
        private readonly DashTrackReader _audio;
        private long _durationTicks;

        private MediaStreamDescription _videoDesc;
        private MediaStreamDescription _audioDesc;

        private bool _opened;
        private bool _closed;
        private bool _initVideo;
        private bool _initAudio;

        private bool _descDone;

        private DispatcherTimer _openTimer;
        private DateTime _openAt;

        private DispatcherTimer _bufferTimer;
        private bool _videoBuffering;
        private bool _audioBuffering;

        private bool _audioDiscontinuity;

        public string Status = "";

        private int _sentVideo;
        private int _sentAudio;

        private int _askVideo;
        private int _askAudio;

        private long _seekTicks;
        private bool _seekWaiting;

        private static void OnUi(Action action)
        {
            Dispatcher dispatcher = Deployment.Current == null ? null : Deployment.Current.Dispatcher;
            if (dispatcher == null)
            {
                action();
                return;
            }

            dispatcher.BeginInvoke(action);
        }

        public string StatusText
        {
            get
            {
                string text = Status + " 要" + _askVideo + "/" + _askAudio
                    + " 样" + _sentVideo + "/" + _sentAudio
                    + " V" + _video.QueuedSeconds + " A" + _audio.QueuedSeconds;
                if (_video.Status.Length > 0 && _video.Status != "init好了")
                {
                    text += " " + _video.Status;
                }
                if (_audio.Status.Length > 0 && _audio.Status != "init好了")
                {
                    text += " " + _audio.Status;
                }
                return text;
            }
        }

        public Action<bool, string> Opened;

        public bool Seeking
        {
            get { return _seekWaiting; }
        }

        public DashSource(string videoUrl, string audioUrl, double durationSeconds)
        {
            _durationTicks = (long)(durationSeconds * 10000000.0);
            _video = new DashTrackReader(videoUrl, true);
            _audio = new DashTrackReader(audioUrl, false);
        }

        protected override void OpenMediaAsync()
        {
            _closed = false;
            _opened = false;
            _initVideo = false;
            _initAudio = false;
            _descDone = false;
            Status = "正在解DASH初始化段…";
            _openAt = DateTime.Now;

            _video.OnInit = OnVideoInit;
            _audio.OnInit = OnAudioInit;

            _video.Start(0);
            _audio.Start(0);

            StartOpenTimer();
            StartBufferTimer();
        }

        private void StartBufferTimer()
        {
            OnUi(delegate
            {
                try
                {
                    if (_bufferTimer == null)
                    {
                        _bufferTimer = new DispatcherTimer();
                        _bufferTimer.Interval = TimeSpan.FromMilliseconds(200);
                        _bufferTimer.Tick += BufferTick;
                    }
                    _bufferTimer.Start();
                }
                catch (Exception)
                {
                }
            });
        }

        private void StopBufferTimer()
        {
            OnUi(delegate
            {
                try
                {
                    if (_bufferTimer != null)
                    {
                        _bufferTimer.Stop();
                    }
                }
                catch (Exception)
                {
                }
            });
        }

        private void BufferTick(object sender, EventArgs e)
        {
            if (_closed)
            {
                return;
            }

            try
            {
                if (_seekWaiting && !_video.HasPendingSeek && !_audio.HasPendingSeek)
                {
                    CompleteSeek();
                }

                bool ready = false;
                if (_videoBuffering && (_video.HasQueued || _video.Ended))
                {
                    _videoBuffering = false;
                    ready = true;
                }
                if (_audioBuffering && (_audio.HasQueued || _audio.Ended))
                {
                    _audioBuffering = false;
                    ready = true;
                }

                if (ready)
                {
                    ReportGetSampleProgress(1.0);
                }
            }
            catch (Exception)
            {
            }
        }

        private void StartOpenTimer()
        {
            OnUi(delegate
            {
                try
                {
                    if (_openTimer == null)
                    {
                        _openTimer = new DispatcherTimer();
                        _openTimer.Interval = TimeSpan.FromMilliseconds(200);
                        _openTimer.Tick += OpenTick;
                    }
                    _openTimer.Start();
                }
                catch (Exception)
                {
                }
            });
        }

        private void StopOpenTimer()
        {
            OnUi(delegate
            {
                try
                {
                    if (_openTimer != null)
                    {
                        _openTimer.Stop();
                    }
                }
                catch (Exception)
                {
                }
            });
        }

        private void OpenTick(object sender, EventArgs e)
        {
            TryOpen();
        }

        private void OnVideoInit()
        {
            _initVideo = true;
            TryOpen();
        }

        private void OnAudioInit()
        {
            _initAudio = true;
            TryOpen();
        }

        private void TryOpen()
        {
            OnUi(TryOpenCore);
        }

        private void TryOpenCore()
        {
            if (_closed || _opened || !_initVideo || !_initAudio)
            {
                return;
            }

            if (!_descDone)
            {
                _videoDesc = BuildVideo(_video.Track);
                _audioDesc = BuildAudio(_audio.Track);
                if (_videoDesc == null || _audioDesc == null)
                {
                    StopOpenTimer();
                    string why = StatusText;
                    Fail(why.Length > 0 ? "DASH起不来 " + why : "编解码私有数据没解出来");
                    return;
                }
                _descDone = true;
            }

            bool ready = (_video.HasQueued || _video.Ended)
                && (_audio.HasQueued || _audio.Ended);
            if (!ready && (DateTime.Now - _openAt).TotalSeconds < 8)
            {
                Status = "等分片…";
                return;
            }

            Dictionary<MediaSourceAttributesKeys, string> attributes =
                new Dictionary<MediaSourceAttributesKeys, string>();

            if (_durationTicks <= 0)
            {
                _durationTicks = DurationFromSidx();
            }

            attributes[MediaSourceAttributesKeys.Duration] = _durationTicks.ToString();
            attributes[MediaSourceAttributesKeys.CanSeek] = "true";

            List<MediaStreamDescription> streams = new List<MediaStreamDescription>();
            streams.Add(_videoDesc);
            streams.Add(_audioDesc);

            _opened = true;
            Status = "已报开播";
            StopOpenTimer();
            try
            {
                OnUi(delegate { ReportOpenMediaCompleted(attributes, streams); });
            }
            catch (Exception ex)
            {
                Fail("报开播失败 " + ex.Message);
                return;
            }

            if (Opened != null)
            {
                Opened(true, "");
            }
        }

        private void Fail(string reason)
        {
            Status = reason;
            OnUi(delegate { ErrorOccurred(reason); });
        }

        protected override void CloseMedia()
        {
            _closed = true;
            _opened = false;
            StopOpenTimer();
            StopBufferTimer();
            _video.Stop();
            _audio.Stop();
        }

        public void Stop()
        {
            CloseMedia();
        }

        protected override void GetSampleAsync(MediaStreamType mediaStreamType)
        {
            if (mediaStreamType == MediaStreamType.Video)
            {
                _askVideo++;
            }
            else
            {
                _askAudio++;
            }

            if (_closed || !_opened)
            {
                return;
            }

            try
            {
                DeliverNext(mediaStreamType);
            }
            catch (Exception ex)
            {
                Fail("取样本出错 " + ex.Message);
            }
        }

        private void DeliverNext(MediaStreamType mediaStreamType)
        {
            bool video = mediaStreamType == MediaStreamType.Video;
            DashTrackReader reader = video ? _video : _audio;
            MediaStreamDescription description = video ? _videoDesc : _audioDesc;

            if (reader.HasPendingSeek)
            {
                if (video)
                {
                    _videoBuffering = true;
                }
                else
                {
                    _audioBuffering = true;
                }
                ReportGetSampleProgress(0);
                return;
            }

            MediaStreamSample sample = null;
            if (!video && _audioDiscontinuity)
            {
                sample = reader.TakeAudioDiscontinuity(description);
                if (sample != null)
                {
                    _audioDiscontinuity = false;
                }
            }
            else
            {
                sample = reader.TakeSample(description);
            }

            if (sample != null)
            {
                if (video)
                {
                    _sentVideo++;
                }
                else
                {
                    _sentAudio++;
                }

                ReportGetSampleCompleted(sample);
                return;
            }

            if (video)
            {
                _videoBuffering = true;
            }
            else
            {
                _audioBuffering = true;
            }

            double progress = reader.QueuedSeconds / 20.0;
            if (progress > 1)
            {
                progress = 1;
            }
            if (progress < 0)
            {
                progress = 0;
            }

            ReportGetSampleProgress(progress);
        }

        protected override void SeekAsync(long seekToTime)
        {
            if (_closed)
            {
                return;
            }

            double seconds = seekToTime / 10000000.0;
            _video.RequestSeek(seconds);
            _audio.RequestSeek(seconds);

            _seekTicks = seekToTime;
            _videoBuffering = true;
            _audioBuffering = true;

            if (_video.HasPendingSeek || _audio.HasPendingSeek)
            {
                _seekWaiting = true;
                return;
            }

            CompleteSeek();
        }

        private void CompleteSeek()
        {
            _seekWaiting = false;
            _audioDiscontinuity = true;

            double seconds = _seekTicks / 10000000.0;
            double video = _video.CurrentSeconds;
            if (video >= 0)
            {
                seconds = video;
                _audio.RequestSeek(seconds);
            }

            long target = (long)(seconds * 10000000.0);
            OnUi(delegate { ReportSeekCompleted(target); });
        }

        protected override void GetDiagnosticAsync(MediaStreamSourceDiagnosticKind diagnosticKind)
        {
            long value;
            if (diagnosticKind == MediaStreamSourceDiagnosticKind.BufferLevelInBytes)
            {
                value = _video.BufferedBytes + _audio.BufferedBytes;
            }
            else
            {
                int queued = _video.QueuedSeconds > _audio.QueuedSeconds
                    ? _video.QueuedSeconds
                    : _audio.QueuedSeconds;
                value = queued * 1000;
            }

            MediaStreamSourceDiagnosticKind kind = diagnosticKind;
            long report = value;
            OnUi(delegate { ReportGetDiagnosticCompleted(kind, report); });
        }

        protected override void SwitchMediaStreamAsync(MediaStreamDescription mediaStreamDescription)
        {
            MediaStreamDescription target = mediaStreamDescription;
            OnUi(delegate
            {
                try
                {
                    ReportSwitchMediaStreamCompleted(target);
                }
                catch (Exception)
                {
                }
            });
        }

        private long DurationFromSidx()
        {
            DashTrack track = _video.Track;
            if (track.Segments.Count == 0)
            {
                track = _audio.Track;
            }

            double total = 0;
            for (int i = 0; i < track.Segments.Count; i++)
            {
                total += track.Segments[i].DurationSeconds;
            }
            return (long)(total * 10000000.0);
        }

        private static MediaStreamDescription BuildVideo(DashTrack track)
        {
            if (track == null || track.CodecPrivateData.Length == 0 || track.Width <= 0)
            {
                return null;
            }

            Dictionary<MediaStreamAttributeKeys, string> attributes =
                new Dictionary<MediaStreamAttributeKeys, string>();
            attributes[MediaStreamAttributeKeys.CodecPrivateData] = track.CodecPrivateData;
            attributes[MediaStreamAttributeKeys.VideoFourCC] = "H264";
            attributes[MediaStreamAttributeKeys.Width] = track.Width.ToString();
            attributes[MediaStreamAttributeKeys.Height] = track.Height.ToString();

            return new MediaStreamDescription(MediaStreamType.Video, attributes);
        }

        private static MediaStreamDescription BuildAudio(DashTrack track)
        {
            if (track == null || track.CodecPrivateData.Length == 0)
            {
                return null;
            }

            Dictionary<MediaStreamAttributeKeys, string> attributes =
                new Dictionary<MediaStreamAttributeKeys, string>();
            attributes[MediaStreamAttributeKeys.CodecPrivateData] = track.CodecPrivateData;

            return new MediaStreamDescription(MediaStreamType.Audio, attributes);
        }
    }
}
