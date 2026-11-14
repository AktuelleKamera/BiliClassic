package tv.biliclassic.util;

import android.view.KeyEvent;

public final class KeyBindingRecorder {

    public static final int RESULT_NOT_HANDLED = 0;
    public static final int RESULT_CONSUMED = 1;
    public static final int RESULT_ABORT = 2;

    public interface Listener {
        void onRecordingChanged();
        void onFinished();
    }

    private static final int[] RECORD_ORDER = {
            KeyBindingUtil.ACTION_MENU,
            KeyBindingUtil.ACTION_RETURN,
            KeyBindingUtil.ACTION_UP,
            KeyBindingUtil.ACTION_DOWN,
            KeyBindingUtil.ACTION_LEFT,
            KeyBindingUtil.ACTION_RIGHT,
            KeyBindingUtil.ACTION_CONFIRM,
            KeyBindingUtil.ACTION_PAGE_UP,
            KeyBindingUtil.ACTION_PAGE_DOWN,
            KeyBindingUtil.ACTION_REFRESH
    };

    private static final String[] RECORD_NAMES = {
            "菜单键", "返回键", "上键", "下键",
            "左键", "右键", "确认键",
            "上一页", "下一页", "刷新键"
    };

    private final Listener listener;
    private boolean recording;
    private int index;
    private long lastRecordTime;

    public KeyBindingRecorder(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        recording = true;
        index = 0;
        lastRecordTime = 0L;
        KeyBindingUtil.clearAll();
        if (listener != null) listener.onRecordingChanged();
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isFinished() {
        return index >= RECORD_ORDER.length;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return RECORD_ORDER.length;
    }

    public String getCurrentName() {
        return isFinished() ? "" : RECORD_NAMES[index];
    }

    public int handleKeyEvent(KeyEvent event) {
        if (!recording || event == null
                || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() != 0
                || isFinished()) {
            return RESULT_NOT_HANDLED;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && RECORD_ORDER[index] != KeyBindingUtil.ACTION_RETURN) {
            return RESULT_ABORT;
        }
        long now = System.currentTimeMillis();
        if (now - lastRecordTime < 1000L) {
            return RESULT_CONSUMED;
        }
        lastRecordTime = now;
        KeyBindingUtil.saveKey(RECORD_ORDER[index], event.getKeyCode());
        index++;
        if (listener != null) {
            if (isFinished()) listener.onFinished();
            else listener.onRecordingChanged();
        }
        return RESULT_CONSUMED;
    }
}
