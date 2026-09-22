using System;

namespace BiliClassic.Danmaku
{
    public static class Inflate
    {
        private const int MaxBits = 15;

        private const int MaxOutput = 8 * 1024 * 1024;

        private static readonly short[] LengthBase = new short[]
        {
            3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
            35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
        };

        private static readonly short[] LengthExtra = new short[]
        {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
            3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
        };

        private static readonly short[] DistBase = new short[]
        {
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
            257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
            8193, 12289, 16385, 24577
        };

        private static readonly short[] DistExtra = new short[]
        {
            0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
            7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
        };

        private static readonly short[] LengthOrder = new short[]
        {
            16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
        };

        public static byte[] Decompress(byte[] data)
        {
            if (data == null || data.Length == 0)
            {
                throw new ArgumentException("没有数据可解压");
            }

            int offset = 0;
            if (data.Length > 2 && data[0] == 0x1F && data[1] == 0x8B)
            {
                offset = 10;
            }
            else if (data.Length > 2 && data[0] == 0x78
                && ((data[0] << 8) | data[1]) % 31 == 0)
            {
                offset = 2;
            }

            Worker worker = new Worker(data, offset, data.Length - offset);
            return worker.Run();
        }

        private sealed class Huffman
        {
            public readonly short[] Count = new short[MaxBits + 1];
            public readonly short[] Symbol = new short[320];
        }

        private sealed class Worker
        {
            private readonly byte[] _in;
            private readonly int _inEnd;
            private int _inPos;
            private int _bitBuf;
            private int _bitCnt;

            private byte[] _out = new byte[8192];
            private int _outPos;

            public Worker(byte[] input, int offset, int length)
            {
                _in = input;
                _inPos = offset;
                _inEnd = offset + length;
            }

            public byte[] Run()
            {
                bool last;
                do
                {
                    last = ReadBits(1) != 0;
                    int type = ReadBits(2);

                    if (type == 0)
                    {
                        Stored();
                    }
                    else
                    {
                        Huffman lenCode = new Huffman();
                        Huffman distCode = new Huffman();

                        if (type == 1)
                        {
                            FixedCodes(lenCode, distCode);
                        }
                        else if (type == 2)
                        {
                            DynamicCodes(lenCode, distCode);
                        }
                        else
                        {
                            throw new Exception("非法的块类型");
                        }

                        Compressed(lenCode, distCode);
                    }
                }
                while (!last);

                byte[] result = new byte[_outPos];
                Array.Copy(_out, result, _outPos);
                return result;
            }


            private int ReadBits(int need)
            {
                int value = _bitBuf;
                while (_bitCnt < need)
                {
                    if (_inPos >= _inEnd)
                    {
                        throw new Exception("数据提前结束");
                    }
                    value |= _in[_inPos++] << _bitCnt;
                    _bitCnt += 8;
                }

                _bitBuf = value >> need;
                _bitCnt -= need;
                return value & ((1 << need) - 1);
            }

            private void AlignToByte()
            {
                _bitBuf = 0;
                _bitCnt = 0;
            }


            private void Stored()
            {
                AlignToByte();

                if (_inPos + 4 > _inEnd)
                {
                    throw new Exception("存储块头不完整");
                }

                int len = _in[_inPos] | (_in[_inPos + 1] << 8);
                int nlen = _in[_inPos + 2] | (_in[_inPos + 3] << 8);
                _inPos += 4;

                if ((len ^ 0xFFFF) != nlen)
                {
                    throw new Exception("存储块长度校验失败");
                }
                if (_inPos + len > _inEnd)
                {
                    throw new Exception("存储块数据不完整");
                }

                for (int i = 0; i < len; i++)
                {
                    Put(_in[_inPos++]);
                }
            }

            private static void FixedCodes(Huffman lenCode, Huffman distCode)
            {
                byte[] lengths = new byte[288];
                for (int i = 0; i < 144; i++) lengths[i] = 8;
                for (int i = 144; i < 256; i++) lengths[i] = 9;
                for (int i = 256; i < 280; i++) lengths[i] = 7;
                for (int i = 280; i < 288; i++) lengths[i] = 8;
                Construct(lenCode, lengths, 0, 288);

                byte[] distLengths = new byte[30];
                for (int i = 0; i < 30; i++) distLengths[i] = 5;
                Construct(distCode, distLengths, 0, 30);
            }

