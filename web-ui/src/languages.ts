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
import { shallowRef } from 'vue'

const texts = shallowRef<Record<string, string> | null>(null)
const catalogs = new Map<string, Promise<Record<string, string>>>()
export async function loadLanguage(language: string) {
  if (language === 'en-us') return null
  let catalog = catalogs.get(language)
  if (!catalog) {
    catalog = fetch(`/api/languages/${encodeURIComponent(language)}`, {
      cache: 'no-store',
    })
      .then(async (response) => {
        if (!response.ok) throw new Error(t('Could not load language'))
        return (await response.json()) as Record<string, string>
      })
      .catch((cause) => {
        catalogs.delete(language)
        throw cause
      })
    catalogs.set(language, catalog)
  }
  return catalog
}

export function applyLanguage(catalog: Record<string, string> | null) {
  texts.value = catalog
}

export function t(source: string, ...parameters: unknown[]) {
  const values = texts.value
  if (values === null && parameters.length === 0) return source
  const translated = values?.[source] ?? source
  return parameters.length
    ? translated.replace(/\{([0-9]+)\}/g, (_, index: string) =>
        String(parameters[Number(index)]),
      )
    : translated
}
