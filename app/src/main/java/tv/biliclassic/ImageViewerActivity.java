package tv.biliclassic;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.widget.PhotoView;
import tv.biliclassic.widget.PhotoViewPager;

public class ImageViewerActivity extends BaseActivity {

    // 判断是否现代设备（内存充足，Android 4.0+ 且内存大于 48MB）
    private static final boolean IS_MODERN_DEVICE;

    static {
        boolean isModern = false;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 14) {
                long maxMemory = Runtime.getRuntime().maxMemory();
                if (maxMemory > 48 * 1024 * 1024) {
                    isModern = true;
                }
            }
        } catch (Exception e) {
            isModern = false;
        }
        IS_MODERN_DEVICE = isModern;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        final Intent intent = getIntent();
        final ArrayList<String> imageList = intent.getStringArrayListExtra("imageList");
        final int startIndex = intent.getIntExtra("index", 0);

        final PhotoViewPager viewPager = (PhotoViewPager) findViewById(R.id.viewPager);
        final TextView textView = (TextView) findViewById(R.id.text_page);

        final List<View> photoViewList = new ArrayList<View>();

        final GlobalImageCache cache = GlobalImageCache.getInstance();

        // 加载所有图片
        for (int i = 0; i < imageList.size(); i++) {
            final PhotoView photoView = new PhotoView(ImageViewerActivity.this);
            final String url = imageList.get(i);

            // 先检查缓存
            Bitmap cached = cache.get(url);
            if (cached != null && !cached.isRecycled()) {
                photoView.setImageBitmap(cached);
                setMaxScale(photoView, 6.25f);
                photoViewList.add(photoView);
                continue;
            }

            // 异步加载图片
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String imageUrl = url;
                        if (imageUrl.startsWith("https://")) {
                            imageUrl = "http://" + imageUrl.substring(8);
                        }
                        final Bitmap bitmap = downloadImage(imageUrl);
                        if (bitmap != null && !bitmap.isRecycled()) {
                            cache.put(url, bitmap);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    photoView.setImageBitmap(bitmap);
                                    setMaxScale(photoView, 6.25f);
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            photoViewList.add(photoView);
        }

        // 适配器
        tv.biliclassic.adapter.ViewPagerViewAdapter vpiAdapter =
                new tv.biliclassic.adapter.ViewPagerViewAdapter(photoViewList);

        viewPager.setAdapter(vpiAdapter);

        // 跳转到指定页码
        if (startIndex > 0 && startIndex < imageList.size()) {
            viewPager.setCurrentItem(startIndex);
        }

        // 初始化页码显示
        textView.setText("第" + (viewPager.getCurrentItem() + 1) + "/" + imageList.size() + "张");

        // 页码切换监听
        viewPager.setOnPageChangeListener(new android.support.v4.view.ViewPager.OnPageChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                textView.setText("第" + (position + 1) + "/" + imageList.size() + "张");
            }

            @Override
            public void onPageSelected(int position) {
                textView.setText("第" + (position + 1) + "/" + imageList.size() + "张");
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        // 下载按钮
        ImageButton btnDownload = (ImageButton) findViewById(R.id.btn_download);
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = viewPager.getCurrentItem();
                if (position < 0 || position >= imageList.size()) return;
                String url = imageList.get(position);
                saveImageToGallery(url);
            }
        });
    }

    // 兼容方法

    // 通过反射调用 setMaximumScale（兼容 PhotoView 不同版本）
    private void setMaxScale(PhotoView photoView, float maxScale) {
        if (photoView == null) return;
        try {
            java.lang.reflect.Method method = PhotoView.class.getMethod("setMaximumScale", float.class);
            method.invoke(photoView, maxScale);
        } catch (Exception e) {
            // 如果 PhotoView 没有这个方法，忽略
        }
    }

    // 保存图片（重新下载原图）

    private void saveImageToGallery(final String url) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 重新下载原图（不采样，用于保存）
                    final Bitmap originalBitmap = downloadOriginalImage(url);
                    if (originalBitmap == null || originalBitmap.isRecycled()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ImageViewerActivity.this, "获取原图失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    String fileName = "BiliClassic_" + System.currentTimeMillis() + ".jpg";
                    final File file = saveBitmapToFile(originalBitmap, fileName);
                    originalBitmap.recycle();
                    if (file == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ImageViewerActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    notifyGallery(file);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageViewerActivity.this, "已保存: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ImageViewerActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // 下载原图（用于保存，尽量保持清晰）
    private Bitmap downloadOriginalImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            // Android 2.3 兼容：https 转 http
            if (urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();

            // 只做最基础的采样，防止 OOM
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            // 如果图片太大，适当采样防止 OOM（但尽量保持清晰）
            int sampleSize = 1;
            int maxDimension = Math.max(opts.outWidth, opts.outHeight);
            if (maxDimension > 4000) {
                sampleSize = 2;
            }
            if (maxDimension > 8000) {
                sampleSize = 4;
            }

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // 保存到文件

    private File saveBitmapToFile(Bitmap bitmap, String fileName) throws Exception {
        File targetFile = null;

        // 优先尝试保存到 SD 卡 Pictures 目录
        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                File externalDir = new File("/sdcard/Pictures/BiliClassic");
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }
                if (externalDir.exists() && externalDir.canWrite()) {
                    File externalFile = new File(externalDir, fileName);
                    FileOutputStream fos = new FileOutputStream(externalFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                    targetFile = externalFile;
                }
            }
        } catch (Exception e) {
            // 外部存储失败，继续尝试内部存储
        }

        // 如果 SD 卡保存失败，降级到内部存储
        if (targetFile == null) {
            File internalDir = new File(getFilesDir(), "Pictures");
            if (!internalDir.exists()) {
                internalDir.mkdirs();
            }
            File internalFile = new File(internalDir, fileName);
            FileOutputStream fos = new FileOutputStream(internalFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            targetFile = internalFile;
        }

        return targetFile;
    }

    // 通知图库刷新
    private void notifyGallery(File file) {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(android.net.Uri.fromFile(file));
            sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
            // 忽略
        }
    }

    // 显示图片下载（根据设备动态采样）

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            // Android 2.3 兼容：https 转 http
            if (urlStr.startsWith("https://")) {
                urlStr = "http://" + urlStr.substring(8);
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            is = conn.getInputStream();

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            int sampleSize;

            // 现代设备：显示原图（ARGB_8888）
            if (IS_MODERN_DEVICE) {
                sampleSize = 1;
            } else {
                // 老设备：根据图片尺寸降采样
                sampleSize = 4;
                int maxDimension = Math.max(opts.outWidth, opts.outHeight);
                if (maxDimension > 2000) {
                    sampleSize = 4;
                }
                if (maxDimension > 3000) {
                    sampleSize = 6;
                }
                if (maxDimension > 4000) {
                    sampleSize = 8;
                }
                if (maxDimension > 6000) {
                    sampleSize = 12;
                }
            }

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeStream(is, null, opts);
        } catch (OutOfMemoryError e) {
            System.gc();
            // OOM 时固定用 8 倍采样重试
            try {
                if (conn != null) conn.disconnect();
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
                is = conn.getInputStream();
                BitmapFactory.Options optsRetry = new BitmapFactory.Options();
                optsRetry.inSampleSize = 8;
                optsRetry.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(is, null, optsRetry);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}