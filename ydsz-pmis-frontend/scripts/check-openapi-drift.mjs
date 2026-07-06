/**
 * OpenAPI Schema 漂移检测脚本
 *
 * 原理:
 *   CI 环境无法启动后端应用(依赖 Nacos/PG/Seata),故无法用 springdoc-openapi-maven-plugin
 *   生成 spec JSON。本脚本采用"静态签名哈希"方案:
 *   1. 扫描后端所有 Controller 文件的 @*Mapping 注解,生成签名哈希
 *   2. 读取 src/api/openapi/schema.d.ts 头部的签名注释
 *   3. 比较哈希,不匹配则判定为漂移,CI 失败
 *
 * 使用:
 *   node scripts/check-openapi-drift.mjs           # 检查漂移
 *   node scripts/check-openapi-drift.mjs --update   # 更新 schema.d.ts 中的签名
 *
 * 退出码:
 *   0 = 无漂移
 *   1 = 存在漂移(schema.d.ts 不存在或签名不匹配)
 */
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const ROOT = join(__dirname, '..')

const BACKEND_DIR = resolve(ROOT, '../ydsz-pmis-backend')
const SCHEMA_FILE = join(ROOT, 'src/api/openapi/schema.d.ts')
const SIGNATURE_MARKER = '@openapi-signature'

/**
 * 递归查找所有 Controller.java 文件
 */
function findControllers(dir, results = []) {
  const entries = readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      findControllers(fullPath, results)
    } else if (entry.name.endsWith('Controller.java')) {
      results.push(fullPath)
    }
  }
  return results
}

/**
 * 从 Controller 文件提取 API 端点签名
 * 扫描 @*Mapping 注解及其路径
 */
function extractEndpoints(filePath) {
  const content = readFileSync(filePath, 'utf-8')
  const endpoints = []

  // 匹配类级 @RequestMapping(Java 注解路径只用双引号或单引号)
  const classMapping = content.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/)
  const basePath = classMapping ? classMapping[1] : ''

  // 匹配方法级 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping
  const methodPattern = /@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["']\s*\))?/g
  let match
  while ((match = methodPattern.exec(content)) !== null) {
    const method = match[1].toUpperCase()
    const path = match[2] || ''
    endpoints.push(`${method} ${basePath}${path}`)
  }

  return endpoints
}

/**
 * 生成所有 Controller 的签名哈希
 */
function generateSignature() {
  if (!existsSync(BACKEND_DIR)) {
    console.error(`[check-openapi-drift] 后端目录不存在: ${BACKEND_DIR}`)
    return null
  }

  const controllers = findControllers(BACKEND_DIR)
  if (controllers.length === 0) {
    console.warn('[check-openapi-drift] 未找到任何 Controller 文件')
    return null
  }

  // 收集所有端点并排序,确保顺序稳定
  const allEndpoints = []
  for (const controller of controllers.sort()) {
    const endpoints = extractEndpoints(controller)
    allEndpoints.push(...endpoints)
  }
  allEndpoints.sort()

  const hash = createHash('sha256')
  hash.update(allEndpoints.join('\n'))
  return {
    hash: hash.digest('hex').substring(0, 16),
    controllerCount: controllers.length,
    endpointCount: allEndpoints.length,
  }
}

/**
 * 从 schema.d.ts 头部读取已记录的签名
 */
function readRecordedSignature() {
  if (!existsSync(SCHEMA_FILE)) {
    return null
  }
  const content = readFileSync(SCHEMA_FILE, 'utf-8')
  const match = content.match(new RegExp(`${SIGNATURE_MARKER}:\\s*([a-f0-9]+)`))
  return match ? match[1] : null
}

/**
 * 主流程
 */
function main() {
  const isUpdate = process.argv.includes('--update')
  const signature = generateSignature()

  if (!signature) {
    console.error('[check-openapi-drift] 无法生成签名,请检查后端目录')
    process.exit(1)
  }

  console.log(`[check-openapi-drift] 当前签名: ${signature.hash}`)
  console.log(`[check-openapi-drift] Controller 数: ${signature.controllerCount}`)
  console.log(`[check-openapi-drift] 端点数: ${signature.endpointCount}`)

  if (isUpdate) {
    console.log('[check-openapi-drift] --update 模式: 仅输出签名,不修改文件')
    console.log(`\n请在重新生成 schema.d.ts 后,在文件头部添加:`)
    console.log(`  // ${SIGNATURE_MARKER}: ${signature.hash}`)
    process.exit(0)
  }

  const recorded = readRecordedSignature()
  if (!recorded) {
    console.error(`[check-openapi-drift] schema.d.ts 不存在或缺少签名标记`)
    console.error(`[check-openapi-drift] 文件: ${SCHEMA_FILE}`)
    console.error(`[check-openapi-drift] 期望签名: ${signature.hash}`)
    console.error(`[check-openapi-drift] 请在本地启动后端后运行: pnpm openapi:gen`)
    console.error(`[check-openapi-drift] 然后在文件头部添加: // ${SIGNATURE_MARKER}: ${signature.hash}`)
    process.exit(1)
  }

  console.log(`[check-openapi-drift] 已记录签名: ${recorded}`)

  if (recorded !== signature.hash) {
    console.error('[check-openapi-drift] 检测到漂移! 后端 Controller 已变更但 schema.d.ts 未更新')
    console.error(`[check-openapi-drift] 期望: ${signature.hash}`)
    console.error(`[check-openapi-drift] 实际: ${recorded}`)
    console.error('[check-openapi-drift] 请在本地启动后端后运行: pnpm openapi:gen')
    console.error(`[check-openapi-drift] 然后更新签名: // ${SIGNATURE_MARKER}: ${signature.hash}`)
    process.exit(1)
  }

  console.log('[check-openapi-drift] 签名匹配,无漂移')
  process.exit(0)
}

main()
