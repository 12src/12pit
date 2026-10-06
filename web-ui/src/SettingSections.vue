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
import { computed } from 'vue'
import type { Feature } from './api'
import FormattedText from './FormattedText.vue'
import SettingRow from './SettingRow.vue'

const props = defineProps<{
  featureId: string
  sections: Feature['sections']
  capturing: { featureId: string; settingId: string } | null
  busy: boolean
  showDetails: boolean
  flat?: boolean
}>()
const emit = defineEmits<{
  change: [settingId: string, value: boolean | number | string]
  capture: [settingId: string]
  cancel: []
}>()
const groups = computed<Feature['sections']>(() =>
  props.flat
    ? [{ options: props.sections.flatMap((section) => section.options) }]
    : props.sections,
)
</script>

<template>
  <section
    v-for="(section, index) in groups"
    :key="`${section.subcategory?.id ?? ''}/${section.id ?? index}`"
    :class="{ 'settings-options': flat }"
  >
    <h2 v-if="section.name"><FormattedText :text="section.name" /></h2>
    <SettingRow
      v-for="option in section.options"
      :key="option.id"
      :option="option"
      :capturing="
        capturing?.featureId === featureId && capturing?.settingId === option.id
      "
      :busy="busy"
      :show-details="showDetails"
      @change="emit('change', option.id, $event)"
      @capture="emit('capture', option.id)"
      @cancel="emit('cancel')"
    />
  </section>
</template>
