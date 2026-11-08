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

namespace BiliClassic
{
    public class IsFirstLoad
    {
        public const string first_path = "loaded.txt";
        public static bool IsLoaded
        {
            get
            {
                return (Api.TXT_File.Txt_File_Reader(first_path) != "");
            }
            set
            {
                Api.TXT_File.Txt_File_Writer("Loaded", first_path);
            }
        }
    }
}
