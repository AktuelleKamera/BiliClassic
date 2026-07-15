package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.util.ReplyHelper;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class CommentFragment extends Fragment {

    private static final String TAG = "CommentFragment";

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private View footerView;
    private ProgressBar footerProgressBar;
    private TextView footerText;

    private CommentAdapter adapter;
    private List<CommentItem> commentList = new ArrayList<CommentItem>();
    private Set<Long> commentIdSet = new HashSet<Long>();

    private long aid;
    private String bvid;
    private String nextCursor = "";
    private boolean isLoading = false;
    private boolean isEnd = false;

    // 保存滚动位置
    private int savedScrollPosition = -1;
    private int savedScrollOffset = 0;

    // 排序控制
    private int currentSortMode = 0; // 0=时间,1=热度
    private TextView sortTimeBtn, sortHotBtn;

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
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        listView.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        adapter = new CommentAdapter(getActivity(), commentList, aid, this);
        adapter.setMid(SharedPreferencesUtil.getLong("mid", 0));
        adapter.setReplyType(1);
        listView.setAdapter(adapter);

        // 排序按钮（按时间 / 按热度）
        sortTimeBtn = (TextView) view.findViewById(R.id.sort_time);
        sortHotBtn = (TextView) view.findViewById(R.id.sort_hot);
        if (sortTimeBtn != null && sortHotBtn != null) {
            sortTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSortMode = 0;
                    sortTimeBtn.setTextColor(0xFFD86DA5);
                    sortHotBtn.setTextColor(0xFF999999);
                    sortAndRefreshComments();
                }
            });
            sortHotBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSortMode = 1;
                    sortTimeBtn.setTextColor(0xFF999999);
                    sortHotBtn.setTextColor(0xFFD86DA5);
                    sortAndRefreshComments();
                }
            });
        }

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

        adapter.setOnReplyClickListener(new CommentAdapter.OnReplyClickListener() {
            @Override
            public void onReplyClick(CommentItem comment, ReplyItem reply) {
                showReplyDialog(comment, reply);
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bvid = args.getString("bvid");
        }

        Log.e(TAG, "========== onCreateView ==========");
        Log.e(TAG, "aid=" + aid + ", bvid=" + bvid);

        // 滚动监听 - 保存滚动位置
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    // 保存滚动位置
                    int firstVisible = view.getFirstVisiblePosition();
                    View firstChild = view.getChildAt(0);
                    int offset = (firstChild == null) ? 0 : firstChild.getTop();
                    savedScrollPosition = firstVisible;
                    savedScrollOffset = offset;

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

        // 检查是否有保存的滚动位置
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt("savedScrollPosition", -1);
            savedScrollOffset = savedInstanceState.getInt("savedScrollOffset", 0);
        }

        loadComments();

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("savedScrollPosition", savedScrollPosition);
        outState.putInt("savedScrollOffset", savedScrollOffset);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从回复列表返回时恢复滚动位置
        restoreScrollPosition();
    }

    @Override
    public void onStop() {
        super.onStop();
        // 在停止前保存滚动位置
        saveCurrentScrollPosition();
        if (adapter != null) {
            adapter.clearCache();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.clearCache();
        }
        System.gc();
    }

    // 恢复滚动位置
    private void restoreScrollPosition() {
        if (savedScrollPosition >= 0 && listView != null && adapter != null && savedScrollPosition < adapter.getCount()) {
            listView.post(new Runnable() {
                @Override
                public void run() {
                    listView.setSelectionFromTop(savedScrollPosition, savedScrollOffset);
                }
            });
        }
    }

    // 保存当前滚动位置
    private void saveCurrentScrollPosition() {
        if (listView == null) return;
        int firstVisible = listView.getFirstVisiblePosition();
        View firstChild = listView.getChildAt(0);
        int offset = (firstChild == null) ? 0 : firstChild.getTop();
        savedScrollPosition = firstVisible;
        savedScrollOffset = offset;
    }

    // 刷新评论（供外部调用）
    public void refreshComments() {
        if (isLoading) return;
        nextCursor = "";
        isEnd = false;
        commentList.clear();
        commentIdSet.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        savedScrollPosition = 0;
        savedScrollOffset = 0;
        loadComments();
    }

    private void sortAndRefreshComments() {
        if (commentList == null || commentList.size() == 0) {
            refreshComments();
            return;
        }
        java.util.Collections.sort(commentList, new java.util.Comparator<CommentItem>() {
            @Override
            public int compare(CommentItem a, CommentItem b) {
                if (currentSortMode == 1) {
                    return Integer.valueOf(b.likeCount).compareTo(Integer.valueOf(a.likeCount));
                } else {
                    return Long.valueOf(b.time).compareTo(Long.valueOf(a.time));
                }
            }
        });
        if (adapter != null) {
            adapter.updateData(commentList);
        }
        // 排序后回到顶部
        if (listView != null) {
            listView.setSelection(0);
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
                item.replyCount = reply.optInt("rcount", 0);

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
                    // 解析图片
                    JSONArray pictures = content.optJSONArray("pictures");
                    if (pictures != null && pictures.length() > 0) {
                        item.pictureList = new ArrayList<String>();
                        for (int p = 0; p < pictures.length(); p++) {
                            JSONObject pic = pictures.getJSONObject(p);
                            String imgSrc = pic.optString("img_src", "");
                            if (imgSrc != null && imgSrc.length() > 0) {
                                if (imgSrc.startsWith("https://")) {
                                    imgSrc = "http://" + imgSrc.substring(8);
                                }
                                item.pictureList.add(imgSrc);
                            }
                        }
                    }
                } else {
                    item.message = "";
                }

                item.likeCount = reply.optInt("like", 0);
                item.liked = reply.optInt("action", 0) == 1;
                item.time = reply.optLong("ctime", 0);

                JSONArray replyReplies = reply.optJSONArray("replies");
                if (replyReplies != null && replyReplies.length() > 0) {
                    item.replies = new ArrayList<ReplyItem>();
                    for (int j = 0; j < replyReplies.length(); j++) {
                        try {
                            JSONObject rr = replyReplies.getJSONObject(j);
                            ReplyItem ri = new ReplyItem();
                            ri.rpid = rr.optLong("rpid", 0);
                            ri.root = rr.optLong("root", item.rpid);
                            if (ri.root == 0) ri.root = item.rpid;
                            ri.parent = rr.optLong("parent", ri.rpid);
                            if (ri.parent == 0) ri.parent = ri.rpid;
                            JSONObject rmember = rr.optJSONObject("member");
                            if (rmember != null) {
                                ri.userName = rmember.optString("uname", "");
                                ri.mid = rmember.optLong("mid", 0);
                            }
                            JSONObject rcontent = rr.optJSONObject("content");
                            ri.message = rcontent != null ? rcontent.optString("message", "") : "";
                            item.replies.add(ri);
                        } catch (Exception e) { }
                    }
                }

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
                // 恢复滚动位置
                restoreScrollPosition();
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
                                isEnd = true;
                                showEnd();
                            }
                        } else {
                            isEnd = true;
                            showEnd();
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
                item.replyCount = reply.optInt("rcount", 0);

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
                    // 解析图片
                    JSONArray pictures = content.optJSONArray("pictures");
                    if (pictures != null && pictures.length() > 0) {
                        item.pictureList = new ArrayList<String>();
                        for (int p = 0; p < pictures.length(); p++) {
                            JSONObject pic = pictures.getJSONObject(p);
                            String imgSrc = pic.optString("img_src", "");
                            if (imgSrc != null && imgSrc.length() > 0) {
                                if (imgSrc.startsWith("https://")) {
                                    imgSrc = "http://" + imgSrc.substring(8);
                                }
                                item.pictureList.add(imgSrc);
                            }
                        }
                    }
                } else {
                    item.message = "";
                }

                item.likeCount = reply.optInt("like", 0);
                item.liked = reply.optInt("action", 0) == 1;
                item.time = reply.optLong("ctime", 0);

                JSONArray replyReplies = reply.optJSONArray("replies");
                if (replyReplies != null && replyReplies.length() > 0) {
                    item.replies = new ArrayList<ReplyItem>();
                    for (int j = 0; j < replyReplies.length(); j++) {
                        try {
                            JSONObject rr = replyReplies.getJSONObject(j);
                            ReplyItem ri = new ReplyItem();
                            ri.rpid = rr.optLong("rpid", 0);
                            ri.root = rr.optLong("root", item.rpid);
                            if (ri.root == 0) ri.root = item.rpid;
                            ri.parent = rr.optLong("parent", ri.rpid);
                            if (ri.parent == 0) ri.parent = ri.rpid;
                            JSONObject rmember = rr.optJSONObject("member");
                            if (rmember != null) {
                                ri.userName = rmember.optString("uname", "");
                                ri.mid = rmember.optLong("mid", 0);
                            }
                            JSONObject rcontent = rr.optJSONObject("content");
                            ri.message = rcontent != null ? rcontent.optString("message", "") : "";
                            item.replies.add(ri);
                        } catch (Exception e) { }
                    }
                }

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
                    if (footerProgressBar != null) {
                        footerProgressBar.setVisibility(View.GONE);
                    }
                    if (footerText != null) {
                        footerText.setText(getString(R.string.emoticon__no_more_data));
                        footerText.setVisibility(View.VISIBLE);
                    }
                    footerView.setVisibility(View.VISIBLE);
                } else {
                    footerView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showReplyDialog(final CommentItem comment, final ReplyItem reply) {
        if (getActivity() == null) return;
        String hint = "输入回复内容...";
        if (reply != null && reply.userName != null && reply.userName.length() > 0) {
            hint = "回复 " + reply.userName + " 的评论...";
        } else if (comment != null && comment.userName != null && comment.userName.length() > 0) {
            hint = "回复 " + comment.userName + " 的评论...";
        }

        final LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText input = new EditText(getActivity());
        input.setHint(hint);
        input.setLines(3);
        layout.addView(input);

        final TextView clearText = new TextView(getActivity());
        clearText.setText("清空");
        clearText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clearText.setPadding(0, 8, 0, 0);
        clearText.setTextSize(14);
        clearText.setTextColor(0xFF666666);
        clearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
            }
        });
        layout.addView(clearText);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("发送回复");
        builder.setView(layout);
        builder.setPositiveButton("发送", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String text = input.getText().toString().trim();
                if (text == null || text.length() == 0) {
                    Toast.makeText(getActivity(), "回复内容不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveCurrentScrollPosition();
                long root = comment != null ? comment.rpid : 0;
                long parent = reply != null ? reply.rpid : (comment != null ? comment.rpid : 0);
                sendReply(root, parent, text);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void sendReply(final long root, final long parent, final String text) {
        if (getActivity() == null) return;

        ReplyHelper.sendReply(getActivity(), aid, root, parent, text, new ReplyHelper.ReplyCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(getActivity(), "回复发送成功", Toast.LENGTH_SHORT).show();
                refreshComments();
            }

            @Override
            public void onFailed(String error) {
                // 错误已在 ReplyHelper 中 Toast 显示
            }
        });
    }

    private String extractCsrfFromCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return null;
        }
        Pattern p = Pattern.compile("bili_jct=([a-f0-9]+)");
        Matcher m = p.matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
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

    private void showEnd() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (footerProgressBar != null) {
                    footerProgressBar.setVisibility(View.GONE);
                }
                if (footerText != null) {
                    footerText.setText(getString(R.string.emoticon__no_more_data));
                    footerText.setVisibility(View.VISIBLE);
                }
                footerView.setVisibility(View.VISIBLE);
                isLoading = false;
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

    // 显示全部回复
    public void showAllReplies(CommentItem item) {
        if (item == null || item.replies == null || item.replies.size() == 0) {
            Toast.makeText(getActivity(), "暂无回复", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ReplyListActivity.class);
        intent.putExtra("title", "全部回复（" + item.replies.size() + "条）");
        intent.putExtra("root_user_name", item.userName);
        intent.putExtra("root_comment_message", item.message);
        intent.putExtra("root_mid", item.mid);
        intent.putExtra("root_time", item.time);
        intent.putExtra("root_avatar", item.userAvatar);
        if (item.pictureList != null && item.pictureList.size() > 0) {
            intent.putExtra("root_pictures", new ArrayList<String>(item.pictureList));
        }

        String[] replyTexts = new String[item.replies.size()];
        for (int i = 0; i < item.replies.size(); i++) {
            ReplyItem ri = item.replies.get(i);
            String name = ri.userName != null ? ri.userName : "用户";
            String text = ri.message != null ? ri.message : "";
            replyTexts[i] = name + ": " + text;
        }
        intent.putExtra("replies", replyTexts);
        intent.putExtra("aid", aid);
        intent.putExtra("bvid", bvid);
        intent.putExtra("rpid", item.rpid);
        intent.putExtra("total_count", item.replyCount);
        intent.putExtra("root_like_count", item.likeCount);
        intent.putExtra("root_liked", item.liked);
        startActivity(intent);
    }

    public static class ReplyItem {
        public String userName;
        public String message;
        public long mid;
        public long rpid;
        public long root;
        public long parent;
    }

    public static class CommentItem {
        public long rpid;
        public String userName;
        public String userAvatar;
        public String message;
        public int likeCount;
        public boolean liked;
        public long time;
        public long mid;
        public int replyCount;
        public List<String> pictureList;
        public List<ReplyItem> replies;
    }
}