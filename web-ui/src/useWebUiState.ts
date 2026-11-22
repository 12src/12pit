/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  applyData,
  changeFavorite,
  changeRelations,
  changeSetting,
  createProfile,
  deleteProfile,
  dismissLanguageHint,
  loadCatalog,
  loadLinks,
  loadPreferences,
  loadProfiles,
  loadRelations,
  loadSettingValues,
  loadTransfer,
  renameProfile,
  resetSettings,
  selectProfile,
  subscribe,
  type ConfigurationCatalog,
  type Feature,
  type Option,
  type ProfilesState,
  type RelationInput,
  type RelationResult,
  type RelationsState,
  type RelationType,
  type SettingValue,
  type TransferState,
  type WebUiLink,
  type WebUiPreferences,
} from './api'
import { applyLanguage, loadLanguage, t, type Catalog } from './languages'

export interface WebUiState {
  language: string
  version: string
  features: Feature[]
  webui: WebUiPreferences & { settings: Feature }
  profiles: ProfilesState
  relations: RelationsState
  transfer: TransferState
}

export type ProfileAction = 'create' | 'rename' | 'delete' | 'switch' | 'reset'

const emptyPreferences: WebUiPreferences = {
  languageHint: false,
  favorites: [],
  ready: false,
  problem: null,
}
const emptyProfiles: ProfilesState = {
  loadState: 'LOADING',
  activeId: null,
  entries: [],
  problems: [],
  unpersisted: false,
}
const emptyRelations: RelationsState = { problem: null, entries: [] }
const emptyTransfer: TransferState = { swapBindings: { problem: null } }

