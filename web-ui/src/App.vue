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
import { computed, nextTick, ref, watch } from 'vue'
import { Brush, ExternalLink } from '@lucide/vue'
import { t } from './languages'
import FeaturesPage from './FeaturesPage.vue'
import LanguageNotice from './LanguageNotice.vue'
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
  state: loadedState,
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
} = useWebUiState()

const contentReady = ref(false)
// Matches the skeleton delay in skeleton.css.
const skeletonAt = performance.now() + 200

watch(
  loadedState,
  () => {
    if (performance.now() < skeletonAt) {
      contentReady.value = true
      return
    }
    window.setTimeout(
      () => {
        contentReady.value = true
      },
      skeletonAt + 300 - performance.now(),
    )
  },
  { once: true },
)

const state = computed(() => (contentReady.value ? loadedState.value : null))

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
const requestedLanguage = ref<string | null>(null)
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
      .find((feature) => feature.id === id)!
      .sections.find((section) => section.subcategory)?.subcategory?.id ?? null
  cancelCapture()
}

function resetProfile() {
  cancelCapture()
  profileAction('reset')
}

function openLanguage(value: string) {
  selectPage('settings')
  requestedLanguage.value = value
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
        <span v-if="state" class="brand-lockup">
          <span class="brand-name startup-fade">12<span>pit</span></span>
          <small class="brand-version" :title="state.version">{{
            state.version
          }}</small>
        </span>
      </div>
      <nav
        :aria-label="t('Pages')"
        :style="{ '--indicator-top': navIndicatorTop }"
      >
        <template v-if="state">
          <span class="nav-indicator startup-fade" aria-hidden="true" />
          <button
            class="startup-fade"
            :class="{ active: page === 'features' }"
            :aria-current="page === 'features' ? 'page' : undefined"
            @click="selectPage('features')"
          >
            {{ t('Features') }}
          </button>
          <button
            class="startup-fade"
            :class="{ active: page === 'profiles' }"
            :aria-current="page === 'profiles' ? 'page' : undefined"
            @click="selectPage('profiles')"
          >
            {{ t('Profiles') }}
          </button>
          <button
            class="startup-fade"
            :class="{ active: page === 'relations' }"
            :aria-current="page === 'relations' ? 'page' : undefined"
            @click="selectPage('relations')"
          >
            {{ t('Relations') }}
          </button>
          <button
            class="nav-settings startup-fade"
            :class="{ active: page === 'settings' }"
            :aria-current="page === 'settings' ? 'page' : undefined"
            @click="selectPage('settings')"
          >
            {{ t('Settings') }}
          </button>
        </template>
        <template v-else>
          <span
            v-for="index in 4"
            :key="index"
            class="nav-skeleton-item"
            :class="{ 'nav-settings': index === 4 }"
            aria-hidden="true"
          >
            <span class="skeleton" />
          </span>
        </template>
      </nav>
      <a class="editor-link startup-fade" href="./cosmetic.html">
        <Brush :size="16" aria-hidden="true" />
        {{ t('Pixel editor') }}
      </a>
      <div class="community-links">
        <template v-if="links">
          <a
            v-for="link in links"
            :key="link.id"
            class="startup-fade"
            :href="link.url"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="link.name"
            :title="link.name"
          >
            <span
              v-if="link.id === 'discord'"
              class="community-icon community-icon-discord"
              aria-hidden="true"
            />
            <span
              v-else-if="link.id === 'github'"
              class="community-icon community-icon-github"
              aria-hidden="true"
            />
            <ExternalLink v-else :size="20" aria-hidden="true" />
          </a>
        </template>
        <template v-else>
          <span
            v-for="index in 2"
            :key="index"
            class="community-skeleton"
            aria-hidden="true"
          >
            <span class="skeleton" />
          </span>
        </template>
      </div>
    </aside>
    <main id="page-content" :aria-busy="!state">
      <div v-if="!state" class="page-view startup-page" aria-hidden="true">
        <div class="heading">
          <span class="skeleton startup-title" />
          <span class="skeleton startup-search" />
        </div>
        <div class="startup-chips">
          <span v-for="index in 5" :key="index" class="skeleton" />
        </div>
        <div class="startup-list">
          <div v-for="index in 8" :key="index" class="startup-row">
            <span class="startup-row-text">
              <span class="skeleton" />
              <span class="skeleton" />
            </span>
            <span class="skeleton startup-chevron" />
            <span class="startup-favorite">
              <span class="skeleton startup-star" />
            </span>
            <span class="startup-toggle">
              <span class="skeleton startup-switch" />
            </span>
          </div>
        </div>
      </div>
      <ResizeTransition
        v-else
        :name="pageTransition"
        class="page-stage startup-fade"
      >
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
            :settings="state.webui.settings"
            :requested-language="requestedLanguage"
            :profiles-loading="state.profiles.loadState === 'LOADING'"
            :busy="busy"
            :pending="pending || !state.webui.ready"
            :capturing="capturing"
            @reset="resetProfile"
            @transfer="openTransfer"
            @change="setting"
            @capture="startCapture"
            @cancel="cancelCapture"
            @language-opened="requestedLanguage = null"
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
  <div class="toast-stack" :inert="transferMode !== null">
    <Transition name="toast" appear>
      <UiNotice
        v-if="error"
        class="error-notice"
        :dismiss-label="t('Dismiss error')"
        @dismiss="error = ''"
        >{{ error }}</UiNotice
      >
    </Transition>
    <LanguageNotice
      v-if="state"
      :state="state"
      :busy="busy"
      :disable="hideLanguageHint"
      @switch="openLanguage"
      @error="reportError"
    />
  </div>
  <PageScrollbar v-if="state" :inert="transferMode !== null" />
  <TransferDialog
    v-if="state && transferMode"
    :mode="transferMode"
    :state="state"
    :apply-import="importData"
    @close="closeTransfer"
  />
</template>
