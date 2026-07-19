/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年7月12日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tv.biliclassic.api.PlayerApi;
import tv.biliclassic.api.VideoInfoApi;
import tv.biliclassic.model.PlayerData;
import tv.biliclassic.model.Stats;
import tv.biliclassic.model.VideoInfo;
import tv.biliclassic.model.VideoPart;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.player.PlayerAnimActivity;
import tv.biliclassic.util.FileProviderCompat;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.player.BiliPlayerActivity;

import tv.biliclassic.util.SdkHelper;
public class VideoDetailFragment extends Fragment {

    public static class VideoPage {
        public long cid;
        public String title;
        public int page;
    }


    private ImageView ivCover;
    private TextView tvTitle, tvUpName, tvUpNameNew, tvPlay, tvDanmaku, tvDesc, tvPartCount, tvPubDate;
    private Button btnPlay;
    private ObservableListView lvParts;
    private LinearLayout tagsContainer;

    private VideoPartAdapter partAdapter;
    private ArrayList<VideoPart> partList = new ArrayList<VideoPart>();
    private List<VideoPage> videoPages = new ArrayList<VideoPage>();

    private long aid;
    private String bvid;
    public VideoInfo videoInfo;
    private int currentPartIndex = 0;
    private String[] tags = {"", "", "", "", "", "", "", "", ""};

    private boolean mOfflineMode;

    // 防连点
    private boolean isPlayButtonClicked = false;
    private Handler mHandler = new Handler();
    private ScrollView mScrollView;

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
        tvPubDate = (TextView) view.findViewById(R.id.tv_pubdate);
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

