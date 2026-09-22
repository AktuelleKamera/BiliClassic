using System;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;

namespace BiliClassic.Api
{
    public sealed class CachedMedia
    {
        public string FileName = "";

        public long Size;

        public string Error = "";

        public bool Ok
        {
            get { return Error.Length == 0; }
        }
    }

    public static class MediaCache
    {
        private const string Prefix = "media_";

        private const int BufferSize = 16384;

        private sealed class State
        {
            public HttpWebRequest Request;
            public HttpWebResponse Response;
            public Stream Source;
            public IsolatedStorageFile Store;
            public IsolatedStorageFileStream Target;
            public byte[] Buffer = new byte[BufferSize];
            public string FileName;
            public long Total;
            public long Received;
            public Action<long, long> OnProgress;
            public Action<CachedMedia> OnDone;
        }

        public static string FileNameFor(string bvid, string cid)
        {
            return Prefix + bvid + "_" + cid + ".mp4";
        }

        public static CachedMedia Existing(string fileName)
        {
            try
            {
                IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
                if (!store.FileExists(fileName))
                {
                    return null;
                }
                using (IsolatedStorageFileStream stream =
                    store.OpenFile(fileName, FileMode.Open, FileAccess.Read))
                {
                    CachedMedia media = new CachedMedia();
                    media.FileName = fileName;
                    media.Size = stream.Length;
                    return media;
                }
            }
            catch (Exception)
            {
                return null;
            }
        }

        public static void Download(string url, string fileName,
                                    Action<long, long> onProgress, Action<CachedMedia> onDone)
        {
            if (string.IsNullOrEmpty(url))
            {
                onDone(Fail("URL 为空", fileName));
                return;
            }

            Uri uri;
            if (!TryCreateUri(url, out uri))
            {
                onDone(Fail("地址不合法", fileName));
                return;
            }

            State state = new State();
            state.FileName = fileName;
            state.OnProgress = onProgress;
            state.OnDone = onDone;

            try
            {
                state.Request = (HttpWebRequest)WebRequest.Create(uri);
                state.Request.Method = "GET";

                state.Request.AllowReadStreamBuffering = false;
                state.Request.UserAgent = Http.UserAgent;

                TrySetHeader(state.Request, "Referer", Http.Referer);
                TrySetHeader(state.Request, "Origin", Http.Origin);

                try
                {
                    state.Request.CookieContainer = BiliSession.GetContainer();
                }
                catch (Exception)
                {
                }
            }
            catch (Exception ex)
            {
                onDone(Fail("创建请求失败: " + ex.Message, fileName));
                return;
            }

            state.Request.BeginGetResponse(OnResponse, state);
        }

        public static IsolatedStorageFileStream Open(string fileName)
        {
            IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
            return store.OpenFile(fileName, FileMode.Open, FileAccess.Read);
        }

        public static long Clear()
        {
            long freed = 0;
            try
            {
                IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
                string[] files = store.GetFileNames(Prefix + "*.mp4");
                for (int i = 0; i < files.Length; i++)
                {
                    try
                    {
                        using (IsolatedStorageFileStream stream =
                            store.OpenFile(files[i], FileMode.Open, FileAccess.Read))
                        {
                            freed += stream.Length;
                        }
                        store.DeleteFile(files[i]);
                    }
                    catch (Exception)
                    {
                    }
                }
            }
            catch (Exception)
            {
            }
            return freed;
        }

        public static void Delete(string fileName)
        {
            try
            {
                IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
                if (store.FileExists(fileName))
                {
                    store.DeleteFile(fileName);
                }
            }
            catch (Exception)
            {
            }
        }

        public static string FormatSize(long bytes)
        {
            if (bytes >= 1024L * 1024L)
            {
                return (bytes / 1024.0 / 1024.0).ToString("0.0") + " MB";
            }
            if (bytes >= 1024L)
            {
                return (bytes / 1024.0).ToString("0") + " KB";
            }
            return bytes + " B";
        }

