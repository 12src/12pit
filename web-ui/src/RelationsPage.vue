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
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Plus, Trash2, X } from '@lucide/vue'
import ExpandTransition from './ExpandTransition.vue'
import ResizeTransition from './ResizeTransition.vue'
import SearchField from './SearchField.vue'
import UiNotice from './UiNotice.vue'
import type {
  RelationEntry,
  RelationInput,
  RelationResult,
  RelationType,
  State,
} from './api'

const props = defineProps<{
  relations: State['relations']
  busy: boolean
  hasRequestError: boolean
  update: (
    action: 'add' | 'remove',
    relation: RelationType,
    entries: RelationInput[],
  ) => Promise<RelationResult[] | null>
}>()

const relationTypes: RelationType[] = ['FRIEND', 'ENEMY']
const relation = ref<RelationType>('FRIEND')
const relationTransition = ref('slide-left')
const search = ref('')
const input = ref('')
const nameInput = ref<HTMLTextAreaElement | null>(null)
const addOpen = ref(false)
const selected = ref<string[]>([])
const confirmingDelete = ref(false)
const deletingKey = ref<string | null>(null)
const issues = ref<RelationResult[]>([])

const entries = computed(() =>
  props.relations.entries
    .filter((entry) => entry.relation === relation.value)
    .sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
    ),
)
const visible = computed(() =>
  entries.value.filter((entry) => {
    const query = search.value.trim().toLowerCase()
    return (
      entry.name.toLowerCase().includes(query) ||
      !!entry.uuid?.toLowerCase().includes(query)
    )
  }),
)
const selectedEntries = computed(() =>
  entries.value.filter((entry) => selected.value.includes(entryKey(entry))),
)
const allVisibleSelected = computed(
  () =>
    visible.value.length > 0 &&
    visible.value.every((entry) => selected.value.includes(entryKey(entry))),
)
const someVisibleSelected = computed(() =>
  visible.value.some((entry) => selected.value.includes(entryKey(entry))),
)
const parsed = computed(() => {
  const names: string[] = []
  const invalid: string[] = []
  const seen = new Set<string>()
  const existing = new Set(
    entries.value.map((entry) => entry.name.toLowerCase()),
  )
  for (const part of input.value.split(/[,\r\n]/)) {
    const name = part.trim()
    if (!name) continue
    if (!/^[A-Za-z0-9_]{1,48}$/.test(name)) invalid.push(name)
    else {
      const key = name.toLowerCase()
      if (!seen.has(key) && !existing.has(key)) names.push(name)
      seen.add(key)
    }
  }
  return { names, invalid }
})

function entryKey(entry: RelationEntry) {
  return entry.uuid ?? `pending:${entry.name.toLowerCase()}`
}

function switchRelation(next: RelationType) {
  if (props.busy || relation.value === next) return
  relationTransition.value = next === 'ENEMY' ? 'slide-left' : 'slide-right'
  relation.value = next
}

function onRelationShortcut(event: KeyboardEvent) {
  const target = event.target
  if (
    props.busy ||
    event.altKey ||
    event.ctrlKey ||
    event.metaKey ||
    event.repeat ||
    (target instanceof HTMLElement &&
      (target.isContentEditable ||
        target.matches('textarea, select, input:not([type="checkbox"])')))
  )
    return
  const next =
    event.key.toLowerCase() === 'f'
      ? 'FRIEND'
      : event.key.toLowerCase() === 'e'
        ? 'ENEMY'
        : null
  if (next && next !== relation.value) {
    event.preventDefault()
    switchRelation(next)
  }
}

onMounted(() => window.addEventListener('keydown', onRelationShortcut))
onUnmounted(() => window.removeEventListener('keydown', onRelationShortcut))

watch(relation, () => {
  selected.value = []
  confirmingDelete.value = false
  deletingKey.value = null
  issues.value = []
})
watch(entries, (current) => {
  const keys = new Set(current.map(entryKey))
  if (selected.value.some((key) => !keys.has(key)))
    confirmingDelete.value = false
  selected.value = selected.value.filter((key) => keys.has(key))
  if (deletingKey.value && !keys.has(deletingKey.value))
    deletingKey.value = null
})
watch(search, () => {
  deletingKey.value = null
})

function toggleVisible(event: Event) {
  confirmingDelete.value = false
  deletingKey.value = null
  const keys = visible.value.map(entryKey)
  selected.value = (event.target as HTMLInputElement).checked
    ? [...new Set([...selected.value, ...keys])]
    : selected.value.filter((key) => !keys.includes(key))
}

async function toggleAdd() {
  addOpen.value = !addOpen.value
  if (addOpen.value) {
    await nextTick()
    nameInput.value?.focus()
  }
}

