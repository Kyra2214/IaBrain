from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
xml_files = [
    ROOT / "app/src/main/res/layout/activity_welcome.xml",
    ROOT / "app/src/main/res/drawable/bg_loading.xml",
    ROOT / "app/src/main/res/values/colors.xml",
    ROOT / "app/src/main/res/values-night/colors.xml",
    ROOT / "app/src/main/res/values/strings.xml",
]
for path in xml_files:
    ET.parse(path)
    print(f"XML OK: {path.relative_to(ROOT)}")

required_images = {
    ROOT / "app/src/main/res/drawable-nodpi/ic_logo.png": (1024, 1024),
    ROOT / "app/src/main/res/mipmap-mdpi/ic_launcher.png": (48, 48),
    ROOT / "app/src/main/res/mipmap-hdpi/ic_launcher.png": (72, 72),
    ROOT / "app/src/main/res/mipmap-xhdpi/ic_launcher.png": (96, 96),
    ROOT / "app/src/main/res/mipmap-xxhdpi/ic_launcher.png": (144, 144),
    ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png": (192, 192),
    ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png": (432, 432),
}
for path, expected in required_images.items():
    image = Image.open(path)
    if image.size != expected:
        raise SystemExit(f"Unexpected image size for {path}: {image.size}, expected {expected}")
    if image.mode != "RGBA":
        raise SystemExit(f"Expected RGBA image for {path}, got {image.mode}")
    print(f"IMAGE OK: {path.relative_to(ROOT)} {image.size} {image.mode}")

print("Branding validation passed")
