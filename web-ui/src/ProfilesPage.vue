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
import { nextTick, ref } from 'vue'
import { ArrowRight, Check, Pencil, Plus, Trash2, X } from '@lucide/vue'
import type { State } from './api'
import { t } from './languages'
import FormattedText from './FormattedText.vue'
import UiNotice from './UiNotice.vue'

const props = defineProps<{
  profiles: State['profiles']
  busy: boolean
  action: (action: string, id?: string, name?: string) => Promise<boolean>
}>()
const newName = ref('')
const showCreate = ref(false)
const editingId = ref<string | null>(null)
const editingName = ref('')
const deletingId = ref<string | null>(null)

async function createProfile() {
  const name = newName.value.trim()
  if (!name) return
  const succeeded = await props.action('create', undefined, name)
  if (succeeded) closeCreate()
  else
    void nextTick(() =>
      document
        .querySelector<HTMLInputElement>('.profile-create input')
        ?.focus(),
    )
}

async function renameProfile() {
  if (!editingId.value || !editingName.value.trim()) return
  const id = editingId.value
  const succeeded = await props.action('rename', id, editingName.value.trim())
  if (succeeded) {
    editingId.value = null
    void nextTick(() =>
      document
        .querySelector<HTMLButtonElement>(
          `.profile-row[data-id="${id}"] .rename-button`,
        )
        ?.focus(),
    )
  }
}

function beginCreate() {
  deletingId.value = null
  editingId.value = null
  showCreate.value = true
  void nextTick(() =>
    document.querySelector<HTMLInputElement>('.profile-create input')?.focus(),
  )
}

function closeCreate(restoreFocus = true) {
  showCreate.value = false
  newName.value = ''
  if (restoreFocus)
    void nextTick(() =>
      document.querySelector<HTMLButtonElement>('.new-profile-button')?.focus(),
    )
}

function onCreateFocusOut(event: FocusEvent) {
  if (!showCreate.value || props.busy) return
  const next = event.relatedTarget
  if (
    next instanceof Node &&
    (event.currentTarget as HTMLElement).contains(next)
  )
    return
  closeCreate(false)
}

function beginDelete(id: string) {
  showCreate.value = false
  newName.value = ''
  editingId.value = null
  deletingId.value = id
  void nextTick(() =>
    document.querySelector<HTMLButtonElement>('.confirm-cancel')?.focus(),
  )
}

function cancelDelete(id: string) {
  deletingId.value = null
  void nextTick(() =>
    document
      .querySelector<HTMLButtonElement>(
        `.profile-row[data-id="${id}"] .delete-button`,
      )
      ?.focus(),
  )
}

async function confirmDelete(id: string) {
  const succeeded = await props.action('delete', id)
  if (succeeded) {
    deletingId.value = null
    void nextTick(() =>
      document.querySelector<HTMLButtonElement>('.new-profile-button')?.focus(),
    )
  }
}

function beginRename(id: string, name: string) {
  showCreate.value = false
  newName.value = ''
  deletingId.value = null
  editingId.value = id
  editingName.value = name
  void nextTick(() => {
    const input = document.querySelector<HTMLInputElement>(
      '.profile-row .name-input',
    )
    input?.focus()
    input?.select()
  })
}

function cancelRename(id: string) {
  editingId.value = null
  void nextTick(() =>
    document
      .querySelector<HTMLButtonElement>(
        `.profile-row[data-id="${id}"] .rename-button`,
      )
      ?.focus(),
  )
}
</script>

