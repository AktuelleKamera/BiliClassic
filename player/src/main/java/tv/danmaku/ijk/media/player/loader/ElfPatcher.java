package tv.danmaku.ijk.media.player.loader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal ARM ELF32 patcher for API 1-3 DT_NEEDED paths.
 * The patched file gets a new read-only PT_LOAD segment containing a copied
 * dynstr plus the replacement strings.
 */
public final class ElfPatcher {
    private static final int PT_LOAD = 1;
    private static final int PT_DYNAMIC = 2;

    private static final int DT_NEEDED = 1;
    private static final int DT_STRTAB = 5;
    private static final int DT_STRSZ = 10;

    private ElfPatcher() {
    }

    public static void patch(File input, File output, Map<String, String> replacements)
            throws IOException {
        byte[] data = readAll(input);
        if (data.length < 52 || data[0] != 0x7f || data[1] != 'E'
                || data[2] != 'L' || data[3] != 'F'
                || data[4] != 1 || data[5] != 1) {
            throw new IOException("not ELF32 little-endian");
        }

        int phoff = u32(data, 28);
        int phentsize = u16(data, 42);
        int phnum = u16(data, 44);
        int[] ph = new int[phnum];
        int dynamicOff = -1;
        int dynamicSize = 0;
        int lastLoadIndex = -1;
        int lastLoadOffset = 0;
        int lastLoadVaddr = 0;
        int lastLoadFileSize = 0;

        for (int i = 0; i < phnum; i++) {
            int off = phoff + i * phentsize;
            ph[i] = off;
            int type = u32(data, off);
            int vaddr = u32(data, off + 8);
            int memsz = u32(data, off + 20);
            if (type == PT_DYNAMIC) {
                dynamicOff = u32(data, off + 4);
                dynamicSize = u32(data, off + 16);
            } else if (type == PT_LOAD) {
                int fileOffset = u32(data, off + 4);
                if (lastLoadIndex < 0 || fileOffset > lastLoadOffset) {
                    lastLoadIndex = i;
                    lastLoadOffset = fileOffset;
                    lastLoadVaddr = vaddr;
                    lastLoadFileSize = u32(data, off + 16);
                }
            }
        }

        if (dynamicOff < 0 || dynamicSize <= 0 || lastLoadIndex < 0) {
            throw new IOException("ELF layout not supported");
        }

        int strtabVaddr = -1;
        int strsz = -1;
        List<Integer> neededEntries = new ArrayList<Integer>();
        int strtabEntry = -1;
        int strszEntry = -1;
        for (int j = 0; j < dynamicSize; j += 8) {
            int entry = dynamicOff + j;
            int tag = s32(data, entry);
            int value = u32(data, entry + 4);
            if (tag == 0) break;
            if (tag == DT_NEEDED) neededEntries.add(Integer.valueOf(entry));
            if (tag == DT_STRTAB) {
                strtabVaddr = value;
                strtabEntry = entry;
            }
            if (tag == DT_STRSZ) {
                strsz = value;
                strszEntry = entry;
            }
        }
        if (strtabVaddr < 0 || strsz <= 0 || strtabEntry < 0 || strszEntry < 0) {
            throw new IOException("dynstr not found");
        }

        int strtabOff = vaddrToOffset(data, ph, strtabVaddr, strsz);
        byte[] oldDynstr = copyOfRange(data, strtabOff, strtabOff + strsz);
        ByteArrayOutputStream dynstr = new ByteArrayOutputStream();
        dynstr.write(oldDynstr, 0, oldDynstr.length);
        Map<String, Integer> newOffsets = new HashMap<String, Integer>();

        for (int i = 0; i < neededEntries.size(); i++) {
            int entry = neededEntries.get(i).intValue();
            int nameOff = u32(data, entry + 4);
            String name = readString(oldDynstr, nameOff);
            String replacement = replacements.get(name);
            if (replacement == null) continue;

            Integer newOff = newOffsets.get(replacement);
            if (newOff == null) {
                newOff = Integer.valueOf(dynstr.size());
                byte[] bytes = replacement.getBytes("UTF-8");
                dynstr.write(bytes, 0, bytes.length);
                dynstr.write(0);
                newOffsets.put(replacement, newOff);
            }
            put32(data, entry + 4, newOff.intValue());
        }

        byte[] newDynstr = dynstr.toByteArray();
        int newOffset = data.length;
        int newVaddr = lastLoadVaddr + (newOffset - lastLoadOffset);
        put32(data, strtabEntry + 4, newVaddr);
        put32(data, strszEntry + 4, newDynstr.length);

        int lastLoadPh = ph[lastLoadIndex];
        for (int i = lastLoadOffset + lastLoadFileSize; i < newOffset; i++) {
            data[i] = 0;
        }
        int newLoadSize = newOffset + newDynstr.length - lastLoadOffset;
        put32(data, lastLoadPh + 16, newLoadSize);
        put32(data, lastLoadPh + 20, newLoadSize);

        byte[] result = new byte[newOffset + newDynstr.length];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(newDynstr, 0, result, newOffset, newDynstr.length);

        FileOutputStream out = new FileOutputStream(output);
        try {
            out.write(result);
        } finally {
            out.close();
        }
    }

    private static int vaddrToOffset(byte[] data, int[] ph, int vaddr, int size)
            throws IOException {
        for (int i = 0; i < ph.length; i++) {
            int off = ph[i];
            if (u32(data, off) != PT_LOAD) continue;
            int fileOff = u32(data, off + 4);
            int fileVaddr = u32(data, off + 8);
            int fileSize = u32(data, off + 16);
            if (vaddr >= fileVaddr && vaddr + size <= fileVaddr + fileSize) {
                return fileOff + (vaddr - fileVaddr);
            }
        }
        throw new IOException("vaddr not mapped");
    }

    private static byte[] copyOfRange(byte[] data, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }

    private static String readString(byte[] data, int offset) throws IOException {
        if (offset < 0 || offset >= data.length) throw new IOException("bad string offset");
        int end = offset;
        while (end < data.length && data[end] != 0) end++;
        if (end >= data.length) throw new IOException("unterminated string");
        return new String(data, offset, end - offset, "UTF-8");
    }

    private static byte[] readAll(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static int alignUp(int value, int align) {
        return (value + align - 1) & ~(align - 1);
    }

    private static int u16(byte[] data, int off) {
        return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8);
    }

    private static int u32(byte[] data, int off) {
        return (data[off] & 0xff)
                | ((data[off + 1] & 0xff) << 8)
                | ((data[off + 2] & 0xff) << 16)
                | ((data[off + 3] & 0xff) << 24);
    }

    private static int s32(byte[] data, int off) {
        return u32(data, off);
    }

    private static void put32(byte[] data, int off, int value) {
        data[off] = (byte) value;
        data[off + 1] = (byte) (value >>> 8);
        data[off + 2] = (byte) (value >>> 16);
        data[off + 3] = (byte) (value >>> 24);
    }
}
