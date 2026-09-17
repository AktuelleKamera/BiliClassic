using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;

namespace BiliClassic.Api
{
    /// <summary>
    /// 本地键值存储：IsolatedStorage里的"k=v"文本
    /// 文件名沿用SmsLoginService旧名
    /// 已存的buvid3/bili_device_id不会丢
    /// </summary>
    public static class LocalStore
    {
        private const string FileName = "biliclassic_local.txt";

        public static string Get(string key)
        {
            if (string.IsNullOrEmpty(key))
            {
                return "";
            }
            Dictionary<string, string> map = Load();
            string value;
            return map.TryGetValue(key, out value) ? (value ?? "") : "";
        }

        public static void Set(string key, string value)
        {
            if (string.IsNullOrEmpty(key))
            {
                return;
            }
            Dictionary<string, string> map = Load();
            map[key] = value ?? "";
            Save(map);
        }

        public static bool Has(string key)
        {
            return Get(key).Length > 0;
        }

        public static Dictionary<string, string> Load()
        {
            Dictionary<string, string> map = new Dictionary<string, string>();
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    if (!store.FileExists(FileName))
                    {
                        return map;
                    }
                    using (IsolatedStorageFileStream stream = store.OpenFile(FileName, FileMode.Open, FileAccess.Read))
                    {
                        using (StreamReader reader = new StreamReader(stream))
                        {
                            string line;
                            while ((line = reader.ReadLine()) != null)
                            {
                                int eq = line.IndexOf('=');
                                if (eq > 0)
                                {
                                    map[line.Substring(0, eq)] = line.Substring(eq + 1);
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
            return map;
        }

        public static void Save(Dictionary<string, string> map)
        {
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    using (IsolatedStorageFileStream stream = store.OpenFile(FileName, FileMode.Create, FileAccess.Write))
                    {
                        using (StreamWriter writer = new StreamWriter(stream))
                        {
                            foreach (KeyValuePair<string, string> pair in map)
                            {
                                writer.WriteLine(pair.Key + "=" + pair.Value);
                            }
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
        }
    }
}
