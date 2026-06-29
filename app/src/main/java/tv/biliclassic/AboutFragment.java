package tv.biliclassic;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

// 使用 android.support.v4.app.Fragment（ActionBarSherlock 自带的）
import android.support.v4.app.Fragment;

public class AboutFragment extends Fragment {

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_about, container, false);

        TextView appBrief = (TextView) view.findViewById(R.id.app_brief);
        String versionName = "0.4.0";
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "0.4.0";
        }
        appBrief.setText("哔哩经典 " + versionName + "\n安卓2也要看B站！");

        TextView officialWebsite = (TextView) view.findViewById(R.id.official_website);
        if (officialWebsite != null) {
            officialWebsite.setText(Html.fromHtml("<a href=\"http://www.biliclassic.cn\">官网</a>"));
            officialWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        TextView releaseWebsite = (TextView) view.findViewById(R.id.release_website);
        releaseWebsite.setText(Html.fromHtml("<a href=\"https://github.com/AktuelleKamera/BiliClassic\">GitHub</a>"));
        releaseWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        TextView bilibiliWebsite = (TextView) view.findViewById(R.id.bilibili_website);
        bilibiliWebsite.setText(Html.fromHtml("<a href=\"https://www.bilibili.com\">哔哩哔哩弹幕网</a>"));
        bilibiliWebsite.setMovementMethod(LinkMovementMethod.getInstance());
        }

        return view;
    }
}