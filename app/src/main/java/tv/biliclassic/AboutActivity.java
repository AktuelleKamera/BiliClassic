package tv.biliclassic;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bili_about);

        // 获取版本号
        String versionName = "0.4.0";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "0.4.0";
        }

        // 应用名称和版本号
        TextView appBrief = (TextView) findViewById(R.id.app_brief);
        appBrief.setText("哔哩经典 " + versionName);

        // 反馈论坛链接
        TextView releaseWebsite = (TextView) findViewById(R.id.release_website);
        releaseWebsite.setText(Html.fromHtml("<a href=\"https://github.com/AktuelleKamera/BiliClassic\">GitHub Issues</a>"));
        releaseWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        // B 站链接
        TextView bilibiliWebsite = (TextView) findViewById(R.id.bilibili_website);
        bilibiliWebsite.setText(Html.fromHtml("<a href=\"https://www.bilibili.com\">哔哩哔哩弹幕网</a>"));
        bilibiliWebsite.setMovementMethod(LinkMovementMethod.getInstance());
    }
}