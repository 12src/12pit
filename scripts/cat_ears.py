import argparse
import json
import math
from pathlib import Path
import zlib

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIRECTORY = ROOT / "src/main/resources/assets/pit12/cosmetic"
PREVIEW_DIRECTORY = ROOT / "docs/preview"

RESOLUTION = 16

GEOMETRY = {
    "width": 3.0,
    "height": 5.0,
    "thickness": 3.0,
    "segments": 3,
    "taper": 0.62,
    "splay": 0.18,
    "tilt": 0.1,
    "lean": 0.15,
    "pivot": (2.6, -8.0, -1.0),
}

PRESETS = {
    "plain": {
        "fur": "#2b2b2b",
        "inner": "#df5b73",
        "pattern": "plain",
        "stripe": "#1f1f1f",
    },
    "tabby": {
        "fur": "#2b2b2b",
        "inner": "#df5b73",
        "pattern": "tabby",
        "stripe": "#151515",
    },
    "spots": {
        "fur": "#2b2b2b",
        "inner": "#df5b73",
        "pattern": "spots",
        "stripe": "#3d3d3d",
    },
}

LIGHT = (0.35, -0.78, 0.52)
CAMERA = (0.6, 0.28)
PREVIEW_SIZE = 480
CU = 16.0


def number(value):
    return round(float(value), 4)


def vec(values):
    return [number(value) for value in values]


def hex_color(text):
    value = text.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))


def color_hex(color):
    return "#{:02x}{:02x}{:02x}".format(color[0], color[1], color[2])


def shade(color, factor):
    return tuple(min(255, max(0, int(round(channel * factor)))) for channel in color)


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


def rotation_matrix(rotation):
    x, y, z = rotation
    cos_x, sin_x = math.cos(x), math.sin(x)
    cos_y, sin_y = math.cos(y), math.sin(y)
    cos_z, sin_z = math.cos(z), math.sin(z)
    return (
        (cos_z * cos_y, cos_z * sin_y * sin_x - sin_z * cos_x,
         cos_z * sin_y * cos_x + sin_z * sin_x),
        (sin_z * cos_y, sin_z * sin_y * sin_x + cos_z * cos_x,
         sin_z * sin_y * cos_x - cos_z * sin_x),
        (-sin_y, cos_y * sin_x, cos_y * cos_x),
    )


def apply(matrix, point):
    return tuple(sum(matrix[row][column] * point[column] for column in range(3))
                 for row in range(3))


def inverse_apply(matrix, point):
    return tuple(sum(matrix[row][column] * point[row] for row in range(3))
                 for column in range(3))


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
                "pivot": pivot,
                "rotation": rotation,
                "origin": origin,
                "size": (box_width, box_height, box_depth),
                "inner": False,
            })
            offset -= box_height
    return parts


def contains(part, point, margin=0.0):
    matrix = rotation_matrix(part["rotation"])
    local = inverse_apply(matrix, (point[0] - part["pivot"][0],
                                   point[1] - part["pivot"][1],
                                   point[2] - part["pivot"][2]))
    origin = part["origin"]
    size = part["size"]
    return (origin[0] - margin <= local[0] <= origin[0] + size[0] + margin
            and origin[1] - margin <= local[1] <= origin[1] + size[1] + margin
            and origin[2] - margin <= local[2] <= origin[2] + size[2] + margin)


def noise(x, y, z):
    value = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    return (value & 0xFF) / 255.0


def voxel_color(preset, cell, inner):
    if inner:
        return hex_color(preset["inner"])
    fur = hex_color(preset["fur"])
    stripe = hex_color(preset["stripe"])
    pattern = preset["pattern"]
    if pattern == "tabby" and cell[1] % 4 == 0:
        return stripe
    if pattern == "spots" and noise(cell[0], cell[1], cell[2]) > 0.86:
        return stripe
    return fur


