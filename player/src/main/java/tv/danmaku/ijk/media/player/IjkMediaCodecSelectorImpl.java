package tv.danmaku.ijk.media.player;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * MediaCodec 硬解候选选择逻辑（从 IjkMediaPlayer$DefaultMediaCodecSelector 迁出）。
 *
 * MediaCodecList/MediaCodecInfo 是 API 16+ 类：这些引用若留在
 * IjkMediaPlayer（或其内部类）的字节码里，硬失败校验平台（2.1 及以下）
 * 在验证外层类解析内部类时会连锁拒载。本类通过 Class.forName 按需反射加载，
 * 仅在 mediacodec 硬解路径（API 16+）上被触碰。
 */
final class IjkMediaCodecSelectorImpl {

    private static final String TAG = IjkMediaCodecSelectorImpl.class.getSimpleName();

    private IjkMediaCodecSelectorImpl() {}

    private static int getSdkInt() {
        try {
            return Build.VERSION.class.getField("SDK_INT").getInt(null);
        } catch (Exception e) {
            try {
                return Integer.parseInt(Build.VERSION.SDK);
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    /** 返回选中的解码器名，无合适候选时返回 null */
    @SuppressWarnings("deprecation")
    public static String onMediaCodecSelect(String mimeType, int profile, int level) {
        if (getSdkInt() < Build.VERSION_CODES.JELLY_BEAN)
            return null;

        if (mimeType == null || mimeType.length() == 0)
            return null;

        Log.i(TAG, String.format(Locale.US, "onSelectCodec: mime=%s, profile=%d, level=%d", mimeType, profile, level));
        ArrayList<IjkMediaCodecInfo> candidateCodecList = new ArrayList<IjkMediaCodecInfo>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            Log.d(TAG, String.format(Locale.US, "  found codec: %s", codecInfo.getName()));
            if (codecInfo.isEncoder())
                continue;

            String[] types = codecInfo.getSupportedTypes();
            if (types == null)
                continue;

            for (String type : types) {
                if (type == null || type.length() == 0)
                    continue;

                Log.d(TAG, String.format(Locale.US, "    mime: %s", type));
                if (!type.equalsIgnoreCase(mimeType))
                    continue;

                IjkMediaCodecInfo candidate = IjkMediaCodecInfo.setupCandidate(codecInfo, mimeType);
                if (candidate == null)
                    continue;

                candidateCodecList.add(candidate);
                Log.i(TAG, String.format(Locale.US, "candidate codec: %s rank=%d", codecInfo.getName(), candidate.mRank));
                candidate.dumpProfileLevels(mimeType);
            }
        }

        if (candidateCodecList.size() == 0) {
            return null;
        }

        IjkMediaCodecInfo bestCodec = candidateCodecList.get(0);

        for (IjkMediaCodecInfo codec : candidateCodecList) {
            if (codec.mRank > bestCodec.mRank) {
                bestCodec = codec;
            }
        }

        if (bestCodec.mRank < IjkMediaCodecInfo.RANK_LAST_CHANCE) {
            Log.w(TAG, String.format(Locale.US, "unaccetable codec: %s", bestCodec.mCodecInfo.getName()));
            return null;
        }

        Log.i(TAG, String.format(Locale.US, "selected codec: %s rank=%d", bestCodec.mCodecInfo.getName(), bestCodec.mRank));
        return bestCodec.mCodecInfo.getName();
    }
}
