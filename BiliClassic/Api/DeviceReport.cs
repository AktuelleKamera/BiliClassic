using System;
using System.Globalization;
using System.IO;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Windows;
using Microsoft.Phone.Info;

// Android版也有的设备信息上报（
namespace BiliClassic.Api
{
    public static class DeviceReport
    {
        private const string ReportUrl = "http://status.awmc233.cn/device_report.php";

        private const string KeyLastReport = "device_report_last";
        private const string KeyInstallId = "device_report_install";

        private const long MinIntervalMs = 6L * 60L * 60L * 1000L;

        public static void ReportIfNeeded()
        {
            try
            {
                long now = CurrentUnixMs();
                long last;
                if (long.TryParse(LocalStore.Get(KeyLastReport), out last)
                    && last > 0 && now - last < MinIntervalMs)
                {
                    return;
                }

                string key = DeviceKey();
                if (key.Length == 0)
                {
                    return;
                }

                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    Send(key);
                });
            }
            catch (Exception)
            {
            }
        }

        private static void Send(string key)
        {
            string body;
            try
            {
                body = BuildBody(key);
            }
            catch (Exception)
            {
                return;
            }

            SendTo(ReportUrl, key, body);
        }

        private static void SendTo(string url, string key, string body)
        {
            HttpWebRequest request;
            try
            {
                request = (HttpWebRequest)WebRequest.Create(new Uri(url));
                request.Method = "POST";
                request.ContentType = "application/x-www-form-urlencoded; charset=utf-8";
                request.UserAgent = Http.UserAgent;
                request.AllowReadStreamBuffering = true;
            }
            catch (Exception)
            {
                return;
            }

            byte[] payload = Encoding.UTF8.GetBytes(body ?? "");

            request.BeginGetRequestStream(delegate(IAsyncResult ar)
            {
                try
                {
                    using (Stream stream = request.EndGetRequestStream(ar))
                    {
                        stream.Write(payload, 0, payload.Length);
                    }
                }
                catch (Exception)
                {
                    return;
                }

                request.BeginGetResponse(delegate(IAsyncResult ar2)
                {
                    try
                    {
                        HttpWebResponse response = (HttpWebResponse)request.EndGetResponse(ar2);
                        int code = (int)response.StatusCode;
                        try
                        {
                            response.Close();
                        }
                        catch (Exception)
                        {
                        }

                        if (code >= 200 && code < 300)
                        {
                            LocalStore.Set(KeyLastReport, CurrentUnixMs().ToString());
                        }
                    }
                    catch (Exception)
                    {
                    }
                }, null);
            }, null);
        }

        private static string BuildBody(string key)
        {
            StringBuilder body = new StringBuilder();
            Add(body, "device_key", key);
            Add(body, "platform", "wp");
            string manufacturer = DeviceInfo("DeviceManufacturer", "");
            string model = DeviceModel(manufacturer);
            Add(body, "brand", manufacturer.Length > 0 ? manufacturer : "wp");
            Add(body, "manufacturer", manufacturer);
            Add(body, "model", model);
            Add(body, "os_version", OsVersion());
            Add(body, "locale", CultureInfo.CurrentCulture.Name);
            Add(body, "app_version", AppVersion());

            try
            {
                Add(body, "screen_width",
                    ((int)Application.Current.Host.Content.ActualWidth).ToString());
                Add(body, "screen_height",
                    ((int)Application.Current.Host.Content.ActualHeight).ToString());
            }
            catch (Exception)
            {
            }

            return body.ToString();
        }

        private static void Add(StringBuilder body, string name, string value)
        {
            if (body.Length > 0)
            {
                body.Append('&');
            }
            body.Append(Uri.EscapeDataString(name));
            body.Append('=');
            body.Append(Uri.EscapeDataString(value == null ? "" : value));
        }

        private static string DeviceKey()
        {
            string raw = "";
            try
            {
                object value = DeviceExtendedProperties.GetValue("DeviceUniqueId");
                byte[] bytes = value as byte[];
                if (bytes != null && bytes.Length > 0)
                {
                    raw = Convert.ToBase64String(bytes);
                }
            }
            catch (Exception)
            {
            }

            if (raw.Length == 0)
            {
                raw = LocalStore.Get(KeyInstallId);
                if (string.IsNullOrEmpty(raw))
                {
                    raw = Guid.NewGuid().ToString();
                    LocalStore.Set(KeyInstallId, raw);
                }
            }

            return Sha256("biliclassic-device-v1|" + raw);
        }

        private static string DeviceModel(string manufacturer)
        {
            string model = DeviceStatusName();
            if (model.Length == 0)
            {
                model = DeviceInfo("DeviceName", "");
            }
            if (model.Length > 0 && manufacturer.Length > 0
                && model.Equals(manufacturer, StringComparison.OrdinalIgnoreCase))
            {
                return "";
            }
            return model;
        }

        private static string DeviceStatusName()
        {
            try
            {
                Type type = Type.GetType(
                    "Microsoft.Phone.Info.DeviceStatus, Microsoft.Phone");
                if (type == null)
                {
                    return "";
                }
                PropertyInfo property = type.GetProperty("DeviceName");
                if (property == null)
                {
                    return "";
                }
                MethodInfo getter = property.GetGetMethod();
                if (getter == null)
                {
                    return "";
                }
                object value = getter.Invoke(null, null);
                string text = value as string;
                return text == null ? "" : text.Trim();
            }
            catch (Exception)
            {
                return "";
            }
        }

        private static string OsVersion()
        {
            Version version = Environment.OSVersion.Version;
            if (version.Major == 7)
            {
                if (version.Minor < 1)
                {
                    // 7.0.x = WP7.0
                    return "7.0";
                }
                // Microsoft的78命名
                return version.Build >= 8858 ? "7.8" : "7.5";
            }
            if (version.Major == 8)
            {
                return version.Minor >= 1 ? "8.1" : "8.0";
            }
            if (version.Major == 6 && version.Minor == 3)
            {
                return "8.1";
            }
            return version.Major == 10 ? "10" : version.ToString();
        }

        private static string DeviceInfo(string key, string fallback)
        {
            try
            {
                object value = DeviceExtendedProperties.GetValue(key);
                string text = value as string;
                if (!string.IsNullOrEmpty(text))
                {
                    return text.Trim();
                }
            }
            catch (Exception)
            {
            }
            return fallback;
        }

        private static string AppVersion()
        {
            try
            {
                string full = Assembly.GetExecutingAssembly().FullName;
                int start = full.IndexOf("Version=", StringComparison.Ordinal);
                if (start >= 0)
                {
                    start += "Version=".Length;
                    int end = full.IndexOf(',', start);
                    return end > start ? full.Substring(start, end - start)
                        : full.Substring(start);
                }
            }
            catch (Exception)
            {
            }
            return "";
        }

        private static string Sha256(string value)
        {
            try
            {
                using (SHA256Managed sha = new SHA256Managed())
                {
                    byte[] hash = sha.ComputeHash(Encoding.UTF8.GetBytes(value ?? ""));
                    StringBuilder hex = new StringBuilder(hash.Length * 2);
                    for (int i = 0; i < hash.Length; i++)
                    {
                        hex.Append(hash[i].ToString("x2"));
                    }
                    return hex.ToString();
                }
            }
            catch (Exception)
            {
                return "";
            }
        }

        private static long CurrentUnixMs()
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)DateTime.UtcNow.Subtract(epoch).TotalMilliseconds;
        }
    }
}
