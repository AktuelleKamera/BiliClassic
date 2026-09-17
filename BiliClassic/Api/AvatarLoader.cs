using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    /// <summary>
    /// 头像加载器
    /// 固定1:1，不能与封面共用
    /// </summary>
    public static class AvatarLoader
    {
        private const int Size = 240;

        private const int MaxConcurrent = 3;

        private static readonly Dictionary<string, BitmapImage> Cache =
            new Dictionary<string, BitmapImage>();
        private static readonly List<string> Failed = new List<string>();
        private static readonly Queue<UserItem> Pending = new Queue<UserItem>();
        private static readonly List<string> PendingUrls = new List<string>();

        private static int _active;

        /// <summary>请求加载头像，已加载/加载中/失败都跳过</summary>
        public static void Request(UserItem item)
        {
            if (item == null || item.Avatar != null)
            {
                return;
            }

            string url = ThumbUrl(item);
            if (string.IsNullOrEmpty(url))
            {
                return;
            }

            BitmapImage cached;
            if (Cache.TryGetValue(url, out cached))
            {
                item.Avatar = cached;
                return;
            }

            if (Failed.Contains(url) || PendingUrls.Contains(url))
            {
                return;
            }

            Pending.Enqueue(item);
            PendingUrls.Add(url);
            Pump();
        }

        /// <summary>
        /// 头像缩略图地址
        /// 复用VideoItem.BuildThumbUrl
        /// </summary>
        private static string ThumbUrl(UserItem item)
        {
            string url = item.AvatarUrl ?? "";
            if (url.StartsWith("//", StringComparison.Ordinal))
            {
                url = "https:" + url;
            }
            return VideoItem.BuildThumbUrl(url, Size, Size);
        }

        private static void Pump()
        {
            while (_active < MaxConcurrent && Pending.Count > 0)
            {
                UserItem item = Pending.Dequeue();
                string url = ThumbUrl(item);
                PendingUrls.Remove(url);

                if (item.Avatar != null || Failed.Contains(url))
                {
                    continue;
                }

                BitmapImage cached;
                if (Cache.TryGetValue(url, out cached))
                {
                    item.Avatar = cached;
                    continue;
                }

                StartDownload(item, url);
            }
        }

        private static void StartDownload(UserItem item, string url)
        {
            _active++;
            Http.GetBytes(url, Http.Referer, delegate(byte[] bytes, string error)
            {
                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    _active--;

                    if (bytes == null || bytes.Length == 0)
                    {
                        if (!Failed.Contains(url))
                        {
                            Failed.Add(url);
                        }
                    }
                    else
                    {
                        try
                        {
                            BitmapImage bitmap = new BitmapImage();
                            bitmap.SetSource(new MemoryStream(bytes));
                            Cache[url] = bitmap;
                            item.Avatar = bitmap;
                        }
                        catch (Exception)
                        {
                            if (!Failed.Contains(url))
                            {
                                Failed.Add(url);
                            }
                        }
                    }

                    Pump();
                });
            });
        }
    }
}
