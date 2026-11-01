package tv.biliclassic.util;

import android.os.Build;

import java.io.BufferedReader;
import java.io.FileReader;

public class DeviceUtils {

    private static String getBuildField(String name) {
        try { return (String) Build.class.getField(name).get(null); }
        catch (Exception e) { return null; }
    }

    public static String getDeviceModel() {
        return getBuildField("MODEL");
    }

    public static String getDeviceRelease() {
        return getBuildField("RELEASE");
    }

    public static String getDeviceManufacturer() {
        return getBuildField("MANUFACTURER");
    }

    public static CpuAbi.Arch getMyCpuArch() {
        return CpuAbi.getArch();
    }

    public static String getCpuAbi() {
        return CpuAbi.getCpuAbi();
    }

    public static String getCpuAbi2() {
        return CpuAbi.getCpuAbi2();
    }

    public static boolean supportABI(String abi) {
        return CpuAbi.supportsAbi(abi);
    }

    public static boolean supportX86() {
        return CpuAbi.isX86();
    }

    public static boolean supportRiscv64() {
        return CpuAbi.isRiscv64();
    }

    public static boolean isRealARMArch() {
        return CpuAbi.isArm();
    }

    public static boolean isArmv5() {
        if (!isArmeabiLegacy()) return false;
        return "ARMv5te".equals(checkArmv5OrV6());
    }

    public static boolean isArmv6WithoutVfp() {
        if (!isArmeabiLegacy()) return false;
        return "ARMv6".equals(checkArmv5OrV6()) && !hasVfp();
    }

    public static boolean isLegacy = false;

    public static boolean isUnsupportedCpu() {
        // ARMv5 / 无 VFP 的 ARMv6：本身解码吃力，但若带硬件视频解码 DSP
        // （如 RK、全志等播放器 SoC），可转码成 240P 后硬解，不算无法播放。
        return !isLegacy && (isArmv5() || isArmv6WithoutVfp()) && !hasHardwareVideoDsp();
    }

    /**
     * 需要服务端转码才能硬解的老设备（ARMv5 / 无 VFP 的 ARMv6）。
     * 命中则默认开启「转码播放」，让这类设备也能流畅看 240P。
     *
     * 带硬件视频解码 DSP 的（RK / 全志等）直接硬解，不默认转码，
     * 与 {@link #isUnsupportedCpu()} 保持一致。
     */
    public static boolean needsTranscodeForHwDecode() {
        return (needsNoVfpIjk() || isSlowArmv7ForTranscode())
                && !hasHardwareVideoDsp();
    }

    public static boolean isTranscodePlaySupported() {
        return SdkHelper.getSdkInt() < 21
                && (!hasNeon() || isSlowArmv7ForTranscode());
    }

    private static Boolean sSlowArmv7;

    public static boolean isSlowArmv7ForTranscode() {
        if (sSlowArmv7 != null) return sSlowArmv7.booleanValue();
        String abi = CpuAbi.getCpuAbi();
        if (abi != null && abi.contains("v7")) {
            String rawInfo = readCpuInfo();

            String info = rawInfo == null ? "" : rawInfo.toLowerCase();
            if (info.contains("omap")) {
                sSlowArmv7 = Boolean.TRUE;
                return true;
            }
        }
        sSlowArmv7 = Boolean.FALSE;
        return false;
    }

    /**
     * 无 VFP 的 ARMv5 / ARMv6：自带 libijkffmpeg.so 含 VFP 指令跑不了，
     * 需要单独的 no-VFP legacy 版（放在兼容包 tv.biliclassic.libnovfp 里）。
     */
    public static boolean needsNoVfpIjk() {
        return isArmv5() || isArmv6WithoutVfp();
    }

    // 带硬件视频解码 DSP 的老 SoC 关键字（ARMv5TE/ARM11 上也能硬解转码后的 H.264）
    private static final String[] DSP_SOC_KEYS = {
        "rockchip", "rk26", "rk27", "rk28", "rk29", "rk30", "rk31", "rk32", "rk33",
        "telechips", "tcc8", "tcc9",
        "allwinner", "boxchip", "sunxi", "sun4i", "sun5i", "sun6i",
        "amlogic", "meson", "aml8",
        "mstar", "novatek", "sigma", "smp86", "realtek", "rtd"
    };

