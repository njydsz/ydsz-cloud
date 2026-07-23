#!/usr/bin/env node
/**
 * i18n 硬编码中文检测脚本（P3-11 落地）。
 *
 * 扫描 src/views/ 和 src/components/ 下的 .vue 文件，
 * 检测 template 中未使用 t() / $t() 包裹的硬编码中文字符串。
 *
 * 用法：
 *   node scripts/check-i18n-coverage.mjs [--threshold=80]
 *
 * 退出码：
 *   0 — 硬编码中文占比低于阈值（通过）
 *   1 — 硬编码中文占比超过阈值（失败）
 *
 * @author ydsz-team
 * @since 1.3.1 (P3-11)
 */
import { readFileSync, readdirSync, statSync } from 'fs'
import { join, extname } from 'path'

const ROOT = join(process.cwd(), 'src')
const THRESHOLD = parseInt(process.argv.find(a => a.startsWith('--threshold='))?.split('=')[1] || '90', 10)

// 匹配 template 区域中的硬编码中文（不在 {{ }} 内，不在 t() / $t() 内）
const CHINESE_REGEX = /[\u4e00-\u9fa5]+/
// 匹配 t('...') 或 $t('...') 调用
const I18N_CALL_REGEX = /\b(?:\$?t)\s*\([^)]+\)/g
// 匹配 <!-- 注释 -->
const COMMENT_REGEX = /<!--[\s\S]*?-->/g
// 匹配 <script> 块
const SCRIPT_REGEX = /<script[\s\S]*?<\/script>/g

let totalFiles = 0
let filesWithHardcoded = 0
let totalChineseStrings = 0
let totalI18nCalls = 0
const violations = []

function scanDirectory(dir) {
  const entries = readdirSync(dir)
  for (const entry of entries) {
    const fullPath = join(dir, entry)
    const stat = statSync(fullPath)
    if (stat.isDirectory()) {
      scanDirectory(fullPath)
    } else if (extname(entry) === '.vue') {
      scanVueFile(fullPath)
    }
  }
}

function scanVueFile(filePath) {
  totalFiles++
  const content = readFileSync(filePath, 'utf-8')

  // 提取 template 部分（移除 script 和 注释）
  const withoutScript = content.replace(SCRIPT_REGEX, '')
  const withoutComments = withoutScript.replace(COMMENT_REGEX, '')
  const templateMatch = withoutComments.match(/<template>([\s\S]*?)<\/template>/)
  if (!templateMatch) return

  const template = templateMatch[1]

  // 统计 i18n 调用
  const i18nCalls = template.match(I18N_CALL_REGEX) || []
  totalI18nCalls += i18nCalls.length

  // 检测硬编码中文（在标签内容中，不在 {{ }} 内，不在属性值中）
  // 匹配 >中文< 或 >中文 文本< 格式
  const textNodeRegex = />([^<{]+)</g
  let match
  let fileHasHardcoded = false
  while ((match = textNodeRegex.exec(template)) !== null) {
    const text = match[1].trim()
    if (text && CHINESE_REGEX.test(text)) {
      totalChineseStrings++
      if (!fileHasHardcoded) {
        filesWithHardcoded++
        fileHasHardcoded = true
      }
      if (violations.length < 50) {
        violations.push({
          file: filePath.replace(process.cwd() + '/', ''),
          text: text.substring(0, 50),
        })
      }
    }
  }
}

// 执行扫描
const dirsToScan = ['views', 'components'].map(d => join(ROOT, d))
for (const dir of dirsToScan) {
  try {
    scanDirectory(dir)
  } catch (e) {
    console.error(`Warning: Could not scan ${dir}: ${e.message}`)
  }
}

// 计算覆盖率
const totalChinese = totalChineseStrings + totalI18nCalls
const coverageRate = totalChinese > 0
  ? ((totalI18nCalls / totalChinese) * 100).toFixed(1)
  : '100.0'
const hardcodedRate = totalChinese > 0
  ? ((totalChineseStrings / totalChinese) * 100).toFixed(1)
  : '0.0'

console.log('========================================')
console.log('  i18n 覆盖率检测报告 (P3-11)')
console.log('========================================')
console.log(`扫描 .vue 文件数: ${totalFiles}`)
console.log(`含硬编码中文文件: ${filesWithHardcoded}`)
console.log(`i18n t() 调用总数: ${totalI18nCalls}`)
console.log(`硬编码中文文案数: ${totalChineseStrings}`)
console.log(`i18n 覆盖率: ${coverageRate}%`)
console.log(`硬编码占比: ${hardcodedRate}%`)
console.log(`阈值(硬编码占比): ${THRESHOLD}%`)
console.log('')

if (violations.length > 0) {
  console.log('前 50 个硬编码中文示例:')
  violations.forEach((v, i) => {
    console.log(`  ${i + 1}. [${v.file}] "${v.text}"`)
  })
  console.log('')
}

if (parseFloat(hardcodedRate) > THRESHOLD) {
  console.log(`❌ 硬编码中文占比 ${hardcodedRate}% 超过阈值 ${THRESHOLD}%，请使用 t('key') 替换硬编码中文`)
  process.exit(1)
} else {
  console.log(`✅ 硬编码中文占比 ${hardcodedRate}% 在阈值 ${THRESHOLD}% 以内`)
  process.exit(0)
}
