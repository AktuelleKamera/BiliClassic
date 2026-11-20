package tv.biliclassic.player;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class PlayerStageEcho {
    private final TextView mView;
    private static final class Line {
        final int order;
        final String text;
        Line(int order, String text) { this.order = order; this.text = text; }
    }

    private final List<Line> mFinished = new ArrayList<Line>();
    private final List<String> mPending = new ArrayList<String>();

    private static int orderOf(String text) {
        if (text == null) return 80;
        if (text.contains("设备信息")) return 10;
        if (text.contains("检查模式")) return 20;
        if (text.contains("弹幕字体")) return 30;
        if (text.contains("弹幕显示")) return 31;
        if (text.contains("弹幕装填类型")) return 32;
        if (text.contains("播放地址") || text.contains("解析视频")) return 40;
        if (text.contains("全舰弹幕")) return 50;
        if (text.contains("初始化播放器")) return 60;
        if (text.contains("开始视频缓冲")) return 70;
        return 80;
    }

    public PlayerStageEcho(TextView view) {
        mView = view;
    }

    public void appendFinished(String text) {
        if (text == null || text.length() == 0) return;
        for (int i = mPending.size() - 1; i >= 0; i--) {
            String p = mPending.get(i);
            if (p.startsWith(text) || text.startsWith(p)) {
                mPending.remove(i);
            }
        }
        for (int i = 0; i < mFinished.size(); i++) {
            if (mFinished.get(i).text.equals(text)) return;
        }
        mFinished.add(new Line(orderOf(text), text));
        render();
    }

    public void appendFinishedWithStatus(String stage, boolean success) {
        appendFinished(stage + (success ? "【完成】" : "【失败】"));
    }

    public void setPending(String text) {
        if (text == null || text.length() == 0) return;
        if (!mPending.contains(text)) mPending.add(text);
        render();
    }

    public void render() {
        if (mView == null) return;
        StringBuilder sb = new StringBuilder();
        List<Line> finished = new ArrayList<Line>(mFinished);
        java.util.Collections.sort(finished, new java.util.Comparator<Line>() {
            public int compare(Line a, Line b) {
                if (a.order != b.order) return a.order < b.order ? -1 : 1;
                return a.text.compareTo(b.text);
            }
        });
        for (int i = 0; i < finished.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(finished.get(i).text);
        }
        List<String> pending = new ArrayList<String>(mPending);
        java.util.Collections.sort(pending, new java.util.Comparator<String>() {
            public int compare(String a, String b) {
                int oa = orderOf(a), ob = orderOf(b);
                if (oa != ob) return oa < ob ? -1 : 1;
                return a.compareTo(b);
            }
        });
        for (int i = 0; i < pending.size(); i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(pending.get(i)).append("…");
        }
        sb.append('>');
        mView.setText(sb.toString());
        mView.setVisibility(TextView.VISIBLE);
    }
}
