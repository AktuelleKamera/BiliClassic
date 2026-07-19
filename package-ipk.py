#!/usr/bin/env python3
"""Packages the built app into a proper webOS IPK (ar archive format)."""
import tarfile
import io
import os
import struct
import time
import shutil

PROJECT = os.path.dirname(os.path.abspath(__file__))
DEPLOY = os.path.join(PROJECT, "deploy")
OUTPUT = os.path.join(PROJECT, "bin")
APP_VER = "0.1.0"
IPK_NAME = f"tv.biliclassic.webos_{APP_VER}_all.ipk"


def create_ar_header(name, size):
    """Create a Unix ar archive header (60 bytes) - space-padded fields."""
    name_b = name.ljust(16).encode("ascii")
    ts_b = str(int(time.time())).ljust(12).encode("ascii")
    owner = b"0     "       # 6 bytes, space-padded
    group = b"0     "       # 6 bytes, space-padded
    mode  = b"100644  "     # 8 bytes, space-padded (octal file mode)
    size_b = str(size).ljust(10).encode("ascii")

    header = b"".join([
        name_b, ts_b, owner, group, mode, size_b, b"\x60\x0a"
    ])
    return header


def create_ar_archive(files, output_path):
    """Create an ar archive containing the given files.
    
    files: list of (name, data_bytes) tuples
    """
    with open(output_path, "wb") as f:
        # Write global header
        f.write(b"!<arch>\n")
        for name, data in files:
            header = create_ar_header(name, len(data))
            f.write(header)
            f.write(data)
            # Pad to even byte boundary
            if len(data) % 2 != 0:
                f.write(b"\n")


def make_control_tgz():
    """Create control.tar.gz for the IPK."""
    control = (
        "Package: tv.biliclassic.webos\n"
        f"Version: {APP_VER}\n"
        "Section: unknown\n"
        "Priority: optional\n"
        "Maintainer: BiliClassic\n"
        "Architecture: all\n"
        "Installed-Size: 1\n"
        "Description: BiliClassic for webOS TouchPad\n"
        "webOS-Package-Format-Version: 2\n"
        "webOS-Packager-Version: 3.0.0b15\n"
    )
    postinst = (
        "#!/bin/sh\n"
        "initctl stop BiliProxy 2>/dev/null\n"
        "initctl start BiliProxy 2>/dev/null\n"
        "exit 0\n"
    )
    prerm = (
        "#!/bin/sh\n"
        "initctl stop BiliProxy 2>/dev/null\n"
        "rm -f /etc/event.d/BiliProxy\n"
        "exit 0\n"
    )
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz", format=tarfile.USTAR_FORMAT) as tar:
        info = tarfile.TarInfo(name="./control")
        info.size = len(control)
        tar.addfile(info, io.BytesIO(control.encode("utf-8")))
        info2 = tarfile.TarInfo(name="./postinst")
        info2.size = len(postinst)
        info2.mode = 0o755
        tar.addfile(info2, io.BytesIO(postinst.encode("utf-8")))
        info3 = tarfile.TarInfo(name="./prerm")
        info3.size = len(prerm)
        info3.mode = 0o755
        tar.addfile(info3, io.BytesIO(prerm.encode("utf-8")))
    return buf.getvalue()


