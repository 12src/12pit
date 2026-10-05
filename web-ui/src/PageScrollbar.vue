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
import { computed, onMounted, onUnmounted, ref } from 'vue'

const viewportHeight = ref(document.documentElement.clientHeight)
const contentHeight = ref(document.documentElement.scrollHeight)
const scrollTop = ref(document.documentElement.scrollTop)
const dragging = ref(false)
const maximum = computed(() =>
  Math.max(0, contentHeight.value - viewportHeight.value),
)
const thumbHeight = computed(() =>
  Math.min(
    viewportHeight.value,
    Math.max(
      28,
      (viewportHeight.value * viewportHeight.value) / contentHeight.value,
    ),
  ),
)
const thumbTop = computed(() =>
  maximum.value === 0
    ? 0
    : (scrollTop.value / maximum.value) *
      (viewportHeight.value - thumbHeight.value),
)
let pointerOffset = 0
let observer: ResizeObserver | undefined

function update() {
  viewportHeight.value = document.documentElement.clientHeight
  contentHeight.value = document.documentElement.scrollHeight
  scrollTop.value = document.documentElement.scrollTop
}

function move(event: PointerEvent) {
  if (!(event.currentTarget as HTMLElement).hasPointerCapture(event.pointerId))
    return
  const travel = viewportHeight.value - thumbHeight.value
  if (travel === 0) return
  const position = Math.min(travel, Math.max(0, event.clientY - pointerOffset))
  window.scrollTo(window.scrollX, (position / travel) * maximum.value)
}

function startDrag(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0) return
  event.preventDefault()
  const track = event.currentTarget as HTMLElement
  track.focus({ preventScroll: true })
  track.setPointerCapture(event.pointerId)
  pointerOffset =
    event.clientY >= thumbTop.value &&
    event.clientY <= thumbTop.value + thumbHeight.value
      ? event.clientY - thumbTop.value
      : thumbHeight.value / 2
  dragging.value = true
  move(event)
}

function stopDrag(event: PointerEvent) {
  const track = event.currentTarget as HTMLElement
  if (track.hasPointerCapture(event.pointerId)) {
    track.releasePointerCapture(event.pointerId)
    dragging.value = false
  }
}

function onKey(event: KeyboardEvent) {
  let top = scrollTop.value
  switch (event.key) {
    case 'ArrowUp':
      top -= 40
      break
    case 'ArrowDown':
      top += 40
      break
    case 'PageUp':
      top -= viewportHeight.value
      break
    case 'PageDown':
      top += viewportHeight.value
      break
    case 'Home':
      top = 0
      break
    case 'End':
      top = maximum.value
      break
    default:
      return
  }
  event.preventDefault()
  window.scrollTo(window.scrollX, top)
}

onMounted(() => {
  update()
  observer = new ResizeObserver(update)
  observer.observe(document.body)
  window.addEventListener('scroll', update, { passive: true })
  window.addEventListener('resize', update)
})

onUnmounted(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', update)
  window.removeEventListener('resize', update)
})
</script>

<template>
  <div
    v-show="maximum > 0"
    class="page-scrollbar"
    :class="{ dragging }"
    role="scrollbar"
    tabindex="0"
    :aria-label="t('Page scroll')"
    aria-controls="page-content"
    aria-orientation="vertical"
    :aria-valuemin="0"
    :aria-valuemax="Math.round(maximum)"
    :aria-valuenow="Math.round(scrollTop)"
    @pointerdown="startDrag"
    @pointermove="move"
    @pointerup="stopDrag"
    @pointercancel="stopDrag"
    @lostpointercapture="dragging = false"
    @keydown="onKey"
  >
    <span
      class="page-scrollbar-thumb"
      :style="{
        height: `${thumbHeight}px`,
        transform: `translateY(${thumbTop}px)`,
      }"
      aria-hidden="true"
    />
  </div>
</template>

<style scoped>
.page-scrollbar {
  position: fixed;
  inset: 0 0 0 auto;
  z-index: 10;
  width: 14px;
  touch-action: none;
  user-select: none;
}
.page-scrollbar-thumb {
  position: absolute;
  top: 0;
  right: 4px;
  width: 6px;
  border-radius: 3px;
  background: var(--control-border);
}
.page-scrollbar:is(:hover, :focus-visible, .dragging) .page-scrollbar-thumb {
  background: var(--muted);
}
</style>
