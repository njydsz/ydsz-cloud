<!--
  @file DSL 管理页面
  @description 规则 DSL 管理页面：提供 YAML/JSON 格式的 DSL 校验、解析、导入、导出和试运行预览。
               对应路由 /execution/rule-engine/dsl-manager。
  @module views/execution/rule-engine
-->
<script setup lang="ts">
/**
 * DSL 管理页面
 *
 * 功能区域：
 *  1. DSL 编辑器：YAML/JSON 文本编辑区域 + 格式切换
 *  2. 操作工具栏：校验 / 解析 / 导入 / 导出 / 预览
 *  3. 校验结果面板：valid / errors / ruleCount / chainCount
 *  4. 导入结果面板：成功/失败计数 + 导入编码列表
 *  5. 导出面板：导出内容展示 + 下载
 *  6. 预览结果面板：规则评估结果列表
 */
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Download, Upload, Check, Close, Refresh, Document, View, Edit } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type {
  DslValidateResult,
  DslImportResult,
  DslExportResult,
  DslPreviewResult,
} from '@/api/rule-engine'
import { logger } from '@/utils/logger'

const { t } = useI18n()

// ==================== 响应式状态 ====================

/** 当前激活的标签页 */
const activeTab = ref<'editor' | 'export' | 'preview'>('editor')

/** DSL 格式 */
const format = ref<'yaml' | 'json'>('yaml')

/** DSL 文本内容 */
const dslContent = ref<string>(`rules:
  - code: EXAMPLE_RULE
    name: 示例规则
    type: expression
    condition: amount > 1000
    severityExpression: amount > 5000 ? 'RED' : 'YELLOW'
    defaultSeverity: YELLOW
    priority: 50
    enabled: true
    titleTemplate: "金额超限: {amount}"
    descriptionTemplate: "当前金额 {amount} 超过阈值 1000"
`)

/** 校验结果 */
const validateResult = ref<DslValidateResult | null>(null)

/** 解析结果（JSON 展示） */
const parseResult = ref<any>(null)

/** 导入结果 */
const importResult = ref<DslImportResult | null>(null)

/** 导出结果 */
const exportResult = ref<DslExportResult | null>(null)

/** 导出分类过滤 */
const exportCategory = ref<string>('')

/** 预览结果 */
const previewResults = ref<DslPreviewResult[]>([])

/** 预览事实数据（JSON 文本） */
const previewFacts = ref<string>('{\n  "amount": 2000\n}')

/** 加载状态 */
const loading = reactive({
  validate: false,
  parse: false,
  importDsl: false,
  exportAll: false,
  preview: false,
})

// ==================== 计算属性 ====================

/** 预览事实数据解析 */
const parsedFacts = computed<Record<string, any>>(() => {
  try {
    return JSON.parse(previewFacts.value)
  } catch {
    return {}
  }
})

// ==================== 操作方法 ====================

/** 校验 DSL */
async function handleValidate() {
  if (!dslContent.value.trim()) {
    ElMessage.warning('DSL 内容不能为空')
    return
  }
  loading.validate = true
  try {
    const res = await ruleApi.validateDsl({
      content: dslContent.value,
      format: format.value,
    })
    validateResult.value = res
    parseResult.value = null
    if (res.valid) {
      ElMessage.success(`校验通过：${res.ruleCount} 条规则，${res.chainCount || 0} 条链`)
    } else {
      ElMessage.warning(`校验失败：${res.errors.length} 个错误`)
    }
  } catch (e: any) {
    logger.error('DSL 校验失败', e)
    ElMessage.error('DSL 校验失败: ' + (e.message || '未知错误'))
  } finally {
    loading.validate = false
  }
}

/** 解析 DSL */
async function handleParse() {
  if (!dslContent.value.trim()) {
    ElMessage.warning('DSL 内容不能为空')
    return
  }
  loading.parse = true
  try {
    const res = await ruleApi.parseDsl({
      content: dslContent.value,
      format: format.value,
    })
    parseResult.value = res
    validateResult.value = null
    ElMessage.success('DSL 解析成功')
  } catch (e: any) {
    logger.error('DSL 解析失败', e)
    ElMessage.error('DSL 解析失败: ' + (e.message || '未知错误'))
  } finally {
    loading.parse = false
  }
}

