package tv.biliclassic.player;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import tv.biliclassic.R;

public class PlayerQualityManager {

    private Activity mActivity;
    private TextView mQualityButton;
    private ListView mQualityListView;
    private ArrayAdapter<String> mAdapter;
    private String[] mQualityNames;
    private int[] mQualityValues;
    private int mCurrentQn;
    private boolean mAllowSwitch;
    private OnQualityChangeListener mListener;
    private boolean mListVisible = false;

    public interface OnQualityChangeListener {
        void onQualityChange(int newQn);
    }

    public PlayerQualityManager(Activity activity) {
        mActivity = activity;
    }

    public void init(String[] names, int[] values, int currentQn, boolean allowSwitch) {
        mQualityNames = names;
        mQualityValues = values;
        mCurrentQn = currentQn;
        mAllowSwitch = allowSwitch;

        mQualityButton = (TextView) mActivity.findViewById(R.id.media_quality);
        mQualityListView = (ListView) mActivity.findViewById(R.id.media_quality_options);

        if (mQualityButton != null) {
            updateButtonText();
            if (mAllowSwitch && mQualityNames != null && mQualityNames.length > 1) {
                mQualityButton.setVisibility(View.VISIBLE);
                mQualityButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        toggleQualityList();
                    }
                });
            } else {
                mQualityButton.setVisibility(View.VISIBLE);
                mQualityButton.setClickable(false);
            }
        }

        if (mQualityListView != null) {
            mQualityListView.setVisibility(View.GONE);
            if (mAllowSwitch && mQualityNames != null && mQualityNames.length > 1) {
                mAdapter = new ArrayAdapter<String>(mActivity,
                        android.R.layout.simple_list_item_1, mQualityNames) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(mActivity.getResources().getColor(
                                position == getCurrentQualityIndex()
                                        ? R.color._bkgd__pink_light
                                        : R.color.videoplay__video_title_text));
                        tv.setTextSize(14);
                        tv.setGravity(android.view.Gravity.CENTER);
                        tv.setMinHeight(40);
                        tv.setBackgroundResource(R.drawable.bili_player_control_item_background);
                        return tv;
                    }
                };
                mQualityListView.setAdapter(mAdapter);
                mQualityListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        hideQualityList();
                        if (mQualityValues != null && position < mQualityValues.length) {
                            int newQn = mQualityValues[position];
                            if (newQn != mCurrentQn && mListener != null) {
                                mCurrentQn = newQn;
                                updateButtonText();
                                mAdapter.notifyDataSetChanged();
                                mListener.onQualityChange(newQn);
                            }
                        }
                    }
                });
            }
        }
    }

    public void setOnQualityChangeListener(OnQualityChangeListener listener) {
        mListener = listener;
    }

    public void toggleQualityList() {
        if (mQualityListView == null) return;
        if (mListVisible) {
            hideQualityList();
        } else {
            showQualityList();
        }
    }

    public void showQualityList() {
        if (mQualityListView == null) return;
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mQualityListView.setVisibility(View.VISIBLE);
        mListVisible = true;
        alignQualityList();
    }

    private void alignQualityList() {
        if (mQualityButton == null || mQualityListView == null) return;
        mQualityListView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int[] btnLoc = new int[2];
                    int[] listLoc = new int[2];
                    mQualityButton.getLocationOnScreen(btnLoc);
                    mQualityListView.getLocationOnScreen(listLoc);
                    int btnRight = btnLoc[0] + mQualityButton.getWidth();
                    int listRight = listLoc[0] + mQualityListView.getWidth();
                    int offset = btnRight - listRight;
                    // setTranslationX 是 API 11 才加入。注意 2.1 及以下 Dalvik 校验器为
                    // 硬失败模式，字节码里保留直接引用会导致整类被拒载（运行时守卫无效），
                    // 必须走反射：低版本抛 NoSuchMethodException 后用 Margin 对齐
                    try {
                        mQualityListView.getClass()
                                .getMethod("setTranslationX", float.class)
                                .invoke(mQualityListView, Float.valueOf(offset));
                    } catch (NoSuchMethodException e) {
                        android.view.ViewGroup.LayoutParams lp = mQualityListView.getLayoutParams();
                        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                            ((android.view.ViewGroup.MarginLayoutParams) lp).leftMargin = offset;
                            mQualityListView.setLayoutParams(lp);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        });
    }

    public void hideQualityList() {
        if (mQualityListView == null) return;
        mQualityListView.setVisibility(View.GONE);
        mListVisible = false;
    }

    public boolean isQualityListVisible() {
        return mListVisible;
    }

    public void updateCurrentQuality(int qn) {
        mCurrentQn = qn;
        updateButtonText();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void updateButtonText() {
        if (mQualityButton == null) return;
        String text = getQualityShortName(mCurrentQn);
        mQualityButton.setText(text != null ? text : "画质");
    }

    private int getCurrentQualityIndex() {
        if (mQualityValues == null) return -1;
        for (int i = 0; i < mQualityValues.length; i++) {
            if (mQualityValues[i] == mCurrentQn) return i;
        }
        return -1;
    }

    public static String getQualityShortName(int qn) {
        switch (qn) {
            case 6:  return "极速"; //240P
            case 16: return "流畅"; //360P
            case 32: return "清晰"; //480P
            case 64: return "高清"; //720P
            case 74: return "高清60帧"; //720P60
            case 80: return "超清"; //1080P
            case 112: return "超清+"; //1080P+
            case 116: return "超清60帧"; //1080P60
            case 120: return "超高清"; //4K
            default: return qn > 0 ? (qn + "P") : "画质";
        }
    }

    public void release() {
        mActivity = null;
        mQualityButton = null;
        mQualityListView = null;
        mAdapter = null;
        mListener = null;
    }
}
