#if WP81
using System;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows;
using Windows.Networking.Proximity;

namespace BiliClassic.Api
{
    public static class NfcSync
    {
        private static ProximityDevice _device;
        private static long _uriSubscription = -1;
        private static long _ndefSubscription = -1;
        private static Action<string> _handler;

        public static bool Available
        {
            get { return ProximityDevice.GetDefault() != null; }
        }

        public static bool Start(Action<string> handler)
        {
            Stop();
            _handler = handler;
            _device = ProximityDevice.GetDefault();
            if (_device == null)
            {
                return false;
            }
            _uriSubscription = _device.SubscribeForMessage("WindowsUri", OnMessage);
            // Android Beam 的 NDEF 带 AAR，可能不走 WindowsUri，原始 NDEF 也收一份
            try
            {
                _ndefSubscription = _device.SubscribeForMessage("NDEF", OnNdefMessage);
            }
            catch (Exception)
            {
                _ndefSubscription = -1;
            }
            return _uriSubscription >= 0 || _ndefSubscription >= 0;
        }

        public static void Stop()
        {
            StopSubscription(_uriSubscription);
            StopSubscription(_ndefSubscription);
            _uriSubscription = -1;
            _ndefSubscription = -1;
            _device = null;
            _handler = null;
        }

        /// <summary>开启接收，收到同步数据弹确认框（可在任何页面）</summary>
        public static bool StartWithPrompt()
        {
            return Start(delegate(string value)
            {
                if (string.IsNullOrEmpty(value)
                    || !value.StartsWith("biliclassic://sync", StringComparison.OrdinalIgnoreCase))
                {
                    return;
                }
                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    MessageBoxResult answer = MessageBox.Show(
                            "收到零距同步数据，是否导入？", "零距同步",
                            MessageBoxButton.OKCancel);
                    if (answer == MessageBoxResult.OK)
                    {
                        Apply(value);
                    }
                });
            });
        }

        private static void StopSubscription(long subscription)
        {
            if (_device == null || subscription < 0)
            {
                return;
            }
            try
            {
                _device.StopSubscribingForMessage(subscription);
            }
            catch (Exception)
            {
            }
        }

        public static bool SendLogin()
        {
            ProximityDevice device = ProximityDevice.GetDefault();
            if (device == null)
            {
                return false;
            }
            string cookie = BiliSession.CookieString;
            if (string.IsNullOrEmpty(cookie))
            {
                return false;
            }
            string encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes(cookie));
            string uri = "biliclassic://sync?from=login&cookie="
                    + Uri.EscapeDataString(encoded);
            device.PublishUriMessage(new Uri(uri));
            return true;
        }

        public static string Apply(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "空数据";
            }
            Uri uri;
            if (!Uri.TryCreate(value, UriKind.Absolute, out uri)
                    || uri.Scheme != "biliclassic" || uri.Host != "sync")
            {
                return "不是零距同步数据";
            }

            string from = GetParameter(uri, "from");
            if (from == "login")
            {
                string cookie = DecodeBase64(GetParameter(uri, "cookie"));
                if (string.IsNullOrEmpty(cookie))
                {
                    return "登录信息无效";
                }
                BiliSession.SetCookieString(cookie);
                BiliSession.Save();
                return "登录信息已同步";
            }
            if (from == "data")
            {
                string json = DecodeGzipBase64(GetParameter(uri, "d"));
                if (string.IsNullOrEmpty(json))
                {
                    return "同步数据无效";
                }
                return ApplyData(json);
            }
            return "已接收：" + value;
        }

        private static string ApplyData(string json)
        {
            int changed = 0;
            Match quality = Regex.Match(json, "\"video_quality\"\\s*:\\s*(\\d+)");
            if (quality.Success)
            {
                int value;
                if (int.TryParse(quality.Groups[1].Value, out value))
                {
                    AppSettings.PlayQuality = value;
                    changed++;
                }
            }
            Match stream = Regex.Match(json, "\"play_stream_format\"\\s*:\\s*(\\d+)");
            if (stream.Success)
            {
                AppSettings.DashPlayback = stream.Groups[1].Value == "16";
                changed++;
            }
            Match cookie = Regex.Match(json, "\"cookies\"\\s*:\\s*\"([^\"]+)\"");
            if (cookie.Success)
            {
                BiliSession.SetCookieString(cookie.Groups[1].Value);
                BiliSession.Save();
                changed++;
            }
            return changed > 0 ? "设置已同步" : "没有可导入的设置";
        }

        private static void OnMessage(ProximityDevice sender, ProximityMessage message)
        {
            Deliver(ReadText(message));
        }

        private static void OnNdefMessage(ProximityDevice sender, ProximityMessage message)
        {
            Deliver(ParseNdefUri(ReadBytes(message)));
        }

        private static void Deliver(string text)
        {
            Action<string> handler = _handler;
            if (handler == null || string.IsNullOrEmpty(text))
            {
                return;
            }
            handler(text);
        }

        private static string ReadText(ProximityMessage message)
        {
            if (message == null || message.Data == null)
            {
                return null;
            }
            try
            {
                Windows.Storage.Streams.DataReader reader =
                        Windows.Storage.Streams.DataReader.FromBuffer(message.Data);
                reader.UnicodeEncoding = Windows.Storage.Streams.UnicodeEncoding.Utf8;
                return reader.ReadString(reader.UnconsumedBufferLength);
            }
            catch (Exception)
            {
                return null;
            }
        }

        private static byte[] ReadBytes(ProximityMessage message)
        {
            if (message == null || message.Data == null)
            {
                return null;
            }
            try
            {
                byte[] bytes = new byte[(int)message.Data.Length];
                using (Windows.Storage.Streams.DataReader reader =
                        Windows.Storage.Streams.DataReader.FromBuffer(message.Data))
                {
                    reader.ReadBytes(bytes);
                }
                return bytes;
            }
            catch (Exception)
            {
                return null;
            }
        }

        /// <summary>从原始 NDEF 里抠出 URI 记录</summary>
        private static string ParseNdefUri(byte[] data)
        {
            if (data == null)
            {
                return null;
            }
            try
            {
                int pos = 0;
                while (pos + 3 <= data.Length)
                {
                    byte header = data[pos++];
                    int tnf = header & 0x07;
                    bool shortRecord = (header & 0x10) != 0;
                    bool hasId = (header & 0x08) != 0;
                    bool last = (header & 0x40) != 0;

                    int typeLength = data[pos++];
                    int payloadLength;
                    if (shortRecord)
                    {
                        payloadLength = data[pos++];
                    }
                    else
                    {
                        payloadLength = (data[pos] << 24) | (data[pos + 1] << 16)
                                | (data[pos + 2] << 8) | data[pos + 3];
                        pos += 4;
                    }

                    int idLength = 0;
                    if (hasId)
                    {
                        idLength = data[pos++];
                    }

                    string type = Encoding.UTF8.GetString(data, pos, typeLength);
                    pos += typeLength + idLength;

                    if (pos + payloadLength > data.Length)
                    {
                        return null;
                    }

                    // TNF=well-known, 类型 U 是 URI 记录
                    if (tnf == 0x01 && type == "U" && payloadLength > 0)
                    {
                        int prefix = data[pos];
                        string suffix = Encoding.UTF8.GetString(
                                data, pos + 1, payloadLength - 1);
                        return UriPrefix(prefix) + suffix;
                    }

                    pos += payloadLength;
                    if (last)
                    {
                        break;
                    }
                }
            }
            catch (Exception)
            {
            }
            return null;
        }

        private static string UriPrefix(int code)
        {
            switch (code)
            {
                case 0x01: return "http://www.";
                case 0x02: return "https://www.";
                case 0x03: return "http://";
                case 0x04: return "https://";
                case 0x05: return "tel:";
                case 0x06: return "mailto:";
                default: return "";
            }
        }

        private static string GetParameter(Uri uri, string key)
        {
            string query = uri == null ? "" : uri.Query;
            if (query.StartsWith("?"))
            {
                query = query.Substring(1);
            }
            string[] pairs = query.Split('&');
            for (int i = 0; i < pairs.Length; i++)
            {
                int eq = pairs[i].IndexOf('=');
                string name = eq >= 0 ? pairs[i].Substring(0, eq) : pairs[i];
                if (Uri.UnescapeDataString(name) != key)
                {
                    continue;
                }
                return eq >= 0
                        ? Uri.UnescapeDataString(pairs[i].Substring(eq + 1))
                        : "";
            }
            return "";
        }

        private static string DecodeBase64(string value)
        {
            try
            {
                byte[] bytes = Convert.FromBase64String(value ?? "");
                return Encoding.UTF8.GetString(bytes, 0, bytes.Length);
            }
            catch (Exception)
            {
                return "";
            }
        }

        private static string DecodeGzipBase64(string value)
        {
            try
            {
                // WP8.1 Silverlight 没有 System.IO.Compression，用现成的 Inflate
                byte[] compressed = Convert.FromBase64String(value ?? "");
                byte[] raw = BiliClassic.Danmaku.Inflate.Decompress(compressed);
                return Encoding.UTF8.GetString(raw, 0, raw.Length);
            }
            catch (Exception)
            {
                return "";
            }
        }
    }
}
#endif
