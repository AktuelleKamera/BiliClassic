using System;
using System.Collections.Generic;
using System.Text;

namespace BiliClassic.Api
{
    public sealed class DashSegment
    {
        public long Offset;
        public long Size;
        public double StartSeconds;
        public double DurationSeconds;
    }

    public sealed class DashSample
    {
        public bool Keyframe;
        public long Timestamp;
        public long Duration;
        public int Offset;

        public long FileOffset;

        public int Size;
    }

    public sealed class DashTrack
    {
        public bool IsVideo;

        public int Timescale;

        public long Duration;

        public string FourCC;

        public string CodecPrivateData = "";

        public int NalLengthSize = 4;

        public int Width;
        public int Height;

        public int Channels;
        public int SampleRate;

        public readonly List<DashSegment> Segments = new List<DashSegment>();

        public long InitEnd;

        public long DefaultSampleDuration;

        public long DefaultSampleSize;

        public long DefaultSampleFlags;

        public uint TrackId;

        public double TickToSeconds
        {
            get { return Timescale > 0 ? 1.0 / Timescale : 0.0; }
        }
    }

    public static class DashMp4
    {
        public static string ToHex(byte[] data, int offset, int count)
        {
            if (data == null || count <= 0)
            {
                return "";
            }

            StringBuilder sb = new StringBuilder(count * 2);
            for (int i = 0; i < count; i++)
            {
                sb.Append(data[offset + i].ToString("X2"));
            }
            return sb.ToString();
        }

        public static bool ParseInit(byte[] data, int length, DashTrack track, out string error)
        {
            error = "";
            if (data == null || length < 16)
            {
                error = "init段太短";
                return false;
            }

            int moov = FindBox(data, 0, length, "moov");
            if (moov < 0)
            {
                error = "没有moov";
                return false;
            }

            int moovEnd = BoxEnd(data, moov);
            track.InitEnd = moovEnd;

            int pos = moov + 8;
            while (pos + 8 <= moovEnd)
            {
                int size = ReadInt32(data, pos);
                if (size < 8 || pos + size > moovEnd)
                {
                    break;
                }

                if (Matches(data, pos + 4, "trak"))
                {
                    ParseTrak(data, pos, pos + size, track);
                }
                else if (Matches(data, pos + 4, "mvex"))
                {
                    ParseMvex(data, pos + 8, pos + size, track);
                }

                pos += size;
            }

            if (track.Timescale <= 0)
            {
                error = "没读到mdhd时间基";
                return false;
            }

            if (track.CodecPrivateData.Length == 0)
            {
                error = "编解码数据为空";
                return false;
            }

            if (track.IsVideo && track.FourCC == null)
            {
                error = "不支持的编码";
                return false;
            }

            int sidx = FindBox(data, 0, length, "sidx");
            if (sidx >= 0)
            {
                ParseSidx(data, sidx, track);
            }

            return true;
        }

        public static bool ParseFragment(byte[] data, int moofStart, int dataEnd,
                                         DashTrack track, long bufferOffset,
                                         List<DashSample> samples)
        {
            int moofEnd = BoxEnd(data, moofStart);
            if (moofEnd <= moofStart || moofEnd > dataEnd)
            {
                return false;
            }

            int pos = moofStart + 8;
            while (pos + 8 <= moofEnd)
            {
                int size = ReadInt32(data, pos);
                if (size < 8 || pos + size > moofEnd)
                {
                    break;
                }

                if (Matches(data, pos + 4, "traf"))
                {
                    ParseTraf(data, pos, pos + size, moofStart, track, bufferOffset, samples);
                }

                pos += size;
            }

            return samples.Count > 0;
        }

        public static int FindBox(byte[] data, int from, int to, string type)
        {
            int pos = from;
            while (pos + 8 <= to)
            {
                int size = ReadInt32(data, pos);
                if (size < 8 || pos + size > to)
                {
                    return -1;
                }

                if (Matches(data, pos + 4, type))
                {
                    return pos;
                }

                pos += size;
            }
            return -1;
        }

