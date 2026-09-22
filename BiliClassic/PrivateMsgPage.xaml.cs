using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using BiliClassic.Api;
using Microsoft.Phone.Controls;

namespace BiliClassic
{
    public partial class PrivateMsgPage : PhoneApplicationPage
    {
        private readonly ObservableCollection<PrivateMsgSession> _sessions =
            new ObservableCollection<PrivateMsgSession>();

        private bool _loading;
        private bool _loaded;

        public PrivateMsgPage()
        {
            InitializeComponent();
            SessionList.ItemsSource = _sessions;
            ThemeHelper.ApplyPage(this);
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            if (!_loaded)
            {
                _loaded = true;
                Load();
            }
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);
            _loaded = false;
        }

        private void Load()
        {
            if (_loading)
            {
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                ShowEmpty("请先登录");
                return;
            }

            _loading = true;
            ShowStatus("正在加载…");
            EmptyText.Visibility = Visibility.Collapsed;

            PrivateMsgService.FetchSessions(delegate(List<PrivateMsgSession> sessions, string error)
            {
                Dispatcher.BeginInvoke(new Action(delegate
                {
                    _loading = false;

                    _sessions.Clear();
                    for (int i = 0; i < sessions.Count; i++)
                    {
                        _sessions.Add(sessions[i]);
                        AvatarLoader.Request(sessions[i].User);
                    }

                    if (!string.IsNullOrEmpty(error))
                    {
                        ShowStatus(error);
                    }
                    else if (_sessions.Count == 0)
                    {
                        ShowEmpty("还没有私信");
                    }
                    else
                    {
                        StatusText.Visibility = Visibility.Collapsed;
                    }
                }));
            });
        }

        private void SessionList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            PrivateMsgSession session = SessionList.SelectedItem as PrivateMsgSession;
            if (session == null)
            {
                return;
            }
            SessionList.SelectedIndex = -1;

            if (session.TalkerUid <= 0)
            {
                return;
            }
            NavigationService.Navigate(new Uri(
                "/PrivateMsgChatPage.xaml?uid=" + session.TalkerUid, UriKind.Relative));
        }

        private void RefreshMenuItem_Click(object sender, EventArgs e)
        {
            Load();
        }

        private void ShowEmpty(string message)
        {
            StatusText.Text = "";
            StatusText.Visibility = Visibility.Collapsed;
            EmptyText.Text = message;
            EmptyText.Visibility = Visibility.Visible;
        }

        private void ShowStatus(string message)
        {
            EmptyText.Visibility = Visibility.Collapsed;
            StatusText.Text = message;
            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Visibility.Collapsed
                : Visibility.Visible;
        }
    }
}
