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
  Download,
  Eraser,
  PaintBucket,
  Pencil,
  Pipette,
  Redo2,
  RotateCcw,
  Undo2,
  Upload,
} from '@lucide/vue'
import { t } from '../languages'
import { STYLES, faceRects, regionRect, type Model } from './model'
import { drawPreview } from './preview'
import {
  colorToHex,
  floodFill,
  hexToColor,
  paintPixel,
  readPixel,
} from './pixels'

type Tool = 'pencil' | 'eraser' | 'fill' | 'picker'

const palette = [
  '#ffffff',
  '#d8d8d8',
  '#9a9a9a',
  '#2b2b2b',
  '#000000',
  '#e58aa0',
  '#f2b8c6',
  '#c9a227',
  '#7fc8a9',
  '#6fa8dc',
]

const styleIndex = ref(0)
const tool = ref<Tool>('pencil')
const color = ref('#ffffff')
const zoom = ref(8)
const showUv = ref(true)
const status = ref('')

const paintCanvas = ref<HTMLCanvasElement | null>(null)
const previewCanvas = ref<HTMLCanvasElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const model = shallowRef<Model>(STYLES[0].model)
const textureSize = ref(STYLES[0].model.textureSize[0])
const pixels = shallowRef<Uint8ClampedArray>(new Uint8ClampedArray(0))

const yaw = ref(0.6)
const pitch = ref(0.28)

const undoStack: Uint8ClampedArray[] = []
const redoStack: Uint8ClampedArray[] = []
const undoCount = ref(0)
const redoCount = ref(0)

const canUndo = computed(() => undoCount.value > 0)
const canRedo = computed(() => redoCount.value > 0)

function updateCounts() {
  undoCount.value = undoStack.length
  redoCount.value = redoStack.length
}

async function loadStyle(index: number) {
  const style = STYLES[index]
  model.value = style.model
  const size = style.model.textureSize[0]
  textureSize.value = size
  const image = new Image()
  image.src = style.texture
  await image.decode()
  const source = document.createElement('canvas')
  source.width = size
  source.height = size
  const context = source.getContext('2d')
  if (!context) return
  context.drawImage(image, 0, 0)
  pixels.value = context.getImageData(0, 0, size, size).data
  undoStack.length = 0
  redoStack.length = 0
  updateCounts()
  render()
  renderPreview()
}

function render() {
  const element = paintCanvas.value
  const size = textureSize.value
  if (!element || !pixels.value.length) return
  const scale = zoom.value
  element.width = size * scale
  element.height = size * scale
  const context = element.getContext('2d')
  if (!context) return
  context.fillStyle = '#1a1d20'
  context.fillRect(0, 0, element.width, element.height)
  context.fillStyle = '#22262a'
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if ((x + y) % 2 === 1) {
        context.fillRect(x * scale, y * scale, scale, scale)
      }
    }
  }
  const offscreen = document.createElement('canvas')
  offscreen.width = size
  offscreen.height = size
  const offscreenContext = offscreen.getContext('2d')
  if (!offscreenContext) return
  offscreenContext.putImageData(
    new ImageData(new Uint8ClampedArray(pixels.value), size, size),
    0,
    0,
  )
  context.imageSmoothingEnabled = false
  context.drawImage(offscreen, 0, 0, size * scale, size * scale)
  context.strokeStyle = 'rgba(255, 255, 255, 0.08)'
  context.lineWidth = 1
  for (let index = 0; index <= size; index++) {
    context.beginPath()
    context.moveTo(index * scale + 0.5, 0)
    context.lineTo(index * scale + 0.5, size * scale)
    context.stroke()
    context.beginPath()
    context.moveTo(0, index * scale + 0.5)
    context.lineTo(size * scale, index * scale + 0.5)
    context.stroke()
  }
  if (showUv.value) drawUv(context, scale)
}

const UV_COLORS = [
  '#ff8a80',
  '#ffd180',
  '#ffff8d',
  '#b9f6ca',
  '#80d8ff',
  '#ea80fc',
]

