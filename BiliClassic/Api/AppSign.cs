using System;
using System.Collections.Generic;
using System.Text;

namespace BiliClassic.Api
{
    public static class AppSign
    {
        public static string BuildBody(Dictionary<string, string> parameters,
                                       string appKey, string appSec)
        {
            parameters["appkey"] = appKey;
            parameters["ts"] = CurrentUnixSeconds().ToString();

            List<string> keys = new List<string>(parameters.Keys);
            keys.Sort(StringComparer.Ordinal);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.Count; i++)
            {
                if (sb.Length > 0)
                {
                    sb.Append('&');
                }
                sb.Append(FormEncode(keys[i])).Append('=').Append(FormEncode(parameters[keys[i]]));
            }

            parameters["sign"] = Md5Util.Hex(sb.ToString() + appSec);
            return FormBody(parameters);
        }

        private static string FormBody(Dictionary<string, string> parameters)
        {
            StringBuilder sb = new StringBuilder();
            foreach (KeyValuePair<string, string> pair in parameters)
            {
                if (sb.Length > 0)
                {
                    sb.Append('&');
                }
                sb.Append(FormEncode(pair.Key)).Append('=').Append(FormEncode(pair.Value));
            }
            return sb.ToString();
        }

        public static string FormEncode(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            for (int i = 0; i < bytes.Length; i++)
            {
                char c = (char)bytes[i];
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '*')
                {
                    sb.Append(c);
                }
                else if (c == ' ')
                {
                    sb.Append('+');
                }
                else
                {
                    sb.Append('%');
                    sb.Append(bytes[i].ToString("X2"));
                }
            }
            return sb.ToString();
        }

        private static long CurrentUnixSeconds()
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)DateTime.UtcNow.Subtract(epoch).TotalSeconds;
        }
    }
}
