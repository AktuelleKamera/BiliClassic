package tv.biliclassic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

import tv.biliclassic.widget.CropImageView;

/**
 * 图片背景裁剪页：拖动/双指缩放定位，整屏可视区域即裁剪结果
 */
public class CropActivity extends BaseActivity {

    public static final String EXTRA_INPUT = "input_uri";
    public static final String EXTRA_OUTPUT = "output_path";

    private CropImageView cropView;
    private Bitmap src;
    private String outputPath;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyFullscreen();
        setContentView(R.layout.activity_crop);

        cropView = (CropImageView) findViewById(R.id.crop_image);
        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT);

        Button ok = (Button) findViewById(R.id.crop_ok);
        Button cancel = (Button) findViewById(R.id.crop_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCrop();
            }
        });

        load(getIntent().getStringExtra(EXTRA_INPUT));
    }

    /** 全屏延伸到状态栏/刘海区域 */
    private void applyFullscreen() {
        try {
            int sdk = tv.biliclassic.util.SdkHelper.getSdkInt();
            if (sdk >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                java.lang.reflect.Field f = WindowManager.LayoutParams.class
                        .getField("layoutInDisplayCutoutMode");
                f.setInt(lp, 1); // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                getWindow().setAttributes(lp);
            }
            if (sdk >= 11) {
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                try {
                    View.class.getMethod("setSystemUiVisibility", int.class)
                            .invoke(getWindow().getDecorView(), Integer.valueOf(flags));
                } catch (Throwable t) {
                }
            }
        } catch (Throwable t) {
        }
    }

    private void load(final String uriStr) {
        if (uriStr == null || uriStr.length() == 0) {
            finish();
            return;
        }
        final int maxW = getResources().getDisplayMetrics().widthPixels * 2;
        final int maxH = getResources().getDisplayMetrics().heightPixels * 2;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = null;
                try {
                    Uri uri = Uri.parse(uriStr);
                    InputStream is = getContentResolver().openInputStream(uri);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, o);
                    is.close();
                    int sample = 1;
                    while (o.outWidth / sample > maxW || o.outHeight / sample > maxH) {
                        sample <<= 1;
                    }
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    is = getContentResolver().openInputStream(uri);
                    bitmap = BitmapFactory.decodeStream(is, null, o2);
                    is.close();
                } catch (Exception e) {
                    bitmap = null;
                }
                final Bitmap b = bitmap;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (b == null) {
                            Toast.makeText(CropActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        src = b;
                        cropView.setBitmap(b);
                    }
                });
            }
        }).start();
    }

    private void doCrop() {
        if (saving || src == null) {
            return;
        }
        saving = true;
        final Bitmap out = cropView.crop();
        if (out == null) {
            saving = false;
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    FileOutputStream fos = new FileOutputStream(outputPath);
                    out.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                    fos.close();
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                }
                out.recycle();
                final boolean fok = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fok) {
                            Intent data = new Intent();
                            data.putExtra(EXTRA_OUTPUT, outputPath);
                            setResult(RESULT_OK, data);
                        } else {
                            Toast.makeText(CropActivity.this, "裁剪失败", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_CANCELED);
                        }
                        finish();
                    }
                });
            }
        }).start();
    }
}