            private void DynamicCodes(Huffman lenCode, Huffman distCode)
            {
                byte[] lengths = new byte[320];

                int hlit = ReadBits(5) + 257;
                int hdist = ReadBits(5) + 1;
                int hclen = ReadBits(4) + 4;

                if (hlit > 286 || hdist > 30)
                {
                    throw new Exception("动态码表长度非法");
                }

                for (int i = 0; i < hclen; i++)
                {
                    lengths[LengthOrder[i]] = (byte)ReadBits(3);
                }
                for (int i = hclen; i < 19; i++)
                {
                    lengths[LengthOrder[i]] = 0;
                }

                Huffman lenLenCode = new Huffman();
                Construct(lenLenCode, lengths, 0, 19);

                int index = 0;
                int total = hlit + hdist;
                while (index < total)
                {
                    int symbol = Decode(lenLenCode);
                    if (symbol < 0)
                    {
                        throw new Exception("码长码损坏");
                    }

                    if (symbol < 16)
                    {
                        lengths[index++] = (byte)symbol;
                        continue;
                    }

                    int repeat;
                    int value = 0;
                    if (symbol == 16)
                    {
                        if (index == 0)
                        {
                            throw new Exception("没有可重复的上一个码长");
                        }
                        value = lengths[index - 1];
                        repeat = 3 + ReadBits(2);
                    }
                    else if (symbol == 17)
                    {
                        repeat = 3 + ReadBits(3);
                    }
                    else
                    {
                        repeat = 11 + ReadBits(7);
                    }

                    if (index + repeat > total)
                    {
                        throw new Exception("码长重复次数越界");
                    }
                    while (repeat-- > 0)
                    {
                        lengths[index++] = (byte)value;
                    }
                }

                Construct(lenCode, lengths, 0, hlit);
                Construct(distCode, lengths, hlit, hdist);
            }

            private void Compressed(Huffman lenCode, Huffman distCode)
            {
                while (true)
                {
                    int symbol = Decode(lenCode);
                    if (symbol < 0)
                    {
                        throw new Exception("长度码损坏");
                    }

                    if (symbol < 256)
                    {
                        Put((byte)symbol);
                        continue;
                    }
                    if (symbol == 256)
                    {
                        return;
                    }

                    symbol -= 257;
                    if (symbol >= 29)
                    {
                        throw new Exception("非法的长度码");
                    }

                    int length = LengthBase[symbol] + ReadBits(LengthExtra[symbol]);

                    int distSymbol = Decode(distCode);
                    if (distSymbol < 0 || distSymbol >= 30)
                    {
                        throw new Exception("非法的距离码");
                    }

                    int distance = DistBase[distSymbol] + ReadBits(DistExtra[distSymbol]);
                    if (distance > _outPos)
                    {
                        throw new Exception("回溯距离超出已输出范围");
                    }

                    for (int i = 0; i < length; i++)
                    {
                        Put(_out[_outPos - distance]);
                    }
                }
            }


            private static int Construct(Huffman h, byte[] lengths, int offset, int n)
            {
                for (int i = 0; i <= MaxBits; i++)
                {
                    h.Count[i] = 0;
                }
                for (int s = 0; s < n; s++)
                {
                    int len = lengths[offset + s];
                    if (len > MaxBits)
                    {
                        throw new Exception("码长超出上限");
                    }
                    h.Count[len]++;
                }

                if (h.Count[0] == n)
                {
                    return 0;
                }

                int left = 1;
                for (int len = 1; len <= MaxBits; len++)
                {
                    left <<= 1;
                    left -= h.Count[len];
                    if (left < 0)
                    {
                        throw new Exception("Huffman 码表过度订阅");
                    }
                }

                short[] offs = new short[MaxBits + 2];
                offs[1] = 0;
                for (int len = 1; len < MaxBits; len++)
                {
                    offs[len + 1] = (short)(offs[len] + h.Count[len]);
                }

                for (int s = 0; s < n; s++)
                {
                    int len = lengths[offset + s];
                    if (len != 0)
                    {
                        h.Symbol[offs[len]++] = (short)s;
                    }
                }

                return left;
            }

            private int Decode(Huffman h)
            {
                int code = 0;
                int first = 0;
                int index = 0;

                for (int len = 1; len <= MaxBits; len++)
                {
                    code |= ReadBits(1);
                    int count = h.Count[len];
                    if (code - first < count)
                    {
                        return h.Symbol[index + (code - first)];
                    }
                    index += count;
                    first += count;
                    first <<= 1;
                    code <<= 1;
                }
                return -10;
            }


            private void Put(byte value)
            {
                if (_outPos == _out.Length)
                {
                    if (_out.Length >= MaxOutput)
                    {
                        throw new Exception("解压结果炸了喵");
                    }
                    int size = _out.Length * 2;
                    if (size > MaxOutput)
                    {
                        size = MaxOutput;
                    }
                    byte[] bigger = new byte[size];
                    Array.Copy(_out, bigger, _outPos);
                    _out = bigger;
                }
                _out[_outPos++] = value;
            }
        }
    }
}
