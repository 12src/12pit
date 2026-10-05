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
import { t } from './languages'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { fromArgb, parseColor, toArgb, toHex, toHsva, toRgba } from './color'

const props = defineProps<{ value: number; label: string; busy: boolean }>()
const emit = defineEmits<{ change: [value: number] }>()
const menu = ref<HTMLDetailsElement | null>(null)
const area = ref<HTMLDivElement | null>(null)
const textInput = ref<HTMLInputElement | null>(null)
const draft = ref(toHsva(fromArgb(props.value)))
const format = ref<'Hex' | 'RGBA' | 'HSLA'>('Hex')
const invalid = ref(false)
let pointer: number | null = null
const rgba = computed(() => toRgba(draft.value))
const preview = computed(
  () =>
    `rgba(${rgba.value.r}, ${rgba.value.g}, ${rgba.value.b}, ${rgba.value.a})`,
)
const opaque = computed(
  () => `rgb(${rgba.value.r}, ${rgba.value.g}, ${rgba.value.b})`,
)
const formatted = computed(() => {
  const color = rgba.value
  if (format.value === 'Hex') return toHex(color)
  const alpha = Number(color.a.toFixed(3))
  if (format.value === 'RGBA')
    return `rgba(${color.r}, ${color.g}, ${color.b}, ${alpha})`
  const lightness = draft.value.v * (1 - draft.value.s / 2)
  const saturation =
    lightness === 0 || lightness === 1
      ? 0
      : (draft.value.v - lightness) / Math.min(lightness, 1 - lightness)
  return `hsla(${Number(draft.value.h.toFixed(2))}, ${Number((saturation * 100).toFixed(2))}%, ${Number((lightness * 100).toFixed(2))}%, ${alpha})`
})
const text = ref(formatted.value)

function syncText() {
  text.value = formatted.value
  invalid.value = false
}

watch(
  () => props.value,
  (value) => {
    if (value === toArgb(rgba.value)) return
    draft.value = toHsva(fromArgb(value))
    if (document.activeElement !== textInput.value) syncText()
  },
)
watch(format, syncText)
watch(
  () => props.busy,
  (busy) => {
    if (!busy) return
    cancelPointer()
    if (menu.value) menu.value.open = false
  },
)

function publish() {
  if (props.busy) return
  syncText()
  const value = toArgb(rgba.value)
  if (value !== props.value) emit('change', value)
}

function commitText() {
  if (props.busy) return
  const color = parseColor(text.value)
  if (!color) {
    invalid.value = true
    return
  }
  const next = toHsva(color)
  if (next.s === 0) next.h = draft.value.h
  draft.value = next
  publish()
}

function resetText() {
  syncText()
  textInput.value?.blur()
}

function setChannel(channel: 'h' | 's' | 'v' | 'a', event: Event) {
  if (props.busy) return
  const value = Number((event.target as HTMLInputElement).value)
  draft.value[channel] =
    channel === 'h' ? value % 360 : value / (channel === 'a' ? 255 : 100)
  syncText()
}

function movePointer(event: PointerEvent) {
  if (pointer !== event.pointerId || props.busy || !area.value) return
  const bounds = area.value.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  draft.value.s = Math.max(
    0,
    Math.min(1, (event.clientX - bounds.left) / bounds.width),
  )
  draft.value.v =
    1 - Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height))
  syncText()
}

function startPointer(event: PointerEvent) {
  if (props.busy || event.button !== 0 || pointer !== null || !area.value)
    return
  pointer = event.pointerId
  area.value.setPointerCapture(pointer)
  area.value.querySelector('input')?.focus({ preventScroll: true })
  movePointer(event)
}

function finishPointer(event: PointerEvent) {
  if (pointer !== event.pointerId) return
  movePointer(event)
  pointer = null
  publish()
}

function cancelPointer() {
  const captured = pointer
  pointer = null
  if (captured !== null && area.value?.hasPointerCapture(captured))
    area.value.releasePointerCapture(captured)
  draft.value = toHsva(fromArgb(props.value))
  syncText()
}

