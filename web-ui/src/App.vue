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
import { computed, nextTick, ref } from 'vue'
import { t } from './languages'
import FeaturesPage from './FeaturesPage.vue'
import PageScrollbar from './PageScrollbar.vue'
import ProfilesPage from './ProfilesPage.vue'
import RelationsPage from './RelationsPage.vue'
import ResizeTransition from './ResizeTransition.vue'
import SettingsPage from './SettingsPage.vue'
import TransferDialog from './TransferDialog.vue'
import UiNotice from './UiNotice.vue'
import { useKeyCapture } from './useKeyCapture'
import { useWebUiState } from './useWebUiState'

const {
  state,
  error,
  pending,
  busy,
  features,
  settings,
  showDetails,
  favorites,
  toggleFavorite,
  setting,
  profileAction,
  updateRelations,
  imported,
  reportError,
} = useWebUiState()
const { capturing, startCapture, cancelCapture } = useKeyCapture(
  pending,
  setting,
  reportError,
)
const page = ref<'features' | 'relations' | 'profiles' | 'settings'>('features')
const selectedId = ref<string | null>(null)
const selectedSubcategoryId = ref<string | null>(null)
const search = ref('')
const category = ref('all')
const transferMode = ref<'export' | 'import' | null>(null)
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

function selectPage(next: typeof page.value) {
  if (next === page.value && !selectedId.value) return
  pageTransition.value =
    next === page.value
      ? 'slide-right'
      : navIndex.value > navOrder[next]
        ? 'slide-down'
        : 'slide-up'
  page.value = next
  selectedId.value = null
  cancelCapture()
}

function openFeature(id: string) {
  pageTransition.value = 'slide-left'
  selectedId.value = id
  selectedSubcategoryId.value =
    features.value
      .find((feature) => feature.id === id)
      ?.sections.find((section) => section.subcategory)?.subcategory?.id ?? null
  cancelCapture()
}

function resetProfile() {
  cancelCapture()
  profileAction('reset')
}

function openTransfer(mode: 'export' | 'import') {
  cancelCapture()
  transferMode.value = mode
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
</script>

<template>
  <div class="app" :inert="transferMode !== null">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-lockup" :class="{ 'is-hidden': !state }"
          ><span class="brand-name">12<span>pit</span></span
          ><small class="brand-version" :title="state?.version ?? ''">{{
            state?.version ?? ''
          }}</small></span
        >
      </div>
      <nav
        :class="{ 'is-hidden': !state }"
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
    <main id="page-content" :aria-busy="!state">
      <ResizeTransition v-if="state" :name="pageTransition" class="page-stage">
        <div :key="pageKey" class="page-view">
          <FeaturesPage
            v-if="page === 'features'"
            v-model:search="search"
            v-model:category="category"
            v-model:subcategory="selectedSubcategoryId"
            :features="features"
            :favorites="favorites"
            :favorites-ready="state.webui.ready"
            :selected-id="selectedId"
            :capturing="capturing"
            :busy="pending"
            :show-details="showDetails"
            @open="openFeature"
            @back="selectPage('features')"
            @favorite="toggleFavorite"
            @change="setting"
            @capture="startCapture"
            @cancel="cancelCapture"
          />
          <RelationsPage
            v-else-if="page === 'relations'"
            :relations="state.relations"
            :busy="busy"
            :has-request-error="!!error"
            :update="updateRelations"
          />
          <SettingsPage
            v-else-if="page === 'settings'"
            :settings="settings"
            :profiles-loading="state.profiles.loadState === 'LOADING'"
            :busy="busy"
            :pending="pending || !state.webui.ready"
            :show-details="showDetails"
            :capturing="capturing"
            @reset="resetProfile"
            @transfer="openTransfer"
            @change="setting"
            @capture="startCapture"
            @cancel="cancelCapture"
          />
          <ProfilesPage
            v-else
            :profiles="state.profiles"
            :busy="busy"
            :action="profileAction"
          />
        </div>
      </ResizeTransition>
    </main>
  </div>
  <UiNotice
    v-if="error"
    class="error-notice"
    :dismiss-label="t('Dismiss error')"
    @dismiss="error = ''"
    >{{ error }}</UiNotice
  >
  <PageScrollbar v-if="state" :inert="transferMode !== null" />
  <TransferDialog
    v-if="state && transferMode"
    :mode="transferMode"
    :state="state"
    :accept-import="imported"
    @close="closeTransfer"
  />
</template>
