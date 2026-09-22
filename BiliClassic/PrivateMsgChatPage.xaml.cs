using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Navigation;
using System.Windows.Threading;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class PrivateMsgChatPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<PrivateMsgItem> _messages =
            new ObservableCollection<PrivateMsgItem>();

        private string _uid = "";
        private string _talkerName = "";

        private bool _loading;
        private bool _sending;
        private bool _initialized;
        private bool _loaded;

        private long _maxSeqno;

        private DispatcherTimer _timer;

        public PrivateMsgChatPage()
        {
            InitializeComponent();
            ThemeHelper.ApplyPage(this);
            MessageList.ItemsSource = _messages;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            string value;
            if (NavigationContext.QueryString.TryGetValue("uid", out value))
            {
                _uid = value;
            }

            if (string.IsNullOrEmpty(_uid))
            {
                ShowStatus("没有收到会话ID");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowStatus("请先登录");
                SendButton.IsEnabled = false;
                return;
            }

            if (!_loaded)
            {
                _loaded = true;
                _talkerName = PrivateMsgService.GetCachedName(_uid);
                if (_talkerName.Length > 0)
                {
                    TitleText.Text = _talkerName;
                }
                LoadCard();
                Refresh(true);
            }
            else
            {
                Refresh(false);
            }

            StartTimer();
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            StopTimer();
        }

        private void StartTimer()
        {
            if (_timer == null)
            {
                _timer = new DispatcherTimer();
                _timer.Interval = TimeSpan.FromSeconds(15);
                _timer.Tick += delegate(object sender, EventArgs e)
                {
                    Refresh(IsNearBottom());
                };
            }
            _timer.Start();
        }

        private void StopTimer()
        {
            if (_timer != null)
            {
                _timer.Stop();
            }
        }

        private void LoadCard()
        {
            PrivateMsgService.FetchCard(_uid, delegate(UserItem user, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (user == null || user.Name.Length == 0)
                    {
                        return;
                    }
                    _talkerName = user.Name;
                    TitleText.Text = user.Name;
                }));
            });
        }

        private void Refresh(bool scrollBottom)
        {
            if (_loading)
            {
                return;
            }
            _loading = true;

            PrivateMsgService.FetchMessages(_uid, _talkerName,
                delegate(List<PrivateMsgItem> items, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;
                    ApplyMessages(items, error, scrollBottom);
                }));
            });
        }

        private void ApplyMessages(List<PrivateMsgItem> items, string error, bool scrollBottom)
        {
            if (!string.IsNullOrEmpty(error))
            {
                ShowStatus(error);
                return;
            }

            ShowStatus("");

            if (!_initialized)
            {
                _initialized = true;
                _messages.Clear();
                for (int i = 0; i < items.Count; i++)
                {
                    _messages.Add(items[i]);
                    if (items[i].Seqno > _maxSeqno)
                    {
                        _maxSeqno = items[i].Seqno;
                    }
                }

                EmptyText.Visibility = _messages.Count == 0
                    ? Visibility.Visible : Visibility.Collapsed;
                ScrollToBottom();
                PrivateMsgService.MarkRead(_uid);
                return;
            }

            int added = 0;
            for (int i = 0; i < items.Count; i++)
            {
                if (items[i].Seqno <= _maxSeqno)
                {
                    continue;
                }
                _messages.Add(items[i]);
                if (items[i].Seqno > _maxSeqno)
                {
                    _maxSeqno = items[i].Seqno;
                }
                added++;
            }

            if (_messages.Count > 0)
            {
                EmptyText.Visibility = Visibility.Collapsed;
            }
            if (added > 0 && scrollBottom)
            {
                ScrollToBottom();
            }
        }

        private void SendButton_Click(object sender, RoutedEventArgs e)
        {
            Send();
        }

        private void InputBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                Send();
            }
        }

        private void Send()
        {
            string text = (InputBox.Text ?? "").Trim();
            if (text.Length == 0)
            {
                ShowStatus("还没有输入内容");
                return;
            }
            if (_sending)
            {
                return;
            }

            _sending = true;
            SendButton.IsEnabled = false;
            ShowStatus("正在发送…");

            PrivateMsgService.Send(_uid, text, delegate(bool ok, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _sending = false;
                    SendButton.IsEnabled = true;

                    if (ok)
                    {
                        InputBox.Text = "";
                        ShowStatus("");
                        Refresh(true);
                    }
                    else
                    {
                        ShowStatus(error);
                    }
                }));
            });
        }

        private void ScrollToBottom()
        {
            if (_messages.Count == 0)
            {
                return;
            }
            Dispatcher.BeginInvoke(new Action(delegate
            {
                try
                {
                    MessageList.ScrollIntoView(_messages[_messages.Count - 1]);
                }
                catch (Exception)
                {
                }
            }));
        }

        private bool IsNearBottom()
        {
            int count = _messages.Count;
            if (count == 0)
            {
                return true;
            }

            ListBoxItem last = MessageList.ItemContainerGenerator
                .ContainerFromIndex(count - 1) as ListBoxItem;
            if (last == null)
            {
                return false;
            }

            double y;
            try
            {
                y = last.TransformToVisual(MessageList).Transform(new Point(0, 0)).Y;
            }
            catch (Exception)
            {
                return false;
            }
            return y < MessageList.ActualHeight + 60;
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message;
            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Visibility.Collapsed
                : Visibility.Visible;
        }
    }
}
