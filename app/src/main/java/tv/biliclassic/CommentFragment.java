package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class CommentFragment extends Fragment {

    private static final String TAG = "CommentFragment";

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private View footerView;
    private ProgressBar footerProgressBar;

    private CommentAdapter adapter;
    private List<CommentItem> commentList = new ArrayList<CommentItem>();
    private Set<Long> commentIdSet = new HashSet<Long>();

    private long aid;
    private String bvid;
    private String nextCursor = "";
    private boolean isLoading = false;
    private boolean isEnd = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment, container, false);

        listView = (ListView) view.findViewById(R.id.list_view);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        listView.setDivider(null);
        listView.setDividerHeight(0);

        footerView = LayoutInflater.from(getActivity()).inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        listView.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        adapter = new CommentAdapter(getActivity(), commentList);
        listView.setAdapter(adapter);

        // 设置评论点击监听
        adapter.setOnUserClickListener(new CommentAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(long mid, String userName) {
                if (mid != 0) {
                    Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), "无法获取用户信息", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bvid = args.getString("bvid");
        }

        Log.e(TAG, "========== onCreateView ==========");
        Log.e(TAG, "aid=" + aid + ", bvid=" + bvid);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && totalCount > 0) {
                        loadMoreComments();
                    }
                } else {
                    adapter.setScrolling(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreComments();
                    }
                }
            }
        });

        loadComments();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.clearCache();
        }
        System.gc();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.clearCache();
        }
    }

    private void loadComments() {
        if (aid == 0 && (bvid == null || bvid.length() == 0)) {
            Log.e(TAG, "aid 和 bvid 都无效，无法加载评论");
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        emptyView.setText("无法加载评论");
                        emptyView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
            return;
        }

        if (isLoading) return;

        isLoading = true;
        nextCursor = "";
        isEnd = false;

        commentList.clear();
        commentIdSet.clear();

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        footerView.setVisibility(View.GONE);

        final String oidParam;
        if (aid != 0) {
            oidParam = String.valueOf(aid);
            Log.e(TAG, "使用 aid 请求评论: " + oidParam);
        } else {
            oidParam = bvid;
            Log.e(TAG, "直接使用 bvid 请求评论: " + oidParam);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/reply/main?type=1&oid=" + oidParam;
                    Log.e(TAG, "评论 API URL: " + url);

                    ArrayList<String> headers = new ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");

                    String cookies = SharedPreferencesUtil.getString("cookies", "");
                    if (cookies != null && cookies.length() > 0) {
                        headers.add("Cookie");
                        headers.add(cookies);
                    }

                    String response = NetWorkUtil.get(url, headers);
                    Log.e(TAG, "评论 API 响应长度: " + (response == null ? "null" : String.valueOf(response.length())));

                    if (response == null || response.length() == 0) {
                        showError("网络返回为空");
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);
                    String message = json.optString("message", "");
                    Log.e(TAG, "评论 API code: " + code + ", message: " + message);

                    if (code == 0) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            JSONObject cursor = data.optJSONObject("cursor");
                            if (cursor != null) {
                                nextCursor = cursor.optString("next", "");
                                isEnd = cursor.optBoolean("is_end", true);
                                Log.e(TAG, "nextCursor: " + nextCursor + ", isEnd: " + isEnd);
                            }

                            JSONArray replies = data.optJSONArray("replies");
                            if (replies != null && replies.length() > 0) {
                                Log.e(TAG, "获取到 " + replies.length() + " 条评论");
                                parseFirstComments(replies);
                            } else {
                                Log.e(TAG, "没有评论");
                                showEmpty("暂无评论");
                            }
                        } else {
                            Log.e(TAG, "data 为 null");
                            showEmpty("暂无评论");
                        }
                    } else {
                        Log.e(TAG, "API 返回错误: " + message);
                        showError("加载失败: " + message);
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "加载评论异常: " + e.getMessage(), e);
                    showError("加载失败: " + e.getMessage());
                } finally {
                    isLoading = false;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void parseFirstComments(JSONArray replies) throws Exception {
        final List<CommentItem> items = new ArrayList<CommentItem>();

        for (int i = 0; i < replies.length(); i++) {
            try {
                JSONObject reply = replies.getJSONObject(i);
                if (reply == null) continue;

                long replyId = reply.optLong("rpid", 0);
                if (replyId == 0) continue;

                if (commentIdSet.contains(replyId)) {
                    continue;
                }
                commentIdSet.add(replyId);

                CommentItem item = new CommentItem();
                item.rpid = replyId;

                JSONObject member = reply.optJSONObject("member");
                if (member != null) {
                    item.userName = member.optString("uname", "匿名用户");
                    // 获取 mid
                    item.mid = member.optLong("mid", 0);
                    String avatar = member.optString("avatar", "");
                    if (avatar != null && avatar.length() > 0) {
                        avatar = avatar.replace("/64", "/48");
                        if (avatar.startsWith("https://")) {
                            avatar = "http://" + avatar.substring(8);
                        }
                    }
                    item.userAvatar = avatar;
                } else {
                    item.userName = "匿名用户";
                    item.userAvatar = null;
                    item.mid = 0;
                }

                JSONObject content = reply.optJSONObject("content");
                if (content != null) {
                    item.message = content.optString("message", "");
                } else {
                    item.message = "";
                }

                JSONObject stat = reply.optJSONObject("stat");
                if (stat != null) {
                    item.likeCount = stat.optInt("like", 0);
                } else {
                    item.likeCount = 0;
                }

                item.time = reply.optLong("ctime", 0);

                items.add(item);
            } catch (Exception e) {
                Log.e(TAG, "解析单条评论失败: " + e.getMessage());
            }
        }

        if (getActivity() == null) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentList.clear();
                commentList.addAll(items);
                adapter.updateData(commentList);

                if (commentList.size() == 0) {
                    emptyView.setText("暂无评论");
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    if (!isEnd && nextCursor != null && nextCursor.length() > 0) {
                        footerView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void loadMoreComments() {
        if (isLoading || isEnd) return;
        if (nextCursor == null || nextCursor.length() == 0) {
            isEnd = true;
            footerView.setVisibility(View.GONE);
            return;
        }

        isLoading = true;

        footerProgressBar.setVisibility(View.VISIBLE);
        footerView.setVisibility(View.VISIBLE);

        final String oidParam = (aid != 0) ? String.valueOf(aid) : bvid;
        final String cursor = nextCursor;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/v2/reply/main?type=1&oid=" + oidParam + "&next=" + cursor;
                    Log.e(TAG, "加载更多评论 URL: " + url);

                    ArrayList<String> headers = new ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");

                    String cookies = SharedPreferencesUtil.getString("cookies", "");
                    if (cookies != null && cookies.length() > 0) {
                        headers.add("Cookie");
                        headers.add(cookies);
                    }

                    String response = NetWorkUtil.get(url, headers);

                    if (response == null || response.length() == 0) {
                        showLoadMoreError("网络返回为空");
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);
                    Log.e(TAG, "加载更多 code: " + code);

                    if (code == 0) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            JSONObject cursor = data.optJSONObject("cursor");
                            if (cursor != null) {
                                nextCursor = cursor.optString("next", "");
                                isEnd = cursor.optBoolean("is_end", true);
                            }

                            JSONArray replies = data.optJSONArray("replies");
                            if (replies != null && replies.length() > 0) {
                                appendMoreComments(replies);
                            } else {
                                showLoadMoreError("没有更多评论");
                            }
                        } else {
                            showLoadMoreError("没有更多评论");
                        }
                    } else {
                        String message = json.optString("message", "加载失败");
                        showLoadMoreError(message);
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "加载更多异常: " + e.getMessage(), e);
                    showLoadMoreError("加载失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void appendMoreComments(JSONArray replies) throws Exception {
        final List<CommentItem> items = new ArrayList<CommentItem>();

        for (int i = 0; i < replies.length(); i++) {
            try {
                JSONObject reply = replies.getJSONObject(i);
                if (reply == null) continue;

                long replyId = reply.optLong("rpid", 0);
                if (replyId == 0) continue;

                if (commentIdSet.contains(replyId)) {
                    continue;
                }
                commentIdSet.add(replyId);

                CommentItem item = new CommentItem();
                item.rpid = replyId;

                JSONObject member = reply.optJSONObject("member");
                if (member != null) {
                    item.userName = member.optString("uname", "匿名用户");
                    item.mid = member.optLong("mid", 0);
                    String avatar = member.optString("avatar", "");
                    if (avatar != null && avatar.length() > 0) {
                        avatar = avatar.replace("/64", "/48");
                        if (avatar.startsWith("https://")) {
                            avatar = "http://" + avatar.substring(8);
                        }
                    }
                    item.userAvatar = avatar;
                } else {
                    item.userName = "匿名用户";
                    item.userAvatar = null;
                    item.mid = 0;
                }

                JSONObject content = reply.optJSONObject("content");
                if (content != null) {
                    item.message = content.optString("message", "");
                } else {
                    item.message = "";
                }

                JSONObject stat = reply.optJSONObject("stat");
                if (stat != null) {
                    item.likeCount = stat.optInt("like", 0);
                } else {
                    item.likeCount = 0;
                }

                item.time = reply.optLong("ctime", 0);

                items.add(item);
            } catch (Exception e) {
                Log.e(TAG, "解析单条评论失败: " + e.getMessage());
            }
        }

        if (getActivity() == null) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentList.addAll(items);
                adapter.updateData(commentList);

                footerProgressBar.setVisibility(View.GONE);
                isLoading = false;

                if (isEnd) {
                    footerView.setVisibility(View.GONE);
                    if (items.size() > 0) {
                        Toast.makeText(getActivity(), "已经到底啦", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    footerView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showLoadMoreError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                footerProgressBar.setVisibility(View.GONE);
                isLoading = false;
                footerView.setVisibility(View.GONE);
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static class CommentItem {
        public long rpid;
        public String userName;
        public String userAvatar;
        public String message;
        public int likeCount;
        public long time;
        public long mid;
    }
}