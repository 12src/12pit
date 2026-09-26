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
import { computed, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Copy,
  Link,
  LogOut,
  Plus,
  Trash2,
} from '@lucide/vue'
import { changeSync, type State, type SyncState } from './api'

const props = defineProps<{
  state: SyncState
  baseUrl: string
  onlineLabel: string
}>()
const emit = defineEmits<{
  updated: [state: State]
  openSettings: []
}>()

const invitation = ref('')
const joinPassword = ref('')
const policyMode = ref<SyncState['joinMode']>(props.state.joinMode)
const policyPassword = ref('')
const busy = ref(false)
const error = ref('')
const copied = ref('')
const activeAction = ref<'create' | 'join' | null>(null)
const view = ref<'overview' | 'members'>('overview')
const confirm = ref<{
  kind: 'leave' | 'remove' | 'dissolve' | 'relations'
  id?: string
} | null>(null)

watch(
  () => props.state.channelId,
  (current, previous) => {
    if (!current && previous) {
      invitation.value = ''
      joinPassword.value = ''
      policyPassword.value = ''
      confirm.value = null
      view.value = 'overview'
    }
  },
)
watch(
  () => props.state.joinMode,
  (mode) => {
    policyMode.value = mode
  },
)
watch(
  () => props.state.status,
  (status) => {
    if (status !== 'joining') activeAction.value = null
  },
)

const connected = computed(() => props.state.status === 'connected')
const owner = computed(
  () =>
    props.state.members.find((member) => member.id === props.state.fingerprint)
      ?.role === 'owner',
)
const self = computed(() =>
  props.state.members.find((member) => member.id === props.state.fingerprint),
)
const lastOwner = computed(
  () =>
    owner.value &&
    props.state.members.filter((member) => member.role === 'owner').length ===
      1,
)
const inviteParts = computed(() =>
  /^([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}):([0-9a-f]{48})$/i.exec(
    invitation.value.trim(),
  ),
)
const joinChannelId = computed(() => {
  const value = invitation.value.trim()
  return (
    inviteParts.value?.[1] ??
    (/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value) ? value : '')
  )
})
const invalidJoinInput = computed(
  () => !!invitation.value.trim() && !joinChannelId.value,
)

async function perform(
  action: string,
  values: Record<string, unknown> = {},
): Promise<boolean> {
  if (busy.value) return false
  busy.value = true
  error.value = ''
  try {
    emit('updated', await changeSync(action, values))
    return true
  } catch (failure) {
    error.value =
      failure instanceof Error ? failure.message : 'Sync request failed'
    return false
  } finally {
    busy.value = false
  }
}

function join() {
  if (!joinChannelId.value || !props.baseUrl) return
  activeAction.value = 'join'
  void perform('join', {
    channelId: joinChannelId.value,
    inviteToken: inviteParts.value?.[2].toLowerCase() ?? '',
    password: inviteParts.value ? '' : joinPassword.value,
  })
}

async function savePolicy() {
  if (policyMode.value === 'password' && !policyPassword.value) return
  if (
    await perform('joinPolicy', {
      mode: policyMode.value,
      password: policyPassword.value,
    })
  ) {
    policyPassword.value = ''
  }
}

function create() {
  activeAction.value = 'create'
  void perform('create')
}

function openMembers() {
  confirm.value = null
  view.value = 'members'
}

function backToOverview() {
  confirm.value = null
  view.value = 'overview'
}

async function confirmAction(
  action: string,
  values: Record<string, unknown> = {},
) {
  if (await perform(action, values)) confirm.value = null
}

function access(
  member: SyncState['members'][number],
  role: 'owner' | 'writer' | 'reader',
  writeProfiles = member.writeProfiles,
  writeRelations = member.writeRelations,
) {
  void perform('access', {
    memberId: member.id,
    role,
    writeProfiles,
    writeRelations,
  })
}

function changeRole(member: SyncState['members'][number], event: Event) {
  const select = event.target as HTMLSelectElement
  const role = select.value as 'owner' | 'writer' | 'reader'
  select.value = member.role
  access(
    member,
    role,
    role === 'owner' ||
      (role === 'writer' && member.role === 'writer' && member.writeProfiles),
    role === 'owner' ||
      (role === 'writer' && member.role === 'writer' && member.writeRelations),
  )
}

