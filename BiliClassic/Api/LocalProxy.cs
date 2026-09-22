#if WP81
using System;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;
using Windows.Networking;
using Windows.Networking.Sockets;
using Windows.Storage.Streams;
using Windows.Web.Http;

namespace BiliClassic.Api
{
    // 【重要】MediaElement 改不了请求头，只能走这个本地代理补 Referer，仅 WP8.1 有，喵
    public static class LocalProxy
    {
        private const int Port = 19001;

        private static StreamSocketListener _listener;

        private static string _host = "";

        private static int _generation;

        private static volatile string _videoUrl = "";
        private static volatile string _audioUrl = "";

        private static readonly HttpClient VideoClient = new HttpClient();
        private static readonly HttpClient AudioClient = new HttpClient();

        private const int MaxStreamRetries = 3;

        private static int _served;
        private static int _inFlight;
        private static string _last = "";

        private static readonly List<StreamSocket> Live = new List<StreamSocket>();

        public static string Status = "";

        public static string Referer = Http.Referer;

        public static string Summary()
        {
            return "代理" + _served + "个/挂" + _inFlight + " " + _last;
        }

        public static bool IsRunning
        {
            get { return _listener != null; }
        }

        public static string VideoSource(int nonce)
        {
            return Origin() + "/video.mp4?n=" + nonce;
        }

        public static string AudioSource(int nonce)
        {
            return Origin() + "/audio.mp4?n=" + nonce;
        }

        private static string Origin()
        {
            return _host.IndexOf(':') >= 0
                ? "http://[" + _host + "]:" + Port
                : "http://" + _host + ":" + Port;
        }

        public static void SetTargets(string videoUrl, string audioUrl)
        {
            _videoUrl = videoUrl == null ? "" : videoUrl;
            _audioUrl = audioUrl == null ? "" : audioUrl;
        }



        public static void Start()
        {
            Start(null);
        }

        public static void Start(Action<string> onReady)
        {
            if (_listener != null)
            {
                if (onReady != null)
                {
                    onReady(Status);
                }
                return;
            }

            Bind(onReady);
        }

        private static async void Bind(Action<string> onReady)
        {
            int generation = _generation;
            string[] hosts = new string[] { "127.0.0.1", "localhost", "::1" };

            for (int i = 0; i < hosts.Length; i++)
            {
                StreamSocketListener listener = new StreamSocketListener();
                listener.ConnectionReceived += OnConnection;
                try
                {
                    await listener.BindEndpointAsync(new HostName(hosts[i]), Port.ToString());
                    if (generation != _generation)
                    {
                        listener.Dispose();
                        return;
                    }

                    _listener = listener;
                    _host = hosts[i];
                    Check(onReady);
                    return;
                }
                catch (Exception ex)
                {
                    try { listener.Dispose(); } catch (Exception) { }
                    Status = hosts[i] + "绑定失败 " + ex.Message;
                }
            }

            if (onReady != null)
            {
                onReady(Status);
            }
        }

        private static async void Check(Action<string> onReady)
        {
            try
            {
                HttpClient client = new HttpClient();
                HttpResponseMessage response = await client.GetAsync(new Uri(Origin() + "/ping"));
                Status = (int)response.StatusCode == 200
                    ? "通"
                    : "自检 " + (int)response.StatusCode;
                response.Dispose();
            }
            catch (Exception ex)
            {
                Status = "自检失败 " + ex.Message;
            }

            if (onReady != null)
            {
                onReady(Status);
            }
        }

        public static void Stop()
        {
            _generation++;

            StreamSocketListener listener = _listener;
            _listener = null;
            _host = "";
            Status = "";
            if (listener != null)
            {
                try { listener.Dispose(); } catch (Exception) { }
            }

            lock (Live)
            {
                foreach (StreamSocket socket in Live)
                {
                    try { socket.Dispose(); } catch (Exception) { }
                }
                Live.Clear();
            }

            _videoUrl = "";
            _audioUrl = "";
        }

