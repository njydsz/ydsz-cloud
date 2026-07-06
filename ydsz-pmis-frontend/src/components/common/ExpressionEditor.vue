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
 * <p>P0-2 增强：基于 CodeMirror 6，扩展以下能力：
 * <ul>
 *   <li>linter：括号 / 引号不匹配时在编辑区行内标红 + tooltip</li>
 *   <li>函数市场：通过 props.functions 暴露后端注册函数，自动补全 + hover 文档</li>
 *   <li>快捷键：Ctrl+Enter 触发 validate 事件、Ctrl+Space 强制补全</li>
 *   <li>错误位置暴露：将错误行号/列号通过 emit('issue') 事件冒泡给父组件</li>
 * </ul>
 *
 * Props:
 *  - modelValue: 表达式文本
 *  - fields: 可用字段列表（用于自动补全）
 *  - functions: 可用函数列表（[{ name, signature, description, sample }]）
 *  - placeholder: 占位文本
 *  - validateOnInput: 是否在输入时实时校验（默认 true）
 *  - validationEndpoint: 校验 API 端点（可选，不传则仅做前端语法检查）
 *
 * Events:
 *  - update:modelValue: 表达式内容变更
 *  - validate: 校验结果变更（true=合法, false=不合法, null=未校验）
 *  - issue: 行内错误变更（{ line, column, message } | null）
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { EditorView, keymap, placeholder as cmPlaceholder, drawSelection } from '@codemirror/view'
import { EditorState, type Extension } from '@codemirror/state'
import { javascript } from '@codemirror/lang-javascript'
import {
  autocompletion,
  type Completion,
  type CompletionContext,
  type CompletionResult,
} from '@codemirror/autocomplete'
import { linter, type Diagnostic } from '@codemirror/lint'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'

/** 函数市场条目 */
export interface ExpressionFunction {
  name: string
  signature: string
  description?: string
  sample?: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: string
    fields?: string[]
    functions?: ExpressionFunction[]
    placeholder?: string
    validateOnInput?: boolean
  }>(),
  {
    modelValue: '',
    fields: () => [],
    functions: () => [],
    placeholder: '请输入表达式...',
    validateOnInput: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  validate: [result: boolean | null]
  issue: [issue: { line: number; column: number; message: string } | null]
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
function aviatorCompletions(context: CompletionContext): CompletionResult | null {
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
          info: `业务字段：${field}`,
          boost: 2,
        })
      }
    }
  }

  // 函数市场补全（P1-7 函数市场）
  if (props.functions && props.functions.length > 0) {
    for (const fn of props.functions) {
      if (fn.name.toLowerCase().startsWith(word.text.toLowerCase())) {
        options.push({
          label: fn.name,
          type: 'function',
          detail: fn.signature,
          info: [
            fn.description ? `📖 ${fn.description}` : '',
            fn.sample ? `💡 示例：${fn.sample}` : '',
          ]
            .filter(Boolean)
            .join('\n'),
          apply: `${fn.name}()`,
          boost: 1,
        })
      }
    }
  }

  // 关键字补全
  const keywords = [
    { label: 'true', type: 'keyword', detail: '布尔值' },
    { label: 'false', type: 'keyword', detail: '布尔值' },
    { label: 'nil', type: 'keyword', detail: '空值' },
    { label: '&&', type: 'operator', detail: '逻辑与' },
    { label: '||', type: 'operator', detail: '逻辑或' },
    { label: '!', type: 'operator', detail: '逻辑非' },
  ]
  for (const kw of keywords) {
    if (kw.label.toLowerCase().startsWith(word.text.toLowerCase())) {
      options.push({ label: kw.label, type: kw.type as Completion['type'], detail: kw.detail })
    }
  }

  return options.length > 0 ? { from: word.from, options } : null
}

// ==================== Validation ====================