    /**
     * 设备是否带硬件视频解码 DSP：扫描 Build 字段与 /proc/cpuinfo 里的 SoC 型号关键字。
     * 用于把 RK / 全志等"有能力"的 ARMv5TE 老设备从无法播放名单里排除。
     */
    public static boolean hasHardwareVideoDsp() {
        StringBuilder sb = new StringBuilder();
        String[] fields = {"HARDWARE", "BOARD", "DEVICE", "MANUFACTURER", "BRAND", "PRODUCT", "MODEL"};
        for (String f : fields) {
            String v = getBuildField(f);
            if (v != null) sb.append(v).append(' ');
        }
        String cpuinfo = readCpuInfo();
        String probe = sb.toString().toLowerCase() + " "
                + (cpuinfo == null ? "" : cpuinfo.toLowerCase());
        for (String k : DSP_SOC_KEYS) {
            if (probe.contains(k)) return true;
        }
        return false;
    }

    private static boolean isArmeabiLegacy() {
        String abi = CpuAbi.getCpuAbi();
        return abi != null && abi.startsWith("armeabi") && !abi.contains("v7");
    }

    private static boolean hasVfp() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Features") && line.contains("vfp")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception e) {
            // 读不到就认为没有
        }
        return false;
    }

    /**
     * CPU 是否支持 NEON 指令集：读 /proc/cpuinfo 的 Features 行。
     * ARMv8 的 32 位兼容层报 asimd，同样视为支持。读取失败按不支持处理。
     */
    public static boolean hasNeon() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Features")) {
                        return line.contains("neon") || line.contains("asimd");
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 是否高通（Qualcomm）/ 联发科（MediaTek）芯片组设备。
     * 老设备 Build.HARDWARE / Build.BOARD 常是机型代号，不含芯片型号，
     * 所以不能只靠单个 Build 字段，需联查多项 Build 字段 + 扫描 /proc/cpuinfo 全文。
     * 用于 2.1-4.0 老设备上 IJK 硬解选项的显示条件。
     */
    public static boolean isQualcommOrMtk() {
        // 组合多项 Build 字段一次性判断
        StringBuilder sb = new StringBuilder();
        String[] fields = {"HARDWARE", "BOARD", "DEVICE", "MANUFACTURER", "BRAND", "MODEL", "PRODUCT", "CPU_ABI"};
        for (String f : fields) {
            String v = getBuildField(f);
            if (v != null) sb.append(v).append(' ');
        }
        String buildText = sb.toString().toLowerCase();
        boolean buildHit = isQualcommText(buildText) || isMtkText(buildText);
        if (buildHit) {
            android.util.Log.e("DeviceUtils", "isQualcommOrMtk: Build字段已命中, [" + buildText + "]");
            return true;
        }

        // /proc/cpuinfo 全文扫描（覆盖 Hardware / model name / Processor / CPU implementer 等行）
        String cpuinfo = readCpuInfo();
        if (cpuinfo != null) {
            String c = cpuinfo.toLowerCase();
            boolean cpuHit = isQualcommText(c) || isMtkText(c) || isQualcommByCpuId(cpuinfo);
            android.util.Log.e("DeviceUtils", "isQualcommOrMtk: cpuinfo命中=" + cpuHit
                    + " 关键行=[" + extractCpuChipLine(cpuinfo) + "]");
            if (cpuHit) return true;
        }

        android.util.Log.e("DeviceUtils", "isQualcommOrMtk: 未识别到高通/MTK 芯片组");
        return false;
    }

    // 高通特征：qualcomm / snapdragon / msm / qcom / qsd / scorpion / krait
    private static boolean isQualcommText(String s) {
        return s.contains("qualcomm") || s.contains("snapdragon")
                || s.contains("msm") || s.contains("qcom") || s.contains("qsd")
                || s.contains("scorpion") || s.contains("krait");
    }

    // 最可靠信号：内核从 CPU MIDR 寄存器读出的 implementer。
    // ARM 公版 Cortex = 0x41；高通自研 Scorpion/Krait = 0x51
    // （MTK 用 ARM 公版 Cortex，implementer 同为 0x41，因此 MTK 只能靠名称关键字判断）。
    private static boolean isQualcommByCpuId(String cpuinfo) {
        if (cpuinfo == null) return false;
        String implementer = getCpuInfoField(cpuinfo, "cpu implementer");
        String part = getCpuInfoField(cpuinfo, "cpu part");
        // implementer 0x51 = Qualcomm（Scorpion：part 0x02d0；Krait 家族同理）
        if ("51".equals(implementer)) {
            return true;
        }
        // 兼容部分老内核：implementer 虽报 0x41（被误判为 ARM 公版），但 part 为 0x02d0 即为 Scorpion
        return "41".equals(implementer) && ("2d0".equals(part) || "002d0".equals(part));
    }

    // 提取 cpuinfo 中某字段的值（如 "cpu implementer : 0x51" -> "51"）
    private static String getCpuInfoField(String cpuinfo, String fieldName) {
        if (cpuinfo == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(fieldName) + "\\s*\\:\\s*(0x[0-9A-Fa-f]+|\\d+)");
        java.util.regex.Matcher m = p.matcher(cpuinfo.toLowerCase());
        if (m.find()) {
            String v = m.group(1);
            if (v.startsWith("0x") || v.startsWith("0x")) {
                v = v.substring(2);
            }
            return v.trim();
        }
        return null;
    }

    // 联发科特征：mediatek / mtk / mt65xx / mt66xx / mt67xx / mt81xx / mt83xx / helio
    private static boolean isMtkText(String s) {
        return s.contains("mediatek") || s.contains("mtk") || s.contains("helio")
                || s.contains("mt65") || s.contains("mt66")
                || s.contains("mt67") || s.contains("mt81") || s.contains("mt83")
                || s.contains("mt85");
    }

    private static String readCpuInfo() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
        }
        return null;
    }

    // 提取 cpuinfo 中与芯片相关的行（用于日志排查）
    private static String extractCpuChipLine(String cpuinfo) {
        if (cpuinfo == null) return "";
        String[] lines = cpuinfo.split("\\n");
        StringBuilder res = new StringBuilder();
        for (String line : lines) {
            String low = line.toLowerCase();
            if (low.startsWith("hardware") || low.startsWith("model name")
                    || low.startsWith("processor") || low.startsWith("features")
                    || low.startsWith("cpu implementer") || low.startsWith("cpu part")) {
                if (res.length() > 0) res.append(" | ");
                res.append(line);
            }
        }
        return res.length() > 300 ? res.substring(0, 300) : res.toString();
    }

    /**
     * 检测是否需要 Legacy 版本喵
     * 返回 true 表示需要 Legacy 版本（不弹窗）
     * 返回 false 表示不需要 Legacy 版本（弹窗提示）
     */
    public static boolean isLegacyDevice() {
        // ARMv5 或 ARMv6 无 VFP，确实需要 Legacy
        if (isArmv5() || isArmv6WithoutVfp()) {
            return true;
        }

        // 检测 SDK 版本（Android 2.2 及以下视为 Legacy）
        if (SdkHelper.getSdkInt() < 9) {
            return true;
        }

        return false;
    }

    /**
     * 检测 ARMv5 还是 ARMv6（兼容 ARMv5TEJ 等格式）
     */
    public static String checkArmv5OrV6() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                // 检查CPU architecture行
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
                // 检查Processor行
                if (line.startsWith("Processor") && (line.contains("ARM926") ||
                        line.contains("ARM922") || line.contains("ARM9EJ"))) {
                    reader.close();
                    return "ARMv5te";
                }
                // 检查Features行（edsp 是 ARMv6 特性）
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
    public static String checkArmv7Subtype() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            boolean isTegra2 = false;
            String hardware = "";

            while ((line = reader.readLine()) != null) {
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

            // Tegra 2：我有最先进的VFPv3-D16，根本不需要NEON，即使死机也无所谓！
            if (isTegra2 || !hasNeon()) {
                return "ARMv7-A (无NEON)";
            }
            return "ARMv7-A (有NEON)";
        } catch (Exception e) {
            return "ARMv7-A";
        }
    }
}
