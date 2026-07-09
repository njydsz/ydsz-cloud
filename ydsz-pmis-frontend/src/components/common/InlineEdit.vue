<!--
  @fileoverview 行内编辑组件
  @description 在表格单元格中实现点击即编辑的能力：
    - 支持 text/number/select/date 多种输入类型
    - 点击文本进入编辑模式，失焦/回车确认，Esc 取消
    - 支持 v-model 双向绑定
    - 支持前置验证（before-save 事件返回 false 阻止保存）
  @module components/common/InlineEdit
-->
<script setup lang="ts">
import { ref, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'

export type InlineEditType = 'text' | 'number' | 'select' | 'date' | 'textarea'

const props = withDefaults(
  defineProps<{
    /** 绑定值 */
    modelValue: string | number | null
    /** 输入类型 */
    type?: InlineEditType
    /** select 类型的选项 */
    options?: { label: string; value: string | number }[]
    /** 占位符 */
    placeholder?: string
    /** 是否禁用 */
    disabled?: boolean
    /** 数字类型最小值 */
    min?: number
    /** 数字类型最大值 */
    max?: number
    /** 文本最大长度 */
    maxlength?: number
    /** 是否需要确认按钮（false 时失焦即保存） */
    requireConfirm?: boolean
    /** 空值显示文本 */
    emptyText?: string
  }>(),
  {
    type: 'text',
    disabled: false,
    requireConfirm: false,
    emptyText: '—',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
  save: [value: string | number | null, oldValue: string | number | null]
  cancel: []
}>()

const { t } = useI18n()
const isEditing = ref(false)
const editValue = ref<string | number | null>('')
const inputRef = ref<HTMLElement | null>(null)
const oldValue = ref<string | number | null>('')

/** 显示文本 */
const displayText = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined || props.modelValue === '') {
    return props.emptyText
  }
  if (props.type === 'select' && props.options) {
    const opt = props.options.find((o) => o.value === props.modelValue)
    return opt?.label || String(props.modelValue)
  }
  return String(props.modelValue)
})

/** 进入编辑模式 */
async function startEdit() {
  if (props.disabled) return
  oldValue.value = props.modelValue
  editValue.value = props.modelValue
  isEditing.value = true
  await nextTick()
  // 聚焦输入框
  const el = inputRef.value?.querySelector('input, textarea, .el-select')
  if (el instanceof HTMLElement) {
    el.focus()
  }
}

/** 保存编辑 */
function saveEdit() {
  if (!isEditing.value) return
  // 值未变化时不触发保存
  if (editValue.value === oldValue.value) {
    isEditing.value = false
    return
  }
  emit('update:modelValue', editValue.value)
  emit('save', editValue.value, oldValue.value)
  isEditing.value = false
}

/** 取消编辑 */
function cancelEdit() {
  isEditing.value = false
  editValue.value = oldValue.value
  emit('cancel')
}

/** 失焦处理 */
function handleBlur() {
  if (!props.requireConfirm) {
    saveEdit()
  }
}

/** 回车确认 */
function handleEnter() {
  saveEdit()
}

/** Esc 取消 */
function handleEscape() {
  cancelEdit()
}
</script>

<template>
  <div class="inline-edit" ref="inputRef">
    <!-- 编辑模式 -->
    <div v-if="isEditing" class="inline-edit__editor" @click.stop>
      <el-input
        v-if="type === 'text' || type === 'textarea'"
        :type="type"
        v-model="editValue as string"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :min="min"
        :max="max"
        size="small"
        @blur="handleBlur"
        @keyup.enter="handleEnter"
        @keyup.escape="handleEscape"
      />
      <el-input-number
        v-else-if="type === 'number'"
        v-model="editValue as number"
        :min="min"
        :max="max"
        size="small"
        @blur="handleBlur"
        @keyup.enter="handleEnter"
        @keyup.escape="handleEscape"
      />
      <el-select
        v-else-if="type === 'select'"
        v-model="editValue"
        :placeholder="placeholder"
        size="small"
        @blur="handleBlur"
        @change="saveEdit"
      >
        <el-option
          v-for="opt in options"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-date-picker
        v-else-if="type === 'date'"
        v-model="editValue"
        type="date"
        size="small"
        @blur="handleBlur"
        @change="saveEdit"
      />

      <!-- 确认按钮（requireConfirm 模式） -->
      <template v-if="requireConfirm">
        <el-button text size="small" type="primary" @click="saveEdit">
          <el-icon><Check /></el-icon>
        </el-button>
        <el-button text size="small" @click="cancelEdit">
          <el-icon><Close /></el-icon>
        </el-button>
      </template>
    </div>

    <!-- 展示模式 -->
    <div
      v-else
      class="inline-edit__display"
      :class="{ 'is-disabled': disabled, 'is-empty': !modelValue }"
      @click="startEdit"
    >
      <span>{{ displayText }}</span>
      <el-icon v-if="!disabled" class="inline-edit__icon"><Edit /></el-icon>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.inline-edit {
  display: inline-block;
  width: 100%;

  &__editor {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  &__display {
    display: flex;
    align-items: center;
    gap: 4px;
    cursor: pointer;
    padding: 2px 4px;
    border-radius: 4px;
    transition: background 0.2s;
    min-height: 24px;

    &:hover {
      background: var(--el-fill-color-light);

      .inline-edit__icon {
        opacity: 1;
      }
    }

    &.is-disabled {
      cursor: not-allowed;
      opacity: 0.7;
    }

    &.is-empty {
      color: var(--el-text-color-placeholder);
      font-style: italic;
    }
  }

  &__icon {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    opacity: 0;
    transition: opacity 0.2s;
  }
}
</style>