        private static async void OnConnection(StreamSocketListener sender,
                                               StreamSocketListenerConnectionReceivedEventArgs args)
        {
            StreamSocket socket = args.Socket;
            lock (Live)
            {
                Live.Add(socket);
            }

            try
            {
                while (true)
                {
                    string request = await ReadRequest(socket);
                    if (request.Length == 0 || !await Handle(socket, request))
                    {
                        return;
                    }
                }
            }
            catch (Exception) { }
            finally
            {
                lock (Live)
                {
                    Live.Remove(socket);
                }
                try { socket.Dispose(); } catch (Exception) { }
            }
        }

        private static async Task<bool> Handle(StreamSocket socket, string request)
        {
            string first = FirstLine(request);
            string path = PathOf(first);

            if (path.StartsWith("/ping", StringComparison.OrdinalIgnoreCase))
            {
                await WriteSimple(socket, 200, "ok");
                return false;
            }

            bool audio = path.StartsWith("/audio", StringComparison.OrdinalIgnoreCase);
            string target = audio ? _audioUrl : _videoUrl;
            if (target.Length == 0)
            {
                return false;
            }

            _inFlight++;
            try
            {
                return await Serve(socket, target, HeaderValue(request, "Range"),
                    first.StartsWith("HEAD", StringComparison.OrdinalIgnoreCase), audio);
            }
            finally
            {
                _inFlight--;
            }
        }

        private static async Task<string> ReadRequest(StreamSocket socket)
        {
            StringBuilder sb = new StringBuilder();
            DataReader reader = new DataReader(socket.InputStream);
            reader.InputStreamOptions = InputStreamOptions.Partial;
            try
            {
                while (sb.Length < 16384)
                {
                    uint read = await reader.LoadAsync(1024);
                    if (read == 0)
                    {
                        break;
                    }

                    sb.Append(reader.ReadString(read));
                    if (sb.ToString().IndexOf("\r\n\r\n", StringComparison.Ordinal) >= 0)
                    {
                        break;
                    }
                }
            }
            finally
            {
                reader.DetachStream();
                reader.Dispose();
            }
            return sb.ToString();
        }

        private static async Task<bool> Serve(StreamSocket socket, string target, string range,
                                              bool head, bool audio)
        {
            HttpResponseMessage response = await Get(target, range, head, audio);
            if ((response == null || !IsOk((int)response.StatusCode))
                && target.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                if (response != null)
                {
                    response.Dispose();
                }
                response = await Get("http://" + target.Substring(8), range, head, audio);
            }

            if (response == null)
            {
                await WriteSimple(socket, 502, "Bad Gateway");
                return false;
            }

            if (!IsOk((int)response.StatusCode))
            {
                int code = (int)response.StatusCode;
                response.Dispose();
                await WriteSimple(socket, code, "Upstream");
                return false;
            }

            _served++;
            _last = (audio ? "audio " : "video ") + (int)response.StatusCode;
            if (response.Content.Headers.ContentLength.HasValue)
            {
                _last += " " + (response.Content.Headers.ContentLength.Value / 1024) + "K";
            }

            bool keepAlive = await WriteHeaders(socket, response);
            if (head)
            {
                response.Dispose();
                return keepAlive;
            }

            long total = response.Content.Headers.ContentLength.HasValue
                ? (long)response.Content.Headers.ContentLength.Value
                : -1;
            long written = 0;

            for (int attempt = 0; attempt <= MaxStreamRetries; attempt++)
            {
                bool ended = false;
                long copied = 0;
                try
                {
                    IInputStream input = await response.Content.ReadAsInputStreamAsync();
                    using (input)
                    {
                        copied = (long)await RandomAccessStream.CopyAsync(input, socket.OutputStream);
                    }
                    ended = true;
                }
                catch (Exception) { }
                finally
                {
                    response.Dispose();
                }

                written += copied;
                if (!ended || total < 0 || written >= total)
                {
                    break;
                }

                response = await Get(target, "bytes=" + (RangeStart(range) + written) + "-", false, audio);
                if (response == null || !IsOk((int)response.StatusCode))
                {
                    if (response != null)
                    {
                        response.Dispose();
                    }
                    break;
                }
            }

            return keepAlive;
        }