function closeMenu() {
  cancelPointer()
  if (menu.value) menu.value.open = false
}

function closeOutside(event: PointerEvent) {
  if (menu.value?.open && !menu.value.contains(event.target as Node)) {
    if (document.activeElement === textInput.value) commitText()
    closeMenu()
  }
}

function closeOnBlur(event: FocusEvent) {
  if (
    pointer === null &&
    !menu.value?.contains(event.relatedTarget as Node | null)
  )
    closeMenu()
}

function escapeMenu(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !menu.value?.open) return
  event.preventDefault()
  event.stopPropagation()
  closeMenu()
  menu.value.querySelector('summary')?.focus()
}

onMounted(() => document.addEventListener('pointerdown', closeOutside))
onUnmounted(() => document.removeEventListener('pointerdown', closeOutside))
</script>

<template>
  <details
    ref="menu"
    class="choice-picker color-picker"
    :class="{ busy }"
    @focusout="closeOnBlur"
    @keydown="escapeMenu"
    @toggle="menu?.open ? syncText() : cancelPointer()"
  >
    <summary
      :aria-label="label"
      :aria-disabled="busy"
      :tabindex="busy ? -1 : 0"
    >
      <span class="color-preview" :style="{ '--picked-color': preview }" />
      <span class="color-value">{{ toHex(rgba) }}</span>
      <ChevronDown :size="15" />
    </summary>
    <div class="color-panel">
      <div
        ref="area"
        class="color-area"
        role="group"
        :aria-label="t('Saturation and brightness')"
        :style="{ '--picked-hue': `hsl(${draft.h}, 100%, 50%)` }"
        @pointerdown.prevent="startPointer"
        @pointermove="movePointer"
        @pointerup="finishPointer"
        @pointercancel="cancelPointer"
        @lostpointercapture="pointer !== null && cancelPointer()"
      >
        <span
          class="color-point"
          :style="{ left: `${draft.s * 100}%`, top: `${(1 - draft.v) * 100}%` }"
        />
        <input
          class="color-area-input"
          type="range"
          :aria-label="t('Saturation')"
          min="0"
          max="100"
          step="1"
          :value="draft.s * 100"
          :disabled="busy"
          @input="setChannel('s', $event)"
          @change="publish"
        />
        <input
          class="color-area-input"
          type="range"
          :aria-label="t('Brightness')"
          min="0"
          max="100"
          step="1"
          :value="draft.v * 100"
          :disabled="busy"
          @input="setChannel('v', $event)"
          @change="publish"
        />
      </div>
      <label class="color-slider-label">
        <span>{{ t('Hue') }}</span>
        <input
          class="color-slider color-hue"
          type="range"
          :aria-label="t('Hue')"
          min="0"
          max="359"
          step="1"
          :value="draft.h"
          :disabled="busy"
          @input="setChannel('h', $event)"
          @change="publish"
        />
      </label>
      <label class="color-slider-label">
        <span>{{ t('Opacity') }}</span>
        <input
          class="color-slider color-alpha"
          type="range"
          :aria-label="t('Opacity')"
          min="0"
          max="255"
          step="1"
          :value="Math.round(draft.a * 255)"
          :aria-valuetext="`${Math.round(draft.a * 100)}%`"
          :disabled="busy"
          :style="{ '--opaque-color': opaque }"
          @input="setChannel('a', $event)"
          @change="publish"
        />
      </label>
      <div class="color-formats" role="group" :aria-label="t('Color format')">
        <button
          v-for="name in ['Hex', 'RGBA', 'HSLA'] as const"
          :key="name"
          type="button"
          :aria-pressed="format === name"
          :disabled="busy"
          @click="format = name"
        >
          {{ name }}
        </button>
      </div>
      <input
        ref="textInput"
        v-model="text"
        class="number-input color-text"
        type="text"
        :aria-label="`${label} ${format} value`"
        :aria-invalid="invalid"
        :disabled="busy"
        spellcheck="false"
        autocomplete="off"
        @input="invalid = false"
        @change="commitText"
        @keydown.enter.prevent="textInput?.blur()"
        @keydown.esc.prevent.stop="resetText"
      />
      <span v-if="invalid" class="color-error" role="alert">{{
        t('Enter a valid Hex, RGBA or HSLA color.')
      }}</span>
    </div>
  </details>
