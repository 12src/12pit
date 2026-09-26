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
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronRight,
  Download,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  Upload,
  X,
} from '@lucide/vue'
import {
  changeProfile,
  changeOnline,
  changeRelations,
  changeSetting,
  changeSync,
  loadState,
  type Feature,
  type RelationInput,
  type RelationResult,
  type RelationType,
  type State,
} from './api'
import FormattedText from './FormattedText.vue'
import RelationsPage from './RelationsPage.vue'
import SettingRow from './SettingRow.vue'
import SyncPage from './SyncPage.vue'
import TransferDialog from './TransferDialog.vue'

const state = ref<State | null>(null)
const ready = ref(false)
const page = ref<'features' | 'relations' | 'profiles' | 'settings' | 'sync'>(
  'features',
)
const selectedId = ref<string | null>(null)
const search = ref('')
const category = ref('all')
const newName = ref('')
const showCreate = ref(false)
const editingId = ref<string | null>(null)
const editingName = ref('')
const deletingId = ref<string | null>(null)
const error = ref('')
const pending = ref(false)
const transferMode = ref<'export' | 'import' | null>(null)
const capturing = ref<{ featureId: string; settingId: string } | null>(null)
const onlineBaseUrl = ref('')
const onlineNickname = ref('')
const keyConfirm = ref<'regenerate' | 'import' | null>(null)
const remoteReplace = ref<string | null>(null)
const dismissedSyncMessage = ref<string | null>(null)
const importedPrivateKey = ref('')
const navOrder = {
  features: 0,
  profiles: 1,
  relations: 2,
  sync: 3,
  settings: 4,
}
const navIndex = computed(() => navOrder[page.value])
const navIndicatorTop = computed(() =>
  navIndex.value === 4
    ? 'calc(100% - 62px)'
    : navIndex.value === 3
      ? 'calc(100% - 106px)'
      : `${22 + navIndex.value * 44}px`,
)
const pageKey = computed(() =>
  page.value === 'features' && selectedId.value
    ? `feature:${selectedId.value}`
    : page.value,
)
const pageTransition = ref('slide-down')
let stream: EventSource | undefined
let requestVersion = 0
let refreshQueued = false
type SettingEdit = {
  featureId: string
  optionId: string
  value: boolean | number | string
}
const pendingSettings = new Map<string, SettingEdit>()
const queuedSettings = new Map<string, SettingEdit>()
let sendingSetting = false

