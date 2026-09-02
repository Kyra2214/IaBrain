from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "brand" / "iabrain-symbol.png"
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

symbol = Image.open(SOURCE).convert("RGBA")

# Keep a high-resolution transparent copy for the welcome/loading UI.
ui_symbol = ImageOps.contain(symbol, (1024, 1024), Image.Resampling.LANCZOS)
canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
canvas.alpha_composite(ui_symbol, ((1024 - ui_symbol.width) // 2, (1024 - ui_symbol.height) // 2))
canvas.save(DRAWABLE / "ic_logo.png", optimize=True)

# Build legacy launcher bitmaps with the existing rounded navy tile.
launcher_sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in launcher_sizes.items():
    tile = Image.new("RGBA", (size, size), (11, 11, 25, 255))
    radius = max(1, round(size * 0.28))
    mask = Image.new("L", (size, size), 0)
    mask = ImageOps.fit(mask, (size, size))
    # Rounded mask drawn with Pillow's built-in rounded rectangle.
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    tile.putalpha(mask)
    fitted = ImageOps.contain(symbol, (round(size * 0.76), round(size * 0.76)), Image.Resampling.LANCZOS)
    tile.alpha_composite(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2))
    out_dir = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
    tile.save(out_dir / "ic_launcher.png", optimize=True)
    tile.save(out_dir / "ic_launcher_round.png", optimize=True)

# Adaptive-icon foreground: safe margin avoids clipping under Android masks.
foreground_size = 432
foreground = Image.new("RGBA", (foreground_size, foreground_size), (0, 0, 0, 0))
fitted = ImageOps.contain(symbol, (round(foreground_size * 0.74), round(foreground_size * 0.74)), Image.Resampling.LANCZOS)
foreground.alpha_composite(fitted, ((foreground_size - fitted.width) // 2, (foreground_size - fitted.height) // 2))
foreground.save(ROOT / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher_foreground.png", optimize=True)
