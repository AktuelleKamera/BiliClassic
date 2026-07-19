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

        TextView appBrief = (TextView) findViewById(R.id.app_brief);
        String versionName = getVersionName();
        appBrief.setText("哔哩经典 " + versionName);

        TextView releaseWebsite = (TextView) findViewById(R.id.release_website);
        releaseWebsite.setText(Html.fromHtml("<a href=\"https://github.com/AktuelleKamera/BiliClassic\">GitHub Issues</a>"));
        releaseWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        TextView bilibiliWebsite = (TextView) findViewById(R.id.bilibili_website);
        bilibiliWebsite.setText(Html.fromHtml("<a href=\"https://www.bilibili.com\">哔哩哔哩弹幕网</a>"));
        bilibiliWebsite.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private String getVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.4.8";
        }
    }
}