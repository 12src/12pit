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
export interface Option {
  id: string
  name: string
  description: string
  kind: 'BOOLEAN' | 'NUMBER' | 'CHOICE' | 'COLOR' | 'KEYBIND'
  value: boolean | number
  min?: number
  max?: number
  step?: number
  keyName?: string
  choices?: { value: number; name: string }[]
}

export interface Feature {
  id: string
  name: string
  description: string
  categoryId: string
  category: string
  toggleable: boolean
  enabled: boolean
  sections: { id: string; name: string; options: Option[] }[]
}

export type RelationType = 'FRIEND' | 'ENEMY'

export interface SyncState {
  status: string
  message: string | null
  channelId: string
  joinMode: 'invite' | 'password' | 'open'
  fingerprint: string
  syncProfiles: boolean
  syncRelations: boolean
  relationsReadOnly: boolean
  profilesUpload: 'none' | 'current' | 'all'
  relationsUpload: boolean
  members: {
    id: string
    nickname: string
    role: 'owner' | 'writer' | 'reader'
    writeProfiles: boolean
    writeRelations: boolean
  }[]
  remoteProfiles: { ownerId: string; id: string; name: string }[]
  invites: { id: string; token?: string; expiresAt: number }[]
}

export interface RelationEntry {
  uuid: string | null
  name: string
  relation: RelationType
}

export interface RelationInput {
  uuid?: string | null
  name: string
}

export interface RelationResult {
  name: string
  message: string
}

export interface State {
  version: string
  online: {
    provider: 'selfhosted' | '12pit'
    region: 'global'
    baseUrl: string
    endpoint: string
    nickname: string
  }
  sync: SyncState
  features: Feature[]
  profiles: {
    loadState: 'LOADING' | 'READY' | 'DEGRADED'
    activeId: string | null
    entries: { id: string; name: string }[]
    problems: string[]
    unpersisted: boolean
  }
  relations: {
    problem: string | null
    entries: RelationEntry[]
  }
}

export interface RelationConflict {
  id: string
  type: RelationType
  kind: 'group' | 'identity' | 'name'
  local: RelationEntry[]
  incoming: RelationEntry
}

export interface TransferPreview {
  profiles: { id: string; name: string }[]
  relations: Partial<Record<RelationType, number>>
  conflicts: RelationConflict[]
  fingerprint: string
}

async function request<T = State>(path: string, body?: object): Promise<T> {
  const response = await fetch(path, {
    method: body ? 'POST' : 'GET',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    cache: 'no-store',
  })
  const payload = await response.json()
  if (!response.ok) throw new Error(payload.error || 'Request failed')
  return payload as T
}

export const loadState = () => request('/api/state')
export const changeSetting = (
  featureId: string,
  settingId: string,
  value: boolean | number | string,
) => request('/api/setting', { featureId, settingId, value })
export const changeProfile = (action: string, id?: string, name?: string) =>
  request('/api/profile', { action, id, name })
export const changeRelations = (
  action: 'add' | 'remove',
  relation: RelationType,
  entries: RelationInput[],
) =>
  request<{ state: State; results: RelationResult[] }>('/api/relation', {
    action,
    relation,
    entries,
  })

export const changeSync = (
  action: string,
  values: Record<string, unknown> = {},
) => request<State>('/api/sync', { action, ...values })

export const changeOnline = (
  action: 'provider' | 'region' | 'selfHostedUrl' | 'nickname',
  value: string,
) => request<State>('/api/online', { action, [action]: value })

export const exportData = (profiles: string[], relations: RelationType[]) =>
  request<Record<string, unknown>>('/api/transfer/export', {
    profiles,
    relations,
  })
export const previewData = (data: object) =>
  request<TransferPreview>('/api/transfer/preview', { data })
export const applyData = (
  data: object,
  profiles: string[],
  relations: RelationType[],
  mode: 'merge' | 'replace',
  resolutions: Record<string, 'local' | 'imported'>,
  fingerprint: string,
) =>
  request<State>('/api/transfer/apply', {
    data,
    profiles,
    relations,
    mode,
    resolutions,
    fingerprint,
  })
