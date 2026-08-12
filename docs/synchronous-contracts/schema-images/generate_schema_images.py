from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
CATALOG = OUT.parent / "moneybags-synchronous-contract.json"

NAVY = "#0B2545"
INK = "#172B4D"
MUTED = "#556987"
GRID = "#9AABC0"
PALETTE = ["#2E7D5B", "#2E74B5", "#178A94", "#7662A8", "#A47700"]


def font(name: str, size: int):
    for candidate in (name, "arial.ttf"):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


F_TITLE = font("arialbd.ttf", 40)
F_SUBTITLE = font("arial.ttf", 18)
F_TABLE = font("arialbd.ttf", 18)
F_FIELD = font("arial.ttf", 14)
F_KEY = font("arialbd.ttf", 13)
F_SMALL = font("arial.ttf", 12)
F_FOOT = font("arial.ttf", 13)


def normalize_type(value: str) -> str:
    return (value.replace("TIMESTAMP TZ", "TIMESTAMP WITH TZ")
            .replace("TIMESTAMP(6) WITH TIME ZONE", "TIMESTAMP WITH TZ")
            .replace(" NULL", ""))


TYPE_PATTERN = re.compile(r"\s+(VARCHAR2|NUMBER|CHAR|DATE|TIMESTAMP|CLOB|BLOB)\b")


def parse_fields(columns: str, relationships: str):
    foreign_names = set(re.findall(r"FK\s+([A-Z_]+)\s*->", relationships))
    fields = []
    for part in columns.split(";"):
        part = part.strip()
        if not part:
            continue
        key = ""
        if part.endswith(" PK"):
            key, part = "PK", part[:-3]
        type_match = TYPE_PATTERN.search(part)
        if not type_match:
            continue
        names = part[:type_match.start()].strip()
        dtype = normalize_type(part[type_match.start():].strip())
        for name in names.split("/"):
            name = name.strip()
            if not name:
                continue
            label = key
            if name in foreign_names:
                label = "PK/FK" if label == "PK" else "FK"
            fields.append((label, name, dtype))
    return fields


def wrap_text(draw, text: str, width: int, used_font):
    words = text.split()
    lines, current = [], ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if draw.textbbox((0, 0), candidate, font=used_font)[2] <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines or [""]


def rounded(draw, rectangle, fill, outline, radius=10, width=2):
    draw.rounded_rectangle(rectangle, radius=radius, fill=fill, outline=outline, width=width)


def draw_table(draw, box, title, fields, color, note):
    x, y, width, height = box
    header_h, row_h = 36, 22
    rounded(draw, (x, y, x + width, y + height), "white", color, width=3)
    draw.rounded_rectangle((x, y, x + width, y + header_h), radius=10, fill=color)
    draw.rectangle((x, y + 20, x + width, y + header_h), fill=color)
    draw.text((x + 12, y + 9), title, font=F_TABLE, fill="white")
    yy = y + header_h + 8
    for key, field_name, dtype in fields:
        if key:
            key_color = "#8A5900" if "UQ" in key else "#9A6700"
            draw.text((x + 11, yy + 3), key, font=F_KEY, fill=key_color)
        draw.text((x + 56, yy + 3), field_name, font=F_FIELD, fill=INK)
        type_font = F_SMALL if len(dtype) > 19 else F_FIELD
        type_width = draw.textbbox((0, 0), dtype, font=type_font)[2]
        draw.text((x + width - 11 - type_width, yy + 4), dtype, font=type_font, fill=MUTED)
        yy += row_h
    draw.line((x + 10, yy + 2, x + width - 10, yy + 2), fill="#D8E0EA", width=1)
    note_lines = wrap_text(draw, note, width - 22, F_SMALL)[:2]
    for line in note_lines:
        yy += 15
        draw.text((x + 11, yy), line, font=F_SMALL, fill=MUTED)


