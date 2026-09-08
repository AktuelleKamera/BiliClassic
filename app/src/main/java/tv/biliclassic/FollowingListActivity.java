package tv.biliclassic;

import android.os.Bundle;
import android.view.View;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 关注的人：显示自己关注了的 UP 主列表（list 模式，带分割线，古早风格）。
 * 点击跳转用户主页，右侧垃圾桶按钮取消关注。
 */
public class FollowingListActivity extends BaseActivity {

    private ListView listView;
    private TextView emptyView;
    private View footerView;
    private TextView footerText;

    private FollowingAdapter adapter;
    private List<UserInfo> userList = new ArrayList<UserInfo>();

    private boolean isLoading = false;
    private boolean hasMore = true;
    private int page = 1;

    // 遥控器导航选中位置（-1 = 未选中）
    private int selectedPosition = -1;
    // OK 键长按计时（repeatCount 阈值，约 400ms 触发长按）
    private static final int OK_LONG_PRESS_REPEAT = 20;
    private boolean mOkLongPressed = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_following_list);
        initRoundTitleBar();

        listView = (ListView) findViewById(R.id.list_view);
        emptyView = (TextView) findViewById(R.id.empty_view);

        adapter = new FollowingAdapter(this, userList);
        adapter.setOnUnfollowListener(new FollowingAdapter.OnUnfollowListener() {
            @Override
            public void onUnfollowed(UserInfo user, int position) {
                userList.remove(position);
                adapter.notifyDataSetChanged();
                if (userList.size() == 0) {
                    showEmpty(getString(R.string.following_list_empty));
                }
            }
        });

        // 加载更多 footer
        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        listView.addFooterView(footerView);
        setFooterVisible(false);

        listView.setAdapter(adapter);

        // 隐藏 ListView 自带 selector（避免覆盖 item 背景造成滚动误触发按压闪烁）
        listView.setSelector(android.R.color.transparent);

        // 点击 item 跳转用户主页（ListView 管理触摸判定，滚动不会误触）
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= userList.size()) return;
                UserInfo user = userList.get(position);
                if (user == null || user.mid == 0) return;
                Intent intent = new Intent(FollowingListActivity.this, UserProfileActivity.class);
                intent.putExtra("mid", user.mid);
                startActivity(intent);
            }
        });

        // 分割线由 item 内部绘制，不设 ListView 全局 divider（否则 footer 下方也会多一条线）
        listView.setCacheColorHint(0x00000000);

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
            showEmpty(getString(R.string.following_list_not_login));
            return;
        }
        page = 1;
        hasMore = true;
        userList.clear();
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

        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) {
            isLoading = false;
            setFooterVisible(false);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<UserInfo> result = new ArrayList<UserInfo>();
                    final int code = UserInfoApi.getFollowingList(mid, targetPage, result);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            setFooterVisible(false);
                            if (code == 0 && result.size() > 0) {
                                page = targetPage;
                                userList.addAll(result);
                                adapter.notifyDataSetChanged();
                                if (result.size() < 20) {
                                    hasMore = false;
                                }
                                showList();
                            } else if (code == 1) {
                                hasMore = false;
                                if (userList.size() == 0) {
                                    showEmpty(getString(R.string.following_list_empty));
                                }
                            } else {
                                if (userList.size() == 0) {
                                    showEmpty(getString(R.string.following_list_load_fail));
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            setFooterVisible(false);
                            if (userList.size() == 0) {
                                showEmpty(getString(R.string.following_list_load_fail));
                            }
                        }
                    });
                }
            }
        }).start();
    }

    // ===== 遥控器按键导航：方向键移动高亮，OK 短按跳转主页，OK 长按取消关注 =====
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (userList == null || userList.size() == 0 || listView == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_2
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_8) {
            return super.dispatchKeyEvent(event);
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        // 按键恢复：取消触摸滑动时的隐藏，重新显示光标
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }

        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            // OK 键：repeatCount 超过阈值视为长按 → 取消关注；首次按下短按 → 跳转主页
            if (event.getRepeatCount() >= OK_LONG_PRESS_REPEAT) {
                if (!mOkLongPressed) {
                    mOkLongPressed = true;
                    UserInfo user = userList.get(selectedPosition);
                    if (user != null && adapter != null) {
                        adapter.confirmUnfollow(user, selectedPosition);
                    }
                }
                return true;
            }
            if (event.getRepeatCount() == 0) {
                UserInfo user = userList.get(selectedPosition);
                if (user != null && user.mid != 0) {
                    Intent intent = new Intent(FollowingListActivity.this, UserProfileActivity.class);
                    intent.putExtra("mid", user.mid);
                    startActivity(intent);
                }
                return true;
            }
            return true;
        }

        // 首次按下才移动光标；长按 repeat 只消费不移动
        if (event.getRepeatCount() == 0) {
            int count = userList.size();
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_NUM_8) {
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
        int count = userList.size();
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
