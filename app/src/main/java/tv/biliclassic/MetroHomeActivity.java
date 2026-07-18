package tv.biliclassic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.RecommendApi;
import tv.biliclassic.model.VideoCard;

public class MetroHomeActivity extends BaseActivity {

    private ScrollView mTileScroll;
    private LinearLayout mTileContainer;
    private TextView mTvLoading;
    private List<VideoCard> mVideoList = new ArrayList<VideoCard>();
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsLoading = false;
    private boolean mIsEnd = false;
    private boolean mFirstLoad = true;
    private int mPage = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metro_home);

        mTileScroll = (ScrollView) findViewById(R.id.tile_scroll);
        mTileContainer = (LinearLayout) findViewById(R.id.tile_container);
        mTvLoading = (TextView) findViewById(R.id.tv_loading);

        mTileScroll.post(new Runnable() {
            @Override
            public void run() {
                mTileScroll.scrollTo(0, 0);
                loadRecommend();
            }
        });

        mTileScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    View content = mTileScroll.getChildAt(0);
                    if (content != null) {
                        int scrollY = mTileScroll.getScrollY();
                        int contentHeight = content.getHeight();
                        int scrollViewHeight = mTileScroll.getHeight();
                        if (scrollY + scrollViewHeight >= contentHeight - 100) {
                            loadMore();
                        }
                    }
                }
                return false;
            }
        });
    }

    private void loadRecommend() {
        mIsLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    RecommendApi.getRecommend(items);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (items.size() == 0) {
                                mTvLoading.setText("暂无推荐视频");
                                mIsLoading = false;
                                return;
                            }
                            mVideoList.addAll(items);
                            mTvLoading.setVisibility(View.GONE);
                            mTileScroll.setVisibility(View.VISIBLE);
                            addTileRows(0, items.size());
                            mFirstLoad = false;
                            mIsLoading = false;
                            mPage = 1;
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mVideoList.size() == 0) {
                                mTvLoading.setText("加载失败");
                            }
                            mIsLoading = false;
                        }
                    });
                }
            }
        }).start();
    }

    private void loadMore() {
        if (mIsLoading || mIsEnd) return;
        mIsLoading = true;
        mPage++;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    RecommendApi.getPopular(items, mPage);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (items.size() == 0) {
                                mIsEnd = true;
                            } else {
                                int start = mVideoList.size();
                                mVideoList.addAll(items);
                                addTileRows(start, mVideoList.size());
                            }
                            mIsLoading = false;
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIsEnd = true;
                            mIsLoading = false;
                        }
                    });
                }
            }
        }).start();
    }

    private void addTileRows(final int startIndex, final int endIndex) {
        for (int i = startIndex; i < endIndex; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(8, 4, 8, 4);

            row.addView(createTile(i));
            if (i + 1 < endIndex) {
                row.addView(createTile(i + 1));
            }

            if (mFirstLoad) {
                row.setVisibility(View.INVISIBLE);
                mTileContainer.addView(row);
            } else {
                mTileContainer.addView(row);
                int newRowIndex = (i - startIndex) / 2;
                animateRowIn(row, newRowIndex * 120);
            }
        }

        if (mFirstLoad) {
            animateAllRows();
        } else {
            mTileScroll.post(new Runnable() {
                @Override
                public void run() {
                    mTileScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private FrameLayout createTile(final int index) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int tileSizePx = (screenWidth - 48) / 2;

        FrameLayout tile = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSizePx, (int) (tileSizePx * 0.7f));
        lp.setMargins(4, 4, 4, 4);
        tile.setLayoutParams(lp);
        tile.setClickable(true);
        tile.setFocusable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setColor(0xFFD86DA5);

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setShape(GradientDrawable.RECTANGLE);
        pressedBg.setColor(0xFFC06090);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        sld.addState(new int[]{}, normalBg);
        tile.setBackgroundDrawable(sld);

        TextView label = new TextView(this);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        label.setLayoutParams(labelLp);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setMaxLines(3);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(8, 8, 8, 8);
        tile.addView(label);

        final VideoCard card = mVideoList.get(index);
        label.setText(card.title);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MetroHomeActivity.this, VideoDetailActivity.class);
                if (card.aid != 0) {
                    intent.putExtra("aid", card.aid);
                } else if (card.bvid != null && card.bvid.length() > 0) {
                    intent.putExtra("bvid", card.bvid);
                }
                startActivity(intent);
            }
        });

        return tile;
    }

    private void animateAllRows() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int count = mTileContainer.getChildCount();

        for (int i = 0; i < count; i++) {
            final View row = mTileContainer.getChildAt(i);
            row.setVisibility(View.VISIBLE);

            TranslateAnimation a = new TranslateAnimation(screenWidth, 0, 0, 0);
            a.setDuration(350);
            a.setStartOffset(i * 150);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);

            if (i == count - 1) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        clearRowAnimations();
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
            }

            row.startAnimation(a);
        }
    }

    private void animateRowIn(View row, int delay) {
        row.setVisibility(View.VISIBLE);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        TranslateAnimation a = new TranslateAnimation(screenWidth, 0, 0, 0);
        a.setDuration(400);
        a.setStartOffset(delay);
        a.setInterpolator(new DecelerateInterpolator());
        a.setFillAfter(true);
        row.startAnimation(a);
    }

    private void clearRowAnimations() {
        for (int i = 0; i < mTileContainer.getChildCount(); i++) {
            mTileContainer.getChildAt(i).clearAnimation();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
