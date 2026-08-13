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

package master.flame.danmaku.danmaku.renderer.android;

import master.flame.danmaku.controller.DanmakuFilters;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.GlobalFlagValues;
import master.flame.danmaku.danmaku.model.IDanmakuIterator;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.renderer.IRenderer;
import master.flame.danmaku.danmaku.renderer.Renderer;


public class DanmakuRenderer extends Renderer {

    private final DanmakuTimer mStartTimer = new DanmakuTimer();
    private final RenderingState mRenderingState = new RenderingState();
    private DanmakusRetainer.Verifier mVerifier;
    private final DanmakusRetainer.Verifier verifier = new DanmakusRetainer.Verifier() {
        @Override
        public boolean skipLayout(BaseDanmaku danmaku, float fixedTop, int lines, boolean willHit) {
            if (danmaku.priority == 0 && DanmakuFilters.getDefault().filterSecondary(danmaku, lines, 0, mStartTimer, willHit)) {
                danmaku.setVisibility(false);
                return true;
            }
            return false;
        }
    };

    @Override
    public void clear() {
        DanmakusRetainer.clear();
        DanmakuFilters.getDefault().clear();
    }

    @Override
    public void release() {
        DanmakusRetainer.release();
        DanmakuFilters.getDefault().clear();
    }

    @Override
    public void setVerifierEnabled(boolean enabled) {
        mVerifier = (enabled ? verifier : null);
    }

    @Override
    public RenderingState draw(IDisplayer disp, IDanmakus danmakus, long startRenderTime) {
        int lastTotalDanmakuCount = mRenderingState.totalDanmakuCount;
        mRenderingState.reset();
        IDanmakuIterator itr = danmakus.iterator();
        int orderInScreen = 0;
        mStartTimer.update(System.currentTimeMillis());
        int sizeInScreen = danmakus.size();
        BaseDanmaku drawItem = null;
        // 诊断：分段计时（measure/layout/draw），定位 700ms 卡在哪个阶段
        long t0 = 0;
        boolean diag = master.flame.danmaku.util.DiagLogger.enabled();
        if (diag) {
            t0 = System.currentTimeMillis();
        }
        long tMeasure = 0, tLayout = 0, tDraw = 0, tFilter = 0, tOther = 0;
        int drawCount = 0, cacheHit = 0, cacheMiss = 0;
        while (itr.hasNext()) {

            drawItem = itr.next();
            long ts = diag ? System.currentTimeMillis() : 0;

            if (drawItem.isLate()) {
                break;
            }

            if (!drawItem.hasPassedFilter()) {
                DanmakuFilters.getDefault().filter(drawItem, orderInScreen, sizeInScreen, mStartTimer, false);
            }
            if (diag) tFilter += System.currentTimeMillis() - ts;

            if (drawItem.time < startRenderTime
                    || (drawItem.priority == 0 && drawItem.isFiltered())) {
                continue;
            }

            if (drawItem.getType() == BaseDanmaku.TYPE_SCROLL_RL){
                // 同屏弹幕密度只对滚动弹幕有效
                orderInScreen++;
            }

            // measure
            if (!drawItem.isMeasured()) {
                ts = diag ? System.currentTimeMillis() : 0;
                drawItem.measure(disp);
                if (diag) tMeasure += System.currentTimeMillis() - ts;
            }

            // layout
            ts = diag ? System.currentTimeMillis() : 0;
            DanmakusRetainer.fix(drawItem, disp, mVerifier);
            if (diag) tLayout += System.currentTimeMillis() - ts;

            // draw
            if (!drawItem.isOutside() && drawItem.isShown()) {
                if (drawItem.lines == null && drawItem.getBottom() > disp.getHeight()) {
                    continue;    // skip bottom outside danmaku
                }
                ts = diag ? System.currentTimeMillis() : 0;
                int renderingType = drawItem.draw(disp);
                if (diag) tDraw += System.currentTimeMillis() - ts;
                drawCount++;
                if(renderingType == IRenderer.CACHE_RENDERING) {
                    mRenderingState.cacheHitCount++;
                    if (diag) cacheHit++;
                } else if(renderingType == IRenderer.TEXT_RENDERING) {
                    mRenderingState.cacheMissCount++;
                    if (diag) cacheMiss++;
                }
                mRenderingState.addCount(drawItem.getType(), 1);
                mRenderingState.addTotalCount(1);
            }

        }

        if (diag && (System.currentTimeMillis() - t0 > 30)) {
            // 只记录较慢的帧，避免刷屏
            master.flame.danmaku.util.DiagLogger.diag("Danmaku",
                    "render frame=" + (System.currentTimeMillis() - t0)
                            + "ms total=" + sizeInScreen
                            + " drawn=" + drawCount
                            + " cacheHit=" + cacheHit
                            + " cacheMiss=" + cacheMiss
                            + " tMeasure=" + tMeasure
                            + " tLayout=" + tLayout
                            + " tDraw=" + tDraw
                            + " tFilter=" + tFilter);
        }

        mRenderingState.nothingRendered = (mRenderingState.totalDanmakuCount == 0);
        mRenderingState.endTime = drawItem != null ? drawItem.time : RenderingState.UNKNOWN_TIME;
        if (mRenderingState.nothingRendered) {
            mRenderingState.beginTime = RenderingState.UNKNOWN_TIME;
        }
        mRenderingState.incrementCount = mRenderingState.totalDanmakuCount - lastTotalDanmakuCount;
        mRenderingState.consumingTime = mStartTimer.update(System.currentTimeMillis());
        return mRenderingState;
    }

}
