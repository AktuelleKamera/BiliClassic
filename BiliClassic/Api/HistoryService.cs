using System;

namespace BiliClassic.Api
{
    /// <summary>
    /// 播放历史上报
    /// 安卓版同接口，progress是秒，未登录不发
    /// </summary>
    public static class HistoryService
    {
        private const string ReportUrl = "https://api.bilibili.com/x/v2/history/report";

        /// <summary>上报进度，失败静默：历史不重要，不能影响播放</summary>
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
                // 静默：成功失败都不提示
            });
        }
    }
}