function changePermission(
  member: SyncState['members'][number],
  key: 'writeProfiles' | 'writeRelations',
  event: Event,
) {
  const input = event.target as HTMLInputElement
  const enabled = input.checked
  input.checked = member[key]
  access(
    member,
    'writer',
    key === 'writeProfiles' ? enabled : member.writeProfiles,
    key === 'writeRelations' ? enabled : member.writeRelations,
  )
}

function memberAccess(member: SyncState['members'][number]) {
  if (member.role === 'owner') return 'Full access to the channel.'
  if (member.role === 'reader') return 'Can view shared data.'
  const permissions = [
    member.writeProfiles && 'profiles',
    member.writeRelations && 'relations',
  ].filter(Boolean)
  return permissions.length
    ? `Can edit ${permissions.join(' and ')}.`
    : 'No editing permissions.'
}

function selection(profiles: boolean, relations: boolean) {
  return perform('selection', { profiles, relations })
}

function selectProfiles(event: Event) {
  const input = event.target as HTMLInputElement
  const enabled = input.checked
  input.checked = props.state.syncProfiles
  void selection(enabled, props.state.syncRelations)
}

function selectRelations(event: Event) {
  const input = event.target as HTMLInputElement
  const enabled = input.checked
  input.checked = props.state.syncRelations
  if (enabled && !props.state.syncRelations) {
    confirm.value = { kind: 'relations' }
    return
  }
  void selection(props.state.syncProfiles, enabled)
}

function uploads(profiles: 'none' | 'current' | 'all', relations: boolean) {
  void perform('uploads', { profiles, relations })
}

function selectProfileUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const enabled = input.checked
  input.checked = props.state.profilesUpload !== 'none'
  uploads(enabled ? 'current' : 'none', props.state.relationsUpload)
}

function selectProfileScope(event: Event) {
  const select = event.target as HTMLSelectElement
  const scope = select.value as 'current' | 'all'
  select.value = props.state.profilesUpload
  uploads(scope, props.state.relationsUpload)
}

function selectRelationsUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const enabled = input.checked
  input.checked = props.state.relationsUpload
  uploads(props.state.profilesUpload, enabled)
}

async function copy(value: string, key: string) {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = key
    window.setTimeout(() => {
      if (copied.value === key) copied.value = ''
    }, 1600)
  } catch {
    error.value = 'Unable to copy to clipboard'
  }
}
</script>

