package tv.biliclassic.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tv.biliclassic.R;

/**
 * 内置目录选择器。旧系统没有系统级文件选择器，1.5 起都能用。
 * 注意：不能用 AlertDialog 自带按钮实现"返回"——点对话框任何按钮框架都会
 * 自动 dismiss，所以按钮行做在自定义布局里，关窗时机自己控制。
 */
public class FolderPickerDialog {

    public interface Callback {
        void onPicked(String path);
    }

    public static void show(final Context context, String startPath, int titleRes, final Callback callback) {
        final File[] current = new File[]{resolveStart(startPath)};
        final AlertDialog[] holder = new AlertDialog[1];
        // 与其它弹窗一致：包一层主题，Holo 设备(API 11+)自动用上 Holo 弹窗风格
        final Context ctx = SdkHelper.dialogContext(DialogUtil.wrap(context));

        final ListView list = new ListView(ctx);
        CrownScrollHelper.attach(list);
        refresh(list, ctx, current[0]);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item == null) return;
                String name = item.toString();
                File target;
                if ("..".equals(name)) {
                    target = current[0].getParentFile();
                } else {
                    target = new File(current[0], name);
                }
                if (target != null && target.isDirectory()) {
                    current[0] = target;
                    refresh(list, ctx, current[0]);
                    showPath(holder[0], current[0]);
                }
            }
        });

        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (density * 8f + 0.5f);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(pad, 0, pad, pad);

        Button back = new Button(ctx);
        back.setText(R.string.folder_picker_up);
        back.setSingleLine(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUp(list, ctx, current, holder[0]);
            }
        });

        Button choose = new Button(ctx);
        choose.setText(R.string.folder_picker_choose);
        choose.setSingleLine(true);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                callback.onPicked(current[0].getAbsolutePath());
            }
        });

        Button exit = new Button(ctx);
        exit.setText(R.string.videodetail_cancel);
        exit.setSingleLine(true);
        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
            }
        });

        // 中间"选择此目录"文字最长，占双份宽度，避免窄屏上换行挤压
        row.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(choose, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(exit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setView(root)
                .create();
        holder[0] = dialog;

        // 返回键 = 返回上一级；到根目录才交给系统默认行为（关闭）
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (current[0].getParentFile() != null) {
                        goUp(list, ctx, current, holder[0]);
                        return true;
                    }
                }
                return false;
            }
        });

        // 必须先 show() 再更新标题：show 之前访问窗口/标题视图会提前安装 decor，
        // 导致旧系统抛 "requestFeature() must be called before adding content"
        dialog.show();
        showPath(dialog, current[0]);
    }

    private static void goUp(ListView list, Context context, File[] current, AlertDialog dialog) {
        File parent = current[0].getParentFile();
        if (parent != null) {
            current[0] = parent;
            refresh(list, context, current[0]);
            showPath(dialog, current[0]);
        }
    }

    private static void showPath(AlertDialog dialog, File dir) {
        if (dialog == null) return;
        String path = dir.getAbsolutePath();
        // 完整路径显示在标题里；老平台标题默认单行省略，放开为多行换行显示
        try {
            android.widget.TextView tv =
                    (android.widget.TextView) dialog.findViewById(android.R.id.title);
            if (tv instanceof android.widget.TextView) {
                tv.setSingleLine(false);
                tv.setMaxLines(4);
                tv.setEllipsize(null);
                tv.setText(path);
                return;
            }
        } catch (Throwable t) {
        }
        dialog.setTitle(path);
    }

    private static void refresh(ListView list, Context context, File dir) {
        List<String> data = new ArrayList<String>();
        if (dir.getParentFile() != null) data.add("..");
        File[] files = dir.listFiles();
        if (files != null) {
            List<String> names = new ArrayList<String>();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) names.add(files[i].getName());
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            data.addAll(names);
        }
        // ArrayAdapter.clear() 是 API 11 才有的，老系统直接重建 adapter
        list.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, data));
    }

    private static File resolveStart(String startPath) {
        if (startPath != null && startPath.length() > 0) {
            File f = new File(startPath);
            if (f.isDirectory()) return f;
            File parent = f.getParentFile();
            if (parent != null && parent.isDirectory()) return parent;
        }
        File storage = new File("/storage");
        if (storage.isDirectory()) return storage;
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null && ext.isDirectory()) return ext;
        return new File("/");
    }
}