const features = computed(
  () => state.value?.features.filter((feature) => feature.id !== 'webui') ?? [],
)
const settings = computed(() =>
  state.value?.features.find((feature) => feature.id === 'webui'),
)
const current = computed(() =>
  features.value.find((feature) => feature.id === selectedId.value),
)
const canChangeKey = computed(() => {
  const sync = state.value?.sync
  if (
    sync &&
    ['joining', 'preparingKey', 'leaving', 'dissolving'].includes(sync.status)
  )
    return false
  if (!sync?.channelId) return true
  const self = sync.members.find((member) => member.id === sync.fingerprint)
  return (
    !!self &&
    sync.status === 'connected' &&
    (self?.role !== 'owner' ||
      sync.members.filter((member) => member.role === 'owner').length > 1)
  )
})
const remoteProfiles = computed(() => {
  const sync = state.value?.sync
  return (
    sync?.remoteProfiles.filter(
      (entry) => entry.ownerId !== sync.fingerprint,
    ) ?? []
  )
})
const syncNavStatus = computed(() => {
  const sync = state.value?.sync
  if (!sync) return 'Unavailable'
  if (sync.status === 'joining') return 'Joining'
  if (sync.status === 'error') return 'Error'
  if (!sync.channelId) return 'Not joined'
  const labels: Record<string, string> = {
    connected: 'Connected',
    connecting: 'Connecting',
    leaving: 'Leaving',
    dissolving: 'Dissolving',
    preparingKey: 'Changing key',
    error: 'Error',
    unconfigured: 'Offline',
  }
  return labels[sync.status] ?? sync.status
})
const onlineStatusLabel = computed(() =>
  state.value?.online.provider === '12pit' ? 'Global' : 'Self-hosted',
)
const onlineUrlInvalid = computed(() => {
  const value = onlineBaseUrl.value.trim()
  if (!value) return false
  try {
    const url = new URL(value)
    return !['http:', 'https:'].includes(url.protocol) || !url.hostname
  } catch {
    return true
  }
})
const keyUnavailableReason = computed(() => {
  const sync = state.value?.sync
  if (sync?.status === 'joining')
    return 'Wait for channel setup to finish before changing this device key.'
  if (!sync?.channelId || canChangeKey.value) return ''
  const self = sync.members.find((member) => member.id === sync.fingerprint)
  if (
    self?.role === 'owner' &&
    sync.members.filter((member) => member.role === 'owner').length === 1
  ) {
    return 'Assign another owner in Members before changing this device key.'
  }
  return 'Reconnect to the channel before changing this device key.'
})
const onlineCopied = ref(false)
const categories = computed(() => [
  ...new Map(
    features.value.map((feature) => [feature.categoryId, feature.category]),
  ).entries(),
])
const visible = computed(() =>
  features.value.filter(
    (feature) =>
      (category.value === 'all' || feature.categoryId === category.value) &&
      `${feature.name} ${feature.description}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
)
const grouped = computed(() => {
  const groups = new Map<string, Feature[]>()
  visible.value.forEach((feature) => {
    if (!groups.has(feature.category)) groups.set(feature.category, [])
    groups.get(feature.category)!.push(feature)
  })
  return [...groups].map(([name, items]) => ({ name, items }))
})
const color = computed(
  () =>
    settings.value?.sections
      .flatMap((section) => section.options)
      .find((option) => option.id === 'gui_color')?.value,
)
const showDetails = computed(() =>
  Boolean(
    settings.value?.sections
      .flatMap((section) => section.options)
      .find((option) => option.id === 'show_details')?.value ?? true,
  ),
)

watch(
  () => state.value?.online.baseUrl,
  (value) => {
    if (typeof value === 'string') onlineBaseUrl.value = value
  },
  { immediate: true },
)

watch(
  () => state.value?.online.nickname,
  (value) => {
    if (typeof value === 'string') onlineNickname.value = value
  },
  { immediate: true },
)

watch(
  () => state.value?.sync.status,
  (value, previous) => {
    if (
      value === 'connected' &&
      previous !== 'connected' &&
      page.value === 'profiles' &&
      state.value?.sync.syncProfiles
    )
      onlineAction('profiles')
  },
)

watch(
  () => state.value?.sync.fingerprint,
  (current, previous) => {
    if (current && previous && current !== previous)
      importedPrivateKey.value = ''
  },
)

watch(
  () => state.value?.sync.message,
  () => {
    dismissedSyncMessage.value = null
  },
)

watch(
  color,
  (value) => {
    if (typeof value !== 'number') return
    const hex = `#${value.toString(16).padStart(6, '0')}`
    document.documentElement.style.setProperty('--accent', hex)
    document.documentElement.style.setProperty('--wash', `${hex}22`)
    const red = (value >> 16) & 255,
      green = (value >> 8) & 255,
      blue = value & 255
    document.documentElement.style.setProperty(
      '--accent-text',
      (red * 299 + green * 587 + blue * 114) / 1000 >= 140
        ? '#111315'
        : '#f4f5f6',
    )
  },
  { immediate: true },
)

function applySetting(next: State, edit: SettingEdit) {
  const feature = next.features.find((item) => item.id === edit.featureId)
  if (!feature) return
  if (edit.optionId === 'enabled') {
    feature.enabled = Boolean(edit.value)
    return
  }
  const option = feature.sections
    .flatMap((section) => section.options)
    .find((item) => item.id === edit.optionId)
  if (!option) return
  if (typeof edit.value === 'string') option.keyName = edit.value
  else option.value = edit.value
}

function displayState(next: State) {
  for (const edit of pendingSettings.values()) applySetting(next, edit)
  state.value = next
}

async function refresh(clearError = true) {
  const version = ++requestVersion
  try {
    const next = await loadState()
    if (version !== requestVersion) return
    if (pending.value || sendingSetting) {
      refreshQueued = true
      return
    }
    displayState(next)
    ready.value = true
    if (clearError) error.value = ''
  } catch (cause) {
    if (version === requestVersion) {
      error.value = message(cause)
    }
  }
}

function message(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Request failed'
}

function serverChanged() {
  if (pending.value || sendingSetting) refreshQueued = true
  else void refresh()
}

async function mutate(action: () => Promise<State>): Promise<boolean> {
  if (pending.value) return false
  pending.value = true
  requestVersion++
  try {
    displayState(await action())
    error.value = ''
    return true
  } catch (cause) {
    error.value = message(cause)
    return false
  } finally {
    pending.value = false
    if (refreshQueued && !sendingSetting) {
      refreshQueued = false
      void refresh()
    }
  }
}

function setting(
  featureId: string,
  optionId: string,
  value: boolean | number | string,
) {
  const key = `${featureId}:${optionId}`
  const edit = { featureId, optionId, value }
  pendingSettings.set(key, edit)
  queuedSettings.set(key, edit)
  if (state.value) applySetting(state.value, edit)
  void flushSettings()
}

