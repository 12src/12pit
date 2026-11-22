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
import spots from '../../../src/main/resources/assets/pit12/cosmetic/spots.json'
import tabby from '../../../src/main/resources/assets/pit12/cosmetic/tabby.json'
import plain from '../../../src/main/resources/assets/pit12/cosmetic/plain.json'

export interface VoxelJson {
  kind: string
  resolution: number
  palette: string[]
  voxels: number[][]
}

export interface Hit {
  x: number
  y: number
  z: number
  nx: number
  ny: number
  nz: number
  placeholder: boolean
}

export interface Mesh {
  positions: Float32Array
  colors: Float32Array
  lines: Float32Array
  faces: number
}

const DIRECTIONS = [
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
  [0, 0, 1],
  [0, 0, -1],
]

const CORNERS = [
  [
    [1, 0, 0],
    [1, 1, 0],
    [1, 1, 1],
    [1, 0, 1],
  ],
  [
    [0, 0, 1],
    [0, 1, 1],
    [0, 1, 0],
    [0, 0, 0],
  ],
  [
    [0, 1, 0],
    [0, 1, 1],
    [1, 1, 1],
    [1, 1, 0],
  ],
  [
    [0, 0, 0],
    [1, 0, 0],
    [1, 0, 1],
    [0, 0, 1],
  ],
  [
    [1, 0, 1],
    [1, 1, 1],
    [0, 1, 1],
    [0, 0, 1],
  ],
  [
    [0, 0, 0],
    [0, 1, 0],
    [1, 1, 0],
    [1, 0, 0],
  ],
]

const SHADE = [0.78, 0.78, 0.5, 1.0, 0.9, 0.72]

const EDGES = [
  [0, 1],
  [1, 2],
  [2, 3],
  [3, 0],
]

export function models(): { id: string; json: VoxelJson }[] {
  return [
    { id: 'plain', json: plain as unknown as VoxelJson },
    { id: 'tabby', json: tabby as unknown as VoxelJson },
    { id: 'spots', json: spots as unknown as VoxelJson },
  ]
}

function key(x: number, y: number, z: number) {
  return `${x},${y},${z}`
}

function parseColor(text: string): [number, number, number] {
  const value = text.replace('#', '')
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ]
}

export class VoxelModel {
  resolution: number
  palette: string[]
  cells = new Map<string, number>()

  constructor(resolution: number, palette: string[]) {
    this.resolution = resolution
    this.palette = palette.slice()
  }

  static fromJson(json: VoxelJson): VoxelModel {
    const model = new VoxelModel(json.resolution, json.palette)
    for (const voxel of json.voxels) {
      model.cells.set(key(voxel[0], voxel[1], voxel[2]), voxel[3])
    }
    return model
  }

  toJson(): VoxelJson {
    const voxels: number[][] = []
    for (const [location, index] of this.cells) {
      const [x, y, z] = location.split(',').map(Number)
      voxels.push([x, y, z, index])
    }
    voxels.sort(
      (first, second) =>
        first[0] - second[0] || first[1] - second[1] || first[2] - second[2],
    )
    return {
      kind: 'voxels',
      resolution: this.resolution,
      palette: this.palette.slice(),
      voxels,
    }
  }

  clone(): VoxelModel {
    const copy = new VoxelModel(this.resolution, this.palette)
    copy.cells = new Map(this.cells)
    return copy
  }

  has(x: number, y: number, z: number) {
    return this.cells.has(key(x, y, z))
  }

  colorIndex(x: number, y: number, z: number) {
    return this.cells.get(key(x, y, z))
  }

  set(x: number, y: number, z: number, index: number) {
    this.cells.set(key(x, y, z), index)
  }

  remove(x: number, y: number, z: number) {
    this.cells.delete(key(x, y, z))
  }

  colorOf(index: number) {
    return parseColor(this.palette[index] ?? '#ffffff')
  }

  indexOf(hex: string) {
    const normalized = hex.toLowerCase()
    const existing = this.palette.findIndex(
      (entry) => entry.toLowerCase() === normalized,
    )
    if (existing >= 0) return existing
    this.palette.push(normalized)
    return this.palette.length - 1
  }

  bounds() {
    let minimumX = Infinity
    let minimumY = Infinity
    let minimumZ = Infinity
    let maximumX = -Infinity
    let maximumY = -Infinity
    let maximumZ = -Infinity
    for (const location of this.cells.keys()) {
      const [x, y, z] = location.split(',').map(Number)
      minimumX = Math.min(minimumX, x)
      minimumY = Math.min(minimumY, y)
      minimumZ = Math.min(minimumZ, z)
      maximumX = Math.max(maximumX, x)
      maximumY = Math.max(maximumY, y)
      maximumZ = Math.max(maximumZ, z)
    }
    if (minimumX === Infinity) {
      return { min: [-4, -12, -4], max: [4, 0, 4] }
    }
    return {
      min: [minimumX, minimumY, minimumZ],
      max: [maximumX + 1, maximumY + 1, maximumZ + 1],
    }
  }

