package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import tv.biliclassic.api.PrivateMsgApi;
import tv.biliclassic.model.PrivateMsgSession;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 私信会话列表
 */
public class PrivateMsgListActivity extends BaseActivity {

    private ListView listView;
    private TextView emptyView;
    private PrivateMsgSessionAdapter adapter;
    private List<PrivateMsgSession> sessionList = new ArrayList<PrivateMsgSession>();
    private HashMap<Long, UserInfo> userMap = new HashMap<Long, UserInfo>();

    private boolean isLoading = false;
    private int selectedPosition = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_msg_list);
        initRoundTitleBar();

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new PrivateMsgSessionAdapter(this, sessionList, userMap);
        listView.setAdapter(adapter);
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(0x00000000);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                openSession(position);
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    tv.biliclassic.util.ImageLoader.setScrolling(false);
                    adapter.setHideHighlight(false);
                } else {
                    tv.biliclassic.util.ImageLoader.setScrolling(true);
                    adapter.setHideHighlight(true);
                }
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.release();
        }
        super.onDestroy();
    }

    private void loadSessions() {
        if (isLoading) return;
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) {
            showEmpty("请先登录");
            return;
        }
        isLoading = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<PrivateMsgSession> result = PrivateMsgApi.getNewSessionsList(0, 50);
                    if (result.size() == 0) {
                        result = PrivateMsgApi.getSessionsList(50);
                    }
                    Collections.sort(result, new Comparator<PrivateMsgSession>() {
                        @Override
                        public int compare(PrivateMsgSession o1, PrivateMsgSession o2) {
                            boolean u1 = o1.unread > 0;
                            boolean u2 = o2.unread > 0;
                            if (u1 == u2) return 0;
                            return u1 ? -1 : 1;
                        }
                    });
                    ArrayList<Long> uidList = new ArrayList<Long>();
                    for (int i = 0; i < result.size(); i++) {
                        uidList.add(result.get(i).talkerUid);
                    }
                    HashMap<Long, UserInfo> map = new HashMap<Long, UserInfo>();
                    try {
                        map = PrivateMsgApi.getUsersInfo(uidList);
                    } catch (Exception ignore) {
                    }
                    final ArrayList<PrivateMsgSession> finalResult = result;
                    final HashMap<Long, UserInfo> finalMap = map;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            sessionList.clear();
                            sessionList.addAll(finalResult);
                            userMap.clear();
                            userMap.putAll(finalMap);
                            adapter.notifyDataSetChanged();
                            if (sessionList.size() == 0) {
                                showEmpty("还没有私信");
                            } else {
                                showList();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if (sessionList.size() == 0) {
                                showEmpty("加载失败，请检查网络");
                            }
                            MsgUtil.showMsg(PrivateMsgListActivity.this, "加载失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void openSession(int position) {
        if (position < 0 || position >= sessionList.size()) return;
        PrivateMsgSession session = sessionList.get(position);
        if (session == null || session.talkerUid == 0) return;
        Intent intent = new Intent(this, PrivateMsgActivity.class);
        intent.putExtra("uid", session.talkerUid);
        UserInfo user = userMap.get(session.talkerUid);
        String name = user != null ? user.name : session.talkerName;
        String face = user != null ? user.avatar : session.talkerFace;
        intent.putExtra("name", name);
        intent.putExtra("face", face);
        startActivity(intent);
    }

    private void showEmpty(String msg) {
        if (emptyView != null) {
            emptyView.setText(msg);
            emptyView.setVisibility(View.VISIBLE);
        }
        if (listView != null) {
            listView.setVisibility(View.GONE);
        }
    }

    private void showList() {
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (listView != null) {
            listView.setVisibility(View.VISIBLE);
        }
    }

    // 遥控器导航
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (sessionList == null || sessionList.size() == 0 || listView == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        adapter.setHideHighlight(false);
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            if (event.getRepeatCount() == 0) {
                openSession(selectedPosition);
            }
            return true;
        }
        if (event.getRepeatCount() == 0) {
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else {
                selectedPosition = Math.min(sessionList.size() - 1, selectedPosition + 1);
            }
            adapter.setSelectedPosition(selectedPosition);
            listView.setSelection(selectedPosition);
        }
        return true;
    }
}
