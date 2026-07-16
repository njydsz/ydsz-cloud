/**
 * OpenAPI → TypeScript 类型自动生成脚本（批次 20 P1-3 补齐）
 *
 * 工作流:
 *   1. 后端服务启动后, 访问 http://localhost:9000/v3/api-docs 获取 JSON 规范
 *   2. 本脚本调用 openapi-typescript 将其转为 src/api/openapi/schema.d.ts
 *   3. 前端代码可 import { paths, components } from '@/api/openapi/schema'
 *
 * 使用:
 *   npm run openapi:gen
 *   npm run openapi:gen -- http://your-gateway:9000/v3/api-docs
 */
import { execSync } from 'node:child_process'
import { writeFileSync, mkdirSync, existsSync, readdirSync, readFileSync, unlinkSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import http from 'node:http'
import https from 'node:https'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const ROOT = join(__dirname, '..')

const SPEC_URL = process.argv[2] || process.env.OPENAPI_URL || 'http://localhost:9000/v3/api-docs'
const OUT_DIR = join(ROOT, 'src', 'api', 'openapi')
const OUT_FILE = join(OUT_DIR, 'schema.d.ts')

/**
 * 下载 OpenAPI 规范 JSON
 */
function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http
    lib
      .get(url, (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} for ${url}`))
          return
        }
        let data = ''
        res.on('data', (chunk) => (data += chunk))
        res.on('end', () => {
          try {
            resolve(JSON.parse(data))
          } catch (e) {
            reject(new Error(`解析 JSON 失败: ${e.message}`))
          }
        })
      })
      .on('error', reject)
  })
}

/**
 * 生成 .d.ts 头注释(含签名标记)
 */
function genHeader(spec) {
  const title = spec?.info?.title || 'Unknown'
  const version = spec?.info?.version || '0.0.0'
  // 调用 check-openapi-drift.mjs 的签名计算逻辑
  const signature = computeControllerSignature()
  return `/**
 * OpenAPI 3.0 规范自动生成的 TypeScript 类型
 *
 * @openapi-signature: ${signature}
 *
 * 来源: ${SPEC_URL}
 * 标题: ${title}
 * 版本: ${version}
 *
 * 该文件由 scripts/openapi-gen.mjs 自动生成, 请勿手动编辑
 *   重新生成命令: npm run openapi:gen
 *   签名由 scripts/check-openapi-drift.mjs 校验
 */
`
}

/**
 * 计算后端 Controller 签名(与 check-openapi-drift.mjs 保持一致)
 */
function computeControllerSignature() {
  const BACKEND_DIR = resolve(ROOT, '../ydsz-pmis-backend')
  if (!existsSync(BACKEND_DIR)) return 'unknown'

  const controllers = []
  function findControllers(dir) {
    const entries = readdirSync(dir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = join(dir, entry.name)
      if (entry.isDirectory()) findControllers(fullPath)
      else if (entry.name.endsWith('Controller.java')) controllers.push(fullPath)
    }
  }
  findControllers(BACKEND_DIR)

  const allEndpoints = []
  for (const controller of controllers.sort()) {
    const content = readFileSync(controller, 'utf-8')
    const classMapping = content.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/)
    const basePath = classMapping ? classMapping[1] : ''
    const methodPattern = /@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["']\s*\))?/g
    let match
    while ((match = methodPattern.exec(content)) !== null) {
      allEndpoints.push(`${match[1].toUpperCase()} ${basePath}${match[2] || ''}`)
    }
  }
  allEndpoints.sort()

  return createHash('sha256')
    .update(allEndpoints.join('\n'))
    .digest('hex')
    .substring(0, 16)
}

async function main() {
  console.log(`[openapi-gen] 下载 OpenAPI 规范: ${SPEC_URL}`)

  let spec
  try {
    spec = await fetchJson(SPEC_URL)
  } catch (e) {
    console.error(`[openapi-gen] 失败: ${e.message}`)
    console.error(`[openapi-gen] 提示: 请确认后端服务已启动, 且 ${SPEC_URL} 可访问`)
    process.exit(1)
  }

  if (!spec.openapi && !spec.swagger) {
    console.error('[openapi-gen] 响应不是 OpenAPI 规范 (缺少 openapi/swagger 字段)')
    process.exit(1)
  }

  if (!existsSync(OUT_DIR)) {
    mkdirSync(OUT_DIR, { recursive: true })
  }

  // 检查 openapi-typescript 是否可用
  let hasOpenapiTs = false
  try {
    execSync('npx --no-install openapi-typescript --version', { stdio: 'ignore' })
    hasOpenapiTs = true
  } catch {
    hasOpenapiTs = false
  }

  const header = genHeader(spec)

  if (hasOpenapiTs) {
    console.log('[openapi-gen] 使用 openapi-typescript 生成...')
    const tmpJson = join(OUT_DIR, 'spec.tmp.json')
    writeFileSync(tmpJson, JSON.stringify(spec, null, 2))
    try {
      execSync(`npx openapi-typescript "${tmpJson}" -o "${OUT_FILE}"`, {
        stdio: 'inherit',
        cwd: ROOT,
      })
    } finally {
      try {
        unlinkSync(tmpJson)
      } catch {
        // ignore
      }
    }
  } else {
    console.warn('[openapi-gen] openapi-typescript 未安装, 输出最简 schema 占位')
    const placeholder = `${header}
export interface paths {}
export interface components {}
export interface operations {}
`
    writeFileSync(OUT_FILE, placeholder)
  }

  // 附加: 写入 index.ts 包装层
  const indexFile = join(OUT_DIR, 'index.ts')
  const indexContent = `/**
 * OpenAPI 生成的类型入口
 * 用法: import type { paths, components } from '@/api/openapi'
 */
export type { paths, components, operations } from './schema'

/** API 基础 URL (从环境变量读取) */
export const OPENAPI_BASE_URL =
  (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) || '/api'
`
  writeFileSync(indexFile, indexContent)

  console.log(`[openapi-gen] 完成 ✓`)
  console.log(`[openapi-gen] 类型文件: ${OUT_FILE}`)
  console.log(`[openapi-gen] 入口: ${indexFile}`)
}

main().catch((e) => {
  console.error('[openapi-gen] 异常:', e)
  process.exit(1)
})
