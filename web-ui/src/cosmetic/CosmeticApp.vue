<!--
This file is part of 12pit.

Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>

12pit is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

12pit is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with 12pit. If not, see <https://www.gnu.org/licenses/>.
-->
<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
} from 'vue'
import {
  ArrowLeft,
  Boxes,
  Download,
  Eraser,
  FlipHorizontal2,
  Grid3x3,
  PaintBucket,
  Pencil,
  Pipette,
  Redo2,
  RotateCcw,
  Undo2,
  Upload,
} from '@lucide/vue'
import { t } from '../languages'
import { VoxelModel, models, type Hit } from './voxel'
import { Viewport } from './viewport'

type Tool = 'paint' | 'erase' | 'add' | 'pick'

const catalog = models()
const palette = [
  '#2b2b2b',
  '#df5b73',
  '#1f1f1f',
  '#ffffff',
  '#9a9a9a',
  '#7fc8a9',
]

const styleIndex = ref(0)
const tool = ref<Tool>('paint')
const color = ref(palette[0])
const mirror = ref(false)
const grid = ref(true)
const sliceEnabled = ref(false)
const slice = ref(-9)
const resolution = ref(16)
const status = ref('')
const faceCount = ref(0)
const voxelCount = ref(0)

const canvas = ref<HTMLCanvasElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const model = shallowRef<VoxelModel>(VoxelModel.fromJson(catalog[0].json))
let viewport: Viewport | null = null

const undoStack: VoxelModel[] = []
const redoStack: VoxelModel[] = []
const undoCount = ref(0)
const redoCount = ref(0)
const canUndo = computed(() => undoCount.value > 0)
const canRedo = computed(() => redoCount.value > 0)