        partAdapter.setOnPartClickListener(new VideoPartAdapter.OnPartClickListener() {
            @Override
            public void onPartClick(VideoPart part, int position) {
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
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (lvParts != null) {
            lvParts.setFocusable(false);
            lvParts.setFocusableInTouchMode(false);
        }

        final ScrollView scrollView = (ScrollView) view.findViewById(R.id.scroll_view);
        if (scrollView != null) {
            scrollView.setFocusableInTouchMode(true);
            scrollView.requestFocus();
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(ScrollView.FOCUS_UP);
                    scrollView.scrollTo(0, 0);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 重置防连点
        isPlayButtonClicked = false;
        // 滚动到顶部
        forceScrollToTop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
        clearImages();
    }

    // 强制滚动到顶部
    private void forceScrollToTop() {
        if (mScrollView == null) return;
        // fullScroll 确保滚动到顶部
        mScrollView.fullScroll(ScrollView.FOCUS_UP);
        mScrollView.scrollTo(0, 0);
        if (ivCover != null) {
            ivCover.setFocusable(true);
            ivCover.setFocusableInTouchMode(true);
            ivCover.requestFocus();
        }
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

    // 下载：准备阶段（解析视频地址+获取可用画质）

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
        return SettingsActivity.getVideoQuality();
    }


    // 数据加载

    private void loadVideoData() {
        if (!isAdded() || getActivity() == null) return;
        tvTitle.setText("加载中…");

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

    private void loadVideoDataFromOffline() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File downloadDir;
                    if (PermissionUtil.hasWriteStorage(getActivity())) {
                        downloadDir = new java.io.File(
                                android.os.Environment.getExternalStorageDirectory(), "BiliClassic/Download");
                        if (!downloadDir.isDirectory()) {
                            downloadDir = null;
                        }
                    } else {
                        downloadDir = null;
                    }
                    if (downloadDir == null) {
                        downloadDir = new java.io.File(getActivity().getFilesDir(), "Download");
                    }

                    tv.biliclassic.download.VideoDownloadEnvironment env =
                            new tv.biliclassic.download.VideoDownloadEnvironment(downloadDir);
                    final java.util.ArrayList<tv.biliclassic.download.VideoDownloadEntry> entries =
                            env.loadEntriesForAvid(aid);

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

        if (tvPubDate != null) {
            if (videoInfo.pubdate > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                String dateStr = sdf.format(new Date(videoInfo.pubdate * 1000));
                tvPubDate.setText("发布时间: " + dateStr);
            } else {
                tvPubDate.setText("发布时间: --");
            }
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

        // 数据加载完成后强制滚动到顶部
        forceScrollToTop();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                forceScrollToTop();
            }
        }, 200);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                forceScrollToTop();
            }
        }, 500);
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
                java.io.File tempFile = null;
                try {
                    URL urlObj = new URL(finalUrl);
                    conn = (HttpURLConnection) urlObj.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();

                    // 下载到临时文件（避免 decodeStream mark/reset 问题）
                    tempFile = new java.io.File(getActivity().getCacheDir(), "vd_" + finalUrl.hashCode() + ".tmp");
                    InputStream is = conn.getInputStream();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                    byte[] buf = new byte[8192];
                    int readLen;
                    while ((readLen = is.read(buf)) != -1) {
                        fos.write(buf, 0, readLen);
                    }
                    is.close();
                    fos.close();
                    conn.disconnect();
                    conn = null;

                    if (!tempFile.exists() || tempFile.length() == 0) return;

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(tempFile.getAbsolutePath(), opts);

                    int scale = 1;
                    if (opts.outWidth > 200 || opts.outHeight > 150) {
                        scale = Math.max(opts.outWidth / 200, opts.outHeight / 150);
                        if (scale < 1) scale = 1;
                        if (scale > 4) scale = 4;
                    }

                    Bitmap bitmap = null;
                    while (scale <= 16 && bitmap == null) {
                        try {
                            opts = new BitmapFactory.Options();
                            opts.inSampleSize = scale;
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), opts);
                        } catch (OutOfMemoryError e) {
                            scale *= 2;
                        }
                    }
                    final Bitmap resultBitmap = bitmap;
                    if (resultBitmap != null && getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                if (resultBitmap != null && !resultBitmap.isRecycled()) {
                                    ivCover.setImageBitmap(resultBitmap);
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
                    if (tempFile != null && tempFile.exists()) tempFile.delete();
                }
            }
        }).start();
    }

    private void playVideo() {
        // 防连点
        if (isPlayButtonClicked) {
            return;
        }
        isPlayButtonClicked = true;

        if (!isAdded() || getActivity() == null) {
            isPlayButtonClicked = false;
            return;
        }
        if (videoInfo == null) {
            Toast.makeText(getActivity(), "视频信息未加载", Toast.LENGTH_SHORT).show();
            isPlayButtonClicked = false;
            return;
        }

        // 离线模式：直接播放本地缓存文件
        if (mOfflineMode) {
            playOfflineVideo();
            isPlayButtonClicked = false;
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
            isPlayButtonClicked = false;
            return;
        }

        final long tempAid = videoInfo.aid;
        final String tempTitle = videoInfo.title;
        final int tempPartIndex = currentPartIndex;
        final String tempPartTitle = (videoInfo.pagenames != null && videoInfo.pagenames.size() > 1 && tempPartIndex < videoInfo.pagenames.size())
                ? videoInfo.pagenames.get(tempPartIndex) : tempTitle;

        final long[] cidArray;
        final String[] partNameArray;
        if (videoInfo.cids != null && videoInfo.cids.size() > 1) {
            cidArray = new long[videoInfo.cids.size()];
            for (int i = 0; i < cidArray.length; i++) cidArray[i] = videoInfo.cids.get(i);
            partNameArray = videoInfo.pagenames.toArray(new String[videoInfo.pagenames.size()]);
        } else {
            cidArray = null;
            partNameArray = null;
        }

        if (SettingsActivity.isOnlinePlayEnabled()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final PlayerData playerData = new PlayerData();
                        playerData.aid = tempAid;
                        playerData.cid = targetCid;
                        playerData.title = tempPartTitle;
                    int quality = getSafeQuality();
                    playerData.qn = quality;
                    PlayerApi.getVideo(playerData, false);
                    reconcileQuality(playerData);
                    final String videoUrl = playerData.videoUrl;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isAdded() || getActivity() == null) return;
                                    if (videoUrl != null && videoUrl.length() > 0) {
                                        Intent intent = new Intent(getActivity(), BiliPlayerActivity.class);
                                        intent.putExtra("video_url", videoUrl);
                                        intent.putExtra("video_title", tempPartTitle);
                                        intent.putExtra("aid", tempAid);
                                        intent.putExtra("cid", targetCid);
                                        intent.putExtra("online_mode", true);
                                        intent.putExtra("part_index", tempPartIndex);
                                        if (cidArray != null) {
                                            intent.putExtra("cids", cidArray);
                                            intent.putExtra("pagenames", partNameArray);
                                        }
                                        putQualityExtras(intent, playerData);
                                        isPlayButtonClicked = false;
                                        startActivity(intent);
                                    } else {
                                        Toast.makeText(getActivity(), "获取播放地址失败", Toast.LENGTH_SHORT).show();
                                        isPlayButtonClicked = false;
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
                                    isPlayButtonClicked = false;
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
                    final PlayerData playerData = new PlayerData();
                    playerData.aid = tempAid;
                    playerData.cid = targetCid;
                    playerData.title = tempPartTitle;
                    int quality = getSafeQuality();
                    playerData.qn = quality;
                    PlayerApi.getVideo(playerData, false);
                    reconcileQuality(playerData);
                    final String videoUrl = playerData.videoUrl;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isAdded() || getActivity() == null) return;
                                if (videoUrl != null && videoUrl.length() > 0) {
                                    if (getActivity() instanceof VideoDetailActivity) {
                                        ((VideoDetailActivity) getActivity()).setPausingForTransient(true);
                                    }
                                    Intent intent = new Intent(getActivity(), PlayerAnimActivity.class);
                                    intent.putExtra("video_url", videoUrl);
                                    intent.putExtra("video_title", tempPartTitle);
                                    intent.putExtra("aid", tempAid);
                                    intent.putExtra("cid", targetCid);
                                    intent.putExtra("part_index", tempPartIndex);
                                    if (cidArray != null) {
                                        intent.putExtra("cids", cidArray);
                                        intent.putExtra("pagenames", partNameArray);
                                    }
                                    putQualityExtras(intent, playerData);
                                    isPlayButtonClicked = false;
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(getActivity(), "获取播放地址失败", Toast.LENGTH_SHORT).show();
                                    isPlayButtonClicked = false;
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
                                isPlayButtonClicked = false;
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void playOfflineVideo() {
        int pageIndex = currentPartIndex;
        if (pageIndex < 0) pageIndex = 0;

        int actualPage = pageIndex + 1;
        if (videoInfo.pages != null && pageIndex < videoInfo.pages.size()) {
            actualPage = videoInfo.pages.get(pageIndex);
        }

        java.io.File downloadDir;
        if (PermissionUtil.hasWriteStorage(getActivity())) {
            downloadDir = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (!downloadDir.isDirectory()) {
                downloadDir = null;
            }
        } else {
            downloadDir = null;
        }
        if (downloadDir == null) {
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

        int qualityQn = 0;
        String qualityName = null;
        try {
            java.io.File entryFile = env.getEntryFile(false);
            if (entryFile != null && entryFile.exists()) {
                tv.biliclassic.download.VideoDownloadEntry entry =
                        tv.biliclassic.download.VideoDownloadEntry.loadFromFile(entryFile);
                if (entry != null) {
                    qualityQn = entry.quality;
                    qualityName = entry.qualityName;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VideoDetail", "读取entry.json失败: " + e.getMessage());
        }

        String pageTitle = (videoInfo.pagenames != null && videoInfo.pagenames.size() > 1 && pageIndex < videoInfo.pagenames.size())
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
        intent.putExtra("offline_mode", true);
        intent.putExtra("current_qn", qualityQn);
        if (qualityName != null && qualityName.length() > 0) {
            intent.putExtra("qn_str_array", new String[]{qualityName});
        } else if (qualityQn > 0) {
            String shortName = tv.biliclassic.download.VideoDownloadEnvironment.getQualityName(qualityQn);
            intent.putExtra("qn_str_array", new String[]{shortName});
        }
        isPlayButtonClicked = false;

        // 非隐私模式：离线播放前上传播放记录（00：00）
        long reportCid = (videoInfo.cids != null && pageIndex < videoInfo.cids.size())
                ? videoInfo.cids.get(pageIndex) : 0;
        if (reportCid > 0) {
            tv.biliclassic.player.BiliPlayerActivity.reportHistoryStatic(
                    getActivity(), aid, reportCid, 0);
        }

        // 检查播放器偏好：非内置/自动 → 用外部播放器
        int pref = SettingsActivity.getPlayerPreference();
        if (pref != 8) {
            android.net.Uri uri = SdkHelper.getSdkInt() >= 24
                    ? FileProviderCompat.getUriForFile(getActivity(), videoFile)
                    : android.net.Uri.fromFile(videoFile);
            Intent extIntent = new Intent(Intent.ACTION_VIEW);
            extIntent.setDataAndType(uri, "video/mp4");
            if (SdkHelper.getSdkInt() >= 24) {
                extIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            String pkg = SettingsActivity.getPlayerPackageName();
            if (pkg != null) {
                extIntent.setPackage(pkg);
                if (getActivity().getPackageManager().queryIntentActivities(extIntent, 0).size() == 0) {
                    extIntent.setPackage(null);
                }
            }
            try {
                if (getActivity() instanceof VideoDetailActivity) {
                    ((VideoDetailActivity) getActivity()).setPausingForTransient(true);
                }
                startActivity(extIntent);
                return;
            } catch (Exception e) {
                Toast.makeText(getActivity(), "未找到可用的外部播放器", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        startActivity(intent);
    }

    private void putQualityExtras(Intent intent, PlayerData playerData) {
        if (playerData == null) return;

        if (playerData.qnStrList != null && playerData.qnStrList.length > 0) {
            String[] fixedNames = new String[playerData.qnStrList.length];
            for (int i = 0; i < playerData.qnStrList.length; i++) {
                String name = playerData.qnStrList[i];
                if (name != null) {
                    if (name.contains("1080P") && name.contains("高清")) {
                        name = name.replace("高清", "超清");
                    }
                    if (name.contains("720") && name.contains("准高清")) {
                        name = name.replace("准高清", "高清");
                    }
                }
                fixedNames[i] = name;
            }
            intent.putExtra("qn_str_array", fixedNames);
        }
        if (playerData.qnValueList != null && playerData.qnValueList.length > 0) {
            intent.putExtra("qn_value_array", playerData.qnValueList);
        }
        intent.putExtra("current_qn", playerData.qn);
    }

    private void reconcileQuality(PlayerData playerData) {
        if (playerData.qnValueList != null && playerData.qnValueList.length > 0) {
            int bestQn = playerData.qnValueList[0];
            boolean found = false;
            for (int q : playerData.qnValueList) {
                if (q == playerData.qn) { bestQn = q; found = true; break; }
                if (q < playerData.qn && (bestQn > playerData.qn || q > bestQn)) bestQn = q;
            }
            if (!found) {
                playerData.qn = bestQn;
                playerData.timeStamp = 0;
                try {
                    PlayerApi.getVideo(playerData, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}