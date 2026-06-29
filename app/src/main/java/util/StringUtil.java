/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 *
 * 这是清朝版本的StringUtil QwQ
 */
package tv.biliclassic.util;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {

    /**
     * 追加字符串
     */
    public static void appendString(SpannableStringBuilder stringBuilder, String str) {
        if (stringBuilder == null) {
            return;
        }
        stringBuilder.append(str);
    }

    /**
     * 单位转换
     */
    public static String toWan(long num) {
        if (num >= 100000000) {
            float value = (float) num / 100000000;
            String result = formatFloat(value);
            return result + "亿";
        } else if (num >= 10000) {
            float value = (float) num / 10000;
            String result = formatFloat(value);
            return result + "万";
        } else {
            return String.valueOf(num);
        }
    }

    /**
     * 手动格式化浮点数
     */
    private static String formatFloat(float value) {
        int intPart = (int) value;
        int fracPart = (int) ((value - intPart) * 10);
        String result = String.valueOf(intPart);
        if (fracPart > 0) {
            result = result + "." + String.valueOf(fracPart);
        }
        return result;
    }

    /**
     * 时间格式化
     */
    public static String toTime(int progress) {
        int hour = progress / 3600;
        int minute = (progress % 3600) / 60;
        int second = progress % 60;

        String hourStr = padZero(hour);
        String minStr = padZero(minute);
        String secStr = padZero(second);

        if (hour > 0) {
            return hourStr + ":" + minStr + ":" + secStr;
        } else {
            return minStr + ":" + secStr;
        }
    }

    /**
     * 补零
     */
    private static String padZero(int num) {
        if (num < 10) {
            return "0" + num;
        }
        return String.valueOf(num);
    }

    /**
     * HTML 转字符串
     */
    public static String htmlToString(String html) {
        if (html == null) {
            return "";
        }
        String result = html;
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&quot;", "\"");
        result = result.replace("&amp;", "&");
        result = result.replace("&#39;", "'");
        result = result.replace("&#34;", "\"");
        result = result.replace("&#38;", "&");
        result = result.replace("&#60;", "<");
        result = result.replace("&#62;", ">");
        return result;
    }

    public static String htmlReString(String html) {
        if (html == null) {
            return "";
        }
        String result = html;
        result = result.replace("<p>", "");
        result = result.replace("</p>", "\n");
        result = result.replace("<br>", "\n");
        result = result.replace("<em class=\"keyword\">", "");
        result = result.replace("</em>", "");
        return result;
    }

    /**
     * 移除 HTML 标签
     */
    public static String removeHtml(String html) {
        if (html == null) {
            return "";
        }
        try {
            Pattern pattern = Pattern.compile("<[^>]*>");
            Matcher matcher = pattern.matcher(html);
            return matcher.replaceAll("");
        } catch (Exception e) {
            return html.replaceAll("<[^>]*>", "");
        }
    }

    public static String unEscape(String str) {
        if (str == null) {
            return "";
        }
        return str.replaceAll("\\\\(.)", "$1");
    }

    public static void setTopSpan(SpannableStringBuilder spannableString) {
        if (spannableString == null) {
            return;
        }
        String text = spannableString.toString();
        if (text != null && text.length() > 0 && text.startsWith("置顶")) {
            try {
                spannableString.setSpan(new ForegroundColorSpan(Color.rgb(207, 75, 95)),
                        0, "置顶".length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /**
     * 难绷的 ClickableSpan 实现
     */
    public static class SimpleClickableSpan extends ClickableSpan {
        private final String url;

        public SimpleClickableSpan(String url) {
            this.url = url;
        }

        public void onClick(View widget) {
        }

        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
            ds.setColor(Color.rgb(0x66, 0xcc, 0xff));
        }
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return str != null && str.length() > 0;
    }
}