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
import {
  nextTick,
  onBeforeUpdate,
  onMounted,
  onUnmounted,
  onUpdated,
  type Ref,
} from 'vue'

export function useSizeTransition(
  element: Ref<HTMLElement | null>,
  options: { automatic?: boolean; fade?: () => boolean } = {},
) {
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
  let height: number | undefined
  let restingHeight = ''
  let restingOverflow = ''
  let holding = false
  let fade = false
  let animations: Animation[] = []
  const opacity = new Map<Element, string>()
  let revision = 0

  function cancel() {
    for (const animation of animations) animation.cancel()
    animations = []
  }

  function hold() {
    const node = element.value
    if (!node) return
    const current = node.getBoundingClientRect().height
    revision++
    if (!holding) {
      restingHeight = node.style.height
      restingOverflow = node.style.overflow
    }
    holding = true
    fade = (options.fade?.() ?? false) || (height !== undefined && fade)
    opacity.clear()
    if (options.fade) {
      for (const child of node.children)
        opacity.set(child, window.getComputedStyle(child).opacity)
    }
    cancel()
    height = current
    node.style.height = `${current}px`
    node.style.overflow = 'clip'
  }

  async function resize() {
    const version = revision
    await nextTick()
    const node = element.value
    if (!node || version !== revision || height === undefined) return
    const start = node.getBoundingClientRect().height
    cancel()
    node.style.height = restingHeight
    const target = node.getBoundingClientRect().height
    height = undefined
    let fading = false
    for (const value of opacity.values()) {
      if (Number(value) < 1) fading = true
    }
    if (motion.matches || (Math.abs(start - target) < 0.5 && !fading)) {
      node.style.height = restingHeight
      node.style.overflow = restingOverflow
      holding = false
      return
    }
    const timing = {
      duration: fade ? 280 : 180,
      easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
    }
    const sizing = node.animate(
      fade
        ? [
            { height: `${start}px`, offset: 0 },
            { height: `${start}px`, offset: 0.2 },
            { height: `${target}px`, offset: 0.8 },
            { height: `${target}px`, offset: 1 },
          ]
        : [{ height: `${start}px` }, { height: `${target}px` }],
      timing,
    )
    animations.push(sizing)
    sizing.onfinish = () => {
      if (version !== revision) return
      node.style.overflow = restingOverflow
      holding = false
      animations = []
    }
    node.style.height = restingHeight
    if (options.fade) {
      for (const child of node.children) {
        const startOpacity = opacity.get(child) ?? '1'
        if (!fade && Number(startOpacity) >= 1) continue
        animations.push(
          child.animate(
            fade
              ? [
                  { opacity: startOpacity, offset: 0 },
                  { opacity: 0, offset: 0.2 },
                  { opacity: 0, offset: 0.8 },
                  { opacity: 1, offset: 1 },
                ]
              : [{ opacity: startOpacity }, { opacity: 1 }],
            timing,
          ),
        )
      }
    }
  }

  function finish() {
    revision++
    cancel()
    if (element.value && holding) {
      element.value.style.height = restingHeight
      element.value.style.overflow = restingOverflow
    }
    holding = false
    height = undefined
    opacity.clear()
  }

  if (options.automatic) {
    onBeforeUpdate(hold)
    onUpdated(() => void resize())
  }
  onMounted(() => motion.addEventListener('change', finish))
  onUnmounted(() => {
    finish()
    motion.removeEventListener('change', finish)
  })
  return { hold, resize }
}
