import argparse
import json
import math
from pathlib import Path
import zlib

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIRECTORY = ROOT / "src/main/resources/assets/pit12/cosmetic"
TEXTURE_DIRECTORY = ROOT / "src/main/resources/assets/pit12/textures/cosmetic"
PREVIEW_DIRECTORY = ROOT / "docs/preview"

GEOMETRY = {
    "width": 3.0,
    "height": 5.0,
    "thickness": 3.0,
    "segments": 3,
    "taper": 0.62,
    "splay": 0.26,
    "tilt": 0.12,
    "lean": 0.3,
    "pivot": (2.6, -8.0, -1.0),
    "texture_size": 64,
}

PRESETS = {
    "plain": {
        "fur": (255, 255, 255),
        "shadow": (176, 176, 176),
        "inner": (235, 166, 186),
        "pattern": "plain",
    },
    "tabby": {
        "fur": (255, 255, 255),
        "shadow": (150, 150, 150),
        "inner": (235, 166, 186),
        "pattern": "tabby",
    },
    "spots": {
        "fur": (255, 255, 255),
        "shadow": (162, 162, 162),
        "inner": (235, 166, 186),
        "pattern": "spots",
    },
}

LIGHT = (0.35, -0.78, 0.52)
CAMERA = (0.6, 0.28)
PREVIEW_SIZE = 480


def number(value):
    return round(float(value), 4)


def vec(values):
    return [number(value) for value in values]


def split_heights(height, segments, taper):
    weights = [taper ** index for index in range(segments)]
    total = sum(weights)
    heights = []
    remaining = int(round(height))
    for index in range(segments):
        if index == segments - 1:
            heights.append(max(1, remaining))
            break
        share = max(1, int(round(height * weights[index] / total)))
        share = min(share, remaining - (segments - index - 1))
        heights.append(share)
        remaining -= share
    return heights


def ear_parts(geometry):
    count = geometry["segments"]
    taper = geometry["taper"]
    width = geometry["width"]
    height = geometry["height"]
    thickness = geometry["thickness"]
    widths = [max(1, int(round(width * taper ** index))) for index in range(count)]
    depths = [max(1, int(round(thickness * taper ** index))) for index in range(count)]
    heights = split_heights(height, count, taper)
    parts = []
    for side in (1, -1):
        pivot = (side * geometry["pivot"][0], geometry["pivot"][1], geometry["pivot"][2])
        rotation = (geometry["tilt"], 0.0, side * geometry["splay"])
        offset = 0
        for index, (box_width, box_height, box_depth) in enumerate(zip(widths, heights, depths)):
            lean = side * geometry["lean"] * index
            origin = (-box_width / 2.0 + lean, offset - box_height, -box_depth / 2.0)
            parts.append({
                "rotationPoint": vec(pivot),
                "rotation": vec(rotation),
                "origin": vec(origin),
                "size": [box_width, box_height, box_depth],
                "inner": False,
            })
            if box_width >= 2:
                parts.append({
                    "rotationPoint": vec(pivot),
                    "rotation": vec(rotation),
                    "origin": vec((origin[0] + (box_width - 1) / 2.0, origin[1],
                                   origin[2] - 0.05)),
                    "size": [1, box_height, 1],
                    "inner": True,
                })
            offset -= box_height
    return parts


def pack(parts, size):
    x = 1
    y = 1
    row_height = 0
    for part in parts:
        box_width, box_height, box_depth = part["size"]
        region_width = 2 * (box_width + box_depth)
        region_height = box_depth + 2 * box_height
        if x + region_width + 1 > size:
            x = 1
            y += row_height + 1
            row_height = 0
        if y + region_height + 1 > size:
            raise ValueError("texture {} is too small for the model".format(size))
        part["uv"] = [x, y]
        part["region"] = [region_width, region_height]
        x += region_width + 1
        row_height = max(row_height, region_height)


def noise(x, y):
    value = (x * 73856093) ^ (y * 19349663)
    return (value & 0xFF) / 255.0


def mix(first, second, amount):
    return tuple(int(round(first[i] + (second[i] - first[i]) * amount)) for i in range(3))


