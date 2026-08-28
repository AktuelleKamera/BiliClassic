/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.ui.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.LinkedList;
import java.util.Locale;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.DrawHandler.Callback;
import master.flame.danmaku.controller.DrawHelper;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.controller.IDanmakuViewController;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.renderer.IRenderer.RenderingState;

public class DanmakuView extends View implements IDanmakuView, IDanmakuViewController {

    public static final String TAG = "DanmakuView";

    private static int sSdkInt = -1;

    private static int getSdkInt() {
        if (sSdkInt < 0) {
            try {
                sSdkInt = Build.VERSION.class.getField("SDK_INT").getInt(null);
            } catch (Exception e) {
                try {
                    sSdkInt = Integer.parseInt(Build.VERSION.SDK);
                } catch (Exception e2) {
                    sSdkInt = 0;
                }
            }
        }
        return sSdkInt;
    }

    private Callback mCallback;

    private HandlerThread mHandlerThread;

    private DrawHandler handler;
    
    private boolean isSurfaceCreated;

    private boolean mEnableDanmakuDrwaingCache = true;

    private OnDanmakuClickListener mOnDanmakuClickListener;

    private DanmakuTouchHelper mTouchHelper;
    
    private boolean mShowFps;

    private boolean mDanmakuVisible = true;

    protected int mDrawingThreadType = THREAD_TYPE_NORMAL_PRIORITY;

    private Object mDrawMonitor = new Object();

    private boolean mDrawFinished = false;

    private boolean mRequestRender = false;

    private long mUiThreadId;

    public DanmakuView(Context context) {
        super(context);
        init();
    }

