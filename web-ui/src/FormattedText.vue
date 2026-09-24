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
import { computed, type CSSProperties } from 'vue'

const props = defineProps<{ text: string }>()
const colors = [
  '#000000',
  '#0000aa',
  '#00aa00',
  '#00aaaa',
  '#aa0000',
  '#aa00aa',
  '#ffaa00',
  '#aaaaaa',
  '#555555',
  '#5555ff',
  '#55ff55',
  '#55ffff',
  '#ff5555',
  '#ff55ff',
  '#ffff55',
  '#ffffff',
]

const runs = computed(() => {
  const result: { text: string; style: CSSProperties }[] = []
  let color: string | undefined
  let bold = false
  let italic = false
  let underline = false
  let strike = false
  let text = ''
  function flush() {
    if (!text) return
    result.push({
      text,
      style: {
        color,
        fontWeight: bold ? 'bold' : undefined,
        fontStyle: italic ? 'italic' : undefined,
        textDecoration:
          [underline && 'underline', strike && 'line-through']
            .filter(Boolean)
            .join(' ') || undefined,
      },
    })
    text = ''
  }
  for (let index = 0; index < props.text.length; index++) {
    const code = props.text[index + 1]?.toLowerCase()
    if (
      props.text[index] === '\u00a7' &&
      code &&
      /^[0-9a-flmnor]$/.test(code)
    ) {
      flush()
      if (code === 'r' || /^[0-9a-f]$/.test(code)) {
        color = code === 'r' ? undefined : colors[parseInt(code, 16)]
        bold = italic = underline = strike = false
      } else if (code === 'l') bold = true
      else if (code === 'm') strike = true
      else if (code === 'n') underline = true
      else if (code === 'o') italic = true
      index++
    } else {
      text += props.text[index]
    }
  }
  flush()
  return result
})
</script>

<template>
  <span
    ><span v-for="(run, index) in runs" :key="index" :style="run.style">{{
      run.text
    }}</span></span
  >
</template>
