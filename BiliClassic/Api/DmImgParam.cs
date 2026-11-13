using System;
using System.Collections.Generic;
using System.Text;

namespace BiliClassic.Api
{
    /// <summary>动态接口的 dm_img_* 风控参数</summary>
    public static class DmImgParam
    {
        private const string ImgStr =
            "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ";

        private const string CoverImgStr =
            "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwIERpcmVjdDNEMTEgdnNfNV8wIHBzXzVfMCwgRDNEMTEpR29vZ2xlIEluYy4gKEludGVsKQ";

        private static readonly int[] Timestamps = new int[]
        {
            2943, 3046, 3152, 3252, 3354, 3454, 3558, 3665,
            3767, 5566, 5676, 5778, 5881, 7296, 7573, 135289
        };

        private static readonly string[] ClassNames = new string[]
        {
            "clearfix", "g-search", "section", "loading", "full-rows",
            "n-btn", "router-link-exact-active", "router-link-active", "active"
        };

        public static void AddTo(Dictionary<string, string> parameters)
        {
            if (parameters == null)
            {
                return;
            }

            parameters["dm_img_str"] = ImgStr;
            parameters["dm_cover_img_str"] = CoverImgStr;
            parameters["dm_img_list"] = BuildImgList();
            parameters["dm_img_inter"] = BuildInter();
        }

        private static string BuildImgList()
        {
            Random random = new Random();
            StringBuilder sb = new StringBuilder();
            sb.Append('[');

            sb.Append("{\"x\":2355,\"y\":725,\"z\":0,\"timestamp\":2145,\"k\":")
              .Append(random.Next(67) + 60)
              .Append(",\"type\":0}");

            int num = random.Next(Timestamps.Length - 6) + 6;
            for (int i = 0; i <= num; i++)
            {
                sb.Append(',');
                AppendImgItem(sb, random.Next(200), random.Next(200), i + 1,
                    Timestamps[i], i == num ? 1 : 0, random);
            }

            sb.Append(']');
            return sb.ToString();
        }

        private static void AppendImgItem(StringBuilder sb, int x, int y, int index,
                                          int timestamp, int type, Random random)
        {
            int[] xyz = F114i(x, y, index, random);
            sb.Append("{\"x\":").Append(xyz[0])
              .Append(",\"y\":").Append(xyz[1])
              .Append(",\"z\":").Append(xyz[2])
              .Append(",\"timestamp\":").Append(timestamp)
              .Append(",\"k\":").Append(random.Next(67) + 60)
              .Append(",\"type\":").Append(type)
              .Append('}');
        }

        private static string BuildInter()
        {
            Random random = new Random();
            int y = RandomInRange(random, -1500, -300);
            int x = RandomInRange(random, 100, 700);
            int width = RandomInRange(random, 100, 8000);
            int height = RandomInRange(random, 20, 500);

            int[] p = F114(y, x, random);
            int[] s = F514(width, height, random);

            string c1 = ClassNames[random.Next(ClassNames.Length)];
            string c2 = ClassNames[random.Next(ClassNames.Length)];
            string c = Convert.ToBase64String(Encoding.UTF8.GetBytes(c1 + " " + c2));

            int[] wh = F114(width, height, random);
            int[] of = F514(y, x, random);

            StringBuilder sb = new StringBuilder();
            sb.Append("{\"ds\":[{\"t\":").Append(random.Next(6))
              .Append(",\"c\":\"").Append(c)
              .Append("\",\"p\":[").Append(p[0]).Append(',').Append(p[2]).Append(',').Append(p[1])
              .Append("],\"s\":[").Append(s[2]).Append(',').Append(s[0]).Append(',').Append(s[1])
              .Append("]}],\"wh\":[").Append(wh[0]).Append(',').Append(wh[1]).Append(',').Append(wh[2])
              .Append("],\"of\":[").Append(of[0]).Append(',').Append(of[1]).Append(',').Append(of[2])
              .Append("]}");
            return sb.ToString();
        }

        private static int[] F114i(int a, int b, int i, Random random)
        {
            int t = (int)(random.NextDouble() * (114 * i));
            return new int[] { 3 * a + 2 * b + t, 4 * a - 5 * b + t, t };
        }

        private static int[] F114(int a, int b, Random random)
        {
            int t = (int)(random.NextDouble() * 114);
            return new int[] { 2 * a + 2 * b + 3 * t, 4 * a - b + t, t };
        }

        private static int[] F514(int a, int b, Random random)
        {
            int t = (int)(random.NextDouble() * 514);
            return new int[] { 3 * a + 2 * b + t, 4 * a - 4 * b + 2 * t, t };
        }

        private static int RandomInRange(Random random, int min, int max)
        {
            return random.Next(max - min + 1) + min;
        }
    }
}
