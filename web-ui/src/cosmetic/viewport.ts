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
import type { Mesh } from './voxel'

const FIELD_OF_VIEW = (45 * Math.PI) / 180

type Vector = [number, number, number]

function subtract(first: Vector, second: Vector): Vector {
  return [first[0] - second[0], first[1] - second[1], first[2] - second[2]]
}

function cross(first: Vector, second: Vector): Vector {
  return [
    first[1] * second[2] - first[2] * second[1],
    first[2] * second[0] - first[0] * second[2],
    first[0] * second[1] - first[1] * second[0],
  ]
}

function normalize(vector: Vector): Vector {
  const length = Math.hypot(vector[0], vector[1], vector[2]) || 1
  return [vector[0] / length, vector[1] / length, vector[2] / length]
}

function perspective(
  fieldOfView: number,
  aspect: number,
  near: number,
  far: number,
) {
  const scale = 1 / Math.tan(fieldOfView / 2)
  const range = 1 / (near - far)
  return new Float32Array([
    scale / aspect,
    0,
    0,
    0,
    0,
    scale,
    0,
    0,
    0,
    0,
    (near + far) * range,
    -1,
    0,
    0,
    near * far * range * 2,
    0,
  ])
}

function lookAt(eye: Vector, target: Vector, up: Vector) {
  const forward = normalize(subtract(target, eye))
  const right = normalize(cross(forward, up))
  const correctedUp = cross(right, forward)
  return new Float32Array([
    right[0],
    correctedUp[0],
    -forward[0],
    0,
    right[1],
    correctedUp[1],
    -forward[1],
    0,
    right[2],
    correctedUp[2],
    -forward[2],
    0,
    -(right[0] * eye[0] + right[1] * eye[1] + right[2] * eye[2]),
    -(
      correctedUp[0] * eye[0] +
      correctedUp[1] * eye[1] +
      correctedUp[2] * eye[2]
    ),
    forward[0] * eye[0] + forward[1] * eye[1] + forward[2] * eye[2],
    1,
  ])
}

function multiply(first: Float32Array, second: Float32Array) {
  const result = new Float32Array(16)
  for (let column = 0; column < 4; column++) {
    for (let row = 0; row < 4; row++) {
      let value = 0
      for (let index = 0; index < 4; index++) {
        value += first[index * 4 + row] * second[column * 4 + index]
      }
      result[column * 4 + row] = value
    }
  }
  return result
}

const VERTEX_SHADER = `
attribute vec3 aPosition;
attribute vec3 aColor;
uniform mat4 uMvp;
varying vec3 vColor;
void main() {
  vColor = aColor;
  gl_Position = uMvp * vec4(aPosition, 1.0);
}`

const FRAGMENT_SHADER = `
precision mediump float;
varying vec3 vColor;
void main() {
  gl_FragColor = vec4(vColor, 1.0);
}`