    private void init() {
        mUiThreadId = Thread.currentThread().getId();
        setBackgroundColor(Color.TRANSPARENT);
        setDrawingCacheBackgroundColor(Color.TRANSPARENT);
        DrawHelper.useDrawColorToClearCanvas(true, false);
        mTouchHelper = DanmakuTouchHelper.instance(this);
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DanmakuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void addDanmaku(BaseDanmaku item) {
        if (handler != null) {
            handler.addDanmaku(item);
        }
    }
    
    @Override
    public void removeAllDanmakus() {
        if (handler != null) {
            handler.removeAllDanmakus();
        }
    }
    
    @Override
    public void removeAllLiveDanmakus() {
        if (handler != null) {
            handler.removeAllLiveDanmakus();
        }
    }

    @Override
    public IDanmakus getCurrentVisibleDanmakus() {
        if (handler != null) {
            return handler.getCurrentVisibleDanmakus();
        }

        return null;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
        if (handler != null) {
            handler.setCallback(callback);
        }
    }

    @Override
    public void release() {
        stop();
        if(mDrawTimes!= null) mDrawTimes.clear();
    }

    @Override
    public void stop() {
        stopDraw();
    }

    private void stopDraw() {
        if (handler != null) {
            handler.quit();
            handler = null;
        }
        if (mHandlerThread != null) {
            final HandlerThread ht = mHandlerThread;
            mHandlerThread = null;
            Thread recycler = new Thread(new Runnable() {
                public void run() {
                    try {
                        ht.join();
                    } catch (InterruptedException e) {
                    }
                    // HandlerThread.quit() 是 API 18+，直接引用会在 API<18 上 VerifyError。
                    // Looper.quit() 自 API 1 就有，且功能等价。
                    try {
                        ht.getLooper().quit();
                    } catch (Throwable t) {
                    }
                }
            }, "DFM-ThreadRecycler");
            recycler.setDaemon(true);
            recycler.start();
        }
    }
    
    protected Looper getLooper(int type){
        if (mHandlerThread != null) {
            try {
                mHandlerThread.getLooper().quit();
            } catch (Throwable t) {
            }
            mHandlerThread = null;
        }
        
        int priority;
        switch (type) {
            case THREAD_TYPE_MAIN_THREAD:
                return Looper.getMainLooper();
            case THREAD_TYPE_HIGH_PRIORITY:
                priority = android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY;
                break;
            case THREAD_TYPE_LOW_PRIORITY:
                priority = android.os.Process.THREAD_PRIORITY_LOWEST;
                break;
            case THREAD_TYPE_NORMAL_PRIORITY:
            default:
                priority = android.os.Process.THREAD_PRIORITY_DEFAULT;
                break;
        }
        String threadName = "DFM Handler Thread #"+priority;
        mHandlerThread = new HandlerThread(threadName, priority);
        mHandlerThread.start();
        return mHandlerThread.getLooper();
    }

    private void prepare() {
        if (handler == null)
            handler = new DrawHandler(getLooper(mDrawingThreadType), this, mDanmakuVisible);
    }

    @Override
    public void prepare(BaseDanmakuParser parser) {
    	prepare();
        handler.setParser(parser);
        handler.setCallback(mCallback);
        handler.prepare();
    }

    @Override
    public boolean isPrepared() {
        return handler != null && handler.isPrepared();
    }

    @Override
    public void showFPS(boolean show){
        mShowFps = show;
    }
    private static final int MAX_RECORD_SIZE = 50;
    private static final int ONE_SECOND = 1000;
    private LinkedList<Long> mDrawTimes;

    private boolean mClearFlag;
    private float fps() {
        long lastTime = System.currentTimeMillis();
        mDrawTimes.addLast(lastTime);
        float dtime = lastTime - mDrawTimes.getFirst();
        int frames = mDrawTimes.size();
        if (frames > MAX_RECORD_SIZE) {
            mDrawTimes.removeFirst();
        }
        return dtime > 0 ? mDrawTimes.size() * ONE_SECOND / dtime : 0.0f;
    }
    @Override
    public long drawDanmakus() {
        if (!isSurfaceCreated)
            return 0;
        if (!isShown())
            return -1;
        long stime = System.currentTimeMillis();
        lockCanvas();
        return System.currentTimeMillis() - stime;
    }
    
    @SuppressLint("NewApi")
    private void postInvalidateCompat() {
        mRequestRender = true;
        // 注意：postInvalidateOnAnimation() 依赖 Choreographer 的 VSYNC 帧回调。
        // 在部分设备/ROM（如 I9508V, Android 5.0.1 ART）上 Choreographer 的 doFrame
        // 回调异常延迟（每 ~700ms 才触发一次），导致 onDraw 被拖慢、弹幕+视频一起卡。
        // 改用 postInvalidate()（走主线程消息队列，post 探测证实消息队列响应 <20ms），
        // 不依赖 Choreographer VSYNC，绘制及时。API<16 本来就只能 postInvalidate。
        this.postInvalidate();
    }

    private void lockCanvas() {
        if(mDanmakuVisible == false) {
            return;
        }
        long tStart = System.currentTimeMillis();
        postInvalidateCompat();
        // 诊断：探测主线程空闲度——post 一个任务到主线程，看它多久被调度执行
        // 若延迟巨大，说明主线程被其他工作占满（Choreographer 排不上，onDraw 被推迟）
        final long[] probeLatency = new long[]{-1};
        try {
            post(new Runnable() {
                public void run() {
                    probeLatency[0] = System.currentTimeMillis();
                }
            });
        } catch (Throwable t) {
        }
        int waitRounds = 0;
        synchronized (mDrawMonitor) {
            while ((!mDrawFinished) && (handler != null)) {
                try {
                    mDrawMonitor.wait(200);
                    waitRounds++;
                    if (waitRounds == 1 || waitRounds % 2 == 0) {
                        // 等待超过 200ms 仍未完成：主线程 onDraw 被延迟，记录
                        long probeLag = probeLatency[0] > 0 ? probeLatency[0] - tStart : -1;
                        master.flame.danmaku.util.DiagLogger.diag("Danmaku",
                                "lockWait round=" + waitRounds
                                        + " elapsed=" + (System.currentTimeMillis() - tStart)
                                        + "ms mRequestRender=" + mRequestRender
                                        + " visible=" + mDanmakuVisible
                                        + " mainThreadProbe=" + probeLag + "ms");
                        // 主线程 probe 严重延迟时，dump 主线程调用栈定位阻塞点
                        if (waitRounds >= 2 && probeLag > 300) {
                            dumpMainThreadStack();
                        }
                    }
                } catch (InterruptedException e) {
                    if (mDanmakuVisible == false || handler == null || handler.isStop()) {
                        break;
                    } else {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            mDrawFinished = false;
        }
    }

    private void dumpMainThreadStack() {
        try {
            android.os.Looper mainLooper = android.os.Looper.getMainLooper();
            if (mainLooper == null) return;
            java.lang.Thread mainThread = null;
            try {
                java.lang.reflect.Method gt = android.os.Looper.class.getMethod("getThread");
                mainThread = (java.lang.Thread) gt.invoke(mainLooper);
            } catch (Throwable t) {
                mainThread = null;
            }
            if (mainThread == null) return;
            java.lang.StackTraceElement[] st = mainThread.getStackTrace();
            if (st == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append("MAIN_STACK thread=").append(mainThread.getName()).append(":\n");
            for (int i = 0; i < st.length && i < 20; i++) {
                sb.append("  at ").append(st[i].getClassName())
                        .append(".").append(st[i].getMethodName())
                        .append("(").append(st[i].getFileName())
                        .append(":").append(st[i].getLineNumber()).append(")\n");
            }
            master.flame.danmaku.util.DiagLogger.diag("Danmaku", sb.toString());
        } catch (Throwable t) {
        }
    }
    
    private void lockCanvasAndClear() {
        mClearFlag = true;
        lockCanvas();
    }
    
    private void unlockCanvasAndPost() {
        synchronized (mDrawMonitor) {
            mDrawFinished = true;
            mDrawMonitor.notifyAll();
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        long tStart = System.currentTimeMillis();
        if ((!mDanmakuVisible) && (!mRequestRender)) {
            super.onDraw(canvas);
            return;
        }
        if (mClearFlag) {
            DrawHelper.clearCanvas(canvas);
            mClearFlag = false;
        } else {
            if (handler != null) {
                RenderingState rs = handler.draw(canvas);
                if (mShowFps) {
                    if (mDrawTimes == null)
                        mDrawTimes = new LinkedList<Long>();
                    String fps = String.format(Locale.getDefault(),
                            "fps %.2f,time:%d s,cache:%d,miss:%d", fps(), getCurrentTime() / 1000,
                            rs.cacheHitCount, rs.cacheMissCount);
                    DrawHelper.drawFPS(canvas, fps);
                }
            }
        }
        mRequestRender = false;
        unlockCanvasAndPost();
        // onDraw 自身耗时诊断：区分"调度延迟"与"绘制耗时"
        long tDraw = System.currentTimeMillis() - tStart;
        if (tDraw > 100) {
            master.flame.danmaku.util.DiagLogger.diag("Danmaku",
                    "onDrawCost=" + tDraw + "ms visible=" + mDanmakuVisible
                            + " requestRender=" + mRequestRender);
        }
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (handler != null) {
            handler.notifyDispSizeChanged(right - left, bottom - top);
        }
        isSurfaceCreated = true;
    }

    public void toggle() {
        if (isSurfaceCreated) {
            if (handler == null)
                start();
            else if (handler.isStop()) {
                resume();
            } else
                pause();
        }
    }

    @Override
    public void pause() {
        if (handler != null)
            handler.pause();
    }

    @Override
    public void resume() {
        if (handler != null && handler.isPrepared())
            handler.resume();
        else if (handler == null) {
            restart();
        }
    }
    
    @Override
    public boolean isPaused() {
        if(handler != null) {
            return handler.isStop();
        }
        return false;
    }

    public void restart() {
        stop();
        start();
    }

    @Override
    public void start() {
        start(0);
    }

    @Override
    public void start(long postion) {
        if (handler == null) {
            prepare();
        }else{
            handler.removeCallbacksAndMessages(null);
        }
        handler.obtainMessage(DrawHandler.START, postion).sendToTarget();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null != mTouchHelper) {
            mTouchHelper.onTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    public void seekTo(Long ms) {
        if(handler != null){
            handler.seekTo(ms);
        }
    }


    public void enableDanmakuDrawingCache(boolean enable) {
        mEnableDanmakuDrwaingCache = enable;
    }

    @Override
    public boolean isDanmakuDrawingCacheEnabled() {
        return mEnableDanmakuDrwaingCache;
    }

    @Override
    public boolean isViewReady() {
        return isSurfaceCreated;
    }

    @Override
    public View getView() {
        return this;
    }
      
    @Override
    public void show() {
        showAndResumeDrawTask(null);
    }
    
    @Override
    public void showAndResumeDrawTask(Long position) {
        mDanmakuVisible = true;
        mClearFlag = false;
        if (handler == null) {
            return;
        }
        handler.showDanmakus(position);
    }

    @Override
    public void hide() {
        mDanmakuVisible = false;
        if (handler == null) {
            return;
        }
        handler.hideDanmakus(false);
    }
    
    @Override
    public long hideAndPauseDrawTask() {
        mDanmakuVisible = false;
        if (handler == null) {
            return 0;
        }
        return handler.hideDanmakus(true);
    }

    @Override
    public void clear() {
        if (!isViewReady()) {
            return;
        }
        if (!mDanmakuVisible || Thread.currentThread().getId() == mUiThreadId) {
            mClearFlag = true;
            postInvalidateCompat();
        } else {
            lockCanvasAndClear();
        }
    }

    @Override
    public boolean isShown() {
        return mDanmakuVisible && super.isShown();
    }

    @Override
    public void setDrawingThreadType(int type) {
        mDrawingThreadType  = type;
    }

    @Override
    public long getCurrentTime() {
        if (handler != null) {
            return handler.getCurrentTime();
        }
        return 0;
    }

    @Override
    @SuppressLint("NewApi")
    public boolean isHardwareAccelerated() {
        // >= 3.0（isHardwareAccelerated 是 API 11+ 方法，super 调用在 API<11 上会 VerifyError，
        // 用反射绕过）
        if (getSdkInt() >= 11) {
            try {
                java.lang.reflect.Method m = android.view.View.class.getMethod("isHardwareAccelerated");
                return ((Boolean) m.invoke(this)).booleanValue();
            } catch (Throwable t) {
            }
            return false;
        } else {
            return false;
        }
    }

    @Override
    public void clearDanmakusOnScreen() {
        if (handler != null) {
            handler.clearDanmakusOnScreen();
        }
    }

    @Override
    public void clearCache() {
        if (handler != null) {
            handler.clearCache();
        }
    }

    @Override
    public void setOnDanmakuClickListener(OnDanmakuClickListener listener) {
        mOnDanmakuClickListener = listener;
        setClickable(null != listener);
    }

    @Override
    public OnDanmakuClickListener getOnDanmakuClickListener() {
        return mOnDanmakuClickListener;
    }

}
