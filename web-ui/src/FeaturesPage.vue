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
import { computed, ref } from 'vue'
import { ChevronRight, Star } from '@lucide/vue'
import type { Feature } from './api'
import { t } from './languages'
import FormattedText from './FormattedText.vue'
import ResizeTransition from './ResizeTransition.vue'
import SearchField from './SearchField.vue'
import SettingSections from './SettingSections.vue'
import ToggleSwitch from './ToggleSwitch.vue'

const props = defineProps<{
  features: Feature[]
  favorites: Set<string>
  favoritesReady: boolean
  selectedId: string | null
  capturing: { featureId: string; settingId: string } | null
  busy: boolean
  showDetails: boolean
}>()
const emit = defineEmits<{
  open: [id: string]
  back: []
  favorite: [id: string]
  change: [
    featureId: string,
    settingId: string,
    value: boolean | number | string,
  ]
  capture: [featureId: string, settingId: string]
  cancel: []
}>()
const search = defineModel<string>('search', { required: true })
const category = defineModel<string>('category', { required: true })
const selectedSubcategoryId = defineModel<string | null>('subcategory', {
  required: true,
})
const categoryTransition = ref('slide-left')
const subcategoryTransition = ref('slide-left')
const nameOrder = new Intl.Collator('en', { sensitivity: 'base' })

const current = computed(() =>
  props.features.find((feature) => feature.id === props.selectedId),
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
    props.features.map((feature) => [feature.categoryId, feature.category]),
  ).entries(),
])
const visible = computed(() => {
  const query = search.value.trim().toLowerCase()
  return props.features
    .filter(
      (feature) =>
        (category.value === 'all' || feature.categoryId === category.value) &&
        `${feature.name} ${feature.description}`.toLowerCase().includes(query),
    )
    .map((feature) => ({
      feature,
      favorite: props.favorites.has(feature.id),
      sortName: feature.name.replace(/\u00a7[0-9a-flmnor]/gi, ''),
      hasDetails: feature.sections.some(
        (section) => section.options.length > 0,
      ),
    }))
    .sort(
      (a, b) =>
        Number(b.favorite) - Number(a.favorite) ||
        nameOrder.compare(a.sortName, b.sortName),
    )
})
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
  emit('cancel')
}
</script>

<template>
  <template v-if="!current">
    <div class="heading heading-features">
      <h1>{{ t('Features') }}</h1>
      <SearchField v-model="search" :label="t('Search features')" />
    </div>
    <div class="category-buttons" role="group" :aria-label="t('Category')">
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
    <ResizeTransition :name="categoryTransition">
      <div :key="category" class="tab-view feature-list">
        <div
          v-for="{ feature, favorite, hasDetails } in visible"
          :key="feature.id"
          class="feature-row list-row"
        >
          <component
            :is="hasDetails ? 'button' : 'span'"
            class="feature-link"
            :type="hasDetails ? 'button' : undefined"
            @click="hasDetails && emit('open', feature.id)"
          >
            <span>
              <strong><FormattedText :text="feature.name" /></strong>
              <small v-if="showDetails"
                ><FormattedText :text="feature.description"
              /></small>
            </span>
            <ChevronRight v-if="hasDetails" :size="16" />
          </component>
          <button
            type="button"
            class="icon-button feature-favorite"
            :aria-label="t('Favorite {0}', feature.name)"
            :aria-pressed="favorite"
            :disabled="busy || !favoritesReady"
            :title="
              favorite ? t('Remove from favorites') : t('Add to favorites')
            "
            @click="emit('favorite', feature.id)"
          >
            <Star :size="18" aria-hidden="true" />
          </button>
          <ToggleSwitch
            v-if="feature.toggleable"
            :model-value="feature.enabled"
            :label="t('Enable {0}', feature.name)"
            :disabled="busy"
            @update:model-value="emit('change', feature.id, 'enabled', $event)"
          />
        </div>
        <p v-if="!visible.length" class="empty">
          {{ t('No matching features.') }}
        </p>
      </div>
    </ResizeTransition>
  </template>
  <template v-else>
    <div class="breadcrumbs" role="navigation" :aria-label="t('Breadcrumb')">
      <ol>
        <li>
          <button type="button" @click="emit('back')">
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
    <ResizeTransition :name="subcategoryTransition">
      <div :key="selectedSubcategoryId ?? ''" class="tab-view">
        <SettingSections
          :feature-id="current.id"
          :sections="currentSections"
          :capturing="capturing"
          :busy="busy"
          :show-details="showDetails"
          @change="(id, value) => emit('change', current!.id, id, value)"
          @capture="emit('capture', current!.id, $event)"
          @cancel="emit('cancel')"
        />
      </div>
    </ResizeTransition>
  </template>
</template>
