using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Text;

namespace BiliClassic.Api
{
    public sealed class OfflineEntry
    {
        public string Bvid = "";
        public string Cid = "";
        public string Aid = "";
        public string Title = "";
        public string PartTitle = "";
        public string Page = "";
        public string Author = "";
        public string Pic = "";

        public string FileName = "";

        public long Size;

        public long AddedAt;

        public string Key
        {
            get { return KeyFor(Bvid, Cid, Aid); }
        }

        public static string KeyFor(string bvid, string cid, string aid)
        {
            string id = string.IsNullOrEmpty(bvid) ? (aid ?? "") : bvid;
            return id + "_" + (cid ?? "");
        }
    }

    public static class OfflineStore
    {
        private const string FileName = "biliclassic_offline.txt";
        private const char Separator = '\t';

        private static readonly object Gate = new object();

        public static List<OfflineEntry> All()
        {
            lock (Gate)
            {
                return LoadUnlocked();
            }
        }

        public static void Add(OfflineEntry entry)
        {
            if (entry == null || string.IsNullOrEmpty(entry.FileName))
            {
                return;
            }

            lock (Gate)
            {
                List<OfflineEntry> list = LoadUnlocked();
                RemoveByKey(list, entry.Key);
                list.Insert(0, entry);
                SaveUnlocked(list);
            }
        }

        public static void Remove(OfflineEntry entry)
        {
            if (entry == null)
            {
                return;
            }

            lock (Gate)
            {
                List<OfflineEntry> list = LoadUnlocked();
                RemoveByKey(list, entry.Key);
                SaveUnlocked(list);
            }
        }

        public static void Clear()
        {
            lock (Gate)
            {
                SaveUnlocked(new List<OfflineEntry>());
            }
        }

        private static void RemoveByKey(List<OfflineEntry> list, string key)
        {
            for (int i = list.Count - 1; i >= 0; i--)
            {
                if (list[i].Key == key)
                {
                    list.RemoveAt(i);
                }
            }
        }

        private static List<OfflineEntry> LoadUnlocked()
        {
            List<OfflineEntry> list = new List<OfflineEntry>();
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    if (!store.FileExists(FileName))
                    {
                        return list;
                    }
                    using (IsolatedStorageFileStream stream =
                        store.OpenFile(FileName, FileMode.Open, FileAccess.Read))
                    {
                        using (StreamReader reader = new StreamReader(stream))
                        {
                            string line;
                            while ((line = reader.ReadLine()) != null)
                            {
                                OfflineEntry entry = Parse(line);
                                if (entry != null)
                                {
                                    list.Add(entry);
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
            return list;
        }

        private static void SaveUnlocked(List<OfflineEntry> list)
        {
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    using (IsolatedStorageFileStream stream =
                        store.OpenFile(FileName, FileMode.Create, FileAccess.Write))
                    {
                        using (StreamWriter writer = new StreamWriter(stream))
                        {
                            for (int i = 0; i < list.Count; i++)
                            {
                                writer.WriteLine(ToLine(list[i]));
                            }
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
        }

        private static string ToLine(OfflineEntry e)
        {
            StringBuilder sb = new StringBuilder();
            sb.Append(Clean(e.Bvid)).Append(Separator);
            sb.Append(Clean(e.Cid)).Append(Separator);
            sb.Append(Clean(e.Aid)).Append(Separator);
            sb.Append(Clean(e.Title)).Append(Separator);
            sb.Append(Clean(e.PartTitle)).Append(Separator);
            sb.Append(Clean(e.Page)).Append(Separator);
            sb.Append(Clean(e.Author)).Append(Separator);
            sb.Append(Clean(e.Pic)).Append(Separator);
            sb.Append(Clean(e.FileName)).Append(Separator);
            sb.Append(e.Size).Append(Separator);
            sb.Append(e.AddedAt);
            return sb.ToString();
        }

        private static OfflineEntry Parse(string line)
        {
            if (string.IsNullOrEmpty(line))
            {
                return null;
            }

            string[] parts = line.Split(new char[] { Separator });
            if (parts.Length < 11)
            {
                return null;
            }

            OfflineEntry e = new OfflineEntry();
            e.Bvid = parts[0];
            e.Cid = parts[1];
            e.Aid = parts[2];
            e.Title = parts[3];
            e.PartTitle = parts[4];
            e.Page = parts[5];
            e.Author = parts[6];
            e.Pic = parts[7];
            e.FileName = parts[8];

            long size;
            if (long.TryParse(parts[9], out size))
            {
                e.Size = size;
            }
            long addedAt;
            if (long.TryParse(parts[10], out addedAt))
            {
                e.AddedAt = addedAt;
            }

            return string.IsNullOrEmpty(e.FileName) ? null : e;
        }

        private static string Clean(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "";
            }
            return value.Replace('\t', ' ').Replace('\r', ' ').Replace('\n', ' ');
        }
    }
}
