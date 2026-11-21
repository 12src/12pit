/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
export type Color = [number, number, number, number]

export function paintPixel(
  pixels: Uint8ClampedArray,
  size: number,
  x: number,
  y: number,
  color: Color,
) {
  if (x < 0 || y < 0 || x >= size || y >= size) return
  const offset = (y * size + x) * 4
  pixels[offset] = color[0]
  pixels[offset + 1] = color[1]
  pixels[offset + 2] = color[2]
  pixels[offset + 3] = color[3]
}

export function readPixel(
  pixels: Uint8ClampedArray,
  size: number,
  x: number,
  y: number,
): Color {
  const offset = (y * size + x) * 4
  return [
    pixels[offset],
    pixels[offset + 1],
    pixels[offset + 2],
    pixels[offset + 3],
  ]
}

export function floodFill(
  pixels: Uint8ClampedArray,
  size: number,
  x: number,
  y: number,
  color: Color,
) {
  if (x < 0 || y < 0 || x >= size || y >= size) return
  const target = readPixel(pixels, size, x, y)
  if (
    target[0] === color[0] &&
    target[1] === color[1] &&
    target[2] === color[2] &&
    target[3] === color[3]
  ) {
    return
  }
  const stack: number[] = [y * size + x]
  while (stack.length) {
    const index = stack.pop() as number
    const currentX = index % size
    const currentY = (index - currentX) / size
    const offset = index * 4
    if (
      pixels[offset] !== target[0] ||
      pixels[offset + 1] !== target[1] ||
      pixels[offset + 2] !== target[2] ||
      pixels[offset + 3] !== target[3]
    ) {
      continue
    }
    pixels[offset] = color[0]
    pixels[offset + 1] = color[1]
    pixels[offset + 2] = color[2]
    pixels[offset + 3] = color[3]
    if (currentX > 0) stack.push(index - 1)
    if (currentX + 1 < size) stack.push(index + 1)
    if (currentY > 0) stack.push(index - size)
    if (currentY + 1 < size) stack.push(index + size)
  }
}

export function hexToColor(hex: string): Color {
  const value = hex.replace('#', '')
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
    255,
  ]
}

export function colorToHex(color: Color): string {
  const channel = (value: number) => value.toString(16).padStart(2, '0')
  return `#${channel(color[0])}${channel(color[1])}${channel(color[2])}`
}
