<script setup lang="ts">
/**
 * @file 表单设计器页面
 * @module views/workflow/form-design
 * @description 双模式表单设计器 —— 可视化拖拽模式（form-create-designer） + JSON 编辑模式。
 *   支持保存表单 schema 到后端。
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormDesigner from '../components/FormDesigner.vue'
import { saveFormSchema, getFormSchema } from '@/api/workflow'
import type { Rule, Options } from '@form-create/element-ui'

// ==================== 双模式 ====================
const editorMode = ref<'design' | 'json'>('design')

// ==================== 子组件引用 ====================
const formDesignerRef = ref<InstanceType<typeof FormDesigner> | null>(null)

// ==================== 状态 ====================
const saving = ref(false)
const loading = ref(false)

// JSON 编辑模式相关
const jsonEditorContent = ref('')
const jsonEditorError = ref('')

// 当前表单信息
const formInfo = ref({
  formCode: '',
  formName: '',
  formSchema: '',
})

// ==================== 表单设计模式操作 ====================

/** 可视化模式下保存 */
async function handleDesignSave(rule: Rule[], options: Options, json: string) {
  saving.value = true
  try {
    // 同步到 JSON 编辑区
    jsonEditorContent.value = JSON.stringify({ rule, options }, null, 2)
    jsonEditorError.value = ''

    // 如果填写了表单信息，则保存到后端
    if (formInfo.value.formCode.trim()) {
      await doSaveToBackend(json)
    } else {
      // 提示用户填写表单编码
      ElMessageBox.prompt('请输入表单编码（如 contract_form）', '保存表单', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
        inputErrorMessage: '表单编码只能包含字母、数字和下划线，且以字母开头',
      }).then(({ value }) => {
        formInfo.value.formCode = value
        doSaveToBackend(json)
      }).catch(() => {
        ElMessage.info('已取消保存，可在页面上方填写表单信息后重新保存')
      })
    }
  } finally {
    saving.value = false
  }
}

// ==================== JSON 编辑模式操作 ====================

/** 校验 JSON 是否合法 */
function validateJson(): boolean {
  jsonEditorError.value = ''
  try {
    const data = JSON.parse(jsonEditorContent.value)
    if (data.rule && !Array.isArray(data.rule)) {
      jsonEditorError.value = 'rule 字段必须是数组'
      return false
    }
    return true
  } catch (e) {
    jsonEditorError.value = 'JSON 格式错误：' + (e as Error).message
    return false
  }
}

/** 应用 JSON 到设计器 */
function handleApplyJson() {
  if (!validateJson()) {
    ElMessage.error('请先修正 JSON 格式错误')
    return
  }

  const data = JSON.parse(jsonEditorContent.value)
  const ruleData = data.rule || data

  if (formDesignerRef.value) {
    formDesignerRef.value.setRule(ruleData)
    if (data.options) {
      formDesignerRef.value.setOptions(data.options)
    }
    editorMode.value = 'design'
    ElMessage.success('JSON 已应用到设计器')
  }
}

/** 格式化 JSON */
function handleFormatJson() {
  try {
    const data = JSON.parse(jsonEditorContent.value)
    jsonEditorContent.value = JSON.stringify(data, null, 2)
    jsonEditorError.value = ''
    ElMessage.success('JSON 已格式化')
  } catch (e) {
    jsonEditorError.value = 'JSON 格式错误，无法格式化：' + (e as Error).message
  }
}

/** 从设计器同步 JSON 到编辑器 */
function handleSyncFromDesigner() {
  if (formDesignerRef.value) {
    const rule = formDesignerRef.value.getRule()
    const options = formDesignerRef.value.getOptions()
    jsonEditorContent.value = JSON.stringify({ rule, options }, null, 2)
    jsonEditorError.value = ''
    editorMode.value = 'json'
    ElMessage.success('已从设计器同步')
  }
}

// ==================== 后端保存 ====================

async function doSaveToBackend(json: string) {
  try {
    const res = await saveFormSchema({
      formCode: formInfo.value.formCode.trim(),
      formName: formInfo.value.formName.trim() || formInfo.value.formCode.trim(),
      formSchema: json,
    })
    if (res.data?.code === 0) {
      ElMessage.success('表单保存成功')
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e as Error).message)
  }
}