        public static int FindMoof(byte[] data, int from, int to)
        {
            return FindBox(data, from, to, "moof");
        }

        public static bool BoxAt(byte[] data, int pos, string type)
        {
            return pos + 8 <= data.Length && Matches(data, pos + 4, type);
        }

        public static long BoxSize(byte[] data, int boxStart)
        {
            long size = ReadUInt32(data, boxStart);
            if (size == 1)
            {
                return 8 + ReadInt64(data, boxStart + 8);
            }
            if (size == 0)
            {
                return 0;
            }
            return size;
        }

        public static int BoxEnd(byte[] data, int boxStart)
        {
            long size = ReadUInt32(data, boxStart);
            if (size == 1)
            {
                return boxStart + 8 + (int)ReadInt64(data, boxStart + 8);
            }
            if (size == 0)
            {
                return data.Length;
            }
            return boxStart + (int)size;
        }

        private static int Payload(byte[] data, int boxStart)
        {
            long size = ReadUInt32(data, boxStart);
            return size == 1 ? boxStart + 16 : boxStart + 8;
        }

        private static void ParseTrak(byte[] data, int trakStart, int trakEnd, DashTrack track)
        {
            int mdia = FindBox(data, trakStart + 8, trakEnd, "mdia");
            if (mdia < 0)
            {
                return;
            }

            int mdiaEnd = BoxEnd(data, mdia);
            int mdhd = FindBox(data, mdia + 8, mdiaEnd, "mdhd");
            if (mdhd >= 0)
            {
                ParseMdhd(data, mdhd, track);
            }

            int hdlr = FindBox(data, mdia + 8, mdiaEnd, "hdlr");
            bool video = true;
            if (hdlr >= 0)
            {
                video = Matches(data, hdlr + 16, "vide");
                if (!video && !Matches(data, hdlr + 16, "soun"))
                {
                    return;
                }
            }

            int minf = FindBox(data, mdia + 8, mdiaEnd, "minf");
            if (minf < 0)
            {
                return;
            }

            int minfEnd = BoxEnd(data, minf);
            int stbl = FindBox(data, minf + 8, minfEnd, "stbl");
            if (stbl < 0)
            {
                return;
            }

            int stblEnd = BoxEnd(data, stbl);
            int stsd = FindBox(data, stbl + 8, stblEnd, "stsd");
            if (stsd < 0)
            {
                return;
            }

            ParseStsd(data, stsd, BoxEnd(data, stsd), video, track);
        }

        private static void ParseMdhd(byte[] data, int mdhd, DashTrack track)
        {
            int p = Payload(data, mdhd);
            int version = data[p];
            if (version == 1)
            {
                track.Timescale = ReadInt32(data, p + 4 + 16);
                track.Duration = ReadInt64(data, p + 4 + 20);
            }
            else
            {
                track.Timescale = ReadInt32(data, p + 4 + 8);
                track.Duration = ReadUInt32(data, p + 4 + 12);
            }
        }

        private static void ParseMvex(byte[] data, int from, int to, DashTrack track)
        {
            int trex = FindBox(data, from, to, "trex");
            if (trex < 0)
            {
                return;
            }

            int p = Payload(data, trex) + 4;
            track.TrackId = (uint)ReadInt32(data, p);
            track.DefaultSampleDuration = ReadUInt32(data, p + 8);
            track.DefaultSampleSize = ReadUInt32(data, p + 12);
            track.DefaultSampleFlags = ReadUInt32(data, p + 16);
        }

