using System;

namespace BiliClassic.Api
{
    public static class HistoryService
    {
        private const string ReportUrl = "https://api.bilibili.com/x/v2/history/report";

        public static void Report(string aid, string cid, int seconds)
        {
            if (string.IsNullOrEmpty(aid) || string.IsNullOrEmpty(cid))
            {
                return;
            }

            string csrf = BiliSession.Csrf ?? "";
            if (csrf.Length == 0)
            {
                return;
            }

            if (seconds < 0)
            {
                seconds = 0;
            }

            string form = "aid=" + aid + "&cid=" + cid
                + "&progress=" + seconds + "&csrf=" + csrf;

            Http.PostForm(ReportUrl, form, Http.Referer, delegate(HttpResult http)
            {
            });
        }
    }
}
