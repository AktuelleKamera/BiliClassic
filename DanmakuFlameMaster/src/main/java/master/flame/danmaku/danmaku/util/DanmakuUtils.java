/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.util;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.AndroidDisplayer;
import master.flame.danmaku.danmaku.model.android.DrawingCache;
import master.flame.danmaku.danmaku.model.android.DrawingCacheHolder;

public class DanmakuUtils {

    // 缓存数组，避免每次分配新数组
    private static final float[] sRect1 = new float[4];
    private static final float[] sRect2 = new float[4];

    // 缓存上次计算的时间，避免重复计算
    private static long sLastCacheTime = -1;
    private static float[] sCachedRect1 = null;
    private static float[] sCachedRect2 = null;
    private static long sCachedTime1 = -1;
    private static long sCachedTime2 = -1;

    /**
     * 检测两个弹幕是否会碰撞
     * 允许不同类型弹幕的碰撞
     */
    public static boolean willHitInDuration(IDisplayer disp, BaseDanmaku d1, BaseDanmaku d2,
                                            long duration, long currTime) {
        // 快速失败检查
        if (d1 == null || d2 == null || d1.isOutside() || d2.isOutside()) {
            return false;
        }

        final int type1 = d1.getType();
        final int type2 = d2.getType();

        // 不同类型不碰撞（提前返回）
        if (type1 != type2) {
            return false;
        }

        long dTime = d2.time - d1.time;
        if (dTime < 0) {
            return true;
        }

        // 时间差超过持续时间或已超时则不碰撞
        if (dTime >= duration || d1.isTimeOut() || d2.isTimeOut()) {
            return false;
        }

        // 固定弹幕总是碰撞
        if (type1 == BaseDanmaku.TYPE_FIX_TOP || type1 == BaseDanmaku.TYPE_FIX_BOTTOM) {
            return true;
        }

        // 检查当前时间点是否碰撞
        if (checkHitAtTime(disp, d1, d2, currTime)) {
            return true;
        }

        // 检查结束时间点是否碰撞
        return checkHitAtTime(disp, d1, d2, d1.time + d1.getDuration());
    }

    private static boolean checkHitAtTime(IDisplayer disp, BaseDanmaku d1, BaseDanmaku d2, long time) {
        // 获取 d1 的位置（使用缓存）
        float[] rect1 = getRectWithCache(d1, disp, time, 1);
        if (rect1 == null) {
            return false;
        }

        // 获取 d2 的位置（使用缓存）
        float[] rect2 = getRectWithCache(d2, disp, time, 2);
        if (rect2 == null) {
            return false;
        }

        return checkHit(d1.getType(), d2.getType(), rect1, rect2);
    }

    // 带缓存的 getRectAtTime
    private static float[] getRectWithCache(BaseDanmaku danmaku, IDisplayer disp, long time, int id) {
        // 检查缓存是否命中
        if (id == 1) {
            if (sCachedTime1 == time && sCachedRect1 != null) {
                return sCachedRect1;
            }
            float[] rect = danmaku.getRectAtTime(disp, time);
            sCachedRect1 = rect;
            sCachedTime1 = time;
            return rect;
        } else {
            if (sCachedTime2 == time && sCachedRect2 != null) {
                return sCachedRect2;
            }
            float[] rect = danmaku.getRectAtTime(disp, time);
            sCachedRect2 = rect;
            sCachedTime2 = time;
            return rect;
        }
    }

    private static boolean checkHit(int type1, int type2, float[] rectArr1, float[] rectArr2) {
        if (type1 != type2) {
            return false;
        }

        if (type1 == BaseDanmaku.TYPE_SCROLL_RL) {
            // 从左到右：如果左边界小于右边界则碰撞
            return rectArr2[0] < rectArr1[2];
        }

        if (type1 == BaseDanmaku.TYPE_SCROLL_LR) {
            // 从右到左：如果右边界大于左边界则碰撞
            return rectArr2[2] > rectArr1[0];
        }

        return false;
    }

    public static DrawingCache buildDanmakuDrawingCache(BaseDanmaku danmaku, IDisplayer disp,
                                                        DrawingCache cache) {
        if (cache == null) {
            cache = new DrawingCache();
        }

        cache.build((int) Math.ceil(danmaku.paintWidth), (int) Math.ceil(danmaku.paintHeight),
                disp.getDensityDpi(), false);
        DrawingCacheHolder holder = cache.get();
        if (holder != null) {
            AndroidDisplayer.drawDanmaku(danmaku, holder.canvas, 0, 0, false);
            if (disp.isHardwareAccelerated()) {
                holder.splitWith(disp.getWidth(), disp.getHeight(),
                        disp.getMaximumCacheWidth(), disp.getMaximumCacheHeight());
            }
        }
        return cache;
    }

    public static int getCacheSize(int w, int h) {
        return w * h * 4;
    }

    public static final boolean isDuplicate(BaseDanmaku obj1, BaseDanmaku obj2) {
        if (obj1 == null || obj2 == null || obj1 == obj2) {
            return false;
        }

        if (obj1.text == null && obj2.text == null) {
            return true;
        }

        if (obj1.text == null || obj2.text == null) {
            return false;
        }

        return obj1.text.equals(obj2.text);
    }

    public static final int compare(BaseDanmaku obj1, BaseDanmaku obj2) {
        if (obj1 == obj2) {
            return 0;
        }

        if (obj1 == null) {
            return -1;
        }

        if (obj2 == null) {
            return 1;
        }

        // 按时间排序
        long val = obj1.time - obj2.time;
        if (val > 0) {
            return 1;
        } else if (val < 0) {
            return -1;
        }

        // 时间相同，按索引排序
        int result = obj1.index - obj2.index;
        if (result > 0) {
            return 1;
        } else if (result < 0) {
            return -1;
        }

        // 索引相同，按类型排序
        result = obj1.getType() - obj2.getType();
        if (result > 0) {
            return 1;
        } else if (result < 0) {
            return -1;
        }

        // 类型相同，按文本排序
        if (obj1.text == null && obj2.text == null) {
            return 0;
        }
        if (obj1.text == null) {
            return -1;
        }
        if (obj2.text == null) {
            return 1;
        }

        int r = obj1.text.toString().compareTo(obj2.text.toString());
        if (r != 0) {
            return r;
        }

        // 文本相同，按颜色排序
        r = obj1.textColor - obj2.textColor;
        if (r != 0) {
            return r < 0 ? -1 : 1;
        }

        // 颜色相同，按索引排序
        r = obj1.index - obj2.index;
        if (r != 0) {
            return r < 0 ? -1 : 1;
        }

        // 最终兼容
        return 0;
    }

    public static final boolean isOverSize(IDisplayer disp, BaseDanmaku item) {
        return disp.isHardwareAccelerated() &&
                (item.paintWidth > disp.getMaximumCacheWidth() ||
                        item.paintHeight > disp.getMaximumCacheHeight());
    }
}