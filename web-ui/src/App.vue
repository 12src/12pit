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
import { t, setLanguage } from './languages'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowRight,
  Check,
  ChevronRight,
  Download,
  LayoutDashboard,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
  X,
} from '@lucide/vue'
import {
  changeProfile,
  changeRelations,
  changeSetting,
  loadState,
  openHudEditor,
  type Feature,
  type RelationInput,
  type RelationResult,
  type RelationType,
  type State,
} from './api'
import FormattedText from './FormattedText.vue'
import PageScrollbar from './PageScrollbar.vue'
import RelationsPage from './RelationsPage.vue'
import SettingRow from './SettingRow.vue'
import TransferDialog from './TransferDialog.vue'

const state = ref<State | null>(null)
const ready = ref(false)
const page = ref<'features' | 'relations' | 'profiles' | 'settings'>('features')
const selectedId = ref<string | null>(null)
const selectedSubcategoryId = ref<string | null>(null)
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
const navOrder = { features: 0, profiles: 1, relations: 2, settings: 3 }
const navIndex = computed(() => navOrder[page.value])
const navIndicatorTop = computed(() =>
  navIndex.value === 3 ? 'calc(100% - 62px)' : `${22 + navIndex.value * 44}px`,
)
const pageKey = computed(() =>
  page.value === 'features' && selectedId.value
    ? `feature:${selectedId.value}`
    : page.value,
)
const pageTransition = ref('slide-down')
const categoryTransition = ref('slide-left')
const subcategoryTransition = ref('slide-left')
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
const subcategories = computed(() => {
  const groups = new Map<string, { id: string; name: string }>()
  for (const section of current.value?.sections ?? []) {
    if (section.subcategory) {
      groups.set(section.subcategory.id, section.subcategory)
    }
  }
  return [...groups.values()]
})
const currentSections = computed(
  () =>
    current.value?.sections.filter(
      (section) =>
        !section.subcategory ||
        section.subcategory.id === selectedSubcategoryId.value,
    ) ?? [],
)
const categories = computed(() => [
  ['all', t('All')],
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
  const groups = new Map<string, { name: string; items: Feature[] }>()
  for (const feature of visible.value) {
    let group = groups.get(feature.categoryId)
    if (!group) {
      group = { name: feature.category, items: [] }
      groups.set(feature.categoryId, group)
    }
    group.items.push(feature)
  }
  return [...groups]
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
      .find((option) => option.id === 'show_details')?.value ?? false,
  ),
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
  void setLanguage(next.language).catch((cause) => {
    error.value = message(cause)
  })
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
  return cause instanceof Error ? cause.message : t('Request failed')
}

function serverChanged() {
  if (pending.value || sendingSetting) refreshQueued = true
  else void refresh()
}

