package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Field;

public class DeviceInfoActivity extends BaseActivity {

    private TextView deviceInfoText;
    private Button btnCopy;
    private Button btnEvaluate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        deviceInfoText = (TextView) findViewById(R.id.device_info_text);
        btnCopy = (Button) findViewById(R.id.btn_copy);
        btnEvaluate = (Button) findViewById(R.id.btn_evaluate);

        deviceInfoText.setText(getDeviceInfoSafe());

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyDeviceInfo();
            }
        });

        btnEvaluate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEvaluateDialog();
            }
        });
    }

    /**
     * 获取设备信息
     */
    private String getDeviceInfoSafe() {
        StringBuilder sb = new StringBuilder();

        String manufacturer = getBuildField("MANUFACTURER");
        String model = getBuildField("MODEL");
        String device = getBuildField("DEVICE");
        String product = getBuildField("PRODUCT");
        String cpuAbi = getBuildField("CPU_ABI");
        String cpuAbi2 = getBuildField("CPU_ABI2");
        String release = getBuildFieldStatic("VERSION", "RELEASE");
        String sdk = getBuildFieldStatic("VERSION", "SDK");
        String sdkInt = getBuildFieldStaticInt("VERSION", "SDK_INT");

        sb.append("=== 硬件信息 ===\n");
        sb.append("CPU架构: ").append(cpuAbi != null ? cpuAbi : "未知").append("\n");
        if (cpuAbi2 != null && cpuAbi2.length() > 0 && !cpuAbi2.equals(cpuAbi)) {
            sb.append("辅助ABI: ").append(cpuAbi2).append("\n");
        }

        sb.append("\n=== 系统信息 ===\n");
        sb.append("Android: ").append(release != null ? release : "未知").append("\n");
        if (sdkInt != null && sdkInt.length() > 0) {
            sb.append("API版本: ").append(sdkInt).append("\n");
        } else if (sdk != null && sdk.length() > 0) {
            sb.append("API版本: ").append(sdk).append("\n");
        }

        sb.append("\n=== 设备信息 ===\n");
        if (manufacturer != null && manufacturer.length() > 0) {
            sb.append("厂商: ").append(manufacturer).append("\n");
        }
        sb.append("型号: ").append(model != null ? model : "未知").append("\n");
        sb.append("代号: ").append(device != null ? device : "未知").append("\n");
        sb.append("产品: ").append(product != null ? product : "未知").append("\n");

        String hardware = getHardwareInfo();
        if (hardware != null && hardware.length() > 0) {
            sb.append("硬件: ").append(hardware).append("\n");
        }

        long totalRamMB = getTotalRam();
        int javaHeapMB = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));

        sb.append("\n=== 内存信息 ===\n");
        if (totalRamMB > 0) {
            sb.append("可用物理内存: ").append(totalRamMB).append(" MB\n");
        }
        sb.append("Java堆内存: ").append(javaHeapMB).append(" MB\n");

        return sb.toString();
    }

    /**
     * 通过反射获取 Build 类的静态字段（非内部类）
     */
    private String getBuildField(String fieldName) {
        try {
            Field field = Build.class.getField(fieldName);
            Object value = field.get(null);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过反射获取 Build 内部类的静态字段
     */
    private String getBuildFieldStatic(String innerClassName, String fieldName) {
        try {
            Class innerClass = Class.forName("android.os.Build$" + innerClassName);
            Field field = innerClass.getField(fieldName);
            Object value = field.get(null);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过反射获取 Build 内部类的 int 静态字段
     */
    private String getBuildFieldStaticInt(String innerClassName, String fieldName) {
        try {
            Class innerClass = Class.forName("android.os.Build$" + innerClassName);
            Field field = innerClass.getField(fieldName);
            Object value = field.get(null);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private long getTotalRam() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = reader.readLine();
            reader.close();

            if (line != null && line.startsWith("MemTotal:")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long totalKB = Long.parseLong(parts[1]);
                    return totalKB / 1024;
                }
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private String getHardwareInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Hardware")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        reader.close();
                        return parts[1].trim();
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
        }
        return null;
    }

    private String getEasterEggComment() {
        try {
            return tv.biliclassic.util.DeviceInfoUtil.getDeviceInfo();
        } catch (Exception e) {
            return "设备信息已显示在上方哦\n\n如果觉得BiliClassic好用，欢迎分享给更多人~";
        }
    }

    private void copyDeviceInfo() {
        String info = deviceInfoText.getText().toString();
        try {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(info);
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEvaluateDialog() {
        final String evaluateText = getEasterEggComment();

        new AlertDialog.Builder(this)
                .setTitle("设备评价")
                .setMessage(evaluateText + "\n\n觉得这个评价准确吗？")
                .setPositiveButton("准确", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(DeviceInfoActivity.this, "谢谢反馈~", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("不准确", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(DeviceInfoActivity.this, "会继续优化！", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("分享", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        shareDeviceInfo();
                    }
                })
                .show();
    }

    private void shareDeviceInfo() {
        String info = deviceInfoText.getText().toString();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "我的设备信息：\n" + info + "\n\n" +
                        "来自 BiliClassic - 支持安卓1.6+的B站客户端");
        startActivity(Intent.createChooser(shareIntent, "分享"));
    }
}