def main():
    if not os.path.isdir(DEPLOY):
        print("Error: deploy/ directory not found. Run build first.")
        return

    os.makedirs(OUTPUT, exist_ok=True)
    ipk_path = os.path.join(OUTPUT, IPK_NAME)

    # Ensure static assets are in deploy/assets/
    assets_dir = os.path.join(DEPLOY, "assets")
    os.makedirs(assets_dir, exist_ok=True)
    ic_src = r"I:\Other\BiliClassic\app\src\main\res\drawable\ic_home.png"
    ic_dst = os.path.join(assets_dir, "ic_home.png")
    if os.path.exists(ic_src) and not os.path.exists(ic_dst):
        shutil.copy2(ic_src, ic_dst)
        print(f"  Copied ic_home.png to deploy/assets/")
    # Copy QR code library (always overwrite with latest)
    qrlib_src = os.path.join(PROJECT, "source", "data", "qrcode-lib.js")
    qrlib_dst = os.path.join(assets_dir, "qrcode-lib.js")
    if os.path.exists(qrlib_src):
        shutil.copy2(qrlib_src, qrlib_dst)
        print(f"  Copied qrcode-lib.js to deploy/assets/")

    # Create data.tar.gz - paths relative to / (/usr/palm/applications/APP_ID/)
    APP_DIR_PREFIX = "usr/palm/applications/tv.biliclassic.webos"
    data_buf = io.BytesIO()
    with tarfile.open(fileobj=data_buf, mode="w:gz", format=tarfile.USTAR_FORMAT) as tar:
        # Add parent directory entries (needed by ipkg extractor)
        parent_dirs = ["usr", "usr/palm", "usr/palm/applications"]
        for d in parent_dirs:
            info = tarfile.TarInfo(name=d)
            info.type = tarfile.DIRTYPE
            info.mode = 0o755
            tar.addfile(info)
        # Add app directory
        info = tarfile.TarInfo(name=APP_DIR_PREFIX)
        info.type = tarfile.DIRTYPE
        info.mode = 0o755
        tar.addfile(info)
        # Add appinfo.json from project root if not in deploy
        appinfo_src = os.path.join(PROJECT, "appinfo.json")
        if os.path.exists(appinfo_src):
            arcname = f"{APP_DIR_PREFIX}/appinfo.json"
            tar.add(appinfo_src, arcname=arcname)
        # Add proxy.js (Node.js API proxy for CORS bypass)
        proxy_src = os.path.join(PROJECT, "proxy.js")
        if os.path.exists(proxy_src):
            arcname = f"{APP_DIR_PREFIX}/proxy.js"
            tar.add(proxy_src, arcname=arcname)
        # Add upstart config for auto-start at boot
        conf_src = os.path.join(PROJECT, "source", "data", "biliproxy.conf")
        if os.path.exists(conf_src):
            # Add directory entry for etc/event.d
            info = tarfile.TarInfo(name="etc")
            info.type = tarfile.DIRTYPE; info.mode = 0o755; tar.addfile(info)
            info = tarfile.TarInfo(name="etc/event.d")
            info.type = tarfile.DIRTYPE; info.mode = 0o755; tar.addfile(info)
            tar.add(conf_src, arcname="etc/event.d/BiliProxy")
        # Add all files from deploy directory
        added_dirs = set()
        for root, dirs, files in os.walk(DEPLOY):
            rel_dir = os.path.relpath(root, DEPLOY).replace("\\", "/")
            # Build the full path for this directory
            if rel_dir == ".":
                full_dir = APP_DIR_PREFIX
            else:
                full_dir = f"{APP_DIR_PREFIX}/{rel_dir}"
            # Add directory entry
            if full_dir not in added_dirs:
                info = tarfile.TarInfo(name=full_dir)
                info.type = tarfile.DIRTYPE
                info.mode = 0o755
                tar.addfile(info)
                added_dirs.add(full_dir)
            # Add files
            for f in files:
                full_path = os.path.join(root, f)
                rel = os.path.relpath(full_path, DEPLOY).replace("\\", "/")
                arcname = f"{APP_DIR_PREFIX}/{rel}"
                tar.add(full_path, arcname=arcname)

    data_tar_gz = data_buf.getvalue()

    # Create control.tar.gz
    control_tar_gz = make_control_tgz()

    # Create the ar archive (the actual IPK)
    create_ar_archive(
        [
            ("debian-binary", b"2.0\n"),
            ("control.tar.gz", control_tar_gz),
            ("data.tar.gz", data_tar_gz),
        ],
        ipk_path,
    )

    print(f"IPK created: {ipk_path}")
    # Verify
    with open(ipk_path, "rb") as f:
        magic = f.read(8)
    print(f"  Format: {repr(magic)}")


if __name__ == "__main__":
    main()
