package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.LiveApi;
import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.LivePlayInfo;
import tv.biliclassic.model.LiveRoom;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.player.BiliPlayerActivity;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.StringUtil;

/**
 * 生放送房间详情页：显示封面/标题/主播/信息/清晰度/线路，并可进入播放器。
 * 由生放送列表（LiveRoomListActivity）点击进入。
 */
public class LiveInfoActivity extends BaseActivity {

    private long roomId;

    private TextView textTitle, textAnchor, textStatus, textInfo, textTags, textDescription;
    private TextView btnQuality, btnRoute, btnPlay;
    private ImageView imgCover, imgAnchor;
    private View loading, scrollView;

    private LiveRoom room;
    private LivePlayInfo playInfo;

    // 当前选中的清晰度 / 线路
    private int selectedQualityQn = 0;
    private int selectedQualityPos = 0;
    private int selectedRoute = 0;

    // 当前 (stream, format, codec) 下标
    private int[] idx = null;

    private volatile boolean loadingPlayInfo = false;

    private boolean descExpand = false;
    private boolean tagsExpand = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roomId = getIntent().getLongExtra("room_id", 0);
        if (roomId == 0) {
            Toast.makeText(this, getString(R.string.live_info_invalid), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_live_info);
        initRoundTitleBar();

        loading = findViewById(R.id.loading);
        scrollView = findViewById(R.id.scroll_view);
        imgCover = (ImageView) findViewById(R.id.img_cover);
        textTitle = (TextView) findViewById(R.id.text_title);
        imgAnchor = (ImageView) findViewById(R.id.img_anchor);
        textAnchor = (TextView) findViewById(R.id.text_anchor);
        textStatus = (TextView) findViewById(R.id.text_status);
        textInfo = (TextView) findViewById(R.id.text_info);
        textTags = (TextView) findViewById(R.id.text_tags);
        textDescription = (TextView) findViewById(R.id.text_description);
        btnQuality = (TextView) findViewById(R.id.btn_quality);
        btnRoute = (TextView) findViewById(R.id.btn_route);
        btnPlay = (TextView) findViewById(R.id.btn_play);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loading.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        btnQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });
        btnRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRouteDialog();
            }
        });
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });

        loadData();
    }

    private void loadData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LiveRoom roomInfo = null;
                LivePlayInfo info = null;
                UserInfo anchor = null;
                try {
                    roomInfo = LiveApi.getRoomInfo(roomId);
                } catch (Exception e) {
                    android.util.Log.e("LiveInfo", "getRoomInfo fail", e);
                }
                long playId = (roomInfo != null && roomInfo.realRoomId() > 0) ? roomInfo.realRoomId() : roomId;
                try {
                    info = LiveApi.getRoomPlayInfo(playId, 0);
                } catch (Exception e) {
                    android.util.Log.e("LiveInfo", "getRoomPlayInfo fail", e);
                }
                if (roomInfo != null && roomInfo.uid > 0) {
                    try {
                        anchor = UserInfoApi.getUserInfo(roomInfo.uid);
                    } catch (Exception e) {
                        android.util.Log.e("LiveInfo", "getUserInfo fail", e);
                    }
                }
                renderData(roomInfo, info, anchor);
            }
        }).start();
    }

    private void renderData(final LiveRoom roomInfo, final LivePlayInfo info, final UserInfo anchor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loading.setVisibility(View.GONE);
                scrollView.setVisibility(View.VISIBLE);

                if (roomInfo != null) {
                    room = roomInfo;
                }
                if (info != null) {
                    playInfo = info;
                    recoverQualityAndStream();
                }

                if (room == null && playInfo == null) {
                    MsgUtil.showMsg(LiveInfoActivity.this, getString(R.string.live_info_empty));
                    finish();
                    return;
                }

                String title = room != null ? room.title : "";
                if (playInfo != null && (title == null || title.length() == 0)) {
                    title = getString(R.string.live_info_player_prefix) + roomId;
                }
                if (title == null) title = "";
                textTitle.setText(title);

                // 主播
                String anchorName = room != null && room.uname != null && room.uname.length() > 0
                        ? room.uname : "";
                String anchorFace = room != null ? room.face : "";
                if (anchor != null) {
                    if (anchor.name != null) anchorName = anchor.name;
                    if (anchor.avatar != null) anchorFace = anchor.avatar;
                }
                if (anchorName.length() == 0) anchorName = "未知主播";
                textAnchor.setText(anchorName);
                if (anchorFace != null && anchorFace.length() > 0) {
                    ImageLoader.bind(imgAnchor, anchorFace, R.drawable.bili_default_avatar, 40, 40);
                } else {
                    imgAnchor.setImageResource(R.drawable.bili_default_avatar);
                }

                // 状态
                int status = room != null ? room.live_status : (playInfo != null ? playInfo.live_status : 0);
                textStatus.setText(statusText(status));

                // 分区 / 房间号 / 人气
                StringBuilder infoText = new StringBuilder();
                String area = areaText();
                if (area != null && area.length() > 0) infoText.append("分区：").append(area);
                if (infoText.length() > 0) infoText.append("　");
                infoText.append("房间号：").append(roomId);
                int online = room != null ? room.online : 0;
                if (online > 0) {
                    infoText.append("　").append(StringUtil.toWan(online)).append("人观看");
                }
                textInfo.setText(infoText.toString());

                // 标签
                String tags = room != null ? room.tags : "";
                if (tags == null) tags = "";
                textTags.setVisibility(tags.length() > 0 ? View.VISIBLE : View.GONE);
                textTags.setText(tags);
                textTags.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (tagsExpand) {
                            textTags.setMaxLines(1);
                        } else {
                            textTags.setMaxLines(233);
                        }
                        tagsExpand = !tagsExpand;
                    }
                });

                // 简介
                String desc = room != null ? room.description : "";
                if (desc == null) desc = "";
                desc = StringUtil.removeHtml(desc);
                textDescription.setVisibility(desc.length() > 0 ? View.VISIBLE : View.GONE);
                textDescription.setText(desc);
                textDescription.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (descExpand) {
                            textDescription.setMaxLines(4);
                        } else {
                            textDescription.setMaxLines(512);
                        }
                        descExpand = !descExpand;
                    }
                });

                // 封面
                String cover = room != null ? room.pickCover() : "";
                if (cover != null && cover.length() > 0) {
                    ImageLoader.bind(imgCover, cover, R.drawable.bili_default_image_tv_with_bg, 360, 200);
                }

                updateButtons();
            }
        });
    }

    private String statusText(int status) {
        if (status == 1) return "直播中";
        if (status == 2) return "轮播中";
        if (status == 0) return "未开播";
        return "未知";
    }

    private String areaText() {
        if (room == null) return "";
        StringBuilder sb = new StringBuilder();
        if (room.area_parent_name != null && room.area_parent_name.length() > 0) {
            sb.append(room.area_parent_name);
        }
        if (room.area_name != null && room.area_name.length() > 0) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(room.area_name);
        }
        return sb.toString();
    }

    /**
     * 从响应里恢复当前清晰度与（偏好）流下标：优先 FLV。
     */
    private void recoverQualityAndStream() {
        if (playInfo == null) return;
        int[] pref = LiveApi.pickPreferredStream(playInfo);
        if (pref == null) {
            idx = null;
            return;
        }
        idx = pref;
        // 取当前清晰度
        LivePlayInfo.Codec codec = getCurrentCodec();
        if (codec != null) {
            selectedQualityQn = codec.current_qn;
        }
        // 在 g_qn_desc / QualityMap 定位到当前 qn 的位置
        selectedQualityPos = findQualityPos(selectedQualityQn);
    }

    private LivePlayInfo.Codec getCurrentCodec() {
        if (playInfo == null || idx == null || playInfo.playUrl == null) return null;
        try {
            LivePlayInfo.ProtocolInfo p = playInfo.playUrl.stream.get(idx[0]);
            LivePlayInfo.Format f = p.format.get(idx[1]);
            return f.codec.get(idx[2]);
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取可选清晰度列表：优先返回接口 g_qn_desc，否则用 QualityMap */
    private List<Object[]> buildQualityList() {
        List<Object[]> out = new ArrayList<Object[]>();
        // Object[]{qn(int), 名称(String)}
        if (playInfo != null && playInfo.playUrl != null && playInfo.playUrl.g_qn_desc != null
                && !playInfo.playUrl.g_qn_desc.isEmpty()) {
            for (int i = 0; i < playInfo.playUrl.g_qn_desc.size(); i++) {
                LivePlayInfo.QnDesc q = playInfo.playUrl.g_qn_desc.get(i);
                if (q == null) continue;
                out.add(new Object[]{Integer.valueOf(q.qn), q.desc});
            }
        } else {
            java.util.Iterator<java.util.Map.Entry<String, Integer>> it =
                    LiveApi.QualityMap.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, Integer> e = it.next();
                out.add(new Object[]{e.getValue(), e.getKey()});
            }
        }
        return out;
    }

    private int findQualityPos(int qn) {
        List<Object[]> qList = buildQualityList();
        for (int i = 0; i < qList.size(); i++) {
            if (((Integer) qList.get(i)[0]).intValue() == qn) return i;
        }
        return 0;
    }

    private void updateButtons() {
        if (!hasPlayableStream()) {
            // 无流：隐藏清晰度/线路，仅保留一个提示性的播放按钮（不可用）
            btnQuality.setVisibility(View.GONE);
            btnRoute.setVisibility(View.GONE);
            btnPlay.setText(getString(R.string.live_info_no_stream));
            btnPlay.setClickable(false);
            return;
        }
        btnPlay.setClickable(true);
        btnPlay.setText(getString(R.string.live_info_play));

        String qualityName = qualityName(selectedQualityQn, selectedQualityPos);
        btnQuality.setText(qualityName);

        int routeCount = getRouteCount();
        if (routeCount > 1) {
            btnRoute.setText(getString(R.string.live_info_route) + (selectedRoute + 1));
            btnRoute.setClickable(true);
        } else {
            btnRoute.setText(getString(R.string.live_info_route) + "1");
            btnRoute.setClickable(false);
        }
    }

    private String qualityName(int qn, int pos) {
        List<Object[]> qList = buildQualityList();
        if (pos >= 0 && pos < qList.size()) {
            String name = (String) qList.get(pos)[1];
            if (name != null && name.length() > 0) return name;
        }
        // 回退：直接显示 qn
        return "清晰度 " + qn;
    }

    private boolean hasPlayableStream() {
        if (idx == null || playInfo == null || playInfo.playUrl == null) return false;
        try {
            LivePlayInfo.ProtocolInfo p = playInfo.playUrl.stream.get(idx[0]);
            LivePlayInfo.Format f = p.format.get(idx[1]);
            LivePlayInfo.Codec c = f.codec.get(idx[2]);
            return c.url_info != null && !c.url_info.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private int getRouteCount() {
        if (idx == null || playInfo == null || playInfo.playUrl == null) return 0;
        try {
            LivePlayInfo.ProtocolInfo p = playInfo.playUrl.stream.get(idx[0]);
            LivePlayInfo.Format f = p.format.get(idx[1]);
            LivePlayInfo.Codec c = f.codec.get(idx[2]);
            return c.url_info != null ? c.url_info.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showQualityDialog() {
        if (playInfo == null) return;
        final List<Object[]> qList = buildQualityList();
        final String[] names = new String[qList.size()];
        for (int i = 0; i < qList.size(); i++) {
            names[i] = (String) qList.get(i)[1];
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(SdkHelper.dialogContext(this));
        builder.setTitle(getString(R.string.live_info_quality_title));
        builder.setSingleChoiceItems(names, selectedQualityPos, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                d.dismiss();
                switchQuality(which, (Integer) qList.get(which)[0]);
            }
        });
        builder.setNegativeButton(getString(R.string.videodetail_cancel), null);
        builder.show();
    }

    private void switchQuality(final int pos, final int qn) {
        if (loadingPlayInfo) return;
        loadingPlayInfo = true;
        setButtonsEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                LivePlayInfo info = null;
                try {
                    info = LiveApi.getRoomPlayInfo(roomId, qn);
                } catch (Exception e) {
                    android.util.Log.e("LiveInfo", "switchQuality fail", e);
                }
                final LivePlayInfo finalInfo = info;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingPlayInfo = false;
                        setButtonsEnabled(true);
                        if (finalInfo == null || !finalInfo.hasStream()) {
                            MsgUtil.showMsg(LiveInfoActivity.this, getString(R.string.live_info_quality_fail));
                            return;
                        }
                        playInfo = finalInfo;
                        selectedQualityPos = pos;
                        selectedQualityQn = qn;
                        selectedRoute = 0;
                        int[] pref = LiveApi.pickPreferredStream(playInfo);
                        idx = (pref == null) ? null : pref;
                        updateButtons();
                        Toast.makeText(LiveInfoActivity.this,
                                getString(R.string.live_info_quality_switched), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void showRouteDialog() {
        int routeCount = getRouteCount();
        if (routeCount <= 1) return;
        final String[] names = new String[routeCount];
        for (int i = 0; i < routeCount; i++) {
            names[i] = getString(R.string.live_info_route) + (i + 1);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(SdkHelper.dialogContext(this));
        builder.setTitle(getString(R.string.live_info_route_title));
        builder.setSingleChoiceItems(names, selectedRoute, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                d.dismiss();
                selectedRoute = which;
                updateButtons();
            }
        });
        builder.setNegativeButton(getString(R.string.videodetail_cancel), null);
        builder.show();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnQuality.setEnabled(enabled);
        btnRoute.setEnabled(enabled);
        btnPlay.setEnabled(enabled);
    }

    private void play() {
        if (playInfo == null || idx == null) {
            Toast.makeText(this, getString(R.string.live_info_no_stream), Toast.LENGTH_SHORT).show();
            return;
        }

        // 取当前清晰度对应的 stream（保持 idx 相对直播当前的 stream）
        int[] indices = idx;
        String playUrl = LiveApi.buildPlayUrl(playInfo, indices[0], indices[1], indices[2], selectedRoute);
        if (playUrl == null) {
            Toast.makeText(this, getString(R.string.live_info_build_url_fail), Toast.LENGTH_SHORT).show();
            return;
        }

        String title = (room != null && room.title != null && room.title.length() > 0)
                ? room.title : (getString(R.string.live_info_player_prefix) + roomId);
        String cover = room != null ? room.pickCover() : "";

        Intent intent = new Intent(this, BiliPlayerActivity.class);
        intent.putExtra("video_url", playUrl);
        intent.putExtra("video_title", getString(R.string.live_info_player_prefix) + title);
        intent.putExtra("aid", roomId);
        intent.putExtra("online_mode", true);
        intent.putExtra("live", true);
        if (cover != null && cover.length() > 0) {
            intent.putExtra("cover_url", cover);
        }
        startActivity(intent);
    }
}
