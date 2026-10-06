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
import { computed, ref, watch } from 'vue'
import PickerMenu from './PickerMenu.vue'
import UiNotice from './UiNotice.vue'
import { fromArgb, parseColor, toArgb, toHex, toHsva, toRgba } from './color'

const props = defineProps<{ value: number; label: string; busy: boolean }>()
const emit = defineEmits<{ change: [value: number] }>()
const area = ref<HTMLDivElement | null>(null)
const textInput = ref<HTMLInputElement | null>(null)
const draft = ref(toHsva(fromArgb(props.value)))
const format = ref<'Hex' | 'RGBA' | 'HSLA'>('Hex')
const invalid = ref(false)
const pointer = ref<number | null>(null)
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
  if (pointer.value !== event.pointerId || props.busy || !area.value) return
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
  if (props.busy || event.button !== 0 || pointer.value !== null || !area.value)
    return
  pointer.value = event.pointerId
  area.value.setPointerCapture(pointer.value)
  area.value.querySelector('input')?.focus({ preventScroll: true })
  movePointer(event)
}

function finishPointer(event: PointerEvent) {
  if (pointer.value !== event.pointerId) return
  movePointer(event)
  pointer.value = null
  publish()
}

function cancelPointer() {
  const captured = pointer.value
  pointer.value = null
  if (captured !== null && area.value?.hasPointerCapture(captured))
    area.value.releasePointerCapture(captured)
  draft.value = toHsva(fromArgb(props.value))
  syncText()
}

function beforeClose(reason: string) {
  if (reason === 'outside' && document.activeElement === textInput.value)
    commitText()
}
</script>

<template>
  <PickerMenu
    class="color-picker"
    :label="label"
    :busy="busy"
    :hold-focus="pointer !== null"
    @open="syncText"
    @before-close="beforeClose"
    @close="cancelPointer"
  >
    <template #trigger>
      <span class="color-preview" :style="{ '--picked-color': preview }" />
      <span class="color-value">{{ toHex(rgba) }}</span>
    </template>
    <div class="color-panel menu-panel">
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
          class="range-slider color-slider color-hue"
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
          class="range-slider color-slider color-alpha"
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
      <div
        class="color-formats segmented"
        role="group"
        :aria-label="t('Color format')"
      >
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
      <UiNotice v-if="invalid" variant="text" class="color-error">{{
        t('Enter a valid Hex, RGBA or HSLA color.')
      }}</UiNotice>
    </div>
  </PickerMenu>
</template>
