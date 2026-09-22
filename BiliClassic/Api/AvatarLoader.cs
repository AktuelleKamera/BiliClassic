using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
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

        public static void Request(UserItem item)
        {
            Request(item, false);
        }

        public static void Retry(UserItem item)
        {
            Request(item, true);
        }

        private static void Request(UserItem item, bool clearFailed)
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

            if (clearFailed)
            {
                Failed.Remove(url);
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
