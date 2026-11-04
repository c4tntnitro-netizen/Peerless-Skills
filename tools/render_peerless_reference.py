"""Render the Peerless effect list using Starsector's own bitmap fonts and skill art."""
from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image, ImageDraw


MOD = Path(__file__).resolve().parents[1]
CORE = MOD.parents[1] / "starsector-core"
OUT = MOD / "docs" / "assets"


def fields(line: str) -> dict:
    return {key: value.strip('"') for key, value in
            re.findall(r'(\w+)=("[^"]*"|\S+)', line)}


class BitmapFont:
    def __init__(self, name: str):
        self.glyphs = {}
        self.kerning = {}
        path = CORE / "graphics" / "fonts" / name
        for line in path.read_text(encoding="utf-8").splitlines():
            data = fields(line)
            if line.startswith("common "):
                self.height = int(data["lineHeight"])
            elif line.startswith("page "):
                self.atlas = Image.open(path.parent / data["file"]).convert("RGBA")
            elif line.startswith("char "):
                self.glyphs[int(data["id"])] = {k: int(v) for k, v in data.items()}
            elif line.startswith("kerning "):
                self.kerning[(int(data["first"]), int(data["second"]))] = int(data["amount"])

    def width(self, text: str) -> int:
        total, previous = 0, None
        for char in text:
            code = ord(char)
            if code not in self.glyphs:
                raise ValueError(f"Missing font glyph: {char!r}")
            total += self.kerning.get((previous, code), 0) + self.glyphs[code]["xadvance"]
            previous = code
        return total

    def draw(self, image: Image.Image, text: str, x: int, y: int, color: tuple):
        previous = None
        for char in text:
            code, g = ord(char), self.glyphs[ord(char)]
            x += self.kerning.get((previous, code), 0)
            glyph = self.atlas.crop((g["x"], g["y"], g["x"] + g["width"], g["y"] + g["height"]))
            ink = Image.new("RGBA", glyph.size, color + (255,))
            ink.putalpha(glyph.getchannel("A"))
            image.alpha_composite(ink, (x + g["xoffset"], y + g["yoffset"]))
            x += g["xadvance"]
            previous = code

    def wrap(self, text: str, width: int) -> list[str]:
        sentences = re.split(r'(?<=[.!?])\s+(?=[A-Z])', text)
        if self.width(text) > width and len(sentences) > 1 and all(self.width(s) <= width for s in sentences):
            return sentences
        result, current = [], ""
        for word in text.split():
            candidate = f"{current} {word}" if current else word
            if self.width(candidate) > width and current:
                result.append(current)
                current = word
            else:
                current = candidate
        if current:
            result.append(current)
        assert all(self.width(line) <= width for line in result)
        return result


def gold_icon(asset: dict) -> Image.Image:
    icon = Image.open(MOD / asset["runtime"]).convert("RGBA")
    if asset["metal"] == "bronze":
        # Match the SiC tint in PeerlessSkillIcon; keep the authored details and alpha.
        pixels = icon.load()
        for y in range(icon.height):
            for x in range(icon.width):
                r, g, b, a = pixels[x, y]
                if r > g >= b:
                    pixels[x, y] = (r, b + (r - b) * 3 // 4, b, a)
    assert icon.size == (64, 64)
    return icon


def main():
    catalog = json.loads((MOD / "docs" / "PEERLESS_SKILL_ICONS.json").read_text())
    artwork = {asset["id"]: asset for asset in catalog["assets"]}
    source = (MOD / "src" / "peerless" / "api" / "SkillCatalog.java").read_text()
    entries = re.findall(r'add\("([^"]+)",\s*"([^"]+)",\s*"([^"]*)"\);', source)
    assert len(entries) == 14 and {entry[0] for entry in entries} == set(artwork)
    title = BitmapFont("orbitron20aabold.fnt")
    label = BitmapFont("orbitron12condensed.fnt")
    body = BitmapFont("insignia15LTaa.fnt")
    width, text_x, outer = 1000, 104, 10
    text_width = width - text_x - 22
    rows = []
    for skill_id, name, effect in entries:
        assert title.width(name) <= text_width
        lines = body.wrap(effect, text_width)
        height = max(94, 56 + len(lines) * body.height + 18)
        rows.append((skill_id, name, effect, lines, height))
    height = outer * 2 + sum(row[4] for row in rows)
    image = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    # The faint tactical grid and thin cyan border match the supplied skill tooltips.
    for x in range(0, width, 32):
        draw.line((x, 0, x, height - 1), fill=(2, 5, 6, 255))
    for y in range(0, height, 32):
        draw.line((0, y, width - 1, y), fill=(2, 5, 6, 255))
    draw.rectangle((0, 0, width - 1, height - 1), outline=(64, 132, 146, 255))
    y = outer
    for index, (skill_id, name, effect, lines, row_height) in enumerate(rows):
        if index:
            draw.line((17, y, width - 18, y), fill=(12, 30, 34, 255))
        image.alpha_composite(gold_icon(artwork[skill_id]), (20, y + 12))
        title.draw(image, name, text_x, y + 10, (214, 146, 115))
        label.draw(image, "Peerless", text_x, y + 37, (159, 233, 125))
        for line_index, line in enumerate(lines):
            body.draw(image, line, text_x, y + 56 + line_index * body.height, (255, 210, 0))
        y += row_height
    OUT.mkdir(parents=True, exist_ok=True)
    native = OUT / "peerless_effects.png"
    large = OUT / "peerless_effects_2x.png"
    image.convert("RGB").save(native)
    image.resize((width * 2, height * 2), Image.Resampling.NEAREST).convert("RGB").save(large)
    print(f"Rendered {len(rows)} skills, {width}x{height}; no clipped or missing glyphs.")
    print(native)
    print(large)


if __name__ == "__main__":
    main()
