#!/usr/bin/env node
/**
 * YDSZ 前端 any 警告扫描脚本（批次 19 P2-4）
 *
 * 扫描 src/ 下所有 .ts/.vue 文件，统计 @typescript-eslint/no-explicit-any 规则违规
 * 按文件 + 目录分组输出，便于团队分批收口
 *
 * 用法：
 *   node scripts/scan-any-warnings.mjs
 *   node scripts/scan-any-warnings.mjs --top=20
 *   node scripts/scan-any-warnings.mjs --json > any-report.json
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = join(__filename, '..')
const ROOT = join(__dirname, '..')
const SRC_DIR = join(ROOT, 'src')
const TOP_N = Number((process.argv.find((a) => a.startsWith('--top=')) || '').split('=')[1]) || 20
const JSON_MODE = process.argv.includes('--json')

const TARGET_EXTS = ['.ts', '.vue', '.tsx']
const IGNORE_DIRS = ['node_modules', 'dist', 'coverage', '__tests__']
const ANY_REGEX = /\bany\b/g
const ANY_USAGE_REGEX = /:\s*any\b|<any\b|as\s+any\b|\(\s*any\s*\)/g

/** 遍历目录 */
function walkDir(dir) {
  const result = []
  for (const name of readdirSync(dir)) {
    if (IGNORE_DIRS.includes(name)) continue
    const full = join(dir, name)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      result.push(...walkDir(full))
    } else if (TARGET_EXTS.some((ext) => name.endsWith(ext))) {
      result.push(full)
    }
  }
  return result
}

/** 扫描单个文件 */
function scanFile(file) {
  const content = readFileSync(file, 'utf8')
  const lines = content.split('\n')
  const matches = []
  lines.forEach((line, idx) => {
    // 排除注释行
    const trimmed = line.trim()
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
      return
    }
    // 排除 import 路径中的 any（如 "any-chart"）
    if (line.includes("from '") || line.includes('import(')) return
    const found = line.match(ANY_USAGE_REGEX)
    if (found) {
      matches.push({ line: idx + 1, count: found.length, snippet: trimmed.slice(0, 80) })
    }
  })
  return matches
}

/** 主流程 */
const files = walkDir(SRC_DIR)
const report = []
let totalAny = 0

for (const file of files) {
  const matches = scanFile(file)
  if (matches.length === 0) continue
  const relPath = relative(ROOT, file)
  const count = matches.reduce((s, m) => s + m.count, 0)
  totalAny += count
  report.push({ file: relPath, count, matches })
}

report.sort((a, b) => b.count - a.count)

if (JSON_MODE) {
  console.log(JSON.stringify({ totalAny, topN: TOP_N, files: report }, null, 2))
  process.exit(0)
}

console.log('='.repeat(70))
console.log(`YDSZ 前端 any 警告扫描报告`)
console.log(`扫描时间: ${new Date().toLocaleString('zh-CN')}`)
console.log(`扫描范围: ${SRC_DIR}`)
console.log(`扫描文件: ${files.length}`)
console.log(`违规总数: ${totalAny}`)
console.log('='.repeat(70))

console.log(`\n【Top ${TOP_N} 文件】`)
console.log('-'.repeat(70))
console.log('排名 | 违规数 | 文件路径')
for (let i = 0; i < Math.min(TOP_N, report.length); i++) {
  const r = report[i]
  console.log(`${(i + 1).toString().padStart(2)}   | ${r.count.toString().padStart(4)}   | ${r.file}`)
}

console.log(`\n【按模块汇总】`)
const moduleStats = {}
for (const r of report) {
  const parts = r.file.split('/')
  const mod = parts.slice(2, 4).join('/')
  moduleStats[mod] = (moduleStats[mod] || 0) + r.count
}
const sortedMods = Object.entries(moduleStats).sort((a, b) => b[1] - a[1])
for (const [mod, count] of sortedMods) {
  const bar = '█'.repeat(Math.min(50, Math.floor(count / 2)))
  console.log(`  ${mod.padEnd(40)} ${count.toString().padStart(4)} ${bar}`)
}

console.log('\n【收口建议】')
console.log('1. 优先处理 Top 20 文件')
console.log('2. 优先收口 src/api/execution/、src/views/execution/ 下的 reconcile/alert 目录')
console.log('3. 替换为 src/types/api.ts 中的 ApiResponse<T> / PageData<T> / BusinessEntity')
console.log('4. 收口后运行 pnpm lint 验证')
console.log('='.repeat(70))

process.exit(totalAny > 0 ? 1 : 0)
