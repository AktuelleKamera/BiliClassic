package tv.biliclassic.player.danmaku;

import android.content.Context;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.ui.widget.DanmakuTextureView;

/**
 * 单独持有 DanmakuTextureView（TextureView 是 API 14+）
 *
 * 若直接在 DanmakuManager 里 new DanmakuTextureView，老平台(API&lt;14) 的
 * verifier 解析该类方法时会因引用 android.view.TextureView 而拒载整个
 * DanmakuManager。放到独立类里，只有真正选中 TextureView 模式（且 SDK&gt;=14）
 * 时才会加载本类，从而不影响老平台
 */
public final class DanmakuTextureViewFactory {

    private DanmakuTextureViewFactory() {
    }

    public static IDanmakuView create(Context context) {
        return new DanmakuTextureView(context);
    }
}
