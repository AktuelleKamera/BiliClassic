package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.util.ReplyHelper;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class CommentFragment extends Fragment {

    private static final String TAG = "CommentFragment";

    private static final Map<String, CachedComments> commentCache = new HashMap<String, CachedComments>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

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
    private boolean isRestoring = false;
    private boolean isViewCreated = false;
    private boolean isVisibleToUser = false;
    private boolean hasLoaded = false;

    private static class CachedComments {
        List<CommentItem> items;
        Set<Long> idSet;
        String nextCursor;
        boolean isEnd;
        long timestamp;
        int sortMode;
    }

    private String getCacheKey() {
        if (aid != 0) return "aid_" + aid;
        if (bvid != null) return "bvid_" + bvid;
        return null;
    }

    // 保存滚动位置（静态字段，跨 Fragment 销毁保持）
    private static int sExitScrollPosition = -1;
    private int savedScrollPosition = -1;

    // 排序控制
    private int currentSortMode = 0; // 0=时间,1=热度
    private boolean sortExplicitlySet = false;
    private TextView sortTimeBtn, sortHotBtn;

    // 评论图片上传
    private static final int MAX_COMMENT_IMAGES = 9;
    private ArrayList<String> pendingImageDataList = new ArrayList<String>();
    private TextView dialogImageBtn = null;
    private CommentItem pendingNewComment = null;
    private static final int REQUEST_PICK_COMMENT_IMAGE = 1001;

    private static class PendingLikeUpdate {
        long rpid;
        boolean liked;
        int likeCount;
        PendingLikeUpdate(long rpid, boolean liked, int likeCount) {
            this.rpid = rpid;
            this.liked = liked;
            this.likeCount = likeCount;
        }
    }
    private static PendingLikeUpdate pendingLikeUpdate = null;
    private static CommentFragment activeInstance = null;

    public static void notifyRootLikeChanged(long rpid, boolean liked, int likeCount) {
        CommentFragment instance = activeInstance;
        if (instance != null) {
            instance.applyPendingLike(rpid, liked, likeCount);
        } else {
            pendingLikeUpdate = new PendingLikeUpdate(rpid, liked, likeCount);
        }
    }

    private void applyPendingLike(long rpid, boolean liked, int likeCount) {
        for (CommentItem item : commentList) {
            if (item.rpid == rpid) {
                item.liked = liked;
                item.likeCount = likeCount;
                break;
            }
        }
        commentCache.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static final String[] EMOJIS = {
        "( ゜- ゜)つロ", "_(:з」∠)_", "（⌒▽⌒）", "（￣▽￣）", "⌓‿⌓",
        "(=・ω・=)", "(*°▽°*)", "八(*°▽°*)♪", "✿ヽ(°▽°)ノ✿", "(¦3【▓▓】",
        "눈_눈", "(ಡωಡ)", "_(≧∇≦」∠)_", "━━━∑(ﾟ□ﾟ*川", "━(｀・ω・´)",
        "(￣3￣)", "✧(≖ ◡ ≖✿)", "(･∀･)", "(〜￣△￣)〜", "→_→",
        "(°∀°)ﾉ", "╮(￣▽￣)╭", "( ´_ゝ｀)", "←_←", "(;¬_¬)",
        "(ﾟДﾟ≡ﾟдﾟ)!?", "( ´･･)ﾉ", "(._.`)", "Σ(ﾟдﾟ;)", "Σ( ￣□￣||)",
        "<(´；ω；`)", "（/TДT)/", "(^・ω・^)", "(｡･ω･｡)", "(●￣(ｴ)￣●)",
        "ε=ε=(ノ≧∇≦)ノ", "(´･_･`)", "(-_-#)", "（￣へ￣）", "(￣ε(#￣)",
        "Σ(╯°口°)╯(┴—┴", "ヽ(`Д´)ﾉ", "(\"▔□▔)/", "(º﹃º )", "(๑>؂<๑）",
        "｡ﾟ(ﾟ´Д｀)ﾟ｡", "(∂ω∂)", "(┯_┯)", "(・ω< )★", "( ๑ˊ•̥▵•)੭₎₎",
        "¥ㄟ(´･ᴗ･`)ノ¥", "Σ_(꒪ཀ꒪」∠)_", "٩(๛ ˘ ³˘)۶❤", "(๑‾᷅^‾᷅๑)"
    };

    // 上下文

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

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bvid = args.getString("bvid");
        }

        Log.e(TAG, "========== onCreateView ==========");
        Log.e(TAG, "aid=" + aid + ", bvid=" + bvid);

        adapter = new CommentAdapter(getActivity(), commentList, aid, this);
        adapter.setMid(SharedPreferencesUtil.getLong("mid", 0));
        adapter.setReplyType(1);
        adapter.setBvid(bvid);
        listView.setAdapter(adapter);

        // 排序按钮（按时间 / 按热度）
        sortTimeBtn = (TextView) view.findViewById(R.id.sort_time);
        sortHotBtn = (TextView) view.findViewById(R.id.sort_hot);
        if (sortTimeBtn != null && sortHotBtn != null) {
            sortTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSortMode = 0;
                    sortExplicitlySet = true;
                    sortTimeBtn.setTextColor(0xFFD86DA5);
                    sortHotBtn.setTextColor(0xFF999999);
                    sortAndRefreshComments();
                }
            });
            sortHotBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentSortMode = 1;
                    sortExplicitlySet = true;
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

        adapter.setOnLikeListener(new CommentAdapter.OnLikeListener() {
            @Override
            public void onLikeSuccess() {
                commentCache.clear();
            }
        });

        // 滚动监听 - 保存滚动位置
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    // 保存滚动位置
                    int firstVisible = view.getFirstVisiblePosition();
                    savedScrollPosition = firstVisible;

                    adapter.setScrolling(false);
                    int lastVisible = view.getLastVisiblePosition();
                    int totalCount = adapter.getCount();
                    if (lastVisible >= totalCount - 1 && !isLoading && !isEnd && !isRestoring && totalCount > 0) {
                        loadMoreComments();
                    }
                } else {
                    adapter.setScrolling(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && !isRestoring && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreComments();
                    }
                }
            }
        });

        // 检查是否有保存的滚动位置
        if (savedInstanceState != null) {
            savedScrollPosition = savedInstanceState.getInt("savedScrollPosition", -1);
        }

        isViewCreated = true;
        lazyLoad();

        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        this.isVisibleToUser = isVisibleToUser;
        if (isViewCreated) {
            lazyLoad();
        }
    }

    private void lazyLoad() {
        if (isVisibleToUser && !hasLoaded) {
            hasLoaded = true;
            loadComments();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("savedScrollPosition", savedScrollPosition);
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
        if (pendingLikeUpdate != null) {
            applyPendingLike(pendingLikeUpdate.rpid, pendingLikeUpdate.liked, pendingLikeUpdate.likeCount);
            pendingLikeUpdate = null;
        }
        if (!isRestoring) {
            restoreScrollPosition();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveCurrentScrollPosition();
        sExitScrollPosition = savedScrollPosition;
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQUEST_PICK_COMMENT_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            final android.net.Uri imageUri = data.getData();
            if (imageUri != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final android.app.Activity activity = getActivity();
                        if (activity == null) return;
                        try {
                            java.io.InputStream is = activity.getContentResolver().openInputStream(imageUri);
                            if (is == null) return;
                            byte[] imageBytes = NetWorkUtil.readStream(is);
                            is.close();

                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);

                            int scale = 1;
                            if (opts.outWidth > 1920 || opts.outHeight > 1080) {
                                scale = Math.max(opts.outWidth / 1920, opts.outHeight / 1080);
                            }

                            opts = new BitmapFactory.Options();
                            opts.inSampleSize = scale;
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);

                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                            byte[] compressed = baos.toByteArray();
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();

                            final String fileName = "comment_" + System.currentTimeMillis() + ".jpg";
                            final String resultJson = ReplyApi.uploadReplyImage(aid != 0 ? aid : 0, compressed, fileName);

                            if (resultJson != null && pendingImageDataList.size() < MAX_COMMENT_IMAGES) {
                                pendingImageDataList.add(resultJson);
                                final int imgCount = pendingImageDataList.size();
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (dialogImageBtn != null) {
                                            dialogImageBtn.setText("图片(" + imgCount + ")");
                                        }
                                        Toast.makeText(activity, "图片已上传 (" + imgCount + ")", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity, "图片上传失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } catch (final Exception e) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(activity, "图片上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onStop() {
        super.onStop();
        saveCurrentScrollPosition();
        sExitScrollPosition = savedScrollPosition;
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
        if (savedScrollPosition < 0 || listView == null || adapter == null) return;
        if (savedScrollPosition >= adapter.getCount()) {
            savedScrollPosition = Math.max(0, adapter.getCount() - 1);
        }
        listView.setSelectionFromTop(savedScrollPosition, 0);
    }

    // 保存当前滚动位置
    private void saveCurrentScrollPosition() {
        if (listView == null) return;
        savedScrollPosition = listView.getFirstVisiblePosition();
    }

    // 刷新评论（供外部调用）
    public void refreshComments() {
        if (isLoading) return;
        String cacheKey = getCacheKey();
        if (cacheKey != null) {
            commentCache.remove(cacheKey);
        }
        nextCursor = "";
        isEnd = false;
        commentList.clear();
        commentIdSet.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        savedScrollPosition = 0;
        hasLoaded = true;
        loadComments();
    }

    private void applyCurrentSort() {
        if (commentList == null || commentList.size() < 2) return;
        // Separate pinned comments from the rest
        final List<CommentItem> pinned = new ArrayList<CommentItem>();
        final List<CommentItem> others = new ArrayList<CommentItem>();
        for (CommentItem item : commentList) {
            if (item.isTop) {
                pinned.add(item);
            } else {
                others.add(item);
            }
        }
        java.util.Collections.sort(others, new java.util.Comparator<CommentItem>() {
            @Override
            public int compare(CommentItem a, CommentItem b) {
                if (currentSortMode == 1) {
                    return Integer.valueOf(b.likeCount).compareTo(Integer.valueOf(a.likeCount));
                } else {
                    return Long.valueOf(b.time).compareTo(Long.valueOf(a.time));
                }
            }
        });
        commentList.clear();
        commentList.addAll(pinned);
        commentList.addAll(others);
    }

    private void sortAndRefreshComments() {
        if (commentList == null || commentList.size() == 0) {
            refreshComments();
            return;
        }
        applyCurrentSort();
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

        String cacheKey = getCacheKey();
        CachedComments cached = cacheKey != null ? commentCache.get(cacheKey) : null;

        // 用跨 Fragment 销毁的静态退出位置恢复滚动（bundle 可能因 setAdapter(null) 丢失）
        if (savedScrollPosition < 0 && sExitScrollPosition >= 0) {
            savedScrollPosition = sExitScrollPosition;
        }

        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            commentList.clear();
            commentIdSet.clear();
            commentList.addAll(cached.items);
            commentIdSet.addAll(cached.idSet);
            nextCursor = cached.nextCursor;
            isEnd = cached.isEnd;
            // 恢复排序状态但不重新排序（缓存数据已排好序）
            if (cached.sortMode == 1) {
                currentSortMode = 1;
                sortExplicitlySet = true;
            } else {
                currentSortMode = 0;
            }
            // 更新排序按钮颜色
            if (sortTimeBtn != null && sortHotBtn != null) {
                if (currentSortMode == 1) {
                    sortTimeBtn.setTextColor(0xFF999999);
                    sortHotBtn.setTextColor(0xFFD86DA5);
                } else {
                    sortTimeBtn.setTextColor(0xFFD86DA5);
                    sortHotBtn.setTextColor(0xFF999999);
                }
            }

            progressBar.setVisibility(View.GONE);
            adapter.updateData(commentList);
            if (commentList.size() == 0) {
                emptyView.setText("暂无评论");
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
            isRestoring = true;
            restoreScrollPosition();
            listView.post(new Runnable() {
                @Override
                public void run() {
                    isRestoring = false;
                }
            });
            return;
        }

        isLoading = true;
        nextCursor = "";
        isEnd = false;

        commentList.clear();
        commentIdSet.clear();

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        footerView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                fetchCommentsFromNetwork();
            }
        }).start();
    }

    private void fetchCommentsFromNetwork() {
        final String oidParam;
        if (aid != 0) {
            oidParam = String.valueOf(aid);
            Log.e(TAG, "使用 aid 请求评论: " + oidParam);
        } else {
            oidParam = bvid;
            Log.e(TAG, "直接使用 bvid 请求评论: " + oidParam);
        }

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

            JSONObject json = NetWorkUtil.getJsonStream(url, headers);
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
                    JSONArray topReplies = data.optJSONArray("top_replies");
                    boolean isBegin = cursor != null && cursor.optBoolean("is_begin", false);
                    if (topReplies != null && topReplies.length() > 0 && isBegin) {
                        if (replies != null && replies.length() > 0) {
                            JSONArray merged = new JSONArray();
                            for (int t = 0; t < topReplies.length(); t++) {
                                merged.put(topReplies.get(t));
                            }
                            for (int r = 0; r < replies.length(); r++) {
                                merged.put(replies.get(r));
                            }
                            replies = merged;
                        } else {
                            replies = topReplies;
                        }
                    }
                    if (replies != null && replies.length() > 0) {
                        Log.e(TAG, "获取到 " + replies.length() + " 条评论");
                        commentIdSet.clear();
                        List<CommentItem> items = parseFirstComments(replies);
                        if (items != null) {
                            saveCommentCache(items);
                            applyCommentList(items);
                        }
                    } else {
                        Log.e(TAG, "没有评论");
                        showEmpty("暂无评论");
                    }
                } else {
                    Log.e(TAG, "data 为 null");
                    showEmpty("暂无评论");
                }
            } else if (code == 12002 || (message != null && message.contains("关闭"))) {
                Log.e(TAG, "UP 已关闭评论区");
                showEmpty("UP已关闭评论区");
            } else {
                Log.e(TAG, "API 返回错误: " + message);
                showError("加载失败: " + message);
            }
        } catch (final OutOfMemoryError e) {
            Log.e(TAG, "评论数据过大，内存不足");
            showError("评论数据过大，无法加载");
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

    private void applyCommentList(final List<CommentItem> items) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                commentList.clear();
                commentList.addAll(items);
                if (sortExplicitlySet) {
                    applyCurrentSort();
                }
                // Re-pin the user's newly created comment to the top
                if (pendingNewComment != null) {
                    long targetRpid = pendingNewComment.rpid;
                    CommentItem existing = null;
                    for (CommentItem item : commentList) {
                        if (item.rpid == targetRpid) {
                            existing = item;
                            break;
                        }
                    }
                    if (existing != null) {
                        commentList.remove(existing);
                    }
                    int insertPos = 0;
                    // Find how many pinned comments are at the start
                    while (insertPos < commentList.size() && commentList.get(insertPos).isTop) {
                        insertPos++;
                    }
                    if (existing != null) {
                        commentList.add(insertPos, existing);
                    } else {
                        commentList.add(insertPos, pendingNewComment);
                    }
                    pendingNewComment = null;
                }
                isRestoring = true;
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
                restoreScrollPosition();
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        isRestoring = false;
                    }
                });
            }
        });
    }

    private void saveCommentCache(List<CommentItem> items) {
        String cacheKey = getCacheKey();
        if (cacheKey == null) return;
        saveCurrentScrollPosition();
        CachedComments cached = new CachedComments();
        cached.items = new ArrayList<CommentItem>(items);
        cached.idSet = new HashSet<Long>(commentIdSet);
        cached.nextCursor = nextCursor;
        cached.isEnd = isEnd;
        cached.timestamp = System.currentTimeMillis();
        cached.sortMode = currentSortMode;
        commentCache.put(cacheKey, cached);
    }

    private List<CommentItem> parseFirstComments(JSONArray replies) throws Exception {
        List<CommentItem> items = new ArrayList<CommentItem>();

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
                JSONObject replyCtrl = reply.optJSONObject("reply_control");
                item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);

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

        return items;
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

                    JSONObject json = NetWorkUtil.getJsonStream(url, headers);
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
                } catch (final OutOfMemoryError e) {
                    Log.e(TAG, "评论数据过大，内存不足");
                    showLoadMoreError("评论数据过大");
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
                JSONObject replyCtrl = reply.optJSONObject("reply_control");
                item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);

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
                if (sortExplicitlySet) {
                    applyCurrentSort();
                }
                adapter.updateData(commentList);
                saveCommentCache(commentList);

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
        final boolean isNewComment = (comment == null);

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
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 14) {
            android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
            inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            inputBg.setStroke(dpToPx(2), 0xFFD0D0D0);
            inputBg.setColor(0xFFFFFFFF);
            input.setBackgroundDrawable(inputBg);
        }

        // 顶部按钮行：图片（仅根评论）+ 表情
        final LinearLayout btnRow = new LinearLayout(getActivity());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, dpToPx(6));

        if (isNewComment) {
            final TextView imageBtn = new TextView(getActivity());
            imageBtn.setText("添加图片");
            imageBtn.setTextSize(13);
            imageBtn.setTextColor(0xFFD86DA5);
            imageBtn.setPadding(0, 0, dpToPx(12), 0);
            imageBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
            imageBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pendingImageDataList.size() >= MAX_COMMENT_IMAGES) {
                        Toast.makeText(getActivity(), "最多上传" + MAX_COMMENT_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    pickIntent.setType("image/*");
                    startActivityForResult(pickIntent, REQUEST_PICK_COMMENT_IMAGE);
                }
            });
            btnRow.addView(imageBtn);
            dialogImageBtn = imageBtn;
            if (pendingImageDataList.size() > 0) {
                imageBtn.setText("图片(" + pendingImageDataList.size() + ")");
            }
        }

        final TextView emojiBtn = new TextView(getActivity());
        emojiBtn.setText("表情");
        emojiBtn.setTextSize(13);
        emojiBtn.setTextColor(0xFFD86DA5);
        emojiBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        emojiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmojiPicker(input);
            }
        });
        btnRow.addView(emojiBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(btnRow, lp);
        layout.addView(input, lp);

        final TextView clearText = new TextView(getActivity());
        clearText.setText("清空");
        clearText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clearText.setPadding(0, 8, 0, 0);
        clearText.setTextSize(14);
        clearText.setTextColor(0xFF666666);
        clearText.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        clearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
            }
        });
        layout.addView(clearText, lp);

        final AlertDialog dialog = new AlertDialog.Builder(DialogUtil.wrap(getActivity()))
                .setTitle(isNewComment ? "发评论" : "发送回复")
                .setView(layout)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pendingImageDataList.clear();
                        d.dismiss();
                    }
                })
                .create();

        dialog.show();
        System.gc();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text == null || text.length() == 0) {
                    Toast.makeText(getActivity(), "内容不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveCurrentScrollPosition();
                dialog.dismiss();

                if (isNewComment) {
                    if (pendingImageDataList.size() > 0) {
                        String picturesJson = buildPicturesJson();
                        pendingImageDataList.clear();
                        ReplyHelper.sendReplyWithPictures(getActivity(), aid, 0, 0, text, picturesJson,
                                new ReplyHelper.ReplyCallback() {
                            @Override
                            public void onSuccess(String responseJson) {
                                Toast.makeText(getActivity(), "评论发送成功", Toast.LENGTH_SHORT).show();
                                pendingNewComment = parseCommentFromResponse(responseJson);
                                refreshComments();
                            }
                            @Override
                            public void onFailed(String error) {}
                        });
                    } else {
                        ReplyHelper.sendReply(getActivity(), aid, 0, 0, text, new ReplyHelper.ReplyCallback() {
                            @Override
                            public void onSuccess(String responseJson) {
                                Toast.makeText(getActivity(), "评论发送成功", Toast.LENGTH_SHORT).show();
                                pendingNewComment = parseCommentFromResponse(responseJson);
                                refreshComments();
                            }
                            @Override
                            public void onFailed(String error) {}
                        });
                    }
                } else {
                    long root = comment.rpid;
                    long parent = reply != null ? reply.rpid : comment.rpid;
                    ReplyHelper.sendReply(getActivity(), aid, root, parent, text, new ReplyHelper.ReplyCallback() {
                        @Override
                        public void onSuccess(String responseJson) {
                            Toast.makeText(getActivity(), "回复发送成功", Toast.LENGTH_SHORT).show();
                            refreshComments();
                        }
                        @Override
                        public void onFailed(String error) {}
                    });
                }
            }
        });
    }

    private String buildPicturesJson() {
        try {
            JSONArray pics = new JSONArray();
            for (int i = 0; i < pendingImageDataList.size(); i++) {
                JSONObject imgData = new JSONObject(pendingImageDataList.get(i));
                JSONObject pic = new JSONObject();
                pic.put("img_src", imgData.optString("image_url", ""));
                pic.put("img_width", imgData.optInt("image_width", 0));
                pic.put("img_height", imgData.optInt("image_height", 0));
                pic.put("img_size", imgData.optDouble("img_size", 0));
                pics.put(pic);
            }
            return pics.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void showEmojiPicker(final EditText input) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(DialogUtil.wrap(getActivity()));
        builder.setTitle("选择表情");

        final android.widget.ScrollView scroll = new android.widget.ScrollView(getActivity());
        final android.widget.LinearLayout list = new android.widget.LinearLayout(getActivity());
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        list.setPadding(0, dpToPx(8), 0, dpToPx(8));

        for (int i = 0; i < EMOJIS.length; i++) {
            final String emoji = EMOJIS[i];
            final android.widget.TextView tv = new android.widget.TextView(getActivity());
            tv.setText(emoji);
            tv.setTextSize(16);
            tv.setTextColor(0xFF333333);
            tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            tv.setClickable(true);
            android.graphics.drawable.GradientDrawable emojiNormal = new android.graphics.drawable.GradientDrawable();
            emojiNormal.setColor(0xFFF0F0F0);
            android.graphics.drawable.GradientDrawable emojiPressed = new android.graphics.drawable.GradientDrawable();
            emojiPressed.setColor(0x40D86DA5);
            android.graphics.drawable.StateListDrawable emojiBg = new android.graphics.drawable.StateListDrawable();
            emojiBg.addState(new int[]{android.R.attr.state_pressed}, emojiPressed);
            emojiBg.addState(new int[]{}, emojiNormal);
            tv.setBackgroundDrawable(emojiBg);
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int remaining = 1000 - input.length();
                    if (remaining <= 0) return;
                    String toInsert = emoji.length() <= remaining ? emoji : emoji.substring(0, remaining);
                    int pos = input.getSelectionStart();
                    if (pos < 0) pos = input.length();
                    input.getText().insert(pos, toInsert);
                }
            });
            list.addView(tv);

            if (i < EMOJIS.length - 1) {
                android.view.View divider = new android.view.View(getActivity());
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFDDDDDD);
                list.addView(divider);
            }
        }

        scroll.addView(list, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        builder.setView(scroll);
        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    private int dpToPx(int dp) {
        if (getActivity() == null) return dp;
        float density = getActivity().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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
                footerView.setVisibility(View.VISIBLE);
                if ("没有更多评论".equals(msg)) {
                    footerText.setText(getString(R.string.emoticon__no_more_data));
                } else {
                    footerText.setText(msg);
                }
                footerText.setVisibility(View.VISIBLE);
            }
        });
    }

    public void insertNewComment(CommentItem item) {
        pendingNewComment = item;
        refreshComments();
    }

    public static CommentItem parseCommentFromResponse(String responseJson) {
        try {
            JSONObject result = new JSONObject(responseJson);
            if (result.optInt("code", -1) != 0) return null;
            JSONObject data = result.optJSONObject("data");
            if (data == null) return null;
            JSONObject reply = data.optJSONObject("reply");
            if (reply == null) return null;

            long replyId = reply.optLong("rpid", 0);
            if (replyId == 0) return null;

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
            JSONObject replyCtrl = reply.optJSONObject("reply_control");
            item.isTop = replyCtrl != null && replyCtrl.optBoolean("is_up_top", false);
            item.replies = null;
            return item;
        } catch (Exception e) {
            return null;
        }
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
        intent.putExtra("root_is_top", item.isTop);
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
        public boolean isTop;
        public long time;
        public long mid;
        public int replyCount;
        public List<String> pictureList;
        public List<ReplyItem> replies;
    }
}