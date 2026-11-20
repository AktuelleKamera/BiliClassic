package tv.biliclassic.player;

import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import tv.biliclassic.BaseActivity;
import tv.biliclassic.R;
import tv.biliclassic.util.NetWorkUtil;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 公告音频播放页（对应 WP 版 AudioPlayerPage）。
 *
 * 接收 Intent extra：
 *  - audio_url : 音频直链（必填）
 *  - title     : 标题（可选）
 *  - dis       : 说明文字（可选）
 *  - cookie    : Cookie（可选，用于防盗链）
 *  - agent     : User-Agent（可选，缺省用网页版 UA）
 */
public class AudioPlayerActivity extends BaseActivity
        implements IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnErrorListener {

    private IMediaPlayer mPlayer;
    private SeekBar mSeekBar;
    private TextView mTimeText;
    private TextView mStatusText;
    private TextView mPlayButton;

    private boolean mPrepared;
    private boolean mIsSeeking;
    private boolean mReleased;
    private int mStartPosition;
    private String mUrl;
    private Map<String, String> mHeaders;
    private boolean mHardwareDecode;

    private final Handler mHandler = new Handler();
    private final Runnable mProgressTask = new Runnable() {
        public void run() {
            updateProgress();
            if (!mReleased) {
                mHandler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        initRoundTitleBar();

        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });
        }

        TextView titleView = (TextView) findViewById(R.id.text_title);
        TextView disView = (TextView) findViewById(R.id.dis_text);
        mSeekBar = (SeekBar) findViewById(R.id.seek_bar);
        mTimeText = (TextView) findViewById(R.id.time_text);
        mStatusText = (TextView) findViewById(R.id.status_text);
        mPlayButton = (TextView) findViewById(R.id.btn_play_pause);

        Intent intent = getIntent();
        String url = intent.getStringExtra("audio_url");
        String title = intent.getStringExtra("title");
        String dis = intent.getStringExtra("dis");
        final String cookie = intent.getStringExtra("cookie");
        final String agent = intent.getStringExtra("agent");
        final String referer = intent.getStringExtra("referer");
        final String videoUrl = intent.getStringExtra("video_url");
        final long durationMs = intent.getLongExtra("duration_ms", 0);
        mStartPosition = intent.getIntExtra("start_position", 0);

        if (title != null && title.length() > 0 && titleView != null) {
            titleView.setText(title);
        }
        if (dis != null && dis.length() > 0 && disView != null) {
            disView.setText(dis);
            disView.setVisibility(View.VISIBLE);
        } else if (disView != null) {
            disView.setVisibility(View.GONE);
        }

        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                }

                public void onStartTrackingTouch(SeekBar sb) {
                    mIsSeeking = true;
                }

                public void onStopTrackingTouch(SeekBar sb) {
                    mIsSeeking = false;
                    if (mPlayer != null && mPrepared) {
                        try {
                            mPlayer.seekTo(sb.getProgress());
                        } catch (Throwable t) {
                        }
                    }
                }
            });
        }
        if (mPlayButton != null) {
            mPlayButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    togglePlay();
                }
            });
        }

        if (url == null || url.length() == 0) {
            if (mStatusText != null) mStatusText.setText("音频地址无效");
            if (mPlayButton != null) mPlayButton.setEnabled(false);
            return;
        }

        if (mStatusText != null) mStatusText.setText("正在加载…");
        Map<String, String> headers = new HashMap<String, String>();
        String ua = (agent != null && agent.length() > 0) ? agent : NetWorkUtil.USER_AGENT_WEB;
        headers.put("User-Agent", ua);
        headers.put("Referer", referer != null && referer.length() > 0
                ? referer : "https://www.bilibili.com/");
        if (cookie != null && cookie.length() > 0) {
            headers.put("Cookie", cookie);
        }
        mHeaders = headers;
        String playUrl = url;
        if (videoUrl != null && videoUrl.length() > 0
                && (url.startsWith("http://") || url.startsWith("https://"))) {
            String proxied = tv.biliclassic.ProxyStreamService.startProxyForExternal(
                    this, videoUrl, url, durationMs, headers);
            if (proxied != null && proxied.length() > 0 && !proxied.equals(videoUrl)) {
                playUrl = proxied;
            }
        }
        mUrl = playUrl;
        mHardwareDecode = tv.biliclassic.util.SdkHelper.getSdkInt() >= 16;
        startPlayer();
    }

    private void startPlayer() {
        releasePlayer();
        try {
            IjkMediaPlayer player = new IjkMediaPlayer();
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT);
            player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1L);
            player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                    mHardwareDecode ? 1L : 0L);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setDataSource(mUrl, mHeaders);
            mPlayer = player;
            if (mStatusText != null) mStatusText.setText("正在加载…");
            player.prepareAsync();
        } catch (Throwable t) {
            if (mHardwareDecode) {
                mHardwareDecode = false;
                startPlayer();
            } else if (mStatusText != null) {
                mStatusText.setText("播放失败：" + t.getMessage());
            }
        }
    }

    private void releasePlayer() {
        mHandler.removeCallbacks(mProgressTask);
        mPrepared = false;
        if (mPlayer != null) {
            try { mPlayer.reset(); } catch (Throwable ignored) {}
            try { mPlayer.release(); } catch (Throwable ignored) {}
            mPlayer = null;
        }
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        mPrepared = true;
        if (mStatusText != null) mStatusText.setText("");
        if (mSeekBar != null) {
            try {
                mSeekBar.setMax((int) Math.min(Integer.MAX_VALUE, mp.getDuration()));
            } catch (Throwable t) {
            }
        }
        setPlayButtonText("暂停");
        if (mStartPosition > 0) {
            try {
                mp.seekTo(mStartPosition);
            } catch (Throwable t) {
            }
        }
        try {
            mp.start();
        } catch (Throwable t) {
        }
        mHandler.removeCallbacks(mProgressTask);
        mHandler.post(mProgressTask);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        setPlayButtonText("播放");
        updateProgress();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        if (mHardwareDecode) {
            mHardwareDecode = false;
            startPlayer();
            return true;
        }
        mPrepared = false;
        if (mStatusText != null) mStatusText.setText("播放失败");
        setPlayButtonText("播放");
        return true;
    }

    private void togglePlay() {
        if (mPlayer == null || !mPrepared) {
            return;
        }
        try {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                setPlayButtonText("播放");
            } else {
                mPlayer.start();
                setPlayButtonText("暂停");
            }
        } catch (Throwable t) {
        }
    }

    private void setPlayButtonText(String text) {
        if (mPlayButton != null) {
            mPlayButton.setText(text);
        }
    }

    private void updateProgress() {
        if (mPlayer == null || !mPrepared) {
            return;
        }
        try {
            int position = (int) Math.min(Integer.MAX_VALUE, mPlayer.getCurrentPosition());
            int duration = (int) Math.min(Integer.MAX_VALUE, mPlayer.getDuration());
            if (duration > 0) {
                if (mSeekBar != null && mSeekBar.getMax() != duration) {
                    mSeekBar.setMax(duration);
                }
                if (mSeekBar != null && !mIsSeeking) {
                    mSeekBar.setProgress(position);
                }
                if (mTimeText != null) {
                    mTimeText.setText(format(position) + " / " + format(duration));
                }
            }
        } catch (Throwable t) {
        }
    }

    private static String format(int ms) {
        return PlayerTimeUtil.format(ms);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPlayer != null && mPrepared) {
            try {
                if (mPlayer.isPlaying()) {
                    mPlayer.pause();
                    setPlayButtonText("播放");
                }
            } catch (Throwable t) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mReleased = true;
        releasePlayer();
    }
}
