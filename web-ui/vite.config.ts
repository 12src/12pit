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
import { relative, resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { MagicString, parse } from 'vue/compiler-sfc'
import ts from 'typescript'

function languageLocations(): Plugin {
  let root = ''
  return {
    name: 'language-locations',
    enforce: 'pre',
    configResolved(config) {
      root = resolve(config.root, '..')
    },
    transform(code, id) {
      if (id.includes('?')) return
      const path = relative(root, id).replaceAll('\\', '/')
      if (!path.startsWith('web-ui/src/') || !/\.(vue|ts)$/.test(path)) return
      const edits: { start: number; end: number; text: string }[] = []
      function collect(content: string, offset: number) {
        const file = ts.createSourceFile(
          id,
          content,
          ts.ScriptTarget.Latest,
          true,
        )
        function literal(node: ts.Node): boolean {
          return (
            ts.isStringLiteral(node) ||
            (ts.isBinaryExpression(node) &&
              node.operatorToken.kind === ts.SyntaxKind.PlusToken &&
              literal(node.left) &&
              literal(node.right))
          )
        }
        function visit(node: ts.Node) {
          if (
            ts.isCallExpression(node) &&
            ts.isIdentifier(node.expression) &&
            node.expression.text === 't' &&
            node.arguments.length &&
            literal(node.arguments[0])
          ) {
            const call = offset + node.getStart(file)
            const line = code.slice(0, call).split('\n').length
            const column = call - code.lastIndexOf('\n', call - 1)
            const start = offset + node.arguments[0].getStart(file)
            const end = offset + node.arguments[0].end
            const quote = code[start]
            const location = `${path}:${line}:${column}`
              .replaceAll('\\', '\\\\')
              .replaceAll(quote, `\\${quote}`)
            edits.push({
              start,
              end,
              text: `{ text: ${code.slice(start, end)}, location: ${quote}${location}${quote} }`,
            })
          }
          ts.forEachChild(node, visit)
        }
        visit(file)
      }
      if (path.endsWith('.vue')) {
        const { descriptor } = parse(code, { filename: id })
        for (const block of [descriptor.script, descriptor.scriptSetup]) {
          if (block) collect(block.content, block.loc.start.offset)
        }
        type TemplateRoot = NonNullable<
          NonNullable<typeof descriptor.template>['ast']
        >
        type Interpolation = Extract<
          TemplateRoot['children'][number],
          { type: 5 }
        >
        function template(
          node:
            | TemplateRoot
            | TemplateRoot['children'][number]
            | Interpolation['content']
            | undefined,
        ) {
          if (!node) return
          if (node.type === 4 && !node.isStatic) {
            collect(node.content, node.loc.start.offset)
          } else if (node.type === 5) {
            template(node.content)
          } else if (node.type === 1) {
            for (const prop of node.props) {
              if (prop.type === 7) {
                template(prop.exp)
                template(prop.arg)
              }
            }
            for (const child of node.children) template(child)
          } else if (node.type === 0) {
            for (const child of node.children) template(child)
          }
        }
        template(descriptor.template?.ast)
      } else {
        collect(code, 0)
      }
      if (!edits.length) return
      const result = new MagicString(code)
      for (const edit of edits) {
        result.overwrite(edit.start, edit.end, edit.text)
      }
      return {
        code: result.toString(),
        map: result.generateMap({
          source: id,
          includeContent: true,
          hires: true,
        }),
      }
    },
  }
}

export default defineConfig(() => {
  const target = process.env.WEB_UI_TARGET ?? 'http://127.0.0.1:60916'
  return {
    plugins: [languageLocations(), vue()],
    base: './',
    server: {
      host: '127.0.0.1',
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
          configure(proxy) {
            proxy.on('proxyReq', (request) =>
              request.setHeader('Origin', new URL(target).origin),
            )
          },
        },
      },
    },
  }
})
