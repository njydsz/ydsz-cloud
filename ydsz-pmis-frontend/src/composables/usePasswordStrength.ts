/**
 * 密码强度计算（与后端 PasswordPolicy.strength() 对齐）
 *
 * <p>评分规则（0-4）：
 * <ul>
 *   <li>长度 >= 8 加 1</li>
 *   <li>长度 >= 12 加 1</li>
 *   <li>同时包含大小写字母 加 1</li>
 *   <li>包含数字 加 1</li>
 *   <li>包含特殊字符 加 1</li>
 * </ul>
 *
 * <p>展示建议：
 * <ul>
 *   <li>0 - 极弱（红）</li>
 *   <li>1 - 弱（橙）</li>
 *   <li>2 - 中（黄）</li>
 *   <li>3 - 良（蓝）</li>
 *   <li>4 - 强（绿）</li>
 * </ul>
 */

const STRENGTH_RULES = [
  { re: /[A-Z]/, label: '大写字母' },
  { re: /[a-z]/, label: '小写字母' },
  { re: /\d/, label: '数字' },
  { re: /[^\w\s]/, label: '特殊字符' },
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
  const rules: StrengthRule[] = STRENGTH_RULES.map((r) => ({
    label: r.label,
    pass: r.re.test(pwd),
  }))

  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^\w\s]/.test(pwd)) score++
  if (score > 4) score = 4

  const map: Record<number, { level: StrengthResult['level']; color: string; text: string; percent: number }> = {
    0: { level: 'WEAKEST', color: '#f56c6c', text: '极弱', percent: 10 },
    1: { level: 'WEAK', color: '#e6a23c', text: '弱', percent: 30 },
    2: { level: 'MEDIUM', color: '#f0c40c', text: '中', percent: 55 },
    3: { level: 'STRONG', color: '#409eff', text: '良', percent: 80 },
    4: { level: 'STRONGEST', color: '#67c23a', text: '强', percent: 100 },
  }
  const meta = map[score] ?? map[0]

  const suggestions: string[] = []
  if (pwd.length < 8) suggestions.push('至少 8 位')
  else if (pwd.length < 12) suggestions.push('延长到 12 位以上更佳')
  if (!rules[0].pass) suggestions.push('加入大写字母')
  if (!rules[1].pass) suggestions.push('加入小写字母')
  if (!rules[2].pass) suggestions.push('加入数字')
  if (!rules[3].pass) suggestions.push('加入特殊字符')

  return {
    score,
    level: meta.level,
    color: meta.color,
    text: meta.text,
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
