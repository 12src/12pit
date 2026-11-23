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
import { Check, ExternalLink, Languages, X } from '@lucide/vue'
import { computed, nextTick, ref, watch } from 'vue'
import type { Option } from './api'
import { systemLanguage, t } from './languages'
import FormattedText from './FormattedText.vue'
import SearchField from './SearchField.vue'

const props = defineProps<{
  option: Option
  requestedLanguage: string | null
  busy: boolean
}>()
const emit = defineEmits<{
  change: [value: string]
  open: []
}>()
const dialog = ref<HTMLDialogElement | null>(null)
const search = ref('')
const draft = ref(String(props.option.value))
const selectedChoice = computed(() =>
  props.option.choices!.find((choice) => choice.value === props.option.value)!,
)
const choices = computed(() => {
  const query = search.value.trim().toLowerCase()
  return props.option
    .choices!.map((choice) => ({
      ...choice,
      displayName: t(choice.displayName!),
    }))
    .filter(
      (choice) =>
        choice.name.toLowerCase().includes(query) ||
        choice.displayName.toLowerCase().includes(query) ||
        choice.locale!.includes(query),
    )
})
const detected = computed(() => systemLanguage(props.option))

function open(value = String(props.option.value)) {
  emit('open')
  search.value = ''
  draft.value = value
  dialog.value?.showModal()
  dialog.value?.querySelector<HTMLInputElement>('input')?.focus()
}

function detectLanguage() {
  search.value = ''
  draft.value = String(detected.value!.value)
}

function apply() {
  dialog.value?.close()
  emit('change', draft.value)
}

watch(
  () => props.requestedLanguage,
  async (value) => {
    if (value === null) return
    await nextTick()
    open(value)
  },
  { immediate: true },
)
</script>

<template>
  <div class="row list-row">
    <div>
      <strong><FormattedText :text="option.name" /></strong>
      <p v-if="option.description">
        <FormattedText :text="option.description" />
      </p>
    </div>
    <div class="control">
      <button
        type="button"
        class="secondary language-trigger"
        :disabled="busy"
        :aria-label="t('Change {0}', option.name)"
        aria-haspopup="dialog"
        @click="open()"
      >
        <FormattedText
          :text="selectedChoice.name"
          :lang="selectedChoice.locale"
        />
        <Languages :size="16" aria-hidden="true" />
      </button>
    </div>
  </div>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="transfer-dialog language-dialog"
      aria-labelledby="language-title"
    >
      <header class="transfer-header">
        <h2 id="language-title"><FormattedText :text="option.name" /></h2>
        <button
          type="button"
          class="icon-button"
          :aria-label="t('Close')"
          :title="t('Close')"
          @click="dialog?.close()"
        >
          <X :size="17" />
        </button>
      </header>
      <div class="transfer-body">
        <div class="language-help">
          <!-- 0 is English in the language registry. -->
          <a
            v-if="option.value === 0"
            href="https://github.com/12src/12pit/issues/new"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ t('Request a language on GitHub') }}
            <ExternalLink :size="13" aria-hidden="true" />
          </a>
          <a
            v-else
            href="https://crowdin.com/project/12pit"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ t('Help improve translations on Crowdin') }}
            <ExternalLink :size="13" aria-hidden="true" />
          </a>
        </div>
        <SearchField v-model="search" :label="t('Search languages')" />
        <div class="language-choices" role="group" :aria-label="option.name">
          <button
            v-for="choice in choices"
            :key="choice.value"
            type="button"
            class="language-choice"
            :disabled="busy"
            :aria-pressed="choice.value === draft"
            @click="draft = String(choice.value)"
          >
            <span>
              <strong :lang="choice.locale">
                <FormattedText :text="choice.name" />
              </strong>
              <small>{{ choice.displayName }}</small>
            </span>
            <Check
              :class="{ 'is-hidden': choice.value !== draft }"
              aria-hidden="true"
              :size="20"
            />
          </button>
          <p v-if="!choices.length" class="language-empty">
            {{ t('No matching languages.') }}
          </p>
        </div>
      </div>
      <footer class="transfer-footer">
        <button
          type="button"
          class="secondary language-detect"
          :disabled="busy || !detected"
          @click="detectLanguage"
        >
          {{ t('Use system language') }}
        </button>
        <button type="button" class="secondary" @click="dialog?.close()">
          {{ t('Cancel') }}
        </button>
        <button
          type="button"
          class="primary"
          :disabled="busy || draft === option.value"
          @click="apply"
        >
          {{ t('Apply') }}
        </button>
      </footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.language-trigger {
  justify-content: space-between;
  width: min(100%, 260px);
  padding: 6px 10px;
  border-color: var(--control-border);
  font-weight: 400;
  text-align: left;
  white-space: normal;
}

.language-trigger svg {
  flex: none;
  color: var(--muted);
}

.language-dialog {
  width: min(680px, calc(100vw - 40px));
  padding: 0;
  color: var(--text);
  font: inherit;
}

.language-dialog:not([open]) {
  display: none;
}

.language-dialog::backdrop {
  background: #080a0bc9;
}

.language-dialog .transfer-header,
.language-dialog .transfer-footer {
  flex: none;
}

.language-dialog .transfer-footer {
  flex-wrap: wrap;
}

.language-dialog .transfer-body {
  gap: 16px;
  overflow-y: auto;
}

.language-help {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--bg);
  color: var(--muted);
  font-size: 12px;
}

.language-help a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent);
  text-decoration: none;
  text-underline-offset: 3px;
}

.language-help a:hover {
  text-decoration: underline;
}

.language-help svg {
  flex: none;
}

.language-dialog .search {
  flex: none;
  max-width: none;
}

.language-choices {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(180px, 100%), 1fr));
  align-content: start;
  gap: 4px 8px;
}

.language-choice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  min-height: 58px;
  padding: 10px 12px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text);
  text-align: start;
}

.language-choice strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.language-choice small {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
}

.language-choice span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.language-choice svg {
  flex: none;
  color: var(--accent);
}

.language-choice:hover,
.language-choice:focus-visible {
  background: var(--hover);
}

.language-choice[aria-pressed='true'] {
  background: var(--wash);
}

.language-empty {
  grid-column: 1 / -1;
  padding: 12px;
  color: var(--muted);
}

.language-detect {
  margin-right: auto;
}

@media (max-width: 400px) {
  .language-dialog .transfer-footer {
    gap: 8px;
    padding-inline: 12px;
  }
}
</style>
