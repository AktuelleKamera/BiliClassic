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

    // 缓存像素数组
    private static int[] sCachedPixels;
    private static int sCachedWidth;
    private static int sCachedHeight;

    public static Bitmap createQRCodeBitmap(Context context, String content, int width, int height) {
        Qrcode.init(context);

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
            qrcode.setQrcodeVersion(10);

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

            boolean isHardwareAccelerated = isHardwareAccelerationSupported();

            Bitmap result = null;
            long startTime = System.currentTimeMillis();

            if (isHardwareAccelerated) {
                Log.d(TAG, "检测到硬件加速支持，尝试 Canvas 绘制策略");
                result = drawWithCanvas(code, codeSize, moduleSize, offsetX, offsetY, margin, width, height);
                if (result != null) {
                    Log.d(TAG, "Canvas 绘制成功 (耗时: " + (System.currentTimeMillis() - startTime) + "ms)");
                    return result;
                }
                Log.w(TAG, "Canvas 绘制失败，回退到 setPixels 策略");
            } else {
                Log.d(TAG, "未检测到硬件加速，直接使用 setPixels 策略");
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

    // 方案一：Canvas.drawRect() 绘制（硬件加速）
    private static Bitmap drawWithCanvas(boolean[][] code, int codeSize, int moduleSize,
                                         int offsetX, int offsetY, int margin,
                                         int width, int height) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
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

    // 方案二：setPixels 批量写入（兼容模式）
    private static Bitmap drawWithSetPixels(boolean[][] code, int codeSize, int moduleSize,
                                            int offsetX, int offsetY, int margin,
                                            int width, int height) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // 复用像素数组
            int[] pixels = getCachedPixels(width, height);

            int white = Color.WHITE;
            int black = Color.BLACK;

            // 用 Arrays.fill 填充白色
            Arrays.fill(pixels, white);

            // 预计算黑色像素行
            int[] blackRow = new int[moduleSize];
            for (int i = 0; i < moduleSize; i++) {
                blackRow[i] = black;
            }

            int blackCount = 0;
            int maxX = width;
            int maxY = height;
            int rowLen = moduleSize;

            for (int y = 0; y < codeSize; y++) {
                for (int x = 0; x < codeSize; x++) {
                    if (code[y][x]) {
                        int startX = offsetX + margin + x * moduleSize;
                        int startY = offsetY + margin + y * moduleSize;

                        if (startX >= maxX || startY >= maxY) continue;
                        int actualLen = Math.min(rowLen, maxX - startX);

                        for (int dy = 0; dy < moduleSize; dy++) {
                            int py = startY + dy;
                            if (py >= maxY) break;
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

    // 获取缓存的像素数组
    private static synchronized int[] getCachedPixels(int width, int height) {
        int size = width * height;
        if (sCachedPixels == null || sCachedPixels.length < size) {
            // 数组不够大，重新分配
            sCachedPixels = new int[size];
            sCachedWidth = width;
            sCachedHeight = height;
            Log.d(TAG, "分配新像素数组: " + size + " 个元素");
        } else if (sCachedWidth != width || sCachedHeight != height) {
            // 尺寸变了，但数组够大，更新记录
            sCachedWidth = width;
            sCachedHeight = height;
            Log.d(TAG, "复用像素数组，尺寸变更: " + width + "x" + height);
        } else {
            Log.d(TAG, "复用像素数组，尺寸不变: " + width + "x" + height);
        }
        return sCachedPixels;
    }

    /**
     * 检测硬件加速是否真正可用
     *
     * 检测策略：
     *   - Android 2.3 (API 10) 及以下：无硬件加速，直接返回 false
     *   - Android 4.0 (API 14) 及以上：使用 Canvas.isHardwareAccelerated() 正经方法
     *   - Android 3.0-3.2 (API 11-13)：使用 EGL 迫真方法检测 GPU 是否存在
     *
     * 迫真方法原理：
     *   EGL 是 OpenGL ES 和底层窗口系统之间的接口，存在 EGL 说明设备有 GPU。
     *   通过反射调用 EGLContext.getEGL()，如果返回非 null 说明有 GPU。
     */
    private static boolean isHardwareAccelerationSupported() {
        try {
            int sdkInt = 0;
            try {
                Class<?> versionClass = Class.forName("android.os.Build$VERSION");
                java.lang.reflect.Field sdkField = versionClass.getField("SDK_INT");
                sdkInt = sdkField.getInt(null);
            } catch (Throwable t) {
                Log.d(TAG, "无法获取 SDK_INT，判定为 Android 1.6 或更早");
                return false;
            }

            if (sdkInt < 11) {
                Log.d(TAG, "SDK_INT=" + sdkInt + "，低于 3.0，无硬件加速");
                return false;
            }

            // Android 4.0+：官方 API
            if (sdkInt >= 14) {
                try {
                    Bitmap testBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565);
                    Canvas testCanvas = new Canvas(testBitmap);
                    java.lang.reflect.Method method = Canvas.class.getMethod("isHardwareAccelerated");
                    boolean isAccelerated = ((Boolean) method.invoke(testCanvas)).booleanValue();
                    testBitmap.recycle();
                    Log.d(TAG, "SDK_INT=" + sdkInt + "，Canvas.isHardwareAccelerated()=" + isAccelerated);
                    return isAccelerated;
                } catch (Throwable t) {
                    Log.w(TAG, "4.0+ 正经检测失败: " + t.getMessage() + "，降级到迫真方法");
                }
            }

            // Android 3.0-3.2：EGL 迫真检测
            try {
                Class<?> eglContextClass = Class.forName("javax.microedition.khronos.egl.EGLContext");
                java.lang.reflect.Method getEGLMethod = eglContextClass.getMethod("getEGL");
                Object egl = getEGLMethod.invoke(null);
                boolean hasGpu = (egl != null);
                Log.d(TAG, "SDK_INT=" + sdkInt + "，EGL 迫真检测: " + (hasGpu ? "存在 GPU" : "无 GPU"));
                return hasGpu;
            } catch (Throwable t) {
                Log.d(TAG, "EGL 迫真检测失败: " + t.getMessage());
                return false;
            }

        } catch (Throwable t) {
            Log.e(TAG, "硬件加速检测异常，默认使用 setPixels: " + t.getMessage());
            return false;
        }
    }
}