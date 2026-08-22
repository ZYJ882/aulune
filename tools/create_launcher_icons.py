from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "branding" / "chengguang-icon-master.png"
OUTPUTS = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
BACKGROUND = (12, 15, 59, 255)


def make_full_bleed_icon(source: Image.Image) -> Image.Image:
    image = source.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            # The generated asset has intentionally blank white corner areas.
            # Replace only neutral near-white pixels, never the warm-gold painted orb.
            if a > 0 and r > 230 and g > 230 and b > 225 and max(r, g, b) - min(r, g, b) < 26:
                pixels[x, y] = BACKGROUND
    return image


def write_adaptive_foreground(master: Image.Image) -> None:
    size = 432
    inset = 42
    painted_size = size - inset * 2
    foreground = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    painted = master.resize((painted_size, painted_size), Image.Resampling.LANCZOS)
    # The adaptive foreground is a circle with transparent outer space, so launchers
    # can apply their own mask while the artwork keeps a stable, safe visual center.
    alpha = Image.new("L", (painted_size, painted_size), 0)
    ImageDraw.Draw(alpha).ellipse((0, 0, painted_size - 1, painted_size - 1), fill=255)
    painted.putalpha(alpha)
    foreground.alpha_composite(painted, (inset, inset))
    destination = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    foreground.save(destination, "PNG", optimize=True)
    print(f"Wrote {destination} ({size}x{size})")


def main() -> None:
    master = make_full_bleed_icon(Image.open(SOURCE))
    for folder, size in OUTPUTS.items():
        destination = ROOT / "app" / "src" / "main" / "res" / folder / "ic_launcher.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        master.resize((size, size), Image.Resampling.LANCZOS).save(destination, "PNG", optimize=True)
        print(f"Wrote {destination} ({size}x{size})")
    write_adaptive_foreground(master)


if __name__ == "__main__":
    main()
