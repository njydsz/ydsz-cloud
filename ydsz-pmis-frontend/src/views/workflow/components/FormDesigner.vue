<script setup lang="ts">
/**
 * @file 可视化表单设计器组件（基于 @form-create/designer）
 * @module components/FormDesigner
 * @description 拖拽式表单设计器，左侧组件面板、中间预览画布、右侧属性配置面板。
 *   支持保存/预览/清空/导入JSON/导出JSON 操作。
 */
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import FcDesigner from '@form-create/designer'
import type { Rule, Options } from '@form-create/element-ui'

// ==================== Props ====================
const props = defineProps<{
  /** 初始表单规则（编辑已有表单时传入） */
  initialRule?: Rule[]
  /** 初始表单配置 */
  initialOptions?: Options
  /** 表单编码 */
  formCode?: string
  /** 表单名称 */
  formName?: string
}>()

const emit = defineEmits<{
  (e: 'save', rule: Rule[], options: Options, json: string): void
}>()

// ==================== State ====================
const designerRef = ref<InstanceType<typeof FcDesigner> | null>(null)
const designerReady = ref(false)
const saving = ref(false)
const jsonPreviewVisible = ref(false)
const jsonPreviewContent = ref('')

// ==================== Lifecycle ====================
onMounted(async () => {
  await nextTick()
  // 等待 designer 初始化完成
  setTimeout(() => {
    designerReady.value = true
    if (props.initialRule && props.initialRule.length > 0) {
      setRule(props.initialRule)
    }
    if (props.initialOptions) {
      setOptions(props.initialOptions)
    }
  }, 300)
})

// ==================== Designer 操作 ====================

/** 获取 designer 实例 */
function getDesigner() {
  return designerRef.value
}

/** 设置表单规则 */
function setRule(rule: Rule[]) {
  const designer = getDesigner()
  if (designer) {
    designer.setRule(rule)
  }
}

/** 设置表单配置 */
function setOptions(options: Options) {
  const designer = getDesigner()
  if (designer) {
    designer.setOption(options)
  }
}

/** 获取当前表单规则 */
function getRule(): Rule[] {
  const designer = getDesigner()
  return designer ? designer.getRule() : []
}

/** 获取当前表单 JSON 字符串 */
function getJson(): string {
  const designer = getDesigner()
  return designer ? designer.getJson() : '[]'
}

/** 获取当前表单配置 */
function getOptions(): Options {
  const designer = getDesigner()
  return designer ? designer.getOption() : ({} as Options)
}

// ==================== 工具栏操作 ====================

/** 保存表单 */
function handleSave() {
  const designer = getDesigner()
  if (!designer) return

  const rule = designer.getRule()
  if (!rule || rule.length === 0) {
    ElMessage.warning('请先设计表单，至少添加一个组件')
    return
  }

  saving.value = true
  try {
    const options = designer.getOption()
    const json = designer.getJson()
    emit('save', rule, options, json)
    ElMessage.success('表单已保存')
  } finally {
    saving.value = false
  }
}

/** 预览表单 */
function handlePreview() {
  const designer = getDesigner()
  if (!designer) return
  designer.openPreview()
}

/** 清空表单 */
function handleClear() {
  const designer = getDesigner()
  if (!designer) return
  designer.clearDragRule()
  ElMessage.success('表单已清空')
}

/** 导出 JSON */
function handleExportJson() {
  const designer = getDesigner()
  if (!designer) return

  const rule = designer.getRule()
  if (!rule || rule.length === 0) {
    ElMessage.warning('请先设计表单')
    return
  }

  const json = designer.getJson()
  const options = designer.getOption()
  const fullData = JSON.stringify({ rule: JSON.parse(json), options }, null, 2)

  const blob = new Blob([fullData], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const fileName = (props.formName || props.formCode || 'form-schema') + '.json'
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('表单 JSON 已导出')
}

/** 导入 JSON */
function handleImportJson() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: Event) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return

    try {
      const text = await file.text()
      const data = JSON.parse(text)

      const designer = getDesigner()
      if (!designer) return

      // 支持两种格式：纯 rule 数组，或 { rule, options } 对象
      if (Array.isArray(data)) {
        designer.setRule(data)
      } else if (data.rule && Array.isArray(data.rule)) {
        designer.setRule(data.rule)
        if (data.options) {
          designer.setOption(data.options)
        }
      } else {
        ElMessage.error('JSON 格式不正确，需要包含 rule 数组')
        return
      }
      ElMessage.success('表单 JSON 已导入')
    } catch (err) {
      ElMessage.error('JSON 解析失败：' + (err as Error).message)
    }
  }
  input.click()
}

/** 显示 JSON 预览 */
function handleJsonPreview() {
  const designer = getDesigner()
  if (!designer) return

  const rule = designer.getRule()
  if (!rule || rule.length === 0) {
    ElMessage.warning('请先设计表单')
    return
  }

  const json = designer.getJson()
  const options = designer.getOption()
  const fullData = JSON.stringify({ rule: JSON.parse(json), options }, null, 2)
  jsonPreviewContent.value = fullData
  jsonPreviewVisible.value = true
}

/** 复制 JSON 到剪贴板 */
function handleCopyJson() {
  navigator.clipboard.writeText(jsonPreviewContent.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

// ==================== 暴露方法 ====================
defineExpose({
  getRule,
  getJson,
  getOptions,
  setRule,
  setOptions,
  handleSave,
  handlePreview,
  handleClear,
})
</script>

<template>
  <div class="form-designer">
    <!-- 顶部工具栏 -->
    <div class="form-designer-toolbar">
      <div class="form-designer-toolbar-left">
        <span class="form-designer-title" v-if="formName">
          {{ formName }}
        </span>
        <span class="form-designer-subtitle" v-if="formCode">
          {{ formCode }}
        </span>
      </div>
      <div class="form-designer-toolbar-right">
        <el-button size="small" @click="handleImportJson">导入 JSON</el-button>
        <el-button size="small" @click="handleExportJson">导出 JSON</el-button>
        <el-button size="small" @click="handleJsonPreview">JSON 预览</el-button>
        <el-button size="small" @click="handlePreview">预览</el-button>
        <el-button size="small" @click="handleClear">清空</el-button>
        <el-button size="small" type="primary" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </div>
    </div>

    <!-- 设计器主体 -->
    <div class="form-designer-body">
      <FcDesigner
        ref="designerRef"
        :height="'100%'"
        :config="{
          showSaveBtn: false,
          showPreviewBtn: false,
          showJsonPreview: false,
          showDevice: false,
          showLanguage: false,
          autoActive: true,
          autoResetField: true,
          autoResetName: true,
        }"
        :handle="[]"
      />
    </div>

    <!-- JSON 预览弹窗 -->
    <el-dialog
      v-model="jsonPreviewVisible"
      title="JSON Schema 预览"
      width="700px"
      :close-on-click-modal="false"
    >
      <div class="json-preview-wrapper">
        <pre class="json-preview-content"><code>{{ jsonPreviewContent }}</code></pre>
      </div>
      <template #footer>
        <el-button @click="jsonPreviewVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleCopyJson">复制 JSON</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.form-designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fafbfc;
  flex-shrink: 0;
}

.form-designer-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-designer-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.form-designer-subtitle {
  font-size: 12px;
  color: #94a3b8;
}

.form-designer-toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.form-designer-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.form-designer-body :deep(.fc-designer) {
  height: 100% !important;
}

.json-preview-wrapper {
  max-height: 500px;
  overflow: auto;
  background: #1e293b;
  border-radius: 6px;
  padding: 16px;
}

.json-preview-content {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>