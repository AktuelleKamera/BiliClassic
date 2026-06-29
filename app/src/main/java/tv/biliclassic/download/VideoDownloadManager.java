package tv.biliclassic.download;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;

/**
 * 视频下载管理器
 * 职责：管理下载任务队列，在单线程中顺序执行下载
 * 参考原版哔哩哔哩的 VideoDownloadManager（简化版）
 */
public class VideoDownloadManager {

    private static final int MSG_SUBMIT_TASK = 1;
    private static final int MSG_CANCEL_TASK = 2;
    private static final int MSG_START_NEXT = 3;

    private final VideoDownloadNotificationHelper mNotifHelper;
    private final File mDownloadDir;
    private final Queue<VideoDownloadTask> mPendingTasks;
    private final List<VideoDownloadTask> mPausedTasks;
    private VideoDownloadTask mCurrentTask;
    private Handler mWorkHandler;
    private Handler mMainHandler;
    private boolean mStarted;

    public VideoDownloadManager(VideoDownloadNotificationHelper notifHelper, File downloadDir) {
        mNotifHelper = notifHelper;
        mDownloadDir = downloadDir;
        mPendingTasks = new LinkedList<VideoDownloadTask>();
        mPausedTasks = new ArrayList<VideoDownloadTask>();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        if (mStarted) return;
        mStarted = true;
        HandlerThread thread = new HandlerThread("VideoDownload");
        thread.start();
        mWorkHandler = new Handler(thread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                onHandleMessage(msg);
                return true;
            }
        });
    }

    public void stop() {
        mStarted = false;
        cancelAll();
        if (mWorkHandler != null) {
            mWorkHandler.getLooper().quit();
            mWorkHandler = null;
        }
    }

    private void onHandleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SUBMIT_TASK:
                processSubmitTask((VideoDownloadTask) msg.obj);
                break;
            case MSG_CANCEL_TASK:
                processCancelTaskByKey((Long) msg.obj);
                break;
            case MSG_START_NEXT:
                startNextTask();
                break;
        }
    }

    public void submit(VideoDownloadEntry entry, String videoUrl) {
        if (!mStarted || mWorkHandler == null) return;
        VideoDownloadTask task = new VideoDownloadTask(entry);
        task.videoUrl = videoUrl;
        Message msg = mWorkHandler.obtainMessage(MSG_SUBMIT_TASK, task);
        mWorkHandler.sendMessage(msg);
    }

    public void cancel(long avid, int page) {
        if (mWorkHandler == null) return;
        long key = ((long) avid << 32) | (page & 0xFFFFFFFFL);
        Message msg = Message.obtain(mWorkHandler, MSG_CANCEL_TASK, Long.valueOf(key));
        mWorkHandler.sendMessage(msg);
    }

    public void cancelAll() {
        mPendingTasks.clear();
        for (VideoDownloadTask t : mPausedTasks) { t.cancel(); }
        mPausedTasks.clear();
        if (mCurrentTask != null) {
            mCurrentTask.cancel();
            mCurrentTask = null;
        }
    }

    public void pauseCurrent() {
        if (mCurrentTask != null && !mCurrentTask.isCancelled()) {
            mCurrentTask.pause();
            mCurrentTask.entry.state = VideoDownloadEntry.STATE_STOPPED;
            mPausedTasks.add(mCurrentTask);
            mCurrentTask = null;
            if (mWorkHandler != null) {
                mWorkHandler.sendEmptyMessage(MSG_START_NEXT);
            }
        }
    }

    public void resumePaused(long key) {
        VideoDownloadTask found = null;
        for (VideoDownloadTask t : mPausedTasks) {
            if (t.entry.getKey() == key) { found = t; break; }
        }
        if (found != null) {
            mPausedTasks.remove(found);
            found.resume();
            found.entry.state = VideoDownloadEntry.STATE_IN_QUEUE;
            mPendingTasks.add(found); // 加回队首
            if (mCurrentTask == null && mWorkHandler != null) {
                mWorkHandler.sendEmptyMessage(MSG_START_NEXT);
            }
        }
    }

    public void resumeCurrent() {
        resumeAllPaused();  // 继续所有暂停的
    }

    private void resumeAllPaused() {
        while (!mPausedTasks.isEmpty()) {
            VideoDownloadTask t = mPausedTasks.remove(0);
            t.resume();
            t.entry.state = VideoDownloadEntry.STATE_IN_QUEUE;
            mPendingTasks.add(t);
        }
        if (mCurrentTask == null && mWorkHandler != null) {
            mWorkHandler.sendEmptyMessage(MSG_START_NEXT);
        }
    }

    private void processSubmitTask(VideoDownloadTask task) {
        long key = task.entry.getKey();
        if (mCurrentTask != null && mCurrentTask.entry.getKey() == key) return;
        for (VideoDownloadTask t : mPendingTasks) {
            if (t.entry.getKey() == key) return;
        }
        mPendingTasks.add(task);
        if (mCurrentTask == null) {
            startNextTask();
        }
    }

    private void processCancelTaskByKey(long key) {
        // 取消队列中的
        VideoDownloadTask toRemove = null;
        for (VideoDownloadTask t : mPendingTasks) {
            if (t.entry.getKey() == key) { toRemove = t; break; }
        }
        if (toRemove != null) mPendingTasks.remove(toRemove);
        if (mCurrentTask != null && mCurrentTask.entry.getKey() == key) {
            mCurrentTask.cancel();
            mCurrentTask = null;
            startNextTask();
        }
    }

    private void startNextTask() {
        if (mCurrentTask != null) return;
        mCurrentTask = mPendingTasks.poll();
        if (mCurrentTask == null) return;
        mCurrentTask.entry.state = VideoDownloadEntry.STATE_DOWNLOADING;
        executeDownload(mCurrentTask);
    }

    private void executeDownload(final VideoDownloadTask task) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doDownload(task);
                    doDownloadCover(task);
                    doDownloadDanmaku(task);
                    onDownloadSuccess(task);
                } catch (Exception e) {
                    e.printStackTrace();
                    onDownloadFailed(task, e.getMessage());
                }
            }
        }).start();
    }

    private void doDownload(VideoDownloadTask task) throws Exception {
        VideoDownloadEntry entry = task.entry;
        VideoDownloadEnvironment env = new VideoDownloadEnvironment(
                mDownloadDir, entry.avid, entry.page);
        entry.downloadEnv = env;

        entry.isCompleted = false;
        env.saveEntry(entry);

        if (task.videoUrl == null || task.videoUrl.length() == 0) {
            throw new Exception("视频地址未设置");
        }

        File videoFile = env.getVideoFile();
        File tmpFile = new File(videoFile.getAbsolutePath() + ".tmp");

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            conn = (HttpURLConnection) new URL(task.videoUrl).openConnection();
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            entry.totalBytes = conn.getContentLength();
            is = conn.getInputStream();
            fos = new FileOutputStream(tmpFile);
            byte[] buffer = new byte[8192];
            int len;
            long total = 0L;
            long lastSave = 0L;
            long lastNotify = System.currentTimeMillis();

            while ((len = is.read(buffer)) != -1) {
                if (task.isCancelled()) throw new Exception("下载已取消");
                task.waitIfPaused();
                if (task.isCancelled()) throw new Exception("下载已取消");
                fos.write(buffer, 0, len);
                total += len;
                entry.downloadedBytes = total;

                if (total - lastSave >= 2 * 1024 * 1024) {
                    env.saveEntry(entry);
                    lastSave = total;
                }
                // 每2秒通知一次进度，防止UI线程被刷爆
                long now = System.currentTimeMillis();
                if (now - lastNotify >= 2000) {
                    notifyProgress(entry);
                    lastNotify = now;
                }
            }
            fos.flush();
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception e) {} }
            if (is != null) { try { is.close(); } catch (Exception e) {} }
            if (conn != null) { conn.disconnect(); }
        }

        tmpFile.renameTo(videoFile);
        entry.downloadedBytes = entry.totalBytes;
        entry.isCompleted = true;
        env.saveEntry(entry);
    }

    /**
     * 下载封面图到本地
     */
    private void doDownloadCover(VideoDownloadTask task) {
        VideoDownloadEntry entry = task.entry;
        if (entry.coverUrl == null || entry.coverUrl.length() == 0) return;

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            VideoDownloadEnvironment env = new VideoDownloadEnvironment(
                    mDownloadDir, entry.avid, entry.page);
            File coverFile = env.getCoverFile(true);

            String coverUrl = entry.coverUrl;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }

            conn = (HttpURLConnection) new URL(coverUrl).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (bitmap != null && !bitmap.isRecycled()) {
                fos = new FileOutputStream(coverFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception e) {} }
            if (is != null) { try { is.close(); } catch (Exception e) {} }
            if (conn != null) { conn.disconnect(); }
        }
    }

    private void doDownloadDanmaku(VideoDownloadTask task) {
        VideoDownloadEntry entry = task.entry;
        if (entry.cid <= 0) return;

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            VideoDownloadEnvironment env = new VideoDownloadEnvironment(
                    mDownloadDir, entry.avid, entry.page);
            File danmakuFile = env.getDanmakuFile(true);

            String danmakuUrl = "https://comment.bilibili.com/" + entry.cid + ".xml";
            conn = (HttpURLConnection) new URL(danmakuUrl).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();
            // 检测是否需要解压deflate（响应头可能包含 Content-Encoding: deflate）
            byte[] rawData = readStreamFully(is);
            if (rawData != null && rawData.length > 0) {
                // 尝试deflate解压
                byte[] xmlData = decompressDeflate(rawData);
                fos = new FileOutputStream(danmakuFile);
                fos.write(xmlData);
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 弹幕下载失败不影响视频下载
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception e) {} }
            if (is != null) { try { is.close(); } catch (Exception e) {} }
            if (conn != null) { conn.disconnect(); }
        }
    }

    private byte[] readStreamFully(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

    private byte[] decompressDeflate(byte[] data) throws Exception {
        // 如果以 '<' 开头就是未压缩的 XML，直接返回
        if (data.length > 0 && data[0] == '<') return data;
        java.util.zip.Inflater decompresser = new java.util.zip.Inflater(true);
        decompresser.reset();
        decompresser.setInput(data);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                int count = decompresser.inflate(buf);
                o.write(buf, 0, count);
            }
            byte[] result = o.toByteArray();
            o.close();
            decompresser.end();
            return result;
        } catch (Exception e) {
            // 解压失败，返回原始数据
            try { o.close(); } catch (Exception e2) {}
            decompresser.end();
            return data;
        }
    }

    private void notifyProgress(final VideoDownloadEntry entry) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mNotifHelper.notifyDownloadProgress(entry);
            }
        });
    }

    private void notifyComplete(final VideoDownloadEntry entry) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mNotifHelper.notifyDownloadComplete(entry);
            }
        });
    }

    private void onDownloadSuccess(final VideoDownloadTask task) {
        task.entry.state = VideoDownloadEntry.STATE_STOPPED;
        notifyComplete(task.entry);
        if (mCurrentTask == task) mCurrentTask = null;
        if (mWorkHandler != null) {
            mWorkHandler.sendEmptyMessage(MSG_START_NEXT);
        }
    }

    private void onDownloadFailed(final VideoDownloadTask task, final String error) {
        task.entry.state = VideoDownloadEntry.STATE_STOPPED;
        task.entry.lastErrorMessage = error;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mNotifHelper.notifyDownloadFailed(task.entry, error);
            }
        });
        if (mCurrentTask == task) mCurrentTask = null;
        if (mWorkHandler != null) {
            mWorkHandler.sendEmptyMessage(MSG_START_NEXT);
        }
    }

    static class VideoDownloadTask {
        final VideoDownloadEntry entry;
        String videoUrl;
        private volatile boolean cancelled;
        private volatile boolean paused;

        VideoDownloadTask(VideoDownloadEntry entry) {
            this.entry = entry;
        }

        boolean isCancelled() { return cancelled; }
        void cancel() { cancelled = true; resume(); }

        synchronized boolean isPaused() { return paused; }
        synchronized void pause() { paused = true; }
        synchronized void resume() { paused = false; notifyAll(); }

        synchronized void waitIfPaused() {
            while (paused && !cancelled) {
                try { wait(); } catch (InterruptedException e) { }
            }
        }
    }
}
