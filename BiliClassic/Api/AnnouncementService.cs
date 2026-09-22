using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class Announcement
    {
        public string Id = "";
        public string Title = "";
        public string Content = "";
        public string StartDate = "";
        public string EndDate = "";
        public string Url = "";
        public bool ShowOnce = true;
        public string ButtonText = "知道了";
        public bool ForceShow;
        public int Priority = 100;
        public string MinVersion = "";
        public string MaxVersion = "";
        public int MinVersionCode = -1;
        public int MaxVersionCode = -1;
        public List<string> Platforms = new List<string>();
        public string VideoUrl = "";
        public string VideoText = "";
        public string AudioUrl = "";
        public string AudioText = "";
        public string AudioDisText = "";
    }

    public static class AnnouncementService
    {
        private const string Url = "http://www.biliclassic.cn/api/announcement.json";

        private const string ShownPrefix = "shown_";

        private const string DismissedPrefix = "dismissed_";

        private const string WpPlatform = "wp";

        public static void Fetch(Action<List<Announcement>> onDone)
        {
            Http.GetText(Url, Http.Referer, delegate(HttpResult http)
            {
                List<Announcement> picked = new List<Announcement>();
                try
                {
                    if (http != null && !string.IsNullOrEmpty(http.Body))
                    {
                        picked = Pick(Parse(http.Body));
                    }
                }
                catch (Exception)
                {
                }
                onDone(picked);
            });
        }

        public static void MarkShown(string id)
        {
            if (!string.IsNullOrEmpty(id))
            {
                LocalStore.Set(ShownPrefix + id, "1");
            }
        }

        public static void MarkDismissed(string id)
        {
            if (!string.IsNullOrEmpty(id))
            {
                LocalStore.Set(DismissedPrefix + id, "1");
            }
        }

        public static int CompareVersion(string v1, string v2)
        {
            if (v1 == null || v2 == null)
            {
                return 0;
            }

            string[] parts1 = v1.Split('.');
            string[] parts2 = v2.Split('.');
            int len = Math.Max(parts1.Length, parts2.Length);
            for (int i = 0; i < len; i++)
            {
                int n1 = i < parts1.Length ? ParseInt(parts1[i]) : 0;
                int n2 = i < parts2.Length ? ParseInt(parts2[i]) : 0;
                if (n1 != n2)
                {
                    return n1 - n2;
                }
            }
            return 0;
        }

        private static int ParseInt(string text)
        {
            int value;
            return int.TryParse(text, out value) ? value : 0;
        }

        private static List<Announcement> Parse(string body)
        {
            List<Announcement> list = new List<Announcement>();
            Match array = Regex.Match(body,
                @"""announcements""\s*:\s*\[(.*)\]", RegexOptions.Singleline);
            if (!array.Success)
            {
                return list;
            }

            MatchCollection objects = Regex.Matches(array.Groups[1].Value,
                @"\{[^{}]*\}", RegexOptions.Singleline);
            for (int i = 0; i < objects.Count; i++)
            {
                Announcement a = ParseOne(objects[i].Value);
                if (a != null && a.Id.Length > 0)
                {
                    list.Add(a);
                }
            }
            return list;
        }

        private static Announcement ParseOne(string json)
        {
            if (Regex.IsMatch(json, @"""enabled""\s*:\s*false"))
            {
                return null;
            }

            Announcement a = new Announcement();
            a.Id = JsonText.ReadString(json, "id");
            a.Title = JsonText.ReadString(json, "title");
            a.Content = JsonText.ReadString(json, "content");
            a.StartDate = JsonText.ReadString(json, "start_date");
            a.EndDate = JsonText.ReadString(json, "end_date");
            a.Url = JsonText.ReadString(json, "url");
            a.ButtonText = JsonText.ReadString(json, "button_text");
            a.MinVersion = JsonText.ReadString(json, "min_version");
            a.MaxVersion = JsonText.ReadString(json, "max_version");
            a.VideoUrl = JsonText.ReadString(json, "video_url");
            a.VideoText = JsonText.ReadString(json, "video_text");
            a.AudioUrl = JsonText.ReadString(json, "audio_url");
            a.AudioText = JsonText.ReadString(json, "audio_text");
            a.AudioDisText = JsonText.ReadString(json, "audio_dis_text");
            a.ShowOnce = !Regex.IsMatch(json, @"""show_once""\s*:\s*false");
            a.ForceShow = Regex.IsMatch(json, @"""force_show""\s*:\s*true");
            a.MinVersionCode = (int)ReadNumber(json, "min_version_code", -1);
            a.MaxVersionCode = (int)ReadNumber(json, "max_version_code", -1);
            a.Priority = (int)ReadNumber(json, "priority", 100);
            a.Platforms = ReadPlatforms(json);

            if (a.Title.Length == 0)
            {
                a.Title = "公告";
            }
            if (a.ButtonText.Length == 0)
            {
                a.ButtonText = "知道了";
            }
            return a;
        }

        private static List<Announcement> Pick(List<Announcement> all)
        {
            List<Announcement> list = new List<Announcement>();
            DateTime today = DateTime.Now.Date;
            int versionCode = UpdateService.VersionCode;
            string versionName = UpdateService.DisplayVersion;

            for (int i = 0; i < all.Count; i++)
            {
                Announcement a = all[i];
                if (!PlatformOk(a))
                {
                    continue;
                }
                if (!DateOk(a, today))
                {
                    continue;
                }
                if (!VersionOk(a, versionCode, versionName))
                {
                    continue;
                }
                if (a.ShowOnce && LocalStore.Has(ShownPrefix + a.Id))
                {
                    continue;
                }
                if (LocalStore.Has(DismissedPrefix + a.Id))
                {
                    continue;
                }
                list.Add(a);
            }

            list.Sort(delegate(Announcement x, Announcement y)
            {
                return x.Priority.CompareTo(y.Priority);
            });
            return list;
        }

        private static bool PlatformOk(Announcement a)
        {
            if (a.Platforms.Count == 0)
            {
                return true;
            }
            return a.Platforms.Contains(WpPlatform);
        }

        private static bool DateOk(Announcement a, DateTime today)
        {
            if (a.StartDate.Length == 0 || a.EndDate.Length == 0)
            {
                return true;
            }

            DateTime start;
            DateTime end;
            if (!TryDate(a.StartDate, out start) || !TryDate(a.EndDate, out end))
            {
                return true;
            }
            return today >= start && today <= end;
        }

        private static bool VersionOk(Announcement a, int versionCode, string versionName)
        {
            if (a.MinVersionCode >= 0)
            {
                if (versionCode < a.MinVersionCode)
                {
                    return false;
                }
            }
            else if (a.MinVersion.Length > 0
                && CompareVersion(versionName, a.MinVersion) < 0)
            {
                return false;
            }

            if (a.MaxVersionCode >= 0)
            {
                if (versionCode > a.MaxVersionCode)
                {
                    return false;
                }
            }
            else if (a.MaxVersion.Length > 0
                && CompareVersion(versionName, a.MaxVersion) > 0)
            {
                return false;
            }
            return true;
        }

        private static bool TryDate(string text, out DateTime value)
        {
            if (DateTime.TryParseExact(text, "yyyy-MM-dd",
                    CultureInfo.InvariantCulture, DateTimeStyles.None, out value))
            {
                return true;
            }
            return DateTime.TryParse(text, out value);
        }

        private static List<string> ReadPlatforms(string json)
        {
            List<string> list = new List<string>();
            Match m = Regex.Match(json, @"""platform""\s*:\s*\[([^\]]*)\]");
            if (!m.Success)
            {
                return list;
            }

            foreach (Match item in Regex.Matches(m.Groups[1].Value, @"""([^""]*)"""))
            {
                list.Add(item.Groups[1].Value.ToLowerInvariant());
            }
            return list;
        }

        private static long ReadNumber(string json, string field, long fallback)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            if (!m.Success)
            {
                return fallback;
            }

            long value;
            return long.TryParse(m.Groups[1].Value, out value) ? value : fallback;
        }
    }
}