/** 导入 DSL */
async function handleImport() {
  if (!dslContent.value.trim()) {
    ElMessage.warning('DSL 内容不能为空')
    return
  }
  try {
    await ElMessageBox.confirm(
      '导入将以 Upsert 方式覆盖同名规则，确定继续？',
      '导入确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  loading.importDsl = true
  try {
    const res = await ruleApi.importDsl({
      content: dslContent.value,
      format: format.value,
    })
    importResult.value = res
    if (res.failCount === 0) {
      ElMessage.success(res.summary)
    } else {
      ElMessage.warning(res.summary)
    }
  } catch (e: any) {
    logger.error('DSL 导入失败', e)
    ElMessage.error('DSL 导入失败: ' + (e.message || '未知错误'))
  } finally {
    loading.importDsl = false
  }
}

/** 导出全部规则 DSL */
async function handleExportAll() {
  loading.exportAll = true
  try {
    const res = await ruleApi.exportAllDsl(exportCategory.value || undefined)
    exportResult.value = res
    ElMessage.success(`导出成功：${res.ruleCount} 条规则`)
  } catch (e: any) {
    logger.error('DSL 导出失败', e)
    ElMessage.error('DSL 导出失败: ' + (e.message || '未知错误'))
  } finally {
    loading.exportAll = false
  }
}

/** 下载 DSL 文件 */
function handleDownload() {
  if (!exportResult.value) return
  const blob = new Blob([exportResult.value.content], {
    type: 'text/yaml;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `rules-export-${Date.now()}.yaml`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('文件已下载')
}

/** 预览 DSL */
async function handlePreview() {
  if (!dslContent.value.trim()) {
    ElMessage.warning('DSL 内容不能为空')
    return
  }
  loading.preview = true
  try {
    const res = await ruleApi.previewDsl({
      content: dslContent.value,
      format: format.value,
      facts: parsedFacts.value,
    })
    previewResults.value = res
    const triggered = res.filter((r) => r.triggered).length
    ElMessage.success(`预览完成：${res.length} 条规则，${triggered} 条触发`)
  } catch (e: any) {
    logger.error('DSL 预览失败', e)
    ElMessage.error('DSL 预览失败: ' + (e.message || '未知错误'))
  } finally {
    loading.preview = false
  }
}

/** 格式切换时清空结果 */
function handleFormatChange() {
  validateResult.value = null
  parseResult.value = null
  importResult.value = null
}
</script>

<template>
  <div class="dsl-manager">
    <!-- 页头 -->
    <el-page-header @back="$router.push('/execution/rule-engine')" class="mb-4">
      <template #content>
        <span class="page-title">{{ t('execution.ruleEngine.dslManagement') }}</span>
      </template>
      <template #extra>
        <el-radio-group v-model="format" @change="handleFormatChange" size="small">
          <el-radio-button value="yaml">YAML</el-radio-button>
          <el-radio-button value="json">JSON</el-radio-button>
        </el-radio-group>
      </template>
    </el-page-header>

    <!-- 标签页 -->
    <el-tabs v-model="activeTab" class="dsl-tabs">
      <!-- DSL 编辑器 -->
      <el-tab-pane label="DSL 编辑器" name="editor">
        <el-row :gutter="16">
          <!-- 左侧编辑区 -->
          <el-col :span="14">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>DSL 内容</span>
                  <el-button-group>
                    <el-button
                      :icon="Check"
                      :loading="loading.validate"
                      @click="handleValidate"
                      type="primary"
                      size="small"
                    >
                      校验
                    </el-button>
                    <el-button
                      :icon="View"
                      :loading="loading.parse"
                      @click="handleParse"
                      size="small"
                    >
                      解析
                    </el-button>
                    <el-button
                      :icon="Upload"
                      :loading="loading.importDsl"
                      @click="handleImport"
                      type="warning"
                      size="small"
                    >
                      导入
                    </el-button>
                    <el-button
                      :icon="Refresh"
                      :loading="loading.preview"
                      @click="handlePreview"
                      type="success"
                      size="small"
                    >
                      预览
                    </el-button>
                  </el-button-group>
                </div>
              </template>
              <el-input
                v-model="dslContent"
                type="textarea"
                :rows="24"
                placeholder="在此输入 YAML/JSON DSL 内容..."
                class="dsl-textarea"
                spellcheck="false"
              />
            </el-card>
          </el-col>

          <!-- 右侧结果区 -->
          <el-col :span="10">
            <!-- 校验结果 -->
            <el-card v-if="validateResult" shadow="never" class="result-card">
              <template #header>
                <div class="card-header">
                  <span>校验结果</span>
                  <el-tag :type="validateResult.valid ? 'success' : 'danger'" size="small">
                    <el-icon class="mr-1">
                      <Check v-if="validateResult.valid" />
                      <Close v-else />
                    </el-icon>
                    {{ validateResult.valid ? '通过' : '失败' }}
                  </el-tag>
                </div>
              </template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="规则数">{{ validateResult.ruleCount }}</el-descriptions-item>
                <el-descriptions-item label="链数">{{ validateResult.chainCount || 0 }}</el-descriptions-item>
              </el-descriptions>
              <div v-if="validateResult.errors.length > 0" class="error-list">
                <div v-for="(err, i) in validateResult.errors" :key="i" class="error-item">
                  <el-icon color="#f56c6c"><Close /></el-icon>
                  <span>{{ err }}</span>
                </div>
              </div>
            </el-card>

            <!-- 解析结果 -->
            <el-card v-if="parseResult" shadow="never" class="result-card">
              <template #header>
                <span>解析结果（结构化模型）</span>
              </template>
              <pre class="json-display">{{ JSON.stringify(parseResult, null, 2) }}</pre>
            </el-card>

            <!-- 导入结果 -->
            <el-card v-if="importResult" shadow="never" class="result-card">
              <template #header>
                <div class="card-header">
                  <span>导入结果</span>
                  <el-tag :type="importResult.failCount === 0 ? 'success' : 'warning'" size="small">
                    {{ importResult.summary }}
                  </el-tag>
                </div>
              </template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="总数">{{ importResult.totalRules }}</el-descriptions-item>
                <el-descriptions-item label="成功">
                  <el-text type="success">{{ importResult.successCount }}</el-text>
                </el-descriptions-item>
                <el-descriptions-item label="失败">
                  <el-text type="danger">{{ importResult.failCount }}</el-text>
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="importResult.importedCodes.length > 0" class="imported-codes">
                <el-text type="info" size="small">已导入规则：</el-text>
                <el-tag
                  v-for="code in importResult.importedCodes"
                  :key="code"
                  size="small"
                  class="code-tag"
                >
                  {{ code }}
                </el-tag>
              </div>
              <div v-if="importResult.errors.length > 0" class="error-list">
                <div v-for="(err, i) in importResult.errors" :key="i" class="error-item">
                  <el-icon color="#f56c6c"><Close /></el-icon>
                  <span>{{ err }}</span>
                </div>
              </div>
            </el-card>

            <!-- 预览结果 -->
            <el-card v-if="previewResults.length > 0" shadow="never" class="result-card">
              <template #header>
                <span>预览结果（试运行）</span>
              </template>
              <el-table :data="previewResults" size="small" stripe>
                <el-table-column prop="ruleCode" label="规则编码" width="160" />
                <el-table-column label="触发" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                      {{ row.triggered ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="severity" label="严重度" width="90" />
                <el-table-column prop="title" label="标题" show-overflow-tooltip />
                <el-table-column prop="error" label="错误" show-overflow-tooltip />
              </el-table>
            </el-card>

            <!-- 预览事实数据 -->
            <el-card v-if="activeTab === 'editor'" shadow="never" class="result-card">
              <template #header>
                <span>预览事实数据（JSON）</span>
              </template>
              <el-input
                v-model="previewFacts"
                type="textarea"
                :rows="8"
                placeholder='{"amount": 2000}'
                spellcheck="false"
              />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 导出面板 -->
      <el-tab-pane label="导出" name="export">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>规则 DSL 导出</span>
              <el-button-group>
                <el-input
                  v-model="exportCategory"
                  placeholder="分类过滤（可选）"
                  size="small"
                  style="width: 200px"
                  clearable
                />
                <el-button
                  :icon="Download"
                  :loading="loading.exportAll"
                  @click="handleExportAll"
                  type="primary"
                  size="small"
                >
                  导出全部
                </el-button>
                <el-button
                  v-if="exportResult"
                  :icon="Download"
                  @click="handleDownload"
                  size="small"
                >
                  下载文件
                </el-button>
              </el-button-group>
            </div>
          </template>
          <div v-if="exportResult" class="export-result">
            <el-descriptions :column="3" border size="small" class="mb-4">
              <el-descriptions-item label="格式">{{ exportResult.format }}</el-descriptions-item>
              <el-descriptions-item label="规则数">{{ exportResult.ruleCount || '-' }}</el-descriptions-item>
              <el-descriptions-item label="规则编码">{{ exportResult.ruleCode || '全部' }}</el-descriptions-item>
            </el-descriptions>
            <pre class="yaml-display">{{ exportResult.content }}</pre>
          </div>
          <el-empty v-else description="点击「导出全部」生成 YAML DSL" />
        </el-card>
      </el-tab-pane>

      <!-- 预览面板 -->
      <el-tab-pane label="试运行预览" name="preview">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>DSL 试运行预览</span>
              <el-button
                :icon="Refresh"
                :loading="loading.preview"
                @click="handlePreview"
                type="success"
                size="small"
              >
                执行预览
              </el-button>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="12">
              <h4>DSL 内容</h4>
              <el-input
                v-model="dslContent"
                type="textarea"
                :rows="20"
                spellcheck="false"
              />
            </el-col>
            <el-col :span="12">
              <h4>事实数据（JSON）</h4>
              <el-input
                v-model="previewFacts"
                type="textarea"
                :rows="20"
                spellcheck="false"
              />
            </el-col>
          </el-row>
          <div v-if="previewResults.length > 0" class="preview-results">
            <h4 class="mt-4">评估结果</h4>
            <el-table :data="previewResults" stripe>
              <el-table-column prop="ruleCode" label="规则编码" width="180" />
              <el-table-column label="触发" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.triggered ? 'danger' : 'info'" size="small">
                    {{ row.triggered ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="severity" label="严重度" width="100" />
              <el-table-column prop="title" label="标题" show-overflow-tooltip />
              <el-table-column prop="description" label="描述" show-overflow-tooltip />
              <el-table-column prop="error" label="错误" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-text type="danger" v-if="row.error">{{ row.error }}</el-text>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.dsl-manager {
  padding: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dsl-textarea :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.result-card {
  margin-bottom: 12px;
}

.error-list {
  margin-top: 12px;
}

.error-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: #f56c6c;
}

.imported-codes {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.code-tag {
  font-family: monospace;
}

.json-display,
.yaml-display {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 600px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-results {
  margin-top: 16px;
}

.dsl-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}
</style>