def paint(parts, preset, size):
    pixels = bytearray(size * size * 4)
    for part in parts:
        u, v = part["uv"]
        region_width, region_height = part["region"]
        for y in range(v, v + region_height):
            for x in range(u, u + region_width):
                if part["inner"]:
                    glow = 1.0 - 0.35 * ((y - v) / max(1, region_height - 1))
                    color = tuple(int(round(channel * glow)) for channel in preset["inner"])
                else:
                    color = preset["fur"]
                    pattern = preset["pattern"]
                    if pattern == "tabby" and (y - v) % 4 == 0:
                        color = mix(color, preset["shadow"], 0.55)
                    elif pattern == "spots" and noise(x // 2, y // 2) > 0.82:
                        color = mix(color, preset["shadow"], 0.7)
                    else:
                        color = mix(color, preset["shadow"], noise(x, y) * 0.08)
                index = (y * size + x) * 4
                pixels[index:index + 4] = bytes((color[0], color[1], color[2], 255))
    return pixels


def png_bytes(size, pixels):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        start = y * size * 4
        raw.extend(pixels[start:start + size * 4])

    def chunk(tag, data):
        payload = tag + data
        return len(data).to_bytes(4, "big") + payload + zlib.crc32(payload).to_bytes(4, "big")

    body = b"\x89PNG\r\n\x1a\n"
    body += chunk(b"IHDR", size.to_bytes(4, "big") + size.to_bytes(4, "big") + bytes((8, 6, 0, 0, 0)))
    body += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    body += chunk(b"IEND", b"")
    return body


def rotation_matrix(rotation):
    x, y, z = rotation
    cos_x, sin_x = math.cos(x), math.sin(x)
    cos_y, sin_y = math.cos(y), math.sin(y)
    cos_z, sin_z = math.cos(z), math.sin(z)
    return (
        (cos_z * cos_y, cos_z * sin_y * sin_x - sin_z * cos_x, cos_z * sin_y * cos_x + sin_z * sin_x),
        (sin_z * cos_y, sin_z * sin_y * sin_x + cos_z * cos_x, sin_z * sin_y * cos_x - cos_z * sin_x),
        (-sin_y, cos_y * sin_x, cos_y * cos_x),
    )


def apply(matrix, point):
    return tuple(sum(matrix[row][column] * point[column] for column in range(3)) for row in range(3))


def transform(point, part):
    rotated = apply(rotation_matrix(part["rotation"]), point)
    pivot = part["rotationPoint"]
    return (rotated[0] + pivot[0], rotated[1] + pivot[1], rotated[2] + pivot[2])


def box_corners(part):
    x, y, z = part["origin"]
    width, height, depth = part["size"]
    corners = []
    for corner_x in (x, x + width):
        for corner_y in (y, y + height):
            for corner_z in (z, z + depth):
                corners.append(transform((corner_x, corner_y, corner_z), part))
    return corners


HEAD_EDGES = (
    (0, 1), (0, 2), (1, 3), (2, 3), (4, 5), (4, 6), (5, 7), (6, 7),
    (0, 4), (1, 5), (2, 6), (3, 7),
)

FACE_RECTS = (
    lambda u, v, w, h, d: (u + d + w, v + d, d, h),
    lambda u, v, w, h, d: (u, v + d, d, h),
    lambda u, v, w, h, d: (u + d, v, w, d),
    lambda u, v, w, h, d: (u + d + w, v, w, d),
    lambda u, v, w, h, d: (u + d, v + d, w, h),
    lambda u, v, w, h, d: (u + d + w + d, v + d, w, h),
)

FACES = (
    (0, 1, 3, 2),
    (4, 6, 7, 5),
    (0, 4, 5, 1),
    (2, 3, 7, 6),
    (0, 2, 6, 4),
    (1, 5, 7, 3),
)


def camera_basis():
    yaw, pitch = CAMERA
    right = (math.cos(yaw), 0.0, -math.sin(yaw))
    up = (math.sin(yaw) * math.sin(pitch), math.cos(pitch), math.cos(yaw) * math.sin(pitch))
    forward = (math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
    return right, up, forward


def display(point):
    return (point[0], -point[1], point[2])


def project(point, right, up):
    shown = display(point)
    return (sum(shown[i] * right[i] for i in range(3)), -sum(shown[i] * up[i] for i in range(3)))


def preview_faces(parts, preset, pixels, texture_size):
    faces = []
    for part in parts:
        corners = box_corners(part)
        color = (235, 166, 186) if part.get("inner") else preset["fur"]
        center = tuple(sum(corner[i] for corner in corners) / 8.0 for i in range(3))
        for index, face in enumerate(FACES):
            points = [corners[index] for index in face]
            normal = face_normal(points)
            face_center = tuple(sum(point[i] for point in points) / 4.0 for i in range(3))
            outward = sum((face_center[i] - center[i]) * normal[i] for i in range(3))
            if outward < 0:
                normal = tuple(-value for value in normal)
            rect = FACE_RECTS[index](*part["uv"], *part["size"])
            faces.append((points, normal, average(pixels, texture_size, rect) or color))
    return faces


def average(pixels, size, rect):
    u, v, width, height = rect
    red = green = blue = 0
    count = 0
    for y in range(v, min(size, v + max(1, height))):
        for x in range(u, min(size, u + max(1, width))):
            offset = (y * size + x) * 4
            red += pixels[offset]
            green += pixels[offset + 1]
            blue += pixels[offset + 2]
            count += 1
    if count == 0:
        return None
    return (red // count, green // count, blue // count)


def face_normal(points):
    first = [points[1][i] - points[0][i] for i in range(3)]
    second = [points[2][i] - points[0][i] for i in range(3)]
    normal = (
        first[1] * second[2] - first[2] * second[1],
        first[2] * second[0] - first[0] * second[2],
        first[0] * second[1] - first[1] * second[0],
    )
    length = sum(value * value for value in normal) ** 0.5
    if length == 0:
        return (0.0, 0.0, 0.0)
    return tuple(value / length for value in normal)


def face_shade(normal):
    dot = sum(normal[i] * LIGHT[i] for i in range(3))
    return 0.45 + 0.55 * max(0.0, dot)


def screen_point(point, right, up, scale, offset_x, offset_y):
    shown = project(point, right, up)
    return (shown[0] * scale + offset_x, shown[1] * scale + offset_y)


def draw_line(image, size, start, end, color):
    x0, y0 = int(round(start[0])), int(round(start[1]))
    x1, y1 = int(round(end[0])), int(round(end[1]))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    step_x = 1 if x0 < x1 else -1
    step_y = 1 if y0 < y1 else -1
    error = dx + dy
    while True:
        if 0 <= x0 < size and 0 <= y0 < size:
            offset = (y0 * size + x0) * 4
            image[offset:offset + 4] = bytes((color[0], color[1], color[2], 255))
        if x0 == x1 and y0 == y1:
            break
        doubled = 2 * error
        if doubled >= dy:
            error += dy
            x0 += step_x
        if doubled <= dx:
            error += dx
            y0 += step_y


def fill_polygon(image, size, points, color):
    minimum_y = max(0, int(min(point[1] for point in points)))
    maximum_y = min(size - 1, int(max(point[1] for point in points)))
    for y in range(minimum_y, maximum_y + 1):
        crossings = []
        for index in range(len(points)):
            first = points[index]
            second = points[(index + 1) % len(points)]
            if first[1] == second[1]:
                continue
            if min(first[1], second[1]) <= y + 0.5 < max(first[1], second[1]):
                ratio = (y + 0.5 - first[1]) / (second[1] - first[1])
                crossings.append(first[0] + ratio * (second[0] - first[0]))
        crossings.sort()
        for index in range(0, len(crossings) - 1, 2):
            start = max(0, int(round(crossings[index])))
            end = min(size - 1, int(round(crossings[index + 1])))
            for x in range(start, end + 1):
                offset = (y * size + x) * 4
                image[offset:offset + 4] = bytes((color[0], color[1], color[2], 255))


def preview_bytes(parts, preset, size, pixels, texture_size):
    right, up, forward = camera_basis()
    faces = preview_faces(parts, preset, pixels, texture_size)
    projected = []
    for points, normal, color in faces:
        screen = [project(point, right, up) for point in points]
        depth = sum(sum(display(point)[i] * forward[i] for i in range(3)) for point in points) / len(points)
        projected.append((screen, depth, normal, color))
    minimum_x = min(point[0] for screen, _, _, _ in projected for point in screen)
    maximum_x = max(point[0] for screen, _, _, _ in projected for point in screen)
    minimum_y = min(point[1] for screen, _, _, _ in projected for point in screen)
    maximum_y = max(point[1] for screen, _, _, _ in projected for point in screen)
    span = max(maximum_x - minimum_x, maximum_y - minimum_y)
    scale = (size * 0.86) / span if span else 1.0
    offset_x = size / 2.0 - (minimum_x + maximum_x) / 2.0 * scale
    offset_y = size / 2.0 - (minimum_y + maximum_y) / 2.0 * scale
    ordered = []
    for screen, depth, normal, color in projected:
        placed = [(point[0] * scale + offset_x, point[1] * scale + offset_y) for point in screen]
        ordered.append((depth, placed, normal, color))
    ordered.sort(key=lambda face: -face[0])
    image = bytearray(size * size * 4)
    head = {
        "rotationPoint": [0.0, 0.0, 0.0],
        "rotation": [0.0, 0.0, 0.0],
        "origin": [-4.0, -8.0, -4.0],
        "size": [8, 8, 8],
    }
    corners = [transform(corner, head) for corner in box_corners(head)]
    for first, second in HEAD_EDGES:
        draw_line(image, size,
                  (screen_point(corners[first], right, up, scale, offset_x, offset_y)),
                  (screen_point(corners[second], right, up, scale, offset_x, offset_y)),
                  (150, 150, 150))
    for _, placed, normal, color in ordered:
        shade = face_shade(normal)
        drawn = tuple(int(round(channel * shade)) for channel in color)
        fill_polygon(image, size, placed, drawn)
    return png_bytes(size, image)


def model_payload(parts, geometry):
    return {
        "textureSize": [geometry["texture_size"], geometry["texture_size"]],
        "parts": [
            {
                "rotationPoint": part["rotationPoint"],
                "rotation": part["rotation"],
                "origin": part["origin"],
                "size": part["size"],
                "uv": part["uv"],
                "inner": part["inner"],
            }
            for part in parts
        ],
    }


def read(path):
    return path.read_bytes() if path.exists() else None


def write_changed(path, content):
    if read(path) == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return True


def generate(preset_name):
    preset = PRESETS[preset_name]
    parts = ear_parts(GEOMETRY)
    pack(parts, GEOMETRY["texture_size"])
    pixels = paint(parts, preset, GEOMETRY["texture_size"])
    texture = png_bytes(GEOMETRY["texture_size"], pixels)
    model = (json.dumps(model_payload(parts, GEOMETRY), indent=2) + "\n").encode("utf-8")
    preview = preview_bytes(parts, preset, PREVIEW_SIZE, pixels, GEOMETRY["texture_size"])
    return model, texture, preview


def main():
    parser = argparse.ArgumentParser(description="Generates the cat ear model and texture.")
    parser.add_argument("--preset", action="append", choices=sorted(PRESETS), help="preset to generate")
    parser.add_argument("--check", action="store_true", help="verify the committed assets instead of writing")
    parser.add_argument("--preview", action="store_true", help="only write the preview images")
    arguments = parser.parse_args()
    presets = arguments.preset or sorted(PRESETS)
    changed = []
    stale = []
    for name in presets:
        model, texture, preview = generate(name)
        targets = [
            (MODEL_DIRECTORY / (name + ".json"), model),
            (TEXTURE_DIRECTORY / (name + ".png"), texture),
            (PREVIEW_DIRECTORY / ("cosmetic-" + name + ".png"), preview),
        ]
        for path, content in targets:
            if arguments.check:
                if read(path) != content:
                    stale.append(str(path.relative_to(ROOT)))
            elif arguments.preview:
                if path.parent == PREVIEW_DIRECTORY and write_changed(path, content):
                    changed.append(str(path.relative_to(ROOT)))
            elif write_changed(path, content):
                changed.append(str(path.relative_to(ROOT)))
    if arguments.check:
        if stale:
            print("Outdated assets:\n  " + "\n  ".join(stale))
            return 1
        print("Assets are up to date.")
        return 0
    if changed:
        print("Updated:\n  " + "\n  ".join(changed))
    else:
        print("Assets are already up to date.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
