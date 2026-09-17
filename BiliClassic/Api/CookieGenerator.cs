using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>
    /// 合成cookie，浏览器和设备指纹类
    /// buvid3/buvid4/bili_ticket/_uuid/b_lsid/buvid_fp/b_nut
    /// WP7上Cookie是受限头，这里只生成和持久化
    /// 由BiliSession统一灌进容器，每个请求自动带上
    /// </summary>
    public static class CookieGenerator
    {
        private const string FingerUrl = "https://api.bilibili.com/x/frontend/finger/spi";

        private const string TicketUrl =
            "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket"
            + "?key_id=ec02&hexsign=";

        /// <summary>bili_ticket的HMAC密钥</summary>
        private const string TicketHmacKey = "XgwSnGZ1p";

        private const string Charset = "0123456789ABCDEF";

        /// <summary>_uuid分段长度，8-4-4-4-12</summary>
        private static readonly int[] Pck = new int[] { 8, 4, 4, 4, 12 };

        /// <summary>
        /// 16项，最后一项是"10"不是单字符
        /// 生成的_uuid里会出现"10"，服务端只做宽松校验
        /// </summary>
        private static readonly string[] Mp = new string[]
        {
            "1", "2", "3", "4", "5", "6", "7", "8",
            "9", "A", "B", "C", "D", "E", "F", "10"
        };

        // 键名与安卓版一致
        public const string KeyBuvid3 = "buvid3";
        public const string KeyBuvid4 = "buvid4";
        public const string KeyTicket = "bili_ticket";
        public const string KeyTicketExpires = "bili_ticket_expires";
        public const string KeyUuid = "_uuid";
        public const string KeyBlsid = "b_lsid";
        public const string KeyBuvidFp = "buvid_fp";
        public const string KeyBNut = "b_nut";
        public const string KeyLiveBuvid = "LIVE_BUVID";
        public const string KeyBrowserResolution = "browser_resolution";
        public const string KeyPvid = "PVID";
        public const string KeyEnableWebPush = "enable_web_push";
        public const string KeyHomeFeedColumn = "home_feed_column";

        private static readonly Random Rng = new Random();

        /// <summary>防重入</summary>
        private static bool _ensuring;

        /// <summary>
        /// 生成缺失的指纹cookie，回调必被调一次
        /// 已有值不重复生成，通常不发请求
        /// </summary>
        public static void Ensure(Action onDone)
        {
            if (_ensuring)
            {
                if (onDone != null)
                {
                    onDone();
                }
                return;
            }
            _ensuring = true;

            // 纯本地生成的几个，不需要网络
            if (!LocalStore.Has(KeyUuid))
            {
                LocalStore.Set(KeyUuid, GenUuidInfoc());
            }
            if (!LocalStore.Has(KeyBlsid))
            {
                LocalStore.Set(KeyBlsid, GenBlsid());
            }
            if (!LocalStore.Has(KeyBuvidFp))
            {
                LocalStore.Set(KeyBuvidFp, GenBuvidFp());
            }
            if (!LocalStore.Has(KeyBNut))
            {
                LocalStore.Set(KeyBNut, CurrentUnixSeconds().ToString());
            }

            // 静态指纹cookie
            // 它们会混进之后所有请求的Cookie头，所以一并生成
            if (!LocalStore.Has(KeyLiveBuvid))
            {
                LocalStore.Set(KeyLiveBuvid, GenLiveBuvid());
            }
            if (!LocalStore.Has(KeyBrowserResolution))
            {
                LocalStore.Set(KeyBrowserResolution, "1280-720");
            }
            if (!LocalStore.Has(KeyPvid))
            {
                LocalStore.Set(KeyPvid, "1");
            }
            if (!LocalStore.Has(KeyEnableWebPush))
            {
                LocalStore.Set(KeyEnableWebPush, "DISABLE");
            }
            if (!LocalStore.Has(KeyHomeFeedColumn))
            {
                LocalStore.Set(KeyHomeFeedColumn, "4");
            }

            EnsureBuvids(delegate
            {
                // 先把buvid3灌进容器，请求bili_ticket时要带上
                BiliSession.Refresh();

                EnsureTicket(delegate
                {
                    _ensuring = false;
                    BiliSession.Refresh();
                    if (onDone != null)
                    {
                        onDone();
                    }
                });
            });
        }

        /// <summary>
        /// 返回除登录态外的合成cookie
        /// 供BiliSession合并进CookieContainer
        /// </summary>
        public static List<KeyValuePair<string, string>> GetSynthetic()
        {
            List<KeyValuePair<string, string>> list = new List<KeyValuePair<string, string>>();
            Add(list, "buvid3", LocalStore.Get(KeyBuvid3));
            Add(list, "buvid4", LocalStore.Get(KeyBuvid4));
            Add(list, "bili_ticket", LocalStore.Get(KeyTicket));
            Add(list, "_uuid", LocalStore.Get(KeyUuid));
            Add(list, "b_lsid", LocalStore.Get(KeyBlsid));
            Add(list, "buvid_fp", LocalStore.Get(KeyBuvidFp));
            Add(list, "b_nut", LocalStore.Get(KeyBNut));
            Add(list, "bili_ticket_expires", LocalStore.Get(KeyTicketExpires));
            Add(list, "LIVE_BUVID", LocalStore.Get(KeyLiveBuvid));
            Add(list, "browser_resolution", LocalStore.Get(KeyBrowserResolution));
            Add(list, "PVID", LocalStore.Get(KeyPvid));
            Add(list, "enable_web_push", LocalStore.Get(KeyEnableWebPush));
            Add(list, "home_feed_column", LocalStore.Get(KeyHomeFeedColumn));
            return list;
        }

        /// <summary>取可用的buvid3，没有返回空串</summary>
        public static string GetBuvid3()
        {
            return LocalStore.Get(KeyBuvid3);
        }

        private static void Add(List<KeyValuePair<string, string>> list, string name, string value)
        {
            if (!string.IsNullOrEmpty(value))
            {
                list.Add(new KeyValuePair<string, string>(name, value));
            }
        }

        // ---------------- buvid ----------------

        private static void EnsureBuvids(Action onDone)
        {
            if (IsUsableBuvid(LocalStore.Get(KeyBuvid3)))
            {
                onDone();
                return;
            }

            Http.GetText(FingerUrl, Http.Referer, delegate(HttpResult http)
            {
                try
                {
                    if (http != null && !string.IsNullOrEmpty(http.Body))
                    {
                        Match b3 = Regex.Match(http.Body, @"""b_3"":\s*""([^""]*)""");
                        Match b4 = Regex.Match(http.Body, @"""b_4"":\s*""([^""]*)""");
                        if (b3.Success && b3.Groups[1].Value.Length > 0)
                        {
                            LocalStore.Set(KeyBuvid3, b3.Groups[1].Value);
                        }
                        if (b4.Success && b4.Groups[1].Value.Length > 0)
                        {
                            LocalStore.Set(KeyBuvid4, b4.Groups[1].Value);
                        }
                    }
                }
                catch (Exception)
                {
                }
                onDone();
            });
        }

        /// <summary>
        /// buvid3是否可用
        /// 真实形态是UUID+时间戳+infoc
        /// "XY..."开头的假buvid服务端不认
        /// </summary>
        private static bool IsUsableBuvid(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return false;
            }
            if (value.StartsWith("XY", StringComparison.Ordinal))
            {
                return false;
            }
            return value.Length >= 20;
        }

        // ---------------- bili_ticket ----------------

        private static void EnsureTicket(Action onDone)
        {
            if (LocalStore.Has(KeyTicket))
            {
                onDone();
                return;
            }

            int ts = (int)CurrentUnixSeconds();
            string hexsign = HmacSha256Hex(TicketHmacKey, "ts" + ts);
            // query里有context[ts]，方括号会让Uri拒收，Http那边做了容错
            string url = TicketUrl + hexsign + "&context[ts]=" + ts;

            Http.PostForm(url, "", Http.Referer, null, null, delegate(HttpResult http)
            {
                try
                {
                    if (http != null && !string.IsNullOrEmpty(http.Body))
                    {
                        Match ticket = Regex.Match(http.Body, @"""ticket"":\s*""([^""]*)""");
                        Match createdAt = Regex.Match(http.Body, @"""created_at"":\s*(\d+)");

                        if (ticket.Success && ticket.Groups[1].Value.Length > 0)
                        {
                            LocalStore.Set(KeyTicket, ticket.Groups[1].Value);

                            long created = 0;
                            if (createdAt.Success)
                            {
                                long.TryParse(createdAt.Groups[1].Value, out created);
                            }
                            // 有效期created_at加3天
                            LocalStore.Set(KeyTicketExpires,
                                (created + 3L * 24L * 60L * 60L).ToString());
                        }
                    }
                }
                catch (Exception)
                {
                }
                onDone();
            });
        }

        // ---------------- 各 cookie 的生成算法 ----------------

        /// <summary>
        /// HMAC-SHA256，输出小写hex
        /// </summary>
        private static string HmacSha256Hex(string key, string message)
        {
            try
            {
                HMACSHA256 hmac = new HMACSHA256(Encoding.UTF8.GetBytes(key));
                byte[] hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(message));
                StringBuilder sb = new StringBuilder(hash.Length * 2);
                for (int i = 0; i < hash.Length; i++)
                {
                    sb.Append(hash[i].ToString("x2"));
                }
                return sb.ToString();
            }
            catch (Exception)
            {
                return "";
            }
        }

        /// <summary>_uuid，8-4-4-4-12随机加5位时间后缀和infoc</summary>
        private static string GenUuidInfoc()
        {
            long t = CurrentUnixMilliseconds() % 100000;
            StringBuilder sb = new StringBuilder();

            lock (Rng)
            {
                for (int s = 0; s < Pck.Length; s++)
                {
                    for (int i = 0; i < Pck[s]; i++)
                    {
                        sb.Append(Mp[Rng.Next(16)]);
                    }
                    sb.Append('-');
                }
            }

            sb.Remove(sb.Length - 1, 1);
            sb.Append(t.ToString("D5")).Append("infoc");
            return sb.ToString();
        }

        /// <summary>b_lsid，8位随机十六进制加毫秒时间戳</summary>
        private static string GenBlsid()
        {
            StringBuilder sb = new StringBuilder(8);
            lock (Rng)
            {
                for (int i = 0; i < 8; i++)
                {
                    sb.Append(Charset[Rng.Next(Charset.Length)]);
                }
            }
            return sb.ToString() + "_" + CurrentUnixMilliseconds().ToString("x").ToUpper();
        }

        /// <summary>buvid_fp，毫秒时间戳异或两个常量各取16位hex</summary>
        private static string GenBuvidFp()
        {
            long now = CurrentUnixMilliseconds();
            return (now ^ 0x52DCE729L).ToString("x16") + (now ^ 0x38495AB5L).ToString("x16");
        }

        /// <summary>LIVE_BUVID，AUTO加16位十进制随机数</summary>
        private static string GenLiveBuvid()
        {
            const long Min = 1000000000000000L;
            const long Max = 9999999999999999L;
            double r;
            lock (Rng)
            {
                r = Rng.NextDouble();
            }
            return "AUTO" + (Min + (long)(r * (Max - Min))).ToString();
        }

        private static long CurrentUnixMilliseconds()
        {
            return DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond;
        }

        private static long CurrentUnixSeconds()
        {
            return CurrentUnixMilliseconds() / 1000;
        }
    }
}
