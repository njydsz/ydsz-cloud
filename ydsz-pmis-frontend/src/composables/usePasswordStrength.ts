/**
 * @file 密码强度计算 composable
 * @description 与后端 PasswordPolicy.strength() 对齐的密码强度评估，支持 0-4 分制与改进建议
 * @module composables/usePasswordStrength
 *
 * 评分规则（0-4）：
 *  - 长度 >= 8 加 1
 *  - 长度 >= 12 加 1
 *  - 同时包含大小写字母 加 1
 *  - 包含数字 加 1
 *  - 包含特殊字符 加 1
 *
 * 展示建议：
 *  - 0 - 极弱（红）
 *  - 1 - 弱（橙）
 *  - 2 - 中（黄）
 *  - 3 - 良（蓝）
 *  - 4 - 强（绿）
 */

import i18n from '@/locales'

const STRENGTH_RULES = [
  { re: /[A-Z]/, labelKey: 'common.password.rule.uppercase' },
  { re: /[a-z]/, labelKey: 'common.password.rule.lowercase' },
  { re: /\d/, labelKey: 'common.password.rule.digit' },
  { re: /[^\w\s]/, labelKey: 'common.password.rule.special' },
] as const

export interface StrengthRule {
  label: string
  pass: boolean
}

export interface StrengthResult {
  /** 0-4 强度分 */
  score: number
  /** 展示名 */
  level: 'WEAKEST' | 'WEAK' | 'MEDIUM' | 'STRONG' | 'STRONGEST'
  /** 0-100 展示值 */
  percent: number
  /** 展示颜色（el 色板） */
  color: string
  /** 展示文案 */
  text: string
  /** 触发的具体规则（用于明细提示） */
  rules: StrengthRule[]
  /** 改进建议（未通过的规则） */
  suggestions: string[]
}

/**
 * 计算密码强度
 */
export function calcPasswordStrength(password: string | null | undefined): StrengthResult {
  const pwd = password ?? ''
  const t = i18n.global.t
  const rules: StrengthRule[] = STRENGTH_RULES.map((r) => ({
    label: t(r.labelKey),
    pass: r.re.test(pwd),
  }))

  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^\w\s]/.test(pwd)) score++
  if (score > 4) score = 4

  const map: Record<number, { level: StrengthResult['level']; color: string; textKey: string; percent: number }> = {
    0: { level: 'WEAKEST', color: '#f56c6c', textKey: 'common.password.level.weakest', percent: 10 },
    1: { level: 'WEAK', color: '#e6a23c', textKey: 'common.password.level.weak', percent: 30 },
    2: { level: 'MEDIUM', color: '#f0c40c', textKey: 'common.password.level.medium', percent: 55 },
    3: { level: 'STRONG', color: '#409eff', textKey: 'common.password.level.good', percent: 80 },
    4: { level: 'STRONGEST', color: '#67c23a', textKey: 'common.password.level.strong', percent: 100 },
  }
  const meta = map[score] ?? map[0]

  const suggestions: string[] = []
  if (pwd.length < 8) suggestions.push(t('common.password.suggestionText.min8'))
  else if (pwd.length < 12) suggestions.push(t('common.password.suggestionText.min12'))
  if (!rules[0].pass) suggestions.push(t('common.password.suggestionText.addUpper'))
  if (!rules[1].pass) suggestions.push(t('common.password.suggestionText.addLower'))
  if (!rules[2].pass) suggestions.push(t('common.password.suggestionText.addDigit'))
  if (!rules[3].pass) suggestions.push(t('common.password.suggestionText.addSpecial'))

  return {
    score,
    level: meta.level,
    color: meta.color,
    text: t(meta.textKey),
    percent: meta.percent,
    rules,
    suggestions,
  }
}

/**
 * Vue 组合式：响应式密码强度
 */
import { computed, type Ref } from 'vue'

export function usePasswordStrength(password: Ref<string | null | undefined>) {
  const result = computed(() => calcPasswordStrength(password.value))
  return { result }
}
