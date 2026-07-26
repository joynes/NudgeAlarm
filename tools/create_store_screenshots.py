from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "Screenshot_20260401_172031_NudgeAlarm.jpg"
MASCOT = ROOT / "app/src/main/res/drawable/mascot.png"
OUT = ROOT / "store-screenshots"
W, H = 1080, 1920

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REGULAR = "/System/Library/Fonts/Supplemental/Arial.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def gradient(size, top, bottom):
    img = Image.new("RGB", size, top)
    px = img.load()
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        for x in range(size[0]):
            px[x, y] = c
    return img


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0], size[1]), radius=radius, fill=255)
    return mask


def wrap_text(draw, text, text_font, max_width):
    words = text.split()
    lines = []
    current = ""
    for word in words:
        test = word if not current else f"{current} {word}"
        if draw.textbbox((0, 0), test, font=text_font)[2] <= max_width:
            current = test
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_wrapped(draw, text, xy, text_font, fill, max_width, line_gap=10):
    x, y = xy
    for line in wrap_text(draw, text, text_font, max_width):
        draw.text((x, y), line, font=text_font, fill=fill)
        y += text_font.size + line_gap
    return y


def add_phone(canvas, app_crop, box, shadow_color):
    draw = ImageDraw.Draw(canvas)
    x, y, w, h = box
    shadow = Image.new("RGBA", (w + 70, h + 70), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((35, 35, w + 35, h + 35), radius=72, fill=shadow_color)
    shadow = shadow.filter(ImageFilter.GaussianBlur(24))
    canvas.alpha_composite(shadow, (x - 35, y - 20))

    draw.rounded_rectangle((x - 18, y - 18, x + w + 18, y + h + 18), radius=82, fill=(12, 13, 24), outline=(255, 213, 0), width=4)
    draw.rounded_rectangle((x, y, x + w, y + h), radius=60, fill=(20, 22, 40))

    screen = app_crop.resize((w, h), Image.LANCZOS).convert("RGBA")
    mask = rounded_mask((w, h), 46)
    canvas.paste(screen, (x, y), mask)


def make_card(index, title, subtitle, accent, crop, phone_box, bg_top, bg_bottom):
    canvas = gradient((W, H), bg_top, bg_bottom).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Subtle scanline texture to match the app's retro visual language.
    lines = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    line_draw = ImageDraw.Draw(lines)
    for y in range(0, H, 18):
        line_draw.line((0, y, W, y), fill=(255, 255, 255, 12), width=1)
    canvas.alpha_composite(lines)

    mascot = Image.open(MASCOT).convert("RGBA").resize((132, 130), Image.LANCZOS)
    canvas.alpha_composite(mascot, (68, 84))
    draw.text((220, 92), "NUDGEALARM", font=font(38, True), fill=(255, 213, 0))
    draw.text((220, 142), "GAME ON", font=font(28, True), fill=(0, 255, 122))

    title_y = 260
    for line in wrap_text(draw, title.upper(), font(62, True), 950):
        draw.text((64, title_y), line, font=font(62, True), fill=accent)
        title_y += 74
    subtitle_y = max(420, title_y + 36)
    draw_wrapped(draw, subtitle, (68, subtitle_y), font(34), (235, 237, 250), 650, line_gap=12)

    pill_y = max(564, subtitle_y + 126)
    draw.rounded_rectangle((68, pill_y, 534, pill_y + 72), radius=24, fill=(20, 22, 40), outline=accent, width=3)
    draw.text((100, pill_y + 20), "RETRO QUEST REMINDERS", font=font(24, True), fill=(235, 237, 250))

    app = Image.open(SOURCE).convert("RGB")
    app_crop = app.crop(crop)
    add_phone(canvas, app_crop, phone_box, (0, 0, 0, 150))

    draw.rounded_rectangle((68, 1710, 1012, 1810), radius=26, fill=(12, 13, 24, 210), outline=(255, 255, 255, 34), width=2)
    draw.text((100, 1740), f"{index:02d}", font=font(30, True), fill=accent)
    draw.text((170, 1740), "Complete routines before they become noise", font=font(30, True), fill=(235, 237, 250))

    out = canvas.convert("RGB")
    path = OUT / f"phone-{index:02d}-{title.lower().replace(' ', '-')}.png"
    out.save(path, optimize=True)
    return path


def make_contact_sheet(paths):
    thumbs = []
    for path in paths:
        img = Image.open(path).convert("RGB").resize((270, 480), Image.LANCZOS)
        thumbs.append(img)
    sheet = Image.new("RGB", (1080, 520), (10, 11, 20))
    draw = ImageDraw.Draw(sheet)
    for i, img in enumerate(thumbs):
        x = i * 270
        sheet.paste(img, (x, 0))
        draw.rectangle((x, 0, x + 269, 519), outline=(35, 38, 58), width=2)
    path = OUT / "preview-contact-sheet.png"
    sheet.save(path, optimize=True)
    return path


def main():
    OUT.mkdir(exist_ok=True)

    cards = [
        (
            "Turn reminders into quests",
            "A playful alarm system for routines that need persistence, not another quiet notification.",
            (255, 213, 0),
            (0, 88, 1080, 1860),
            (470, 560, 500, 1080),
            (8, 10, 22),
            (36, 23, 67),
        ),
        (
            "Beat active tasks fast",
            "Complete, snooze, or abandon each quest with big, clear controls when it matters.",
            (0, 245, 255),
            (0, 250, 1080, 1780),
            (510, 555, 490, 1085),
            (13, 18, 34),
            (24, 65, 76),
        ),
        (
            "Snooze with intent",
            "Choose quick delays like 15 minutes, 1 hour, 3 hours, or 20 hours without digging through menus.",
            (0, 255, 122),
            (0, 480, 1080, 2020),
            (500, 550, 500, 1090),
            (9, 24, 19),
            (20, 50, 38),
        ),
        (
            "Built for momentum",
            "A high-contrast arcade interface keeps routines visible, scannable, and hard to ignore.",
            (255, 62, 79),
            (0, 88, 1080, 2220),
            (474, 520, 500, 1160),
            (23, 11, 18),
            (56, 28, 42),
        ),
    ]

    paths = []
    for idx, card in enumerate(cards, 1):
        paths.append(make_card(idx, *card))
    paths.append(make_contact_sheet(paths))

    for path in paths:
        img = Image.open(path)
        print(f"{path.relative_to(ROOT)} {img.size[0]}x{img.size[1]} {path.stat().st_size / 1024:.0f}KB")


if __name__ == "__main__":
    main()