  mesh(slice: number | null): Mesh {
    const unit = 1 / this.resolution
    const positions: number[] = []
    const colors: number[] = []
    const lines: number[] = []
    let faces = 0
    for (const [location, index] of this.cells) {
      const [x, y, z] = location.split(',').map(Number)
      if (slice !== null && y !== slice) continue
      const color = this.colorOf(index)
      for (let direction = 0; direction < DIRECTIONS.length; direction++) {
        const offset = DIRECTIONS[direction]
        if (this.has(x + offset[0], y + offset[1], z + offset[2])) continue
        faces++
        const corners = CORNERS[direction].map((corner) => [
          (x + corner[0]) * unit,
          (y + corner[1]) * unit,
          (z + corner[2]) * unit,
        ])
        const shade = SHADE[direction]
        const red = (color[0] / 255) * shade
        const green = (color[1] / 255) * shade
        const blue = (color[2] / 255) * shade
        for (const triangle of [
          [0, 1, 2],
          [0, 2, 3],
        ]) {
          for (const indexInTriangle of triangle) {
            positions.push(...corners[indexInTriangle])
            colors.push(red, green, blue)
          }
        }
        for (const edge of EDGES) {
          lines.push(...corners[edge[0]], ...corners[edge[1]])
        }
      }
    }
    return {
      positions: new Float32Array(positions),
      colors: new Float32Array(colors),
      lines: new Float32Array(lines),
      faces,
    }
  }

  pick(
    origin: number[],
    direction: number[],
    floorCell: number | null,
  ): Hit | null {
    const hit = this.pickCell(origin, direction)
    if (hit || floorCell === null) return hit
    if (Math.abs(direction[1]) < 1e-6) return null
    const plane = floorCell / this.resolution
    const distance = (plane - origin[1]) / direction[1]
    if (distance <= 0) return null
    const x = (origin[0] + direction[0] * distance) * this.resolution
    const z = (origin[2] + direction[2] * distance) * this.resolution
    const half = this.resolution / 2
    if (x < -half || x >= half || z < -half || z >= half) return null
    return {
      x: Math.floor(x),
      y: floorCell - 1,
      z: Math.floor(z),
      nx: 0,
      ny: -1,
      nz: 0,
      placeholder: true,
    }
  }

  private pickCell(origin: number[], direction: number[]): Hit | null {
    const resolution = this.resolution
    const bounds = this.bounds()
    const originX = origin[0] * resolution
    const originY = origin[1] * resolution
    const originZ = origin[2] * resolution
    const [minimumX, minimumY, minimumZ] = bounds.min
    const [maximumX, maximumY, maximumZ] = bounds.max
    let enter = -Infinity
    let exit = Infinity
    const axes: [number, number, number, number][] = [
      [originX, direction[0], minimumX, maximumX],
      [originY, direction[1], minimumY, maximumY],
      [originZ, direction[2], minimumZ, maximumZ],
    ]
    for (const [position, delta, low, high] of axes) {
      if (Math.abs(delta) < 1e-6) {
        if (position < low || position > high) return null
        continue
      }
      const first = (low - position) / delta
      const second = (high - position) / delta
      enter = Math.max(enter, Math.min(first, second))
      exit = Math.min(exit, Math.max(first, second))
    }
    if (exit < Math.max(enter, 0)) return null
    let distance = Math.max(enter, 0) + 1e-4
    let x = Math.floor(originX + direction[0] * distance)
    let y = Math.floor(originY + direction[1] * distance)
    let z = Math.floor(originZ + direction[2] * distance)
    const stepX = direction[0] > 0 ? 1 : direction[0] < 0 ? -1 : 0
    const stepY = direction[1] > 0 ? 1 : direction[1] < 0 ? -1 : 0
    const stepZ = direction[2] > 0 ? 1 : direction[2] < 0 ? -1 : 0
    const deltaX = stepX === 0 ? Infinity : Math.abs(1 / direction[0])
    const deltaY = stepY === 0 ? Infinity : Math.abs(1 / direction[1])
    const deltaZ = stepZ === 0 ? Infinity : Math.abs(1 / direction[2])
    let maxX =
      stepX === 0
        ? Infinity
        : ((stepX > 0 ? x + 1 : x) - (originX + direction[0] * distance)) /
          direction[0]
    let maxY =
      stepY === 0
        ? Infinity
        : ((stepY > 0 ? y + 1 : y) - (originY + direction[1] * distance)) /
          direction[1]
    let maxZ =
      stepZ === 0
        ? Infinity
        : ((stepZ > 0 ? z + 1 : z) - (originZ + direction[2] * distance)) /
          direction[2]
    let nx = 0
    let ny = 0
    let nz = 0
    for (let step = 0; step < 4096; step++) {
      if (this.has(x, y, z)) return { x, y, z, nx, ny, nz, placeholder: false }
      if (maxX < maxY && maxX < maxZ) {
        x += stepX
        distance = maxX
        maxX += deltaX
        nx = -stepX
        ny = 0
        nz = 0
      } else if (maxY < maxZ) {
        y += stepY
        distance = maxY
        maxY += deltaY
        nx = 0
        ny = -stepY
        nz = 0
      } else {
        z += stepZ
        distance = maxZ
        maxZ += deltaZ
        nx = 0
        ny = 0
        nz = -stepZ
      }
      if (distance > exit) return null
    }
    return null
  }

  resample(resolution: number): VoxelModel {
    if (resolution === this.resolution) return this.clone()
    const copy = new VoxelModel(resolution, this.palette)
    const scale = resolution / this.resolution
    if (scale >= 1) {
      const factor = Math.round(scale)
      for (const [location, index] of this.cells) {
        const [x, y, z] = location.split(',').map(Number)
        for (let dx = 0; dx < factor; dx++) {
          for (let dy = 0; dy < factor; dy++) {
            for (let dz = 0; dz < factor; dz++) {
              copy.set(x * factor + dx, y * factor + dy, z * factor + dz, index)
            }
          }
        }
      }
      return copy
    }
    const divisor = Math.round(1 / scale)
    for (const [location, index] of this.cells) {
      const [x, y, z] = location.split(',').map(Number)
      copy.set(
        Math.floor(x / divisor),
        Math.floor(y / divisor),
        Math.floor(z / divisor),
        index,
      )
    }
    return copy
  }
}