        private static void ParseStsd(byte[] data, int stsd, int stsdEnd, bool video, DashTrack track)
        {
            int pos = Payload(data, stsd) + 8;
            while (pos + 8 <= stsdEnd)
            {
                int size = ReadInt32(data, pos);
                if (size < 8 || pos + size > stsdEnd)
                {
                    break;
                }

                if (video && (Matches(data, pos + 4, "avc1") || Matches(data, pos + 4, "avc3")))
                {
                    track.IsVideo = true;
                    track.Width = ReadUInt16(data, pos + 8 + 24);
                    track.Height = ReadUInt16(data, pos + 8 + 26);

                    int child = pos + 8 + 78;
                    int end = pos + size;
                    int avcC = FindBox(data, child, end, "avcC");
                    if (avcC >= 0 && BuildAvcPrivate(data, avcC, track))
                    {
                        track.FourCC = "H264";
                    }
                    return;
                }

                if (!video && Matches(data, pos + 4, "mp4a"))
                {
                    track.IsVideo = false;
                    track.Channels = ReadUInt16(data, pos + 8 + 16);
                    track.SampleRate = (int)(ReadUInt32(data, pos + 8 + 24) >> 16);

                    int child = pos + 8 + 28;
                    int end = pos + size;
                    int esds = FindBox(data, child, end, "esds");
                    if (esds >= 0 && BuildAacPrivate(data, esds, track))
                    {
                        track.FourCC = null;
                    }
                    return;
                }

                pos += size;
            }
        }

        private static bool BuildAvcPrivate(byte[] data, int avcC, DashTrack track)
        {
            int p = Payload(data, avcC);
            int end = BoxEnd(data, avcC);
            if (p + 7 > end)
            {
                return false;
            }

            int numSps = data[p + 5] & 0x1F;
            track.NalLengthSize = (data[p + 4] & 0x03) + 1;
            int pos = p + 6;
            List<byte> blob = new List<byte>(64);

            for (int i = 0; i < numSps && pos + 2 <= end; i++)
            {
                int len = ReadUInt16(data, pos);
                pos += 2;
                if (len <= 0 || pos + len > end)
                {
                    return false;
                }

                AppendStartCode(blob);
                for (int k = 0; k < len; k++)
                {
                    blob.Add(data[pos + k]);
                }
                pos += len;
            }

            if (pos + 1 > end)
            {
                return false;
            }

            int numPps = data[pos];
            pos++;
            for (int i = 0; i < numPps && pos + 2 <= end; i++)
            {
                int len = ReadUInt16(data, pos);
                pos += 2;
                if (len <= 0 || pos + len > end)
                {
                    return false;
                }

                AppendStartCode(blob);
                for (int k = 0; k < len; k++)
                {
                    blob.Add(data[pos + k]);
                }
                pos += len;
            }

            if (blob.Count == 0)
            {
                return false;
            }

            track.CodecPrivateData = ToHex(blob.ToArray(), 0, blob.Count);
            return true;
        }

        private static void AppendStartCode(List<byte> blob)
        {
            blob.Add(0);
            blob.Add(0);
            blob.Add(0);
            blob.Add(1);
        }

        private static bool BuildAacPrivate(byte[] data, int esds, DashTrack track)
        {
            int end = BoxEnd(data, esds);

            int avgBytes = 0;
            byte[] asc = null;

            ScanAacDescriptors(data, Payload(data, esds) + 4, end, ref avgBytes, ref asc);

            if (asc == null || asc.Length == 0 || track.Channels <= 0 || track.SampleRate <= 0)
            {
                return false;
            }

            if (avgBytes <= 0)
            {
                avgBytes = track.SampleRate * track.Channels * 2;
            }

            int blockAlign = track.Channels * 2;
            List<byte> wfx = new List<byte>(32);
            AppendUInt16(wfx, 0xFF);
            AppendUInt16(wfx, track.Channels);
            AppendUInt32(wfx, track.SampleRate);
            AppendUInt32(wfx, avgBytes);
            AppendUInt16(wfx, blockAlign);
            AppendUInt16(wfx, 16);
            AppendUInt16(wfx, asc.Length);
            for (int i = 0; i < asc.Length; i++)
            {
                wfx.Add(asc[i]);
            }

            track.CodecPrivateData = ToHex(wfx.ToArray(), 0, wfx.Count);
            return true;
        }

