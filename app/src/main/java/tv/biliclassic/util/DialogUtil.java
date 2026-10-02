package tv.biliclassic.util;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ContextThemeWrapper;

public class DialogUtil {

    /** 给 Context 裹上弹窗样式主题，用在 new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(wrap(this))) 中 */
    public static Context wrap(Context base) {
        int val = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DIALOG_STYLE, 0);
        if (val == 1) return base; // 经典样式 = 系统默认
        if (val == 2) return new ContextThemeWrapper(base, tv.biliclassic.R.style.DialogStyle_Holo);
        if (val == 3) return new ContextThemeWrapper(base, tv.biliclassic.R.style.DialogStyle_Material);

        // 默认 = 自动适配
        int sdk = SdkHelper.getSdkInt();
        if (sdk >= 21) return new ContextThemeWrapper(base, tv.biliclassic.R.style.DialogStyle_Material);
        if (sdk >= 11) return new ContextThemeWrapper(base, tv.biliclassic.R.style.DialogStyle_Holo);
        return base; // < 11：系统默认
    }

    public static AlertDialog show(AlertDialog.Builder builder) {
        return builder.show();
    }

    public static AlertDialog show(AlertDialog.Builder builder, boolean cancelable) {
        builder.setCancelable(cancelable);
        return builder.show();
    }
}
