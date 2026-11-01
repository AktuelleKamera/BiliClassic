package tv.biliclassic.util;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.VideoDetailActivity;

public final class CommentLinkUtil {
    private static final Pattern AV_PATTERN =
            Pattern.compile("(?i)(?<![A-Za-z0-9])av(\\d{1,10})(?![A-Za-z0-9])");
    private static final Pattern BV_PATTERN =
            Pattern.compile("(?i)(?<![A-Za-z0-9])bv([0-9a-z]{10})(?![A-Za-z0-9])");

    private CommentLinkUtil() {
    }

    public static boolean applyLinks(SpannableStringBuilder out, int offset, String text,
                                     final Context context, boolean night) {
        if (out == null || text == null || text.length() == 0) return false;
        boolean found = false;
        final int color = night ? 0xFF64B5F6 : 0xFF1E88E5;
        Matcher av = AV_PATTERN.matcher(text);
        while (av.find()) {
            final long aid;
            try {
                aid = Long.parseLong(av.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            int start = offset + av.start();
            int end = offset + av.end();
            out.setSpan(new ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    try {
                        Intent intent = new Intent(context, VideoDetailActivity.class);
                        intent.putExtra("aid", aid);
                        context.startActivity(intent);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(color);
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            found = true;
        }
        Matcher bv = BV_PATTERN.matcher(text);
        while (bv.find()) {
            final String bvid = "BV" + bv.group(1);
            int start = offset + bv.start();
            int end = offset + bv.end();
            out.setSpan(new ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    try {
                        Intent intent = new Intent(context, VideoDetailActivity.class);
                        intent.putExtra("bvid", bvid);
                        context.startActivity(intent);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(color);
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            found = true;
        }
        return found;
    }
}
