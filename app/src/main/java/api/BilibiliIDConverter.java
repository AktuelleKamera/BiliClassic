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
 * 修改时间：2026年6月18日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.api;

import java.util.HashMap;

public class BilibiliIDConverter {
    private static final String table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF";
    private static final int[] s = {11, 10, 3, 8, 4, 6};
    private static final int xor = 177451812;
    private static final long add = 8728348608L;

    private static final HashMap<Character, Integer> tr = new HashMap<Character, Integer>();

    static {
        for (int i = 0; i < 58; i++) {
            tr.put(table.charAt(i), i);
        }
    }

    /**
     * BV 号转 AV 号
     * @param bv BV 号（如 "BV1xx..."）
     * @return AV 号（如 116707616）
     */
    public static long bvtoaid(String bv) {
        if (bv == null || bv.length() < 12) {
            return 0;
        }
        long x = 0;
        long pow58 = 1;
        for (int i = 0; i < 6; i++) {
            Integer temp = tr.get(bv.charAt(s[i]));
            if (temp != null) {
                x += (long) temp * pow58;
            }
            pow58 *= 58;
        }
        x = (x - add) ^ xor;
        return x;
    }

    /**
     * AV 号转 BV 号
     * @param aid AV 号
     * @return BV 号
     */
    public static String aidtobv(long aid) {
        if (aid <= 0) {
            return "";
        }
        long x = (aid ^ xor) + add;
        StringBuilder r = new StringBuilder("BV1  4 1 7  ");
        long pow58 = 1;
        for (int i = 0; i < 6; i++) {
            r.setCharAt(s[i], table.charAt((int) ((x / pow58) % 58)));
            pow58 *= 58;
        }
        return r.toString();
    }
}