package tv.biliclassic.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class LocalJsonCrypto {

    private static final String PREFIX = "BCJ1:";
    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private LocalJsonCrypto() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            byte[] all = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);
            return PREFIX + encodeBase64(all);
        } catch (Throwable ignored) {
            return plainText;
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.length() == 0) return stored;
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] all = decodeBase64(stored.substring(PREFIX.length()));
            if (all.length <= 16) return null;
            byte[] iv = new byte[16];
            System.arraycopy(all, 0, iv, 0, 16);
            byte[] data = new byte[all.length - 16];
            System.arraycopy(all, 16, data, 0, data.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new IvParameterSpec(iv));
            return new String(cipher.doFinal(data), "UTF-8");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String readText(File file) throws IOException {
        if (file == null || !file.isFile()) return null;
        String stored = readUtf8(file);
        if (stored == null || stored.length() == 0) return stored;
        if (!isEncrypted(stored)) {
            long modified = file.lastModified();
            writeText(file, stored);
            if (modified > 0) {
                file.setLastModified(modified);
            }
            return stored;
        }
        return decrypt(stored);
    }

    public static void writeText(File file, String plainText) throws IOException {
        if (file == null || plainText == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(encrypt(plainText).getBytes("UTF-8"));
            out.flush();
        } finally {
            try { out.close(); } catch (Throwable ignored) { }
        }
    }

    private static String readUtf8(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static SecretKeySpec key() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] value = digest.digest("BiliClassic-local-json-v1".getBytes("UTF-8"));
        return new SecretKeySpec(value, "AES");
    }

    private static String encodeBase64(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? data[i++] & 0xFF : -1;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            out.append(BASE64[b0 >> 2]);
            out.append(BASE64[((b0 & 3) << 4) | (b1 == -1 ? 0 : b1 >> 4)]);
            out.append(b1 == -1 ? '=' : BASE64[((b1 & 15) << 2) | (b2 == -1 ? 0 : b2 >> 6)]);
            out.append(b2 == -1 ? '=' : BASE64[b2 & 63]);
        }
        return out.toString();
    }

    private static byte[] decodeBase64(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length() * 3 / 4);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '=') break;
            int v = decodeBase64Char(c);
            if (v < 0) continue;
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static int decodeBase64Char(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }
}