def rasterize(parts, resolution, preset):
    step = CU / resolution
    minimum = [10 ** 9, 10 ** 9, 10 ** 9]
    maximum = [-10 ** 9, -10 ** 9, -10 ** 9]
    for part in parts:
        origin = part["origin"]
        size = part["size"]
        for corner_x in (origin[0], origin[0] + size[0]):
            for corner_y in (origin[1], origin[1] + size[1]):
                for corner_z in (origin[2], origin[2] + size[2]):
                    point = apply(rotation_matrix(part["rotation"]), (corner_x, corner_y, corner_z))
                    point = (point[0] + part["pivot"][0], point[1] + part["pivot"][1],
                             point[2] + part["pivot"][2])
                    for axis in range(3):
                        minimum[axis] = min(minimum[axis], point[axis])
                        maximum[axis] = max(maximum[axis], point[axis])
    voxels = {}
    start = [int(math.floor(minimum[axis] / step)) for axis in range(3)]
    end = [int(math.ceil(maximum[axis] / step)) for axis in range(3)]
    for cell_x in range(start[0], end[0] + 1):
        for cell_y in range(start[1], end[1] + 1):
            for cell_z in range(start[2], end[2] + 1):
                center = ((cell_x + 0.5) * step, (cell_y + 0.5) * step, (cell_z + 0.5) * step)
                for part in parts:
                    if contains(part, center):
                        cell = (cell_x, cell_y, cell_z)
                        voxels[cell] = voxel_color(preset, cell, False)
                        break
    paint_inner(voxels, preset, resolution)
    return voxels


def paint_inner(voxels, preset, resolution):
    inner = hex_color(preset["inner"])
    step = CU / resolution
    base = int(round(-8.0 / step))
    for cell in list(voxels):
        if (cell[0], cell[1], cell[2] - 1) in voxels:
            continue
        if not (base - 3 <= cell[1] <= base - 2):
            continue
        if not (1 <= abs(cell[0]) <= 3):
            continue
        voxels[cell] = inner


NEIGHBORS = (
    ((1, 0, 0), ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0))),
    ((-1, 0, 0), ((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1))),
    ((0, 1, 0), ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1))),
    ((0, -1, 0), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))),
    ((0, 0, 1), ((0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0))),
    ((0, 0, -1), ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))),
)

FACES = (
    (0, 1, 3, 2),
    (4, 6, 7, 5),
    (0, 4, 5, 1),
    (2, 3, 7, 6),
    (0, 2, 6, 4),
    (1, 5, 7, 3),
)

