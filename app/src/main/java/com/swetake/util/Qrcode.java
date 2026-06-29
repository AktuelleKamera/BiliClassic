/*
 * QRCode.java - QR Code generation
 *
 * This file is based on Swetake QRCode (http://www.swetake.com/qrcode/)
 * Copyright (c) Swetake. All rights reserved.
 *
 * Licensed under the original Swetake license:
 * - Free to use, modify and redistribute for non-commercial purposes
 * - Must retain this copyright notice in all copies or substantial portions
 *
 * ---
 *
 * BiliClassic - A classic Bilibili client for legacy Android devices
 * Copyright (c) 2026 BiliClassic Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// Modified for Android compatibility
//

package com.swetake.util;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;

public class Qrcode {

    private static final String TAG = "Qrcode";
    private static Context sContext;

    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
            Log.d(TAG, "Qrcode 初始化成功");
        } else {
            Log.e(TAG, "Qrcode 初始化失败，Context 为 null");
        }
    }

    static final String QRCODE_DATA_PATH = "qrcode_data";
    char qrcodeErrorCorrect = 77;
    char qrcodeEncodeMode = 66;
    int qrcodeVersion = 0;
    int qrcodeStructureappendN = 0;
    int qrcodeStructureappendM = 0;
    int qrcodeStructureappendParity = 0;
    String qrcodeStructureappendOriginaldata = "";

    public Qrcode() {
    }

    public void setQrcodeErrorCorrect(char var1) {
        this.qrcodeErrorCorrect = var1;
    }

    public char getQrcodeErrorCorrect() {
        return this.qrcodeErrorCorrect;
    }

    public int getQrcodeVersion() {
        return this.qrcodeVersion;
    }

    public void setQrcodeVersion(int var1) {
        if(var1 >= 0 && var1 <= 40) {
            this.qrcodeVersion = var1;
        }
    }

    public void setQrcodeEncodeMode(char var1) {
        this.qrcodeEncodeMode = var1;
    }

    public char getQrcodeEncodeMode() {
        return this.qrcodeEncodeMode;
    }

    public void setStructureappend(int var1, int var2, int var3) {
        if(var2 > 1 && var2 <= 16 && var1 > 0 && var1 <= 16 && var3 >= 0 && var3 <= 255) {
            this.qrcodeStructureappendM = var1;
            this.qrcodeStructureappendN = var2;
            this.qrcodeStructureappendParity = var3;
        }
    }

    public int calStructureappendParity(byte[] var1) {
        int var3 = 0;
        boolean var4 = false;
        int var2 = var1.length;
        int var5;
        if(var2 > 1) {
            for(var5 = 0; var3 < var2; ++var3) {
                var5 ^= var1[var3] & 255;
            }
        } else {
            var5 = -1;
        }
        return var5;
    }

    private static InputStream getDataStream(String path) {
        Log.d(TAG, "尝试加载数据文件: " + path);

        try {
            if (sContext != null) {
                String fileName = path.substring(path.lastIndexOf("/") + 1);
                try {
                    InputStream is = sContext.getAssets().open("qrcode_data/" + fileName);
                    if (is != null) {
                        Log.d(TAG, "从 assets 加载成功: " + fileName);
                        return is;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "从 assets 加载失败: " + e.getMessage());
                }
            } else {
                Log.e(TAG, "sContext 为 null，无法从 assets 加载");
            }

            InputStream is = Qrcode.class.getResourceAsStream(path);
            if (is != null) {
                Log.d(TAG, "从 classpath 加载成功: " + path);
                return is;
            }
            is = Qrcode.class.getResourceAsStream("/" + path);
            if (is != null) {
                Log.d(TAG, "从 classpath 加载成功 (绝对路径): " + path);
                return is;
            }

            Log.e(TAG, "找不到数据文件: " + path);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "加载数据文件异常: " + path + " - " + e.getMessage());
            return null;
        }
    }

    public boolean[][] calQrcode(byte[] var1) {
        byte var3 = 0;
        int var2 = var1.length;
        int[] var4 = new int[var2 + 32];
        byte[] var5 = new byte[var2 + 32];
        if(var2 <= 0) {
            return new boolean[][]{{false}};
        } else {
            if(this.qrcodeStructureappendN > 1) {
                var4[0] = 3;
                var5[0] = 4;
                var4[1] = this.qrcodeStructureappendM - 1;
                var5[1] = 4;
                var4[2] = this.qrcodeStructureappendN - 1;
                var5[2] = 4;
                var4[3] = this.qrcodeStructureappendParity;
                var5[3] = 8;
                var3 = 4;
            }

            var5[var3] = 4;
            int[] var6;
            int var7;
            int var54;
            switch(this.qrcodeEncodeMode) {
                case 'A':
                    var6 = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
                    var4[var3] = 2;
                    var54 = var3 + 1;
                    var4[var54] = var2;
                    var5[var54] = 9;
                    var7 = var54++;

                    for(int var8 = 0; var8 < var2; ++var8) {
                        char var56 = (char)var1[var8];
                        byte var10 = 0;
                        if(var56 >= 48 && var56 < 58) {
                            var10 = (byte)(var56 - 48);
                        } else if(var56 >= 65 && var56 < 91) {
                            var10 = (byte)(var56 - 55);
                        } else {
                            if(var56 == 32) {
                                var10 = 36;
                            }
                            if(var56 == 36) {
                                var10 = 37;
                            }
                            if(var56 == 37) {
                                var10 = 38;
                            }
                            if(var56 == 42) {
                                var10 = 39;
                            }
                            if(var56 == 43) {
                                var10 = 40;
                            }
                            if(var56 == 45) {
                                var10 = 41;
                            }
                            if(var56 == 46) {
                                var10 = 42;
                            }
                            if(var56 == 47) {
                                var10 = 43;
                            }
                            if(var56 == 58) {
                                var10 = 44;
                            }
                        }

                        if(var8 % 2 == 0) {
                            var4[var54] = var10;
                            var5[var54] = 6;
                        } else {
                            var4[var54] = var4[var54] * 45 + var10;
                            var5[var54] = 11;
                            if(var8 < var2 - 1) {
                                ++var54;
                            }
                        }
                    }

                    ++var54;
                    break;
                case 'N':
                    var6 = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
                    var4[var3] = 1;
                    var54 = var3 + 1;
                    var4[var54] = var2;
                    var5[var54] = 10;
                    var7 = var54++;

                    for(int var9 = 0; var9 < var2; ++var9) {
                        if(var9 % 3 == 0) {
                            var4[var54] = var1[var9] - 48;
                            var5[var54] = 4;
                        } else {
                            var4[var54] = var4[var54] * 10 + (var1[var9] - 48);
                            if(var9 % 3 == 1) {
                                var5[var54] = 7;
                            } else {
                                var5[var54] = 10;
                                if(var9 < var2 - 1) {
                                    ++var54;
                                }
                            }
                        }
                    }

                    ++var54;
                    break;
                default:
                    var6 = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8};
                    var4[var3] = 4;
                    var54 = var3 + 1;
                    var4[var54] = var2;
                    var5[var54] = 8;
                    var7 = var54++;

                    for(int var57 = 0; var57 < var2; ++var57) {
                        var4[var57 + var54] = var1[var57] & 255;
                        var5[var57 + var54] = 8;
                    }

                    var54 += var2;
            }

            int var11 = 0;
            for(int var12 = 0; var12 < var54; ++var12) {
                var11 += var5[var12];
            }

            byte var13;
            switch(this.qrcodeErrorCorrect) {
                case 'H':
                    var13 = 2;
                    break;
                case 'L':
                    var13 = 1;
                    break;
                case 'Q':
                    var13 = 3;
                    break;
                default:
                    var13 = 0;
            }

            int[][] var14 = new int[][]{{0, 128, 224, 352, 512, 688, 864, 992, 1232, 1456, 1728, 2032, 2320, 2672, 2920, 3320, 3624, 4056, 4504, 5016, 5352, 5712, 6256, 6880, 7312, 8000, 8496, 9024, 9544, 10136, 10984, 11640, 12328, 13048, 13800, 14496, 15312, 15936, 16816, 17728, 18672}, {0, 152, 272, 440, 640, 864, 1088, 1248, 1552, 1856, 2192, 2592, 2960, 3424, 3688, 4184, 4712, 5176, 5768, 6360, 6888, 7456, 8048, 8752, 9392, 10208, 10960, 11744, 12248, 13048, 13880, 14744, 15640, 16568, 17528, 18448, 19472, 20528, 21616, 22496, 23648}, {0, 72, 128, 208, 288, 368, 480, 528, 688, 800, 976, 1120, 1264, 1440, 1576, 1784, 2024, 2264, 2504, 2728, 3080, 3248, 3536, 3712, 4112, 4304, 4768, 5024, 5288, 5608, 5960, 6344, 6760, 7208, 7688, 7888, 8432, 8768, 9136, 9776, 10208}, {0, 104, 176, 272, 384, 496, 608, 704, 880, 1056, 1232, 1440, 1648, 1952, 2088, 2360, 2600, 2936, 3176, 3560, 3880, 4096, 4544, 4912, 5312, 5744, 6032, 6464, 6968, 7288, 7880, 8264, 8920, 9368, 9848, 10288, 10832, 11408, 12016, 12656, 13328}};

            int var15 = 0;
            // 强制使用版本 10
            if (this.qrcodeVersion == 0 || this.qrcodeVersion < 10) {
                this.qrcodeVersion = 10;
            }
            if (this.qrcodeVersion > 40) {
                this.qrcodeVersion = 40;
            }
            var15 = var14[var13][this.qrcodeVersion];
            Log.d(TAG, "使用版本: " + this.qrcodeVersion + ", 纠错等级: " + var13 + ", 容量: " + var15);

            var11 += var6[this.qrcodeVersion];
            var5[var7] = (byte)(var5[var7] + var6[this.qrcodeVersion]);

            int[] var58 = new int[]{0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346, 404, 466, 532, 581, 655, 733, 815, 901, 991, 1085, 1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185, 2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706};
            int var17 = var58[this.qrcodeVersion];
            int var18 = 17 + (this.qrcodeVersion << 2);
            int[] var19 = new int[]{0, 0, 7, 7, 7, 7, 7, 0, 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0};
            int var20 = var19[this.qrcodeVersion] + (var17 << 3);

            byte[] var21 = new byte[var20];
            byte[] var22 = new byte[var20];
            byte[] var23 = new byte[var20];
            byte[] var24 = new byte[15];
            byte[] var25 = new byte[15];
            byte[] var26 = new byte[1];
            byte[] var27 = new byte[128];

            try {
                String var28 = "qrcode_data/qrv" + Integer.toString(this.qrcodeVersion) + "_" + Integer.toString(var13) + ".dat";
                InputStream var29 = getDataStream(var28);
                if (var29 == null) {
                    System.err.println("无法加载数据文件: " + var28);
                    return new boolean[1][1];
                }
                BufferedInputStream var30 = new BufferedInputStream(var29);
                var30.read(var21);
                var30.read(var22);
                var30.read(var23);
                var30.read(var24);
                var30.read(var25);
                var30.read(var26);
                var30.read(var27);
                var30.close();
                var29.close();
            } catch (Exception var53) {
                var53.printStackTrace();
                return new boolean[1][1];
            }

            byte var59 = 1;
            for(byte var60 = 1; var60 < 128; ++var60) {
                if(var27[var60] == 0) {
                    var59 = var60;
                    break;
                }
            }

            byte[] var61 = new byte[var59];
            System.arraycopy(var27, 0, var61, 0, var59);

            byte[] var31 = new byte[]{0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8};
            byte[] var32 = new byte[]{8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0};

            int var33 = var15 >> 3;
            int var34 = 4 * this.qrcodeVersion + 17;
            int var35 = var34 * var34;
            byte[] var36 = new byte[var35 + var34];

            try {
                String var37 = "qrcode_data/qrvfr" + Integer.toString(this.qrcodeVersion) + ".dat";
                InputStream var38 = getDataStream(var37);
                if (var38 == null) {
                    System.err.println("无法加载数据文件: " + var37);
                    return new boolean[1][1];
                }
                BufferedInputStream var39 = new BufferedInputStream(var38);
                var39.read(var36);
                var39.close();
                var38.close();
            } catch (Exception var52) {
                var52.printStackTrace();
                return new boolean[1][1];
            }

            // 修复溢出问题
            if(var11 <= var15 - 4) {
                var4[var54] = 0;
                var5[var54] = 4;
            } else if(var11 < var15) {
                var4[var54] = 0;
                var5[var54] = (byte)(var15 - var11);
            } else if(var11 > var15) {
                // 数据溢出，截断到最大容量
                System.err.println("overflow: var11=" + var11 + " var15=" + var15);
                var5[var54] = 0;
                var11 = var15;
            }

            byte[] var62 = divideDataBy8Bits(var4, var5, var33);
            byte[] var63 = calculateRSECC(var62, var26[0], var61, var33, var17);
            byte[][] var64 = new byte[var34][var34];

            int var41;
            for(int var40 = 0; var40 < var34; ++var40) {
                for(var41 = 0; var41 < var34; ++var41) {
                    var64[var41][var40] = 0;
                }
            }

            int var43;
            for(var41 = 0; var41 < var17; ++var41) {
                byte var42 = var63[var41];
                for(var43 = 7; var43 >= 0; --var43) {
                    int var44 = var41 * 8 + var43;
                    var64[var21[var44] & 255][var22[var44] & 255] = (byte)(255 * (var42 & 1) ^ var23[var44]);
                    var42 = (byte)((var42 & 255) >>> 1);
                }
            }

            for(int var65 = var19[this.qrcodeVersion]; var65 > 0; --var65) {
                var43 = var65 + var17 * 8 - 1;
                var64[var21[var43] & 255][var22[var43] & 255] = (byte)(255 ^ var23[var43]);
            }

            byte var66 = selectMask(var64, var19[this.qrcodeVersion] + var17 * 8);
            byte var67 = (byte)(1 << var66);
            byte var45 = (byte)(var13 << 3 | var66);
            String[] var46 = new String[]{"101010000010010", "101000100100101", "101111001111100", "101101101001011", "100010111111001", "100000011001110", "100111110010111", "100101010100000", "111011111000100", "111001011110011", "111110110101010", "111100010011101", "110011000101111", "110001100011000", "110110001000001", "110100101110110", "001011010001001", "001001110111110", "001110011100111", "001100111010000", "000011101100010", "000001001010101", "000110100001100", "000100000111011", "011010101011111", "011000001101000", "011111100110001", "011101000000110", "010010010110100", "010000110000011", "010111011011010", "010101101111101"};

            for(int var47 = 0; var47 < 15; ++var47) {
                byte var48 = Byte.parseByte(var46[var45].substring(var47, var47 + 1));
                var64[var31[var47] & 255][var32[var47] & 255] = (byte)(var48 * 255);
                var64[var24[var47] & 255][var25[var47] & 255] = (byte)(var48 * 255);
            }

            boolean[][] var68 = new boolean[var34][var34];
            int var49 = 0;
            for(int var50 = 0; var50 < var34; ++var50) {
                for(int var51 = 0; var51 < var34; ++var51) {
                    if((var64[var51][var50] & var67) == 0 && var36[var49] != 49) {
                        var68[var51][var50] = false;
                    } else {
                        var68[var51][var50] = true;
                    }
                    ++var49;
                }
                ++var49;
            }

            return var68;
        }
    }

    private static byte[] divideDataBy8Bits(int[] var0, byte[] var1, int var2) {
        int var3 = var1.length;
        int var5 = 0;
        int var6 = 8;
        int var7 = 0;
        for(int var11 = 0; var11 < var3; ++var11) {
            var7 += var1[var11];
        }
        int var4 = (var7 - 1) / 8 + 1;
        byte[] var12 = new byte[var2];
        for(int var13 = 0; var13 < var4; ++var13) {
            var12[var13] = 0;
        }

        boolean var10;
        for(int var14 = 0; var14 < var3; ++var14) {
            int var8 = var0[var14];
            int var9 = var1[var14];
            var10 = true;
            if(var9 == 0) {
                break;
            }
            while(var10) {
                if(var6 > var9) {
                    var12[var5] = (byte)(var12[var5] << var9 | var8);
                    var6 -= var9;
                    var10 = false;
                } else {
                    var9 -= var6;
                    var12[var5] = (byte)(var12[var5] << var6 | var8 >> var9);
                    if(var9 == 0) {
                        var10 = false;
                    } else {
                        var8 &= (1 << var9) - 1;
                        var10 = true;
                    }
                    ++var5;
                    var6 = 8;
                }
            }
        }

        if(var6 != 8) {
            var12[var5] = (byte)(var12[var5] << var6);
        } else {
            --var5;
        }

        if(var5 < var2 - 1) {
            for(var10 = true; var5 < var2 - 1; var10 = !var10) {
                ++var5;
                if(var10) {
                    var12[var5] = -20;
                } else {
                    var12[var5] = 17;
                }
            }
        }
        return var12;
    }

    private static byte[] calculateRSECC(byte[] var0, byte var1, byte[] var2, int var3, int var4) {
        byte[][] var5 = new byte[256][var1];

        try {
            String var6 = "qrcode_data/rsc" + Byte.toString(var1) + ".dat";
            InputStream var7 = getDataStream(var6);
            if (var7 == null) {
                System.err.println("找不到RSECC数据文件: " + var6);
                return new byte[var4];
            }
            BufferedInputStream var8 = new BufferedInputStream(var7);
            for(int var9 = 0; var9 < 256; ++var9) {
                var8.read(var5[var9]);
            }
            var8.close();
            var7.close();
        } catch (Exception var17) {
            var17.printStackTrace();
            return new byte[var4];
        }

        boolean var18 = false;
        int var20 = 0;
        int var21 = 0;
        byte[][] var22 = new byte[var2.length][];
        byte[] var10 = new byte[var4];
        System.arraycopy(var0, 0, var10, 0, var0.length);

        int var19;
        for(var19 = 0; var19 < var2.length; ++var19) {
            var22[var19] = new byte[(var2[var19] & 255) - var1];
        }

        for(var19 = 0; var19 < var3; ++var19) {
            var22[var21][var20] = var0[var19];
            ++var20;
            if(var20 >= (var2[var21] & 255) - var1) {
                var20 = 0;
                ++var21;
            }
        }

        for(var21 = 0; var21 < var2.length; ++var21) {
            byte[] var11 = (byte[])var22[var21].clone();
            int var12 = var2[var21] & 255;
            int var13 = var12 - var1;
            for(var20 = var13; var20 > 0; --var20) {
                byte var14 = var11[0];
                byte[] var15;
                if(var14 != 0) {
                    var15 = new byte[var11.length - 1];
                    System.arraycopy(var11, 1, var15, 0, var11.length - 1);
                    byte[] var16 = var5[var14 & 255];
                    var11 = calculateByteArrayBits(var15, var16, "xor");
                } else if(var1 < var11.length) {
                    var15 = new byte[var11.length - 1];
                    System.arraycopy(var11, 1, var15, 0, var11.length - 1);
                    var11 = (byte[])var15.clone();
                } else {
                    var15 = new byte[var1];
                    System.arraycopy(var11, 1, var15, 0, var11.length - 1);
                    var15[var1 - 1] = 0;
                    var11 = (byte[])var15.clone();
                }
            }
            System.arraycopy(var11, 0, var10, var0.length + var21 * var1, var1);
        }
        return var10;
    }

    private static byte[] calculateByteArrayBits(byte[] var0, byte[] var1, String var2) {
        byte[] var6;
        byte[] var7;
        if(var0.length > var1.length) {
            var6 = (byte[])var0.clone();
            var7 = (byte[])var1.clone();
        } else {
            var6 = (byte[])var1.clone();
            var7 = (byte[])var0.clone();
        }

        int var3 = var6.length;
        int var4 = var7.length;
        byte[] var5 = new byte[var3];

        for(int var8 = 0; var8 < var3; ++var8) {
            if(var8 < var4) {
                if(var2 == "xor") {
                    var5[var8] = (byte)(var6[var8] ^ var7[var8]);
                } else {
                    var5[var8] = (byte)(var6[var8] | var7[var8]);
                }
            } else {
                var5[var8] = var6[var8];
            }
        }
        return var5;
    }

    private static byte selectMask(byte[][] var0, int var1) {
        int var2 = var0.length;
        int[] var3 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        int[] var4 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        int[] var5 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        int[] var6 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
        int var7 = 0;
        int var8 = 0;
        int[] var9 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};

        int var15;
        for(int var10 = 0; var10 < var2; ++var10) {
            int[] var11 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
            int[] var12 = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
            boolean[] var13 = new boolean[]{false, false, false, false, false, false, false, false};
            boolean[] var14 = new boolean[]{false, false, false, false, false, false, false, false};

            for(var15 = 0; var15 < var2; ++var15) {
                if(var15 > 0 && var10 > 0) {
                    var7 = var0[var15][var10] & var0[var15 - 1][var10] & var0[var15][var10 - 1] & var0[var15 - 1][var10 - 1] & 255;
                    var8 = var0[var15][var10] & 255 | var0[var15 - 1][var10] & 255 | var0[var15][var10 - 1] & 255 | var0[var15 - 1][var10 - 1] & 255;
                }

                for(int var16 = 0; var16 < 8; ++var16) {
                    var11[var16] = (var11[var16] & 63) << 1 | (var0[var15][var10] & 255) >>> var16 & 1;
                    var12[var16] = (var12[var16] & 63) << 1 | (var0[var10][var15] & 255) >>> var16 & 1;
                    if((var0[var15][var10] & 1 << var16) != 0) {
                        ++var9[var16];
                    }
                    if(var11[var16] == 93) {
                        var5[var16] += 40;
                    }
                    if(var12[var16] == 93) {
                        var5[var16] += 40;
                    }
                    if(var15 > 0 && var10 > 0) {
                        if((var7 & 1) != 0 || (var8 & 1) == 0) {
                            var4[var16] += 3;
                        }
                        var7 >>= 1;
                        var8 >>= 1;
                    }
                    if((var11[var16] & 31) != 0 && (var11[var16] & 31) != 31) {
                        var13[var16] = false;
                    } else if(var15 > 3) {
                        if(var13[var16]) {
                            ++var3[var16];
                        } else {
                            var3[var16] += 3;
                            var13[var16] = true;
                        }
                    }
                    if((var12[var16] & 31) != 0 && (var12[var16] & 31) != 31) {
                        var14[var16] = false;
                    } else if(var15 > 3) {
                        if(var14[var16]) {
                            ++var3[var16];
                        } else {
                            var3[var16] += 3;
                            var14[var16] = true;
                        }
                    }
                }
            }
        }

        int var17 = 0;
        byte var18 = 0;
        int[] var19 = new int[]{90, 80, 70, 60, 50, 40, 30, 20, 10, 0, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 90};

        for(int var20 = 0; var20 < 8; ++var20) {
            var6[var20] = var19[20 * var9[var20] / var1];
            var15 = var3[var20] + var4[var20] + var5[var20] + var6[var20];
            if(var15 < var17 || var20 == 0) {
                var18 = (byte)var20;
                var17 = var15;
            }
        }
        return var18;
    }
}