function drawUv(context: CanvasRenderingContext2D, scale: number) {
  for (const part of model.value.parts) {
    const [u, v, width, height] = regionRect(part)
    context.strokeStyle = part.inner
      ? 'rgba(255, 150, 190, 0.9)'
      : 'rgba(120, 200, 255, 0.6)'
    context.strokeRect(
      u * scale + 0.5,
      v * scale + 0.5,
      width * scale - 1,
      height * scale - 1,
    )
    faceRects(part).forEach((rect, index) => {
      context.strokeStyle = `${UV_COLORS[index]}80`
      context.strokeRect(
        rect[0] * scale + 0.5,
        rect[1] * scale + 0.5,
        rect[2] * scale - 1,
        rect[3] * scale - 1,
      )
    })
  }
}

function renderPreview() {
  const element = previewCanvas.value
  if (!element || !pixels.value.length) return
  drawPreview(
    element,
    model.value,
    pixels.value,
    textureSize.value,
    yaw.value,
    pitch.value,
  )
}

function pushUndo() {
  if (undoStack.length >= 40) undoStack.shift()
  undoStack.push(pixels.value.slice())
  redoStack.length = 0
  updateCounts()
}

function undo() {
  const previous = undoStack.pop()
  if (!previous) return
  redoStack.push(pixels.value.slice())
  pixels.value = previous
  updateCounts()
  render()
  renderPreview()
}

function redo() {
  const next = redoStack.pop()
  if (!next) return
  undoStack.push(pixels.value.slice())
  pixels.value = next
  updateCounts()
  render()
  renderPreview()
}

function pixelAt(event: PointerEvent): [number, number] | null {
  const element = paintCanvas.value
  if (!element) return null
  const bounds = element.getBoundingClientRect()
  const size = textureSize.value
  const x = Math.floor(((event.clientX - bounds.left) / bounds.width) * size)
  const y = Math.floor(((event.clientY - bounds.top) / bounds.height) * size)
  if (x < 0 || y < 0 || x >= size || y >= size) return null
  return [x, y]
}

function applyTool(x: number, y: number) {
  const size = textureSize.value
  if (tool.value === 'picker') {
    color.value = colorToHex(readPixel(pixels.value, size, x, y))
    return
  }
  if (tool.value === 'fill') {
    floodFill(pixels.value, size, x, y, hexToColor(color.value))
  } else {
    const rgb = hexToColor(color.value)
    paintPixel(
      pixels.value,
      size,
      x,
      y,
      tool.value === 'eraser' ? [0, 0, 0, 0] : rgb,
    )
  }
  render()
  renderPreview()
}

let drawing = false
let lastPoint: [number, number] | null = null

function stroke(from: [number, number], to: [number, number]) {
  const steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]))
  for (let step = 0; step <= steps; step++) {
    const ratio = steps === 0 ? 0 : step / steps
    const x = Math.round(from[0] + (to[0] - from[0]) * ratio)
    const y = Math.round(from[1] + (to[1] - from[1]) * ratio)
    applyTool(x, y)
  }
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  const point = pixelAt(event)
  if (!point) return
  if (tool.value === 'picker') {
    applyTool(point[0], point[1])
    return
  }
  pushUndo()
  drawing = true
  lastPoint = point
  ;(event.target as HTMLCanvasElement).setPointerCapture(event.pointerId)
  applyTool(point[0], point[1])
}

function onPointerMove(event: PointerEvent) {
  if (!drawing) return
  const point = pixelAt(event)
  if (!point) return
  if (lastPoint) stroke(lastPoint, point)
  else applyTool(point[0], point[1])
  lastPoint = point
}

function onPointerUp() {
  drawing = false
  lastPoint = null
}

let rotating = false
let rotateX = 0
let rotateY = 0

function onPreviewDown(event: PointerEvent) {
  rotating = true
  rotateX = event.clientX
  rotateY = event.clientY
  ;(event.target as HTMLCanvasElement).setPointerCapture(event.pointerId)
}