function toggleDelete(entry: RelationEntry) {
  selected.value = []
  confirmingDelete.value = false
  deletingKey.value =
    deletingKey.value === entryKey(entry) ? null : entryKey(entry)
}

function clearConfirmation() {
  confirmingDelete.value = false
  deletingKey.value = null
}

async function add() {
  if (
    props.busy ||
    !parsed.value.names.length ||
    parsed.value.names.length > 100
  )
    return
  const remaining = parsed.value.invalid
  const results = await props.update(
    'add',
    relation.value,
    parsed.value.names.map((name) => ({ name })),
  )
  if (results === null) return
  await nextTick()
  const saved = new Set(entries.value.map((entry) => entry.name.toLowerCase()))
  issues.value = results.filter((item) => !saved.has(item.name.toLowerCase()))
  input.value = [...remaining, ...issues.value.map((item) => item.name)].join(
    '\n',
  )
  if (!input.value && !issues.value.length) addOpen.value = false
}

async function removeSelected() {
  if (props.busy || !selectedEntries.value.length) return
  const removing = selectedEntries.value
  const results = await props.update(
    'remove',
    relation.value,
    removing.map(({ name, uuid }) => ({ name, uuid })),
  )
  if (results === null) return
  await nextTick()
  const stillHere = new Set(entries.value.map(entryKey))
  issues.value = results.filter((_, index) =>
    stillHere.has(entryKey(removing[index])),
  )
  selected.value = []
  confirmingDelete.value = false
}

async function removeEntry(entry: RelationEntry) {
  if (props.busy) return
  const results = await props.update('remove', relation.value, [
    { name: entry.name, uuid: entry.uuid },
  ])
  if (results === null) return
  await nextTick()
  issues.value = entries.value.some(
    (item) => entryKey(item) === entryKey(entry),
  )
    ? results
    : []
  deletingKey.value = null
}
</script>

