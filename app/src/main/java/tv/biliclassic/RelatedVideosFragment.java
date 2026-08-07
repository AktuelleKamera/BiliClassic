package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
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
import java.util.List;

import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.BroadcastConstants;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.StringUtil;

public class RelatedVideosFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;

    private RelatedVideosAdapter adapter;
    private List<VideoCard> videoList = new ArrayList<VideoCard>();

    private long aid;
    private String bvid;

    // 收藏防连点
    private boolean mIsFavoriteUpdating = false;

    // 键盘光标选中的项，-1 表示无选中
    private int selectedPosition = -1;

    /**
     * 供 VideoDetailActivity.dispatchKeyEvent 调用：
     * 方向键在相关视频列表内上下移动光标（选中高亮），确认键打开视频。
     * 返回 true 表示事件已被消费。
     */
    public boolean handleRemoteKey(KeyEvent event) {
        if (videoList == null || videoList.size() == 0 || listView == null) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = KeyBindingUtil.classify(event.getKeyCode());
        // 只处理 UP/DOWN/CONFIRM 与翻页键（2/8），其他键不消费（交给上层）
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM
                && action != KeyBindingUtil.ACTION_NUM_2
                && action != KeyBindingUtil.ACTION_NUM_8) {
            return false;
        }
        if (selectedPosition < 0) {
            selectedPosition = 0;
        }
        // 按键恢复：取消触摸滑动时的隐藏，重新显示光标并滚回选中项
        if (adapter != null) {
            adapter.setHideHighlight(false);
        }
        // 首次按下才移动光标；长按 repeat 只消费不移动，防止 ListView 内置滚动干扰
        if (event.getRepeatCount() == 0) {
            int count = videoList.size();
            if (action == KeyBindingUtil.ACTION_UP) {
                selectedPosition = Math.max(0, selectedPosition - 1);
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                selectedPosition = Math.min(count - 1, selectedPosition + 1);
            } else if (action == KeyBindingUtil.ACTION_NUM_2) {
                selectedPosition = pageMove(-1);
            } else if (action == KeyBindingUtil.ACTION_NUM_8) {
                selectedPosition = pageMove(1);
            } else if (action == KeyBindingUtil.ACTION_CONFIRM) {
                VideoCard video = videoList.get(selectedPosition);
                if (video != null) {
                    Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                    intent.putExtra("aid", video.aid);
                    intent.putExtra("bvid", video.bvid);
                    startActivity(intent);
                }
                return true;
            }
            applySelection();
        }
        // 所有 DOWN 事件都消费（含长按 repeat），避免列表自身滚动导致"回顶"
        return true;
    }

    /**
     * 数字键 2/8：按一屏（当前可见项数）快速翻页。
     * direction=-1 向上翻，+1 向下翻；返回新位置（已做边界钳制）。
     */
    private int pageMove(int direction) {
        if (listView == null) {
            return selectedPosition;
        }
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        int visibleCount = Math.max(1, last - first + 1);
        int newPos = selectedPosition + direction * visibleCount;
        int count = videoList.size();
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos >= count) {
            newPos = count - 1;
        }
        return newPos;
    }

    private void applySelection() {
        // 先刷新高亮，再立即定位到选中项（setSelection 无动画竞争，不会回顶）
        if (adapter != null) {
            adapter.setSelectedPosition(selectedPosition);
        }
        if (listView != null) {
            listView.setSelection(selectedPosition);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_related_videos, container, false);

        listView = (ListView) view.findViewById(R.id.list_view);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        adapter = new RelatedVideosAdapter(getActivity(), videoList);
        listView.setAdapter(adapter);

        // 隐藏原生 selector，避免覆盖自定义光标高亮（粉色）
        listView.setSelector(android.R.color.transparent);
        listView.setCacheColorHint(0x00000000);

        // 滚动中暂缓封面应用，避免滑到时封面一张张到达触发整屏重绘
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    adapter.setScrolling(false);
                    // 滚动结束：保持高亮隐藏，等待再次按键恢复
                } else {
                    adapter.setScrolling(true);
                    // 开始触摸滚动/甩动：隐藏光标高亮
                    adapter.setHideHighlight(true);
                }
            }
        });

        adapter.setOnVideoClickListener(new RelatedVideosAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(VideoCard video, int position) {
                if (video == null) return;
                Intent intent = new Intent(getActivity(), VideoDetailActivity.class);
                intent.putExtra("aid", video.aid);
                intent.putExtra("bvid", video.bvid);
                startActivity(intent);
            }
        });

        adapter.setOnVideoLongClickListener(new RelatedVideosAdapter.OnVideoLongClickListener() {
            @Override
            public void onVideoLongClick(VideoCard video, int position) {
                if (video == null || video.aid == 0) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_65e0), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mIsFavoriteUpdating) return;
                long mid = SharedPreferencesUtil.getLong("mid", 0);
                String cookies = SharedPreferencesUtil.getString("cookies", "");
                if (mid == 0 || cookies == null || cookies.length() == 0) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_8bf7), Toast.LENGTH_SHORT).show();
                    return;
                }
                showFavoriteDialog(video.aid);
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bvid = args.getString("bvid");
        }

        loadRelatedVideos();

        return view;
    }

    private void showFavoriteDialog(final long aid) {
        if (aid == 0) return;

        final long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) {
            Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_8bf7), Toast.LENGTH_SHORT).show();
            return;
        }

        mIsFavoriteUpdating = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList folders = FavoriteApi.getFavoriteFoldersFast(mid);

                    if (getActivity() == null) return;

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsFavoriteUpdating = false;
                            if (folders == null || folders.size() == 0) {
                                Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_6682), Toast.LENGTH_LONG).show();
                                return;
                            }

                            final String[] folderNames = new String[folders.size()];
                            final long[] folderIds = new long[folders.size()];
                            for (int i = 0; i < folders.size(); i++) {
                                FavoriteFolder folder = (FavoriteFolder) folders.get(i);
                                folderNames[i] = folder.name + " (" + folder.videoCount + "个视频)";
                                folderIds[i] = folder.fid;
                            }

                            new AlertDialog.Builder(DialogUtil.wrap(getActivity()))
                                    .setTitle(getString(R.string.relatedvideosfragment_settitle_9009))
                                    .setItems(folderNames, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            long fid = folderIds[which];
                                            addToFavorite(aid, fid);
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsFavoriteUpdating = false;
                            Toast.makeText(getActivity(), "加载收藏夹失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void addToFavorite(final long aid, final long fid) {
        if (aid == 0 || mIsFavoriteUpdating) return;

        mIsFavoriteUpdating = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = FavoriteApi.addFavorite(aid, null, fid);

                    if (getActivity() == null) return;

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsFavoriteUpdating = false;
                            if (code == 0) {
                                Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_6536), Toast.LENGTH_SHORT).show();
                                if (getActivity() != null) {
                                    getActivity().sendBroadcast(new Intent(BroadcastConstants.ACTION_FAVORITE_CHANGED));
                                }
                            } else if (code == 11201) {
                                Toast.makeText(getActivity(), getActivity().getString(R.string.relatedvideosfragment_toast_5df2), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getActivity(), "收藏失败喵: " + code, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsFavoriteUpdating = false;
                            Toast.makeText(getActivity(), "收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearAll();
    }

    public void clearImages() {
        if (adapter != null) {
            adapter.clearCache();
        }
        if (videoList != null) {
            videoList.clear();
        }
    }

    public void clearAll() {
        if (adapter != null) {
            adapter.clearCache();
            adapter = null;
        }
        if (videoList != null) {
            videoList.clear();
        }
        if (listView != null) {
            listView.setAdapter(null);
        }
    }

    private void loadRelatedVideos() {
        if (aid == 0 && (bvid == null || bvid.length() == 0)) {
            emptyView.setText(getString(R.string.relatedvideosfragment_settext_65e0));
            emptyView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url;
                    if (aid != 0) {
                        url = "https://api.bilibili.com/x/web-interface/archive/related?aid=" + aid;
                    } else {
                        url = "https://api.bilibili.com/x/web-interface/archive/related?bvid=" + bvid;
                    }

                    android.util.Log.e("RelatedVideos", "请求URL: " + url);

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
                        showError("网络返回为空");
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);

                    if (code == 0) {
                        JSONArray data = json.optJSONArray("data");
                        if (data != null && data.length() > 0) {
                            parseRelatedVideos(data);
                        } else {
                            showEmptyResult("暂无相关视频");
                        }
                    } else {
                        final String message = json.optString("message", "加载失败");
                        showError(message);
                    }
                } catch (final Exception e) {
                    final String errorMsg = e.getMessage();
                    if (isAdded()) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getView() == null) {
                                    return;
                                }
                                progressBar.setVisibility(View.GONE);
                                emptyView.setText("加载失败: " + errorMsg);
                                emptyView.setVisibility(View.VISIBLE);
                                Toast.makeText(getActivity(), "加载失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void parseRelatedVideos(JSONArray data) throws Exception {
        final List<VideoCard> items = new ArrayList<VideoCard>();

        for (int i = 0; i < data.length(); i++) {
            try {
                JSONObject obj = data.getJSONObject(i);
                if (obj == null) continue;

                VideoCard video = new VideoCard();
                video.title = obj.optString("title", "未知标题");

                String coverUrl = obj.optString("pic", "");
                if (coverUrl != null && coverUrl.startsWith("https://")) {
                    coverUrl = "http://" + coverUrl.substring(8);
                }
                video.cover = coverUrl;

                JSONObject owner = obj.optJSONObject("owner");
                if (owner != null) {
                    video.upName = owner.optString("name", "未知UP主");
                } else {
                    video.upName = "未知UP主";
                }

                JSONObject stat = obj.optJSONObject("stat");
                if (stat != null) {
                    video.view = StringUtil.toWan(stat.optLong("view", 0)) + "播放";
                } else {
                    video.view = "0播放";
                }

                video.aid = obj.optLong("aid", 0);
                video.bvid = obj.optString("bvid", "");

                if (video.aid != 0) {
                    items.add(video);
                }
            } catch (Exception e) {
                android.util.Log.e("RelatedVideos", "解析单个视频失败: " + e.getMessage());
            }
        }

        if (getActivity() == null) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                videoList.clear();
                videoList.addAll(items);
                if (adapter != null) {
                    adapter.updateData(videoList);
                }

                if (videoList.size() == 0) {
                    emptyView.setText(getString(R.string.relatedvideosfragment_settext_6682));
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    emptyView.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showError(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) return;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyResult(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) return;
                progressBar.setVisibility(View.GONE);
                emptyView.setText(msg);
                emptyView.setVisibility(View.VISIBLE);
            }
        });
    }
}