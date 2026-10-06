package tv.biliclassic;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.widget.StarWarsCrawlView;

/**
 * 彩蛋：星战字幕。拉回声洞 JSON，只取每条的 text，逐人换行滚动，
 * 同时下载 echo.mp3 并播放。等两者都就绪再一起开始（避免画面先出、声音后到）。
 */
public class EasterEggActivity extends Activity {

    private static final String ECHO_JSON = "http://www.biliclassic.cn/api/echo.json";
    private static final String ECHO_MP3 = "http://www.biliclassic.cn/echo.mp3";

    private StarWarsCrawlView mCrawl;
    private MediaPlayer mPlayer;

    private String mEchoText = "";
    private File mMusicFile;
    private boolean mTextReady;
    private boolean mMusicReady;
    private boolean mStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mCrawl = new StarWarsCrawlView(this);
        setContentView(mCrawl, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mCrawl.setEndListener(new StarWarsCrawlView.OnCrawlEndListener() {
            public void onCrawlEnd() {
                finish();
            }
        });

        loadEchoText();
        downloadMusic();
    }

    /** 文字和音乐都就绪后，一起开始 */
    private void tryStart() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (isFinishing() || mStarted) {
                    return;
                }
                if (!mTextReady || !mMusicReady) {
                    return;
                }
                if (mEchoText.length() == 0) {
                    finish();
                    return;
                }
                mStarted = true;
                startMusic();
                mCrawl.start(mEchoText);
            }
        });
    }

    private void loadEchoText() {
        new Thread(new Runnable() {
            public void run() {
                String joined = "";
                try {
                    String json = NetWorkUtil.get(ECHO_JSON);
                    JSONArray arr = new JSONArray(NetWorkUtil.sanitizeJson(json));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        String t = o.optString("text", "");
                        if (t.length() == 0) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(t);
                    }
                    joined = sb.toString();
                } catch (Throwable t) {
                    joined = "";
                    android.util.Log.w("EasterEgg", "echo.json load failed: " + t);
                }
                mEchoText = joined;
                mTextReady = true;
                tryStart();
            }
        }).start();
    }

    private void downloadMusic() {
        new Thread(new Runnable() {
            public void run() {
                File out = null;
                try {
                    out = new File(getCacheDir(), "echo_egg.mp3");
                    download(ECHO_MP3, out);
                } catch (Throwable t) {
                    out = null;
                }
                if (out == null || !out.exists() || out.length() == 0) {
                    mMusicFile = null;
                } else {
                    mMusicFile = out;
                }
                mMusicReady = true;
                tryStart();
            }
        }).start();
    }

    private void startMusic() {
        if (mMusicFile == null) {
            return;
        }
        try {
            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mMusicFile.getAbsolutePath());
            // 放完自动重放，直到退出页面（onDestroy 里 release）
            mPlayer.setLooping(true);
            mPlayer.prepare();
            mPlayer.start();
        } catch (Throwable t) {
            mPlayer = null;
        }
    }

    private void download(String urlStr, File out) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            // 服务器没 UA 会 403
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            conn.setRequestProperty("Referer", "https://www.biliclassic.cn/");
            in = conn.getInputStream();
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            fos.flush();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Throwable t) {
            }
            try {
                if (in != null) in.close();
            } catch (Throwable t) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (mCrawl != null) {
            mCrawl.stop();
        }
        if (mPlayer != null) {
            try {
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                }
            } catch (Throwable t) {
            }
            try {
                mPlayer.release();
            } catch (Throwable t) {
            }
            mPlayer = null;
        }
        super.onDestroy();
    }
}