function onPreviewMove(event: PointerEvent) {
  if (!rotating) return
  yaw.value += (event.clientX - rotateX) * 0.01
  pitch.value = Math.max(
    -1.2,
    Math.min(1.2, pitch.value + (event.clientY - rotateY) * 0.01),
  )
  rotateX = event.clientX
  rotateY = event.clientY
  renderPreview()
}

function onPreviewUp() {
  rotating = false
}

function exportPng() {
  const size = textureSize.value
  const element = document.createElement('canvas')
  element.width = size
  element.height = size
  const context = element.getContext('2d')
  if (!context) return
  context.putImageData(
    new ImageData(new Uint8ClampedArray(pixels.value), size, size),
    0,
    0,
  )
  element.toBlob((blob) => {
    if (!blob) return
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `${STYLES[styleIndex.value].id}.png`
    link.click()
    URL.revokeObjectURL(link.href)
  })
}

function importPng(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const size = textureSize.value
  const image = new Image()
  image.onload = () => {
    if (image.width !== size || image.height !== size) {
      status.value = t('The image must be {0}x{1} pixels.', size, size)
      return
    }
    const element = document.createElement('canvas')
    element.width = size
    element.height = size
    const context = element.getContext('2d')
    if (!context) return
    context.drawImage(image, 0, 0)
    pushUndo()
    pixels.value = context.getImageData(0, 0, size, size).data
    status.value = ''
    render()
    renderPreview()
  }
  image.src = URL.createObjectURL(file)
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

watch([zoom, showUv], render)

onMounted(() => {
  void loadStyle(0)
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
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
            v-for="(style, index) in STYLES"
            :key="style.id"
            :value="index"
          >
            {{ style.id }}
          </option>
        </select>
      </label>
      <label class="cosmetic-field">
        {{ t('Zoom') }}
        <input v-model.number="zoom" type="range" min="4" max="24" step="1" />
      </label>
      <label class="cosmetic-field">
        <input v-model="showUv" type="checkbox" />
        {{ t('UV guides') }}
      </label>
      <span class="cosmetic-status">{{ status }}</span>
    </header>

    <main class="cosmetic-body">
      <aside class="cosmetic-tools">
        <button
          type="button"
          :class="{ active: tool === 'pencil' }"
          :title="t('Pencil')"
          @click="tool = 'pencil'"
        >
          <Pencil :size="16" aria-hidden="true" />
          {{ t('Pencil') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'eraser' }"
          :title="t('Eraser')"
          @click="tool = 'eraser'"
        >
          <Eraser :size="16" aria-hidden="true" />
          {{ t('Eraser') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'fill' }"
          :title="t('Fill')"
          @click="tool = 'fill'"
        >
          <PaintBucket :size="16" aria-hidden="true" />
          {{ t('Fill') }}
        </button>
        <button
          type="button"
          :class="{ active: tool === 'picker' }"
          :title="t('Eyedropper')"
          @click="tool = 'picker'"
        >
          <Pipette :size="16" aria-hidden="true" />
          {{ t('Eyedropper') }}
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
          <button type="button" @click="exportPng">
            <Download :size="16" aria-hidden="true" />
            {{ t('Export PNG') }}
          </button>
          <button type="button" @click="fileInput?.click()">
            <Upload :size="16" aria-hidden="true" />
            {{ t('Import PNG') }}
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="image/png"
            hidden
            @change="importPng"
          />
        </div>
      </aside>

      <section class="cosmetic-canvas">
        <canvas
          ref="paintCanvas"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
        />
      </section>

      <section class="cosmetic-preview">
        <canvas
          ref="previewCanvas"
          width="360"
          height="360"
          @pointerdown="onPreviewDown"
          @pointermove="onPreviewMove"
          @pointerup="onPreviewUp"
          @pointercancel="onPreviewUp"
        />
        <p>{{ t('Drag the preview to rotate the model.') }}</p>
      </section>
    </main>
  </div>
</template>
