using System;
using System.Collections.Generic;
using System.Text;

namespace BiliClassic.Api
{
    /// <summary>
    /// 极简QR编码器，只做版本7+L纠错+字节模式
    /// 登录二维码内容144字节，v7-L容量154字节
    /// 掩码固定0，只影响观感，读码器按格式信息自行反算
    /// 照ISO/IEC 18004实现
    /// </summary>
    public static class QrEncoder
    {
        private const int Version = 7;
        private const int EcCodewordsPerBlock = 20;
        private const int BlockCount = 2;
        private const int DataCodewordsPerBlock = 78;
        // 156数据+40纠错
        private const int TotalCodewords = 196;

        /// <summary>矩阵边长，17+4*版本=45</summary>
        public const int Size = 17 + 4 * Version;

        /// <summary>v7-L字节模式容量</summary>
        public const int ByteCapacity = 154;

        /// <summary>
        /// 校正图形中心坐标，ISO/IEC 18004附录E
        /// 三个坐标两两组合都要画，只跳过压住定位图形的三角
        /// </summary>
        private static readonly int[] AlignmentCenters = new int[] { 6, 22, 38 };

        private static readonly byte[] GfExp = new byte[512];
        private static readonly byte[] GfLog = new byte[256];

        static QrEncoder()
        {
            // GF(256)，本原多项式0x11D
            int x = 1;
            for (int i = 0; i < 255; i++)
            {
                GfExp[i] = (byte)x;
                GfLog[x] = (byte)i;
                x <<= 1;
                if ((x & 0x100) != 0)
                {
                    x ^= 0x11D;
                }
            }
            for (int i = 255; i < 512; i++)
            {
                GfExp[i] = GfExp[i - 255];
            }
        }

        /// <summary>
        /// 编码成模块矩阵，matrix[y, x]，true为黑块
        /// 超过154字节抛ArgumentException
        /// </summary>
        public static bool[,] Encode(string content)
        {
            byte[] data = Encoding.UTF8.GetBytes(content ?? "");
            if (data.Length > ByteCapacity)
            {
                throw new ArgumentException(
                    "内容 " + data.Length + " 字节，超过 v7-L 的 " + ByteCapacity + " 字节容量");
            }

            byte[] codewords = BuildCodewords(data);
            bool[,] matrix = new bool[Size, Size];
            bool[,] reserved = new bool[Size, Size];

            DrawFunctionPatterns(matrix, reserved);
            PlaceData(matrix, reserved, codewords);
            DrawFormatInfo(matrix);
            DrawVersionInfo(matrix);

            return matrix;
        }

        // ---------------- 数据码字与纠错 ----------------

        private static byte[] BuildCodewords(byte[] data)
        {
            int totalDataBits = DataCodewordsPerBlock * BlockCount * 8;
            List<bool> bits = new List<bool>();

            // 字节模式
            AppendBits(bits, 0x4, 4);
            // v7~v9的字节模式字符计数是8位
            AppendBits(bits, data.Length, 8);
            for (int i = 0; i < data.Length; i++)
            {
                AppendBits(bits, data[i], 8);
            }

            // 终止符
            AppendBits(bits, 0, Math.Min(4, totalDataBits - bits.Count));
            while (bits.Count % 8 != 0)
            {
                bits.Add(false);
            }

            List<byte> dataCodewords = new List<byte>();
            for (int i = 0; i < bits.Count; i += 8)
            {
                int value = 0;
                for (int j = 0; j < 8; j++)
                {
                    value = (value << 1) | (bits[i + j] ? 1 : 0);
                }
                dataCodewords.Add((byte)value);
            }

            // 交替填充0xEC/0x11直到156个数据码字
            byte[] pad = new byte[] { 0xEC, 0x11 };
            int padIndex = 0;
            while (dataCodewords.Count < DataCodewordsPerBlock * BlockCount)
            {
                dataCodewords.Add(pad[padIndex]);
                padIndex ^= 1;
            }

            return Interleave(dataCodewords.ToArray());
        }

        private static void AppendBits(List<bool> bits, int value, int length)
        {
            for (int i = length - 1; i >= 0; i--)
            {
                bits.Add(((value >> i) & 1) != 0);
            }
        }

