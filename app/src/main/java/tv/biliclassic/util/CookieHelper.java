package tv.biliclassic.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

/**
 * Cookie 智能解析与构建工具
 * 支持从文本、JSON、Cookie 字符串中提取关键字段
 * 兼容 Java 5 / Android 2.2+
 */
public class CookieHelper {

    // 提取结果容器
    private static class ExtractedFields {
        String sessdata;
        String biliJct;
        String mid;
        String buvid3;
        String buvid4;
        String sid;
        String refreshToken;
    }

    /**
     * 从用户输入的杂乱文本中智能提取 Cookie 核心字段
     * @param content 用户粘贴的文本（可以是 Cookie 字符串、JSON 或任意包含键值对的文本）
     * @return 标准的 Cookie 字符串，如果未找到 SESSDATA 则返回 null
     */
    public static String parseAndBuildCookie(String content) {
        if (content == null || content.length() == 0) {
            return null;
        }

        ExtractedFields extracted = extractAllFields(content);

        if (extracted.sessdata == null || extracted.sessdata.length() == 0) {
            return null;
        }

        return buildCookieString(extracted);
    }

    private static ExtractedFields extractAllFields(String content) {
        ExtractedFields result = new ExtractedFields();
        result.sessdata = extractValue(content, "SESSDATA");
        result.biliJct = extractValue(content, "bili_jct");
        result.mid = extractValue(content, "DedeUserID");
        result.buvid3 = extractValue(content, "buvid3");
        result.buvid4 = extractValue(content, "buvid4");
        result.sid = extractValue(content, "sid");
        result.refreshToken = extractValue(content, "refresh_token");
        return result;
    }

    private static String extractValue(String content, String key) {
        // 多个匹配模式（支持 Cookie、JSON、带引号等）
        String[] patterns = {
                key + "=([^;\\s\\n\\r\"]+?)(?:;|\\s|$)",
                key + "=\"([^\"]+?)\"",
                "\"" + key + "\"\\s*:\\s*\"([^\"]+?)\"",
                key + "=([^;\\s\\n\\r\"]+?)(?:;|\\s|$)",
                key + "=([^;\\s\\u4e00-\\u9fa5]+)"
        };

        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = Pattern.compile(patterns[i], Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String value = matcher.group(1);
                if (value == null) {
                    continue;
                }
                value = value.trim();
                // 移除末尾残留的分号或空格
                value = value.replaceAll("[;\\s]+$", "");
                if (value.length() > 0) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String buildCookieString(ExtractedFields fields) {
        ArrayList parts = new ArrayList();

        if (fields.sessdata != null && fields.sessdata.length() > 0) {
            parts.add("SESSDATA=" + fields.sessdata);
        }
        if (fields.biliJct != null && fields.biliJct.length() > 0) {
            parts.add("bili_jct=" + fields.biliJct);
        }
        if (fields.mid != null && fields.mid.length() > 0) {
            parts.add("DedeUserID=" + fields.mid);
        }
        if (fields.buvid3 != null && fields.buvid3.length() > 0) {
            parts.add("buvid3=" + fields.buvid3);
        }
        if (fields.buvid4 != null && fields.buvid4.length() > 0) {
            parts.add("buvid4=" + fields.buvid4);
        }
        if (fields.sid != null && fields.sid.length() > 0) {
            parts.add("sid=" + fields.sid);
        }

        return join(parts, "; ");
    }

    // 兼容 Java 5 的 join 方法
    private static String join(ArrayList parts, String separator) {
        if (parts == null || parts.size() == 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}