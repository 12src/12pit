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
import { Download, RotateCcw, Upload, X } from '@lucide/vue'
import { ref } from 'vue'
import type { Feature } from './api'
import { t } from './languages'
import SettingSections from './SettingSections.vue'

const props = defineProps<{
  settings: Feature
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
const discordRpcDialog = ref<HTMLDialogElement | null>(null)

function changeSetting(id: string, value: boolean | number | string) {
  if (id === 'discord_rpc' && value === false) {
    discordRpcDialog.value?.showModal()
    return
  }
  emit('change', props.settings.id, id, value)
}

function disableDiscordRpc() {
  discordRpcDialog.value?.close()
  emit('change', props.settings.id, 'discord_rpc', false)
}
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
    :feature-id="settings.id"
    :sections="settings.sections"
    :capturing="capturing"
    :busy="pending"
    :show-details="showDetails"
    flat
    @change="changeSetting"
    @capture="emit('capture', settings.id, $event)"
    @cancel="emit('cancel')"
  />
  <dialog
    ref="discordRpcDialog"
    class="transfer-dialog discord-rpc-dialog"
    aria-labelledby="discord-rpc-title"
    aria-describedby="discord-rpc-description"
  >
    <header class="transfer-header">
      <h2 id="discord-rpc-title">{{ t('Help others discover 12pit?') }}</h2>
      <button
        type="button"
        class="icon-button"
        :aria-label="t('Close')"
        :title="t('Close')"
        @click="discordRpcDialog?.close()"
      >
        <X :size="17" />
      </button>
    </header>
    <div class="transfer-body">
      <p id="discord-rpc-description">
        {{
          t(
            'Showing 12pit on Discord helps more players find us. If you’re comfortable sharing it, we’d love for you to keep it on 🥺',
          )
        }}
      </p>
    </div>
    <footer class="transfer-footer">
      <button
        type="button"
        class="secondary"
        :disabled="pending"
        @click="disableDiscordRpc"
      >
        {{ t('Turn off') }}
      </button>
      <button
        type="button"
        class="primary"
        autofocus
        @click="discordRpcDialog?.close()"
      >
        {{ t('Keep on') }}
      </button>
    </footer>
  </dialog>
</template>

<style scoped>
.discord-rpc-dialog {
  width: min(480px, calc(100vw - 40px));
  padding: 0;
  color: var(--text);
  font: inherit;
}

.discord-rpc-dialog:not([open]) {
  display: none;
}

.discord-rpc-dialog::backdrop {
  background: #080a0bc9;
}

.discord-rpc-dialog .transfer-body {
  overflow: auto;
}

.discord-rpc-dialog p {
  margin: 0;
}
</style>