def line_with_arrow(draw, start, end, color=GRID):
    x1, y1 = start
    x2, y2 = end
    mid = (x1 + x2) // 2
    draw.line((x1, y1, mid, y1, mid, y2, x2, y2), fill=color, width=2)
    angle = 7
    draw.polygon([(x2, y2), (x2 - angle, y2 - angle // 2), (x2 - angle, y2 + angle // 2)], fill=color)


def table_relationships(service, boxes):
    links = []
    service_tables = set(service["tables"])
    for table in service["tables"]:
        relationship_text = service["_blueprints"][table]["keysAndRelationships"]
        for target in re.findall(r"->\s*([A-Z_]+)", relationship_text):
            if target in service_tables and target != table:
                links.append((table, target))
    return links


def service_image(service, blueprints):
    image = Image.new("RGB", (1, 1), "white")
    measure = ImageDraw.Draw(image)
    tables = []
    for table_name in service["tables"]:
        blueprint = blueprints[table_name]
        fields = parse_fields(blueprint["columns"], blueprint["keysAndRelationships"])
        rows = max(len(fields), 3)
        height = 36 + 8 + rows * 22 + 43
        tables.append((table_name, blueprint, fields, height))

    count = len(tables)
    cols = 3 if count <= 9 else 4
    table_w, gap, margin, top = 820, 55, 65, 160
    row_heights = []
    for start in range(0, count, cols):
        row_heights.append(max(item[3] for item in tables[start:start + cols]))
    width = margin * 2 + cols * table_w + (cols - 1) * gap
    height = top + sum(row_heights) + gap * (len(row_heights) - 1) + 260
    canvas = Image.new("RGB", (width, max(height, 1100)), "white")
    draw = ImageDraw.Draw(canvas)
    draw.text((width // 2, 34), f"{service['name'].upper()} - ORACLE DATABASE SCHEMA", font=F_TITLE, fill=NAVY, anchor="ma")
    subtitle = f"{service['oracleSchema']} | Documentation blueprint | local foreign keys only"
    draw.text((width // 2, 88), subtitle, font=F_SUBTITLE, fill=MUTED, anchor="ma")

    boxes = {}
    row, cursor = 0, 0
    y = top
    for index, (table_name, blueprint, fields, box_h) in enumerate(tables):
        if index and index % cols == 0:
            y += row_heights[row] + gap
            row += 1
        col = index % cols
        x = margin + col * (table_w + gap)
        boxes[table_name] = (x, y, table_w, box_h)

    service["_blueprints"] = blueprints
    for child, parent in table_relationships(service, boxes):
        child_box, parent_box = boxes[child], boxes[parent]
        if child_box[0] <= parent_box[0]:
            start = (child_box[0] + child_box[2], child_box[1] + child_box[3] // 2)
            end = (parent_box[0], parent_box[1] + parent_box[3] // 2)
        else:
            start = (child_box[0], child_box[1] + child_box[3] // 2)
            end = (parent_box[0] + parent_box[2], parent_box[1] + parent_box[3] // 2)
        line_with_arrow(draw, start, end)

    for index, (table_name, blueprint, fields, box_h) in enumerate(tables):
        color = "#2E7D5B" if index == 0 else PALETTE[index % len(PALETTE)]
        draw_table(draw, boxes[table_name], table_name, fields, color, blueprint["keysAndRelationships"])

    footer_y = height - 190
    rounded(draw, (margin, footer_y, width - margin, footer_y + 115), "#F5F8FC", "#B6C7D8", width=2)
    draw.text((margin + 18, footer_y + 18), "LEGEND", font=F_TABLE, fill=NAVY)
    draw.text((margin + 18, footer_y + 53), "PK = primary key   FK = local foreign key   UQ = unique constraint", font=F_FOOT, fill=INK)
    draw.text((margin + 18, footer_y + 78), "Solid arrows represent only local schema relationships. IDs from other services are opaque references, not cross-schema foreign keys.", font=F_FOOT, fill=MUTED)
    draw.text((margin, height - 42), f"Tables: {len(tables)} | Service port: {service['port']} | Canonical source: moneybags-synchronous-contract.json", font=F_FOOT, fill=MUTED)

    filename = f"{service['port']}-{service['id']}-oracle-schema.png"
    path = OUT / filename
    canvas.save(path, optimize=True)
    del service["_blueprints"]
    return path


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    blueprints = catalog["databaseTableBlueprints"]
    images = [service_image(service, blueprints) for service in catalog["services"]]
    lines = ["# Service Oracle schema images", "", "One high-resolution schema diagram per Moneybags business service.", ""]
    lines.extend(f"- [{image.stem}]({image.name})" for image in images)
    (OUT / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(str(image) for image in images))


if __name__ == "__main__":
    main()
