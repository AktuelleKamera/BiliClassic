package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_related_videos, container, false);

        listView = (ListView) view.findViewById(R.id.list_view);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        emptyView = (TextView) view.findViewById(R.id.empty_view);

        adapter = new RelatedVideosAdapter(getActivity(), videoList);
        listView.setAdapter(adapter);

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
                    Toast.makeText(getActivity(), "无法收藏该视频", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mIsFavoriteUpdating) return;
                long mid = SharedPreferencesUtil.getLong("mid", 0);
                String cookies = SharedPreferencesUtil.getString("cookies", "");
                if (mid == 0 || cookies == null || cookies.length() == 0) {
                    Toast.makeText(getActivity(), "请先登录的说~", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getActivity(), "请先登录的说~", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(getActivity(), "暂无收藏夹，请先在网页端创建", Toast.LENGTH_LONG).show();
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
                                    .setTitle("选择收藏夹")
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
                                Toast.makeText(getActivity(), "收藏好了喵～(=w=)", Toast.LENGTH_SHORT).show();
                                if (getActivity() != null) {
                                    getActivity().sendBroadcast(new Intent(BroadcastConstants.ACTION_FAVORITE_CHANGED));
                                }
                            } else if (code == 11201) {
                                Toast.makeText(getActivity(), "已收藏过该视频", Toast.LENGTH_SHORT).show();
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
            emptyView.setText("无法加载相关视频");
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
                    emptyView.setText("暂无相关视频");
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