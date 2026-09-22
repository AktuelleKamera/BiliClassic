using System;
using System.Windows;
using BiliClassic.Api;
using Microsoft.Phone.Tasks;

namespace BiliClassic
{
    public static class UpdateChecker
    {
        public static void Check(bool quiet, Action onDone)
        {
            UpdateService.Check(delegate(UpdateInfo info)
            {
                Deployment.Current.Dispatcher.BeginInvoke(delegate
                {
                    try
                    {
                        if (info.HasUpdate)
                        {
                            ShowUpdate(info);
                        }
                        else if (!quiet)
                        {
                            MessageBox.Show(DescribeNoUpdate(info), "检查更新",
                                MessageBoxButton.OK);
                        }
                    }
                    finally
                    {
                        if (onDone != null)
                        {
                            onDone();
                        }
                    }
                });
            });
        }

        private static string DescribeNoUpdate(UpdateInfo info)
        {
            if (info.Error.Length > 0)
            {
                return "检查更新失败：" + info.Error;
            }
            return "当前已是最新版本 " + UpdateService.DisplayVersion;
        }

        private static void ShowUpdate(UpdateInfo info)
        {
            string text = "发现新版本 " + info.Latest;
            if (info.Changelog.Length > 0)
            {
                text += "\n\n" + info.Changelog;
            }
            text += "\n\n点确定打开下载页";

            if (MessageBox.Show(text, "哔哩经典 更新", MessageBoxButton.OKCancel)
                != MessageBoxResult.OK)
            {
                return;
            }

            if (info.DownloadUrl.Length == 0)
            {
                return;
            }

            WebBrowserTask task = new WebBrowserTask();
#if WP8
            task.Uri = new Uri(info.DownloadUrl);
#else
            task.URL = info.DownloadUrl;
#endif
            task.Show();
        }
    }
}
