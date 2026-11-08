using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using Microsoft.Phone.Controls;
using System.IO;
using System.IO.IsolatedStorage;
using System.ComponentModel;

namespace BiliClassic.Api
{
    public class DynamicsMessegeTemp
    {
        public const string dyiPath = "dyitemp.txt";
        public const string msiPath = "msitemp.txt";
        public const string dynPath = "dyntemp.txt";
        public const string msnPath = "msntemp.txt";
        private static string dyi = "";
        private static string msi = "";
        private static int dyn = -1;
        private static int msn = -1;
        public static string DYI
        {
            get
            {
                if (dyi == "")
                {
                    dyi = TXT_File.Txt_File_Reader(dyiPath);
                }
                return dyi;
            }
            set
            {
                dyi = value;
                TXT_File.Txt_File_Writer(dyi, dyiPath);
            }
        }
        public static int DYN
        {
            get
            {
                try
                {
                    if (dyn == -1)
                    {
                        dyn = int.Parse(TXT_File.Txt_File_Reader(dynPath));
                    }
                }
                catch
                {
                    dyn = 0;
                    TXT_File.Txt_File_Writer(0 + "", dynPath);
                }
                return dyn;
            }
            set
            {
                dyn = value;
                TXT_File.Txt_File_Writer(dyn + "", dynPath);
            }
        }
        //私信
        public static string MSI
        {
            get
            {
                if (msi == "")
                {
                    msi = TXT_File.Txt_File_Reader(msiPath);
                }
                return msi;
            }
            set
            {
                msi = value;
                TXT_File.Txt_File_Writer(msi, msiPath);
            }
        }
        public static int MSN
        {
            get
            {
                try
                {
                    if (msn == -1)
                    {
                        msn = int.Parse(TXT_File.Txt_File_Reader(msnPath));
                    }
                }
                catch
                {
                    msn = 0;
                    TXT_File.Txt_File_Writer(0 + "", msnPath);
                }
                return msn;
            }
            set
            {
                msn = value;
                TXT_File.Txt_File_Writer(msn + "", msnPath);
            }
        }
    }
}
