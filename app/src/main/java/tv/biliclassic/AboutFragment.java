package tv.biliclassic;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;
import android.graphics.Paint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.Fragment;

public class AboutFragment extends Fragment {

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_about, container, false);

        TextView appBrief = (TextView) view.findViewById(R.id.app_brief);
        String versionName = getVersionName();
        appBrief.setText("哔哩经典 " + versionName + "\n安卓2也要看B站！");

        TextView officialWebsite = (TextView) view.findViewById(R.id.official_website);
        if (officialWebsite != null) {
            officialWebsite.setText(Html.fromHtml("<a href=\"http://www.biliclassic.cn\">官网</a>"));
            officialWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra("url", "http://www.biliclassic.cn");
                    intent.putExtra("title", "BiliClassic 官网");
                    startActivity(intent);
                }
            });
        }

        final TextView helpWebsite = (TextView) view.findViewById(R.id.help_website);
        if (helpWebsite != null) {
            helpWebsite.setText(Html.fromHtml("<a href=\"about\">帮助</a>"));
            helpWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), AboutActivity.class);
                    startActivity(intent);
                }
            });
        }

        TextView releaseWebsite = (TextView) view.findViewById(R.id.release_website);
        releaseWebsite.setText(Html.fromHtml("<a href=\"https://github.com/AktuelleKamera/BiliClassic\">GitHub</a>"));
        releaseWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        TextView bilibiliWebsite = (TextView) view.findViewById(R.id.bilibili_website);
        bilibiliWebsite.setText(Html.fromHtml("<a href=\"https://www.bilibili.com\">哔哩哔哩弹幕网</a>"));
        bilibiliWebsite.setMovementMethod(LinkMovementMethod.getInstance());

        return view;
    }

    private String getVersionName() {
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.4.5";
        }
    }
}