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
import { ref } from 'vue'
import { Search, X } from '@lucide/vue'
import { t } from './languages'

defineProps<{ label: string }>()
const value = defineModel<string>({ required: true })
const input = ref<HTMLInputElement | null>(null)

function clear() {
  value.value = ''
  input.value?.focus()
}
</script>

<template>
  <div class="search">
    <Search :size="15" />
    <input
      ref="input"
      v-model="value"
      type="search"
      :placeholder="label"
      :aria-label="label"
    />
    <button
      type="button"
      :class="{ 'is-hidden': !value }"
      :disabled="!value"
      :tabindex="value ? 0 : -1"
      :aria-hidden="!value"
      :aria-label="t('Clear search')"
      :title="t('Clear search')"
      @click="clear"
    >
      <X :size="14" />
    </button>
  </div>
</template>
