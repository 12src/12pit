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
import plainModel from '../../../src/main/resources/assets/pit12/cosmetic/plain.json'
import spotsModel from '../../../src/main/resources/assets/pit12/cosmetic/spots.json'
import plainTexture from '../../../src/main/resources/assets/pit12/textures/cosmetic/plain.png?url'
import spotsTexture from '../../../src/main/resources/assets/pit12/textures/cosmetic/spots.png?url'
import tabbyModel from '../../../src/main/resources/assets/pit12/cosmetic/tabby.json'
import tabbyTexture from '../../../src/main/resources/assets/pit12/textures/cosmetic/tabby.png?url'

export interface Part {
  rotationPoint: number[]
  rotation: number[]
  origin: number[]
  size: number[]
  uv: number[]
  inner: boolean
}

export interface Model {
  textureSize: number[]
  parts: Part[]
}

export interface Style {
  id: string
  model: Model
  texture: string
}

export type Rect = [number, number, number, number]

const STYLES: Style[] = [
  { id: 'plain', model: plainModel as Model, texture: plainTexture },
  { id: 'tabby', model: tabbyModel as Model, texture: tabbyTexture },
  { id: 'spots', model: spotsModel as Model, texture: spotsTexture },
]

export { STYLES }

const FACES: number[][] = [
  [0, 1, 3, 2],
  [4, 6, 7, 5],
  [0, 4, 5, 1],
  [2, 3, 7, 6],
  [0, 2, 6, 4],
  [1, 5, 7, 3],
]

const HEAD = {
  rotationPoint: [0, 0, 0],
  rotation: [0, 0, 0],
  origin: [-4, -8, -4],
  size: [8, 8, 8],
}

export function regionRect(part: Part): Rect {
  const width = part.size[0]
  const height = part.size[1]
  const depth = part.size[2]
  return [part.uv[0], part.uv[1], 2 * (width + depth), depth + 2 * height]
}

export function faceRects(part: Part): Rect[] {
  const u = part.uv[0]
  const v = part.uv[1]
  const width = part.size[0]
  const height = part.size[1]
  const depth = part.size[2]
  return [
    [u + depth + width, v + depth, depth, height],
    [u, v + depth, depth, height],
    [u + depth, v, width, depth],
    [u + depth + width, v, width, depth],
    [u + depth, v + depth, width, height],
    [u + depth + width + depth, v + depth, width, height],
  ]
}

function rotate(point: number[], rotation: number[]): number[] {
  const [x, y, z] = point
  const cx = Math.cos(rotation[0])
  const sx = Math.sin(rotation[0])
  const cy = Math.cos(rotation[1])
  const sy = Math.sin(rotation[1])
  const cz = Math.cos(rotation[2])
  const sz = Math.sin(rotation[2])
  const first = [x, y * cx - z * sx, y * sx + z * cx]
  const second = [
    first[0] * cy + first[2] * sy,
    first[1],
    -first[0] * sy + first[2] * cy,
  ]
  return [
    second[0] * cz - second[1] * sz,
    second[0] * sz + second[1] * cz,
    second[2],
  ]
}

export function transform(point: number[], part: Part): number[] {
  const turned = rotate(point, part.rotation)
  return [
    turned[0] + part.rotationPoint[0],
    turned[1] + part.rotationPoint[1],
    turned[2] + part.rotationPoint[2],
  ]
}

export function corners(part: {
  origin: number[]
  size: number[]
  rotation: number[]
  rotationPoint: number[]
}): number[][] {
  const [x, y, z] = part.origin
  const [width, height, depth] = part.size
  const points: number[][] = []
  for (const cornerX of [x, x + width]) {
    for (const cornerY of [y, y + height]) {
      for (const cornerZ of [z, z + depth]) {
        points.push(transform([cornerX, cornerY, cornerZ], part as Part))
      }
    }
  }
  return points
}

export function headCorners(): number[][] {
  return corners(HEAD as Part)
}

export { FACES, HEAD }
