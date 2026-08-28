package tv.biliclassic.util;

import android.content.Intent;
import android.media.session.MediaSession;
import android.view.KeyEvent;

/**
 * MediaSession.Callback 的独立实现类。
 *
 * MediaSession 是 API 21+ 类，本类继承它，因此只能在 API 21+ 平台上被加载——
 * 由 MediaSessionHelper 通过 Class.forName 按需反射实例化，老平台永不触碰。
 * 不能作为 MediaSessionHelper 的内部类：那样硬失败校验平台上会连带拒载外部类。
 */
public class MediaSessionCallbackImpl extends MediaSession.Callback {

    private final MediaSessionHelper owner;

    public MediaSessionCallbackImpl(MediaSessionHelper owner) {
        this.owner = owner;
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        if (owner != null && owner.handleMediaButton(mediaButtonIntent)) {
            return true;
        }
        return super.onMediaButtonEvent(mediaButtonIntent);
    }

    @Override
    public void onPlay() {
        if (owner != null) {
            owner.firePlayPause();
        }
    }

    @Override
    public void onPause() {
        if (owner != null) {
            owner.firePlayPause();
        }
    }
}
