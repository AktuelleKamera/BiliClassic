using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public static class CoverLoader
    {
        private const int MaxConcurrent = 3;

        private static readonly Dictionary<string, BitmapImage> Cache = new Dictionary<string, BitmapImage>();
        private static readonly List<string> Failed = new List<string>();
        private static readonly Queue<VideoItem> Pending = new Queue<VideoItem>();
        private static readonly List<string> PendingUrls = new List<string>();

        private static int _active;

        public static void Request(VideoItem item)
        {
            if (item == null || item.Cover != null)
            {
                return;
            }

            string url = item.CoverUrl;
            if (string.IsNullOrEmpty(url))
            {
                return;
            }

            BitmapImage cached;
            if (Cache.TryGetValue(url, out cached))
            {
                item.Cover = cached;
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

        private static void Pump()
        {
            while (_active < MaxConcurrent && Pending.Count > 0)
            {
                VideoItem item = Pending.Dequeue();
                string url = item.CoverUrl;
                PendingUrls.Remove(url);

                if (item.Cover != null || Failed.Contains(url))
                {
                    continue;
                }

                BitmapImage cached;
                if (Cache.TryGetValue(url, out cached))
                {
                    item.Cover = cached;
                    continue;
                }

                StartDownload(item, url);
            }
        }

        private static void StartDownload(VideoItem item, string url)
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
                            item.Cover = bitmap;
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