async function mutate(action: () => Promise<State>) {
  if (pending.value) return
  pending.value = true
  requestVersion++
  try {
    displayState(await action())
    error.value = ''
  } catch (cause) {
    error.value = message(cause)
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
}

function imported(next: State) {
  requestVersion++
  displayState(next)
  closeTransfer()
  error.value = ''
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
  selectedSubcategoryId.value = subcategories.value[0]?.id ?? null
}

function selectCategory(id: string) {
  categoryTransition.value =
    categories.value.findIndex(([value]) => value === id) >
    categories.value.findIndex(([value]) => value === category.value)
      ? 'slide-left'
      : 'slide-right'
  category.value = id
}

function selectSubcategory(id: string) {
  subcategoryTransition.value =
    subcategories.value.findIndex((group) => group.id === id) >
    subcategories.value.findIndex(
      (group) => group.id === selectedSubcategoryId.value,
    )
      ? 'slide-left'
      : 'slide-right'
  selectedSubcategoryId.value = id
  cancelCapture()
}

function hasDetails(feature: Feature) {
  return feature.sections.some((section) => section.options.length > 0)
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
    error.value = t('This key cannot be bound')
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
          ><small class="brand-version" :title="state.version">{{
            state.version
          }}</small></span
        >
      </div>
      <nav
        :aria-label="t('Pages')"
        :style="{ '--indicator-top': navIndicatorTop }"
      >
        <span class="nav-indicator" aria-hidden="true" />
        <button
          :class="{ active: page === 'features' }"
          :aria-current="page === 'features' ? 'page' : undefined"
          @click="selectPage('features')"
        >
          {{ t('Features') }}
        </button>
        <button
          :class="{ active: page === 'profiles' }"
          :aria-current="page === 'profiles' ? 'page' : undefined"
          @click="selectPage('profiles')"
        >
          {{ t('Profiles') }}
        </button>
        <button
          :class="{ active: page === 'relations' }"
          :aria-current="page === 'relations' ? 'page' : undefined"
          @click="selectPage('relations')"
        >
          {{ t('Relations') }}
        </button>
        <button
          class="nav-settings"
          :class="{ active: page === 'settings' }"
          :aria-current="page === 'settings' ? 'page' : undefined"
          @click="selectPage('settings')"
        >
          {{ t('Settings') }}
        </button>
      </nav>
      <div class="community-links">
        <a
          href="https://discord.gg/e9PRKMUenc"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Discord"
          title="Discord"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            focusable="false"
          >
            <path
              d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"
            />
          </svg>
        </a>
        <a
          href="https://github.com/12src/12pit"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="GitHub"
          title="GitHub"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            focusable="false"
          >
            <path
              d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
            />
          </svg>
        </a>
      </div>
    </aside>
    <main id="page-content">
      <div v-if="error" class="error-notice" role="alert">
        <span>{{ error }}</span>
        <button
          class="notice-close"
          type="button"
          :aria-label="t('Dismiss error')"
          :title="t('Dismiss error')"
          @click="error = ''"
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
                  <h1>{{ t('Features') }}</h1>
                  <div class="search">
                    <Search :size="15" /><input
                      v-model="search"
                      type="search"
                      :placeholder="t('Search features')"
                      :aria-label="t('Search features')"
                    />
                    <button
                      v-if="search"
                      type="button"
                      :aria-label="t('Clear search')"
                      :title="t('Clear search')"
                      @click="clearSearch"
                    >
                      <X :size="14" />
                    </button>
                  </div>
                </div>
                <div
                  class="category-buttons"
                  role="group"
                  :aria-label="t('Category')"
                >
                  <button
                    v-for="[id, name] in categories"
                    :key="id"
                    type="button"
                    :aria-pressed="category === id"
                    @click="selectCategory(id)"
                  >
                    <FormattedText :text="name" />
                  </button>
                </div>
                <Transition :name="categoryTransition" mode="out-in">
                  <div :key="category" class="tab-view feature-list">
                    <section v-for="[id, group] in grouped" :key="id">
                      <h2 v-if="category === 'all'">
                        <FormattedText :text="group.name" />
                      </h2>
                      <div
                        v-for="feature in group.items"
                        :key="feature.id"
                        class="feature-row"
                      >
                        <button
                          v-if="hasDetails(feature)"
                          class="feature-link"
                          @click="openFeature(feature.id)"
                        >
                          <span
                            ><strong
                              ><FormattedText :text="feature.name" /></strong
                            ><small v-if="showDetails"
                              ><FormattedText
                                :text="feature.description" /></small
                          ></span>
                          <ChevronRight :size="16" />
                        </button>
                        <span v-else class="feature-link">
                          <span
                            ><strong
                              ><FormattedText :text="feature.name" /></strong
                            ><small v-if="showDetails"
                              ><FormattedText
                                :text="feature.description" /></small
                          ></span>
                        </span>
                        <button
                          v-if="feature.toggleable"
                          class="switch-button"
                          type="button"
                          role="switch"
                          :aria-label="t('Enable {0}', feature.name)"
                          :aria-checked="feature.enabled"
                          @click="
                            setting(feature.id, 'enabled', !feature.enabled)
                          "
                        >
                          <span
                            class="switch"
                            :class="{ on: feature.enabled }"
                          />
                        </button>
                      </div>
                    </section>
                    <p v-if="!visible.length" class="empty">
                      {{ t('No matching features.') }}
                    </p>
                  </div>
                </Transition>
              </template>
              <template v-else>
                <div
                  class="breadcrumbs"
                  role="navigation"
                  :aria-label="t('Breadcrumb')"
                >
                  <ol>
                    <li>
                      <button type="button" @click="backToFeatures">
                        {{ t('Features') }}
                      </button>
                    </li>
                    <li>
                      <ChevronRight :size="14" aria-hidden="true" />
                      <h1 aria-current="page">
                        <FormattedText :text="current.name" />
                      </h1>
                    </li>
                  </ol>
                </div>
                <p v-if="showDetails" class="feature-description">
                  <FormattedText :text="current.description" />
                </p>
                <div
                  v-if="subcategories.length"
                  class="category-buttons"
                  role="group"
                  :aria-label="t('Subcategory')"
                >
                  <button
                    v-for="subcategory in subcategories"
                    :key="subcategory.id"
                    type="button"
                    :aria-pressed="selectedSubcategoryId === subcategory.id"
                    @click="selectSubcategory(subcategory.id)"
                  >
                    <FormattedText :text="subcategory.name" />
                  </button>
                </div>
                <Transition :name="subcategoryTransition" mode="out-in">
                  <div :key="selectedSubcategoryId ?? ''" class="tab-view">
                    <section
                      v-for="section in currentSections"
                      :key="`${section.subcategory?.id ?? ''}/${section.id ?? ''}`"
                    >
                      <h2 v-if="section.name">
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
                        @change="
                          (value) => setting(current!.id, option.id, value)
                        "
                        @capture="startCapture(current!.id, option.id)"
                        @cancel="cancelCapture"
                      />
                    </section>
                  </div>
                </Transition>
              </template>
            </template>
            <RelationsPage
              v-else-if="page === 'relations'"
              :relations="state.relations"
              :busy="pending || sendingSetting"
              :update="updateRelations"
            />
            <template v-else-if="page === 'settings'">
              <div class="heading heading-settings">
                <h1>{{ t('Settings') }}</h1>
                <div class="settings-transfer-actions">
                  <button
                    type="button"
                    class="secondary"
                    :disabled="pending || sendingSetting"
                    @click="mutate(openHudEditor)"
                  >
                    <LayoutDashboard :size="15" />{{ t('Edit HUD') }}
                  </button>
                  <button
                    type="button"
                    class="secondary export-button"
                    :disabled="pending || sendingSetting"
                    @click="transferMode = 'export'"
                  >
                    <Download :size="15" />{{ t('Export') }}
                  </button>
                  <button
                    type="button"
                    class="secondary import-button"
                    :disabled="pending || sendingSetting"
                    @click="transferMode = 'import'"
                  >
                    <Upload :size="15" />{{ t('Import') }}
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
            </template>
            <template v-else>
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
                      showCreate ? t('Create') : t('New profile')
                    }}
                  </button>
                </form>
              </div>
              <p v-if="state.profiles.loadState === 'LOADING'" class="empty">
                {{ t('Loading profiles...') }}
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
                        >{{ t('Delete') }}
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
                          {{ t('Cancel') }}
                        </button>
                        <button
                          class="danger"
                          type="button"
                          :disabled="pending"
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
                        :disabled="pending"
                        @keydown.enter.prevent="renameProfile"
                        @keydown.esc.prevent="cancelRename(profile.id)"
                      />
                      <div class="profile-actions">
                        <button
                          class="icon-button"
                          :disabled="pending || !editingName.trim()"
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
                      <span
                        v-if="profile.id === state.profiles.activeId"
                        class="active-label"
                        >{{ t('Active') }}</span
                      >
                      <div class="profile-actions">
                        <button
                          v-if="profile.id !== state.profiles.activeId"
                          class="secondary use-profile"
                          :disabled="pending"
                          @click="profileAction('switch', profile.id)"
                        >
                          {{ t('Switch') }}<ArrowRight :size="14" />
                        </button>
                        <button
                          class="icon-button rename-button"
                          :disabled="pending"
                          :aria-label="t('Rename {0}', profile.name)"
                          :title="t('Rename {0}', profile.name)"
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
                                ? t('Cannot delete active profile')
                                : t('Delete {0}', profile.name)
                            "
                            :title="
                              profile.id === state.profiles.activeId
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
                <p v-if="!state.profiles.entries.length" class="empty">
                  {{ t('No profiles.') }}
                </p>
              </template>
            </template>
          </div>
        </Transition>
      </div>
    </main>
  </div>
  <div v-else class="disconnected" />
  <PageScrollbar v-if="state && ready" />
  <TransferDialog
    v-if="state && transferMode"
    :mode="transferMode"
    :state="state"
    @close="closeTransfer"
    @imported="imported"
  />
</template>