        private static async Task<HttpResponseMessage> Get(string url, string range, bool head, bool audio)
        {
            try
            {
                HttpRequestMessage request = new HttpRequestMessage(
                    head ? HttpMethod.Head : HttpMethod.Get, new Uri(url));
                request.Headers.Referer = new Uri(Referer);
                request.Headers.UserAgent.ParseAdd(Http.UserAgent);

                if (!head)
                {
                    request.Headers.TryAppendWithoutValidation("Range",
                        string.IsNullOrEmpty(range) ? "bytes=0-" : range);
                }

                HttpClient client = audio ? AudioClient : VideoClient;
                return await client.SendRequestAsync(request, HttpCompletionOption.ResponseHeadersRead);
            }
            catch (Exception)
            {
                return null;
            }
        }

        private static bool IsOk(int code)
        {
            return code >= 200 && code < 300;
        }

        private static long RangeStart(string range)
        {
            if (string.IsNullOrEmpty(range))
            {
                return 0;
            }

            int equals = range.IndexOf('=');
            if (equals < 0)
            {
                return 0;
            }

            string spec = range.Substring(equals + 1);
            int dash = spec.IndexOf('-');
            if (dash <= 0)
            {
                return 0;
            }

            long start;
            return long.TryParse(spec.Substring(0, dash), out start) ? start : 0;
        }

        private static async Task<bool> WriteHeaders(StreamSocket socket, HttpResponseMessage response)
        {
            int code = (int)response.StatusCode;
            StringBuilder sb = new StringBuilder();
            sb.Append(code == 206 ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
            sb.Append("Accept-Ranges: bytes\r\n");

            if (response.Content.Headers.ContentRange != null)
            {
                sb.Append("Content-Range: ")
                    .Append(response.Content.Headers.ContentRange.ToString()).Append("\r\n");
            }

            string type = response.Content.Headers.ContentType != null
                ? response.Content.Headers.ContentType.ToString()
                : "video/mp4";
            sb.Append("Content-Type: ").Append(type).Append("\r\n");

            if (response.Content.Headers.ContentLength.HasValue)
            {
                sb.Append("Content-Length: ")
                    .Append(response.Content.Headers.ContentLength.Value).Append("\r\n");
            }

            sb.Append("Connection: close\r\n\r\n");

            await Write(socket, sb.ToString());
            return false;
        }

        private static async Task WriteSimple(StreamSocket socket, int status, string reason)
        {
            string body = reason + "\r\n";
            string head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + Encoding.UTF8.GetByteCount(body) + "\r\n"
                + "Connection: close\r\n\r\n";
            await Write(socket, head + body);
        }

        private static async Task Write(StreamSocket socket, string text)
        {
            DataWriter writer = new DataWriter(socket.OutputStream);
            try
            {
                writer.WriteBytes(Encoding.UTF8.GetBytes(text));
                await writer.StoreAsync();
                writer.DetachStream();
            }
            finally
            {
                writer.Dispose();
            }
        }

        private static string FirstLine(string request)
        {
            int idx = request.IndexOf("\r\n", StringComparison.Ordinal);
            return idx > 0 ? request.Substring(0, idx) : request;
        }

        private static string PathOf(string firstLine)
        {
            string[] parts = firstLine.Split(' ');
            return parts.Length >= 2 ? parts[1] : "/";
        }

        private static string HeaderValue(string request, string name)
        {
            string[] lines = request.Split(new string[] { "\r\n" }, StringSplitOptions.None);
            for (int i = 0; i < lines.Length; i++)
            {
                int idx = lines[i].IndexOf(':');
                if (idx <= 0)
                {
                    continue;
                }

                if (string.Compare(lines[i].Substring(0, idx).Trim(), name,
                        StringComparison.OrdinalIgnoreCase) == 0)
                {
                    return lines[i].Substring(idx + 1).Trim();
                }
            }
            return "";
        }
    }
}
#endif
