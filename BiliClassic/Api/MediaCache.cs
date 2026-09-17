using System;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;

namespace BiliClassic.Api
{
    /// <summary>媒体下载结果</summary>
    public sealed class CachedMedia
    {
        /// <summary>隔离存储文件名</summary>
        public string FileName = "";

        /// <summary>字节数</summary>
        public long Size;

        /// <summary>失败原因，成功为空</summary>
        public string Error = "";

        public bool Ok
        {
            get { return Error.Length == 0; }
        }
    }

    /// <summary>离线播放缓存</summary>
    public static class MediaCache
    {
        /// <summary>离线缓存的文件名前缀</summary>
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

        /// <summary>离线缓存文件名</summary>
        public static string FileNameFor(string bvid, string cid)
        {
            return Prefix + bvid + "_" + cid + ".mp4";
        }

        /// <summary>本地是否已有这个视频</summary>
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

        /// <summary>下载并覆盖缓存文件</summary>
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

                // 视频十几MB，不能整包缓冲进内存
                // 关掉缓冲后响应流只能走BeginRead
                state.Request.AllowReadStreamBuffering = false;
                state.Request.UserAgent = Http.UserAgent;

                TrySetHeader(state.Request, "Referer", Http.Referer);
                TrySetHeader(state.Request, "Origin", Http.Origin);

                // Cookie是受限头只能靠容器，不挂就一个都不发
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

        /// <summary>打开缓存文件交给MediaElement，换视频时调用方负责关</summary>
        public static IsolatedStorageFileStream Open(string fileName)
        {
            IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication();
            return store.OpenFile(fileName, FileMode.Open, FileAccess.Read);
        }

        /// <summary>
        /// 删掉所有离线缓存，返回释放字节数
        /// 缓存按视频存且播完不删，不清理隔离存储迟早填满
        /// </summary>
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

        /// <summary>删掉某个缓存文件</summary>
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

        /// <summary>字节数转可读字符串</summary>
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
                state.OnDone(Fail("HTTP 失败 [" + wex.Status + "]: " + wex.Message, state.FileName));
                return;
            }
            catch (Exception ex)
            {
                state.OnDone(Fail("请求失败: " + ex.Message, state.FileName));
                return;
            }

            // 分块传输时ContentLength是-1，进度只能显示已下载量
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
                state.OnDone(Fail("创建缓存文件失败: " + ex.Message, state.FileName));
                return;
            }

            try
            {
                state.Source = state.Response.GetResponseStream();
                state.Source.BeginRead(state.Buffer, 0, state.Buffer.Length, OnRead, state);
            }
            catch (Exception ex)
            {
                state.OnDone(Fail("读取响应流失败: " + ex.Message, state.FileName));
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
                state.OnDone(Fail("下载中断: " + ex.Message, state.FileName));
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
                state.OnDone(Fail("写缓存文件失败: " + ex.Message, state.FileName));
                return;
            }

            state.Received += count;

            if (state.OnProgress != null)
            {
                state.OnProgress(state.Received, state.Total);
            }

            try
            {
                state.Source.BeginRead(state.Buffer, 0, state.Buffer.Length, OnRead, state);
            }
            catch (Exception ex)
            {
                state.OnDone(Fail("继续下载失败: " + ex.Message, state.FileName));
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

            // 200也可能返回空body，这种当失败
            if (state.Received <= 0)
            {
                media.Error = "服务器返回了空内容";
            }

            state.OnDone(media);
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

            // query里未转义的[]会让Uri拒收，编码后服务端照样认
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
