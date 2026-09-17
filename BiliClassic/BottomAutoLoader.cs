using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace BiliClassic
{
    /// <summary>
    /// 列表滚到底部自动加载下一页
    /// </summary>
    public static class BottomAutoLoader
    {
        private const double Threshold = 120;

        /// <summary>监听触底，触发onBottom</summary>
        public static void Attach(ListBox list, Action onBottom)
        {
            if (list == null || onBottom == null)
            {
                return;
            }

            // 判据不依赖ExtentHeight：虚拟化时它只按已实现条目算
            double lastOffset = -1;
            DateTime lastAt = DateTime.MinValue;

            list.Loaded += delegate
            {
                ScrollViewer viewer = FindChild<ScrollViewer>(list);
                if (viewer == null)
                {
                    return;
                }

                viewer.LayoutUpdated += delegate
                {
                    Check(list, viewer, onBottom, ref lastOffset, ref lastAt);
                };
            };
        }

        private static void Check(ListBox list, ScrollViewer viewer, Action onBottom,
                                  ref double lastOffset, ref DateTime lastAt)
        {
            double offset = viewer.VerticalOffset;

            // 位移必须递增
            if (offset <= lastOffset)
            {
                return;
            }

            int count = list.Items.Count;
            if (count == 0)
            {
                return;
            }

            // 末条已被虚拟化面板生成
            ListBoxItem last = list.ItemContainerGenerator.ContainerFromIndex(count - 1)
                as ListBoxItem;
            if (last == null)
            {
                return;
            }

            // 量末条的实际Y坐标
            double y;
            try
            {
                y = last.TransformToVisual(list).Transform(new Point(0, 0)).Y;
            }
            catch (Exception)
            {
                return;
            }

            if (y > list.ActualHeight + Threshold)
            {
                return;
            }

            // 冷却800毫秒
            if ((DateTime.Now - lastAt).TotalMilliseconds < 800)
            {
                return;
            }

            lastOffset = offset;
            lastAt = DateTime.Now;
            onBottom();
        }

        private static T FindChild<T>(DependencyObject parent) where T : DependencyObject
        {
            int count = VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                DependencyObject child = VisualTreeHelper.GetChild(parent, i);

                T typed = child as T;
                if (typed != null)
                {
                    return typed;
                }

                T nested = FindChild<T>(child);
                if (nested != null)
                {
                    return nested;
                }
            }
            return null;
        }
    }
}
