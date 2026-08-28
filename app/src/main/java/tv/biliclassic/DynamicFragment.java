package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.DynamicApi;
import tv.biliclassic.model.Dynamic;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 动态页：关注动态列表（分页/下拉刷新）、发送文字动态、点赞、删除。
 */
public class DynamicFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private SwipeRefreshLayout swipeRefresh;

    private DynamicAdapter adapter;
    private List<Dynamic> feedList = new ArrayList<Dynamic>();

    // null = 尚未加载过；"" = 没有更多
    private String offset = null;
    private boolean isLoading = false;
    private boolean publishing = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dynamic, container, false);

        // API 3: 移除 SwipeRefreshLayout（引起 Layout.draw 递归），与推荐页处理一致
        if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
            SwipeRefreshLayout srl = (SwipeRefreshLayout)
                    view.findViewById(R.id.swipe_refresh_dynamic);
            ViewGroup parent = (ViewGroup) view.findViewById(R.id.dynamic_content);
            ListView list = (ListView) view.findViewById(R.id.dynamic_list);
            if (srl != null && parent != null && list != null) {
                int idx = parent.indexOfChild(srl);
                ViewGroup gParent = (ViewGroup) list.getParent();
                if (gParent != null) gParent.removeView(list);
                parent.removeView(srl);
                parent.addView(list, idx, srl.getLayoutParams());
            }
        }

        listView = (ListView) view.findViewById(R.id.dynamic_list);
        progressBar = (ProgressBar) view.findViewById(R.id.dynamic_progress);
        emptyView = (TextView) view.findViewById(R.id.dynamic_empty);

        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 4) {
            swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_dynamic);
            if (swipeRefresh != null) {
                swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        loadFeed();
                    }
                });
            }
        }

        adapter = new DynamicAdapter(getActivity(), feedList, new DynamicAdapter.Listener() {
            @Override
            public void onLike(Dynamic d) {
                doLike(d);
            }

            @Override
            public void onDelete(Dynamic d) {
                confirmDelete(d);
            }
        });
        listView.setAdapter(adapter);

        // 滚动接近底部自动加载下一页
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    checkLoadMore();
                }
            }
        });

        View btnSend = view.findViewById(R.id.btn_send_dynamic);
        if (btnSend != null) {
            btnSend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPublishDialog();
                }
            });
        }

        View btnRefresh = view.findViewById(R.id.btn_refresh_dynamic);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadFeed();
                }
            });
        }

        loadFeed();

        return view;
    }

    private boolean isLoggedIn() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private void showLoading() {
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (progressBar != null && feedList.size() == 0) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (listView != null && feedList.size() == 0) {
            listView.setVisibility(View.GONE);
        }
    }

    private void hideLoading() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        stopRefreshing();
    }

    private void stopRefreshing() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    private void showEmpty(String msg) {
        hideLoading();
        if (emptyView != null) {
            emptyView.setText(msg);
            emptyView.setVisibility(View.VISIBLE);
        }
        if (listView != null && feedList.size() == 0) {
            listView.setVisibility(View.GONE);
        }
    }

    private void showToast(String msg) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    /** 刷新（回到第一页） */
    public void loadFeed() {
        if (isLoading) return;
        if (!isLoggedIn()) {
            offset = null;
            feedList.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            showEmpty(getString(R.string.dynamicfragment_settext_login));
            return;
        }
        isLoading = true;
        showLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                final List<Dynamic> items = new ArrayList<Dynamic>();
                String next = "";
                try {
                    next = DynamicApi.getFeedList(items, null);
                } catch (final Exception e) {
                    error = e.getMessage();
                }
                final String fError = error;
                final String fNext = next;
                final int fCount = items.size();
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        isLoading = false;
                        if (getActivity() == null || getView() == null) return;
                        if (fError != null) {
                            if (feedList.size() == 0) {
                                showEmpty(getString(R.string.emoticon__failed_need_retry));
                            } else {
                                hideLoading();
                                showToast("刷新失败: " + fError);
                            }
                            return;
                        }
                        offset = fNext;
                        feedList.clear();
                        feedList.addAll(items);
                        adapter.notifyDataSetChanged();
                        hideLoading();
                        if (fCount == 0) {
                            showEmpty(getString(R.string.dynamicfragment_settext_6682));
                        } else {
                            if (emptyView != null) emptyView.setVisibility(View.GONE);
                            if (listView != null) listView.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    private void checkLoadMore() {
        if (listView == null || isLoading) return;
        if (offset == null || offset.length() == 0) return;
        if (feedList.size() == 0) return;
        int last = listView.getLastVisiblePosition();
        if (last >= listView.getCount() - 2) {
            loadMore();
        }
    }

    private void loadMore() {
        if (isLoading || offset == null || offset.length() == 0) return;
        isLoading = true;
        final String pageOffset = offset;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                final List<Dynamic> items = new ArrayList<Dynamic>();
                String next = "";
                try {
                    next = DynamicApi.getFeedList(items, pageOffset);
                } catch (final Exception e) {
                    error = e.getMessage();
                }
                final String fError = error;
                final String fNext = next;
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        isLoading = false;
                        if (getActivity() == null || getView() == null) return;
                        if (fError != null) {
                            showToast("加载更多失败: " + fError);
                            return;
                        }
                        offset = fNext;
                        feedList.addAll(items);
                        adapter.notifyDataSetChanged();
                        if (items.size() == 0) {
                            showToast(getString(R.string.emoticon__no_more_data));
                        }
                    }
                });
            }
        }).start();
    }

    private void doLike(final Dynamic d) {
        if (!isLoggedIn()) {
            showToast(getString(R.string.dynamicfragment_settext_login));
            return;
        }
        final boolean target = !d.liked;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code;
                try {
                    code = DynamicApi.likeDynamic(d.dynamicId, target);
                } catch (Exception e) {
                    code = -1;
                }
                final int fCode = code;
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() == null) return;
                        if (fCode == 0) {
                            d.liked = target;
                            d.likeCount += target ? 1 : -1;
                            if (d.likeCount < 0) d.likeCount = 0;
                            adapter.notifyDataSetChanged();
                        } else {
                            showToast(getString(R.string.dynamicfragment_toast_op_fail)
                                    + " (" + fCode + ")");
                        }
                    }
                });
            }
        }).start();
    }

    private void confirmDelete(final Dynamic d) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(getActivity())))
                .setTitle(getString(R.string.dynamicfragment_settitle_delete))
                .setMessage(getString(R.string.dynamicfragment_setmessage_delete))
                .setPositiveButton(getString(R.string.dynamicfragment_btn_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                doDelete(d);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doDelete(final Dynamic d) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code;
                try {
                    code = DynamicApi.deleteDynamic(d.dynamicId);
                } catch (Exception e) {
                    code = -1;
                }
                final int fCode = code;
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() == null) return;
                        if (fCode == 0) {
                            feedList.remove(d);
                            adapter.notifyDataSetChanged();
                            if (feedList.size() == 0) {
                                showEmpty(getString(R.string.dynamicfragment_settext_6682));
                            }
                            showToast(getString(R.string.dynamicfragment_toast_op_success));
                        } else {
                            showToast(getString(R.string.dynamicfragment_toast_op_fail)
                                    + " (" + fCode + ")");
                        }
                    }
                });
            }
        }).start();
    }

    private void showPublishDialog() {
        if (getActivity() == null) return;
        if (!isLoggedIn()) {
            showToast(getString(R.string.dynamicfragment_settext_login));
            startActivity(new Intent(getActivity(), LoginActivity.class));
            return;
        }
        if (publishing) {
            showToast(getString(R.string.dynamicfragment_toast_sending));
            return;
        }

        final EditText input = new EditText(getActivity());
        input.setHint(getString(R.string.dynamicfragment_hint_input));
        input.setLines(3);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});

        android.app.AlertDialog dlg = new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(DialogUtil.wrap(getActivity())))
                .setTitle(getString(R.string.dynamicfragment_settitle_publish))
                .setView(input)
                .setPositiveButton(getString(R.string.dynamicfragment_btn_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        android.widget.Button positiveBtn = tv.biliclassic.util.SdkHelper.getDialogButton(dlg, AlertDialog.BUTTON_POSITIVE);
        if (positiveBtn != null) positiveBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String text = input.getText().toString().trim();
                        if (text == null || text.length() == 0) {
                            showToast(getString(R.string.dynamicfragment_toast_empty));
                            return;
                        }
                        publish(text);
                    }
                });
    }

    private void publish(final String content) {
        if (getActivity() == null || publishing) return;
        publishing = true;
        showToast(getString(R.string.dynamicfragment_toast_sending));

        new Thread(new Runnable() {
            @Override
            public void run() {
                int code;
                try {
                    code = DynamicApi.publishText(content);
                } catch (Exception e) {
                    code = -1;
                }
                final int fCode = code;
                postUi(new Runnable() {
                    @Override
                    public void run() {
                        publishing = false;
                        if (getActivity() == null) return;
                        if (fCode == 0) {
                            showToast(getString(R.string.dynamicfragment_toast_success));
                            loadFeed();
                        } else {
                            showToast(getString(R.string.dynamicfragment_toast_fail)
                                    + " (" + fCode + ")");
                        }
                    }
                });
            }
        }).start();
    }

    private void postUi(Runnable r) {
        if (getActivity() == null) return;
        runUi(r);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.clearCache();
        }
    }
    /** UI 线程安全执行：单次读取 getActivity()，避免后台线程两次调用间被置空导致 NPE */
    private void runUi(java.lang.Runnable r) {
        android.support.v4.app.FragmentActivity a = getActivity();
        if (a != null) a.runOnUiThread(r);
    }

}