function headLines(): number[] {
  const corners = [
    [-0.25, -0.5, -0.25],
    [-0.25, -0.5, 0.25],
    [-0.25, 0, -0.25],
    [-0.25, 0, 0.25],
    [0.25, -0.5, -0.25],
    [0.25, -0.5, 0.25],
    [0.25, 0, -0.25],
    [0.25, 0, 0.25],
  ]
  const edges = [
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
  const lines: number[] = []
  for (const [first, second] of edges) {
    lines.push(...corners[first], ...corners[second])
  }
  return lines
}

const HEAD = new Float32Array(headLines())

function floorLines(): Float32Array {
  const resolution = model.value.resolution
  const half = resolution / 2
  const unit = 1 / resolution
  const y = -0.5
  const lines: number[] = []
  for (let index = -half; index <= half; index++) {
    lines.push(index * unit, y, -half * unit, index * unit, y, half * unit)
    lines.push(-half * unit, y, index * unit, half * unit, y, index * unit)
  }
  return new Float32Array(lines)
}

function rebuild() {
  if (!viewport) return
  const current = model.value
  const mesh = current.mesh(sliceEnabled.value ? slice.value : null)
  const floor = floorLines()
  const lines = new Float32Array(mesh.lines.length + HEAD.length + floor.length)
  lines.set(mesh.lines)
  lines.set(HEAD, mesh.lines.length)
  lines.set(floor, mesh.lines.length + HEAD.length)
  viewport.setMesh({ ...mesh, lines })
  faceCount.value = mesh.faces
  voxelCount.value = current.cells.size
  viewport.gridVisible = grid.value
  viewport.draw()
}

function resetCamera() {
  viewport?.setCenter(center(), radius())
}

function center(): [number, number, number] {
  const bounds = model.value.bounds()
  return [
    (bounds.min[0] + bounds.max[0]) / (2 * model.value.resolution),
    (bounds.min[1] + bounds.max[1]) / (2 * model.value.resolution),
    (bounds.min[2] + bounds.max[2]) / (2 * model.value.resolution),
  ]
}

function radius() {
  const bounds = model.value.bounds()
  return (
    Math.max(
      bounds.max[0] - bounds.min[0],
      bounds.max[1] - bounds.min[1],
      bounds.max[2] - bounds.min[2],
    ) / model.value.resolution
  )
}

function updateCounts() {
  undoCount.value = undoStack.length
  redoCount.value = redoStack.length
}

function pushUndo() {
  if (undoStack.length >= 40) undoStack.shift()
  undoStack.push(model.value.clone())
  redoStack.length = 0
  updateCounts()
}

function undo() {
  const previous = undoStack.pop()
  if (!previous) return
  redoStack.push(model.value.clone())
  model.value = previous
  updateCounts()
  rebuild()
}

function redo() {
  const next = redoStack.pop()
  if (!next) return
  undoStack.push(model.value.clone())
  model.value = next
  updateCounts()
  rebuild()
}

function loadStyle(index: number) {
  model.value = VoxelModel.fromJson(catalog[index].json)
  resolution.value = model.value.resolution
  undoStack.length = 0
  redoStack.length = 0
  updateCounts()
  rebuild()
  resetCamera()
  viewport?.draw()
}

function floorCell() {
  return -model.value.resolution / 2
}

function pick(event: PointerEvent) {
  if (!viewport) return null
  const ray = viewport.ray(event.clientX, event.clientY)
  return {
    ray,
    hit: model.value.pick(ray.origin, ray.direction, floorCell()),
  }
}

function paintCell(x: number, y: number, z: number, index: number) {
  model.value.set(x, y, z, index)
  if (mirror.value) model.value.set(-x - 1, y, z, index)
}

function eraseCell(x: number, y: number, z: number) {
  model.value.remove(x, y, z)
  if (mirror.value) model.value.remove(-x - 1, y, z)
}

function applyTool(hit: Hit) {
  const current = model.value
  if (hit.placeholder) {
    if (tool.value === 'erase' || tool.value === 'pick') return
    paintCell(hit.x, hit.y, hit.z, current.indexOf(color.value))
    return
  }
  if (tool.value === 'pick') {
    const index = current.colorIndex(hit.x, hit.y, hit.z)
    if (index !== undefined) color.value = current.palette[index]
    return
  }
  const index = current.indexOf(color.value)
  if (tool.value === 'paint') {
    paintCell(hit.x, hit.y, hit.z, index)
  } else if (tool.value === 'erase') {
    eraseCell(hit.x, hit.y, hit.z)
  } else if (tool.value === 'add') {
    const x = hit.x + hit.nx
    const y = hit.y + hit.ny
    const z = hit.z + hit.nz
    if (sliceEnabled.value && y !== slice.value) return
    if (!current.has(x, y, z)) paintCell(x, y, z, index)
  }
}

let painting = false
let orbiting = false
let lastX = 0
let lastY = 0
let lastCell = ''

function onPointerDown(event: PointerEvent) {
  if (!viewport) return
  canvas.value?.setPointerCapture(event.pointerId)
  if (event.button !== 0) {
    orbiting = true
    lastX = event.clientX
    lastY = event.clientY
    return
  }
  if (event.altKey) {
    const result = pick(event)
    if (result?.hit) applyTool({ ...result.hit })
    return
  }
  const result = pick(event)
  if (!result?.hit) return
  pushUndo()
  painting = true
  lastCell = `${result.hit.x},${result.hit.y},${result.hit.z}`
  applyTool(result.hit)
  rebuild()
}

function onPointerMove(event: PointerEvent) {
  if (orbiting) {
    viewport?.orbit(event.clientX - lastX, event.clientY - lastY)
    lastX = event.clientX
    lastY = event.clientY
    viewport?.draw()
    return
  }
  if (!painting) return
  const result = pick(event)
  if (!result?.hit) return
  const cell = `${result.hit.x},${result.hit.y},${result.hit.z}`
  if (cell === lastCell) return
  lastCell = cell
  applyTool(result.hit)
  rebuild()
}

function onPointerUp() {
  painting = false
  orbiting = false
  lastCell = ''
}

function onWheel(event: WheelEvent) {
  event.preventDefault()
  viewport?.zoom(event.deltaY > 0 ? 1.1 : 1 / 1.1)
  viewport?.draw()
}

function changeResolution(value: number) {
  model.value = model.value.resample(value)
  resolution.value = value
  undoStack.length = 0
  redoStack.length = 0
  updateCounts()
  rebuild()
  resetCamera()
  viewport?.draw()
}

function exportJson() {
  const data = JSON.stringify(model.value.toJson(), null, 2)
  const blob = new Blob([`${data}\n`], { type: 'application/json' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${catalog[styleIndex.value].id}.json`
  link.click()
  URL.revokeObjectURL(link.href)
}

function importJson(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const parsed = JSON.parse(String(reader.result))
      if (parsed.kind !== 'voxels') {
        status.value = t('This is not a voxel model.')
        return
      }
      pushUndo()
      model.value = VoxelModel.fromJson(parsed)
      resolution.value = model.value.resolution
      status.value = ''
      rebuild()
    } catch {
      status.value = t('Could not read the file.')
    }
  }
  reader.readAsText(file)
  input.value = ''
}

function onKeyDown(event: KeyboardEvent) {
  if (!event.ctrlKey && !event.metaKey) return
  if (event.key.toLowerCase() === 'z') {
    event.preventDefault()
    if (event.shiftKey) redo()
    else undo()
  } else if (event.key.toLowerCase() === 'y') {
    event.preventDefault()
    redo()
  }
}

function onResize() {
  viewport?.resize()
  viewport?.draw()
}

watch(grid, (value) => {
  if (viewport) viewport.gridVisible = value
  viewport?.draw()
})
watch([sliceEnabled, slice], rebuild)
watch(resolution, (value) => {
  if (value !== model.value.resolution) changeResolution(value)
})

onMounted(() => {
  if (!canvas.value) return
  try {
    viewport = new Viewport(canvas.value)
  } catch (error) {
    status.value = String(error)
    return
  }
  viewport.resize()
  rebuild()
  resetCamera()
  viewport.draw()
  window.addEventListener('resize', onResize)
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <div class="cosmetic">
    <header class="cosmetic-bar">
      <a class="cosmetic-back" href="./index.html">
        <ArrowLeft :size="16" aria-hidden="true" />
        {{ t('Back') }}
      </a>
      <h1>{{ t('Cat ears') }}</h1>
      <label class="cosmetic-field">
        {{ t('Style') }}
        <select v-model.number="styleIndex" @change="loadStyle(styleIndex)">
          <option
            v-for="(entry, index) in catalog"
            :key="entry.id"
            :value="index"
          >
            {{ entry.id }}
          </option>
        </select>
      </label>
      <label class="cosmetic-field">
        {{ t('Resolution') }}
        <select v-model.number="resolution">
          <option :value="8">8</option>
          <option :value="16">16</option>
          <option :value="32">32</option>
        </select>
      </label>
      <label class="cosmetic-field">
        <input v-model="grid" type="checkbox" />
        {{ t('Grid') }}
      </label>
      <label class="cosmetic-field">
        <input v-model="mirror" type="checkbox" />
        {{ t('Mirror') }}
      </label>
      <span class="cosmetic-status">{{ status }}</span>
    </header>

    <main class="cosmetic-body">
      <aside class="cosmetic-tools">
        <button
          type="button"
          :class="{ active: tool === 'paint' }"
          @click="tool = 'paint'"
        >
          <Pencil :size="16" aria-hidden="true" />
          {{ t('Paint') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'erase' }"
          @click="tool = 'erase'"
        >
          <Eraser :size="16" aria-hidden="true" />
          {{ t('Erase') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'add' }"
          @click="tool = 'add'"
        >
          <Boxes :size="16" aria-hidden="true" />
          {{ t('Add') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'pick' }"
          @click="tool = 'pick'"
        >
          <Pipette :size="16" aria-hidden="true" />
          {{ t('Pick color') }}
        </button>

        <label class="cosmetic-color">
          {{ t('Color') }}
          <input v-model="color" type="color" />
        </label>
        <div class="cosmetic-palette">
          <button
            v-for="swatch in palette"
            :key="swatch"
            type="button"
            class="cosmetic-swatch"
            :style="{ background: swatch }"
            :title="swatch"
            @click="color = swatch"
          />
        </div>

        <label class="cosmetic-slice">
          <span>
            <input v-model="sliceEnabled" type="checkbox" />
            {{ t('Slice') }}
          </span>
          <input
            v-model.number="slice"
            type="range"
            min="-16"
            max="0"
            step="1"
          />
        </label>

        <div class="cosmetic-actions">
          <button type="button" :disabled="!canUndo" @click="undo">
            <Undo2 :size="16" aria-hidden="true" />
            {{ t('Undo') }}
          </button>
          <button type="button" :disabled="!canRedo" @click="redo">
            <Redo2 :size="16" aria-hidden="true" />
            {{ t('Redo') }}
          </button>
          <button type="button" @click="loadStyle(styleIndex)">
            <RotateCcw :size="16" aria-hidden="true" />
            {{ t('Reset') }}
          </button>
          <button type="button" @click="exportJson">
            <Download :size="16" aria-hidden="true" />
            {{ t('Export JSON') }}
          </button>
          <button type="button" @click="fileInput?.click()">
            <Upload :size="16" aria-hidden="true" />
            {{ t('Import JSON') }}
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="application/json"
            hidden
            @change="importJson"
          />
        </div>
      </aside>

      <section class="cosmetic-canvas">
        <canvas
          ref="canvas"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @wheel="onWheel"
          @contextmenu.prevent
        />
      </section>

      <aside class="cosmetic-preview">
        <p class="cosmetic-stats">
          <FlipHorizontal2 :size="14" aria-hidden="true" />
          {{ t('Drag to paint, right-drag to rotate, wheel to zoom.') }}
        </p>
        <p class="cosmetic-stats">
          <Grid3x3 :size="14" aria-hidden="true" />
          {{ t('{0} voxels, {1} faces', voxelCount, faceCount) }}
        </p>
        <p class="cosmetic-stats">{{ t('Resolution {0}', resolution) }}</p>
      </aside>
    </main>
  </div>
</template>
