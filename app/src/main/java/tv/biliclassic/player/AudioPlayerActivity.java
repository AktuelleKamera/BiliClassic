package tv.biliclassic.player;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
        implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private MediaPlayer mPlayer;
    private SeekBar mSeekBar;
    private TextView mTimeText;
    private TextView mStatusText;
    private TextView mPlayButton;

    private boolean mPrepared;
    private boolean mIsSeeking;
    private boolean mReleased;

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
        try {
            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);

            Map<String, String> headers = new HashMap<String, String>();
            String ua = (agent != null && agent.length() > 0) ? agent : NetWorkUtil.USER_AGENT_WEB;
            headers.put("User-Agent", ua);
            if (cookie != null && cookie.length() > 0) {
                headers.put("Cookie", cookie);
            }
            mPlayer.setDataSource(this, Uri.parse(url), headers);
            mPlayer.prepareAsync();
        } catch (Throwable t) {
            if (mStatusText != null) mStatusText.setText("播放失败：" + t.getMessage());
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mPrepared = true;
        if (mStatusText != null) mStatusText.setText("");
        if (mSeekBar != null) {
            try {
                mSeekBar.setMax(mp.getDuration());
            } catch (Throwable t) {
            }
        }
        setPlayButtonText("暂停");
        try {
            mp.start();
        } catch (Throwable t) {
        }
        mHandler.removeCallbacks(mProgressTask);
        mHandler.post(mProgressTask);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        setPlayButtonText("播放");
        updateProgress();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
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
            int position = mPlayer.getCurrentPosition();
            int duration = mPlayer.getDuration();
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
        if (ms < 0) ms = 0;
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String sec = seconds < 10 ? "0" + seconds : String.valueOf(seconds);
        return minutes + ":" + sec;
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
        mHandler.removeCallbacks(mProgressTask);
        if (mPlayer != null) {
            try {
                mPlayer.reset();
            } catch (Throwable t) {
            }
            try {
                mPlayer.release();
            } catch (Throwable t) {
            }
            mPlayer = null;
        }
    }
}
