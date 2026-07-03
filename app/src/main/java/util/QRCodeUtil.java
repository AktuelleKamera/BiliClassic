package tv.biliclassic.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.swetake.util.Qrcode;

import java.util.Arrays;

public class QRCodeUtil {

    private static final String TAG = "QRCodeUtil";
    private static boolean sQrcodeInited = false;

    // 缓存像素数组 (setPixels 用)
    private static int[] sCachedPixels;
    private static int sCachedWidth;
    private static int sCachedHeight;

    // 缓存黑色像素行 (setPixels 用)
    private static int[] sCachedBlackRow;
    private static int sCachedBlackRowSize;

    // 缓存 Bitmap (Canvas 用)
    private static Bitmap sCachedBitmap;
    private static int sCachedBitmapWidth;
    private static int sCachedBitmapHeight;

    // 在 Application 中调用一次
    public static void init(Context context) {
        if (!sQrcodeInited) {
            Qrcode.init(context);
            sQrcodeInited = true;
            Log.d(TAG, "Qrcode 初始化完成");
        }
    }

    public static Bitmap createQRCodeBitmap(Context context, String content, int width, int height) {
        // 如果没初始化，自动初始化
        if (!sQrcodeInited) {
            Qrcode.init(context);
            sQrcodeInited = true;
            Log.d(TAG, "Qrcode 自动初始化完成");
        }

        if (content == null || content.length() == 0) {
            Log.w(TAG, "内容为空");
            return null;
        }
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "尺寸无效: " + width + "x" + height);
            return null;
        }

        try {
            Qrcode qrcode = new Qrcode();
            qrcode.setQrcodeErrorCorrect('L');
            qrcode.setQrcodeEncodeMode('B');
            qrcode.setQrcodeVersion(7);

            byte[] bytes = content.getBytes("UTF-8");
            boolean[][] code = qrcode.calQrcode(bytes);

            int codeSize = code.length;
            int moduleSize = Math.min(width, height) / (codeSize + 4);
            if (moduleSize < 1) {
                moduleSize = 1;
            }

            int margin = moduleSize / 3;
            if (margin < 1) {
                margin = 1;
            }

            int realSize = codeSize * moduleSize + margin * 2;
            int offsetX = (width - realSize) / 2;
            int offsetY = (height - realSize) / 2;

            if (offsetX < 0) offsetX = 0;
            if (offsetY < 0) offsetY = 0;

            int sdkInt = getSDKInt();

            Bitmap result = null;
            long startTime = System.currentTimeMillis();

            // API 14+ 使用 Canvas drawRect 方案（利用硬件加速）
            // API < 14 使用 setPixels 方案（更稳定）
            if (sdkInt >= 14) {
                Log.d(TAG, "API " + sdkInt + "，尝试 Canvas 绘制策略");
                result = drawWithCanvas(code, codeSize, moduleSize, offsetX, offsetY, margin, width, height);
                if (result != null) {
                    Log.d(TAG, "Canvas 绘制成功 (耗时: " + (System.currentTimeMillis() - startTime) + "ms)");
                    return result;
                }
                Log.w(TAG, "Canvas 绘制失败，回退到 setPixels 策略");
            } else {
                Log.d(TAG, "API " + sdkInt + "，直接使用 setPixels 策略");
            }

            Log.d(TAG, "执行 setPixels 绘制策略");
            result = drawWithSetPixels(code, codeSize, moduleSize, offsetX, offsetY, margin, width, height);
            if (result != null) {
                Log.d(TAG, "setPixels 绘制成功 (耗时: " + (System.currentTimeMillis() - startTime) + "ms)");
                return result;
            }

            Log.e(TAG, "所有绘制策略均失败");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "生成二维码异常: " + e.getMessage(), e);
            return null;
        }
    }

    // 方案一：Canvas.drawRect() 绘制（硬件加速 + Bitmap 缓存）
    private static Bitmap drawWithCanvas(boolean[][] code, int codeSize, int moduleSize,
                                         int offsetX, int offsetY, int margin,
                                         int width, int height) {
        try {
            // 复用 Bitmap
            Bitmap bitmap = getCachedBitmap(width, height);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);

            Rect rect = new Rect();
            int blackCount = 0;

            for (int y = 0; y < codeSize; y++) {
                for (int x = 0; x < codeSize; x++) {
                    if (code[y][x]) {
                        int left = offsetX + margin + x * moduleSize;
                        int top = offsetY + margin + y * moduleSize;
                        int right = left + moduleSize;
                        int bottom = top + moduleSize;

                        if (left < 0) left = 0;
                        if (top < 0) top = 0;
                        if (right > width) right = width;
                        if (bottom > height) bottom = height;

                        if (left < right && top < bottom) {
                            rect.set(left, top, right, bottom);
                            canvas.drawRect(rect, paint);
                            blackCount++;
                        }
                    }
                }
            }

            Log.d(TAG, "Canvas 绘制: 黑块数量=" + blackCount + "，尺寸=" + width + "x" + height);
            return bitmap;

        } catch (Throwable t) {
            Log.w(TAG, "Canvas 绘制失败: " + t.getMessage());
            return null;
        }
    }

    // 方案二：setPixels 批量写入（像素数组缓存 + 黑色行缓存）
    private static Bitmap drawWithSetPixels(boolean[][] code, int codeSize, int moduleSize,
                                            int offsetX, int offsetY, int margin,
                                            int width, int height) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // 复用像素数组
            int[] pixels = getCachedPixels(width, height);

            int white = Color.WHITE;
            int black = Color.BLACK;

            // 使用 Arrays.fill 填充白色
            Arrays.fill(pixels, white);

            // 复用黑色像素行
            int[] blackRow = getCachedBlackRow(moduleSize, black);

            int blackCount = 0;
            int maxX = width;
            int maxY = height;
            int rowLen = moduleSize;

            for (int y = 0; y < codeSize; y++) {
                for (int x = 0; x < codeSize; x++) {
                    if (code[y][x]) {
                        int startX = offsetX + margin + x * moduleSize;
                        int startY = offsetY + margin + y * moduleSize;

                        if (startX >= maxX || startY >= maxY) {
                            continue;
                        }
                        int actualLen = Math.min(rowLen, maxX - startX);

                        for (int dy = 0; dy < moduleSize; dy++) {
                            int py = startY + dy;
                            if (py >= maxY) {
                                break;
                            }
                            int baseIdx = py * width + startX;
                            System.arraycopy(blackRow, 0, pixels, baseIdx, actualLen);
                        }
                        blackCount++;
                    }
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            Log.d(TAG, "setPixels 绘制: 黑块数量=" + blackCount + "，像素数组大小=" + pixels.length);
            return bitmap;

        } catch (Throwable t) {
            Log.w(TAG, "setPixels 绘制失败: " + t.getMessage());
            return null;
        }
    }

    // 获取缓存的像素数组 (setPixels 用)
    private static synchronized int[] getCachedPixels(int width, int height) {
        int size = width * height;
        if (sCachedPixels == null || sCachedPixels.length < size) {
            sCachedPixels = new int[size];
            sCachedWidth = width;
            sCachedHeight = height;
            Log.d(TAG, "分配新像素数组: " + size + " 个元素");
        } else if (sCachedWidth != width || sCachedHeight != height) {
            sCachedWidth = width;
            sCachedHeight = height;
            Log.d(TAG, "复用像素数组，尺寸变更: " + width + "x" + height);
        } else {
            Log.d(TAG, "复用像素数组，尺寸不变: " + width + "x" + height);
        }
        return sCachedPixels;
    }

    // 获取缓存的黑色像素行 (setPixels 用)
    private static synchronized int[] getCachedBlackRow(int moduleSize, int black) {
        if (sCachedBlackRow == null || sCachedBlackRowSize != moduleSize) {
            sCachedBlackRow = new int[moduleSize];
            sCachedBlackRowSize = moduleSize;
            for (int i = 0; i < moduleSize; i++) {
                sCachedBlackRow[i] = black;
            }
            Log.d(TAG, "分配黑色像素行: " + moduleSize + " 个元素");
        } else {
            Log.d(TAG, "复用黑色像素行: " + moduleSize + " 个元素");
        }
        return sCachedBlackRow;
    }

    // 获取缓存的 Bitmap (Canvas 用)
    private static synchronized Bitmap getCachedBitmap(int width, int height) {
        if (sCachedBitmap == null || sCachedBitmapWidth != width || sCachedBitmapHeight != height) {
            sCachedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            sCachedBitmapWidth = width;
            sCachedBitmapHeight = height;
            Log.d(TAG, "分配新 Bitmap: " + width + "x" + height);
        } else {
            Log.d(TAG, "复用 Bitmap: " + width + "x" + height);
        }
        return sCachedBitmap;
    }

    // 获取 SDK_INT，兼容所有版本
    private static int getSDKInt() {
        try {
            Class<?> versionClass = Class.forName("android.os.Build$VERSION");
            java.lang.reflect.Field sdkField = versionClass.getField("SDK_INT");
            return sdkField.getInt(null);
        } catch (Throwable t) {
            Log.d(TAG, "无法获取 SDK_INT，默认 API 1");
            return 1;
        }
    }
}