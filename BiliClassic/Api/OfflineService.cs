using System;
using System.Collections.Generic;

namespace BiliClassic.Api
{
    public static class OfflineService
    {
        public static void Cache(VideoDetail detail, VideoPart part,
                                 Action<long, long> onProgress, Action<bool, string> onDone)
        {
            try
            {
                if (detail == null || part == null || string.IsNullOrEmpty(part.Cid))
                {
                    SafeDone(onDone, false, "缺少分P信息");
                    return;
                }

                string id = IdFor(detail);
                if (string.IsNullOrEmpty(id))
                {
                    SafeDone(onDone, false, "缺少视频编号");
                    return;
                }

                string fileName = MediaCache.FileNameFor(id, part.Cid);

                CachedMedia existing = MediaCache.Existing(fileName);
                if (existing != null && existing.Size > 0)
                {
                    SaveEntry(detail, part, fileName, existing.Size);
                    SafeDone(onDone, true, "这个分P已经在本地了");
                    return;
                }

                PlayUrlService.Fetch(detail.Bvid, part.Cid, "html5", delegate(PlayUrl play)
                {
                    try
                    {
                        if (play == null || !play.Ok || play.Urls.Count == 0)
                        {
                            SafeDone(onDone, false,
                                play == null || string.IsNullOrEmpty(play.Error) ? "取播放地址失败" : play.Error);
                            return;
                        }

                        MediaCache.Download(play.Urls[0], fileName, onProgress, delegate(CachedMedia media)
                        {
                            try
                            {
                                if (media == null || !media.Ok)
                                {
                                    SafeDone(onDone, false,
                                        media == null || string.IsNullOrEmpty(media.Error) ? "下载失败" : media.Error);
                                    return;
                                }

                                SaveEntry(detail, part, fileName, media.Size);
                                SafeDone(onDone, true, "缓存完成 " + MediaCache.FormatSize(media.Size));
                            }
                            catch (Exception ex)
                            {
                                SafeDone(onDone, false, "缓存回调出错: " + ex.Message);
                            }
                        });
                    }
                    catch (Exception ex)
                    {
                        SafeDone(onDone, false, "缓存出错: " + ex.Message);
                    }
                });
            }
            catch (Exception ex)
            {
                SafeDone(onDone, false, "缓存出错: " + ex.Message);
            }
        }

        private static void SafeDone(Action<bool, string> onDone, bool ok, string message)
        {
            try
            {
                if (onDone != null)
                {
                    onDone(ok, message ?? "");
                }
            }
            catch (Exception)
            {
            }
        }

        public static void CacheAll(VideoDetail detail,
                                    Action<VideoPart, long, long> onProgress,
                                    Action<bool, string> onDone)
        {
            if (detail == null || detail.Parts.Count == 0)
            {
                onDone(false, "缺少分P信息");
                return;
            }
            CacheParts(detail, 0, onProgress, onDone);
        }

