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
        if (text.contains("\u8bbe\u5907\u4fe1\u606f")) return 10;
        if (text.contains("\u68c0\u67e5\u6a21\u5f0f")) return 20;
        if (text.contains("\u5f39\u5e55\u5b57\u4f53")) return 30;
        if (text.contains("\u5f39\u5e55\u663e\u793a")) return 31;
        if (text.contains("\u5f39\u5e55\u88c5\u586b\u7c7b\u578b")) return 32;
        if (text.contains("\u64ad\u653e\u5730\u5740") || text.contains("\u89e3\u6790\u89c6\u9891")) return 40;
        if (text.contains("\u5168\u8230\u5f39\u5e55")) return 50;
        if (text.contains("\u521d\u59cb\u5316\u64ad\u653e\u5668")) return 60;
        if (text.contains("\u5f00\u59cb\u89c6\u9891\u7f13\u51b2")) return 70;
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
        mFinished.add(new Line(orderOf(text), text));
        render();
    }

    public void appendFinishedWithStatus(String stage, boolean success) {
        appendFinished(stage + (success ? "\u3010\u5b8c\u6210\u3011" : "\u3010\u5931\u8d25\u3011"));
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
            sb.append(pending.get(i)).append("\u2026");
        }
        sb.append('>');
        mView.setText(sb.toString());
        mView.setVisibility(TextView.VISIBLE);
    }
}
