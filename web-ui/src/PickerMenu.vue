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
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ChevronDown } from '@lucide/vue'

const props = defineProps<{
  label: string
  busy: boolean
  holdFocus?: boolean
}>()
const emit = defineEmits<{
  open: []
  close: []
  beforeClose: [reason: 'outside' | 'blur' | 'escape' | 'busy' | 'select']
}>()
const element = ref<HTMLDetailsElement | null>(null)
let expanded = false

function focusTrigger() {
  element.value?.querySelector('summary')?.focus()
}

function open() {
  if (element.value && !props.busy) element.value.open = true
}

function close(
  reason: 'outside' | 'blur' | 'escape' | 'busy' | 'select' = 'select',
) {
  if (!element.value?.open) return
  emit('beforeClose', reason)
  element.value.open = false
  expanded = false
  emit('close')
}

function onToggle() {
  const next = element.value?.open ?? false
  if (next === expanded) return
  expanded = next
  if (next) emit('open')
  else emit('close')
}

function closeOutside(event: PointerEvent) {
  if (!element.value?.contains(event.target as Node)) close('outside')
}

function closeOnBlur(event: FocusEvent) {
  if (
    !props.holdFocus &&
    !element.value?.contains(event.relatedTarget as Node | null)
  )
    close('blur')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !element.value?.open) return
  event.preventDefault()
  event.stopPropagation()
  close('escape')
  focusTrigger()
}

watch(
  () => props.busy,
  (busy) => {
    if (busy) close('busy')
  },
)
onMounted(() => document.addEventListener('pointerdown', closeOutside))
onUnmounted(() => document.removeEventListener('pointerdown', closeOutside))
defineExpose({ element, open, close, focusTrigger })
</script>

<template>
  <details
    ref="element"
    class="choice-picker"
    :class="{ busy }"
    @focusout="closeOnBlur"
    @keydown="onKeydown"
    @toggle="onToggle"
  >
    <summary
      :aria-label="label"
      :aria-disabled="busy"
      :tabindex="busy ? -1 : 0"
      @click="busy && $event.preventDefault()"
    >
      <slot name="trigger" /><ChevronDown :size="15" />
    </summary>
    <slot />
  </details>
</template>
