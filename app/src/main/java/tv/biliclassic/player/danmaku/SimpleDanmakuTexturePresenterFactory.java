package tv.biliclassic.player.danmaku;

import android.content.Context;
import android.view.View;

/**
 * 单独持有 SimpleDanmakuTexturePresenter（TextureView 是 API 14+）
 *
 * 只有真正选中 TextureView 模式（且 SDK&gt;=14）时才会加载本类，
 * 避免老平台因引用 android.view.TextureView 而拒载调用方
 */
public final class SimpleDanmakuTexturePresenterFactory {

    private SimpleDanmakuTexturePresenterFactory() {
    }

    public static View create(Context context, SimpleDanmakuEngine engine) {
        return new SimpleDanmakuTexturePresenter(context, engine);
    }
}
