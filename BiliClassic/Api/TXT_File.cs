using System;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Ink;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using System.IO;
using System.IO.IsolatedStorage;

namespace BiliClassic.Api
{
    public class TXT_File
    {


        public static void Txt_File_Writer(string inf, string path)
        {
            try
            {
                using (IsolatedStorageFile storage = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    using (IsolatedStorageFileStream fileStream = new IsolatedStorageFileStream(path, FileMode.Create, storage))
                    {
                        using (StreamWriter writer = new StreamWriter(fileStream))
                        {
                            //System.Diagnostics.Debug.WriteLine("WRT" + inf + " " + path);
                            writer.Write(inf);
                        }
                    }
                }
            }
            catch
            {
            }
        }

        public static string Txt_File_Reader(string path)
        {
            try
            {
                using (IsolatedStorageFile storage = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    if (storage.FileExists(path))
                    {
                        using (IsolatedStorageFileStream fileStream = new IsolatedStorageFileStream(path, FileMode.Open, FileAccess.Read, storage))
                        {
                            using (StreamReader reader = new StreamReader(fileStream))
                            {
                                //System.Diagnostics.Debug.WriteLine("RED" + reader.ReadToEnd() + " " + path);
                                return reader.ReadToEnd();
                            }
                        }
                    }
                    else
                    {
                        return "";
                    }
                }
            }
            catch
            {
                return "";
            }
        }
    }
}