<template>
  <div class="sync-page">
    <template v-if="view === 'members' && props.state.channelId">
      <button class="back" type="button" @click="backToOverview">
        <ArrowLeft :size="15" /> Sync
      </button>
      <div class="heading sync-heading">
        <h1>Channel access</h1>
      </div>
      <p v-if="error || props.state.message" class="form-error" role="alert">
        {{ error || props.state.message }}
      </p>
      <section
        class="sync-section sync-join-method"
        :class="{
          'sync-join-method-with-invitations':
            owner && props.state.joinMode === 'invite',
        }"
      >
        <form
          v-if="owner"
          class="sync-policy-form"
          @submit.prevent="savePolicy"
        >
          <div class="sync-policy-row">
            <h2>Join method</h2>
            <div class="sync-policy-controls">
              <select
                v-model="policyMode"
                aria-label="Join method"
                :disabled="busy || !connected"
              >
                <option value="open">Anyone with ID</option>
                <option value="password">Password only</option>
                <option value="invite">Invitation only</option>
              </select>
              <button
                class="secondary"
                type="submit"
                :disabled="
                  busy ||
                  !connected ||
                  (policyMode === props.state.joinMode &&
                    policyMode !== 'password') ||
                  (policyMode === 'password' && !policyPassword)
                "
              >
                Save
              </button>
            </div>
          </div>
          <input
            v-if="policyMode === 'password'"
            v-model="policyPassword"
            type="password"
            autocomplete="new-password"
            placeholder="New password"
            aria-label="New channel password"
            :disabled="busy || !connected"
          />
        </form>
        <div v-else class="sync-policy-row">
          <h2>Join method</h2>
          <span class="sync-notice">{{
            props.state.joinMode === 'invite'
              ? 'Invitation only'
              : props.state.joinMode === 'password'
                ? 'Password only'
                : 'Anyone with ID'
          }}</span>
        </div>
      </section>
      <section
        v-if="owner && props.state.joinMode === 'invite'"
        class="sync-section"
      >
        <div class="sync-section-heading">
          <h2>Invitations</h2>
          <button
            class="secondary"
            type="button"
            :disabled="busy || !connected"
            @click="perform('invite')"
          >
            <Plus :size="15" /> Create invitation
          </button>
        </div>
        <div v-if="props.state.invites.length" class="sync-invitations">
          <div
            v-for="invite in props.state.invites"
            :key="invite.id"
            class="sync-invitation"
          >
            <div class="sync-channel-id">
              <code>{{
                invite.token
                  ? `${props.state.channelId}:${invite.token}`
                  : `Invitation ${invite.id.slice(0, 8)}`
              }}</code>
              <button
                v-if="invite.token"
                class="icon-button"
                type="button"
                :title="copied === invite.id ? 'Copied' : 'Copy invitation'"
                :aria-label="
                  copied === invite.id ? 'Invitation copied' : 'Copy invitation'
                "
                @click="
                  copy(`${props.state.channelId}:${invite.token}`, invite.id)
                "
              >
                <Check v-if="copied === invite.id" :size="15" /><Copy
                  v-else
                  :size="15"
                />
              </button>
              <button
                class="icon-button sync-remove"
                type="button"
                title="Delete invitation"
                aria-label="Delete invitation"
                :disabled="busy || !connected"
                @click="perform('revokeInvite', { inviteId: invite.id })"
              >
                <Trash2 :size="15" />
              </button>
            </div>
            <small class="sync-expiry"
              >Expires {{ new Date(invite.expiresAt).toLocaleString() }}</small
            >
          </div>
        </div>
      </section>
      <section
        class="sync-section sync-members-list"
        aria-label="Channel members"
      >
        <div class="sync-section-heading">
          <h2>Members</h2>
          <span>{{ props.state.members.length }}</span>
        </div>
        <p v-if="!props.state.members.length" class="sync-notice">
          Waiting for channel members.
        </p>
        <div
          v-for="(member, index) in props.state.members"
          :key="member.id"
          class="sync-member"
          :class="{ 'sync-member-first': index === 0 }"
        >
          <div class="sync-member-heading">
            <div class="sync-member-name">
              <strong>{{ member.nickname || 'Unnamed device' }}</strong>
              <span
                v-if="member.id === props.state.fingerprint"
                class="sync-you"
                >You</span
              >
            </div>
            <div class="sync-member-actions">
              <select
                v-if="owner"
                :value="member.role"
                :disabled="
                  busy ||
                  !connected ||
                  (member.id === props.state.fingerprint && lastOwner)
                "
                :aria-label="`Role for ${member.nickname || member.id}`"
                @change="changeRole(member, $event)"
              >
                <option value="reader">Read</option>
                <option value="writer">Write</option>
                <option value="owner">Owner</option>
              </select>
              <span v-else class="sync-role">{{
                member.role === 'owner'
                  ? 'Owner'
                  : member.role === 'writer'
                    ? 'Write'
                    : 'Read'
              }}</span>
              <button
                v-if="owner && member.id !== props.state.fingerprint"
                class="icon-button sync-remove"
                type="button"
                :title="`Remove ${member.nickname || 'member'}`"
                :aria-label="`Remove ${member.nickname || 'member'}`"
                :disabled="busy || !connected"
                @click="confirm = { kind: 'remove', id: member.id }"
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </div>
          <code class="sync-fingerprint" :title="member.id">{{
            member.id
          }}</code>
          <div
            v-if="member.role === 'writer' && owner"
            class="sync-member-permissions"
          >
            <label
              ><input
                type="checkbox"
                :checked="member.writeProfiles"
                :disabled="!connected || busy"
                @change="changePermission(member, 'writeProfiles', $event)"
              />
              Allow profile edits</label
            >
            <label
              ><input
                type="checkbox"
                :checked="member.writeRelations"
                :disabled="!connected || busy"
                @change="changePermission(member, 'writeRelations', $event)"
              />
              Allow relation edits</label
            >
          </div>
          <p v-else class="sync-member-detail">{{ memberAccess(member) }}</p>
          <div
            v-if="confirm?.kind === 'remove' && confirm.id === member.id"
            class="sync-confirm sync-confirm-warning"
          >
            <span
              >Remove {{ member.nickname || 'this device' }} from the
              channel?</span
            >
            <div class="sync-confirm-actions">
              <button class="secondary" type="button" @click="confirm = null">
                Cancel
              </button>
              <button
                class="danger"
                type="button"
                :disabled="busy || !connected"
                @click="confirmAction('remove', { memberId: member.id })"
              >
                Remove member
              </button>
            </div>
          </div>
        </div>
      </section>
      <section class="sync-section sync-danger-zone">
        <div class="sync-section-heading"><h2>Danger zone</h2></div>
        <div class="sync-action-row">
          <span
            >Leave channel
            <small v-if="lastOwner">· Assign another owner first</small></span
          >
          <button
            class="secondary"
            type="button"
            :disabled="busy || !connected || lastOwner"
            @click="confirm = { kind: 'leave' }"
          >
            <LogOut :size="15" /> Leave
          </button>
        </div>
        <div
          v-if="confirm?.kind === 'leave'"
          class="sync-confirm sync-confirm-warning"
        >
          <span>Leave channel? Local data stays here.</span>
          <div class="sync-confirm-actions">
            <button class="secondary" type="button" @click="confirm = null">
              Cancel
            </button>
            <button
              class="danger"
              type="button"
              :disabled="busy || !connected"
              @click="confirmAction('leave')"
            >
              Leave
            </button>
          </div>
        </div>
        <template v-if="owner">
          <div class="sync-action-row">
            <span>Dissolve channel</span>
            <button
              class="secondary sync-remove"
              type="button"
              :disabled="busy || !connected"
              @click="confirm = { kind: 'dissolve' }"
            >
              <Trash2 :size="15" /> Dissolve
            </button>
          </div>
          <div
            v-if="confirm?.kind === 'dissolve'"
            class="sync-confirm sync-confirm-warning"
          >
            <span>Delete the channel and shared data for everyone?</span>
            <div class="sync-confirm-actions">
              <button class="secondary" type="button" @click="confirm = null">
                Cancel
              </button>
              <button
                class="danger"
                type="button"
                :disabled="busy || !connected"
                @click="confirmAction('dissolve')"
              >
                Dissolve
              </button>
            </div>
          </div>
        </template>
      </section>
    </template>
    <template v-else>
      <div class="heading sync-heading">
        <h1>Sync</h1>
        <div class="sync-heading-actions">
          <button
            class="sync-provider-link"
            type="button"
            @click="emit('openSettings')"
          >
            <span class="sync-provider-dot" aria-hidden="true" />{{
              props.onlineLabel
            }}
            <ArrowRight :size="14" />
          </button>
        </div>
      </div>
      <p v-if="error || props.state.message" class="form-error" role="alert">
        {{ error || props.state.message }}
      </p>

      <section v-if="!props.state.channelId" class="sync-section sync-start">
        <p v-if="!baseUrl" class="sync-setup-note" role="status">
          Provider not configured.
        </p>
        <div class="sync-choices">
          <div class="sync-choice sync-create-choice">
            <h2>Create channel</h2>
            <button
              class="primary"
              type="button"
              :disabled="busy || !baseUrl || props.state.status === 'joining'"
              @click="create"
            >
              <Plus :size="15" />
              {{
                props.state.status === 'joining' && activeAction === 'create'
                  ? 'Creating…'
                  : 'Create channel'
              }}
            </button>
          </div>
          <form class="sync-choice sync-join-choice" @submit.prevent="join">
            <h2>Join channel</h2>
            <label class="online-field" for="sync-invitation"
              >Channel ID or invitation code</label
            >
            <input
              id="sync-invitation"
              v-model="invitation"
              type="text"
              autocomplete="off"
              spellcheck="false"
              placeholder="Channel ID or invitation"
              :aria-invalid="invalidJoinInput"
              :disabled="busy || props.state.status === 'joining'"
            />
            <small v-if="invalidJoinInput" class="form-error"
              >Invalid channel ID or invitation code.</small
            >
            <input
              v-if="!inviteParts"
              v-model="joinPassword"
              type="password"
              autocomplete="current-password"
              placeholder="Password (if required)"
              aria-label="Channel password (if required)"
              :disabled="busy || props.state.status === 'joining'"
            />
            <button
              class="secondary"
              type="submit"
              :disabled="
                busy ||
                !baseUrl ||
                !joinChannelId ||
                props.state.status === 'joining'
              "
            >
              <Link :size="15" />
              {{
                props.state.status === 'joining' && activeAction === 'join'
                  ? 'Joining…'
                  : 'Join channel'
              }}
            </button>
          </form>
        </div>
      </section>

      <template v-else>
        <section class="sync-section sync-channel-summary">
          <div class="sync-channel-summary-row">
            <div class="sync-channel-summary-id">
              <div class="sync-channel-id">
                <code>{{ props.state.channelId }}</code>
                <button
                  class="icon-button"
                  type="button"
                  :title="copied === 'channel' ? 'Copied' : 'Copy channel ID'"
                  :aria-label="
                    copied === 'channel'
                      ? 'Channel ID copied'
                      : 'Copy channel ID'
                  "
                  @click="copy(props.state.channelId, 'channel')"
                >
                  <Check v-if="copied === 'channel'" :size="15" /><Copy
                    v-else
                    :size="15"
                  />
                </button>
              </div>
            </div>
            <button class="sync-access-link" type="button" @click="openMembers">
              <strong>Channel access</strong>
              <ArrowRight :size="17" aria-hidden="true" />
            </button>
          </div>
        </section>

        <section class="sync-section">
          <div class="sync-controls">
            <div class="sync-control-group">
              <h3>Receive</h3>
              <label class="sync-option"
                ><input
                  type="checkbox"
                  :checked="props.state.syncProfiles"
                  :disabled="busy"
                  @change="selectProfiles"
                /><span><strong>Profiles</strong></span></label
              >
              <label class="sync-option"
                ><input
                  type="checkbox"
                  :checked="props.state.syncRelations"
                  :disabled="busy"
                  @change="selectRelations"
                /><span><strong>Relations</strong></span></label
              >
              <div
                v-if="confirm?.kind === 'relations'"
                class="sync-confirm sync-confirm-warning"
              >
                <span
                  >Channel relations will replace your local list.
                  Continue?</span
                >
                <div class="sync-confirm-actions">
                  <button
                    class="secondary"
                    type="button"
                    @click="confirm = null"
                  >
                    Cancel
                  </button>
                  <button
                    class="danger"
                    type="button"
                    :disabled="busy"
                    @click="
                      confirmAction('selection', {
                        profiles: props.state.syncProfiles,
                        relations: true,
                      })
                    "
                  >
                    Use channel list
                  </button>
                </div>
              </div>
            </div>
            <div class="sync-control-group">
              <h3>Share</h3>
              <label class="sync-option"
                ><input
                  type="checkbox"
                  :checked="props.state.profilesUpload !== 'none'"
                  :disabled="
                    busy ||
                    (!self?.writeProfiles &&
                      props.state.profilesUpload === 'none')
                  "
                  @change="selectProfileUpload"
                /><span><strong>Profiles</strong></span></label
              >
              <label
                v-if="props.state.profilesUpload !== 'none'"
                class="sync-scope"
              >
                <span>Share</span>
                <select
                  :value="props.state.profilesUpload"
                  :disabled="busy || !self?.writeProfiles"
                  @change="selectProfileScope"
                >
                  <option value="current">Current profile</option>
                  <option value="all">All profiles</option>
                </select>
              </label>
              <label class="sync-option"
                ><input
                  type="checkbox"
                  :checked="props.state.relationsUpload"
                  :disabled="
                    busy ||
                    (!self?.writeRelations && !props.state.relationsUpload)
                  "
                  @change="selectRelationsUpload"
                /><span><strong>Relations</strong></span></label
              >
              <p
                v-if="self && (!self.writeProfiles || !self.writeRelations)"
                class="sync-permission-note"
              >
                Limited by your channel permissions.
              </p>
            </div>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