        private static byte[] Interleave(byte[] data)
        {
            byte[][] dataBlocks = new byte[BlockCount][];
            byte[][] ecBlocks = new byte[BlockCount][];

            for (int b = 0; b < BlockCount; b++)
            {
                dataBlocks[b] = new byte[DataCodewordsPerBlock];
                Array.Copy(data, b * DataCodewordsPerBlock, dataBlocks[b], 0, DataCodewordsPerBlock);
                ecBlocks[b] = ComputeReedSolomon(dataBlocks[b], EcCodewordsPerBlock);
            }

            byte[] result = new byte[TotalCodewords];
            int index = 0;
            for (int i = 0; i < DataCodewordsPerBlock; i++)
            {
                for (int b = 0; b < BlockCount; b++)
                {
                    result[index++] = dataBlocks[b][i];
                }
            }
            for (int i = 0; i < EcCodewordsPerBlock; i++)
            {
                for (int b = 0; b < BlockCount; b++)
                {
                    result[index++] = ecBlocks[b][i];
                }
            }
            return result;
        }

        private static byte[] ComputeReedSolomon(byte[] data, int ecCount)
        {
            byte[] generator = BuildGenerator(ecCount);
            byte[] remainder = new byte[ecCount];

            for (int i = 0; i < data.Length; i++)
            {
                int factor = (data[i] ^ remainder[0]) & 0xFF;
                for (int j = 0; j < ecCount - 1; j++)
                {
                    remainder[j] = remainder[j + 1];
                }
                remainder[ecCount - 1] = 0;

                for (int j = 0; j < ecCount; j++)
                {
                    remainder[j] ^= Multiply(generator[j + 1], (byte)factor);
                }
            }
            return remainder;
        }

        /// <summary>
        /// 生成多项式(x-a^0)(x-a^1)...(x-a^(ecCount-1))
        /// 系数最高次在前，首项恒为1，故调用处用generator[j+1]
        /// </summary>
        private static byte[] BuildGenerator(int ecCount)
        {
            byte[] generator = new byte[] { 1 };
            for (int i = 0; i < ecCount; i++)
            {
                byte[] next = new byte[generator.Length + 1];
                for (int j = 0; j < generator.Length; j++)
                {
                    next[j] ^= generator[j];
                    next[j + 1] ^= Multiply(generator[j], GfExp[i]);
                }
                generator = next;
            }
            return generator;
        }

        private static byte Multiply(byte a, byte b)
        {
            if (a == 0 || b == 0)
            {
                return 0;
            }
            return GfExp[GfLog[a] + GfLog[b]];
        }

        // ---------------- 功能图形 ----------------

        private static void DrawFunctionPatterns(bool[,] matrix, bool[,] reserved)
        {
            DrawFinder(matrix, reserved, 0, 0);
            DrawFinder(matrix, reserved, Size - 7, 0);
            DrawFinder(matrix, reserved, 0, Size - 7);

            // 定时图形，第6行/第6列，从第8个模块起
            for (int i = 8; i < Size - 8; i++)
            {
                SetFunction(matrix, reserved, i, 6, i % 2 == 0);
                SetFunction(matrix, reserved, 6, i, i % 2 == 0);
            }

            // 校正图形，中心{6,22,38}，只跳过压住定位图形的三角
            // 必须在定时图形之后画，会覆盖经过的定时图形
            for (int i = 0; i < AlignmentCenters.Length; i++)
            {
                for (int j = 0; j < AlignmentCenters.Length; j++)
                {
                    int cx = AlignmentCenters[i];
                    int cy = AlignmentCenters[j];

                    bool overlapsFinder =
                        (cx == 6 && cy == 6) ||
                        (cx == Size - 7 && cy == 6) ||
                        (cx == 6 && cy == Size - 7);
                    if (overlapsFinder)
                    {
                        continue;
                    }

                    DrawAlignment(matrix, reserved, cx, cy);
                }
            }

            // 固定深色模块
            SetFunction(matrix, reserved, 8, Size - 8, true);

            // 预留格式信息区
            for (int i = 0; i <= 8; i++)
            {
                Reserve(reserved, 8, i);
                Reserve(reserved, i, 8);
            }
            for (int i = 0; i < 8; i++)
            {
                Reserve(reserved, Size - 1 - i, 8);
                Reserve(reserved, 8, Size - 1 - i);
            }

            // 预留版本信息区，两块3x6
            for (int i = 0; i < 18; i++)
            {
                int a = Size - 11 + i % 3;
                int b = i / 3;
                Reserve(reserved, a, b);
                Reserve(reserved, b, a);
            }
        }

