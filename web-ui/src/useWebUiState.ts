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
  changeProfile,
  changeRelations,
  changeSetting,
  loadState,
  type RelationInput,
  type RelationResult,
  type RelationType,
  type State,
} from './api'
import { applyLanguage, loadLanguage, t } from './languages'

type SettingEdit = {
  featureId: string
  optionId: string
  value: boolean | number | string
}

export function useWebUiState() {
  const state = ref<State | null>(null)
  const error = ref('')
  const pending = ref(false)
  const sendingSetting = ref(false)
  const busy = computed(() => pending.value || sendingSetting.value)
  const features = computed(
    () =>
      state.value?.features.filter((feature) => feature.id !== 'webui') ?? [],
  )
  const settings = computed(() =>
    state.value?.features.find((feature) => feature.id === 'webui'),
  )
  const settingsOptions = computed(
    () => settings.value?.sections.flatMap((section) => section.options) ?? [],
  )
  const color = computed(
    () =>
      settingsOptions.value.find((option) => option.id === 'gui_color')?.value,
  )
  const showDetails = computed(() =>
    Boolean(
      settingsOptions.value.find((option) => option.id === 'show_details')
        ?.value ?? false,
    ),
  )
  const pendingSettings = new Map<string, SettingEdit>()
  const queuedSettings = new Map<string, SettingEdit>()
  let stream: EventSource | undefined
  let requestVersion = 0
  let refreshQueued = false
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

  async function displayState(next: State, version: number, clearError = true) {
    let catalog: Record<string, string> | null = null
    let languageError: unknown
    try {
      catalog = await loadLanguage(next.language)
    } catch (cause) {
      languageError = cause
    }
    if (stopped || version !== requestVersion) return false
    for (const edit of pendingSettings.values()) applySetting(next, edit)
    applyLanguage(catalog)
    state.value = next
    if (languageError) reportError(languageError)
    else if (clearError) error.value = ''
    return true
  }

  async function refresh(clearError = true) {
    const version = ++requestVersion
    try {
      const next = await loadState()
      if (stopped || version !== requestVersion) return
      if (busy.value) {
        refreshQueued = true
        return
      }
      await displayState(next, version, clearError)
    } catch (cause) {
      if (!stopped && version === requestVersion) reportError(cause)
    }
  }

  function refreshAfterMutation(clearError = true) {
    if (!stopped && refreshQueued && !busy.value) {
      refreshQueued = false
      void refresh(clearError)
    }
  }

  function serverChanged() {
    if (busy.value) refreshQueued = true
    else void refresh(false)
  }

  async function mutate<T>(
    action: () => Promise<T>,
    snapshot: (result: T) => State,
  ): Promise<T | null> {
    if (busy.value || stopped) return null
    pending.value = true
    const version = ++requestVersion
    try {
      const result = await action()
      return (await displayState(snapshot(result), version)) ? result : null
    } catch (cause) {
      if (!stopped) reportError(cause)
      return null
    } finally {
      pending.value = false
      refreshAfterMutation(false)
    }
  }

  async function profileAction(action: string, id?: string, name?: string) {
    return (
      (await mutate(
        () => changeProfile(action, id, name),
        (result) => result,
      )) !== null
    )
  }

  async function updateRelations(
    action: 'add' | 'remove',
    relation: RelationType,
    entries: RelationInput[],
  ): Promise<RelationResult[] | null> {
    const result = await mutate(
      () => changeRelations(action, relation, entries),
      (result) => result.state,
    )
    return result?.results ?? null
  }

  function setting(
    featureId: string,
    optionId: string,
    value: boolean | number | string,
  ) {
    if (pending.value || stopped) return
    const key = `${featureId}:${optionId}`
    const edit = { featureId, optionId, value }
    pendingSettings.set(key, edit)
    queuedSettings.set(key, edit)
    if (state.value) applySetting(state.value, edit)
    void flushSettings()
  }

  async function flushSettings() {
    if (sendingSetting.value) return
    sendingSetting.value = true
    let failed = false
    try {
      while (queuedSettings.size && !stopped) {
        const [key, edit] = queuedSettings.entries().next().value!
        queuedSettings.delete(key)
        const version = ++requestVersion
        try {
          const next = await changeSetting(
            edit.featureId,
            edit.optionId,
            edit.value,
          )
          if (pendingSettings.get(key) === edit) pendingSettings.delete(key)
          await displayState(next, version, !failed)
        } catch (cause) {
          if (pendingSettings.get(key) === edit) pendingSettings.delete(key)
          if (!stopped) reportError(cause)
          refreshQueued = true
          failed = true
        }
      }
    } finally {
      sendingSetting.value = false
      refreshAfterMutation(!failed)
    }
  }

  async function imported(next: State) {
    if (stopped) return false
    pending.value = true
    try {
      return await displayState(next, ++requestVersion)
    } finally {
      pending.value = false
      refreshAfterMutation(false)
    }
  }

  onMounted(() => {
    void refresh()
    stream = new EventSource('/api/events')
    stream.onopen = serverChanged
    stream.onmessage = serverChanged
  })
  onUnmounted(() => {
    stopped = true
    requestVersion++
    stream?.close()
    pendingSettings.clear()
    queuedSettings.clear()
  })
  return {
    state,
    error,
    pending,
    busy,
    features,
    settings,
    showDetails,
    setting,
    profileAction,
    updateRelations,
    imported,
    reportError,
  }
}