        private static void ScanAacDescriptors(byte[] data, int p, int end,
                                               ref int avgBytes, ref byte[] asc)
        {
            while (p + 2 <= end && asc == null)
            {
                int tag = data[p];
                int size = 0;
                int lenBytes = 0;
                while (p + 1 + lenBytes < end && lenBytes < 4)
                {
                    int b = data[p + 1 + lenBytes];
                    size = (size << 7) | (b & 0x7F);
                    lenBytes++;
                    if ((b & 0x80) == 0)
                    {
                        break;
                    }
                }

                int body = p + 1 + lenBytes;
                if (size <= 0 || body + size > end)
                {
                    return;
                }

                if (tag == 0x03)
                {
                    ScanAacDescriptors(data, body + 3, body + size, ref avgBytes, ref asc);
                }
                else if (tag == 0x04)
                {
                    if (size >= 13)
                    {
                        avgBytes = (int)(ReadUInt32(data, body + 9) / 8);
                    }
                    ScanAacDescriptors(data, body + 13, body + size, ref avgBytes, ref asc);
                }
                else if (tag == 0x05)
                {
                    asc = new byte[size];
                    Array.Copy(data, body, asc, 0, size);
                    return;
                }

                p = body + size;
            }
        }

        private static void ParseSidx(byte[] data, int sidx, DashTrack track)
        {
            int p = Payload(data, sidx);
            int version = data[p];
            int end = BoxEnd(data, sidx);

            long firstOffset;
            if (version == 0)
            {
                firstOffset = ReadUInt32(data, p + 16);
                p += 20;
            }
            else
            {
                firstOffset = ReadInt64(data, p + 20);
                p += 28;
            }

            if (p + 4 > end)
            {
                return;
            }

            int count = ReadUInt16(data, p + 2);
            p += 4;

            long offset = end + firstOffset;
            double time = 0;
            for (int i = 0; i < count && p + 12 <= end; i++)
            {
                long size = ReadUInt32(data, p) & 0x7FFFFFFF;
                long duration = ReadUInt32(data, p + 4);
                p += 12;

                DashSegment segment = new DashSegment();
                segment.Offset = offset;
                segment.Size = size;
                segment.StartSeconds = time * track.TickToSeconds;
                segment.DurationSeconds = duration * track.TickToSeconds;
                track.Segments.Add(segment);

                offset += size;
                time += duration;
            }
        }

