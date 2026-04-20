"""
Скрипт для загрузки весов CycleGAN с Google Drive.
Запускать один раз: python download_weights.py
"""

import urllib.request
import sys
from pathlib import Path


def download_from_gdrive(file_id: str, dest: Path):
    url = f"https://drive.usercontent.google.com/download?id={file_id}&export=download&confirm=t"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as response, open(dest, "wb") as f:
        total = int(response.headers.get("Content-Length", 0))
        downloaded = 0
        chunk = 1024 * 256
        while True:
            buf = response.read(chunk)
            if not buf:
                break
            f.write(buf)
            downloaded += len(buf)
            if total:
                pct = downloaded / total * 100
                mb = downloaded / 1024 / 1024
                print(f"\r  {pct:.1f}% ({mb:.1f} MB)", end="", flush=True)
    print()


# Google Drive File IDs из официального репо junyanz/pytorch-CycleGAN-and-pix2pix
# https://github.com/junyanz/pytorch-CycleGAN-and-pix2pix/blob/master/scripts/download_cyclegan_model.sh
WEIGHTS = {
    "monet":   "1tWBMxVpMFGPr1R5i7c5lHNkMJkpGfKJz",
    "vangogh": "1bBCaJSbMFxSzCMUhFGUNBQikJb0NRVLG",
    "ukiyoe":  "1fljDmBMM3QJbLDFvMqDzSqDFGBR2ULCK",
    "cezanne": "18tWP5_9GHi0l8UFTLQ2hAMSxeJTLf9ik",
}


def main():
    weights_dir = Path("weights")
    weights_dir.mkdir(exist_ok=True)

    for name, file_id in WEIGHTS.items():
        dest = weights_dir / f"{name}.pth"
        if dest.exists():
            print(f"[SKIP] {name}.pth уже существует ({dest.stat().st_size / 1024 / 1024:.1f} MB)")
            continue

        print(f"[DOWNLOAD] {name}.pth ...")
        try:
            download_from_gdrive(file_id, dest)
            size_mb = dest.stat().st_size / 1024 / 1024
            print(f"[OK] {name}.pth — {size_mb:.1f} MB")
        except Exception as e:
            print(f"[ОШИБКА] {name}: {e}")
            if dest.exists():
                dest.unlink()
            sys.exit(1)

    print("\nВсе веса загружены. Запускай: python app.py")


if __name__ == "__main__":
    main()
