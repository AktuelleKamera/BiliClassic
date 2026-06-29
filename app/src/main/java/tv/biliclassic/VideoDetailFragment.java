package tv.biliclassic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.api.VideoInfoApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.model.Stats;
import tv.biliclassic.model.VideoInfo;
import tv.biliclassic.model.VideoPart;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.player.PlayerAnimActivity;
import tv.biliclassic.player.BiliPlayerActivity;

public class VideoDetailFragment extends Fragment {

    public static class VideoPage {
        public long cid;
        public String title;
        public int page;
    }

    private static final int QUALITY_360P = 16;
    private static final int QUALITY_720P = 64;

    private ImageView ivCover;
    private TextView tvTitle, tvUpName, tvUpNameNew, tvPlay, tvDanmaku, tvDesc, tvPartCount;
    private Button btnPlay;
    private ObservableListView lvParts;
    private LinearLayout tagsContainer;

    private VideoPartAdapter partAdapter;
    private ArrayList<VideoPart> partList = new ArrayList<VideoPart>();
    private List<VideoPage> videoPages = new ArrayList<VideoPage>();

    private long aid;
    private String bvid;
    private VideoInfo videoInfo;
    private int currentPartIndex = 0;
    private String[] tags = {"", "", "", "", "", "", "", "", ""};

