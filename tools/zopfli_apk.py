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


def _paeth(a, b, c):
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def _png_chunks(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not png")
    pos = 8
    chunks = []
    idat = []
    while pos + 12 <= len(data):
        size = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + size]
        chunks.append((kind, payload))
        pos += 12 + size
        if kind == b"IDAT":
            idat.append(payload)
    if not chunks or chunks[0][0] != b"IHDR":
        raise ValueError("no ihdr")
    return chunks, b"".join(idat)


def _png_build(chunks, idat):
    out = bytearray(b"\x89PNG\r\n\x1a\n")
    inserted = False
    for kind, payload in chunks:
        if kind == b"IDAT":
            if inserted:
                continue
            payload = idat
            inserted = True
        out += struct.pack(">I", len(payload)) + kind + payload
        out += struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)
    return bytes(out)


def optimize_png(data):
    try:
        chunks, idat = _png_chunks(data)
        if any(kind == b"acTL" for kind, _ in chunks):
            return data
        ihdr = chunks[0][1]
        if len(ihdr) != 13:
            return data
        width, height, depth, color, _, _, interlace = struct.unpack(
            ">IIBBBBB", ihdr
        )
        if interlace != 0 or color not in (0, 2, 3, 4, 6):
            return data
        channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
        row_size = (width * channels * depth + 7) // 8
        bpp = max(1, (channels * depth + 7) // 8)
        raw = zlib.decompress(idat)
        if len(raw) != (row_size + 1) * height:
            return data

        rows = []
        previous = bytearray(row_size)
        pos = 0
        for _ in range(height):
            kind = raw[pos]
            pos += 1
            row = bytearray(raw[pos:pos + row_size])
            pos += row_size
            if kind == 1:
                for i in range(row_size):
                    row[i] = (row[i] + (row[i - bpp] if i >= bpp else 0)) & 255
            elif kind == 2:
                for i in range(row_size):
                    row[i] = (row[i] + previous[i]) & 255
            elif kind == 3:
                for i in range(row_size):
                    left = row[i - bpp] if i >= bpp else 0
                    row[i] = (row[i] + ((left + previous[i]) >> 1)) & 255
            elif kind == 4:
                for i in range(row_size):
                    left = row[i - bpp] if i >= bpp else 0
                    up_left = previous[i - bpp] if i >= bpp else 0
                    row[i] = (row[i] + _paeth(left, previous[i], up_left)) & 255
            elif kind != 0:
                return data
            rows.append(row)
            previous = row

        filtered = bytearray()
        previous = bytearray(row_size)
        for row in rows:
            best = None
            for kind in range(5):
                out = bytearray(row_size)
                for i in range(row_size):
                    left = row[i - bpp] if i >= bpp else 0
                    up = previous[i]
                    up_left = previous[i - bpp] if i >= bpp else 0
                    if kind == 0:
                        value = row[i]
                    elif kind == 1:
                        value = (row[i] - left) & 255
                    elif kind == 2:
                        value = (row[i] - up) & 255
                    elif kind == 3:
                        value = (row[i] - ((left + up) >> 1)) & 255
                    else:
                        value = (row[i] - _paeth(left, up, up_left)) & 255
                    out[i] = value
                score = sum(min(value, 256 - value) for value in out)
                if best is None or score < best[0]:
                    best = (score, kind, out)
            filtered.append(best[1])
            filtered += best[2]
            previous = row

        candidates = []
        for payload in (bytes(filtered), raw):
            try:
                candidates.append(zopfli_zlib.compress(payload, numiterations=25))
            except Exception:
                pass
        if not candidates:
            return data
        rebuilt = _png_build(chunks, min(candidates, key=len))
        return rebuilt if len(rebuilt) < len(data) else data
    except Exception:
        return data


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
        original = zin.read(info.filename)
        data = original
        if info.filename.lower().endswith(".png"):
            data = optimize_png(data)
        png_changed = data != original
        method = info.compress_type
        comp = data
        compress_it = info.filename != "resources.arsc" and (
            method == 8 or target(info.filename) or png_changed)
        if png_changed and len(data) > 64:
            packed = zopfli_zlib.compress(data, numiterations=25)
            raw = packed[2:-4]
            if len(raw) < info.compress_size:
                method = 8
                comp = raw
            else:
                data = original
                method = info.compress_type
                comp = original_compressed(zin, info)
                if comp is None:
                    comp = deflate(data)
        elif compress_it and len(data) > 64:
            packed = zopfli_zlib.compress(data, numiterations=25)
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
