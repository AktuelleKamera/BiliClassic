using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace BiliClassic
{
    public static class BottomAutoLoader
    {
        private const double Threshold = 120;

        public static void Attach(ListBox list, Action onBottom)
        {
            if (list == null || onBottom == null)
            {
                return;
            }

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

            if (offset <= lastOffset)
            {
                return;
            }

            int count = list.Items.Count;
            if (count == 0)
            {
                return;
            }

            ListBoxItem last = list.ItemContainerGenerator.ContainerFromIndex(count - 1)
                as ListBoxItem;
            if (last == null)
            {
                return;
            }

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
