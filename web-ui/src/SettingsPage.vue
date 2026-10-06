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
import { Download, RotateCcw, Upload } from '@lucide/vue'
import type { Feature } from './api'
import { t } from './languages'
import SettingSections from './SettingSections.vue'

defineProps<{
  settings: Feature | undefined
  profilesLoading: boolean
  busy: boolean
  pending: boolean
  showDetails: boolean
  capturing: { featureId: string; settingId: string } | null
}>()
const emit = defineEmits<{
  reset: []
  transfer: [mode: 'export' | 'import']
  change: [
    featureId: string,
    settingId: string,
    value: boolean | number | string,
  ]
  capture: [featureId: string, settingId: string]
  cancel: []
}>()
</script>

<template>
  <div class="heading heading-settings">
    <h1>{{ t('Settings') }}</h1>
    <div class="settings-transfer-actions">
      <button
        type="button"
        class="secondary"
        :disabled="busy || profilesLoading"
        :title="t('Replaces the active profile configuration with defaults.')"
        @click="emit('reset')"
      >
        <RotateCcw :size="15" />{{ t('Restore defaults') }}
      </button>
      <button
        type="button"
        class="secondary export-button"
        :disabled="busy"
        @click="emit('transfer', 'export')"
      >
        <Download :size="15" />{{ t('Export') }}
      </button>
      <button
        type="button"
        class="secondary import-button"
        :disabled="busy"
        @click="emit('transfer', 'import')"
      >
        <Upload :size="15" />{{ t('Import') }}
      </button>
    </div>
  </div>
  <SettingSections
    v-if="settings"
    :feature-id="settings.id"
    :sections="settings.sections"
    :capturing="capturing"
    :busy="pending"
    :show-details="showDetails"
    flat
    @change="(id, value) => emit('change', settings!.id, id, value)"
    @capture="emit('capture', settings!.id, $event)"
    @cancel="emit('cancel')"
  />
</template>
