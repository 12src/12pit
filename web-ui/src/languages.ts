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
import { shallowRef, unref, type MaybeRef } from 'vue'
import type { Option } from './api'

export type Catalog = Record<string, string | Record<string, string>>
export type SourceText = string | { text: string; location: string }

const texts = shallowRef<Catalog | null>(null)
const catalogs = new Map<string, Promise<Catalog>>()
export async function loadLanguage(language: string) {
  if (language === 'en-us') return null
  let catalog = catalogs.get(language)
  if (!catalog) {
    catalog = fetch(`/api/languages/${encodeURIComponent(language)}`, {
      cache: 'no-store',
    })
      .then(async (response) => {
        if (!response.ok) throw new Error('Could not load language')
        return (await response.json()) as Catalog
      })
      .catch((cause) => {
        catalogs.delete(language)
        throw cause
      })
    catalogs.set(language, catalog)
  }
  return catalog
}

export function applyLanguage(language: string, catalog: Catalog | null) {
  document.documentElement.lang = language
  texts.value = catalog
}

export const t = translate.bind(null, texts)

export function translate(
  catalog: MaybeRef<Catalog | null>,
  source: SourceText,
  ...parameters: unknown[]
) {
  const original = typeof source === 'string' ? source : source.text
  const values = unref(catalog)
  if (values === null && parameters.length === 0) return original
  const target = values?.[original]
  const translated =
    typeof target === 'string'
      ? target
      : typeof source === 'string'
        ? original
        : (target?.[source.location] ??
          target?.[
            source.location.slice(0, source.location.lastIndexOf(':'))
          ] ??
          original)
  return parameters.length
    ? translated.replace(/\{([0-9]+)\}/g, (_, index: string) =>
        String(parameters[Number(index)]),
      )
    : translated
}

export function systemLanguage(option: Option) {
  const locales = navigator.languages.length
    ? navigator.languages
    : [navigator.language]
  for (const locale of locales) {
    const exact = option.choices!.find(
      (choice) => choice.locale === locale.toLowerCase(),
    )
    if (exact) return exact
    let requested: Intl.Locale
    try {
      requested = new Intl.Locale(locale).maximize()
    } catch {
      continue
    }
    // The registry uses Crowdin region tags, so maximize() fills in the script.
    const match = option.choices!.find((choice) => {
      const available = new Intl.Locale(choice.locale!).maximize()
      return (
        available.language === requested.language &&
        available.script === requested.script
      )
    })
    if (match) return match
  }
}
