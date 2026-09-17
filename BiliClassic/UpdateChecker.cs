using System;
using System.Windows;
using BiliClassic.Api;
using Microsoft.Phone.Tasks;

namespace BiliClassic
{
    /// <summary>
    /// 更新提示
    /// 只弹窗和跳浏览器，不下载不安装
    /// </summary>
    public static class UpdateChecker
    {
        /// <summary>
        /// 检查更新并提示
        /// quiet=true 时静默：没更新或失败都不弹窗，只在有新版时提示
        /// </summary>
        public static void Check(bool quiet, Action onDone)
        {
            UpdateService.Check(delegate(UpdateInfo info)
            {
                // 回调不在UI线程
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

            // 只跳浏览器：WP已不支持侧载，装包得用户自己来
            if (info.DownloadUrl.Length == 0)
            {
                return;
            }

            WebBrowserTask task = new WebBrowserTask();
            // WP7.0只有URL这个string属性，Uri是7.1才加的
            task.URL = info.DownloadUrl;
            task.Show();
        }
    }
}