        private static void OnResponse(IAsyncResult ar)
        {
            State state = (State)ar.AsyncState;

            try
            {
                state.Response = (HttpWebResponse)state.Request.EndGetResponse(ar);
            }
            catch (WebException wex)
            {
                SafeDone(state, Fail("HTTP 失败 [" + wex.Status + "]: " + wex.Message, state.FileName));
                return;
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("请求失败: " + ex.Message, state.FileName));
                return;
            }

            state.Total = state.Response.ContentLength;

            try
            {
                state.Store = IsolatedStorageFile.GetUserStoreForApplication();
                if (state.Store.FileExists(state.FileName))
                {
                    state.Store.DeleteFile(state.FileName);
                }
                state.Target = state.Store.CreateFile(state.FileName);
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("创建缓存文件失败: " + ex.Message, state.FileName));
                return;
            }

            try
            {
                state.Source = state.Response.GetResponseStream();
                state.Source.BeginRead(state.Buffer, 0, state.Buffer.Length, OnRead, state);
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("读取响应流失败: " + ex.Message, state.FileName));
            }
        }

        private static void OnRead(IAsyncResult ar)
        {
            State state = (State)ar.AsyncState;

            int count;
            try
            {
                count = state.Source.EndRead(ar);
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("下载中断: " + ex.Message, state.FileName));
                return;
            }

            if (count <= 0)
            {
                Finish(state);
                return;
            }

            try
            {
                state.Target.Write(state.Buffer, 0, count);
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("写缓存文件失败: " + ex.Message, state.FileName));
                return;
            }

            state.Received += count;

            if (state.OnProgress != null)
            {
                SafeProgress(state);
            }

            try
            {
                state.Source.BeginRead(state.Buffer, 0, state.Buffer.Length, OnRead, state);
            }
            catch (Exception ex)
            {
                SafeDone(state, Fail("继续下载失败: " + ex.Message, state.FileName));
            }
        }

        private static void Finish(State state)
        {
            try
            {
                if (state.Target != null)
                {
                    state.Target.Close();
                    state.Target = null;
                }
                if (state.Source != null)
                {
                    state.Source.Close();
                    state.Source = null;
                }
                if (state.Response != null)
                {
                    state.Response.Close();
                    state.Response = null;
                }
            }
            catch (Exception)
            {
            }

            CachedMedia media = new CachedMedia();
            media.FileName = state.FileName;
            media.Size = state.Received;

            if (state.Received <= 0)
            {
                media.Error = "服务器返回了空内容";
            }

            SafeDone(state, media);
        }

        private static void SafeDone(State state, CachedMedia media)
        {
            try
            {
                if (state.OnDone != null)
                {
                    state.OnDone(media);
                }
            }
            catch (Exception)
            {
            }
        }

        private static void SafeProgress(State state)
        {
            try
            {
                if (state.OnProgress != null)
                {
                    state.OnProgress(state.Received, state.Total);
                }
            }
            catch (Exception)
            {
            }
        }

        private static bool TryCreateUri(string url, out Uri uri)
        {
            try
            {
                uri = new Uri(url, UriKind.Absolute);
                return true;
            }
            catch (Exception)
            {
            }

            try
            {
                uri = new Uri(url.Replace("[", "%5B").Replace("]", "%5D"), UriKind.Absolute);
                return true;
            }
            catch (Exception)
            {
                uri = null;
                return false;
            }
        }

        private static void TrySetHeader(HttpWebRequest request, string name, string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return;
            }
            try
            {
                request.Headers[name] = value;
            }
            catch (Exception)
            {
            }
        }

        private static CachedMedia Fail(string error, string fileName)
        {
            CachedMedia media = new CachedMedia();
            media.FileName = fileName;
            media.Error = error;
            return media;
        }
    }
}
