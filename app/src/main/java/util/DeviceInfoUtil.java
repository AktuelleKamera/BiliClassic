package tv.biliclassic.util;

import android.os.Build;
import java.io.BufferedReader;
import java.io.FileReader;

public class DeviceInfoUtil {

    public static String getDeviceInfo() {
        // 调试日志：打印设备信息
        android.util.Log.e("DeviceInfoUtil", "=== 开始检测设备 ===");
        android.util.Log.e("DeviceInfoUtil", "manufacturer = [" + Build.MANUFACTURER + "]");
        android.util.Log.e("DeviceInfoUtil", "model = [" + Build.MODEL + "]");
        android.util.Log.e("DeviceInfoUtil", "device = [" + Build.DEVICE + "]");
        android.util.Log.e("DeviceInfoUtil", "product = [" + Build.PRODUCT + "]");
        android.util.Log.e("DeviceInfoUtil", "cpu_abi = [" + Build.CPU_ABI + "]");

        StringBuilder sb = new StringBuilder();

        String abi = Build.CPU_ABI;
        String hardware = getHardwareInfo();
        String model = Build.MODEL;
        String device = Build.DEVICE;
        String manufacturer = Build.MANUFACTURER;

        // ========== 横屏设备彩蛋（优先检测）==========

        android.util.Log.e("DeviceInfoUtil", "检查 HTC ChaCha 系列...");

        // HTC ChaCha 系列
        if ("HTC".equalsIgnoreCase(manufacturer)) {
            android.util.Log.e("DeviceInfoUtil", "厂商匹配 HTC，检查型号...");
            if (model != null && (model.contains("A810e") ||
                    model.contains("A810") ||
                    model.contains("ChaCha") ||
                    model.contains("Status") ||
                    model.contains("PB86100"))) {
                android.util.Log.e("DeviceInfoUtil", "✅ 匹配到 HTC ChaCha 系列！");
                sb.append("HTC ChaCha / Status\n");
                sb.append("群主的专用开发机！全键盘好评desu~\n");
                sb.append("横屏专属适配机型~\n");
                sb.append("架构: ARMv6 | 请使用v6解码包~");
                return sb.toString();
            } else {
                android.util.Log.e("DeviceInfoUtil", "型号不匹配，model=[" + model + "]");
            }
        } else {
            android.util.Log.e("DeviceInfoUtil", "厂商不匹配 HTC，manufacturer=[" + manufacturer + "]");
        }

        // 三星 Galaxy Y Pro / Galaxy Pro 系列
        android.util.Log.e("DeviceInfoUtil", "检查三星 Galaxy Y Pro 系列...");
        if ("samsung".equalsIgnoreCase(manufacturer)) {
            if (model != null && (model.contains("GT-B5510") ||
                    model.contains("GT-B5510L") ||
                    model.contains("GT-B5510B") ||
                    model.contains("GT-B7510"))) {
                android.util.Log.e("DeviceInfoUtil", "✅ 匹配到三星 Galaxy Y Pro！");
                sb.append("三星Galaxy Y Pro / Galaxy Pro\n");
                sb.append("横屏全键盘小钢炮！\n");
                sb.append("横屏专属适配机型~\n");
                sb.append("架构: ARMv6 | 请使用v6解码包~");
                return sb.toString();
            }
        }

        // ========== 索尼A5100微单相机彩蛋 ==========
        if (model != null && (model.equalsIgnoreCase("ScalarA") ||
                model.toLowerCase().contains("scalara"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到索尼A5100微单！");
            sb.append("索尼A5100 (ILCE-5100)\n");
            sb.append("诶？！这不是相机吗？！\n");
            sb.append("咱的软件居然在微单上跑起来了！\n");
            sb.append("你该不会是用相机在刷B站吧！(°ω°)\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // ========== 小米/红米手机彩蛋 ==========

        android.util.Log.e("DeviceInfoUtil", "检查小米/红米系列...");

        // 小米手机1 (MI-ONE Plus)
        if (model != null && (model.equalsIgnoreCase("MI-ONE Plus") ||
                model.equalsIgnoreCase("mione_plus"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米手机1 Plus！");
            sb.append("小米手机1 (MI-ONE Plus)\n");
            sb.append("为发烧而生！一代神机！\n");
            sb.append("1999交个朋友！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米手机青春版 (MI-ONE)
        if (model != null && (model.equalsIgnoreCase("MI-ONE") ||
                model.equalsIgnoreCase("MiOne") ||
                model.equalsIgnoreCase("mione"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米手机青春版！");
            sb.append("小米手机青春版 (MI-ONE)\n");
            sb.append("15万台限量！你居然是那十五万分之一！\n");
            sb.append("稀有精英desu！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米1S (MI 1S)
        if (model != null && (model.equalsIgnoreCase("MI 1S") ||
                model.equalsIgnoreCase("Mi1S") ||
                model.equalsIgnoreCase("MI1S") ||
                model.equalsIgnoreCase("xiaomi mi1s"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米1S！");
            sb.append("小米1S (MI 1S)\n");
            sb.append("1S青春版/原版共用型号\n");
            sb.append("小米手机1的升级版，一代经典！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米2 / 小米2S
        if (model != null && (model.equalsIgnoreCase("MI 2") ||
                model.equalsIgnoreCase("MI 2C") ||
                model.equalsIgnoreCase("MI 2S") ||
                model.equalsIgnoreCase("MI 2SC") ||
                model.equalsIgnoreCase("Mi2") ||
                model.equalsIgnoreCase("Mi2S"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米2/2S！");
            sb.append("小米2 / 小米2S\n");
            sb.append("一代经典！是不是碉堡了~\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米2A
        if (model != null && (model.equalsIgnoreCase("MI 2A") ||
                model.equalsIgnoreCase("Mi2A"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米2A！");
            sb.append("小米2A\n");
            sb.append("千元就有顶级双核和NFC！是不是碉堡了~\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米3 联通版/电信版
        if (model != null && (model.equalsIgnoreCase("MI 3W") ||
                model.equalsIgnoreCase("MI 3C") ||
                model.equalsIgnoreCase("Mi3W") ||
                model.equalsIgnoreCase("Mi3C"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米3联通/电信版！");
            sb.append("小米3 联通/电信版\n");
            sb.append("骁龙800！当年的旗舰配置！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米3 移动版 (Tegra 4)
        if (model != null && (model.equalsIgnoreCase("MI 3") ||
                model.equalsIgnoreCase("Mi3"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米3移动版！");
            sb.append("小米3 移动版 (Tegra 4)\n");
            sb.append("核弹级神U！移动版专属！\n");
            sb.append("一发就可以摧毁一个航母战斗群~ (｀・ω・´)\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 小米4
        if (model != null && (model.equalsIgnoreCase("MI 4") ||
                model.equalsIgnoreCase("Mi4") ||
                model.equalsIgnoreCase("MI 4LTE") ||
                model.equalsIgnoreCase("Mi4LTE") ||
                (device != null && device.toLowerCase().contains("cancro")))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到小米4！");
            sb.append("小米4 (Cancro)\n");
            sb.append("一块钢板的艺术之旅！\n");
            sb.append("骁龙801，依然能战！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // 红米1 / 红米1S
        if (model != null && (model.equalsIgnoreCase("2013022") ||
                model.equalsIgnoreCase("2013023") ||
                model.equalsIgnoreCase("2014011") ||
                model.equalsIgnoreCase("HM 1") ||
                model.equalsIgnoreCase("HM 1S") ||
                model.equalsIgnoreCase("HM 1SC") ||
                model.equalsIgnoreCase("HM 1SLTETD") ||
                model.toLowerCase().contains("hongmi"))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到红米1/1S！");
            sb.append("红米1 / 红米1S\n");
            sb.append("曾经的性价比神机！799交个朋友~\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // ========== HTC HD2 ==========
        if (model != null && (model.toLowerCase().contains("hd2") ||
                model.toLowerCase().contains("leo") ||
                model.equalsIgnoreCase("T8585") ||
                model.equalsIgnoreCase("T8588") ||
                model.equalsIgnoreCase("T9193") ||
                model.equalsIgnoreCase("HTC Leo") ||
                (device != null && device.toLowerCase().contains("leo")))) {
            android.util.Log.e("DeviceInfoUtil", "✅ 匹配到HTC HD2！");
            sb.append("HTC HD2\n");
            sb.append("一代神机！从WM6.5刷到各种奇葩系统和Android 7.1！\n");
            sb.append("架构: ARMv7-A | 请使用v7a解码包~");
            return sb.toString();
        }

        // ========== 常规架构检测 ==========
        android.util.Log.e("DeviceInfoUtil", "未匹配到特殊设备，进入常规架构检测...");

        if (abi != null) {
            if (abi.contains("arm64") || abi.contains("aarch64")) {
                android.util.Log.e("DeviceInfoUtil", "检测到 ARMv8-A");
                sb.append("ARMv8-A (AArch64)\n");
                sb.append("诶？64位？这就是传说中的未来手机吗？建议用系统播放器试试看~\n");
                sb.append("架构: ARMv8-A");
                return sb.toString();
            } else if (abi.contains("armeabi-v7a")) {
                android.util.Log.e("DeviceInfoUtil", "检测到 ARMv7-A");
                String v7Type = checkArmv7Subtype();
                sb.append("ARMv7-A (32位)\n");
                if (v7Type.contains("无NEON")) {
                    sb.append("注意：此设备不支持NEON指令集！\n");
                    sb.append("虽然需要安装v7a解码包，但可能无法愉快玩耍哦~\n");
                } else {
                    sb.append("主流配置desu！使用v7a解码包就能愉快玩耍了~\n");
                }
                sb.append("架构: ARMv7-A");
                return sb.toString();
            } else if (abi.contains("armeabi")) {
                android.util.Log.e("DeviceInfoUtil", "检测到 ARMv5/v6");
                String armVersion = checkArmv5OrV6();
                if (armVersion.equals("ARMv5te")) {
                    sb.append("ARMv5TE (ARM9)\n");
                    sb.append("Orz... 这是从博物馆里挖出来的出土文物吗？\n");
                    sb.append("能跑起来已经是奇迹了desu！请使用v5te解码包~\n");
                    sb.append("架构: ARMv5TE");
                } else {
                    sb.append("ARMv6 (ARM11)\n");
                    sb.append("普通手机desu！请使用v6解码包就能看了哦~\n");
                    sb.append("架构: ARMv6");
                }
                return sb.toString();
            } else if (abi.contains("x86_64")) {
                android.util.Log.e("DeviceInfoUtil", "检测到 x86-64");
                sb.append("x86-64 (64位)\n");
                sb.append("平板的英特尔芯！好稀有desu...可以试试系统播放器~\n");
                sb.append("架构: x86-64");
                return sb.toString();
            } else if (abi.contains("x86")) {
                android.util.Log.e("DeviceInfoUtil", "检测到 x86");
                sb.append("IA-32 (x86)\n");
                sb.append("平板的英特尔芯！好稀有desu...可以试试系统播放器~\n");
                sb.append("架构: IA-32");
                return sb.toString();
            }
        }

        // 默认
        android.util.Log.e("DeviceInfoUtil", "未匹配到任何架构，返回默认");
        sb.append("Unknown Architecture\n");
        sb.append("咱也不太清楚这是什么设备呢... 如果遇到问题请反馈~\n");
        sb.append("架构: 未知");
        return sb.toString();
    }

    private static String getHardwareInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("Hardware\t")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        reader.close();
                        return parts[1].trim();
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    /**
     * 检测 ARMv5 还是 ARMv6（兼容 ARMv5TEJ 等格式）
     */
    private static String checkArmv5OrV6() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                // 检查 CPU architecture 行
                if (line.startsWith("CPU architecture")) {
                    if (line.contains("5") || line.contains("5TEJ") || line.contains("5TE")) {
                        reader.close();
                        return "ARMv5te";
                    }
                    if (line.contains("6")) {
                        reader.close();
                        return "ARMv6";
                    }
                }
                // 检查 Processor 行
                if (line.startsWith("Processor") && (line.contains("ARM926") ||
                        line.contains("ARM922") || line.contains("ARM9EJ"))) {
                    reader.close();
                    return "ARMv5te";
                }
                // 检查 Features 行（edsp 是 ARMv6 特性）
                if (line.startsWith("Features") && line.contains("edsp")) {
                    reader.close();
                    return "ARMv6";
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "ARMv6";
    }

    /**
     * 检测 ARMv7 设备的 NEON 支持情况
     */
    private static String checkArmv7Subtype() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            boolean hasNeon = false;
            boolean isTegra2 = false;
            String hardware = "";

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Features")) {
                    if (line.contains("neon")) {
                        hasNeon = true;
                    }
                }
                if (line.startsWith("Hardware") || line.startsWith("Hardware\t")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        hardware = parts[1].toLowerCase();
                        if (hardware.contains("tegra")) {
                            isTegra2 = true;
                        }
                    }
                }
            }
            reader.close();

            if (isTegra2 || !hasNeon) {
                return "ARMv7-A (无NEON)";
            }
            return "ARMv7-A (有NEON)";
        } catch (Exception e) {
            return "ARMv7-A";
        }
    }
}