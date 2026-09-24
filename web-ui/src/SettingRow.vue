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
import { computed, ref, watch } from 'vue'
import { Check, ChevronDown } from '@lucide/vue'
import type { Option } from './api'
import FormattedText from './FormattedText.vue'

const props = defineProps<{
  option: Option
  capturing: boolean
  busy: boolean
  showDetails: boolean
}>()
const emit = defineEmits<{
  change: [value: boolean | number | string]
  capture: []
  cancel: []
}>()
const palette = [
  0x078d70, 0x26ceaa, 0x98e8c1, 0xffffff, 0x7bade2, 0x5049cc, 0x3d1a78,
]
const draft = ref(Number(props.option.value))
const numericInput = ref<HTMLInputElement | null>(null)
const menu = ref<HTMLDetailsElement | null>(null)
const selectedChoice = computed(
  () =>
    props.option.choices?.find((choice) => choice.value === props.option.value)
      ?.name ?? String(props.option.value),
)
const rangeFill = computed(() => {
  const min = props.option.min ?? 0
  const max = props.option.max ?? 0
  const percent =
    max === min
      ? 0
      : Math.max(0, Math.min(100, ((draft.value - min) / (max - min)) * 100))
  // The thumb center starts 7px inside the track, so the fill must follow it.
  return `calc(${percent}% + ${7 - percent * 0.14}px)`
})
watch(
  () => props.option.value,
  (value) => {
    draft.value = Number(value)
    if (numericInput.value && document.activeElement !== numericInput.value)
      numericInput.value.value = String(value)
  },
)
watch(
  () => props.busy,
  (busy) => {
    if (busy && menu.value) menu.value.open = false
    else if (!busy) draft.value = Number(props.option.value)
  },
)

function setDraft(event: Event) {
  draft.value = Number((event.target as HTMLInputElement).value)
}
function commitNumber(event: Event) {
  const input = event.target as HTMLInputElement
  const value = Number(input.value)
  if (
    !input.value ||
    !Number.isFinite(value) ||
    value < (props.option.min ?? value) ||
    value > (props.option.max ?? value)
  ) {
    input.value = String(draft.value)
    return
  }
  draft.value = value
  emit('change', value)
}
function resetNumber() {
  if (numericInput.value) numericInput.value.value = String(draft.value)
  numericInput.value?.blur()
}
function setChoice(value: number) {
  emit('change', value)
  if (menu.value) {
    menu.value.open = false
    menu.value.querySelector('summary')?.focus()
  }
}
function closeMenu(event: FocusEvent) {
  if (menu.value && !menu.value.contains(event.relatedTarget as Node | null))
    menu.value.open = false
}
function menuKeydown(event: KeyboardEvent) {
  if (!menu.value) return
  if (event.key === 'Escape') {
    menu.value.open = false
    menu.value.querySelector('summary')?.focus()
    event.preventDefault()
  } else if (
    event.key === 'ArrowDown' ||
    event.key === 'ArrowUp' ||
    event.key === 'Home' ||
    event.key === 'End'
  ) {
    event.preventDefault()
    menu.value.open = true
    const buttons = [
      ...menu.value.querySelectorAll<HTMLButtonElement>(
        '.choice-options button',
      ),
    ]
    const current = buttons.indexOf(document.activeElement as HTMLButtonElement)
    const next =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? buttons.length - 1
          : current < 0
            ? event.key === 'ArrowDown'
              ? 0
              : buttons.length - 1
            : (current +
                (event.key === 'ArrowDown' ? 1 : -1) +
                buttons.length) %
              buttons.length
    buttons[next]?.focus()
  }
}
function keyLabel(value: string) {
  if (value === 'RSHIFT') return 'Right Shift'
  if (value === 'LSHIFT') return 'Left Shift'
  return value === 'NONE' ? 'Unbound' : value
}
</script>

<template>
  <div class="row">
    <div>
      <strong><FormattedText :text="option.name" /></strong>
      <p v-if="showDetails && option.description">
        <FormattedText :text="option.description" />
      </p>
    </div>
    <div class="control">
      <button
        v-if="option.kind === 'BOOLEAN'"
        class="switch-button"
        type="button"
        :disabled="busy"
        role="switch"
        :aria-label="option.name"
        :aria-checked="Boolean(option.value)"
        @click="emit('change', !option.value)"
      >
        <span class="switch" :class="{ on: option.value }" />
      </button>
      <template v-else-if="option.kind === 'NUMBER'">
        <div class="numeric-control" :style="{ '--range-fill': rangeFill }">
          <input
            type="range"
            :aria-label="option.name"
            :min="option.min"
            :max="option.max"
            :disabled="busy"
            :step="option.step"
            :value="draft"
            @input="setDraft"
            @change="emit('change', draft)"
          />
          <input
            ref="numericInput"
            class="number-input"
            type="number"
            :aria-label="`${option.name} value`"
            :min="option.min"
            :max="option.max"
            :step="option.step"
            :value="draft"
            :disabled="busy"
            @change="commitNumber"
            @keydown.enter.prevent="numericInput?.blur()"
            @keydown.esc.prevent="resetNumber"
          />
        </div>
      </template>
      <details
        v-else-if="option.kind === 'CHOICE'"
        ref="menu"
        class="choice-picker"
        :class="{ busy }"
        @focusout="closeMenu"
        @keydown="menuKeydown"
      >
        <summary
          :aria-label="option.name"
          :aria-disabled="busy"
          :tabindex="busy ? -1 : 0"
        >
          <FormattedText :text="selectedChoice" /><ChevronDown :size="15" />
        </summary>
        <div class="choice-options">
          <button
            v-for="choice in option.choices"
            :key="choice.value"
            type="button"
            :disabled="busy"
            :aria-label="choice.name.replace(/\u00a7[0-9a-flmnor]/gi, '')"
            :aria-current="choice.value === option.value ? 'true' : undefined"
            @click="setChoice(choice.value)"
          >
            <FormattedText :text="choice.name" /><Check
              v-if="choice.value === option.value"
              :size="14"
            />
          </button>
        </div>
      </details>
      <div
        v-else-if="option.kind === 'COLOR'"
        class="swatches"
        role="group"
        :aria-label="option.name"
      >
        <button
          v-for="rgb in palette"
          :key="rgb"
          class="swatch"
          type="button"
          :disabled="busy"
          :class="{ selected: option.value === rgb }"
          :style="{ '--color': `#${rgb.toString(16).padStart(6, '0')}` }"
          :aria-label="`Color #${rgb.toString(16).padStart(6, '0')}`"
          :title="`#${rgb.toString(16).padStart(6, '0')}`"
          :aria-pressed="option.value === rgb"
          @click="emit('change', rgb)"
        >
          <span />
        </button>
      </div>
      <button
        v-else-if="option.kind === 'KEYBIND'"
        type="button"
        class="secondary"
        :class="{ capturing }"
        :disabled="busy"
        :aria-label="
          capturing ? `Cancel ${option.name} capture` : `Change ${option.name}`
        "
        @click="capturing ? emit('cancel') : emit('capture')"
      >
        {{ capturing ? 'Press a key' : keyLabel(option.keyName ?? 'NONE') }}
      </button>
    </div>
  </div>
</template>
