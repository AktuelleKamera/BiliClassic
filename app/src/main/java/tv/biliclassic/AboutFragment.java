package tv.biliclassic;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.graphics.Paint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.Fragment;

import tv.biliclassic.util.SdkHelper;

public class AboutFragment extends Fragment {

    // ===== 遥控器按键导航：官网/帮助/GitHub/2012/哔哩哔哩 纵向链接 =====
    private final java.util.ArrayList<View> mNavItems = new java.util.ArrayList<View>();
    private int mNavIndex = -1;
    private boolean mKeyNavActive = false;

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_about, container, false);

        TextView appBrief = (TextView) view.findViewById(R.id.app_brief);
        if (appBrief != null) {
            String versionName = getVersionName();
            appBrief.setText("哔哩经典 " + versionName + "\n安卓" + (SdkHelper.getSdkInt() < 5 ? "1" : "2") + "也要看B站！");

            // 长按版本号：切换性能日志开关
            appBrief.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    boolean on = tv.biliclassic.util.PerfLog.toggle();
                    if (getActivity() != null) {
                        Toast.makeText(getActivity(), on ? "性能日志已开启" : "性能日志已关闭", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        TextView officialWebsite = (TextView) view.findViewById(R.id.official_website);
        if (officialWebsite != null) {
            officialWebsite.setText(Html.fromHtml("<a href=\"http://www.biliclassic.cn\">官网</a>"));
            officialWebsite.setFocusable(true);
            officialWebsite.setClickable(true);
            officialWebsite.setBackgroundDrawable(createLinkHighlight());
            officialWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra("url", "http://www.biliclassic.cn");
                    intent.putExtra("title", "BiliClassic 官网");
                    startActivity(intent);
                }
            });
            mNavItems.add(officialWebsite);
        }

        final TextView helpWebsite = (TextView) view.findViewById(R.id.help_website);
        if (helpWebsite != null) {
            helpWebsite.setText(Html.fromHtml("<a href=\"about\">帮助</a>"));
            helpWebsite.setFocusable(true);
            helpWebsite.setClickable(true);
            helpWebsite.setBackgroundDrawable(createLinkHighlight());
            helpWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), AboutActivity.class);
                    startActivity(intent);
                }
            });
            mNavItems.add(helpWebsite);
        }

        TextView releaseWebsite = (TextView) view.findViewById(R.id.release_website);
        if (releaseWebsite != null) {
            releaseWebsite.setText(Html.fromHtml("<a href=\"https://github.com/AktuelleKamera/BiliClassic\">GitHub</a>"));
            releaseWebsite.setFocusable(true);
            releaseWebsite.setClickable(true);
            releaseWebsite.setBackgroundDrawable(createLinkHighlight());
            releaseWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra("url", "https://github.com/AktuelleKamera/BiliClassic");
                    intent.putExtra("title", "GitHub");
                    startActivity(intent);
                }
            });
            mNavItems.add(releaseWebsite);
        }

        TextView oldpodsWebsite = (TextView) view.findViewById(R.id.oldpods_website);
        if (oldpodsWebsite != null) {
            oldpodsWebsite.setText(Html.fromHtml("<a href=\"http://2012rs.oldpods.cn\">2012资源站</a>"));
            oldpodsWebsite.setFocusable(true);
            oldpodsWebsite.setClickable(true);
            oldpodsWebsite.setBackgroundDrawable(createLinkHighlight());
            oldpodsWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra("url", "http://2012rs.oldpods.cn");
                    intent.putExtra("title", "2012资源站");
                    startActivity(intent);
                }
            });
            mNavItems.add(oldpodsWebsite);
        }

        TextView bilibiliWebsite = (TextView) view.findViewById(R.id.bilibili_website);
        if (bilibiliWebsite != null) {
            bilibiliWebsite.setText(Html.fromHtml("<a href=\"https://www.bilibili.com\">哔哩哔哩弹幕网</a>"));
            bilibiliWebsite.setFocusable(true);
            bilibiliWebsite.setClickable(true);
            bilibiliWebsite.setBackgroundDrawable(createLinkHighlight());
            bilibiliWebsite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), WebViewActivity.class);
                    intent.putExtra("url", "https://www.bilibili.com");
                    intent.putExtra("title", "哔哩哔哩弹幕网");
                    startActivity(intent);
                }
            });
            mNavItems.add(bilibiliWebsite);
        }

        return view;
    }

    /** 供 MainActivity.dispatchKeyEvent 调用：方向键在链接间移动，确认键触发。 */
    public boolean handleRemoteKey(android.view.KeyEvent event) {
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            return false;
        }
        if (mNavItems.size() == 0) {
            return false;
        }
        if (mNavIndex < 0 || mNavIndex >= mNavItems.size()) {
            mNavIndex = 0;
        }
        // 首次按键启用高亮（触屏机不按键不高亮）
        if (!mKeyNavActive) {
            mKeyNavActive = true;
        }
        if (event.getRepeatCount() != 0) {
            return true;
        }
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
            mNavIndex = Math.max(0, mNavIndex - 1);
            applyNavHighlight();
            return true;
        } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
            mNavIndex = Math.min(mNavItems.size() - 1, mNavIndex + 1);
            applyNavHighlight();
            return true;
        } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            View v = mNavItems.get(mNavIndex);
            if (v != null) {
                v.performClick();
            }
            return true;
        }
        return true;
    }

    /** 选中链接背景变粉高亮，未选中恢复触屏按压背景。 */
    private void applyNavHighlight() {
        for (int i = 0; i < mNavItems.size(); i++) {
            View v = mNavItems.get(i);
            if (v == null) continue;
            if (i == mNavIndex) {
                v.setBackgroundColor(0x66D86DA5);
            } else {
                v.setBackgroundDrawable(createLinkHighlight());
            }
        }
    }

    /** 链接触屏按压时粉色高亮，平时透明。 */
    private android.graphics.drawable.StateListDrawable createLinkHighlight() {
        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable pressed = new android.graphics.drawable.GradientDrawable();
        pressed.setColor(0x66D86DA5);
        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setColor(0x00000000);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private String getVersionName() {
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.4.10";
        }
    }
}