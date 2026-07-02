<!--
  @file 行内编辑组件 (P2-15 行内编辑)
  @description 双击文本进入编辑态, 支持 text/number/select/date 四种编辑类型,
               Enter 提交、Escape 取消、blur 自动提交, 支持自定义校验规则.
               适用于表格单元格、详情页字段的就地编辑场景.
  @module components/common/InlineEdit
-->
<script setup lang="ts">
/**
 * 行内编辑
 *
 * - 双击(display-value)进入编辑态, 显示对应类型的编辑控件
 * - Enter 提交(commit 事件 + update:modelValue), Escape 取消, blur 自动提交
 * - rules 校验失败时不退出编辑态并保持焦点
 */
import { ref, nextTick } from 'vue'
import { Edit } from '@element-plus/icons-vue'

type EditType = 'text' | 'number' | 'select' | 'date'

interface Props {
  modelValue: string | number | undefined
  type?: EditType
  options?: Array<{ label: string; value: string | number }>
  width?: string
  placeholder?: string
  disabled?: boolean
  rules?: Array<(val: any) => string | true>
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  width: '200px',
  placeholder: '请输入',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number]
  commit: [value: string | number]
}>()

const editing = ref(false)
const editValue = ref<string | number>('')
const inputRef = ref<any>(null)
const errorMsg = ref('')

const startEdit = () => {
  if (props.disabled) return
  editing.value = true
  editValue.value = props.modelValue ?? ''
  errorMsg.value = ''
  nextTick(() => {
    inputRef.value?.focus?.()
  })
}

const commitEdit = () => {
  // 校验
  if (props.rules) {
    for (const rule of props.rules) {
      const result = rule(editValue.value)
      if (result !== true) {
        errorMsg.value = result
        return
      }
    }
  }
  editing.value = false
  errorMsg.value = ''
  emit('update:modelValue', editValue.value)
  emit('commit', editValue.value)
}

const cancelEdit = () => {
  editing.value = false
  errorMsg.value = ''
  editValue.value = props.modelValue ?? ''
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault()
    commitEdit()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    cancelEdit()
  }
}
</script>

<template>
  <div class="inline-edit" :style="{ width }">
    <template v-if="!editing">
      <span class="display-value" :class="{ disabled }" @dblclick="startEdit">
        {{ modelValue || placeholder }}
        <el-icon v-if="!disabled" class="edit-icon" @click="startEdit">
          <Edit />
        </el-icon>
      </span>
    </template>
    <template v-else>
      <el-input
        v-if="type === 'text' || type === 'number'"
        ref="inputRef"
        v-model="editValue"
        :type="type"
        :placeholder="placeholder"
        size="small"
        @keydown="handleKeydown"
        @blur="commitEdit"
      />
      <el-select
        v-else-if="type === 'select'"
        ref="inputRef"
        v-model="editValue"
        :placeholder="placeholder"
        size="small"
        style="width: 100%"
        @blur="commitEdit"
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
        ref="inputRef"
        v-model="editValue"
        type="date"
        value-format="YYYY-MM-DD"
        size="small"
        style="width: 100%"
        @blur="commitEdit"
      />
    </template>
  </div>
</template>

<script lang="ts">
export default { name: 'InlineEdit' }
</script>

<style lang="scss" scoped>
.inline-edit {
  display: inline-block;
}
.display-value {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.2s;
  min-height: 24px;
}
.display-value:hover {
  background: var(--el-fill-color-light);
}
.display-value.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.edit-icon {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  opacity: 0;
  transition: opacity 0.2s;
}
.display-value:hover .edit-icon {
  opacity: 1;
}
</style>
