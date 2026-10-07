package tv.biliclassic.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;

/**
 * 夜间/半透明皮肤：把写死的白色系背景与深色文字就地替换。
 * 顶栏统一半透明，列表/面板背景按夜间或自定义背景图切换。
 */
public final class UiSkin {

    private static final java.util.WeakHashMap<View, Integer> STATE =
            new java.util.WeakHashMap<View, Integer>();
    // 记录被改写前的背景/文字色，关夜间或关半透时还原
    private static final java.util.WeakHashMap<View, Object> BG_ORIG =
            new java.util.WeakHashMap<View, Object>();
    private static final java.util.WeakHashMap<View, Integer> TEXT_ORIG =
            new java.util.WeakHashMap<View, Integer>();
    private static final Object NULL_BG = new Object();
    private static android.graphics.drawable.Drawable.ConstantState sWhiteItemCs;
    private static android.graphics.drawable.Drawable.ConstantState sThumbBorderCs;
    private static android.graphics.drawable.Drawable.ConstantState sDayTextureCs;
    private static Bitmap sSample;
    private static Canvas sSampleCanvas;

    private UiSkin() {
    }

    public static boolean isNight() {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_MODE, false);
    }

    /**
     * 供列表 adapter 在绑定时调用：对单个 item 视图树套用夜间/半透明皮肤。
     * 列表项在滚动时动态创建，{@link #apply(Activity)} 的遍历覆盖不到，需逐项处理。
     */
    public static void recolorItem(View v) {
        if (v == null) return;
        final boolean night = isNight();
        final boolean trans = BgUtil.hasImage();
        // 白天且无自定义背景图、又没有已涂过色需要还原的视图时直接返回，避免整树遍历（软件渲染下换页/滚动的大头）
        if (!night && !trans && BG_ORIG.isEmpty() && TEXT_ORIG.isEmpty()) {
            return;
        }
        try {
            recolor(v, night, trans, false);
        } catch (Throwable t) {
        }
    }

    /** 设置背景资源但保留 View 的 padding：旧 ROM（如 1.5）setBackgroundDrawable 会把 XML padding 重置为 0 */
    public static void setBgResourceKeepPadding(View v, int res) {
        if (v == null) return;
        int l = v.getPaddingLeft(), t = v.getPaddingTop();
        int r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackgroundResource(res);
        if (v.getPaddingLeft() != l || v.getPaddingTop() != t
                || v.getPaddingRight() != r || v.getPaddingBottom() != b) {
            v.setPadding(l, t, r, b);
        }
    }

    /** 同上，纯色背景 */
    public static void setBgColorKeepPadding(View v, int color) {
        if (v == null) return;
        int l = v.getPaddingLeft(), t = v.getPaddingTop();
        int r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setBackgroundColor(color);
        if (v.getPaddingLeft() != l || v.getPaddingTop() != t
                || v.getPaddingRight() != r || v.getPaddingBottom() != b) {
            v.setPadding(l, t, r, b);
        }
    }

    /** 供动态列表使用：返回适配夜间/半透明的行背景色 */
    public static int surface(int day) {
        if (isNight()) {
            return 0xFF171717;
        }
        if (BgUtil.hasImage()) {
            return 0xB3000000 | (day & 0x00FFFFFF);
        }
        return day;
    }

    /** 遍历视图树，按夜间/半透明改写背景与文字颜色 */
    public static void apply(Activity a) {
        if (a == null) {
            return;
        }
        boolean night = isNight();
        boolean trans = BgUtil.hasImage();
        boolean settings = a instanceof SettingsActivity;
        // 白天、无自定义背景、且没有任何已套过皮肤的痕迹时无需遍历
        if (!night && !trans && !settings && BG_ORIG.isEmpty() && TEXT_ORIG.isEmpty()) {
            return;
        }
        try {
            View root = a.getWindow().getDecorView();
            recolor(root, night, trans, settings);
            applyBars(a, night, trans);
            applyPanels(a, night, trans);
        } catch (Throwable t) {
        }
    }

    private static void applyBars(Activity a, boolean night, boolean trans) {
        try {
            View tb = a.findViewById(R.id.title_bar);
            if (tb == null) {
                return;
            }
            if (night) {
                markBg(tb, 0xCC101010, 11);
            } else if (trans) {
                markBg(tb, 0xCCFFFFFF, 12);
            } else {
                restoreBg(tb);
            }
        } catch (Throwable t) {
        }
    }

    private static void applyPanels(Activity a, boolean night, boolean trans) {
        try {
            View sh = a.findViewById(R.id.main_search_history);
            if (sh == null) {
                return;
            }
            if (night) {
                markBg(sh, 0xE6101010, 21);
            } else if (trans) {
                markBg(sh, 0xE6F5F5F5, 22);
            } else {
                restoreBg(sh);
            }
        } catch (Throwable t) {
        }
    }

    private static void markBg(View v, int color, int want) {
        Integer cur = STATE.get(v);
        if (cur != null && cur.intValue() == want) {
            return;
        }
        rememberBg(v);
        setBgColor(v, color);
        STATE.put(v, Integer.valueOf(want));
    }

    private static void markBgRes(View v, int res, int want) {
        Integer cur = STATE.get(v);
        if (cur != null && cur.intValue() == want) {
            return;
        }
        rememberBg(v);
        setBgRes(v, res);
        STATE.put(v, Integer.valueOf(want));
    }

    /** 记下被改前的背景（纯色存 Integer，其余存 Drawable），还原时用 */
    private static void rememberBg(View v) {
        if (!BG_ORIG.containsKey(v)) {
            Drawable d = v.getBackground();
            if (d instanceof ColorDrawable) {
                BG_ORIG.put(v, Integer.valueOf(drawableColor(d)));
            } else {
                BG_ORIG.put(v, d == null ? NULL_BG : (Object) d);
            }
        }
    }

    /** 该 View 背景是否就是列表项通用白色底 item_click_effect_white（按 ConstantState 比对） */
    private static boolean isWhiteItemBg(View v, Drawable d) {
        try {
            if (sWhiteItemCs == null) {
                sWhiteItemCs = v.getContext().getResources()
                        .getDrawable(R.drawable.item_click_effect_white).getConstantState();
            }
            android.graphics.drawable.Drawable.ConstantState cs = d.getConstantState();
            return cs != null && cs == sWhiteItemCs;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 封面白色边框 bili_thumb_boarder（按 ConstantState 比对） */
    private static boolean isThumbBorderBg(View v, Drawable d) {
        try {
            if (sThumbBorderCs == null) {
                sThumbBorderCs = v.getContext().getResources()
                        .getDrawable(R.drawable.bili_thumb_boarder).getConstantState();
            }
            android.graphics.drawable.Drawable.ConstantState cs = d.getConstantState();
            return cs != null && cs == sThumbBorderCs;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 该 View 背景是否就是白天的纸纹 bili_texture_background（按 ConstantState 比对） */
    private static boolean isDayTextureBg(View v, Drawable d) {
        try {
            if (sDayTextureCs == null) {
                sDayTextureCs = v.getContext().getResources()
                        .getDrawable(R.drawable.bili_texture_background).getConstantState();
            }
            android.graphics.drawable.Drawable.ConstantState cs = d.getConstantState();
            return cs != null && cs == sDayTextureCs;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void restoreBg(View v) {
        if (!BG_ORIG.containsKey(v)) {
            return;
        }
        Object o = BG_ORIG.remove(v);
        STATE.remove(v);
        int l = v.getPaddingLeft();
        int t = v.getPaddingTop();
        int r = v.getPaddingRight();
        int b = v.getPaddingBottom();
        try {
            if (o == NULL_BG) {
                v.setBackgroundDrawable(null);
            } else if (o instanceof Integer) {
                v.setBackgroundColor(((Integer) o).intValue());
            } else {
                v.setBackgroundDrawable((Drawable) o);
            }
        } catch (Throwable t2) {
        }
        v.setPadding(l, t, r, b);
    }

    /**
     * 老 ROM 上换背景会把 View 原有的 padding 清掉（文字贴边），换完补回来。
     */
    private static void setBgColor(View v, int color) {
        int l = v.getPaddingLeft();
        int t = v.getPaddingTop();
        int r = v.getPaddingRight();
        int b = v.getPaddingBottom();
        v.setBackgroundColor(color);
        v.setPadding(l, t, r, b);
    }

    private static void setBgRes(View v, int res) {
        int l = v.getPaddingLeft();
        int t = v.getPaddingTop();
        int r = v.getPaddingRight();
        int b = v.getPaddingBottom();
        v.setBackgroundResource(res);
        v.setPadding(l, t, r, b);
    }

    private static void recolor(View v, boolean night, boolean trans, boolean settings) {
        if (v == null) {
            return;
        }
        try {
            if (settings && v.getBackground() instanceof android.graphics.drawable.LayerDrawable) {
                int want = night ? 1 : (trans ? 2 : 3);
                Integer cur = STATE.get(v);
                // 默认白天且无自定义背景时不重设，避免老 ROM 重设共享 drawable 出问题
                if (!(cur == null && want == 3)) {
                    if (want == 1) {
                        markBgRes(v, R.drawable.settings_item_bg_night, 1);
                    } else if (want == 2) {
                        markBgRes(v, R.drawable.settings_item_bg_translucent, 2);
                    } else {
                        markBgRes(v, R.drawable.settings_item_bg, 3);
                    }
                }
            } else {
                Drawable d = v.getBackground();
                if (d instanceof ColorDrawable) {
                    if (night || trans) {
                        if (!BG_ORIG.containsKey(v)) {
                            BG_ORIG.put(v, Integer.valueOf(drawableColor(d)));
                        }
                        Object o = BG_ORIG.get(v);
                        int orig = (o instanceof Integer)
                                ? ((Integer) o).intValue() : drawableColor(d);
                        // 细线（分割线）单独映射成线色，否则 #E0E0E0 这类浅灰会被当面板→夜间看不见
                        int nc = isThinDivider(v)
                                ? (night ? 0x33FFFFFF : 0x66D9D9D9)
                                : mapColor(orig, night, trans);
                        if (drawableColor(v.getBackground()) != nc) {
                            setBgColor(v, nc);
                        }
                    } else {
                        restoreBg(v);
                    }
                } else if (!night && BG_ORIG.containsKey(v)) {
                    // 白天：还原之前被换掉的列表底
                    restoreBg(v);
                } else if (night && isWhiteItemBg(v, d)) {
                    // 列表项通用白色底（selector）→ 夜间深灰版
                    rememberBg(v);
                    setBgRes(v, R.drawable.item_click_effect_grey);
                } else if (night && isThumbBorderBg(v, d)) {
                    // 封面白框 → 夜间深色框
                    rememberBg(v);
                    setBgRes(v, R.drawable.bili_thumb_boarder_night);
                } else if (night && isDayTextureBg(v, d)) {
                    // 白天纸纹 → 夜间纸纹（否则整页仍是亮的）
                    rememberBg(v);
                    setBgRes(v, R.drawable.bili_texture_background_night);
                }
            }
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                if (night) {
                    if (!TEXT_ORIG.containsKey(v)) {
                        TEXT_ORIG.put(v, Integer.valueOf(t.getCurrentTextColor()));
                    }
                    int orig = TEXT_ORIG.get(v).intValue();
                    int ntc = mapText(orig, night);
                    if (t.getCurrentTextColor() != ntc) {
                        t.setTextColor(ntc);
                    }
                } else if (TEXT_ORIG.containsKey(v)) {
                    int orig = TEXT_ORIG.remove(v).intValue();
                    if (t.getCurrentTextColor() != orig) {
                        t.setTextColor(orig);
                    }
                }
            }
        } catch (Throwable t) {
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                recolor(g.getChildAt(i), night, trans, settings);
            }
        }
    }

    /** 取纯色背景的颜色：优先反射 getColor，缺失时把 drawable 画到 1x1 取色 */
    public static int drawableColor(Drawable d) {
        try {
            Object c = ColorDrawable.class.getMethod("getColor").invoke(d);
            if (c instanceof Integer) {
                return ((Integer) c).intValue();
            }
        } catch (Throwable t) {
        }
        try {
            if (sSample == null) {
                sSample = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                sSampleCanvas = new Canvas(sSample);
            }
            Rect old = d.getBounds();
            d.setBounds(0, 0, 1, 1);
            sSample.eraseColor(0);
            d.draw(sSampleCanvas);
            d.setBounds(old);
            return sSample.getPixel(0, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 是否是细线分割线（高度 ≤ 2dp） */
    private static boolean isThinDivider(View v) {
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null || lp.height <= 0) {
            return false;
        }
        float dens = v.getResources().getDisplayMetrics().density;
        return lp.height <= (int) (2 * dens + 0.5f);
    }

    private static int mapColor(int c, boolean night, boolean trans) {
        if (!night && !trans) {
            return c;
        }
        int a = (c >>> 24) & 0xFF;
        if (a == 0) {
            return c;
        }
        int r = (c >>> 16) & 0xFF;
        int g = (c >>> 8) & 0xFF;
        int b = c & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        int sat = max - min;
        // 浅灰/白色系表面：夜间转深色，半透明模式转半透明
        if (sat <= 30 && lum >= 200) {
            return night ? (trans ? 0xCC101010 : 0xE6161616)
                    : (0xB3000000 | (c & 0x00FFFFFF));
        }
        // 分割线灰
        if (sat <= 30 && lum >= 150) {
            return night ? 0x33FFFFFF : (trans ? 0x66D9D9D9 : c);
        }
        return c;
    }

    private static int mapText(int c, boolean night) {
        if (!night) {
            return c;
        }
        if (c == 0xFF000000 || c == 0xFF333333) {
            return 0xFFE6E6E6;
        }
        if (c == 0xFF555555 || c == 0xFF666666 || c == 0xFF999999) {
            return 0xFFB0B0B0;
        }
        return c;
    }
}
