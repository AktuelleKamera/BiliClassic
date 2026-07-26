package tv.biliclassic;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CrashReportActivity extends BaseActivity {

    private TextView crashInfoText;
    private Button btnCopy;
    private Button btnShare;
    private Button btnExit;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_report);

        crashInfoText = (TextView) findViewById(R.id.crash_info_text);
        btnCopy = (Button) findViewById(R.id.btn_copy);
        btnShare = (Button) findViewById(R.id.btn_share);
        btnExit = (Button) findViewById(R.id.btn_exit);
        scrollView = (ScrollView) findViewById(R.id.scroll_view);

        String crashInfo = getIntent().getStringExtra("crash_info");
        if (crashInfo != null && crashInfo.length() > 0) {
            crashInfoText.setText(crashInfo);
        } else {
            crashInfoText.setText("未知错误");
        }

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyCrashInfo();
            }
        });

        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCrashInfo();
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                System.exit(0);
            }
        });
    }

    private void copyCrashInfo() {
        String info = crashInfoText.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(info);
        Toast.makeText(this, "崩溃日志已复制", Toast.LENGTH_SHORT).show();
    }

    private void shareCrashInfo() {
        String info = crashInfoText.getText().toString();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "BiliClassic 崩溃日志：\n\n" + info + "\n\n" +
                        "——————\n" +
                        "来自 BiliClassic - 支持安卓1.6+的B站客户端");
        startActivity(Intent.createChooser(shareIntent, "分享崩溃日志"));
    }
}