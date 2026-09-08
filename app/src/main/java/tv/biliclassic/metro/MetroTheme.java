package tv.biliclassic.metro;

/**
 * Metro 页面主题工具：识别 App 自身夜间模式开关，
 * 夜间时把灰色/深色文字换成白色系（Metro 页面夜间底色为黑）。
 */
public final class MetroTheme {

    private MetroTheme() {
    }

    /** 是否夜间模式（与设置里的"夜间模式"开关一致） */
    public static boolean isNight() {
        return tv.biliclassic.util.SharedPreferencesUtil.getBoolean(
                tv.biliclassic.util.SharedPreferencesUtil.NIGHT_MODE, false);
    }

    /** 灰色辅助文字（#999999）：夜间换白色 */
    public static int grey() {
        return isNight() ? 0xFFCCCCCC : 0xFF999999;
    }

    /** 次级灰色文字（#666666）：夜间换白色 */
    public static int secondary() {
        return isNight() ? 0xFFCCCCCC : 0xFF666666;
    }

    /** 深色主文字（#333333）：夜间换近白 */
    public static int dark() {
        return isNight() ? 0xFFF2F2F2 : 0xFF333333;
    }
}
