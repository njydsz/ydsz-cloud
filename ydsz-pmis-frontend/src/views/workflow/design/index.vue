<script setup lang="ts">
/**
 * @file 流程设计器页面
 * @module views/workflow/design
 * @description 双模式可视化设计器：
 *   - BPMN 2.0 专业模式（bpmn.js，对标炎黄盈动/奥哲）
 *   - 经典模式（自绘 SVG，仿钉钉/飞书审批流，轻量拖拽）
 * 附带：版本管理（列表/切换激活/差异对比）+ 模拟运行。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Version, VideoPlay, Refresh, Search, Files } from '@element-plus/icons-vue'
import FlowDesigner from '../components/FlowDesigner.vue'
import BpmnDesigner from '../components/BpmnDesigner.vue'
import {
  pageDefinitions,
  listVersions,
  diffVersions,
  switchVersion,
  simulateFlow,
  listFlowTemplates,
  importFlowTemplate,
  exportAsTemplate,
} from '@/api/workflow'
import type {
  FlowDefinitionDTO,
  FlowVersionDTO,
  VersionDiffDTO,
  SimulateResultDTO,
  FlowTemplateDTO,
} from '@/api/workflow/types'

// ==================== 设计器模式 ====================
const designerMode = ref<'bpmn' | 'classic'>('bpmn')

// ==================== 流程定义选择 ====================
const definitionList = ref<FlowDefinitionDTO[]>([])
const definitionLoading = ref(false)
const selectedDefinitionId = ref<number | undefined>(undefined)
const selectedDefinition = ref<FlowDefinitionDTO | undefined>(undefined)

async function loadDefinitions() {
  definitionLoading.value = true
  try {
    const res = await pageDefinitions({ pageNum: 1, pageSize: 200 })
    definitionList.value = res.data?.records ?? []
    if (definitionList.value.length > 0 && !selectedDefinitionId.value) {
      selectedDefinitionId.value = definitionList.value[0].id
      selectedDefinition.value = definitionList.value[0]
    }
  } catch (e) {
    ElMessage.error('加载流程定义列表失败')
  } finally {
    definitionLoading.value = false
  }
}

function onDefinitionChange(id: number) {
  selectedDefinition.value = definitionList.value.find((d) => d.id === id)
}

// ==================== 版本管理 ====================
const versionDrawer = ref(false)
const versionLoading = ref(false)
const versionList = ref<FlowVersionDTO[]>([])

async function openVersionDrawer() {
  if (!selectedDefinitionId.value) {
    ElMessage.warning('请先选择流程定义')
    return
  }
  versionDrawer.value = true
  await loadVersions()
}

async function loadVersions() {
  if (!selectedDefinitionId.value) return
  versionLoading.value = true
  try {
    const res = await listVersions(selectedDefinitionId.value)
    versionList.value = res.data ?? []
  } catch (e) {
    ElMessage.error('加载版本列表失败')
  } finally {
    versionLoading.value = false
  }
}

async function handleSwitchVersion(row: FlowVersionDTO) {
  try {
    await ElMessageBox.confirm(
      `确认将版本 ${row.version} 设为激活版本？`,
      '切换激活版本',
      { type: 'warning' },
    )
    await switchVersion(row.flowCode, row.version)
    ElMessage.success('版本切换成功')
    await loadVersions()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('版本切换失败')
  }
}

// ==================== 版本差异对比 ====================
const diffDialog = ref(false)
const diffLoading = ref(false)
const diffData = ref<VersionDiffDTO | null>(null)
const diffV1 = ref<number | undefined>(undefined)
const diffV2 = ref<number | undefined>(undefined)

async function openDiffDialog() {
  if (versionList.value.length < 2) {
    ElMessage.warning('至少需要 2 个版本才能对比')
    return
  }
  diffV1.value = versionList.value[0].version
  diffV2.value = versionList.value[1].version
  diffDialog.value = true
  diffData.value = null
}

async function doDiff() {
  if (!selectedDefinitionId.value || !diffV1.value || !diffV2.value) return
  if (diffV1.value === diffV2.value) {
    ElMessage.warning('请选择不同的版本进行对比')
    return
  }
  diffLoading.value = true
  try {
    const res = await diffVersions(
      selectedDefinitionId.value,
      diffV1.value,
      diffV2.value,
    )
    diffData.value = res.data ?? null
  } catch (e) {
    ElMessage.error('差异对比失败')
  } finally {
    diffLoading.value = false
  }
}

// ==================== 模拟运行 ====================
const simulateDialog = ref(false)
const simulating = ref(false)
const simulateForm = reactive({
  flowCode: '',
  variablesJson: '{}',
})
const simulateResult = ref<SimulateResultDTO | null>(null)

function openSimulateDialog() {
  if (!selectedDefinition.value) {
    ElMessage.warning('请先选择流程定义')
    return
  }
  simulateForm.flowCode = selectedDefinition.value.flowCode
  simulateForm.variablesJson = '{}'
  simulateResult.value = null
  simulateDialog.value = true
}

async function doSimulate() {
  if (!simulateForm.flowCode) {
    ElMessage.warning('请填写流程编码')
    return
  }
  let variables: Record<string, unknown>
  try {
    variables = JSON.parse(simulateForm.variablesJson || '{}')
  } catch {
    ElMessage.error('变量 JSON 格式错误')
    return
  }
  simulating.value = true
  simulateResult.value = null
  try {
    const res = await simulateFlow(simulateForm.flowCode, variables)
    simulateResult.value = res.data ?? null
  } catch (e: any) {
    ElMessage.error('模拟运行失败：' + (e?.message ?? '未知错误'))
  } finally {
    simulating.value = false
  }
}

// ==================== 结果标签颜色 ====================
function stepTagType(result: string): string {
  switch (result) {
    case 'PASS':
      return 'success'
    case 'REJECT':
      return 'danger'
    case 'SKIP':
      return 'info'
    case 'ERROR':
      return 'warning'
    default:
      return 'info'
  }
}

// ==================== 模板库 ====================
const templateDialog = ref(false)
const templateLoading = ref(false)
const templateList = ref<FlowTemplateDTO[]>([])
const templateCategory = ref('')

const categoryOptions = [
  { label: '全部', value: '' },
  { label: '人事', value: 'HR' },
  { label: '财务', value: 'FINANCE' },
  { label: '行政', value: 'ADMIN' },
  { label: '项目', value: 'PROJECT' },
  { label: '通用', value: 'GENERAL' },
]

async function openTemplateDialog() {
  templateDialog.value = true
  await loadTemplates()
}

async function loadTemplates() {
  templateLoading.value = true
  try {
    const res = await listFlowTemplates(templateCategory.value || undefined)
    templateList.value = res.data ?? []
  } catch {
    ElMessage.error('加载模板列表失败')
  } finally {
    templateLoading.value = false
  }
}

async function handleImportTemplate(tpl: FlowTemplateDTO) {
  try {
    await ElMessageBox.confirm(
      `确认从模板「${tpl.templateName}」创建新流程定义？`,
      '导入模板',
      { type: 'info' },
    )
    const res = await importFlowTemplate(tpl.templateCode)
    ElMessage.success(`模板导入成功，新定义 ID: ${res.data}`)
    templateDialog.value = false
    await loadDefinitions()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('模板导入失败')
  }
}

// 导出为模板
const exportDialog = ref(false)
const exportForm = reactive({
  templateName: '',
  category: 'GENERAL',
})

function openExportDialog() {
  if (!selectedDefinitionId.value) {
    ElMessage.warning('请先选择流程定义')
    return
  }
  exportForm.templateName = selectedDefinition.value?.flowName || ''
  exportForm.category = 'GENERAL'
  exportDialog.value = true
}

async function doExport() {
  if (!exportForm.templateName.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  try {
    await exportAsTemplate(
      selectedDefinitionId.value!,
      exportForm.templateName.trim(),
      exportForm.category,
    )
    ElMessage.success('导出模板成功')
    exportDialog.value = false
  } catch {
    ElMessage.error('导出失败')
  }
}

function categoryLabel(cat?: string): string {
  const found = categoryOptions.find((c) => c.value === cat)
  return found?.label || cat || '通用'
}

function categoryTagType(cat?: string): string {
  switch (cat) {
    case 'HR': return 'danger'
    case 'FINANCE': return 'warning'
    case 'ADMIN': return 'info'
    case 'PROJECT': return 'success'
    default: return ''
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadDefinitions()
})
</script>

<template>
  <div class="page-workflow-design">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>流程设计器</h2>
          <p class="page-header__sub">
            可视化建模 → 一键部署到工作流引擎（支持 BPMN 2.0 专业模式 + 仿钉钉经典模式）
          </p>
        </div>
        <el-radio-group v-model="designerMode" size="small">
          <el-radio-button value="bpmn">BPMN 2.0 专业模式</el-radio-button>
          <el-radio-button value="classic">仿钉钉经典模式</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 工具栏：流程定义选择 + 版本管理 + 模拟运行 -->
    <div class="design-toolbar">
      <el-select
        v-model="selectedDefinitionId"
        placeholder="选择流程定义"
        filterable
        :loading="definitionLoading"
        style="width: 300px"
        @change="onDefinitionChange"
      >
        <el-option
          v-for="item in definitionList"
          :key="item.id"
          :label="`${item.flowName} (v${item.version})`"
          :value="item.id"
        />
      </el-select>

      <el-button :icon="Refresh" @click="loadDefinitions">刷新</el-button>

      <div class="toolbar-spacer" />

      <el-button :icon="Files" @click="openTemplateDialog">模板库</el-button>
      <el-button @click="openExportDialog">导出为模板</el-button>
      <el-button :icon="Version" @click="openVersionDrawer">版本管理</el-button>
      <el-button type="primary" :icon="VideoPlay" @click="openSimulateDialog">
        模拟运行
      </el-button>
    </div>

    <!-- 设计器主体 -->
    <div class="page-body">
      <BpmnDesigner
        v-if="designerMode === 'bpmn'"
        :flow-code="selectedDefinition?.flowCode"
        :flow-name="selectedDefinition?.flowName"
        :initial-xml="selectedDefinition?.bpmnXml"
      />
      <FlowDesigner v-else />
    </div>

    <!-- 版本管理抽屉 -->
    <el-drawer
      v-model="versionDrawer"
      title="版本管理"
      direction="rtl"
      size="520px"
    >
      <div class="version-toolbar">
        <el-button :icon="Refresh" @click="loadVersions">刷新</el-button>
        <el-button :icon="Search" @click="openDiffDialog">版本对比</el-button>
      </div>

      <el-table
        v-loading="versionLoading"
        :data="versionList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="version" label="版本" width="70" align="center">
          <template #default="{ row }">
            <span>v{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">
              {{ row.active ? '已激活' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="deployTime"
          label="部署时间"
          min-width="160"
        />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.active"
              type="primary"
              link
              size="small"
              @click="handleSwitchVersion(row)"
            >
              设为激活
            </el-button>
            <span v-else class="text-muted">当前版本</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 版本差异对比弹窗 -->
    <el-dialog
      v-model="diffDialog"
      title="版本差异对比"
      width="640px"
      @open="doDiff"
    >
      <div class="diff-selector">
        <el-select v-model="diffV1" placeholder="版本 A" style="width: 120px">
          <el-option
            v-for="v in versionList"
            :key="v.version"
            :label="`v${v.version}`"
            :value="v.version"
          />
        </el-select>
        <span class="diff-arrow">VS</span>
        <el-select v-model="diffV2" placeholder="版本 B" style="width: 120px">
          <el-option
            v-for="v in versionList"
            :key="v.version"
            :label="`v${v.version}`"
            :value="v.version"
          />
        </el-select>
        <el-button type="primary" :loading="diffLoading" @click="doDiff">
          对比
        </el-button>
      </div>

      <div v-if="diffData" class="diff-result">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="版本 A">v{{ diffData.v1 }}</el-descriptions-item>
          <el-descriptions-item label="版本 B">v{{ diffData.v2 }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="diffData.addedNodes?.length" class="diff-section">
          <h4>新增节点</h4>
          <el-tag
            v-for="node in diffData.addedNodes"
            :key="node"
            type="success"
            size="small"
            class="diff-tag"
          >
            {{ node }}
          </el-tag>
        </div>

        <div v-if="diffData.removedNodes?.length" class="diff-section">
          <h4>删除节点</h4>
          <el-tag
            v-for="node in diffData.removedNodes"
            :key="node"
            type="danger"
            size="small"
            class="diff-tag"
          >
            {{ node }}
          </el-tag>
        </div>

        <div v-if="diffData.modifiedNodes?.length" class="diff-section">
          <h4>修改节点</h4>
          <el-tag
            v-for="node in diffData.modifiedNodes"
            :key="node"
            type="warning"
            size="small"
            class="diff-tag"
          >
            {{ node }}
          </el-tag>
        </div>

        <div v-if="diffData.diffContent" class="diff-section">
          <h4>差异详情</h4>
          <pre class="diff-content">{{ diffData.diffContent }}</pre>
        </div>
      </div>
    </el-dialog>

    <!-- 模拟运行弹窗 -->
    <el-dialog
      v-model="simulateDialog"
      title="模拟运行"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="流程编码">
          <el-input v-model="simulateForm.flowCode" placeholder="流程编码" />
        </el-form-item>
        <el-form-item label="流程变量">
          <el-input
            v-model="simulateForm.variablesJson"
            type="textarea"
            :rows="4"
            placeholder='{"amount": 50000, "type": "urgent"}'
          />
          <div class="form-tip">JSON 格式，模拟审批时传入的流程变量</div>
        </el-form-item>
      </el-form>

      <div v-if="simulateResult" class="simulate-result">
        <el-alert
          :title="simulateResult.success ? '模拟完成' : '模拟未完成'"
          :type="simulateResult.success ? 'success' : 'warning'"
          :description="simulateResult.finalResult || simulateResult.errorMessage || ''"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />

        <el-table :data="simulateResult.steps" border stripe size="small">
          <el-table-column prop="stepIndex" label="#" width="50" align="center" />
          <el-table-column prop="nodeCode" label="节点编码" min-width="120" />
          <el-table-column prop="nodeName" label="节点名称" min-width="100" />
          <el-table-column prop="result" label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="stepTagType(row.result)" size="small">
                {{ row.result }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assignee" label="处理人" width="100" />
          <el-table-column prop="comment" label="说明" min-width="120" />
        </el-table>
      </div>

      <template #footer>
        <el-button @click="simulateDialog = false">关闭</el-button>
        <el-button type="primary" :loading="simulating" @click="doSimulate">
          开始模拟
        </el-button>
      </template>
    </el-dialog>

    <!-- 模板库弹窗 -->
    <el-dialog v-model="templateDialog" title="流程模板库" width="800px">
      <div class="template-filter">
        <el-radio-group v-model="templateCategory" @change="loadTemplates">
          <el-radio-button
            v-for="opt in categoryOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div v-loading="templateLoading" class="template-grid">
        <div
          v-for="tpl in templateList"
          :key="tpl.templateCode"
          class="template-card"
          @click="handleImportTemplate(tpl)"
        >
          <div class="card-header">
            <span class="card-name">{{ tpl.templateName }}</span>
            <el-tag :type="categoryTagType(tpl.category)" size="small">
              {{ categoryLabel(tpl.category) }}
            </el-tag>
          </div>
          <p class="card-desc">{{ tpl.description || '暂无描述' }}</p>
          <div class="card-footer">
            <span class="card-uses">使用 {{ tpl.useCount || 0 }} 次</span>
            <el-button type="primary" link size="small">导入</el-button>
          </div>
        </div>
        <el-empty
          v-if="!templateLoading && templateList.length === 0"
          description="暂无模板"
          :image-size="60"
        />
      </div>
    </el-dialog>

    <!-- 导出为模板弹窗 -->
    <el-dialog v-model="exportDialog" title="导出为模板" width="480px">
      <el-form label-width="80px">
        <el-form-item label="模板名称">
          <el-input v-model="exportForm.templateName" placeholder="输入模板名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="exportForm.category" style="width: 100%">
            <el-option
              v-for="opt in categoryOptions.filter((o) => o.value)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportDialog = false">取消</el-button>
        <el-button type="primary" @click="doExport">确认导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-workflow-design {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  padding: 16px;

  .page-header {
    margin-bottom: 12px;
    flex-shrink: 0;

    &-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
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

  .design-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    flex-shrink: 0;

    .toolbar-spacer {
      flex: 1;
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
}

// 版本管理
.version-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.text-muted {
  color: #94a3b8;
  font-size: 12px;
}

// 差异对比
.diff-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .diff-arrow {
    font-weight: 600;
    color: #64748b;
  }
}

.diff-result {
  .diff-section {
    margin-top: 16px;

    h4 {
      margin: 0 0 8px;
      font-size: 14px;
      color: #1e293b;
    }

    .diff-tag {
      margin-right: 6px;
      margin-bottom: 4px;
    }

    .diff-content {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      padding: 12px;
      font-size: 13px;
      line-height: 1.6;
      color: #334155;
      white-space: pre-wrap;
      word-break: break-all;
      max-height: 300px;
      overflow-y: auto;
    }
  }
}

// 模拟运行
.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.simulate-result {
  margin-top: 16px;
}

// 模板库
.template-filter {
  margin-bottom: 16px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  min-height: 200px;
}

.template-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #1890ff;
    box-shadow: 0 2px 8px rgba(24, 144, 255, 0.12);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .card-name {
    font-weight: 600;
    font-size: 14px;
    color: #1e293b;
  }

  .card-desc {
    font-size: 12px;
    color: #64748b;
    line-height: 1.5;
    margin: 0 0 8px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-uses {
    font-size: 11px;
    color: #94a3b8;
  }
}

// P2-4: 移动端响应式适配
@media (max-width: 768px) {
  .page-workflow-design {
    padding: 8px;
    height: calc(100vh - 60px);

    .page-header {
      &-row {
        flex-direction: column;
        gap: 8px;
      }

      h2 {
        font-size: 18px;
      }

      &__sub {
        font-size: 12px;
      }
    }

    .design-toolbar {
      flex-wrap: wrap;

      .el-select {
        width: 100% !important;
      }
    }
  }
}
</style>