HEAD_EDGES = (
    (0, 1), (0, 2), (1, 3), (2, 3), (4, 5), (4, 6), (5, 7), (6, 7),
    (0, 4), (1, 5), (2, 6), (3, 7),
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
    return (sum(shown[i] * right[i] for i in range(3)),
            -sum(shown[i] * up[i] for i in range(3)))


def face_normal(points):
    first = [points[1][i] - points[0][i] for i in range(3)]
    second = [points[2][i] - points[0][i] for i in range(3)]
    normal = (first[1] * second[2] - first[2] * second[1],
              first[2] * second[0] - first[0] * second[2],
              first[0] * second[1] - first[1] * second[0])
    length = sum(value * value for value in normal) ** 0.5
    if length == 0:
        return (0.0, 0.0, 0.0)
    return tuple(value / length for value in normal)


def face_shade(normal):
    dot = sum(normal[i] * LIGHT[i] for i in range(3))
    return 0.45 + 0.55 * max(0.0, dot)


def voxel_faces(voxels, resolution):
    step = CU / resolution
    faces = []
    for cell, color in voxels.items():
        for offset, corners in NEIGHBORS:
            neighbor = (cell[0] + offset[0], cell[1] + offset[1], cell[2] + offset[2])
            if neighbor in voxels:
                continue
            points = []
            for corner in corners:
                points.append(((cell[0] + corner[0]) * step,
                               (cell[1] + corner[1]) * step,
                               (cell[2] + corner[2]) * step))
            normal = face_normal(points)
            center = [sum(point[i] for point in points) / 4.0 for i in range(3)]
            if sum((center[i] - (cell[i] + 0.5) * step) * normal[i] for i in range(3)) < 0:
                normal = tuple(-value for value in normal)
            faces.append((points, normal, color))
    return faces


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
    body += chunk(b"IHDR", size.to_bytes(4, "big") + size.to_bytes(4, "big")
                  + bytes((8, 6, 0, 0, 0)))
    body += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    body += chunk(b"IEND", b"")
    return body


def preview_bytes(voxels, resolution, size):
    right, up, forward = camera_basis()
    faces = voxel_faces(voxels, resolution)
    projected = []
    for points, normal, color in faces:
        screen = [project(point, right, up) for point in points]
        depth = sum(sum(display(point)[i] * forward[i] for i in range(3))
                    for point in points) / len(points)
        projected.append((screen, depth, normal, color))
    head_screen = [project(point, right, up) for point in head_points()]
    all_points = [point for screen, _, _, _ in projected for point in screen] + head_screen
    minimum_x = min(point[0] for point in all_points)
    maximum_x = max(point[0] for point in all_points)
    minimum_y = min(point[1] for point in all_points)
    maximum_y = max(point[1] for point in all_points)
    span = max(maximum_x - minimum_x, maximum_y - minimum_y) or 1.0
    scale = (size * 0.86) / span
    offset_x = size / 2.0 - (minimum_x + maximum_x) / 2.0 * scale
    offset_y = size / 2.0 - (minimum_y + maximum_y) / 2.0 * scale
    image = bytearray(size * size * 4)
    for first, second in HEAD_EDGES:
        start = (head_screen[first][0] * scale + offset_x, head_screen[first][1] * scale + offset_y)
        end = (head_screen[second][0] * scale + offset_x, head_screen[second][1] * scale + offset_y)
        draw_line(image, size, start, end, (150, 150, 150))
    ordered = []
    for screen, depth, normal, color in projected:
        placed = [(point[0] * scale + offset_x, point[1] * scale + offset_y) for point in screen]
        ordered.append((depth, placed, normal, color))
    ordered.sort(key=lambda face: -face[0])
    for _, placed, normal, color in ordered:
        fill_polygon(image, size, placed, shade(color, face_shade(normal)))
    return png_bytes(size, image)


def head_points():
    points = []
    for x in (-4, 4):
        for y in (-8, 0):
            for z in (-4, 4):
                points.append((x, y, z))
    return points


def model_payload(voxels, resolution):
    palette = []
    indexes = {}
    entries = []
    for cell in sorted(voxels):
        color = voxels[cell]
        if color not in indexes:
            indexes[color] = len(palette)
            palette.append(color_hex(color))
        entries.append([cell[0], cell[1], cell[2], indexes[color]])
    return {
        "kind": "voxels",
        "resolution": resolution,
        "palette": palette,
        "voxels": entries,
    }


def read(path):
    return path.read_bytes() if path.exists() else None


def write_changed(path, content):
    if read(path) == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return True


def generate(preset_name, resolution):
    preset = PRESETS[preset_name]
    voxels = rasterize(ear_parts(GEOMETRY), resolution, preset)
    model = (json.dumps(model_payload(voxels, resolution), indent=2) + "\n").encode("utf-8")
    preview = preview_bytes(voxels, resolution, PREVIEW_SIZE)
    return model, preview


def main():
    global RESOLUTION
    parser = argparse.ArgumentParser(description="Generates the voxel cat ear model.")
    parser.add_argument("--preset", action="append", choices=sorted(PRESETS), help="preset to generate")
    parser.add_argument("--resolution", type=int, default=RESOLUTION, help="voxels per block")
    parser.add_argument("--check", action="store_true", help="verify the committed assets instead of writing")
    parser.add_argument("--preview", action="store_true", help="only write the preview images")
    arguments = parser.parse_args()
    if arguments.resolution < 4 or arguments.resolution > 64:
        parser.error("--resolution must be between 4 and 64")
    RESOLUTION = arguments.resolution
    presets = arguments.preset or sorted(PRESETS)
    changed = []
    stale = []
    for name in presets:
        model, preview = generate(name, RESOLUTION)
        targets = [
            (MODEL_DIRECTORY / (name + ".json"), model),
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
