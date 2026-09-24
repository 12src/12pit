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
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  ChevronDown,
  ChevronRight,
  Download,
  RefreshCw,
  Upload,
  X,
} from '@lucide/vue'
import {
  applyData,
  exportData,
  previewData,
  type RelationConflict,
  type RelationEntry,
  type RelationType,
  type State,
  type TransferPreview,
} from './api'

const props = defineProps<{
  mode: 'export' | 'import'
  state: State
}>()
const emit = defineEmits<{
  close: []
  imported: [state: State]
}>()

const relationTypes: RelationType[] = ['FRIEND', 'ENEMY']
const availableProfiles = computed(() =>
  props.mode === 'export'
    ? props.state.profiles.entries
    : (preview.value?.profiles ?? []),
)
const availableRelations = computed(() =>
  props.mode === 'export'
    ? props.state.relations.problem
      ? []
      : relationTypes
    : relationTypes.filter((type) =>
        Object.hasOwn(preview.value?.relations ?? {}, type),
      ),
)
const selectedProfiles = ref<string[]>(
  props.mode === 'export' ? props.state.profiles.entries.map((p) => p.id) : [],
)
const selectedRelations = ref<RelationType[]>(
  props.mode === 'export' && !props.state.relations.problem
    ? [...relationTypes]
    : [],
)
const expandedProfiles = ref(false)
const expandedRelations = ref(true)
const relationMode = ref<'merge' | 'replace'>('merge')
const fileData = ref<Record<string, unknown> | null>(null)
const preview = ref<TransferPreview | null>(null)
const resolutions = ref<Record<string, 'local' | 'imported'>>({})
const filename = ref('')
const busy = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const dialog = ref<HTMLElement | null>(null)
const selectedConflicts = computed(() =>
  relationMode.value === 'merge'
    ? (preview.value?.conflicts ?? []).filter((row) =>
        selectedRelations.value.includes(row.type),
      )
    : [],
)
const canSubmit = computed(
  () =>
    !busy.value &&
    (selectedProfiles.value.length > 0 || selectedRelations.value.length > 0) &&
    (props.mode === 'export' || !!preview.value),
)

onMounted(() => void nextTick(() => dialog.value?.focus()))

function toggleProfiles() {
  selectedProfiles.value =
    selectedProfiles.value.length === availableProfiles.value.length
      ? []
      : availableProfiles.value.map((profile) => profile.id)
}

function toggleRelations() {
  selectedRelations.value =
    selectedRelations.value.length === availableRelations.value.length
      ? []
      : [...availableRelations.value]
}

function changeProfile(id: string) {
  selectedProfiles.value = selectedProfiles.value.includes(id)
    ? selectedProfiles.value.filter((item) => item !== id)
    : [...selectedProfiles.value, id]
}

function changeRelation(type: RelationType) {
  selectedRelations.value = selectedRelations.value.includes(type)
    ? selectedRelations.value.filter((item) => item !== type)
    : [...selectedRelations.value, type]
}

async function readFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  fileData.value = null
  preview.value = null
  filename.value = ''
  error.value = ''
  if (file.size > 3 * 1024 * 1024) {
    error.value = 'File is too large'
    return
  }
  busy.value = true
  try {
    const parsed: unknown = JSON.parse(await file.text())
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('File must be a JSON object')
    }
    fileData.value = parsed as Record<string, unknown>
    filename.value = file.name
    await refreshPreview()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Cannot read file'
  } finally {
    busy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function refreshPreview() {
  if (!fileData.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await previewData(fileData.value)
    preview.value = result
    selectedProfiles.value = result.profiles.map((profile) => profile.id)
    selectedRelations.value = relationTypes.filter((type) =>
      Object.hasOwn(result.relations, type),
    )
    resolutions.value = Object.fromEntries(
      result.conflicts.map((row) => [row.id, 'local' as const]),
    )
  } catch (cause) {
    preview.value = null
    error.value = cause instanceof Error ? cause.message : 'Cannot preview file'
  } finally {
    busy.value = false
  }
}

async function submit() {
  if (!canSubmit.value) return
  busy.value = true
  error.value = ''
  try {
    if (props.mode === 'export') {
      const result = await exportData(
        selectedProfiles.value,
        selectedRelations.value,
      )
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(result, null, 2)], {
          type: 'application/json',
        }),
      )
      const link = document.createElement('a')
      link.href = url
      link.download = '12pit-settings.json'
      document.body.append(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      emit('close')
    } else if (fileData.value && preview.value) {
      const next = await applyData(
        fileData.value,
        selectedProfiles.value,
        selectedRelations.value,
        relationMode.value,
        resolutions.value,
        preview.value.fingerprint,
      )
      emit('imported', next)
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Transfer failed'
  } finally {
    busy.value = false
  }
}

