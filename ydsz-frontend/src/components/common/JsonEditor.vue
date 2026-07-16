<!--
  @fileoverview JSON 编辑器组件（基于 CodeMirror 6）
  @description 提供语法高亮、行号、括号匹配、代码折叠、自动缩进的 JSON 编辑体验：
  - Props: modelValue / readonly / placeholder / minHeight / maxHeight
  - Emits: update:modelValue / validate
  - 场景: 表单设计器 schema 编辑、流程定义 JSON 查看
  @module components/common/JsonEditor
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * JSON 编辑器组件
 *
 * Props:
 *  - modelValue: JSON 文本
 *  - readonly: 是否只读（默认 false）
 *  - placeholder: 占位文本
 *  - minHeight: 最小高度（默认 '200px'）
 *  - maxHeight: 最大高度（默认 '600px'）
 *
 * Events:
 *  - update:modelValue: 内容变更
 *  - validate: JSON 校验结果（true=合法, false=不合法）
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { EditorView, keymap, placeholder as cmPlaceholder, drawSelection, lineNumbers } from '@codemirror/view'
import { EditorState, type Extension } from '@codemirror/state'
import { javascript } from '@codemirror/lang-javascript'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { bracketMatching, foldGutter, indentOnInput, syntaxHighlighting, defaultHighlightStyle } from '@codemirror/language'

const props = withDefaults(
  defineProps<{
    modelValue: string
    readonly?: boolean
    placeholder?: string
    minHeight?: string
    maxHeight?: string
  }>(),
  {
    readonly: false,
    placeholder: '请输入 JSON...',
    minHeight: '200px',
    maxHeight: '600px',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  validate: [result: boolean]
}>()

// ==================== DOM ref ====================
const editorRef = ref<HTMLDivElement>()
const editorView = shallowRef<EditorView>()

/** JSON 校验 */
function validateJson(text: string): boolean {
  if (!text || text.trim() === '') return true
  try {
    JSON.parse(text)
    return true
  } catch {
    return false
  }
}

/** 构建 CodeMirror 扩展 */
function buildExtensions(): Extension[] {
  const exts: Extension[] = [
    // JavaScript 语法高亮（JSON 是 JS 子集，可直接高亮）
    javascript(),
    // 语法高亮主题
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    // 行号
    lineNumbers(),
    // 代码折叠
    foldGutter({
      markerDOM: (open: boolean) => {
        const dom = document.createElement('span')
        dom.textContent = open ? '▾' : '▸'
        dom.style.cursor = 'pointer'
        dom.style.marginRight = '2px'
        dom.style.color = '#64748b'
        return dom
      },
    }),
    // 括号匹配
    bracketMatching(),
    // 自动缩进
    indentOnInput(),
    // 历史记录
    history(),
    // 键盘映射（含 Tab 缩进）
    keymap.of([...defaultKeymap, ...historyKeymap, indentWithTab]),
    // 选中高亮
    drawSelection(),
    // 主题样式（深色背景，与原 textarea 风格一致）
    EditorView.theme({
      '&': {
        fontSize: '13px',
        fontFamily: "'JetBrains Mono', 'Consolas', 'Monaco', 'Courier New', monospace",
        height: '100%',
        backgroundColor: '#1e293b',
      },
      '.cm-content': {
        minHeight: props.minHeight,
        maxHeight: props.maxHeight,
        padding: '12px 16px',
        color: '#e2e8f0',
      },
      '.cm-focused': {
        outline: 'none',
      },
      '.cm-line': {
        lineHeight: '1.6',
      },
      '.cm-gutters': {
        backgroundColor: '#0f172a',
        color: '#64748b',
        border: 'none',
      },
      '.cm-activeLineGutter': {
        backgroundColor: '#1e293b',
        color: '#93c5fd',
      },
      '.cm-activeLine': {
        backgroundColor: 'rgba(59, 130, 246, 0.08)',
      },
      '.cm-foldPlaceholder': {
        backgroundColor: '#334155',
        color: '#94a3b8',
        border: 'none',
        borderRadius: '3px',
        padding: '0 6px',
      },
      '&.cm-focused .cm-selectionBackground, ::selection': {
        backgroundColor: 'rgba(59, 130, 246, 0.3)',
      },
    }),
    // 更新父组件值 + 校验
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        const value = update.state.doc.toString()
        emit('update:modelValue', value)
        emit('validate', validateJson(value))
      }
    }),
  ]

  // 只读模式
  if (props.readonly) {
    exts.push(EditorState.readOnly.of(true))
  }

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
  <div class="json-editor">
    <div ref="editorRef" class="cm-editor-host" />
  </div>
</template>

<style scoped lang="scss">
.json-editor {
  width: 100%;
  height: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px rgba(var(--el-color-primary-rgb), 0.1);
  }

  .cm-editor-host {
    height: 100%;

    :deep(.cm-editor) {
      height: 100%;
    }

    :deep(.cm-scroller) {
      overflow: auto;
    }
  }
}
</style>
