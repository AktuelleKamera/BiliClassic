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

        public void onClick(View widget) {
        }
}