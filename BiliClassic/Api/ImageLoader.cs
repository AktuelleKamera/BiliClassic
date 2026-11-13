using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;

namespace BiliClassic.Api
{
    public static class ImageLoader
    {
        private const int MaxConcurrent = 3;

        private static readonly Dictionary<string, BitmapImage> Cache =
            new Dictionary<string, BitmapImage>();
        private static readonly List<string> Failed = new List<string>();
        private static readonly Queue<KeyValuePair<string, Action<BitmapImage>>> Pending =
            new Queue<KeyValuePair<string, Action<BitmapImage>>>();

        private static int _active;

        public static void Load(string url, Action<BitmapImage> onLoaded)
        {
            if (string.IsNullOrEmpty(url) || onLoaded == null)
            {
                return;
            }

            BitmapImage cached;
            if (Cache.TryGetValue(url, out cached))
            {
                onLoaded(cached);
                return;
            }

            if (Failed.Contains(url))
            {
                return;
            }

            Pending.Enqueue(new KeyValuePair<string, Action<BitmapImage>>(url, onLoaded));
            Pump();
        }

        private static void Pump()
        {
            while (_active < MaxConcurrent && Pending.Count > 0)
            {
                KeyValuePair<string, Action<BitmapImage>> job = Pending.Dequeue();
                string url = job.Key;

                if (Failed.Contains(url))
                {
                    continue;
                }

                BitmapImage cached;
                if (Cache.TryGetValue(url, out cached))
                {
                    try
                    {
                        job.Value(cached);
                    }
                    catch (Exception)
                    {
                    }
                    continue;
                }

                _active++;
                Start(url, job.Value);
            }
        }

        private static void Start(string url, Action<BitmapImage> onLoaded)
        {
            Http.GetBytes(url, Http.Referer, delegate(byte[] bytes, string error)
            {
                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    _active--;

                    BitmapImage bitmap = null;
                    if (bytes != null && bytes.Length > 0)
                    {
                        try
                        {
                            bitmap = new BitmapImage();
                            bitmap.SetSource(new MemoryStream(bytes));
                            Cache[url] = bitmap;
                        }
                        catch (Exception)
                        {
                            if (!Failed.Contains(url))
                            {
                                Failed.Add(url);
                            }
                        }
                    }
                    else if (!Failed.Contains(url))
                    {
                        Failed.Add(url);
                    }

                    if (bitmap != null)
                    {
                        try
                        {
                            onLoaded(bitmap);
                        }
                        catch (Exception)
                        {
                        }
                    }

                    Pump();
                });
            });
        }
    }
}