        /// <summary>定位图形7x7，外带一圈分隔符</summary>
        private static void DrawFinder(bool[,] matrix, bool[,] reserved, int left, int top)
        {
            for (int dy = -1; dy <= 7; dy++)
            {
                for (int dx = -1; dx <= 7; dx++)
                {
                    bool dark =
                        (dx >= 0 && dx <= 6 && (dy == 0 || dy == 6)) ||
                        (dy >= 0 && dy <= 6 && (dx == 0 || dx == 6)) ||
                        (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
                    SetFunction(matrix, reserved, left + dx, top + dy, dark);
                }
            }
        }

        /// <summary>校正图形5x5，外圈黑内圈白中心黑</summary>
        private static void DrawAlignment(bool[,] matrix, bool[,] reserved, int cx, int cy)
        {
            for (int dy = -2; dy <= 2; dy++)
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    bool dark = Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1;
                    SetFunction(matrix, reserved, cx + dx, cy + dy, dark);
                }
            }
        }

        private static void SetFunction(bool[,] matrix, bool[,] reserved, int x, int y, bool dark)
        {
            if (x < 0 || y < 0 || x >= Size || y >= Size)
            {
                return;
            }
            matrix[y, x] = dark;
            reserved[y, x] = true;
        }

        private static void Reserve(bool[,] reserved, int x, int y)
        {
            if (x >= 0 && y >= 0 && x < Size && y < Size)
            {
                reserved[y, x] = true;
            }
        }

        // ---------------- 数据摆放 ----------------

        private static void PlaceData(bool[,] matrix, bool[,] reserved, byte[] codewords)
        {
            int totalBits = codewords.Length * 8;
            int bitIndex = 0;

            // 从右往左两列一组，蛇形上下走，跳过第6列
            for (int right = Size - 1; right >= 1; right -= 2)
            {
                if (right == 6)
                {
                    right = 5;
                }
                for (int vert = 0; vert < Size; vert++)
                {
                    for (int j = 0; j < 2; j++)
                    {
                        int x = right - j;
                        bool upward = ((right + 1) & 2) == 0;
                        int y = upward ? Size - 1 - vert : vert;

                        if (reserved[y, x])
                        {
                            continue;
                        }

                        bool bit = false;
                        if (bitIndex < totalBits)
                        {
                            bit = ((codewords[bitIndex >> 3] >> (7 - (bitIndex & 7))) & 1) != 0;
                        }
                        bitIndex++;

                        // 掩码0，(x+y)为偶数时取反
                        if (((x + y) & 1) == 0)
                        {
                            bit = !bit;
                        }
                        matrix[y, x] = bit;
                    }
                }
            }
        }

        // ---------------- 格式信息与版本信息 ----------------

        private static void DrawFormatInfo(bool[,] matrix)
        {
            // 纠错等级L，掩码0
            int bits = FormatBits(1, 0);

            for (int i = 0; i <= 5; i++)
            {
                matrix[i, 8] = GetBit(bits, i);
            }
            matrix[7, 8] = GetBit(bits, 6);
            matrix[8, 8] = GetBit(bits, 7);
            matrix[8, 7] = GetBit(bits, 8);
            for (int i = 9; i < 15; i++)
            {
                matrix[8, 14 - i] = GetBit(bits, i);
            }

            for (int i = 0; i < 8; i++)
            {
                matrix[8, Size - 1 - i] = GetBit(bits, i);
            }
            for (int i = 8; i < 15; i++)
            {
                matrix[Size - 15 + i, 8] = GetBit(bits, i);
            }
            matrix[Size - 8, 8] = true;   // 固定深色模块
        }

        private static void DrawVersionInfo(bool[,] matrix)
        {
            int bits = VersionBits(Version);
            for (int i = 0; i < 18; i++)
            {
                bool bit = GetBit(bits, i);
                int a = Size - 11 + i % 3;
                int b = i / 3;
                matrix[b, a] = bit;
                matrix[a, b] = bit;
            }
        }

        /// <summary>格式信息，5位数据+BCH(15,5)校验0x537，再异或0x5412</summary>
        private static int FormatBits(int ecLevelBits, int mask)
        {
            int data = (ecLevelBits << 3) | mask;
            int value = data << 10;
            for (int i = 14; i >= 10; i--)
            {
                if (((value >> i) & 1) != 0)
                {
                    value ^= 0x537 << (i - 10);
                }
            }
            return value ^ 0x5412;
        }

        /// <summary>版本信息，6位版本+BCH(18,6)校验0x1F25，无额外异或</summary>
        private static int VersionBits(int version)
        {
            int value = version << 12;
            for (int i = 17; i >= 12; i--)
            {
                if (((value >> i) & 1) != 0)
                {
                    value ^= 0x1F25 << (i - 12);
                }
            }
            return value;
        }

        private static bool GetBit(int value, int index)
        {
            return ((value >> index) & 1) != 0;
        }
    }
}