/** 加载已有表单 */
async function handleLoadForm() {
  if (!formInfo.value.formCode.trim()) {
    ElMessage.warning('请先输入表单编码')
    return
  }

  loading.value = true
  try {
    const res = await getFormSchema(formInfo.value.formCode.trim())
    if (res.data?.code === 0 && res.data?.data) {
      const schema = res.data.data
      if (schema.formSchema) {
        const data = JSON.parse(schema.formSchema)
        const ruleData = data.rule || data

        // 设置到设计器
        if (formDesignerRef.value) {
          formDesignerRef.value.setRule(ruleData)
          if (data.options) {
            formDesignerRef.value.setOptions(data.options)
          }
        }

        // 同步到 JSON 编辑区
        jsonEditorContent.value = schema.formSchema
        formInfo.value.formName = schema.formName || ''

        editorMode.value = 'design'
        ElMessage.success('表单加载成功')
      }
    } else {
      ElMessage.warning(res.data?.message || '表单不存在')
    }
  } catch (e) {
    ElMessage.error('加载失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-form-design">
    <!-- 顶部信息栏 -->
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>表单设计器</h2>
          <p class="page-header__sub">可视化拖拽构建表单，支持 JSON 编辑与导入导出</p>
        </div>
        <el-radio-group v-model="editorMode" size="small">
          <el-radio-button value="design">可视化设计</el-radio-button>
          <el-radio-button value="json">JSON 编辑</el-radio-button>
        </el-radio-group>
      </div>
      <div class="page-header-form">
        <el-input
          v-model="formInfo.formCode"
          placeholder="表单编码（如 contract_form）"
          size="small"
          style="width: 200px"
        />
        <el-input
          v-model="formInfo.formName"
          placeholder="表单名称（如 合同表单）"
          size="small"
          style="width: 200px"
        />
        <el-button size="small" type="primary" @click="handleLoadForm" :loading="loading">
          加载已有表单
        </el-button>
      </div>
    </div>

    <!-- 可视化设计模式 -->
    <div class="page-body" v-show="editorMode === 'design'">
      <FormDesigner
        ref="formDesignerRef"
        :form-code="formInfo.formCode"
        :form-name="formInfo.formName"
        @save="handleDesignSave"
      />
    </div>

    <!-- JSON 编辑模式 -->
    <div class="page-body" v-show="editorMode === 'json'">
      <div class="json-editor">
        <div class="json-editor-toolbar">
          <div class="json-editor-toolbar-left">
            <span class="json-editor-label">JSON Schema 编辑</span>
          </div>
          <div class="json-editor-toolbar-right">
            <el-button size="small" @click="handleSyncFromDesigner">从设计器同步</el-button>
            <el-button size="small" @click="handleFormatJson">格式化</el-button>
            <el-button size="small" type="primary" @click="handleApplyJson">应用到设计器</el-button>
          </div>
        </div>
        <div class="json-editor-content">
          <el-input
            v-model="jsonEditorContent"
            type="textarea"
            :rows="20"
            placeholder='请输入 JSON schema，格式：{ "rule": [...], "options": {...} }'
            class="json-editor-textarea"
          />
        </div>
        <div v-if="jsonEditorError" class="json-editor-error">
          {{ jsonEditorError }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-form-design {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  padding: 16px;
}

.page-header {
  margin-bottom: 12px;
  flex-shrink: 0;

  &-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 10px;
  }

  &-form {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.page-body {
  flex: 1;
  min-height: 0;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

// JSON 编辑器样式
.json-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.json-editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fafbfc;
  flex-shrink: 0;
}

.json-editor-toolbar-left {
  display: flex;
  align-items: center;
}

.json-editor-label {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.json-editor-toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.json-editor-content {
  flex: 1;
  padding: 12px;
  overflow: hidden;
}

.json-editor-textarea {
  height: 100%;

  :deep(.el-textarea__inner) {
    height: 100% !important;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.6;
    background: #1e293b;
    color: #e2e8f0;
    border: none;
    border-radius: 6px;
    resize: none;
  }
}

.json-editor-error {
  margin: 0 12px 12px;
  padding: 8px 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  color: #dc2626;
  font-size: 13px;
  flex-shrink: 0;
}
</style>