        private static void CacheParts(VideoDetail detail, int index,
                                       Action<VideoPart, long, long> onProgress,
                                       Action<bool, string> onDone)
        {
            try
            {
                if (index >= detail.Parts.Count)
                {
                    SafeDone(onDone, true, "缓存完成");
                    return;
                }

                VideoPart part = detail.Parts[index];
                int total = detail.Parts.Count;
                int page = index + 1;

                if (string.IsNullOrEmpty(part.Cid))
                {
                    SafeDone(onDone, false, "P" + page + " 没有 cid，已停止");
                    return;
                }

                string id = IdFor(detail);
                string fileName = MediaCache.FileNameFor(id, part.Cid);

                CachedMedia existing = MediaCache.Existing(fileName);
                if (existing != null && existing.Size > 0)
                {
                    SaveEntry(detail, part, fileName, existing.Size);
                    CacheParts(detail, index + 1, onProgress, onDone);
                    return;
                }

                PlayUrlService.Fetch(detail.Bvid, part.Cid, "html5", delegate(PlayUrl play)
                {
                    try
                    {
                        if (play == null || !play.Ok || play.Urls.Count == 0)
                        {
                            SafeDone(onDone, false, "P" + page + "/" + total + " 取地址失败："
                                + (play == null || string.IsNullOrEmpty(play.Error) ? "未知原因" : play.Error));
                            return;
                        }

                        MediaCache.Download(play.Urls[0], fileName,
                            delegate(long received, long length)
                            {
                                try
                                {
                                    if (onProgress != null)
                                    {
                                        onProgress(part, received, length);
                                    }
                                }
                                catch (Exception)
                                {
                                }
                            },
                            delegate(CachedMedia media)
                            {
                                try
                                {
                                    if (media == null || !media.Ok)
                                    {
                                        SafeDone(onDone, false, "P" + page + "/" + total + " 下载失败："
                                            + (media == null || string.IsNullOrEmpty(media.Error) ? "未知原因" : media.Error));
                                        return;
                                    }

                                    SaveEntry(detail, part, fileName, media.Size);
                                    CacheParts(detail, index + 1, onProgress, onDone);
                                }
                                catch (Exception ex)
                                {
                                    SafeDone(onDone, false, "P" + page + " 缓存回调出错: " + ex.Message);
                                }
                            });
                    }
                    catch (Exception ex)
                    {
                        SafeDone(onDone, false, "P" + page + " 缓存出错: " + ex.Message);
                    }
                });
            }
            catch (Exception ex)
            {
                SafeDone(onDone, false, "缓存出错: " + ex.Message);
            }
        }

        public static bool AreAllCached(VideoDetail detail)
        {
            if (detail == null || detail.Parts.Count == 0)
            {
                return false;
            }
            string id = IdFor(detail);
            if (string.IsNullOrEmpty(id))
            {
                return false;
            }
            for (int i = 0; i < detail.Parts.Count; i++)
            {
                VideoPart part = detail.Parts[i];
                if (string.IsNullOrEmpty(part.Cid))
                {
                    return false;
                }
                CachedMedia media = MediaCache.Existing(
                    MediaCache.FileNameFor(id, part.Cid));
                if (media == null || media.Size <= 0)
                {
                    return false;
                }
            }
            return true;
        }

        public static List<OfflineEntry> All()
        {
            return OfflineStore.All();
        }

        public static bool IsCached(string bvid, string cid)
        {
            if (string.IsNullOrEmpty(cid) || string.IsNullOrEmpty(bvid))
            {
                return false;
            }
            CachedMedia media = MediaCache.Existing(MediaCache.FileNameFor(bvid, cid));
            return media != null && media.Size > 0;
        }

        public static void Delete(OfflineEntry entry)
        {
            if (entry == null)
            {
                return;
            }
            MediaCache.Delete(entry.FileName);
            OfflineStore.Remove(entry);
        }

        public static long Clear()
        {
            long freed = MediaCache.Clear();
            OfflineStore.Clear();
            return freed;
        }

        private static void SaveEntry(VideoDetail detail, VideoPart part,
                                      string fileName, long size)
        {
            OfflineEntry entry = new OfflineEntry();
            entry.Bvid = detail.Bvid ?? "";
            entry.Cid = part.Cid ?? "";
            entry.Aid = detail.Aid ?? "";
            entry.Title = detail.Title ?? "";
            entry.PartTitle = part.Title ?? "";
            entry.Page = part.Page ?? "";
            entry.Author = detail.Author ?? "";
            entry.Pic = detail.Pic ?? "";
            entry.FileName = fileName;
            entry.Size = size;
            entry.AddedAt = (long)(DateTime.UtcNow
                - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;

            OfflineStore.Add(entry);
        }

        private static string IdFor(VideoDetail detail)
        {
            if (!string.IsNullOrEmpty(detail.Bvid))
            {
                return detail.Bvid;
            }
            return detail.Aid ?? "";
        }
    }
}
