package tv.biliclassic.player;

import android.content.Context;
import android.content.Intent;

public final class PlayerIntentFactory {

    private PlayerIntentFactory() {
    }

    public static Intent create(Context context, Class<?> target,
                                String videoUrl, String audioUrl, long durationMs,
                                String title, long aid, long cid, int partIndex,
                                int resumeMs, long[] cids, String[] partNames,
                                String coverUrl, String upName, boolean onlineMode) {
        Intent intent = new Intent(context, target);
        intent.putExtra("video_url", videoUrl);
        if (audioUrl != null && audioUrl.length() > 0) {
            intent.putExtra("audio_url", audioUrl);
        }
        if (durationMs > 0) {
            intent.putExtra("duration_ms", durationMs);
        }
        intent.putExtra("video_title", title);
        intent.putExtra("aid", aid);
        intent.putExtra("cid", cid);
        intent.putExtra("online_mode", onlineMode);
        intent.putExtra("part_index", partIndex);
        if (coverUrl != null) {
            intent.putExtra("cover_url", coverUrl);
        }
        if (upName != null && upName.length() > 0) {
            intent.putExtra("up_name", upName);
        }
        if (cids != null) {
            intent.putExtra("cids", cids);
            intent.putExtra("pagenames", partNames);
        }
        intent.putExtra("resume_position", resumeMs);
        return intent;
    }
}
