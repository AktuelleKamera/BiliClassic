package tv.biliclassic.util;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.Locale;

public final class CpuAbi {

    public enum Arch {
        Unknown, ARM, X86, MIPS, ARM64, X86_64, MIPS64, RISCV64
    }

    private static volatile Arch sArch = Arch.Unknown;
    private static String sCpuFeatures;
    private static Boolean sNeon;
    private static Boolean sVfp;
    private static Boolean s64Bit;

    private CpuAbi() {
    }

    public static String getCpuAbi() {
        String[] supported = getSupportedAbis();
        if (supported != null) {
            for (String value : supported) {
                if (value != null && value.length() > 0) return value;
            }
        }
        String abi = buildField("CPU_ABI");
        if (abi != null && abi.length() > 0) return abi;
        if (getArch() == Arch.ARM) return "armeabi";
        return abi;
    }

    public static String getCpuAbi2() {
        return buildField("CPU_ABI2");
    }

    public static String getBuildValue(String field) {
        return buildField(field);
    }

    public static String[] getSupportedAbis() {
        try {
            Object value = Build.class.getField("SUPPORTED_ABIS").get(null);
            if (value instanceof String[]) return (String[]) value;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean supportsAbi(String abi) {
        if (abi == null || abi.length() == 0) return false;
        if (abi.equalsIgnoreCase(getCpuAbi()) || abi.equalsIgnoreCase(getCpuAbi2())) {
            return true;
        }
        String[] supported = getSupportedAbis();
        if (supported != null) {
            for (String value : supported) {
                if (abi.equalsIgnoreCase(value)) return true;
            }
        }
        return false;
    }

    public static synchronized Arch getArch() {
        if (sArch != Arch.Unknown) return sArch;
        String[] supported = getSupportedAbis();
        if (supported != null) {
            for (String abi : supported) {
                Arch arch = archFromAbi(abi);
                if (arch != Arch.Unknown) {
                    sArch = arch;
                    return arch;
                }
            }
        }
        File[] files = {
                new File("/system/lib64/libc.so"),
                new File("/apex/com.android.runtime/lib64/bionic/libc.so"),
                new File("/system/lib/libc.so"),
                new File("/apex/com.android.runtime/lib/bionic/libc.so")
        };
        byte[] header = new byte[20];
        for (File file : files) {
            if (file == null || !file.canRead()) continue;
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(file, "r");
                raf.readFully(header);
                if (header[0] != 0x7f || header[1] != 'E'
                        || header[2] != 'L' || header[3] != 'F') {
                    continue;
                }
                int elfClass = header[4] & 0xFF;
                boolean is64 = elfClass == 2;
                boolean bigEndian = (header[5] & 0xFF) == 2;
                int machine = bigEndian
                        ? ((header[18] & 0xFF) << 8) | (header[19] & 0xFF)
                        : ((header[19] & 0xFF) << 8) | (header[18] & 0xFF);
                Arch arch = archFromMachine(machine, is64);
                if (arch != Arch.Unknown) {
                    sArch = arch;
                    return arch;
                }
            } catch (Throwable ignored) {
            } finally {
                if (raf != null) {
                    try { raf.close(); } catch (Throwable ignored) {}
                }
            }
        }
        return sArch;
    }

    private static Arch archFromAbi(String abi) {
        if (abi == null) return Arch.Unknown;
        String value = abi.toLowerCase(Locale.US);
        if (value.equals("arm64-v8a")) return Arch.ARM64;
        if (value.equals("armeabi-v7a") || value.equals("armeabi")) return Arch.ARM;
        if (value.equals("x86_64")) return Arch.X86_64;
        if (value.equals("x86")) return Arch.X86;
        if (value.equals("mips64")) return Arch.MIPS64;
        if (value.equals("mips")) return Arch.MIPS;
        if (value.equals("riscv64")) return Arch.RISCV64;
        return Arch.Unknown;
    }

    private static Arch archFromMachine(int machine, boolean is64) {
        switch (machine) {
            case 3: return Arch.X86;
            case 8: return is64 ? Arch.MIPS64 : Arch.MIPS;
            case 40: return Arch.ARM;
            case 62: return Arch.X86_64;
            case 183: return Arch.ARM64;
            case 243: return Arch.RISCV64;
            default: return Arch.Unknown;
        }
    }


    public static boolean is64Bit() {
        if (s64Bit != null) return s64Bit.booleanValue();
        String[] supported = getSupportedAbis();
        if (supported != null) {
            for (String abi : supported) {
                if (abi != null && abi.toLowerCase(Locale.US).contains("64")) {
                    s64Bit = Boolean.TRUE;
                    return true;
                }
            }
        }
        Arch arch = getArch();
        if (arch == Arch.ARM64 || arch == Arch.X86_64
                || arch == Arch.MIPS64 || arch == Arch.RISCV64) {
            s64Bit = Boolean.TRUE;
            return true;
        }
        try {
            Object value = android.os.Process.class.getMethod("is64Bit")
                    .invoke(null);
            if (value instanceof Boolean) {
                s64Bit = (Boolean) value;
                return s64Bit.booleanValue();
            }
        } catch (Throwable ignored) {
        }
        String abi = getCpuAbi();
        s64Bit = Boolean.valueOf(abi != null && abi.contains("64"));
        return s64Bit.booleanValue();
    }

    public static boolean isLegacyArm() {
        String abi = getCpuAbi();
        return abi != null && abi.toLowerCase(Locale.US).startsWith("armeabi")
                && !abi.toLowerCase(Locale.US).contains("v7");
    }

    public static boolean isArm() {
        return supportsAbi("armeabi") || supportsAbi("armeabi-v7a")
                || getArch() == Arch.ARM;
    }

    public static boolean isX86() {
        return supportsAbi("x86") || getArch() == Arch.X86;
    }

    public static boolean isRiscv64() {
        return supportsAbi("riscv64") || getArch() == Arch.RISCV64;
    }

    public static boolean hasNeon() {
        if (sNeon == null) {
            String features = cpuFeatures();
            sNeon = Boolean.valueOf(features.contains("neon"));
        }
        return sNeon.booleanValue();
    }

    public static boolean hasVfp() {
        if (sVfp == null) {
            String features = cpuFeatures();
            sVfp = Boolean.valueOf(features.contains("vfp")
                    || features.contains("asimd"));
        }
        return sVfp.booleanValue();
    }

    private static synchronized String cpuFeatures() {
        if (sCpuFeatures != null) return sCpuFeatures;
        StringBuilder result = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("features")) {
                    result.append(lower).append('\n');
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Throwable ignored) {}
            }
        }
        sCpuFeatures = result.toString();
        return sCpuFeatures;
    }

    private static String buildField(String field) {
        try {
            Object value = Build.class.getField(field).get(null);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
