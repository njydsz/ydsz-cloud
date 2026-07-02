<!--
  @file 表达式编辑器组件（基于 CodeMirror 6）
  @description 提供语法高亮、字段自动补全、实时校验的表达式编辑体验，
               用于规则引擎的条件表达式和严重度表达式编辑。
  @module components/common/ExpressionEditor
-->
<script setup lang="ts">
/**
 * 表达式编辑器组件
 *
 * Props:
 *  - modelValue: 表达式文本
 *  - fields: 可用字段列表（用于自动补全）
 *  - placeholder: 占位文本
 *  - validateOnInput: 是否在输入时实时校验（默认 true）
 *  - validationEndpoint: 校验 API 端点（可选，不传则仅做前端语法检查）
 *
 * Events:
 *  - update:modelValue: 表达式内容变更
 *  - validate: 校验结果变更（true=合法, false=不合法, null=未校验）
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { EditorView, keymap, placeholder as cmPlaceholder, drawSelection } from '@codemirror/view'
import { EditorState, type Extension } from '@codemirror/state'
import { javascript } from '@codemirror/lang-javascript'
import { autocompletion, type Completion } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { syntaxTree } from '@codemirror/language'

const props = withDefaults(
  defineProps<{
    modelValue: string
    fields?: string[]
    placeholder?: string
    validateOnInput?: boolean
  }>(),
  {
    fields: () => [],
    placeholder: '请输入表达式...',
    validateOnInput: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  validate: [result: boolean | null]
}>()

// ==================== DOM ref ====================
const editorRef = ref<HTMLDivElement>()
const editorView = shallowRef<EditorView>()

// Debounce timer for validation
let validateTimer: ReturnType<typeof setTimeout> | null = null

// ==================== Autocomplete source ====================

/**
 * 构建 Aviator 表达式自动补全源
 */
function aviatorCompletions(context: import('@codemirror/autocomplete').CompletionContext): Completion[] | null {
  const word = context.matchBefore(/\w*/)
  if (!word || (word.from === word.to && !context.explicit)) return null

  const options: Completion[] = []

  // 字段名补全
  if (props.fields && props.fields.length > 0) {
    for (const field of props.fields) {
      if (field.toLowerCase().startsWith(word.text.toLowerCase())) {
        options.push({
          label: field,
          type: 'variable',
          detail: '字段',
          boost: 2,
        })
      }
    }
  }

  // 关键字补全
  const keywords = [
    { label: 'true', type: 'keyword', detail: '布尔值' },
    { label: 'false', type: 'keyword', detail: '布尔值' },
    { label: 'nil', type: 'keyword', detail: '空值' },
  ]
  for (const kw of keywords) {
    if (kw.label.startsWith(word.text.toLowerCase())) {
      options.push({ label: kw.label, type: kw.type as any, detail: kw.detail })
    }
  }

  return options.length > 0 ? options : null
}

// ==================== Validation ====================

/**
 * 前端基础语法检查（括号匹配、基本结构）
 */
function basicSyntaxCheck(expression: string): boolean {
  if (!expression || expression.trim() === '') return true
  // 检查括号匹配
  let depth = 0
  for (const ch of expression) {
    if (ch === '(') depth++
    if (ch === ')') depth--
    if (depth < 0) return false
  }
  if (depth !== 0) return false
  // 检查引号匹配
  const quotes = expression.match(/'/g)
  if (quotes && quotes.length % 2 !== 0) return false
  const dquotes = expression.match(/"/g)
  if (dquotes && dquotes.length % 2 !== 0) return false
  return true
}

/** 触发校验（防抖 500ms） */
function triggerValidation() {
  if (!props.validateOnInput) return
  if (validateTimer) clearTimeout(validateTimer)
  validateTimer = setTimeout(() => {
    const valid = basicSyntaxCheck(props.modelValue)
    emit('validate', valid)
  }, 500)
}

// ==================== Editor lifecycle ====================

/** 构建 CodeMirror 扩展 */
function buildExtensions(): Extension[] {
  const exts: Extension[] = [
    // JavaScript 语法高亮（Aviator 语法兼容）
    javascript(),
    // 自动补全
    autocompletion({
      override: [aviatorCompletions],
      defaultKeymap: true,
    }),
    // 历史记录
    history(),
    // 键盘映射
    keymap.of([...defaultKeymap, ...historyKeymap]),
    // 选中高亮
    drawSelection(),
    // 主题样式
    EditorView.theme({
      '&': {
        fontSize: '14px',
        fontFamily: "'JetBrains Mono', 'Courier New', Consolas, monospace",
      },
      '.cm-content': {
        minHeight: '60px',
        padding: '8px 12px',
      },
      '.cm-focused': {
        outline: 'none',
      },
      '.cm-line': {
        lineHeight: '1.6',
      },
      '.cm-tooltip': {
        border: '1px solid #d1d5db',
        borderRadius: '6px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
      },
      '.cm-tooltip-autocomplete > ul > li': {
        padding: '4px 12px',
      },
      '.cm-tooltip-autocomplete > ul > li[aria-selected]': {
        background: '#2563eb',
        color: '#fff',
      },
    }),
    // 更新父组件值
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        const value = update.state.doc.toString()
        emit('update:modelValue', value)
        triggerValidation()
      }
    }),
  ]

  // 占位文本
  if (props.placeholder) {
    exts.push(cmPlaceholder(props.placeholder))
  }

  return exts
}

onMounted(() => {
  nextTick(() => {
    if (!editorRef.value) return

    const state = EditorState.create({
      doc: props.modelValue,
      extensions: buildExtensions(),
    })

    const view = new EditorView({
      state,
      parent: editorRef.value,
    })

    editorView.value = view
  })
})

onBeforeUnmount(() => {
  if (editorView.value) {
    editorView.value.destroy()
  }
  if (validateTimer) clearTimeout(validateTimer)
})

// 外部值变更时同步到编辑器
watch(
  () => props.modelValue,
  (newVal) => {
    const view = editorView.value
    if (view && newVal !== view.state.doc.toString()) {
      view.dispatch({
        changes: {
          from: 0,
          to: view.state.doc.length,
          insert: newVal,
        },
      })
    }
  },
)
</script>

<template>
  <div class="expression-editor">
    <div ref="editorRef" class="cm-editor-host" />
  </div>
</template>

<style scoped lang="scss">
.expression-editor {
  width: 100%;
  border: 1px solid $border-color;
  border-radius: $border-radius-base;
  overflow: hidden;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: $primary-color;
    box-shadow: 0 0 0 2px rgba($primary-color, 0.1);
  }

  .cm-editor-host {
    :deep(.cm-editor) {
      height: 100%;
    }

    :deep(.cm-scroller) {
      overflow: auto;
      max-height: 200px;
    }

    :deep(.cm-gutters) {
      display: none;
    }
  }
}
</style>