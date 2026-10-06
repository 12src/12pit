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
import { onBeforeUpdate, onUpdated, ref } from 'vue'
import { useSizeTransition } from './useSizeTransition'

defineProps<{ name: string }>()
const stage = ref<HTMLElement | null>(null)
const { hold, resize } = useSizeTransition(stage)
let leaving = false

function leave() {
  leaving = true
  hold()
}

function enter() {
  leaving = false
  void resize()
}

onBeforeUpdate(() => {
  if (!leaving) hold()
})
onUpdated(() => {
  if (!leaving) void resize()
})
</script>

<template>
  <div ref="stage" class="resize-stage">
    <Transition
      :name="name"
      mode="out-in"
      @before-leave="leave"
      @enter="enter"
      @enter-cancelled="resize()"
      @leave-cancelled="enter"
    >
      <slot />
    </Transition>
  </div>
</template>
