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
import { onUnmounted, ref, type Ref } from 'vue'
import { t } from './languages'

export function useKeyCapture(
  busy: Readonly<Ref<boolean>>,
  setting: (featureId: string, settingId: string, value: string) => void,
  reportError: (cause: unknown) => void,
) {
  const capturing = ref<{ featureId: string; settingId: string } | null>(null)

  function cancelCapture() {
    capturing.value = null
    window.removeEventListener('keydown', onKey, true)
  }

  function startCapture(featureId: string, settingId: string) {
    if (busy.value) return
    cancelCapture()
    capturing.value = { featureId, settingId }
    window.addEventListener('keydown', onKey, true)
  }

  function onKey(event: KeyboardEvent) {
    if (!capturing.value || event.repeat) return
    event.preventDefault()
    event.stopPropagation()
    if (event.code === 'Escape') {
      cancelCapture()
      return
    }
    const name =
      event.code === '' && event.key === 'Shift'
        ? 'RSHIFT'
        : keyName(event.code)
    if (!name) {
      reportError(new Error(t('This key cannot be bound')))
      cancelCapture()
      return
    }
    const target = capturing.value
    cancelCapture()
    setting(target.featureId, target.settingId, name)
  }

  function keyName(code: string): string | null {
    if (code === 'Backspace' || code === 'Delete') return 'NONE'
    if (/^Key[A-Z]$/.test(code)) return code.slice(3)
    if (/^Digit[0-9]$/.test(code)) return code.slice(5)
    if (/^F([1-9]|1[0-9])$/.test(code)) return code
    if (/^Numpad[0-9]$/.test(code)) return `NUMPAD${code.slice(6)}`
    return (
      (
        {
          ShiftLeft: 'LSHIFT',
          ShiftRight: 'RSHIFT',
          ControlLeft: 'LCONTROL',
          ControlRight: 'RCONTROL',
          AltLeft: 'LMENU',
          AltRight: 'RMENU',
          Space: 'SPACE',
          Tab: 'TAB',
          Enter: 'RETURN',
          NumpadEnter: 'NUMPADENTER',
          NumpadAdd: 'ADD',
          NumpadSubtract: 'SUBTRACT',
          NumpadMultiply: 'MULTIPLY',
          NumpadDivide: 'DIVIDE',
          NumpadDecimal: 'DECIMAL',
          NumpadEqual: 'NUMPADEQUALS',
          NumpadComma: 'NUMPADCOMMA',
          NumpadClear: 'CLEAR',
          ArrowUp: 'UP',
          ArrowDown: 'DOWN',
          ArrowLeft: 'LEFT',
          ArrowRight: 'RIGHT',
          Backquote: 'GRAVE',
          Minus: 'MINUS',
          Equal: 'EQUALS',
          BracketLeft: 'LBRACKET',
          BracketRight: 'RBRACKET',
          Backslash: 'BACKSLASH',
          Semicolon: 'SEMICOLON',
          Quote: 'APOSTROPHE',
          Comma: 'COMMA',
          Period: 'PERIOD',
          Slash: 'SLASH',
          PageUp: 'PRIOR',
          PageDown: 'NEXT',
          Home: 'HOME',
          End: 'END',
          Insert: 'INSERT',
          CapsLock: 'CAPITAL',
          NumLock: 'NUMLOCK',
          ScrollLock: 'SCROLL',
          Pause: 'PAUSE',
          PrintScreen: 'SYSRQ',
          ContextMenu: 'APPS',
          MetaLeft: 'LMETA',
          MetaRight: 'RMETA',
          IntlYen: 'YEN',
          Lang1: 'KANA',
          Lang2: 'KANJI',
          Convert: 'CONVERT',
          NonConvert: 'NOCONVERT',
        } as Record<string, string>
      )[code] ?? null
    )
  }

  onUnmounted(cancelCapture)
  return { capturing, startCapture, cancelCapture }
}