export class Viewport {
  readonly canvas: HTMLCanvasElement
  private readonly gl: WebGLRenderingContext
  private readonly program: WebGLProgram
  private readonly positionLocation: number
  private readonly colorLocation: number
  private readonly mvpLocation: WebGLUniformLocation
  private readonly facePositions: WebGLBuffer
  private readonly faceColors: WebGLBuffer
  private readonly linePositions: WebGLBuffer
  private faceCount = 0
  private lineCount = 0
  yaw = 0.7
  pitch = 0.35
  distance = 30
  center: Vector = [0, -5, 0]
  gridVisible = true

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas
    const gl = canvas.getContext('webgl', {
      antialias: true,
      alpha: false,
      depth: true,
    })
    if (!gl) throw new Error('WebGL is not available')
    this.gl = gl
    this.program = this.compile()
    this.positionLocation = gl.getAttribLocation(this.program, 'aPosition')
    this.colorLocation = gl.getAttribLocation(this.program, 'aColor')
    const mvpLocation = gl.getUniformLocation(this.program, 'uMvp')
    if (!mvpLocation) throw new Error('Missing uniform')
    this.mvpLocation = mvpLocation
    this.facePositions = this.buffer()
    this.faceColors = this.buffer()
    this.linePositions = this.buffer()
    gl.enable(gl.DEPTH_TEST)
    gl.clearColor(0.075, 0.086, 0.094, 1)
  }

  private compile() {
    const gl = this.gl
    const program = gl.createProgram()
    if (!program) throw new Error('Could not create program')
    for (const [type, source] of [
      [gl.VERTEX_SHADER, VERTEX_SHADER],
      [gl.FRAGMENT_SHADER, FRAGMENT_SHADER],
    ] as const) {
      const shader = gl.createShader(type)
      if (!shader) throw new Error('Could not create shader')
      gl.shaderSource(shader, source)
      gl.compileShader(shader)
      gl.attachShader(program, shader)
    }
    gl.linkProgram(program)
    return program
  }

  private buffer() {
    const buffer = this.gl.createBuffer()
    if (!buffer) throw new Error('Could not create buffer')
    return buffer
  }

  setMesh(mesh: Mesh) {
    const gl = this.gl
    gl.bindBuffer(gl.ARRAY_BUFFER, this.facePositions)
    gl.bufferData(gl.ARRAY_BUFFER, mesh.positions, gl.DYNAMIC_DRAW)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.faceColors)
    gl.bufferData(gl.ARRAY_BUFFER, mesh.colors, gl.DYNAMIC_DRAW)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.linePositions)
    gl.bufferData(gl.ARRAY_BUFFER, mesh.lines, gl.DYNAMIC_DRAW)
    this.faceCount = mesh.positions.length / 3
    this.lineCount = mesh.lines.length / 3
  }

  setCenter(center: Vector, radius: number) {
    this.center = center
    this.distance = Math.max(1.2, radius * 2.2)
  }

  orbit(deltaX: number, deltaY: number) {
    this.yaw += deltaX * 0.01
    this.pitch = Math.max(-1.4, Math.min(1.4, this.pitch + deltaY * 0.01))
  }

  zoom(factor: number) {
    this.distance = Math.max(0.2, Math.min(400, this.distance * factor))
  }

  private basis() {
    const eye: Vector = [
      this.center[0] +
        Math.sin(this.yaw) * Math.cos(this.pitch) * this.distance,
      this.center[1] - Math.sin(this.pitch) * this.distance,
      this.center[2] +
        Math.cos(this.yaw) * Math.cos(this.pitch) * this.distance,
    ]
    const forward = normalize(subtract(this.center, eye))
    const right = normalize(cross(forward, [0, -1, 0]))
    const up = cross(right, forward)
    return { eye, forward, right, up }
  }

  draw() {
    const gl = this.gl
    const width = this.canvas.width
    const height = this.canvas.height
    gl.viewport(0, 0, width, height)
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
    gl.useProgram(this.program)
    const { eye } = this.basis()
    const view = lookAt(eye, this.center, [0, -1, 0])
    const projection = perspective(FIELD_OF_VIEW, width / height, 0.01, 2000)
    const mvp = multiply(projection, view)
    gl.uniformMatrix4fv(this.mvpLocation, false, mvp)
    gl.enableVertexAttribArray(this.positionLocation)
    gl.enableVertexAttribArray(this.colorLocation)
    gl.enable(gl.POLYGON_OFFSET_FILL)
    gl.polygonOffset(1, 1)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.facePositions)
    gl.vertexAttribPointer(this.positionLocation, 3, gl.FLOAT, false, 0, 0)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.faceColors)
    gl.vertexAttribPointer(this.colorLocation, 3, gl.FLOAT, false, 0, 0)
    gl.drawArrays(gl.TRIANGLES, 0, this.faceCount)
    gl.disable(gl.POLYGON_OFFSET_FILL)
    if (this.gridVisible && this.lineCount > 0) {
      gl.disableVertexAttribArray(this.colorLocation)
      gl.vertexAttrib3f(this.colorLocation, 0.35, 0.39, 0.43)
      gl.bindBuffer(gl.ARRAY_BUFFER, this.linePositions)
      gl.vertexAttribPointer(this.positionLocation, 3, gl.FLOAT, false, 0, 0)
      gl.drawArrays(gl.LINES, 0, this.lineCount)
      gl.enableVertexAttribArray(this.colorLocation)
    }
  }

  ray(clientX: number, clientY: number) {
    const bounds = this.canvas.getBoundingClientRect()
    const width = bounds.width
    const height = bounds.height
    const ndcX = (2 * (clientX - bounds.left)) / width - 1
    const ndcY = 1 - (2 * (clientY - bounds.top)) / height
    const { eye, forward, right, up } = this.basis()
    const halfHeight = Math.tan(FIELD_OF_VIEW / 2)
    const halfWidth = halfHeight * (width / height)
    const direction = normalize([
      forward[0] + right[0] * ndcX * halfWidth + up[0] * ndcY * halfHeight,
      forward[1] + right[1] * ndcX * halfWidth + up[1] * ndcY * halfHeight,
      forward[2] + right[2] * ndcX * halfWidth + up[2] * ndcY * halfHeight,
    ])
    return { origin: eye as number[], direction: direction as number[] }
  }

  resize() {
    const ratio = Math.min(2, window.devicePixelRatio || 1)
    const width = Math.max(1, Math.round(this.canvas.clientWidth * ratio))
    const height = Math.max(1, Math.round(this.canvas.clientHeight * ratio))
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width
      this.canvas.height = height
    }
  }
}
