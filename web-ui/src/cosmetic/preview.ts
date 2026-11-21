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
import {
  FACES,
  faceRects,
  headCorners,
  type Model,
  type Part,
  type Rect,
  corners,
} from './model'

const LIGHT = [0.35, -0.78, 0.52]

const HEAD_EDGES = [
  [0, 1],
  [0, 2],
  [1, 3],
  [2, 3],
  [4, 5],
  [4, 6],
  [5, 7],
  [6, 7],
  [0, 4],
  [1, 5],
  [2, 6],
  [3, 7],
]

interface Face {
  points: number[][]
  normal: number[]
  color: [number, number, number]
}

export function averageColor(
  pixels: Uint8ClampedArray,
  size: number,
  rect: Rect,
): [number, number, number] {
  const [u, v, width, height] = rect
  let red = 0
  let green = 0
  let blue = 0
  let count = 0
  for (let y = v; y < Math.min(size, v + Math.max(1, height)); y++) {
    for (let x = u; x < Math.min(size, u + Math.max(1, width)); x++) {
      const offset = (y * size + x) * 4
      red += pixels[offset]
      green += pixels[offset + 1]
      blue += pixels[offset + 2]
      count++
    }
  }
  if (count === 0) return [255, 255, 255]
  return [
    Math.round(red / count),
    Math.round(green / count),
    Math.round(blue / count),
  ]
}

function normal(points: number[][]): number[] {
  const first = [
    points[1][0] - points[0][0],
    points[1][1] - points[0][1],
    points[1][2] - points[0][2],
  ]
  const second = [
    points[2][0] - points[0][0],
    points[2][1] - points[0][1],
    points[2][2] - points[0][2],
  ]
  const result = [
    first[1] * second[2] - first[2] * second[1],
    first[2] * second[0] - first[0] * second[2],
    first[0] * second[1] - first[1] * second[0],
  ]
  const length = Math.hypot(result[0], result[1], result[2]) || 1
  return [result[0] / length, result[1] / length, result[2] / length]
}

function center(points: number[][]): number[] {
  const total = [0, 0, 0]
  for (const point of points) {
    total[0] += point[0]
    total[1] += point[1]
    total[2] += point[2]
  }
  return [
    total[0] / points.length,
    total[1] / points.length,
    total[2] / points.length,
  ]
}

function collectFaces(
  model: Model,
  pixels: Uint8ClampedArray,
  size: number,
): Face[] {
  const faces: Face[] = []
  for (const part of model.parts) {
    const points = corners(part as Part)
    const middle = center(points)
    const rects = faceRects(part as Part)
    FACES.forEach((face, index) => {
      const quad = face.map((corner) => points[corner])
      let direction = normal(quad)
      const faceCenter = center(quad)
      const outward =
        (faceCenter[0] - middle[0]) * direction[0] +
        (faceCenter[1] - middle[1]) * direction[1] +
        (faceCenter[2] - middle[2]) * direction[2]
      if (outward < 0) direction = direction.map((value) => -value)
      faces.push({
        points: quad,
        normal: direction,
        color: averageColor(pixels, size, rects[index]),
      })
    })
  }
  return faces
}

function display(point: number[]): number[] {
  return [point[0], -point[1], point[2]]
}

export function drawPreview(
  canvas: HTMLCanvasElement,
  model: Model,
  pixels: Uint8ClampedArray,
  size: number,
  yaw: number,
  pitch: number,
) {
  const context = canvas.getContext('2d')
  if (!context) return
  const width = canvas.width
  const height = canvas.height
  context.clearRect(0, 0, width, height)

  const right = [Math.cos(yaw), 0, -Math.sin(yaw)]
  const up = [
    Math.sin(yaw) * Math.sin(pitch),
    Math.cos(pitch),
    Math.cos(yaw) * Math.sin(pitch),
  ]
  const forward = [
    Math.sin(yaw) * Math.cos(pitch),
    -Math.sin(pitch),
    Math.cos(yaw) * Math.cos(pitch),
  ]
  const project = (point: number[]) => {
    const shown = display(point)
    return [
      shown[0] * right[0] + shown[1] * right[1] + shown[2] * right[2],
      -(shown[0] * up[0] + shown[1] * up[1] + shown[2] * up[2]),
    ]
  }
  const depthOf = (point: number[]) => {
    const shown = display(point)
    return shown[0] * forward[0] + shown[1] * forward[1] + shown[2] * forward[2]
  }

  const faces = collectFaces(model, pixels, size)
  const head = headCorners()
  const projected = faces.map((face) => ({
    face,
    screen: face.points.map(project),
    depth:
      face.points.reduce((total, point) => total + depthOf(point), 0) /
      face.points.length,
  }))
  const headScreen = head.map(project)
  const all = [...projected.flatMap((entry) => entry.screen), ...headScreen]
  const minimumX = Math.min(...all.map((point) => point[0]))
  const maximumX = Math.max(...all.map((point) => point[0]))
  const minimumY = Math.min(...all.map((point) => point[1]))
  const maximumY = Math.max(...all.map((point) => point[1]))
  const span = Math.max(maximumX - minimumX, maximumY - minimumY) || 1
  const scale = (Math.min(width, height) * 0.86) / span
  const offsetX = width / 2 - ((minimumX + maximumX) / 2) * scale
  const offsetY = height / 2 - ((minimumY + maximumY) / 2) * scale
  const place = (point: number[]) =>
    [point[0] * scale + offsetX, point[1] * scale + offsetY] as [number, number]

  context.lineWidth = 2
  context.strokeStyle = '#3a4046'
  context.beginPath()
  for (const [first, second] of HEAD_EDGES) {
    const start = place(headScreen[first])
    const end = place(headScreen[second])
    context.moveTo(start[0], start[1])
    context.lineTo(end[0], end[1])
  }
  context.stroke()

  projected.sort((first, second) => second.depth - first.depth)
  for (const entry of projected) {
    const shade =
      0.45 +
      0.55 *
        Math.max(
          0,
          entry.face.normal[0] * LIGHT[0] +
            entry.face.normal[1] * LIGHT[1] +
            entry.face.normal[2] * LIGHT[2],
        )
    const color = entry.face.color
      .map((channel) => Math.round(channel * shade))
      .join(',')
    const points = entry.screen.map(place)
    context.beginPath()
    context.moveTo(points[0][0], points[0][1])
    for (const point of points.slice(1)) context.lineTo(point[0], point[1])
    context.closePath()
    context.fillStyle = `rgb(${color})`
    context.fill()
    context.strokeStyle = `rgb(${color})`
    context.stroke()
  }
}
