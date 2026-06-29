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
 */
package tv.biliclassic.model;

import org.json.JSONObject;

public class ApiResult {
    public int code;
    public String message;
    public long offset;
    public long timestamp;
    public String business;
    public boolean isBottom;
    public Object result;

    public ApiResult() {
        this.code = 0;
        this.message = "";
        this.offset = 0;
        this.timestamp = 0;
        this.business = "";
        this.isBottom = false;
        this.result = null;
    }

    public ApiResult(int code, String message) {
        this.code = code;
        this.message = message;
        this.offset = 0;
        this.timestamp = 0;
        this.business = "";
        this.isBottom = false;
        this.result = null;
    }

    public ApiResult(JSONObject fullResult) {
        this.code = fullResult.optInt("code", -1);
        this.message = fullResult.optString("message", "json_format_error");
        this.offset = 0;
        this.timestamp = 0;
        this.business = "";
        this.isBottom = false;
        this.result = null;
    }

    public ApiResult fromJson(JSONObject fullResult) {
        this.code = fullResult.optInt("code", -1);
        this.message = fullResult.optString("message", "json_format_error");
        return this;
    }

    public ApiResult setOffset(long timestamp, long offset, String business) {
        if (timestamp != 0) {
            this.timestamp = timestamp;
        }
        this.offset = offset;
        this.business = business;
        return this;
    }

    public ApiResult setBottom(boolean isBottom) {
        this.isBottom = isBottom;
        return this;
    }

    public ApiResult setResult(Object result) {
        this.result = result;
        return this;
    }
}