<template>
  <div class="heading heading-profiles">
    <h1>{{ t('Profiles') }}</h1>
    <form
      class="profile-create"
      :class="{ creating: showCreate }"
      @submit.prevent="createProfile"
      @focusout="onCreateFocusOut"
    >
      <div class="profile-create-input">
        <input
          v-model="newName"
          maxlength="48"
          :placeholder="t('Profile name')"
          :aria-label="t('New profile name')"
          :disabled="!showCreate || profiles.loadState === 'LOADING'"
          :readonly="busy"
          @keydown.esc.prevent="closeCreate()"
          required
        />
      </div>
      <button
        class="primary new-profile-button"
        :type="showCreate ? 'submit' : 'button'"
        :aria-expanded="showCreate"
        :disabled="
          profiles.loadState === 'LOADING' ||
          busy ||
          (showCreate && !newName.trim())
        "
        @click="!showCreate && beginCreate()"
      >
        <Plus :size="15" />
        <span class="state-label">
          <span
            :class="{ 'is-hidden': showCreate }"
            :aria-hidden="showCreate"
            >{{ t('New profile') }}</span
          >
          <span
            :class="{ 'is-hidden': !showCreate }"
            :aria-hidden="!showCreate"
            >{{ t('Create') }}</span
          >
        </span>
      </button>
    </form>
  </div>
  <p v-if="profiles.loadState === 'LOADING'" class="empty">
    {{ t('Loading profiles...') }}
  </p>
  <template v-else>
    <UiNotice
      variant="text"
      v-for="problem in profiles.problems"
      :key="problem"
      class="data-problem"
    >
      {{ problem }}
    </UiNotice>
    <TransitionGroup name="profile-list" tag="section" class="profiles">
      <div
        v-for="profile in profiles.entries"
        :key="profile.id"
        class="profile-row list-row"
        :data-id="profile.id"
        :class="{
          'is-active': profile.id === profiles.activeId,
          'is-deleting': deletingId === profile.id,
        }"
      >
        <template v-if="deletingId === profile.id">
          <span class="delete-question"
            >{{ t('Delete') }}
            <strong><FormattedText :text="profile.name" /></strong>?</span
          >
          <div class="profile-actions">
            <button
              class="secondary confirm-cancel"
              type="button"
              :disabled="busy"
              @click="cancelDelete(profile.id)"
              @keydown.esc.prevent="cancelDelete(profile.id)"
            >
              {{ t('Cancel') }}
            </button>
            <button
              class="danger"
              type="button"
              :disabled="busy"
              @click="confirmDelete(profile.id)"
              @keydown.esc.prevent="cancelDelete(profile.id)"
            >
              {{ t('Delete') }}
            </button>
          </div>
        </template>
        <template v-else-if="editingId === profile.id">
          <input
            v-model="editingName"
            class="name-input"
            maxlength="48"
            :aria-label="t('Profile name')"
            :disabled="busy"
            @keydown.enter.prevent="renameProfile"
            @keydown.esc.prevent="cancelRename(profile.id)"
          />
          <div class="profile-actions">
            <button
              class="icon-button"
              :disabled="busy || !editingName.trim()"
              :aria-label="t('Save name')"
              :title="t('Save name')"
              @click="renameProfile"
            >
              <Check :size="15" />
            </button>
            <button
              class="icon-button"
              :aria-label="t('Cancel rename')"
              :title="t('Cancel rename')"
              @click="cancelRename(profile.id)"
              @keydown.esc.prevent="cancelRename(profile.id)"
            >
              <X :size="15" />
            </button>
          </div>
        </template>
        <template v-else>
          <strong><FormattedText :text="profile.name" /></strong>
          <div class="profile-actions">
            <span class="profile-use-slot">
              <span
                class="active-label"
                :class="{ 'is-hidden': profile.id !== profiles.activeId }"
                :aria-hidden="profile.id !== profiles.activeId"
                >{{ t('Active') }}</span
              >
              <button
                class="secondary use-profile"
                :class="{ 'is-hidden': profile.id === profiles.activeId }"
                :disabled="busy || profile.id === profiles.activeId"
                :tabindex="profile.id === profiles.activeId ? -1 : 0"
                :aria-hidden="profile.id === profiles.activeId"
                @click="action('switch', profile.id)"
              >
                {{ t('Switch') }}<ArrowRight :size="14" />
              </button>
            </span>
            <button
              class="icon-button rename-button"
              :disabled="busy"
              :aria-label="t('Rename {0}', profile.name)"
              :title="t('Rename {0}', profile.name)"
              @click="beginRename(profile.id, profile.name)"
            >
              <Pencil :size="15" />
            </button>
            <span
              class="delete-slot"
              :title="
                profile.id === profiles.activeId
                  ? t('The active profile cannot be deleted')
                  : undefined
              "
            >
              <button
                class="icon-button delete-button"
                :disabled="busy || profile.id === profiles.activeId"
                :aria-label="
                  profile.id === profiles.activeId
                    ? t('Cannot delete active profile')
                    : t('Delete {0}', profile.name)
                "
                :title="
                  profile.id === profiles.activeId
                    ? undefined
                    : t('Delete {0}', profile.name)
                "
                @click="beginDelete(profile.id)"
              >
                <Trash2 :size="15" />
              </button>
            </span>
          </div>
        </template>
      </div>
    </TransitionGroup>
    <p v-if="!profiles.entries.length" class="empty">
      {{ t('No profiles.') }}
    </p>
  </template>
</template>
