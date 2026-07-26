package tv.biliclassic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private ImageButton btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private TextView tvTitle;
    private RelativeLayout topBar;
    private LinearLayout bottomBar;
    private RelativeLayout videoContainer;

    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private boolean controlsVisible = true;
    private boolean isReleased = false;

    private File cacheFile;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private boolean surfaceReady = false;
    private boolean pendingPrepare = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        String videoTitle = getIntent().getStringExtra("video_title");
        String cachePath = getIntent().getStringExtra("cache_path");

        if (cachePath == null) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cacheFile = new File(cachePath);
        if (!cacheFile.exists()) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews(videoTitle);
        playWithSystemPlayer();
    }

    private void initViews(String videoTitle) {
        surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        btnPlayPause = (ImageButton) findViewById(R.id.btn_play_pause);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        tvCurrentTime = (TextView) findViewById(R.id.tv_current_time);
        tvDuration = (TextView) findViewById(R.id.tv_duration);
        tvTitle = (TextView) findViewById(R.id.tv_title);
        topBar = (RelativeLayout) findViewById(R.id.top_bar);
        bottomBar = (LinearLayout) findViewById(R.id.bottom_bar);
        videoContainer = (RelativeLayout) findViewById(R.id.video_container);

        if (videoTitle != null) {
            tvTitle.setText(videoTitle);
        }

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                releasePlayer();
                finish();
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null && isPrepared && !isReleased) {
                    if (isPlaying) {
                        pauseVideo();
                    } else {
                        playVideo();
                    }
                } else {
                    Toast.makeText(PlayerActivity.this, "视频加载中，请稍后...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && isPrepared && !isReleased) {
                    mediaPlayer.seekTo(progress);
                    updateTimeText();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleControls();
                    return true;
                }
                return false;
            }
        });
    }

    private void toggleControls() {
        if (controlsVisible) {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        } else {
            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (controlsVisible) {
                        toggleControls();
                    }
                }
            }, 3000);
        }
        controlsVisible = !controlsVisible;
    }

    private void playWithSystemPlayer() {
        if (surfaceReady) {
            prepareLocalPlayer();
        } else {
            pendingPrepare = true;
        }
    }

    private void prepareLocalPlayer() {
        if (mediaPlayer != null) {
            releasePlayer();
        }
        isPrepared = false;

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(cacheFile.getAbsolutePath());
            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "无法播放: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void adjustVideoSize(final int width, final int height) {
        if (width == 0 || height == 0) return;

        final int containerWidth = videoContainer.getWidth();
        final int containerHeight = videoContainer.getHeight();

        if (containerWidth == 0 || containerHeight == 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    adjustVideoSize(width, height);
                }
            }, 100);
            return;
        }

        float videoRatio = (float) width / height;
        float containerRatio = (float) containerWidth / containerHeight;

        int targetWidth, targetHeight;

        if (videoRatio > containerRatio) {
            targetWidth = containerWidth;
            targetHeight = (int) (containerWidth / videoRatio);
        } else {
            targetHeight = containerHeight;
            targetWidth = (int) (containerHeight * videoRatio);
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) surfaceView.getLayoutParams();
        params.width = targetWidth;
        params.height = targetHeight;
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        surfaceView.setLayoutParams(params);
    }

    private void releasePlayer() {
        if (isReleased) return;
        isReleased = true;

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
        isPrepared = false;
        isPlaying = false;
        surfaceReady = false;
        pendingPrepare = false;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (pendingPrepare) {
            pendingPrepare = false;
            prepareLocalPlayer();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releasePlayer();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (isReleased) return;

        videoWidth = mp.getVideoWidth();
        videoHeight = mp.getVideoHeight();

        adjustVideoSize(videoWidth, videoHeight);

        isPrepared = true;
        int duration = mp.getDuration();
        seekBar.setMax(duration);
        tvDuration.setText(formatTime(duration));

        mp.start();
        isPlaying = true;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        startProgressUpdater();

        toggleControls();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (controlsVisible && !isReleased) {
                    toggleControls();
                }
            }
        }, 3000);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (isReleased) return;
        isPlaying = false;
        isPrepared = false;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        seekBar.setProgress(0);
        tvCurrentTime.setText("00:00");
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "播放出错", Toast.LENGTH_LONG).show();
        return true;
    }

    private void playVideo() {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            startProgressUpdater();
        }
    }

    private void pauseVideo() {
        if (mediaPlayer != null && isPlaying && isPrepared && !isReleased) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void startProgressUpdater() {
        handler.removeCallbacks(progressRunnable);
        if (mediaPlayer != null && isPlaying && isPrepared && !isReleased) {
            updateTimeText();
            handler.postDelayed(progressRunnable, 500);
        }
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            startProgressUpdater();
        }
    };

    private void updateTimeText() {
        if (mediaPlayer != null && isPrepared && !isReleased) {
            try {
                int current = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(current);
                tvCurrentTime.setText(formatTime(current));
            } catch (IllegalStateException e) {}
        }
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}