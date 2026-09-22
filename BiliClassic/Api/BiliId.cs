using System;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class BiliId
    {
        private const string Table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF";

        private static readonly int[] Positions = new int[] { 11, 10, 3, 8, 4, 6 };

        private const long Xor = 177451812L;
        private const long Add = 8728348608L;

        private static readonly Regex AvRegex = new Regex(@"^[aA][vV](\d+)$");

        public static string ToBvid(string input)
        {
            if (string.IsNullOrEmpty(input))
            {
                return "";
            }

            string text = input.Trim();
            if (IsBvid(text))
            {
                return text;
            }

            Match av = AvRegex.Match(text);
            if (!av.Success)
            {
                return "";
            }

            long aid;
            return long.TryParse(av.Groups[1].Value, out aid) ? AidToBvid(aid) : "";
        }

        public static bool IsBvid(string text)
        {
            if (string.IsNullOrEmpty(text) || text.Length != 12)
            {
                return false;
            }
            if (text[0] != 'B' || text[1] != 'V')
            {
                return false;
            }

            for (int i = 2; i < 12; i++)
            {
                if (Table.IndexOf(text[i]) < 0)
                {
                    return false;
                }
            }
            return true;
        }

        public static long BvidToAid(string bvid)
        {
            if (!IsBvid(bvid))
            {
                return 0;
            }

            long x = 0;
            long pow58 = 1;
            for (int i = 0; i < 6; i++)
            {
                x += Table.IndexOf(bvid[Positions[i]]) * pow58;
                pow58 *= 58;
            }
            return (x - Add) ^ Xor;
        }

        public static string AidToBvid(long aid)
        {
            if (aid <= 0)
            {
                return "";
            }

            char[] chars = "BV1  4 1 7  ".ToCharArray();
            long x = (aid ^ Xor) + Add;
            long pow58 = 1;
            for (int i = 0; i < 6; i++)
            {
                chars[Positions[i]] = Table[(int)((x / pow58) % 58)];
                pow58 *= 58;
            }
            return new string(chars);
        }
    }
}
