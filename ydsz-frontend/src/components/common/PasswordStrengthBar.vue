<!--
  @fileoverview 密码强度条（5 段）
  @description 展示 0-4 强度分对应的 5 段进度条 + 等级文字 + 改进建议：
  - Props: modelValue / password / showRules / showSuggestions / compact
            / showInput / placeholder
  - Emits: update:modelValue / change
  - 支持两种模式：自带输入框（v-model）或纯展示（:password）
  - 计算来自 @/composables/usePasswordStrength
  @module components/common/PasswordStrengthBar
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 密码强度条（5 段）
 *
 * 展示 0-4 强度分对应的 5 段进度条 + 等级文字 + 改进建议。
 * 支持两种模式：
 *  1. 自带输入框（v-model 双向绑定）
 *  2. 纯展示模式（仅 :password 传入值）
 *
 * 使用方式 1：自带输入框
 * ```vue
 * <PasswordStrengthBar v-model="form.password" :show-rules="true" />
 * ```
 *
 * 使用方式 2：纯展示
 * ```vue
 * <el-form-item label="新密码" prop="password">
 *   <el-input v-model="form.password" type="password" show-password />
 * </el-form-item>
 * <PasswordStrengthBar :password="form.password" :show-rules="true" />
 * ```
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { calcPasswordStrength, type StrengthResult } from '@/composables/usePasswordStrength'

const { t } = useI18n()

interface Props {
  /** 双向绑定：v-model 传入密码字符串 */
  modelValue?: string
  /** 纯展示：直接传入密码字符串 */
  password?: string
  /** 是否显示规则明细（每个规则的 pass 状态） */
  showRules?: boolean
  /** 是否显示改进建议 */
  showSuggestions?: boolean
  /** 紧凑模式（去除底部文字） */
  compact?: boolean
  /** 展示输入框（默认 true） */
  showInput?: boolean
  /** 输入框 placeholder */
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  password: '',
  showRules: false,
  showSuggestions: true,
  compact: false,
  showInput: true,
  placeholder: '',
})

const emit = defineEmits<{
  'update:modelValue': [v: string]
  'change': [result: StrengthResult]
}>()

const inner = ref<string>(props.modelValue || props.password || '')

watch(
  () => props.modelValue,
  (v) => {
    if (v !== inner.value) inner.value = v || ''
  },
)

watch(
  () => props.password,
  (v) => {
    if (v !== undefined && v !== inner.value) inner.value = v
  },
)

const result = computed<StrengthResult>(() => calcPasswordStrength(inner.value))

const placeholderText = computed(() => props.placeholder || t('common.password.placeholder'))

const segCount = 5
const segs = computed(() => {
  // score 0..4 → 0..5 段点亮
  const activeCount = result.value.score
  return Array.from({ length: segCount }, (_, i) => i < activeCount)
})

function onInput(v: string) {
  inner.value = v
  emit('update:modelValue', v)
  emit('change', result.value)
}
</script>

<template>
  <div class="pwd-strength" :class="{ 'pwd-strength--compact': compact }">
    <el-input
      v-if="showInput"
      :model-value="inner"
      type="password"
      show-password
      :placeholder="placeholderText"
      @update:model-value="onInput"
    >
      <template v-if="$slots.prefix" #prefix>
        <slot name="prefix" />
      </template>
    </el-input>

    <div class="bar" :title="t('common.password.strength', { text: result.text })">
      <div
        v-for="(active, i) in segs"
        :key="i"
        class="seg"
        :class="{ on: active }"
        :style="active ? { background: result.color } : undefined"
      />
    </div>

    <div v-if="!compact" class="meta">
      <span class="level" :style="{ color: result.color }">{{ result.text }}</span>
      <span class="score">{{ t('common.password.score', { score: result.score }) }}</span>
    </div>

    <ul v-if="showRules" class="rules">
      <li v-for="(r, i) in result.rules" :key="i" :class="{ pass: r.pass }">
        <el-icon v-if="r.pass" class="ok"><CircleCheckFilled /></el-icon>
        <el-icon v-else class="ng"><CircleCloseFilled /></el-icon>
        <span>{{ r.label }}</span>
      </li>
    </ul>

    <div v-if="showSuggestions && result.suggestions.length" class="suggestions">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ t('common.password.suggestion', { text: result.suggestions.join('；') }) }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.pwd-strength {
  width: 100%;
  .bar {
    display: flex;
    gap: 4px;
    margin-top: 6px;
    .seg {
      flex: 1;
      height: 6px;
      border-radius: 3px;
      background: #ebeef5;
      transition: background 0.25s ease;
    }
    .seg.on {
      box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.04);
    }
  }
  .meta {
    margin-top: 4px;
    font-size: 12px;
    .level {
      font-weight: 600;
    }
    .score {
      color: #909399;
      margin-left: 4px;
    }
  }
  .rules {
    list-style: none;
    margin: 8px 0 0;
    padding: 0;
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: #909399;
    li {
      display: flex;
      align-items: center;
      gap: 4px;
      transition: color 0.2s;
      &.pass {
        color: #67c23a;
      }
      .ok {
        color: #67c23a;
      }
      .ng {
        color: #f56c6c;
      }
    }
  }
  .suggestions {
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 4px;
    color: #909399;
    font-size: 12px;
  }
}
.pwd-strength--compact {
  .meta {
    display: none;
  }
}
</style>