<template>
  <div class="heading heading-relations">
    <h1>{{ t('Relations') }}</h1>
  </div>

  <div class="relation-toolbar">
    <div
      class="relation-tabs"
      :class="{ enemy: relation === 'ENEMY' }"
      role="group"
      :aria-label="t('Relation type')"
    >
      <button
        v-for="type in relationTypes"
        :key="type"
        type="button"
        :class="{ active: relation === type }"
        :aria-pressed="relation === type"
        :disabled="busy"
        @click="switchRelation(type)"
      >
        {{ type === 'FRIEND' ? t('Friends') : t('Enemies') }}
        <span>{{
          relations.entries.filter((item) => item.relation === type).length
        }}</span>
      </button>
    </div>
    <div class="relation-controls">
      <SearchField v-model="search" :label="t('Search MC ID or UUID')" />
      <div class="relation-actions">
        <button
          v-if="!selectedEntries.length"
          :class="addOpen ? 'secondary' : 'primary'"
          type="button"
          :aria-expanded="addOpen"
          :disabled="busy || !!relations.problem"
          @click="toggleAdd"
        >
          <X v-if="addOpen" :size="15" /><Plus v-else :size="15" />
          <span class="state-label">
            <span :class="{ 'is-hidden': addOpen }" :aria-hidden="addOpen">{{
              t('Add players')
            }}</span>
            <span :class="{ 'is-hidden': !addOpen }" :aria-hidden="!addOpen">{{
              t('Close')
            }}</span>
          </span>
        </button>
        <template v-else>
          <span class="relation-selected-count">{{
            t('{0} selected', selectedEntries.length)
          }}</span>
          <button
            class="icon-button"
            type="button"
            :aria-label="t('Clear selection')"
            :title="t('Clear selection')"
            :disabled="busy"
            @click="selected = []"
          >
            <X :size="15" />
          </button>
          <div class="relation-action-anchor">
            <button
              class="icon-button delete-button"
              type="button"
              :disabled="busy || selectedEntries.length > 100"
              :aria-expanded="confirmingDelete"
              :aria-label="
                t('Remove {0} selected players', selectedEntries.length)
              "
              :title="
                selectedEntries.length > 100
                  ? t('Select at most 100 players')
                  : t('Remove selected players')
              "
              @click="confirmingDelete = !confirmingDelete"
            >
              <Trash2 :size="15" />
            </button>
            <div v-if="confirmingDelete" class="relation-popover">
              <p>
                {{ t('Remove {0} selected players?', selectedEntries.length) }}
              </p>
              <div class="relation-popover-actions">
                <button
                  class="secondary"
                  type="button"
                  @click="confirmingDelete = false"
                >
                  {{ t('Cancel') }}
                </button>
                <button
                  class="danger"
                  type="button"
                  :disabled="busy"
                  @click="removeSelected"
                >
                  {{ t('Remove') }}
                </button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>

  <UiNotice v-if="relations.problem" variant="text" class="data-problem">
    {{ relations.problem }}
  </UiNotice>
  <template v-else>
    <ExpandTransition :open="addOpen">
      <form class="relation-add" @submit.prevent="add">
        <label for="relation-names">
          {{ relation === 'FRIEND' ? t('Add friends') : t('Add enemies') }}
        </label>
        <textarea
          id="relation-names"
          ref="nameInput"
          v-model="input"
          :placeholder="t('MC IDs, separated by commas or new lines')"
          rows="5"
          :disabled="busy"
          @keydown.esc.prevent="addOpen = false"
        />
        <p v-if="parsed.invalid.length" class="relation-validation">
          {{ t('Invalid MC IDs:') }}
          {{ parsed.invalid.slice(0, 3).join(', ')
          }}{{
            parsed.invalid.length > 3
              ? t(' and {0} more', parsed.invalid.length - 3)
              : ''
          }}
        </p>
        <p v-if="parsed.names.length > 100" class="relation-validation">
          {{ t('Add at most 100 players at a time.') }}
        </p>
        <div class="relation-add-actions">
          <span v-if="input.trim()">{{
            t('{0} ready to add', parsed.names.length)
          }}</span>
          <button
            class="primary"
            type="submit"
            :disabled="
              busy || !parsed.names.length || parsed.names.length > 100
            "
          >
            <Plus :size="15" />{{
              parsed.names.length === 1
                ? t('Add player')
                : parsed.names.length
                  ? t('Add {0} players', parsed.names.length)
                  : t('Add players')
            }}
          </button>
        </div>
      </form>
    </ExpandTransition>
    <UiNotice
      v-if="issues.length && !hasRequestError"
      class="error-notice relation-issues"
      :dismiss-label="t('Dismiss errors')"
      @dismiss="issues = []"
    >
      <strong>{{ t('Some players could not be updated.') }}</strong>
      <p v-for="(item, index) in issues" :key="index">
        {{ item.name }}: {{ item.message }}
      </p>
    </UiNotice>
    <ResizeTransition :name="relationTransition">
      <section :key="relation" class="relation-list">
        <div class="relation-list-heading">
          <input
            type="checkbox"
            :checked="allVisibleSelected"
            :indeterminate="someVisibleSelected && !allVisibleSelected"
            :disabled="!visible.length || busy"
            :aria-label="
              allVisibleSelected
                ? t('Deselect visible players')
                : t('Select visible players')
            "
            @change="toggleVisible"
          />
          <span class="relation-name-heading">{{ t('MC ID') }}</span>
          <span class="relation-uuid-heading">{{ t('UUID') }}</span>
        </div>
        <div
          v-for="entry in visible"
          :key="entryKey(entry)"
          class="relation-row list-row"
          :class="{ confirming: deletingKey === entryKey(entry) }"
        >
          <input
            v-model="selected"
            type="checkbox"
            :value="entryKey(entry)"
            :disabled="busy"
            :aria-label="t('Select {0}', entry.name)"
            @change="clearConfirmation"
          />
          <strong>{{ entry.name }}</strong>
          <span class="relation-uuid" :class="{ pending: !entry.uuid }">
            {{ entry.uuid ?? t('Not yet known') }}
          </span>
          <button
            class="icon-button delete-button"
            type="button"
            :disabled="busy"
            :aria-expanded="deletingKey === entryKey(entry)"
            :aria-label="
              deletingKey === entryKey(entry)
                ? t('Cancel removal of {0}', entry.name)
                : t('Remove {0}', entry.name)
            "
            :title="
              deletingKey === entryKey(entry)
                ? t('Cancel removal')
                : t('Remove {0}', entry.name)
            "
            @click="toggleDelete(entry)"
          >
            <Trash2 :size="15" />
          </button>
          <div
            v-if="deletingKey === entryKey(entry)"
            class="relation-popover relation-row-popover"
          >
            <p>{{ t('Remove {0}?', entry.name) }}</p>
            <div class="relation-popover-actions">
              <button
                class="secondary"
                type="button"
                :disabled="busy"
                @click="deletingKey = null"
              >
                {{ t('Cancel') }}
              </button>
              <button
                class="danger"
                type="button"
                :disabled="busy"
                @click="removeEntry(entry)"
              >
                {{ t('Remove') }}
              </button>
            </div>
          </div>
        </div>
        <p v-if="!visible.length" class="empty">
          {{
            search
              ? t('No matching players.')
              : relation === 'FRIEND'
                ? t('No friends yet.')
                : t('No enemies yet.')
          }}
        </p>
      </section>
    </ResizeTransition>
  </template>
</template>