</template>

<style scoped>
.color-picker summary {
  justify-content: flex-start;
}
.color-preview {
  position: relative;
  flex: none;
  width: 22px;
  height: 22px;
  overflow: hidden;
  border: 1px solid #ffffff33;
  border-radius: 4px;
  background: conic-gradient(
      #40464b 25%,
      #7a8186 0 50%,
      #40464b 0 75%,
      #7a8186 0
    )
    0 0 / 8px 8px;
}
.color-preview::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--picked-color);
}
.color-value {
  flex: 1;
  font-variant-numeric: tabular-nums;
}
.color-panel {
  position: absolute;
  z-index: 3;
  top: calc(100% + 4px);
  right: 0;
  display: grid;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--panel);
  box-shadow: 0 8px 20px #0008;
  animation: menu-enter 0.15s ease-out both;
}
.color-area {
  position: relative;
  height: 146px;
  border: 1px solid var(--control-border);
  border-radius: 4px;
  background:
    linear-gradient(to top, #000, transparent),
    linear-gradient(to right, #fff, transparent), var(--picked-hue);
  cursor: crosshair;
  touch-action: none;
}
.color-area:focus-within {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.color-point {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #0009;
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.color-area-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
}
.color-slider-label {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}
.color-slider {
  appearance: none;
  width: 100%;
  height: 28px;
  margin: 0;
  background: transparent;
  cursor: pointer;
}
.color-hue {
  --color-track: linear-gradient(
    to right,
    #f00,
    #ff0,
    #0f0,
    #0ff,
    #00f,
    #f0f,
    #f00
  );
}
.color-alpha {
  --color-track:
    linear-gradient(to right, transparent, var(--opaque-color)),
    conic-gradient(#40464b 25%, #7a8186 0 50%, #40464b 0 75%, #7a8186 0);
  --color-track-size: auto, 8px 8px;
}
.color-slider::-webkit-slider-runnable-track {
  height: 4px;
  border-radius: 4px;
  background-image: var(--color-track);
  background-size: var(--color-track-size, auto);
}
.color-slider::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  margin-top: -5px;
  border: 3px solid var(--accent);
  border-radius: 50%;
  background: var(--text);
  box-shadow: 0 0 0 2px var(--bg);
}
.color-slider::-moz-range-track {
  height: 4px;
  border-radius: 4px;
  background-image: var(--color-track);
  background-size: var(--color-track-size, auto);
}
.color-slider::-moz-range-thumb {
  width: 8px;
  height: 8px;
  border: 3px solid var(--accent);
  border-radius: 50%;
  background: var(--text);
}
.color-formats {
  display: flex;
  padding: 3px;
  border: 1px solid var(--control-border);
  border-radius: 5px;
}
.color-formats button {
  flex: 1;
  min-height: 34px;
  padding: 5px 4px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
}
.color-formats button:hover:not(:disabled) {
  background: var(--hover);
  color: var(--text);
}
.color-formats button[aria-pressed='true'] {
  background: var(--wash);
  color: var(--accent);
}
.color-text {
  width: 100%;
  min-width: 0;
  text-align: left;
}
.color-text[aria-invalid='true'],
.color-text[aria-invalid='true']:hover,
.color-text[aria-invalid='true']:focus {
  border-color: var(--danger);
}
.color-error {
  color: var(--danger);
  font-size: 12px;
}
@media (prefers-reduced-motion: reduce) {
  .color-panel {
    animation: none;
  }
}
</style>