function detail(entry: RelationEntry) {
  return `${entry.name}, ${entry.relation === 'FRIEND' ? 'Friend' : 'Enemy'}${entry.uuid ? `, ${entry.uuid}` : ''}`
}

function conflictLabel(row: RelationConflict) {
  return {
    group: 'Different group',
    identity: 'Different UUID',
    name: 'Different name',
  }[row.kind]
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !busy.value) {
    emit('close')
  }
  if (event.key !== 'Tab' || !dialog.value) return
  const controls = [
    ...dialog.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled)',
    ),
  ].filter((item) => item.offsetParent !== null)
  if (!controls.length) return
  if (event.shiftKey && document.activeElement === controls[0]) {
    event.preventDefault()
    controls[controls.length - 1].focus()
  } else if (
    !event.shiftKey &&
    document.activeElement === controls[controls.length - 1]
  ) {
    event.preventDefault()
    controls[0].focus()
  }
}
</script>

<template>
  <div class="transfer-overlay" @click.self="!busy && emit('close')">
    <div
      ref="dialog"
      class="transfer-dialog"
      :class="{ 'has-conflicts': selectedConflicts.length > 0 }"
      role="dialog"
      aria-modal="true"
      :aria-label="mode === 'export' ? 'Export data' : 'Import data'"
      tabindex="-1"
      @keydown="onKeydown"
    >
      <header class="transfer-header">
        <h2>{{ mode === 'export' ? 'Export data' : 'Import data' }}</h2>
        <button
          class="icon-button"
          type="button"
          aria-label="Close"
          title="Close"
          :disabled="busy"
          @click="emit('close')"
        >
          <X :size="17" />
        </button>
      </header>

      <div
        class="transfer-body"
        :class="{ 'is-empty': mode === 'import' && !preview && !error }"
      >
        <div v-if="mode === 'import'" class="transfer-file">
          <input
            ref="fileInput"
            type="file"
            hidden
            accept=".json,application/json"
            :disabled="busy"
            @change="readFile"
          />
          <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="fileInput?.click()"
          >
            Choose file
          </button>
          <span v-if="filename">{{ filename }}</span>
          <button
            v-if="fileData"
            type="button"
            class="icon-button"
            aria-label="Refresh preview"
            title="Refresh preview"
            :disabled="busy"
            @click="refreshPreview"
          >
            <RefreshCw :size="15" />
          </button>
        </div>

        <div v-if="mode === 'export' || preview" class="transfer-tree">
          <div class="transfer-parent">
            <button
              type="button"
              class="transfer-expand"
              :aria-expanded="expandedProfiles"
              aria-label="Toggle profiles"
              @click="expandedProfiles = !expandedProfiles"
            >
              <ChevronDown v-if="expandedProfiles" :size="16" />
              <ChevronRight v-else :size="16" />
            </button>
            <label>
              <input
                type="checkbox"
                :checked="
                  availableProfiles.length > 0 &&
                  selectedProfiles.length === availableProfiles.length
                "
                :indeterminate="
                  selectedProfiles.length > 0 &&
                  selectedProfiles.length < availableProfiles.length
                "
                :disabled="!availableProfiles.length || busy"
                @change="toggleProfiles"
              />
              Profiles
              <span
                >{{ selectedProfiles.length }}/{{
                  availableProfiles.length
                }}</span
              >
            </label>
          </div>
          <div v-if="expandedProfiles" class="transfer-children">
            <label v-for="profile in availableProfiles" :key="profile.id">
              <input
                type="checkbox"
                :checked="selectedProfiles.includes(profile.id)"
                :disabled="busy"
                @change="changeProfile(profile.id)"
              />
              {{ profile.name }}
            </label>
            <span v-if="!availableProfiles.length" class="transfer-empty"
              >No profiles</span
            >
          </div>

          <div class="transfer-parent">
            <button
              type="button"
              class="transfer-expand"
              :aria-expanded="expandedRelations"
              aria-label="Toggle relations"
              @click="expandedRelations = !expandedRelations"
            >
              <ChevronDown v-if="expandedRelations" :size="16" />
              <ChevronRight v-else :size="16" />
            </button>
            <label>
              <input
                type="checkbox"
                :checked="
                  availableRelations.length > 0 &&
                  selectedRelations.length === availableRelations.length
                "
                :indeterminate="
                  selectedRelations.length > 0 &&
                  selectedRelations.length < availableRelations.length
                "
                :disabled="!availableRelations.length || busy"
                @change="toggleRelations"
              />
              Relations
              <span
                >{{ selectedRelations.length }}/{{
                  availableRelations.length
                }}</span
              >
            </label>
          </div>
          <div v-if="expandedRelations" class="transfer-children">
            <label v-for="type in availableRelations" :key="type">
              <input
                type="checkbox"
                :checked="selectedRelations.includes(type)"
                :disabled="busy"
                @change="changeRelation(type)"
              />
              {{ type === 'FRIEND' ? 'Friends' : 'Enemies' }}
              <span v-if="mode === 'import'">{{
                preview?.relations[type]
              }}</span>
            </label>
            <span v-if="!availableRelations.length" class="transfer-empty"
              >No relations</span
            >
          </div>
        </div>

        <template
          v-if="mode === 'import' && preview && selectedRelations.length"
        >
          <div
            class="transfer-mode"
            role="group"
            aria-label="Relation import mode"
          >
            <button
              type="button"
              :class="{ active: relationMode === 'merge' }"
              :aria-pressed="relationMode === 'merge'"
              :disabled="busy"
              @click="relationMode = 'merge'"
            >
              Merge
            </button>
            <button
              type="button"
              :class="{ active: relationMode === 'replace' }"
              :aria-pressed="relationMode === 'replace'"
              :disabled="busy"
              @click="relationMode = 'replace'"
            >
              Replace
            </button>
          </div>

          <section v-if="selectedConflicts.length" class="transfer-conflicts">
            <div class="transfer-conflict-heading">
              <h3>
                Conflicts <span>{{ selectedConflicts.length }}</span>
              </h3>
              <div>
                <button
                  type="button"
                  :disabled="busy"
                  @click="
                    selectedConflicts.forEach(
                      (row) => (resolutions[row.id] = 'local'),
                    )
                  "
                >
                  Keep local for all
                </button>
                <button
                  type="button"
                  :disabled="busy"
                  @click="
                    selectedConflicts.forEach(
                      (row) => (resolutions[row.id] = 'imported'),
                    )
                  "
                >
                  Use file for all
                </button>
              </div>
            </div>
            <div class="transfer-conflict-list">
              <div
                v-for="row in selectedConflicts"
                :key="row.id"
                class="transfer-conflict"
              >
                <strong>{{ row.incoming.name }}</strong>
                <span class="transfer-kind">{{ conflictLabel(row) }}</span>
                <small>Local: {{ row.local.map(detail).join('; ') }}</small>
                <small>File: {{ detail(row.incoming) }}</small>
                <div
                  class="transfer-choice"
                  role="group"
                  :aria-label="`Resolve ${row.incoming.name}`"
                >
                  <button
                    type="button"
                    :class="{ active: resolutions[row.id] === 'local' }"
                    :aria-pressed="resolutions[row.id] === 'local'"
                    :disabled="busy"
                    @click="resolutions[row.id] = 'local'"
                  >
                    Keep local
                  </button>
                  <button
                    type="button"
                    :class="{ active: resolutions[row.id] === 'imported' }"
                    :aria-pressed="resolutions[row.id] === 'imported'"
                    :disabled="busy"
                    @click="resolutions[row.id] = 'imported'"
                  >
                    Use file
                  </button>
                </div>
              </div>
            </div>
          </section>
        </template>
        <p v-if="error" class="transfer-error" role="alert">{{ error }}</p>
      </div>

      <footer class="transfer-footer">
        <button
          type="button"
          class="secondary"
          :disabled="busy"
          @click="emit('close')"
        >
          Cancel
        </button>
        <button
          type="button"
          class="primary"
          :disabled="!canSubmit"
          @click="submit"
        >
          <Download v-if="mode === 'export'" :size="15" />
          <Upload v-else :size="15" />
          {{ busy ? 'Working...' : mode === 'export' ? 'Export' : 'Import' }}
        </button>
      </footer>
    </div>
  </div>
</template>