        private static void ParseTraf(byte[] data, int trafStart, int trafEnd, int moofStart,
                                     DashTrack track, long bufferOffset, List<DashSample> samples)
        {
            int tfhd = FindBox(data, trafStart + 8, trafEnd, "tfhd");
            if (tfhd < 0)
            {
                return;
            }

            int flags = ReadInt32(data, Payload(data, tfhd)) & 0xFFFFFF;
            int p = Payload(data, tfhd) + 4;
            long baseOffset = moofStart + bufferOffset;
            long defaultDuration = track.DefaultSampleDuration;
            long defaultSize = track.DefaultSampleSize;
            long defaultFlags = track.DefaultSampleFlags;

            p += 4;
            if ((flags & 0x01) != 0)
            {
                baseOffset = ReadInt64(data, p);
                p += 8;
            }
            if ((flags & 0x02) != 0)
            {
                p += 4;
            }
            if ((flags & 0x08) != 0)
            {
                defaultDuration = ReadUInt32(data, p);
                p += 4;
            }
            if ((flags & 0x10) != 0)
            {
                defaultSize = ReadUInt32(data, p);
                p += 4;
            }
            if ((flags & 0x20) != 0)
            {
                defaultFlags = ReadUInt32(data, p);
            }

            long decodeTime = 0;
            int tfdt = FindBox(data, trafStart + 8, trafEnd, "tfdt");
            if (tfdt >= 0)
            {
                int tp = Payload(data, tfdt);
                decodeTime = data[tp] == 1 ? ReadInt64(data, tp + 4) : ReadUInt32(data, tp + 4);
            }

            int trun = FindBox(data, trafStart + 8, trafEnd, "trun");
            if (trun < 0)
            {
                return;
            }

            int trunFlags = ReadInt32(data, Payload(data, trun)) & 0xFFFFFF;
            int q = Payload(data, trun) + 4;
            int sampleCount = ReadInt32(data, q);
            q += 4;

            if ((trunFlags & 0x01) != 0)
            {
                baseOffset += ReadInt32(data, q);
                q += 4;
            }

            long firstFlags = -1;
            if ((trunFlags & 0x04) != 0)
            {
                firstFlags = ReadUInt32(data, q);
                q += 4;
            }

            int trunEnd = BoxEnd(data, trun);
            long offset = baseOffset - bufferOffset;

            for (int i = 0; i < sampleCount; i++)
            {
                DashSample sample = new DashSample();

                if (i == 0 && firstFlags >= 0)
                {
                    sample.Keyframe = ((firstFlags >> 16) & 0x1) == 0;
                }
                else
                {
                    sample.Keyframe = ((defaultFlags >> 16) & 0x1) == 0;
                }

                long duration = defaultDuration;
                long size = defaultSize;

                if ((trunFlags & 0x100) != 0 && q + 4 <= trunEnd)
                {
                    duration = ReadUInt32(data, q);
                    q += 4;
                }
                if ((trunFlags & 0x200) != 0 && q + 4 <= trunEnd)
                {
                    size = ReadUInt32(data, q);
                    q += 4;
                }
                if ((trunFlags & 0x400) != 0 && q + 4 <= trunEnd)
                {
                    long sampleFlags = ReadUInt32(data, q);
                    q += 4;
                    if (i > 0 || firstFlags < 0)
                    {
                        sample.Keyframe = ((sampleFlags >> 16) & 0x1) == 0;
                    }
                }
                if ((trunFlags & 0x800) != 0 && q + 4 <= trunEnd)
                {
                    q += 4;
                }

                sample.Timestamp = decodeTime;
                sample.Duration = duration > 0 ? duration : 1;
                sample.Offset = (int)offset;
                sample.Size = (int)size;

                samples.Add(sample);

                offset += size;
                decodeTime += duration;
            }
        }

        private static bool Matches(byte[] data, int pos, string type)
        {
            if (pos + 4 > data.Length || type.Length != 4)
            {
                return false;
            }
            return data[pos] == (byte)type[0]
                && data[pos + 1] == (byte)type[1]
                && data[pos + 2] == (byte)type[2]
                && data[pos + 3] == (byte)type[3];
        }

        private static int ReadInt32(byte[] data, int pos)
        {
            return (int)ReadUInt32(data, pos);
        }

        private static long ReadUInt32(byte[] data, int pos)
        {
            if (pos + 4 > data.Length)
            {
                return 0;
            }
            return ((long)data[pos] << 24) | ((long)data[pos + 1] << 16)
                | ((long)data[pos + 2] << 8) | data[pos + 3];
        }

        private static int ReadUInt16(byte[] data, int pos)
        {
            if (pos + 2 > data.Length)
            {
                return 0;
            }
            return (data[pos] << 8) | data[pos + 1];
        }

        private static long ReadInt64(byte[] data, int pos)
        {
            if (pos + 8 > data.Length)
            {
                return 0;
            }
            long value = 0;
            for (int i = 0; i < 8; i++)
            {
                value = (value << 8) | data[pos + i];
            }
            return value;
        }

        private static void AppendUInt16(List<byte> list, int value)
        {
            list.Add((byte)(value & 0xFF));
            list.Add((byte)((value >> 8) & 0xFF));
        }

        private static void AppendUInt32(List<byte> list, long value)
        {
            list.Add((byte)(value & 0xFF));
            list.Add((byte)((value >> 8) & 0xFF));
            list.Add((byte)((value >> 16) & 0xFF));
            list.Add((byte)((value >> 24) & 0xFF));
        }
    }
}