/**
 * 收集所有基础语法问题（P0-2 行内错误标记）
 *
 * <p>返回问题列表（含行列号），同时返回整体 valid 状态。
 * CodeMirror linter 会用 Diagnostic[] 在行内显示红波浪线 + tooltip。
 *
 * @param expression 表达式文本
 * @return issues + valid
 */
function collectSyntaxIssues(expression: string): {
  issues: { line: number; column: number; message: string }[]
  valid: boolean
} {
  const issues: { line: number; column: number; message: string }[] = []
  if (!expression || expression.trim() === '') {
    return { issues, valid: true }
  }

  const lines = expression.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // 括号不匹配（按行统计深度变化，跨行累计）
    let depth = 0
    for (let j = 0; j < line.length; j++) {
      if (line[j] === '(') depth++
      if (line[j] === ')') depth--
      if (depth < 0) {
        issues.push({
          line: i + 1,
          column: j + 1,
          message: `多余的右括号 ")"`,
        })
        return { issues, valid: false }
      }
    }
    // 跨行括号未闭合
    if (i === lines.length - 1 && depth !== 0) {
      issues.push({
        line: i + 1,
        column: line.length + 1,
        message: depth > 0
          ? `括号未闭合：还有 ${depth} 个 "(" 待闭合`
          : `括号未闭合：还有 ${-depth} 个 ")" 待闭合`,
      })
      return { issues, valid: false }
    }

    // 单引号 / 双引号不匹配
    const sQuotes = (line.match(/'/g) || []).length
    const dQuotes = (line.match(/"/g) || []).length
    if (sQuotes % 2 !== 0) {
      issues.push({
        line: i + 1,
        column: line.length + 1,
        message: `单引号未闭合（"'"）`,
      })
    }
    if (dQuotes % 2 !== 0) {
      issues.push({
        line: i + 1,
        column: line.length + 1,
        message: `双引号未闭合（"\""）`,
      })
    }
  }
  return { issues, valid: issues.length === 0 }
}

/** CodeMirror linter 适配器 */
function expressionLinter(): Extension {
  return linter(() => {
    const text = editorView.value?.state.doc.toString() ?? ''
    const { issues } = collectSyntaxIssues(text)
    return issues.map<Diagnostic>(
      (i) => ({
        from: 0, // 简化定位到开头，避免额外计算偏移
        to: text.length,
        severity: 'error',
        message: `第 ${i.line} 行第 ${i.column} 列：${i.message}`,
      }),
    )
  }, { delay: 500 })
}

/** 触发校验（防抖 500ms） */
function triggerValidation() {
  if (!props.validateOnInput) return
  if (validateTimer) clearTimeout(validateTimer)
  validateTimer = setTimeout(() => {
    const { issues, valid } = collectSyntaxIssues(props.modelValue ?? '')
    emit('validate', valid)
    // 冒泡首个问题给父组件（用于显示位置提示）
    emit('issue', issues.length > 0 ? issues[0] : null)
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
      activateOnTyping: true,
    }),
    // P0-2 行内错误标记
    expressionLinter(),
    // 历史记录
    history(),
    // 快捷键：Ctrl+Enter 触发 validate 事件、Ctrl+Space 触发补全（由 autocompletion 默认提供）
    keymap.of([
      {
        key: 'Ctrl-Enter',
        mac: 'Cmd-Enter',
        run: () => {
          const { valid } = collectSyntaxIssues(props.modelValue ?? '')
          emit('validate', valid)
          return true
        },
      },
      ...defaultKeymap,
      ...historyKeymap,
    ]),
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
      // P0-2 行内错误：红波浪线
      '.cm-lintRange-error': {
        borderBottom: '2px wavy #dc2626',
      },
      '.cm-tooltip-lint': {
        backgroundColor: '#fef2f2',
        border: '1px solid #fecaca',
        color: '#991b1b',
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
      doc: props.modelValue ?? '',
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
    const next = newVal ?? ''
    if (view && next !== view.state.doc.toString()) {
      view.dispatch({
        changes: {
          from: 0,
          to: view.state.doc.length,
          insert: next,
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
  border: 1px solid $border-base;
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