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
import { Check } from '@lucide/vue'
import type { Option } from './api'
import ColorPicker from './ColorPicker.vue'
import FormattedText from './FormattedText.vue'
import PickerMenu from './PickerMenu.vue'
import ToggleSwitch from './ToggleSwitch.vue'

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
const menu = ref<InstanceType<typeof PickerMenu> | null>(null)
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
    if (!busy) draft.value = Number(props.option.value)
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
  menu.value?.close()
  menu.value?.focusTrigger()
}
function menuKeydown(event: KeyboardEvent) {
  if (props.busy || !menu.value) return
  if (
    event.key === 'ArrowDown' ||
    event.key === 'ArrowUp' ||
    event.key === 'Home' ||
    event.key === 'End'
  ) {
    event.preventDefault()
    menu.value.open()
    const buttons = [
      ...menu.value.element!.querySelectorAll<HTMLButtonElement>(
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
  if (value === 'RSHIFT') return t('Right Shift')
  if (value === 'LSHIFT') return t('Left Shift')
  return value === 'NONE' ? t('Unbound') : value
}
</script>

<template>
  <div class="row list-row">
    <div>
      <strong><FormattedText :text="option.name" /></strong>
      <p v-if="showDetails && option.description">
        <FormattedText :text="option.description" />
      </p>
    </div>
    <div class="control">
      <ToggleSwitch
        v-if="option.kind === 'BOOLEAN'"
        :model-value="Boolean(option.value)"
        :label="option.name"
        :disabled="busy"
        @update:model-value="emit('change', $event)"
      />
      <template v-else-if="option.kind === 'NUMBER'">
        <div class="numeric-control" :style="{ '--range-fill': rangeFill }">
          <input
            class="range-slider"
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
            :aria-label="t('Value for {0}', option.name)"
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
      <PickerMenu
        v-else-if="option.kind === 'CHOICE'"
        ref="menu"
        :label="option.name"
        :busy="busy"
        @keydown="menuKeydown"
      >
        <template #trigger><FormattedText :text="selectedChoice" /></template>
        <div class="choice-options menu-panel">
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
              :class="{ 'is-hidden': choice.value !== option.value }"
              aria-hidden="true"
              :size="14"
            />
          </button>
        </div>
      </PickerMenu>
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
          :aria-label="t('Color #{0}', rgb.toString(16).padStart(6, '0'))"
          :title="`#${rgb.toString(16).padStart(6, '0')}`"
          :aria-pressed="option.value === rgb"
          @click="emit('change', rgb)"
        >
          <span />
        </button>
      </div>
      <ColorPicker
        v-else-if="option.kind === 'COLOR_PICKER'"
        :value="Number(option.value)"
        :label="option.name"
        :busy="busy"
        @change="emit('change', $event)"
      />
      <button
        v-else-if="option.kind === 'KEYBIND'"
        type="button"
        class="secondary keybind-button"
        :class="{ capturing }"
        :disabled="busy"
        :aria-label="
          capturing
            ? t('Cancel capture for {0}', option.name)
            : t('Change {0}', option.name)
        "
        @click="capturing ? emit('cancel') : emit('capture')"
      >
        <span :class="{ 'is-hidden': capturing }" :aria-hidden="capturing">{{
          keyLabel(option.keyName ?? 'NONE')
        }}</span>
        <span :class="{ 'is-hidden': !capturing }" :aria-hidden="!capturing">{{
          t('Press a key')
        }}</span>
      </button>
    </div>
  </div>
</template>
