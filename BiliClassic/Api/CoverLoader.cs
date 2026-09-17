using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    /// <summary>
    /// 封面加载器
    /// 不直接给Image绑URL，自己抓的三个原因
    /// 1限并发，老设备连拉20张打满内存带宽
    /// 2内存缓存加失败标记，滚动不重试坏图
    /// 3下载解码分开，解码强制回UI线程
    /// 队列/缓存/计数只在UI线程改，避免回调线程竞争
    /// </summary>
    public static class CoverLoader
    {
        /// <summary>最大并发下载数</summary>
        private const int MaxConcurrent = 3;

        private static readonly Dictionary<string, BitmapImage> Cache = new Dictionary<string, BitmapImage>();
        private static readonly List<string> Failed = new List<string>();
        private static readonly Queue<VideoItem> Pending = new Queue<VideoItem>();
        private static readonly List<string> PendingUrls = new List<string>();

        private static int _active;

        /// <summary>请求加载封面，已加载/加载中/失败都跳过</summary>
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
                // 回调可能不在UI线程，切回去串行化队列状态
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
                            // MemoryStream不能用using提前关
                            // BitmapImage延后解码，流要活到解码完成
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