export function useWebUiState() {
  const catalog = ref<ConfigurationCatalog | null>(null)
  const links = ref<WebUiLink[] | null>(null)
  const preferences = ref<WebUiPreferences | null>(null)
  const profiles = ref<ProfilesState | null>(null)
  const relations = ref<RelationsState | null>(null)
  const transfer = ref<TransferState | null>(null)
  const state = computed<WebUiState | null>(() => {
    const loaded = catalog.value
    if (!loaded) return null
    return {
      language: loaded.language,
      version: loaded.version,
      features: loaded.features,
      webui: {
        ...(preferences.value ?? emptyPreferences),
        settings: loaded.settings,
      },
      profiles: profiles.value ?? emptyProfiles,
      relations: relations.value ?? emptyRelations,
      transfer: transfer.value ?? emptyTransfer,
    }
  })
  const error = ref('')
  const favorites = computed(() => new Set(preferences.value?.favorites ?? []))
  const pending = ref(false)
  const sendingSetting = ref(false)
  const busy = computed(() => pending.value || sendingSetting.value)
  const features = computed(() => catalog.value?.features ?? [])
  const settingsOptions = computed(() =>
    catalog.value?.settings.sections.flatMap((section) => section.options),
  )
  const color = computed(
    () =>
      settingsOptions.value?.find((option) => option.id === 'gui_color')!.value,
  )
  const featuresById = new Map<string, Feature>()
  const optionsById = new Map<string, Option>()
  const confirmedSettings = new Map<string, SettingValue>()
  const pendingSettings = new Map<string, SettingValue>()
  const queuedSettings = new Map<string, SettingValue>()
  const refreshQueue = new Set<string>()
  const resources = [
    '/api/settings',
    '/api/links',
    '/api/preferences',
    '/api/profiles',
    '/api/relations',
    '/api/transfer',
  ]
  let stream: EventSource | undefined
  let generation = 0
  let refreshing = false
  let languageProblem = ''
  let streamProblem = ''
  let stopped = false

  watch(color, (value) => {
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
  })

  function reportError(cause: unknown) {
    error.value = cause instanceof Error ? cause.message : t('Request failed')
  }

  function clearError() {
    error.value =
      languageProblem ||
      (preferences.value?.problem ? t(preferences.value.problem) : '')
  }

  function applySetting(edit: SettingValue) {
    const key = `${edit.featureId}:${edit.settingId}`
    if (edit.settingId === 'enabled') {
      featuresById.get(edit.featureId)!.enabled = Boolean(edit.value)
      return
    }
    const option = optionsById.get(key)!
    if (option.kind === 'KEYBIND' && edit.keyName === undefined) {
      option.keyName = String(edit.value)
    } else {
      option.value = edit.value
      if (edit.keyName !== undefined) option.keyName = edit.keyName
    }
  }

  function applyValues(values: SettingValue[]) {
    for (const edit of values) {
      const key = `${edit.featureId}:${edit.settingId}`
      confirmedSettings.set(key, edit)
      if (!pendingSettings.has(key)) applySetting(edit)
    }
  }

  function displayCatalog(next: ConfigurationCatalog) {
    catalog.value = next
    featuresById.clear()
    optionsById.clear()
    confirmedSettings.clear()
    for (const feature of [...catalog.value.features, catalog.value.settings]) {
      featuresById.set(feature.id, feature)
      if (feature.toggleable) {
        confirmedSettings.set(`${feature.id}:enabled`, {
          featureId: feature.id,
          settingId: 'enabled',
          value: feature.enabled,
        })
      }
      for (const section of feature.sections) {
        for (const option of section.options) {
          const key = `${feature.id}:${option.id}`
          optionsById.set(key, option)
          confirmedSettings.set(key, {
            featureId: feature.id,
            settingId: option.id,
            value: option.value,
            keyName: option.keyName,
          })
        }
      }
    }
    for (const edit of pendingSettings.values()) applySetting(edit)
  }

  async function readResource(path: string, version: number) {
    let apply: () => void
    if (path === '/api/settings') {
      const next = await loadCatalog()
      let texts: Catalog | null = null
      let failure: unknown
      try {
        texts = await loadLanguage(next.language)
      } catch (cause) {
        failure = cause
      }
      apply = () => {
        applyLanguage(next.language, texts)
        languageProblem = ''
        if (failure) {
          reportError(failure)
          languageProblem = error.value
        }
        displayCatalog(next)
      }
    } else if (path === '/api/links') {
      const next = await loadLinks()
      apply = () => {
        links.value = next
      }
    } else if (path.startsWith('/api/settings/')) {
      if (!catalog.value) {
        refreshQueue.add('/api/settings')
        return
      }
      const featureId = decodeURIComponent(path.slice('/api/settings/'.length))
      if (featureId !== 'values' && !featuresById.has(featureId)) return
      const values = await loadSettingValues(featureId)
      apply = () => applyValues(values)
    } else if (path === '/api/preferences') {
      const next = await loadPreferences()
      apply = () => {
        preferences.value = next
        if (next.problem) error.value = t(next.problem)
      }
    } else if (path === '/api/profiles') {
      const next = await loadProfiles()
      apply = () => {
        profiles.value = next
      }
    } else if (path === '/api/relations') {
      const next = await loadRelations()
      apply = () => {
        relations.value = next
      }
    } else if (path === '/api/transfer') {
      const next = await loadTransfer()
      apply = () => {
        transfer.value = next
      }
    } else return
    if (stopped) return
    if (version !== generation) refreshQueue.add(path)
    else apply()
  }

  function queueRefresh(paths: string[]) {
    for (const path of paths) refreshQueue.add(path)
    void flushRefreshes()
  }

  async function flushRefreshes() {
    if (refreshing || busy.value || stopped) return
    refreshing = true
    try {
      while (refreshQueue.size && !busy.value && !stopped) {
        const paths = [...refreshQueue]
        refreshQueue.clear()
        const version = generation
        await Promise.all(
          paths
            .filter(
              (path) =>
                !paths.includes('/api/settings') ||
                !path.startsWith('/api/settings/'),
            )
            .map(async (path) => {
              try {
                await readResource(path, version)
              } catch (cause) {
                if (stopped) return
                if (version === generation) reportError(cause)
                else refreshQueue.add(path)
              }
            }),
        )
      }
    } finally {
      refreshing = false
    }
  }

  async function mutate<T>(
    action: () => Promise<T>,
    accept: (result: T) => void | Promise<void>,
  ) {
    if (busy.value || stopped) return false
    pending.value = true
    generation++
    try {
      const result = await action()
      if (stopped) return false
      await accept(result)
      clearError()
      return true
    } catch (cause) {
      if (!stopped) reportError(cause)
      return false
    } finally {
      pending.value = false
      void flushRefreshes()
    }
  }

  async function toggleFavorite(id: string) {
    await mutate(
      () => changeFavorite(id, !favorites.value.has(id)),
      (next) => {
        preferences.value = next
      },
    )
  }

  async function profileAction(
    action: ProfileAction,
    id?: string,
    name?: string,
  ) {
    if (action === 'reset') {
      return mutate(resetSettings, async (values) => {
        applyValues(values)
        await readResource('/api/profiles', generation)
      })
    }
    return mutate(
      () => {
        switch (action) {
          case 'create':
            return createProfile(name!)
          case 'rename':
            return renameProfile(id!, name!)
          case 'delete':
            return deleteProfile(id!)
          case 'switch':
            return selectProfile(id!)
        }
      },
      async (next) => {
        profiles.value = next
        if (action === 'switch' || action === 'create')
          await readResource('/api/settings/values', generation)
      },
    )
  }

  async function hideLanguageHint() {
    return mutate(dismissLanguageHint, (next) => {
      preferences.value = next
    })
  }

  async function updateRelations(
    action: 'add' | 'remove',
    relation: RelationType,
    entries: RelationInput[],
  ): Promise<RelationResult[] | null> {
    let results: RelationResult[] | null = null
    await mutate(
      () => changeRelations(action, relation, entries),
      (next) => {
        relations.value = next.relations
        results = next.results
      },
    )
    return results
  }

  function setting(
    featureId: string,
    settingId: string,
    value: boolean | number | string,
  ) {
    if (pending.value || stopped) return
    const key = `${featureId}:${settingId}`
    const edit = { featureId, settingId, value }
    pendingSettings.set(key, edit)
    queuedSettings.set(key, edit)
    applySetting(edit)
    void flushSettings()
  }

  async function flushSettings() {
    if (sendingSetting.value) return
    sendingSetting.value = true
    generation++
    let failed = false
    try {
      while (queuedSettings.size && !stopped) {
        const [key, edit] = queuedSettings.entries().next().value!
        queuedSettings.delete(key)
        try {
          const values = await changeSetting(
            edit.featureId,
            edit.settingId,
            edit.value,
          )
          if (stopped) return
          applyValues(values)
          if (edit.featureId === 'webui' && edit.settingId === 'language')
            await readResource('/api/settings', generation)
          if (!failed) clearError()
        } catch (cause) {
          if (!stopped) reportError(cause)
          refreshQueue.add(
            `/api/settings/${encodeURIComponent(edit.featureId)}`,
          )
          failed = true
        } finally {
          if (!stopped && pendingSettings.get(key) === edit) {
            pendingSettings.delete(key)
            applySetting(confirmedSettings.get(key)!)
          }
        }
      }
    } finally {
      sendingSetting.value = false
      void flushRefreshes()
    }
  }

  async function importData(...args: Parameters<typeof applyData>) {
    if (busy.value || stopped) return false
    pending.value = true
    generation++
    try {
      await applyData(...args)
      if (stopped) return false
      await Promise.all(
        [
          '/api/settings/values',
          '/api/profiles',
          '/api/relations',
          '/api/transfer',
        ].map((path) => readResource(path, generation)),
      )
      clearError()
      return !stopped
    } finally {
      pending.value = false
      void flushRefreshes()
    }
  }

  onMounted(() => {
    queueRefresh(resources)
    stream = subscribe(
      queueRefresh,
      () => {
        if (streamProblem && error.value === streamProblem) clearError()
        streamProblem = ''
        queueRefresh(resources)
      },
      (cause) => {
        reportError(cause)
        streamProblem = error.value
      },
    )
  })
  onUnmounted(() => {
    stopped = true
    generation++
    stream?.close()
    refreshQueue.clear()
    pendingSettings.clear()
    queuedSettings.clear()
  })
  return {
    state,
    links,
    error,
    pending,
    busy,
    features,
    favorites,
    toggleFavorite,
    setting,
    profileAction,
    hideLanguageHint,
    updateRelations,
    importData,
    reportError,
  }
}
