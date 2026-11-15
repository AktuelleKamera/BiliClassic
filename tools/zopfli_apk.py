import argparse
import os
import re
import struct
import zlib
import zipfile

try:
    import zopfli.zlib as zopfli_zlib
except Exception as exc:
    raise SystemExit("zopfli is required: python -m pip install zopfli") from exc


def target(name):
    return (name.startswith("lib/") and name.endswith(".so")) or bool(
        re.match(r"classes\d*\.dex$", name)
    )


def deflate(data, level=9):
    c = zlib.compressobj(level, zlib.DEFLATED, -15)
    return c.compress(data) + c.flush()


def original_compressed(zin, info):
    try:
        fp = zin.fp
        fp.seek(info.header_offset)
        header = fp.read(30)
        if len(header) != 30:
            return None
        name_len, extra_len = struct.unpack("<HH", header[26:30])
        fp.seek(info.header_offset + 30 + name_len + extra_len)
        raw = fp.read(info.compress_size)
        return raw if len(raw) == info.compress_size else None
    except Exception:
        return None


def write_archive(src, dst):
    zin = zipfile.ZipFile(src, "r")
    entries = []
    for info in zin.infolist():
        if info.filename.startswith("META-INF/") and (
            info.filename.endswith((".SF", ".RSA", ".DSA"))
            or info.filename == "META-INF/MANIFEST.MF"
        ):
            continue
        data = zin.read(info.filename)
        method = info.compress_type
        comp = data
        compress_it = info.filename != "resources.arsc" and (
            method == 8 or target(info.filename))
        if compress_it and len(data) > 64:
            packed = zopfli_zlib.compress(data)
            raw = packed[2:-4]
            if len(raw) < info.compress_size or method == 0:
                method = 8
                comp = raw
            else:
                comp = original_compressed(zin, info)
                if comp is None:
                    comp = raw
        elif method == 8:
            comp = original_compressed(zin, info)
            if comp is None:
                comp = deflate(data)
        entries.append((info, method, comp, data))

    total_before = 0
    total_after = 0
    with open(dst, "wb") as out:
        central = []
        offset = 0
        for info, method, comp, data in entries:
            name = info.filename.encode("utf-8")
            flags = 0x800 if any(c > 127 for c in name) else 0
            crc = zlib.crc32(data) & 0xFFFFFFFF
            header = struct.pack(
                "<IHHHHHIIIHH",
                0x04034B50,
                20,
                flags,
                method,
                0,
                0,
                crc,
                len(comp),
                len(data),
                len(name),
                0,
            )
            out.write(header)
            out.write(name)
            out.write(comp)
            total_before += len(data)
            total_after += len(comp)
            central.append(
                struct.pack(
                    "<IHHHHHHIIIHHHHHII",
                    0x02014B50,
                    20,
                    20,
                    flags,
                    method,
                    0,
                    0,
                    crc,
                    len(comp),
                    len(data),
                    len(name),
                    0,
                    0,
                    0,
                    0,
                    info.external_attr,
                    offset,
                )
                + name
            )
            offset += len(header) + len(name) + len(comp)
        central_start = offset
        for item in central:
            out.write(item)
        central_size = out.tell() - central_start
        out.write(
            struct.pack(
                "<IHHHHIIH",
                0x06054B50,
                0,
                0,
                len(entries),
                len(entries),
                central_size,
                central_start,
                0,
            )
        )
    print("zopfli: %s -> %s (%d -> %d bytes compressed data)" % (
        src, dst, total_before, total_after))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    if not os.path.isfile(args.input):
        raise SystemExit("input APK not found: " + args.input)
    write_archive(args.input, args.output)


if __name__ == "__main__":
    main()