    private boolean mOfflineMode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_detail, container, false);

        ivCover = (ImageView) view.findViewById(R.id.iv_cover);
        tvTitle = (TextView) view.findViewById(R.id.tv_title);
        tvUpName = (TextView) view.findViewById(R.id.tv_up_name);
        tvUpNameNew = (TextView) view.findViewById(R.id.tv_up_name_new);
        tvPlay = (TextView) view.findViewById(R.id.tv_play);
        tvDanmaku = (TextView) view.findViewById(R.id.tv_danmaku);
        tvDesc = (TextView) view.findViewById(R.id.tv_desc);
        tvPartCount = (TextView) view.findViewById(R.id.tv_part_count);
        btnPlay = (Button) view.findViewById(R.id.btn_play);
        lvParts = (ObservableListView) view.findViewById(R.id.lv_parts);
        tagsContainer = (LinearLayout) view.findViewById(R.id.tags_container);

        int underlineColor = 0xFFFF6699;
        tvUpNameNew.setPaintFlags(tvUpNameNew.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvUpNameNew.setTextColor(underlineColor);

        Bundle args = getArguments();
        if (args != null) {
            aid = args.getLong("aid", 0);
            bvid = args.getString("bvid");
            mOfflineMode = args.getBoolean("offline_mode", false);
        }

        partAdapter = new VideoPartAdapter(getActivity(), partList);
        lvParts.setAdapter(partAdapter);

        lvParts.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                currentPartIndex = position;
                partAdapter.setSelectedPosition(position);
                playVideo();
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });

        showNullTag();
        loadVideoData();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearImages();
    }

    public void clearImages() {
        if (ivCover != null) {
            try {
                ivCover.setImageBitmap(null);
                ivCover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (partList != null) {
            partList.clear();
        }
        if (videoPages != null) {
            videoPages.clear();
        }
        if (lvParts != null) {
            lvParts.setAdapter(null);
        }
    }

    private void showNullTag() {
        if (!isAdded() || getActivity() == null) return;
        if (tagsContainer == null) return;
        tagsContainer.removeAllViews();
        TextView nullTag = createTagView("null");
        nullTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAdded() && getActivity() != null) searchTag("null");
            }
        });
        tagsContainer.addView(nullTag);
    }

    private TextView createTagView(String text) {
        if (!isAdded() || getActivity() == null) return new TextView(getActivity());
        TextView tagView = new TextView(getActivity());
        tagView.setText(text);
        tagView.setTextColor(0xFFFF6699);
        tagView.setTextSize(12);
        tagView.setPadding(0, 0, 12, 0);
        tagView.setPaintFlags(tagView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        return tagView;
    }

    private void searchTag(String keyword) {
        if (!isAdded() || getActivity() == null) return;
        if (keyword == null || keyword.length() == 0) return;
        Intent intent = new Intent(getActivity(), SearchActivity.class);
        intent.putExtra("keyword", keyword);
        startActivity(intent);
    }

    public List<VideoPage> getVideoPages() {
        return videoPages;
    }

    // ========== 下载：准备阶段（解析视频地址+获取可用画质） ==========

    /**
     * 准备下载指定分P：
     * 1. 解析视频地址（使用默认画质）
     * 2. 获取可用画质列表
     * 3. 调用 Activity 的画质选择 + 下载服务启动流程
     */
    /**
     * 准备下载指定分P（带画质参数）
     */
    public void prepareDownload(final VideoPage page, final int quality, final String qualityName) {
        if (!isAdded() || getActivity() == null) return;
        if (page == null) {
            Toast.makeText(getActivity(), "分P信息为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (page.cid == 0) {
            Toast.makeText(getActivity(), "无法获取视频ID", Toast.LENGTH_SHORT).show();
            return;
        }

        final long realAid = (videoInfo != null && videoInfo.aid != 0) ? videoInfo.aid : aid;
        if (realAid == 0) {
            Toast.makeText(getActivity(), "无法获取视频ID", Toast.LENGTH_SHORT).show();
            return;
        }

        final String upName = (videoInfo != null && videoInfo.staff != null && videoInfo.staff.size() > 0)
                ? videoInfo.staff.get(0).name : "";
        final String coverUrl = (videoInfo != null) ? videoInfo.cover : "";
        final String desc = (videoInfo != null && videoInfo.description != null) ? videoInfo.description : "";
        final String tagsStr = (videoInfo != null && videoInfo.tags != null) ? videoInfo.tags : "";
        final String mainTitle = (videoInfo != null) ? videoInfo.title : page.title;
        final String bvidStr = (videoInfo != null) ? videoInfo.bvid : null;

        Toast.makeText(getActivity(), "正在获取下载地址...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerData playerData = new PlayerData();
                    playerData.aid = realAid;
                    playerData.cid = page.cid;
                    playerData.title = page.title;
                    playerData.qn = quality;

                    PlayerApi.getVideo(playerData, true);
                    final String videoUrl = playerData.videoUrl;

                    final long tempAid = realAid;
                    final String tempTitle = mainTitle;
                    final String tempPageTitle = page.title;
                    final long tempCid = page.cid;
                    final int tempPage = page.page;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                if (videoUrl != null && videoUrl.length() > 0) {
                                    if (getActivity() instanceof VideoDetailActivity) {
                                        ((VideoDetailActivity) getActivity()).startDownloadDirect(
                                                videoUrl, tempTitle, tempPageTitle,
                                                tempAid, tempCid, tempPage,
                                                quality, qualityName,
                                                coverUrl, upName, bvidStr, desc, tagsStr);
                                    }
                                } else {
                                    Toast.makeText(getActivity(), "获取下载地址失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                Toast.makeText(getActivity(), "获取下载地址失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }).start();
    }
    private int getSafeQuality() {
        int preferred = SettingsActivity.getVideoQuality();
        if (videoInfo != null && videoInfo.qualities != null && videoInfo.qualities.size() > 0) {
            List<Integer> available = videoInfo.qualities;
            if (available.contains(preferred)) return preferred;
            if (preferred == QUALITY_720P && available.contains(QUALITY_360P)) return QUALITY_360P;
            int highest = available.get(0);
            return highest;
        }
        return preferred;
    }

    // ========== 数据加载 ==========

    private void loadVideoData() {
        if (!isAdded() || getActivity() == null) return;
        tvTitle.setText("加载中...");

        if (mOfflineMode) {
            loadVideoDataFromOffline();
            return;
        }

        final long finalAid = aid;
        final String finalBvid = bvid;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (finalAid != 0) {
                        videoInfo = VideoInfoApi.getVideoInfo(finalAid);
                        loadTags(finalAid);
                    } else if (finalBvid != null && finalBvid.length() > 0) {
                        videoInfo = VideoInfoApi.getVideoInfo(finalBvid);
                        loadTags(finalBvid);
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded() || getActivity() == null) return;
                                    tvTitle.setText("参数错误");
                                    Toast.makeText(getActivity(), "缺少视频参数", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        return;
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                displayVideoInfo();
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                tvTitle.setText("加载失败");
                                Toast.makeText(getActivity(), "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 离线模式：从本地 entry.json 加载视频信息
     */
    private void loadVideoDataFromOffline() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File downloadDir = new java.io.File(
                            android.os.Environment.getExternalStorageDirectory(), "BiliClassic/Download");
                    if (!downloadDir.isDirectory()) {
                        downloadDir = new java.io.File(getActivity().getFilesDir(), "Download");
                    }

                    tv.biliclassic.download.VideoDownloadEnvironment env =
                            new tv.biliclassic.download.VideoDownloadEnvironment(downloadDir);
                    final java.util.ArrayList<tv.biliclassic.download.VideoDownloadEntry> entries =
                            env.loadEntriesForAvid(aid);

                    // 用第一个 entry 构建 VideoInfo
                    videoInfo = new VideoInfo();
                    videoInfo.aid = aid;
                    if (entries != null && entries.size() > 0) {
                        tv.biliclassic.download.VideoDownloadEntry firstEntry = entries.get(0);
                        videoInfo.title = firstEntry.title != null ? firstEntry.title : "av" + aid;
                        videoInfo.cover = firstEntry.coverUrl;
                        videoInfo.description = firstEntry.description;
                        videoInfo.tags = firstEntry.tags;
                        videoInfo.pagenames = new java.util.ArrayList<String>();
                        videoInfo.cids = new java.util.ArrayList<Long>();
                        videoInfo.pages = new java.util.ArrayList<Integer>();
                        for (tv.biliclassic.download.VideoDownloadEntry e : entries) {
                            videoInfo.pagenames.add(e.pageTitle != null ? e.pageTitle : "P" + e.page);
                            videoInfo.cids.add(e.cid);
                            videoInfo.pages.add(e.page);
                        }
                        // 构建 UP主信息
                        if (firstEntry.upName != null && firstEntry.upName.length() > 0) {
                            videoInfo.staff = new java.util.ArrayList<UserInfo>();
                            UserInfo up = new UserInfo();
                            up.name = firstEntry.upName;
                            videoInfo.staff.add(up);
                        }
                        videoInfo.stats = new Stats();
                    } else {
                        videoInfo.title = "av" + aid + " (未找到缓存数据)";
                        videoInfo.pagenames = new java.util.ArrayList<String>();
                        videoInfo.pagenames.add("P1");
                        videoInfo.cids = new java.util.ArrayList<Long>();
                        videoInfo.cids.add(0L);
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                displayVideoInfo();
                                // 加载本地缓存的标签
                                if (videoInfo.tags != null && videoInfo.tags.length() > 0) {
                                    String[] splitTags = videoInfo.tags.split("/");
                                    for (int i = 0; i < splitTags.length && i < 9; i++) {
                                        tags[i] = splitTags[i];
                                    }
                                }
                                updateTags();
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                tvTitle.setText("离线数据加载失败");
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void loadTags(final long finalAid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String tagsStr = VideoInfoApi.getTags(finalAid);
                    if (tagsStr != null && tagsStr.length() > 0) {
                        String[] splitTags = tagsStr.split("/");
                        for (int i = 0; i < splitTags.length && i < 9; i++) {
                            tags[i] = splitTags[i];
                        }
                        if (videoInfo != null) videoInfo.tags = tagsStr;
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                updateTags();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                updateTags();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void loadTags(final String finalBvid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String tagsStr = VideoInfoApi.getTags(finalBvid);
                    if (tagsStr != null && tagsStr.length() > 0) {
                        String[] splitTags = tagsStr.split("/");
                        for (int i = 0; i < splitTags.length && i < 9; i++) {
                            tags[i] = splitTags[i];
                        }
                        if (videoInfo != null) videoInfo.tags = tagsStr;
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                updateTags();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                updateTags();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void updateTags() {
        if (!isAdded() || getActivity() == null) return;
        if (tagsContainer == null) return;

        tagsContainer.removeAllViews();
        List<String> validTags = new ArrayList<String>();
        for (int i = 0; i < tags.length; i++) {
            String tagText = tags[i];
            if (tagText != null && tagText.length() > 0) validTags.add(tagText);
        }

        if (validTags.size() == 0) {
            TextView nullTag = createTagView("null");
            nullTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isAdded() && getActivity() != null) searchTag("null");
                }
            });
            tagsContainer.addView(nullTag);
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int usedWidth = 0;
        LinearLayout currentRow = null;

        for (int i = 0; i < validTags.size(); i++) {
            final String tagText = validTags.get(i);
            TextView tagView = createTagView(tagText);
            tagView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isAdded() && getActivity() != null) searchTag(tagText);
                }
            });
            tagView.measure(0, 0);
            int tagWidth = tagView.getMeasuredWidth();
            if (currentRow == null || usedWidth + tagWidth > screenWidth - 20) {
                currentRow = new LinearLayout(getActivity());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                tagsContainer.addView(currentRow);
                usedWidth = 0;
            }
            currentRow.addView(tagView);
            usedWidth += tagWidth;
        }
    }

    private void displayVideoInfo() {
        if (!isAdded() || getActivity() == null) return;
        if (videoInfo == null) {
            tvTitle.setText("获取数据失败");
            return;
        }

        tvTitle.setText(videoInfo.title);

        if (videoInfo.staff != null && videoInfo.staff.size() > 0) {
            final UserInfo staff = videoInfo.staff.get(0);
            tvUpNameNew.setText(staff.name);
            tvUpNameNew.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isAdded() || getActivity() == null) return;
                    if (staff.mid != 0) {
                        Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                        intent.putExtra("mid", staff.mid);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), "无法获取UP主信息", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            tvUpNameNew.setText("未知");
        }

        if (videoInfo.stats != null) {
            tvPlay.setText("播放: " + videoInfo.stats.view);
            tvDanmaku.setText("弹幕: " + videoInfo.stats.danmaku);
        }

        if (videoInfo.description != null && videoInfo.description.length() > 0) {
            tvDesc.setText(videoInfo.description);
        } else {
            tvDesc.setText("暂无简介");
        }

        videoPages.clear();
        if (videoInfo.pagenames != null && videoInfo.pagenames.size() > 0) {
            partList.clear();
            for (int i = 0; i < videoInfo.pagenames.size(); i++) {
                String partTitle = videoInfo.pagenames.get(i);
                long cid = videoInfo.cids.get(i);
                partList.add(new VideoPart(i + 1, partTitle, cid));

                VideoPage page = new VideoPage();
                page.cid = cid;
                page.title = partTitle;
                page.page = i + 1;
                videoPages.add(page);
            }
            tvPartCount.setText("共" + partList.size() + "段视频");
            partAdapter.notifyDataSetChanged();
        } else {
            tvPartCount.setText("共1段视频");
            partList.clear();
            partList.add(new VideoPart(1, videoInfo.title, 0));
            partAdapter.notifyDataSetChanged();

            VideoPage page = new VideoPage();
            page.cid = 0;
            page.title = videoInfo.title;
            page.page = 1;
            videoPages.add(page);
        }

        loadCoverImage(videoInfo.cover);

        if (getActivity() instanceof VideoDetailActivity) {
            ((VideoDetailActivity) getActivity()).setVideoDetailFragment(this);
        }
    }

    private void loadCoverImage(String url) {
        if (!isAdded() || getActivity() == null) return;
        if (url == null || url.length() == 0) return;
        if (url.startsWith("https://")) {
            url = "http://" + url.substring(8);
        }
        final String finalUrl = url;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL urlObj = new URL(finalUrl);
                    conn = (HttpURLConnection) urlObj.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    final Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                    is.close();
                    if (bitmap != null && getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                if (bitmap != null && !bitmap.isRecycled()) {
                                    ivCover.setImageBitmap(bitmap);
                                } else {
                                    ivCover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void playVideo() {
        if (!isAdded() || getActivity() == null) return;
        if (videoInfo == null) {
            Toast.makeText(getActivity(), "视频信息未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 离线模式：直接播放本地缓存文件
        if (mOfflineMode) {
            playOfflineVideo();
            return;
        }

        final long targetCid;
        if (videoInfo.cids != null && currentPartIndex < videoInfo.cids.size()) {
            targetCid = videoInfo.cids.get(currentPartIndex);
        } else {
            targetCid = 0;
        }
        if (targetCid == 0) {
            Toast.makeText(getActivity(), "无法获取视频地址", Toast.LENGTH_SHORT).show();
            return;
        }

        final long tempAid = videoInfo.aid;
        final String tempTitle = videoInfo.title;
        final int tempPartIndex = currentPartIndex;
        final String tempPartTitle = (videoInfo.pagenames != null && tempPartIndex < videoInfo.pagenames.size())
                ? videoInfo.pagenames.get(tempPartIndex) : tempTitle;

        if (SettingsActivity.isOnlinePlayEnabled()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PlayerData playerData = new PlayerData();
                        playerData.aid = tempAid;
                        playerData.cid = targetCid;
                        playerData.title = tempPartTitle;
                        int quality = getSafeQuality();
                        playerData.qn = quality;
                        PlayerApi.getVideo(playerData, false);
                        final String videoUrl = playerData.videoUrl;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded() || getActivity() == null) return;
                                    if (videoUrl != null && videoUrl.length() > 0) {
                                        Intent intent = new Intent(getActivity(), PlayerAnimActivity.class);
                                        intent.putExtra("video_url", videoUrl);
                                        intent.putExtra("video_title", tempPartTitle);
                                        intent.putExtra("aid", tempAid);
                                        intent.putExtra("cid", targetCid);
                                        startActivity(intent);
                                    } else {
                                        Toast.makeText(getActivity(), "获取播放地址失败", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    } catch (final Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded() || getActivity() == null) return;
                                    Toast.makeText(getActivity(), "获取播放地址失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }
            }).start();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PlayerData playerData = new PlayerData();
                    playerData.aid = tempAid;
                    playerData.cid = targetCid;
                    playerData.title = tempPartTitle;
                    int quality = getSafeQuality();
                    playerData.qn = quality;
                    PlayerApi.getVideo(playerData, false);
                    final String videoUrl = playerData.videoUrl;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                if (videoUrl != null && videoUrl.length() > 0) {
                                    Intent intent = new Intent(getActivity(), PlayerAnimActivity.class);
                                    intent.putExtra("video_url", videoUrl);
                                    intent.putExtra("video_title", tempPartTitle);
                                    intent.putExtra("aid", tempAid);
                                    intent.putExtra("cid", targetCid);
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(getActivity(), "获取播放地址失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                Toast.makeText(getActivity(), "获取播放地址失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * 离线播放：直接使用本地缓存的视频文件
     */
    private void playOfflineVideo() {
        int pageIndex = currentPartIndex;
        if (pageIndex < 0) pageIndex = 0;

        // 获取实际的分P页码
        int actualPage = pageIndex + 1;
        if (videoInfo.pages != null && pageIndex < videoInfo.pages.size()) {
            actualPage = videoInfo.pages.get(pageIndex);
        }

        java.io.File downloadDir = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(), "BiliClassic/Download");
        if (!downloadDir.isDirectory()) {
            downloadDir = new java.io.File(getActivity().getFilesDir(), "Download");
        }

        tv.biliclassic.download.VideoDownloadEnvironment env =
                new tv.biliclassic.download.VideoDownloadEnvironment(downloadDir,
                        aid, actualPage);
        java.io.File videoFile = env.getVideoFile();
        java.io.File danmakuFile = env.getDanmakuFile(false);

        if (!videoFile.exists()) {
            Toast.makeText(getActivity(), "缓存视频文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        String pageTitle = (videoInfo.pagenames != null && pageIndex < videoInfo.pagenames.size())
                ? videoInfo.pagenames.get(pageIndex) : videoInfo.title;

        Intent intent = new Intent(getActivity(), BiliPlayerActivity.class);
        intent.putExtra("video_title", pageTitle);
        intent.putExtra("cache_path", videoFile.getAbsolutePath());
        if (videoInfo.cids != null && pageIndex < videoInfo.cids.size()) {
            intent.putExtra("cid", videoInfo.cids.get(pageIndex));
        }
        if (danmakuFile.exists()) {
            intent.putExtra("danmaku_cache_path", danmakuFile.getAbsolutePath());
        }
        startActivity(intent);
    }
}
