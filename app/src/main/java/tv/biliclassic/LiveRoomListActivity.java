package tv.biliclassic;

import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.LiveApi;
import tv.biliclassic.model.LiveRoom;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 生放送列表：正在直播的关注房间（需登录，带 Cookie 请求）。
 * 点击条目进入生放送房间详情页。
 */
public class LiveRoomListActivity extends BaseActivity {

    private ListView listView;
    private TextView emptyView;
    private View footerView;
    private TextView footerText;

    private LiveRoomAdapter adapter;
    private List<LiveRoom> roomList = new ArrayList<LiveRoom>();

    private boolean isLoading = false;
    private boolean hasMore = true;
    private int page = 1;

    // 遥控器导航选中位置（-1 = 未选中）
    private int selectedPosition = -1;
    // OK 键长按计时（repeatCount 阈值，约 400ms 触发长按）
    private static final int OK_LONG_PRESS_REPEAT = 20;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room_list);
        initRoundTitleBar();

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new LiveRoomAdapter(this, roomList);
        listView.setAdapter(adapter);

        // 加载更多 footer
        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        listView.addFooterView(footerView);
        setFooterVisible(false);

        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(0x00000000);

        // 点击条目跳转生放送详情
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= roomList.size()) return;
                LiveRoom room = roomList.get(position);
                if (room == null || room.realRoomId() == 0) return;
                Intent intent = new Intent(LiveRoomListActivity.this, LiveInfoActivity.class);
                intent.putExtra("room_id", room.realRoomId());
                startActivity(intent);
            }
        });

        // 滚动到底部加载更多
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    adapter.setHideHighlight(false);
                    if (listView.getLastVisiblePosition() >= listView.getCount() - 1) {
                        loadNextPage();
                    }
                } else {
                    adapter.setScrolling(true);
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

        // 空视图：未登录时点击去登录；已登录无内容时点击重新加载
        emptyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                if (mid == 0) {
                    startActivity(new Intent(LiveRoomListActivity.this, LoginActivity.class));
                } else {
                    loadFirstPage();
                }
            }
        });

        loadFirstPage();
    }

    private void setFooterVisible(boolean visible) {
        if (footerView == null) return;
        footerView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (footerText != null) {
            footerText.setText(visible
                    ? getString(R.string.login_working_hard_6)
                    : "");
        }
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

    private void loadFirstPage() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) {
            // 列表依赖登录 Cookie（关注的直播间），未登录直接提示
            showEmpty(getString(R.string.live_room_list_not_login));
            return;
        }
        page = 1;
        hasMore = true;
        roomList.clear();
        adapter.notifyDataSetChanged();
        showList();
        loadPage(1);
    }

    private void loadNextPage() {
        if (isLoading || !hasMore) return;
        loadPage(page + 1);
    }

    private void loadPage(final int targetPage) {
        if (isLoading) return;
        isLoading = true;
        setFooterVisible(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<LiveRoom> result = new ArrayList<LiveRoom>();
                final int[] codeRef = new int[]{-1};
                try {
                    codeRef[0] = LiveApi.getFollowedRooms(targetPage, result);
                } catch (final Exception e) {
                    android.util.Log.e("LiveRoomList", "loadPage fail", e);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int code = codeRef[0];
                        isLoading = false;
                        setFooterVisible(false);
                        if (code == 0 && result.size() > 0) {
                            page = targetPage;
                            roomList.addAll(result);
                            adapter.notifyDataSetChanged();
                            if (result.size() < 10) {
                                hasMore = false;
                            }
                            showList();
                        } else if (code == 0) {
                            // 这一页没有更多了
                            hasMore = false;
                            if (roomList.size() == 0) {
                                showEmpty(getString(R.string.live_room_list_empty));
                            }
                        } else {
                            if (roomList.size() == 0) {
                                showEmpty(getString(R.string.live_room_list_load_fail));
                            }
                        }
                    }
                });
            }
        }).start();
    }

    // ===== 遥控器按键导航：方向键移动高亮，OK 短按进入生放送 =====
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (roomList == null || roomList.size() == 0 || listView == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = KeyBindingUtil.classify(event.getKeyCode());
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM
                && action != KeyBindingUtil.ACTION_NUM_2
                && action != KeyBindingUtil.ACTION_NUM_8) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }

        if (action == KeyBindingUtil.ACTION_CONFIRM) {
            if (event.getRepeatCount() == 0) {
                LiveRoom room = roomList.get(selectedPosition);
                if (room != null && room.realRoomId() != 0) {
                    Intent intent = new Intent(LiveRoomListActivity.this, LiveInfoActivity.class);
                    intent.putExtra("room_id", room.realRoomId());
                    startActivity(intent);
                }
                return true;
            }
            return true;
        }

        if (event.getRepeatCount() == 0) {
            int count = roomList.size();
            if (action == KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
            }
            applySelection();
        }
        return true;
    }

    private int pageMove(int direction) {
        if (listView == null) return selectedPosition;
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        int count = roomList.size();
        if (newPos < 0) newPos = 0;
        else if (newPos >= count) newPos = count - 1;
        return newPos;
    }

    private void applySelection() {
        if (adapter != null) {
            adapter.setSelectedPosition(selectedPosition);
        }
        if (listView != null) {
            listView.setSelection(selectedPosition);
        }
    }
}
