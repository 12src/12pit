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
export interface Rgba {
  r: number
  g: number
  b: number
  a: number
}

export interface Hsva {
  h: number
  s: number
  v: number
  a: number
}

export function fromArgb(value: number): Rgba {
  return {
    r: (value >>> 16) & 255,
    g: (value >>> 8) & 255,
    b: value & 255,
    a: (value >>> 24) / 255,
  }
}

export function toArgb(color: Rgba): number {
  return (
    (Math.round(color.a * 255) << 24) |
    (Math.round(color.r) << 16) |
    (Math.round(color.g) << 8) |
    Math.round(color.b)
  )
}

export function toHex(color: Rgba): string {
  // CSS puts alpha last; the stored ARGB integer puts it first.
  return `#${[color.r, color.g, color.b, color.a * 255]
    .map((value) => Math.round(value).toString(16).padStart(2, '0'))
    .join('')}`
}

export function toHsva(color: Rgba): Hsva {
  const r = color.r / 255
  const g = color.g / 255
  const b = color.b / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const delta = max - min
  let hue = 0
  if (delta !== 0) {
    if (max === r) hue = (g - b) / delta
    else if (max === g) hue = (b - r) / delta + 2
    else hue = (r - g) / delta + 4
  }
  return {
    h: (hue * 60 + 360) % 360,
    s: max === 0 ? 0 : delta / max,
    v: max,
    a: color.a,
  }
}

export function toRgba(color: Hsva): Rgba {
  const channel = (offset: number) => {
    const k = (offset + color.h / 60) % 6
    return Math.round(
      (color.v - color.v * color.s * Math.max(0, Math.min(k, 4 - k, 1))) * 255,
    )
  }
  return { r: channel(5), g: channel(3), b: channel(1), a: color.a }
}

function component(text: string, maximum: number): number | null {
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)%?$/.test(text)) return null
  const value = text.endsWith('%')
    ? (Number(text.slice(0, -1)) * maximum) / 100
    : Number(text)
  return Number.isFinite(value) && value >= 0 && value <= maximum ? value : null
}

export function parseColor(text: string): Rgba | null {
  const hex = /^#?([\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i.exec(text.trim())
  if (hex) {
    const digits =
      hex[1].length <= 4
        ? [...hex[1]].map((digit) => digit + digit).join('')
        : hex[1]
    return {
      r: parseInt(digits.slice(0, 2), 16),
      g: parseInt(digits.slice(2, 4), 16),
      b: parseInt(digits.slice(4, 6), 16),
      a: digits.length === 8 ? parseInt(digits.slice(6, 8), 16) / 255 : 1,
    }
  }
  const match = /^(rgba?|hsla?)\((.*)\)$/i.exec(text.trim())
  if (!match) return null
  const name = match[1].toLowerCase()
  const parts = match[2].split(',').map((part) => part.trim())
  if (parts.length !== (name.endsWith('a') ? 4 : 3)) return null
  const alpha = parts.length === 4 ? component(parts[3], 1) : 1
  if (alpha === null) return null
  if (name.startsWith('rgb')) {
    const r = component(parts[0], 255)
    const g = component(parts[1], 255)
    const b = component(parts[2], 255)
    return r === null || g === null || b === null ? null : { r, g, b, a: alpha }
  }
  const hueText = parts[0].replace(/deg$/i, '')
  if (
    !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(hueText) ||
    !parts[1].endsWith('%') || !parts[2].endsWith('%')
  ) return null
  const h = Number(hueText)
  const s = component(parts[1], 1)
  const l = component(parts[2], 1)
  if (!Number.isFinite(h) || s === null || l === null) return null
  const v = l + s * Math.min(l, 1 - l)
  return toRgba({
    h: ((h % 360) + 360) % 360,
    s: v === 0 ? 0 : 2 * (1 - l / v),
    v,
    a: alpha,
  })
}