async function flushSettings() {
  if (sendingSetting) return
  sendingSetting = true
  let failed = false
  try {
    while (queuedSettings.size) {
      const [key, edit] = queuedSettings.entries().next().value!
      queuedSettings.delete(key)
      requestVersion++
      try {
        const next = await changeSetting(
          edit.featureId,
          edit.optionId,
          edit.value,
        )
        if (pendingSettings.get(key) === edit) pendingSettings.delete(key)
        displayState(next)
        error.value = ''
      } catch (cause) {
        if (pendingSettings.get(key) === edit) pendingSettings.delete(key)
        error.value = message(cause)
        refreshQueued = true
        failed = true
      }
    }
  } finally {
    sendingSetting = false
    if (refreshQueued) {
      refreshQueued = false
      void refresh(!failed)
    }
  }
}

async function profileAction(action: string, id?: string, name?: string) {
  await mutate(() => changeProfile(action, id, name))
}

function onlineAction(action: string, values: Record<string, unknown> = {}) {
  void mutate(() => changeSync(action, values))
}

async function saveOnlineBaseUrl() {
  if (onlineUrlInvalid.value) return
  const value = onlineBaseUrl.value.trim()
  if (
    (await mutate(() => changeOnline('selfHostedUrl', value))) &&
    state.value
  ) {
    onlineBaseUrl.value = state.value.online.baseUrl
  }
}

function chooseOnlineProvider(event: Event) {
  const select = event.target as HTMLSelectElement
  const provider = select.value
  select.value = state.value?.online.provider ?? 'selfhosted'
  void mutate(() => changeOnline('provider', provider))
}

function chooseOnlineRegion(event: Event) {
  const select = event.target as HTMLSelectElement
  const region = select.value
  select.value = state.value?.online.region ?? 'global'
  void mutate(() => changeOnline('region', region))
}

async function saveOnlineNickname() {
  if (
    (await mutate(() =>
      changeOnline('nickname', onlineNickname.value.trim()),
    )) &&
    state.value
  ) {
    onlineNickname.value = state.value.online.nickname
  }
}

async function copyOnlineFingerprint() {
  const fingerprint = state.value?.sync.fingerprint
  if (!fingerprint) return
  try {
    await navigator.clipboard.writeText(fingerprint)
    onlineCopied.value = true
    window.setTimeout(() => {
      onlineCopied.value = false
    }, 1600)
  } catch {
    error.value = 'Unable to copy to clipboard'
  }
}

async function confirmKeyChange() {
  if (!canChangeKey.value) return
  const action = keyConfirm.value
  if (!action || (action === 'import' && !importedPrivateKey.value.trim()))
    return
  const succeeded = await mutate(() =>
    changeSync(
      action === 'regenerate' ? 'regenerateKey' : 'importKey',
      action === 'import'
        ? { privateKey: importedPrivateKey.value.trim() }
        : {},
    ),
  )
  if (succeeded) {
    keyConfirm.value = null
    importedPrivateKey.value = ''
  }
}

function importRemoteProfile(ownerId: string, profileId: string, name: string) {
  const key = `${ownerId}:${profileId}`
  if (
    state.value?.profiles.entries.some(
      (entry) => entry.name.toLowerCase() === name.toLowerCase(),
    ) &&
    remoteReplace.value !== key
  ) {
    remoteReplace.value = key
    return
  }
  remoteReplace.value = null
  onlineAction('importProfile', { ownerId, profileId })
}

async function updateRelations(
  action: 'add' | 'remove',
  relation: RelationType,
  entries: RelationInput[],
): Promise<RelationResult[]> {
  pending.value = true
  requestVersion++
  try {
    const response = await changeRelations(action, relation, entries)
    displayState(response.state)
    error.value = ''
    return response.results
  } catch (cause) {
    error.value = message(cause)
    throw cause
  } finally {
    pending.value = false
    if (refreshQueued && !sendingSetting) {
      refreshQueued = false
      void refresh()
    }
  }
}

