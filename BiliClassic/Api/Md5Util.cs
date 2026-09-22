using System;
using System.Text;

namespace BiliClassic.Api
{
    public static class Md5Util
    {
        private static readonly int[] ShiftBits = new int[64]
        {
             7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,
             5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,
             4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,
             6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21
        };

        private static readonly uint[] K = new uint[64]
        {
            0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee,
            0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
            0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be,
            0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
            0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa,
            0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
            0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed,
            0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
            0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c,
            0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
            0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05,
            0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
            0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039,
            0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
            0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1,
            0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391
        };

        public static string Hex(string input)
        {
            return Hex(Encoding.UTF8.GetBytes(input ?? ""));
        }

        public static string Hex(byte[] input)
        {
            byte[] hash = Compute(input);
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < hash.Length; i++)
            {
                sb.Append(hash[i].ToString("x2"));
            }
            return sb.ToString();
        }

        public static byte[] Compute(byte[] input)
        {
            if (input == null)
            {
                input = new byte[0];
            }

            long bitLength = (long)input.Length * 8L;
            int totalLength = ((input.Length + 8) / 64 + 1) * 64;

            byte[] message = new byte[totalLength];
            Array.Copy(input, message, input.Length);
            message[input.Length] = 0x80;
            for (int i = 0; i < 8; i++)
            {
                message[totalLength - 8 + i] = (byte)((bitLength >> (8 * i)) & 0xFFL);
            }

            uint a0 = 0x67452301;
            uint b0 = 0xefcdab89;
            uint c0 = 0x98badcfe;
            uint d0 = 0x10325476;

            uint[] m = new uint[16];
            for (int offset = 0; offset < totalLength; offset += 64)
            {
                for (int i = 0; i < 16; i++)
                {
                    int p = offset + i * 4;
                    m[i] = (uint)(message[p]
                                | (message[p + 1] << 8)
                                | (message[p + 2] << 16)
                                | (message[p + 3] << 24));
                }

                uint a = a0;
                uint b = b0;
                uint c = c0;
                uint d = d0;

                for (int i = 0; i < 64; i++)
                {
                    uint f;
                    int g;
                    if (i < 16)
                    {
                        f = (b & c) | (~b & d);
                        g = i;
                    }
                    else if (i < 32)
                    {
                        f = (d & b) | (~d & c);
                        g = (5 * i + 1) % 16;
                    }
                    else if (i < 48)
                    {
                        f = b ^ c ^ d;
                        g = (3 * i + 5) % 16;
                    }
                    else
                    {
                        f = c ^ (b | ~d);
                        g = (7 * i) % 16;
                    }

                    f = unchecked(f + a + K[i] + m[g]);
                    a = d;
                    d = c;
                    c = b;
                    b = unchecked(b + RotateLeft(f, ShiftBits[i]));
                }

                a0 = unchecked(a0 + a);
                b0 = unchecked(b0 + b);
                c0 = unchecked(c0 + c);
                d0 = unchecked(d0 + d);
            }

            byte[] output = new byte[16];
            WriteUInt32LittleEndian(output, 0, a0);
            WriteUInt32LittleEndian(output, 4, b0);
            WriteUInt32LittleEndian(output, 8, c0);
            WriteUInt32LittleEndian(output, 12, d0);
            return output;
        }

        private static uint RotateLeft(uint value, int bits)
        {
            return (value << bits) | (value >> (32 - bits));
        }

        private static void WriteUInt32LittleEndian(byte[] buffer, int offset, uint value)
        {
            buffer[offset] = (byte)(value & 0xFF);
            buffer[offset + 1] = (byte)((value >> 8) & 0xFF);
            buffer[offset + 2] = (byte)((value >> 16) & 0xFF);
            buffer[offset + 3] = (byte)((value >> 24) & 0xFF);
        }
    }
}