async function createProfile() {
  const name = newName.value.trim()
  if (!name) return
  await profileAction('create', undefined, name)
  if (!error.value) closeCreate()
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
  await profileAction('rename', id, editingName.value.trim())
  if (!error.value) {
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
  if (!showCreate.value || pending.value) return
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
  await profileAction('delete', id)
  if (!error.value) {
    deletingId.value = null
    void nextTick(() =>
      document.querySelector<HTMLButtonElement>('.new-profile-button')?.focus(),
    )
  }
}

function selectPage(next: typeof page.value) {
  if (next === page.value && !selectedId.value) return
  if (next === page.value) pageTransition.value = 'slide-right'
  else
    pageTransition.value =
      navIndex.value > navOrder[next] ? 'slide-down' : 'slide-up'
  page.value = next
  selectedId.value = null
  showCreate.value = false
  newName.value = ''
  editingId.value = null
  deletingId.value = null
  cancelCapture()
  remoteReplace.value = null
  if (
    next === 'profiles' &&
    state.value?.sync.status === 'connected' &&
    state.value.sync.syncProfiles
  ) {
    onlineAction('profiles')
  }
}

function imported(next: State) {
  requestVersion++
  displayState(next)
  closeTransfer()
  error.value = ''
}

function dismissNotice() {
  error.value = ''
  dismissedSyncMessage.value = state.value?.sync.message ?? ''
}

function syncUpdated(next: State) {
  requestVersion++
  displayState(next)
  void refresh()
}

function closeTransfer() {
  const mode = transferMode.value
  transferMode.value = null
  void nextTick(() =>
    document
      .querySelector<HTMLButtonElement>(
        `.settings-transfer-actions .${mode}-button`,
      )
      ?.focus(),
  )
}

function openFeature(id: string) {
  pageTransition.value = 'slide-left'
  selectedId.value = id
}

function backToFeatures() {
  pageTransition.value = 'slide-right'
  selectedId.value = null
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

function clearSearch() {
  search.value = ''
  void nextTick(() =>
    document.querySelector<HTMLInputElement>('.search input')?.focus(),
  )
}

function cancelCapture() {
  capturing.value = null
  window.removeEventListener('keydown', onKey, true)
}

function startCapture(featureId: string, settingId: string) {
  if (pending.value) return
  cancelCapture()
  capturing.value = { featureId, settingId }
  window.addEventListener('keydown', onKey, true)
}

function onKey(event: KeyboardEvent) {
  if (!capturing.value || event.repeat) return
  event.preventDefault()
  event.stopPropagation()
  if (event.code === 'Escape') {
    cancelCapture()
    return
  }
  const name = keyName(event.code)
  if (!name) {
    error.value = 'This key cannot be bound'
    cancelCapture()
    return
  }
  const target = capturing.value
  cancelCapture()
  void setting(target.featureId, target.settingId, name)
}

function keyName(code: string): string | null {
  if (code === 'Backspace' || code === 'Delete') return 'NONE'
  if (/^Key[A-Z]$/.test(code)) return code.slice(3)
  if (/^Digit[0-9]$/.test(code)) return code.slice(5)
  if (/^F([1-9]|1[0-9])$/.test(code)) return code
  if (/^Numpad[0-9]$/.test(code)) return `NUMPAD${code.slice(6)}`
  return (
    (
      {
        ShiftLeft: 'LSHIFT',
        ShiftRight: 'RSHIFT',
        ControlLeft: 'LCONTROL',
        ControlRight: 'RCONTROL',
        AltLeft: 'LMENU',
        AltRight: 'RMENU',
        Space: 'SPACE',
        Tab: 'TAB',
        Enter: 'RETURN',
        NumpadEnter: 'NUMPADENTER',
        NumpadAdd: 'ADD',
        NumpadSubtract: 'SUBTRACT',
        NumpadMultiply: 'MULTIPLY',
        NumpadDivide: 'DIVIDE',
        NumpadDecimal: 'DECIMAL',
        NumpadEqual: 'NUMPADEQUALS',
        NumpadComma: 'NUMPADCOMMA',
        NumpadClear: 'CLEAR',
        ArrowUp: 'UP',
        ArrowDown: 'DOWN',
        ArrowLeft: 'LEFT',
        ArrowRight: 'RIGHT',
        Backquote: 'GRAVE',
        Minus: 'MINUS',
        Equal: 'EQUALS',
        BracketLeft: 'LBRACKET',
        BracketRight: 'RBRACKET',
        Backslash: 'BACKSLASH',
        Semicolon: 'SEMICOLON',
        Quote: 'APOSTROPHE',
        Comma: 'COMMA',
        Period: 'PERIOD',
        Slash: 'SLASH',
        PageUp: 'PRIOR',
        PageDown: 'NEXT',
        Home: 'HOME',
        End: 'END',
        Insert: 'INSERT',
        CapsLock: 'CAPITAL',
        NumLock: 'NUMLOCK',
        ScrollLock: 'SCROLL',
        Pause: 'PAUSE',
        PrintScreen: 'SYSRQ',
        ContextMenu: 'APPS',
        MetaLeft: 'LMETA',
        MetaRight: 'RMETA',
        IntlYen: 'YEN',
        Lang1: 'KANA',
        Lang2: 'KANJI',
        Convert: 'CONVERT',
        NonConvert: 'NOCONVERT',
      } as Record<string, string>
    )[code] ?? null
  )
}

onMounted(() => {
  void refresh()
  stream = new EventSource('/api/events')
  stream.onopen = serverChanged
  stream.onmessage = serverChanged
  stream.onerror = () => {
    if (!state.value) error.value = ''
  }
})
onUnmounted(() => {
  stream?.close()
  cancelCapture()
})
</script>

<template>
  <div v-if="state && ready" class="app">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-lockup"
          ><span class="brand-name">12<span>pit</span></span
          ><small class="brand-version">{{ state.version }}</small></span
        >
      </div>
      <nav aria-label="Pages" :style="{ '--indicator-top': navIndicatorTop }">
        <span class="nav-indicator" aria-hidden="true" />
        <button
          :class="{ active: page === 'features' }"
          :aria-current="page === 'features' ? 'page' : undefined"
          @click="selectPage('features')"
        >
          Features
        </button>
        <button
          :class="{ active: page === 'profiles' }"
          :aria-current="page === 'profiles' ? 'page' : undefined"
          @click="selectPage('profiles')"
        >
          Profiles
        </button>
        <button
          :class="{ active: page === 'relations' }"
          :aria-current="page === 'relations' ? 'page' : undefined"
          @click="selectPage('relations')"
        >
          Relations
        </button>
        <span class="nav-divider" aria-hidden="true" />
        <button
          class="nav-sync"
          :class="{ active: page === 'sync' }"
          :aria-current="page === 'sync' ? 'page' : undefined"
          :aria-label="`Sync: ${syncNavStatus}`"
          @click="selectPage('sync')"
        >
          Sync
          <span
            class="sync-status-dot"
            :data-state="state.sync.status"
            :title="syncNavStatus"
            aria-hidden="true"
          />
        </button>
        <button
          class="nav-settings"
          :class="{ active: page === 'settings' }"
          :aria-current="page === 'settings' ? 'page' : undefined"
          @click="selectPage('settings')"
        >
          Settings
        </button>
      </nav>
    </aside>
    <main :class="{ 'settings-main': page === 'settings' }">
      <div
        v-if="
          error ||
          (page === 'profiles' &&
            state.sync.message &&
            state.sync.message !== dismissedSyncMessage)
        "
        class="error-notice"
        role="alert"
      >
        <span>{{ error || state.sync.message }}</span>
        <button
          class="notice-close"
          type="button"
          aria-label="Dismiss error"
          title="Dismiss error"
          @click="dismissNotice"
        >
          <X :size="15" />
        </button>
      </div>
      <div class="page-stage">
        <Transition :name="pageTransition" mode="out-in">
          <div :key="pageKey" class="page-view">
            <template v-if="page === 'features'">
              <template v-if="!current">
                <div class="heading heading-features">
                  <h1>Features</h1>
                  <div class="search">
                    <Search :size="15" /><input
                      v-model="search"
                      type="search"
                      placeholder="Search features"
                      aria-label="Search features"
                    />
                    <button
                      v-if="search"
                      type="button"
                      aria-label="Clear search"
                      title="Clear search"
                      @click="clearSearch"
                    >
                      <X :size="14" />
                    </button>
                  </div>
                </div>
                <div v-if="categories.length > 1" class="tools">
                  <select v-model="category" aria-label="Category">
                    <option value="all">All categories</option>
                    <option
                      v-for="[id, name] in categories"
                      :key="id"
                      :value="id"
                    >
                      {{ name }}
                    </option>
                  </select>
                </div>
                <section v-for="group in grouped" :key="group.name">
                  <h2><FormattedText :text="group.name" /></h2>
                  <div
                    v-for="feature in group.items"
                    :key="feature.id"
                    class="feature-row"
                  >
                    <button
                      class="feature-link"
                      @click="openFeature(feature.id)"
                    >
                      <span
                        ><strong><FormattedText :text="feature.name" /></strong
                        ><small v-if="showDetails"
                          ><FormattedText :text="feature.description" /></small
                      ></span>
                      <ChevronRight :size="16" />
                    </button>
                    <button
                      v-if="feature.toggleable"
                      class="switch-button"
                      type="button"
                      :disabled="pending"
                      role="switch"
                      :aria-label="`Enable ${feature.name}`"
                      :aria-checked="feature.enabled"
                      @click="setting(feature.id, 'enabled', !feature.enabled)"
                    >
                      <span class="switch" :class="{ on: feature.enabled }" />
                    </button>
                  </div>
                </section>
                <p v-if="!visible.length" class="empty">
                  No matching features.
                </p>
              </template>
              <template v-else>
                <button class="back" @click="backToFeatures">
                  <ArrowLeft :size="15" />Features
                </button>
                <div class="heading">
                  <div>
                    <h1><FormattedText :text="current.name" /></h1>
                    <p v-if="showDetails">
                      <FormattedText :text="current.description" />
                    </p>
                  </div>
                  <div v-if="current.toggleable" class="master">
                    Enabled
                    <button
                      class="switch-button"
                      type="button"
                      :disabled="pending"
                      role="switch"
                      :aria-label="`Enable ${current.name}`"
                      :aria-checked="current.enabled"
                      @click="setting(current.id, 'enabled', !current.enabled)"
                    >
                      <span class="switch" :class="{ on: current.enabled }" />
                    </button>
                  </div>
                </div>
                <section v-for="section in current.sections" :key="section.id">
                  <h2
                    v-if="
                      current.sections.length > 1 || section.id !== 'settings'
                    "
                  >
                    <FormattedText :text="section.name" />
                  </h2>
                  <SettingRow
                    v-for="option in section.options"
                    :key="option.id"
                    :option="option"
                    :capturing="
                      capturing?.featureId === current.id &&
                      capturing?.settingId === option.id
                    "
                    :busy="pending"
                    :show-details="showDetails"
                    @change="(value) => setting(current!.id, option.id, value)"
                    @capture="startCapture(current!.id, option.id)"
                    @cancel="cancelCapture"
                  />
                </section>
              </template>
            </template>
            <RelationsPage
              v-else-if="page === 'relations'"
              :relations="state.relations"
              :busy="pending || sendingSetting"
              :read-only="state.sync.relationsReadOnly"
              :update="updateRelations"
            />
            <template v-else-if="page === 'settings'">
              <div class="heading heading-settings">
                <h1>Settings</h1>
                <div class="settings-transfer-actions">
                  <button
                    type="button"
                    class="secondary export-button"
                    :disabled="pending || sendingSetting"
                    @click="transferMode = 'export'"
                  >
                    <Download :size="15" />Export
                  </button>
                  <button
                    type="button"
                    class="secondary import-button"
                    :disabled="pending || sendingSetting"
                    @click="transferMode = 'import'"
                  >
                    <Upload :size="15" />Import
                  </button>
                </div>
              </div>
              <section v-if="settings" class="settings-options">
                <SettingRow
                  v-for="option in settings.sections.flatMap(
                    (section) => section.options,
                  )"
                  :key="option.id"
                  :option="option"
                  :capturing="
                    capturing?.featureId === settings.id &&
                    capturing?.settingId === option.id
                  "
                  :busy="pending"
                  :show-details="showDetails"
                  @change="(value) => setting(settings!.id, option.id, value)"
                  @capture="startCapture(settings!.id, option.id)"
                  @cancel="cancelCapture"
                />
              </section>
              <section class="online-settings" aria-labelledby="online-heading">
                <div class="online-heading">
                  <h2 id="online-heading">Online</h2>
                </div>
                <div class="online-group">
                  <div class="online-setting-row">
                    <strong>Provider</strong>
                    <div class="online-provider-controls">
                      <select
                        :value="state.online.provider"
                        :disabled="pending || !!state.sync.channelId"
                        @change="chooseOnlineProvider"
                      >
                        <option value="selfhosted">Self-hosted</option>
                        <option value="12pit">12pit</option>
                      </select>
                      <form
                        v-if="state.online.provider === 'selfhosted'"
                        class="online-url-control"
                        @submit.prevent="saveOnlineBaseUrl"
                      >
                        <input
                          id="online-server-url"
                          v-model="onlineBaseUrl"
                          type="url"
                          placeholder="https://sync.example.com"
                          :aria-invalid="onlineUrlInvalid"
                          aria-label="Self-hosted server URL"
                          :disabled="pending || !!state.sync.channelId"
                        />
                        <button
                          class="secondary"
                          type="submit"
                          :disabled="
                            pending ||
                            !!state.sync.channelId ||
                            onlineUrlInvalid ||
                            onlineBaseUrl.trim() === state.online.baseUrl
                          "
                        >
                          Save
                        </button>
                      </form>
                      <label v-else class="online-region-control">
                        <span>Region</span>
                        <select
                          :value="state.online.region"
                          :disabled="pending || !!state.sync.channelId"
                          @change="chooseOnlineRegion"
                        >
                          <option value="global">Global</option>
                        </select>
                      </label>
                    </div>
                  </div>
                  <small
                    v-if="onlineUrlInvalid"
                    class="form-error online-setting-error"
                    >Enter a valid server URL.</small
                  >
                  <p v-if="state.sync.channelId" class="online-note">
                    Leave the channel before changing provider.
                  </p>
                  <form
                    class="online-setting-row"
                    @submit.prevent="saveOnlineNickname"
                  >
                    <label for="online-device-name"
                      ><strong>Device name</strong></label
                    >
                    <div class="online-input-action">
                      <input
                        id="online-device-name"
                        v-model="onlineNickname"
                        type="text"
                        maxlength="32"
                        placeholder="Unnamed device"
                        :disabled="pending"
                      />
                      <button
                        class="secondary"
                        type="submit"
                        :disabled="
                          pending ||
                          onlineNickname.trim() === state.online.nickname
                        "
                      >
                        Save
                      </button>
                    </div>
                  </form>
                  <dl class="online-identity">
                    <div>
                      <dt>Fingerprint</dt>
                      <dd :title="state.sync.fingerprint">
                        {{ state.sync.fingerprint || 'Not available' }}
                      </dd>
                      <button
                        class="icon-button"
                        type="button"
                        :disabled="!state.sync.fingerprint"
                        :title="onlineCopied ? 'Copied' : 'Copy fingerprint'"
                        :aria-label="
                          onlineCopied
                            ? 'Fingerprint copied'
                            : 'Copy fingerprint'
                        "
                        @click="copyOnlineFingerprint"
                      >
                        <Check v-if="onlineCopied" :size="15" /><Copy
                          v-else
                          :size="15"
                        />
                      </button>
                    </div>
                  </dl>
                  <details class="online-advanced">
                    <summary><strong>Device key</strong></summary>
                    <div class="online-advanced-body">
                      <p
                        v-if="keyUnavailableReason"
                        class="online-availability"
                      >
                        {{ keyUnavailableReason }}
                      </p>
                      <div class="online-key-row">
                        <strong>Generate a new key</strong>
                        <button
                          class="secondary"
                          type="button"
                          :disabled="pending || !canChangeKey"
                          @click="keyConfirm = 'regenerate'"
                        >
                          <RefreshCw :size="15" /> Regenerate
                        </button>
                      </div>
                      <div class="online-key-row online-key-import">
                        <label for="online-private-key"
                          ><strong>Import a private key</strong></label
                        >
                        <textarea
                          id="online-private-key"
                          v-model="importedPrivateKey"
                          rows="4"
                          autocomplete="off"
                          spellcheck="false"
                          placeholder="-----BEGIN PGP PRIVATE KEY BLOCK-----"
                          :disabled="pending || !canChangeKey"
                        />
                        <button
                          class="secondary"
                          type="button"
                          :disabled="
                            pending ||
                            !canChangeKey ||
                            !importedPrivateKey.trim()
                          "
                          @click="keyConfirm = 'import'"
                        >
                          <Upload :size="15" /> Import key
                        </button>
                      </div>
                      <div
                        v-if="keyConfirm"
                        class="sync-confirm sync-confirm-warning"
                      >
                        <span>{{
                          state.sync.channelId
                            ? 'Change this key and remove this device and its shared data from the channel?'
                            : 'Replace this device key?'
                        }}</span>
                        <div class="sync-confirm-actions">
                          <button
                            class="secondary"
                            type="button"
                            @click="keyConfirm = null"
                          >
                            Cancel
                          </button>
                          <button
                            class="danger"
                            type="button"
                            :disabled="pending || !canChangeKey"
                            @click="confirmKeyChange"
                          >
                            Change key
                          </button>
                        </div>
                      </div>
                    </div>
                  </details>
                </div>
              </section>
            </template>
            <SyncPage
              v-else-if="page === 'sync' && state"
              :state="state.sync"
              :base-url="state.online.endpoint"
              :online-label="onlineStatusLabel"
              @updated="syncUpdated"
              @open-settings="selectPage('settings')"
            />
            <template v-else>
              <div class="heading heading-profiles">
                <h1>Profiles</h1>
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
                      placeholder="Profile name"
                      aria-label="New profile name"
                      :disabled="
                        !showCreate || state.profiles.loadState === 'LOADING'
                      "
                      :readonly="pending"
                      @keydown.esc.prevent="closeCreate()"
                      required
                    />
                  </div>
                  <button
                    class="primary new-profile-button"
                    :type="showCreate ? 'submit' : 'button'"
                    :aria-expanded="showCreate"
                    :disabled="
                      state.profiles.loadState === 'LOADING' ||
                      pending ||
                      (showCreate && !newName.trim())
                    "
                    @click="!showCreate && beginCreate()"
                  >
                    <Plus :size="15" />{{
                      showCreate ? 'Create' : 'New profile'
                    }}
                  </button>
                </form>
              </div>
              <p v-if="state.profiles.loadState === 'LOADING'" class="empty">
                Loading profiles...
              </p>
              <template v-else>
                <p
                  v-for="problem in state.profiles.problems"
                  :key="problem"
                  class="profile-problem"
                >
                  {{ problem }}
                </p>
                <TransitionGroup
                  name="profile-list"
                  tag="section"
                  class="profiles"
                >
                  <div
                    v-for="profile in state.profiles.entries"
                    :key="profile.id"
                    class="profile-row"
                    :data-id="profile.id"
                    :class="{
                      'is-active': profile.id === state.profiles.activeId,
                      'is-deleting': deletingId === profile.id,
                    }"
                  >
                    <template v-if="deletingId === profile.id">
                      <span class="delete-question"
                        >Delete
                        <strong><FormattedText :text="profile.name" /></strong
                        >?</span
                      >
                      <div class="profile-actions">
                        <button
                          class="secondary confirm-cancel"
                          type="button"
                          :disabled="pending"
                          @click="cancelDelete(profile.id)"
                          @keydown.esc.prevent="cancelDelete(profile.id)"
                        >
                          Cancel
                        </button>
                        <button
                          class="danger"
                          type="button"
                          :disabled="pending"
                          @click="confirmDelete(profile.id)"
                          @keydown.esc.prevent="cancelDelete(profile.id)"
                        >
                          Delete
                        </button>
                      </div>
                    </template>
                    <template v-else-if="editingId === profile.id">
                      <input
                        v-model="editingName"
                        class="name-input"
                        maxlength="48"
                        aria-label="Profile name"
                        :disabled="pending"
                        @keydown.enter.prevent="renameProfile"
                        @keydown.esc.prevent="cancelRename(profile.id)"
                      />
                      <div class="profile-actions">
                        <button
                          class="icon-button"
                          :disabled="pending || !editingName.trim()"
                          aria-label="Save name"
                          title="Save name"
                          @click="renameProfile"
                        >
                          <Check :size="15" />
                        </button>
                        <button
                          class="icon-button"
                          aria-label="Cancel rename"
                          title="Cancel rename"
                          @click="cancelRename(profile.id)"
                          @keydown.esc.prevent="cancelRename(profile.id)"
                        >
                          <X :size="15" />
                        </button>
                      </div>
                    </template>
                    <template v-else>
                      <strong><FormattedText :text="profile.name" /></strong>
                      <span
                        v-if="profile.id === state.profiles.activeId"
                        class="active-label"
                        >Active</span
                      >
                      <div class="profile-actions">
                        <button
                          v-if="profile.id !== state.profiles.activeId"
                          class="secondary use-profile"
                          :disabled="pending"
                          @click="profileAction('switch', profile.id)"
                        >
                          Switch<ArrowRight :size="14" />
                        </button>
                        <button
                          class="icon-button rename-button"
                          :disabled="pending"
                          :aria-label="`Rename ${profile.name}`"
                          :title="`Rename ${profile.name}`"
                          @click="beginRename(profile.id, profile.name)"
                        >
                          <Pencil :size="15" />
                        </button>
                        <span
                          class="delete-slot"
                          :title="
                            profile.id === state.profiles.activeId
                              ? 'The active profile cannot be deleted'
                              : undefined
                          "
                        >
                          <button
                            class="icon-button delete-button"
                            :disabled="
                              pending || profile.id === state.profiles.activeId
                            "
                            :aria-label="
                              profile.id === state.profiles.activeId
                                ? 'Cannot delete active profile'
                                : `Delete ${profile.name}`
                            "
                            :title="
                              profile.id === state.profiles.activeId
                                ? undefined
                                : `Delete ${profile.name}`
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
                <p v-if="!state.profiles.entries.length" class="empty">
                  No profiles.
                </p>
                <section
                  v-if="state.sync.channelId && state.sync.syncProfiles"
                  class="remote-profiles"
                >
                  <div class="sync-section-heading">
                    <h2>Channel profiles</h2>
                    <button
                      class="icon-button"
                      type="button"
                      title="Refresh channel profiles"
                      aria-label="Refresh channel profiles"
                      :disabled="pending || state.sync.status !== 'connected'"
                      @click="onlineAction('profiles')"
                    >
                      <RefreshCw :size="15" />
                    </button>
                  </div>
                  <p
                    v-if="state.sync.status !== 'connected'"
                    class="sync-notice"
                  >
                    Connect to view channel profiles.
                  </p>
                  <p v-else-if="!remoteProfiles.length" class="sync-notice">
                    No shared profiles.
                  </p>
                  <div
                    v-for="entry in remoteProfiles"
                    :key="`${entry.ownerId}:${entry.id}`"
                    class="remote-profile-row"
                  >
                    <div class="remote-profile-name">
                      <strong>{{ entry.name }}</strong>
                      <small
                        >{{
                          state.sync.members.find(
                            (member) => member.id === entry.ownerId,
                          )?.nickname || 'Unnamed device'
                        }}
                        ·
                        <span :title="entry.ownerId">{{
                          entry.ownerId
                        }}</span></small
                      >
                    </div>
                    <template
                      v-if="remoteReplace === `${entry.ownerId}:${entry.id}`"
                    >
                      <span>Replace local profile?</span>
                      <button
                        class="secondary"
                        type="button"
                        @click="remoteReplace = null"
                      >
                        Cancel
                      </button>
                    </template>
                    <button
                      class="secondary"
                      type="button"
                      :disabled="
                        pending ||
                        state.sync.status !== 'connected' ||
                        state.profiles.loadState !== 'READY'
                      "
                      @click="
                        importRemoteProfile(entry.ownerId, entry.id, entry.name)
                      "
                    >
                      <Download :size="15" />
                      {{
                        remoteReplace === `${entry.ownerId}:${entry.id}`
                          ? 'Replace'
                          : 'Import'
                      }}
                    </button>
                  </div>
                </section>
              </template>
            </template>
          </div>
        </Transition>
      </div>
    </main>
  </div>
  <div v-else class="disconnected" />
  <TransferDialog
    v-if="state && transferMode"
    :mode="transferMode"
    :state="state"
    @close="closeTransfer"
    @imported="imported"
  />
</template>
