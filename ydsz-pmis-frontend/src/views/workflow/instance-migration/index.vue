<!--
  @fileoverview 流程实例迁移向导页
  @description
    4 步向导：选择源/目标流程定义 → 节点映射 → 预览（dry run）→ 确认执行。
    用于将源流程定义下的存量实例批量迁移到新版本/新定义，支持字段映射。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/instance-migration
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 流程实例迁移向导页
 * @module views/workflow/instance-migration
 * @description P3-5a: 将源流程定义下的实例迁移到目标定义。
 *   4 步向导：选择定义 → 节点映射 → 预览（dry run）→ 确认执行。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDefinitions,
  previewInstanceMigration,
  executeInstanceMigration,
  autoMapNodes,
} from '@/api/workflow'
import type {
  FlowDefinitionDTO,
  InstanceMigrationDTO,
  InstanceMigrationResultDTO,
} from '@/api/workflow/types'

defineOptions({ name: 'InstanceMigration' })

const { t } = useI18n()
const router = useRouter()

// ==================== 步骤控制 ====================
const active = ref(0)

// ==================== 步骤 1：流程定义 ====================
const definitionList = ref<FlowDefinitionDTO[]>([])
const definitionLoading = ref(false)
const sourceDefId = ref<number | undefined>(undefined)
const targetDefId = ref<number | undefined>(undefined)

const sameDefinition = computed(
  () => sourceDefId.value !== undefined && sourceDefId.value === targetDefId.value,
)

// 流程定义下拉展示文案
function defLabel(d: FlowDefinitionDTO): string {
  return t('workflow.instanceMigration.step1.definitionOption', {
    name: d.flowName || d.flowCode,
    version: d.version,
    code: d.flowCode,
  })
}

// ==================== 步骤 2：节点映射 ====================
interface MappingRow {
  source: string
  target: string
}
const mappingRows = ref<MappingRow[]>([])
const autoMapping = ref(false)
// 目标节点候选选项（取自自动映射结果与手动输入的并集）
const targetOptions = ref<string[]>([])

function syncTargetOptions() {
  const set = new Set<string>()
  for (const row of mappingRows.value) {
    if (row.target.trim()) set.add(row.target.trim())
  }
  targetOptions.value = Array.from(set)
}

async function handleAutoMap() {
  if (sourceDefId.value === undefined || targetDefId.value === undefined) {
    ElMessage.warning(t('workflow.instanceMigration.step2.noDefinition'))
    return
  }
  autoMapping.value = true
  try {
    const res = await autoMapNodes(sourceDefId.value, targetDefId.value)
    if (res.data?.code === 0 && res.data?.data) {
      const map = res.data.data
      mappingRows.value = Object.keys(map).map((k) => ({ source: k, target: map[k] }))
      syncTargetOptions()
      ElMessage.success(t('workflow.instanceMigration.step2.autoMapSuccess', { n: mappingRows.value.length }))
    } else {
      ElMessage.error(res.data?.message || t('workflow.instanceMigration.step2.autoMapFailed', { message: '' }))
    }
  } catch (e) {
    ElMessage.error(t('workflow.instanceMigration.step2.autoMapFailed', { message: (e as Error).message }))
  } finally {
    autoMapping.value = false
  }
}

function addMapping() {
  mappingRows.value.push({ source: '', target: '' })
}

function removeMapping(index: number) {
  mappingRows.value.splice(index, 1)
  syncTargetOptions()
}

function onTargetChange() {
  syncTargetOptions()
}

// ==================== 步骤 3：预览结果 ====================
const previewing = ref(false)
const previewResult = ref<InstanceMigrationResultDTO | null>(null)

async function runPreview() {
  previewing.value = true
  try {
    const res = await previewInstanceMigration(buildDto())
    if (res.data?.code === 0 && res.data?.data) {
      previewResult.value = res.data.data
    } else {
      ElMessage.error(res.data?.message || t('workflow.instanceMigration.step3.previewFailed', { message: '' }))
    }
  } catch (e) {
    ElMessage.error(t('workflow.instanceMigration.step3.previewFailed', { message: (e as Error).message }))
  } finally {
    previewing.value = false
  }
}

// ==================== 步骤 4：执行迁移 ====================
const executing = ref(false)
const executeResult = ref<InstanceMigrationResultDTO | null>(null)
const executed = ref(false)

async function runExecute() {
  try {
    await ElMessageBox.confirm(
      t('workflow.instanceMigration.step4.confirmExecute'),
      t('workflow.instanceMigration.step4.confirmExecuteTitle'),
      { type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  executing.value = true
  try {
    const res = await executeInstanceMigration(buildDto())
    if (res.data?.code === 0 && res.data?.data) {
      executeResult.value = res.data.data
      executed.value = true
      ElMessage.success(t('workflow.instanceMigration.step4.executeSuccess'))
    } else {
      ElMessage.error(res.data?.message || t('workflow.instanceMigration.step4.executeFailed', { message: '' }))
    }
  } catch (e) {
    ElMessage.error(t('workflow.instanceMigration.step4.executeFailed', { message: (e as Error).message }))
  } finally {
    executing.value = false
  }
}

// ==================== DTO 构建 ====================
function buildDto(): InstanceMigrationDTO {
  const nodeMapping: Record<string, string> = {}
  for (const row of mappingRows.value) {
    const s = row.source.trim()
    const tg = row.target.trim()
    if (s && tg) nodeMapping[s] = tg
  }
  return {
    sourceDefinitionId: sourceDefId.value as number,
    targetDefinitionId: targetDefId.value as number,
    nodeMapping,
  }
}

// ==================== 状态样式映射 ====================
function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'MIGRATED') return 'success'
  if (status === 'SKIPPED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function statusText(status: string, isPreview: boolean): string {
  const scope = isPreview ? 'step3' : 'step4'
  const key = `workflow.instanceMigration.${scope}.status.${status}`
  const fallback = status
  // 仅在 key 存在时返回翻译，否则回退原值
  return t(key) !== key ? t(key) : fallback
}

// ==================== 步骤跳转 ====================
function next() {
  if (active.value === 0) {
    if (sourceDefId.value === undefined || targetDefId.value === undefined) {
      ElMessage.warning(t('workflow.instanceMigration.step1.selectBoth'))
      return
    }
    if (sameDefinition.value) {
      ElMessage.warning(t('workflow.instanceMigration.step1.sameDefinition'))
      return
    }
  }
  if (active.value === 2) {
    // 进入步骤 3 自动触发预览
    runPreview()
  }
  if (active.value < 3) active.value++
}

function prev() {
  if (active.value > 0) active.value--
}

function resetWizard() {
  active.value = 0
  mappingRows.value = []
  targetOptions.value = []
  previewResult.value = null
  executeResult.value = null
  executed.value = false
}

function goInstanceList() {
  router.push('/workflow/instance')
}

// ==================== 加载流程定义列表 ====================
async function loadDefinitions() {
  definitionLoading.value = true
  try {
    const res = await pageDefinitions({ pageNum: 1, pageSize: 200 })
    if (res.data?.code === 0 && res.data?.data) {
      definitionList.value = res.data.data.list || []
    }
  } catch (e) {
    ElMessage.error(t('workflow.instanceMigration.step1.loadFailed', { message: (e as Error).message }))
  } finally {
    definitionLoading.value = false
  }
}

onMounted(loadDefinitions)
</script>

<template>
  <div class="page-instance-migration">
    <!-- 页头 -->
    <div class="page-header">
      <h2>{{ t('workflow.instanceMigration.title') }}</h2>
      <p class="page-header__sub">{{ t('workflow.instanceMigration.subtitle') }}</p>
    </div>

    <el-card shadow="never" class="page-body">
      <!-- 步骤条 -->
      <el-steps :active="active" finish-status="success" align-center class="wizard-steps">
        <el-step :title="t('workflow.instanceMigration.steps.selectDefinition')" />
        <el-step :title="t('workflow.instanceMigration.steps.nodeMapping')" />
        <el-step :title="t('workflow.instanceMigration.steps.preview')" />
        <el-step :title="t('workflow.instanceMigration.steps.confirm')" />
      </el-steps>

      <!-- 步骤 1：选择源/目标流程定义 -->
      <div v-show="active === 0" class="step-panel">
        <h3 class="step-title">{{ t('workflow.instanceMigration.step1.title') }}</h3>
        <p class="step-desc">{{ t('workflow.instanceMigration.step1.desc') }}</p>
        <el-form label-width="140px" class="step-form">
          <el-form-item :label="t('workflow.instanceMigration.step1.sourceDefinition')" required>
            <el-select
              v-model="sourceDefId"
              :placeholder="t('workflow.instanceMigration.step1.sourcePlaceholder')"
              filterable
              :loading="definitionLoading"
              style="width: 100%"
            >
              <el-option
                v-for="d in definitionList"
                :key="d.id"
                :label="defLabel(d)"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workflow.instanceMigration.step1.targetDefinition')" required>
            <el-select
              v-model="targetDefId"
              :placeholder="t('workflow.instanceMigration.step1.targetPlaceholder')"
              filterable
              :loading="definitionLoading"
              style="width: 100%"
            >
              <el-option
                v-for="d in definitionList"
                :key="d.id"
                :label="defLabel(d)"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="sameDefinition">
            <el-alert
              :title="t('workflow.instanceMigration.step1.sameDefinition')"
              type="error"
              :closable="false"
              show-icon
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤 2：节点映射 -->
      <div v-show="active === 1" class="step-panel">
        <h3 class="step-title">{{ t('workflow.instanceMigration.step2.title') }}</h3>
        <p class="step-desc">{{ t('workflow.instanceMigration.step2.desc') }}</p>
        <div class="step-toolbar">
          <el-button type="primary" :loading="autoMapping" @click="handleAutoMap">
            {{ autoMapping ? t('workflow.instanceMigration.step2.autoMapping') : t('workflow.instanceMigration.step2.autoMap') }}
          </el-button>
          <el-button @click="addMapping">{{ t('workflow.instanceMigration.step2.addMapping') }}</el-button>
        </div>
        <el-table :data="mappingRows" border stripe>
          <el-table-column :label="t('workflow.instanceMigration.step2.sourceNode')" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.source" :placeholder="t('workflow.instanceMigration.step2.sourceNodePlaceholder')" />
            </template>
          </el-table-column>
          <el-table-column :label="t('workflow.instanceMigration.step2.targetNode')" min-width="220">
            <template #default="{ row }">
              <el-select
                v-model="row.target"
                :placeholder="t('workflow.instanceMigration.step2.targetNodePlaceholder')"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="onTargetChange"
              >
                <el-option
                  v-for="opt in targetOptions"
                  :key="opt"
                  :label="opt"
                  :value="opt"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="t('workflow.instanceMigration.step2.removeMapping')" width="100" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeMapping($index)">
                {{ t('workflow.instanceMigration.step2.removeMapping') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="mappingRows.length === 0"
          :description="t('workflow.instanceMigration.step2.empty')"
        />
      </div>

      <!-- 步骤 3：预览迁移结果 -->
      <div v-show="active === 2" class="step-panel">
        <h3 class="step-title">{{ t('workflow.instanceMigration.step3.title') }}</h3>
        <p class="step-desc">{{ t('workflow.instanceMigration.step3.desc') }}</p>
        <div class="step-toolbar">
          <el-button type="primary" :loading="previewing" @click="runPreview">
            {{ previewing ? t('workflow.instanceMigration.step3.previewing') : t('workflow.instanceMigration.step3.rePreview') }}
          </el-button>
        </div>
        <!-- 统计卡片 -->
        <div v-if="previewResult" class="stat-cards">
          <div class="stat-card">
            <div class="stat-card__value">{{ previewResult.totalInstances }}</div>
            <div class="stat-card__label">{{ t('workflow.instanceMigration.step3.stats.total') }}</div>
          </div>
          <div class="stat-card stat-card--success">
            <div class="stat-card__value">{{ previewResult.migratedCount }}</div>
            <div class="stat-card__label">{{ t('workflow.instanceMigration.step3.stats.migrated') }}</div>
          </div>
          <div class="stat-card stat-card--warning">
            <div class="stat-card__value">{{ previewResult.skippedCount }}</div>
            <div class="stat-card__label">{{ t('workflow.instanceMigration.step3.stats.skipped') }}</div>
          </div>
          <div class="stat-card stat-card--danger">
            <div class="stat-card__value">{{ previewResult.failedCount }}</div>
            <div class="stat-card__label">{{ t('workflow.instanceMigration.step3.stats.failed') }}</div>
          </div>
        </div>
        <!-- 详情列表 -->
        <el-table
          v-if="previewResult"
          v-loading="previewing"
          :data="previewResult.details"
          border
          stripe
          max-height="420"
        >
          <el-table-column prop="instanceId" :label="t('workflow.instanceMigration.step3.columns.instanceId')" width="100" />
          <el-table-column prop="instanceTitle" :label="t('workflow.instanceMigration.step3.columns.instanceTitle')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="oldNodeCode" :label="t('workflow.instanceMigration.step3.columns.oldNodeCode')" min-width="130" />
          <el-table-column prop="newNodeCode" :label="t('workflow.instanceMigration.step3.columns.newNodeCode')" min-width="130" />
          <el-table-column :label="t('workflow.instanceMigration.step3.columns.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ statusText(row.status, true) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" :label="t('workflow.instanceMigration.step3.columns.reason')" min-width="180" show-overflow-tooltip />
        </el-table>
        <el-empty
          v-if="previewResult && previewResult.details.length === 0"
          :description="t('workflow.instanceMigration.step3.empty')"
        />
      </div>

      <!-- 步骤 4：确认执行 -->
      <div v-show="active === 3" class="step-panel">
        <h3 class="step-title">{{ t('workflow.instanceMigration.step4.title') }}</h3>
        <p class="step-desc">{{ t('workflow.instanceMigration.step4.desc') }}</p>

        <!-- 未执行：展示执行按钮 -->
        <div v-if="!executed" class="step-toolbar">
          <el-button type="danger" :loading="executing" @click="runExecute">
            {{ executing ? t('workflow.instanceMigration.step4.executing') : t('workflow.instanceMigration.step4.execute') }}
          </el-button>
        </div>

        <!-- 执行完成：展示结果 -->
        <template v-if="executed && executeResult">
          <el-alert
            :title="t('workflow.instanceMigration.step4.done')"
            type="success"
            :closable="false"
            show-icon
            class="done-alert"
          />
          <div class="stat-cards">
            <div class="stat-card">
              <div class="stat-card__value">{{ executeResult.totalInstances }}</div>
              <div class="stat-card__label">{{ t('workflow.instanceMigration.step3.stats.total') }}</div>
            </div>
            <div class="stat-card stat-card--success">
              <div class="stat-card__value">{{ executeResult.migratedCount }}</div>
              <div class="stat-card__label">{{ t('workflow.instanceMigration.step4.status.MIGRATED') }}</div>
            </div>
            <div class="stat-card stat-card--warning">
              <div class="stat-card__value">{{ executeResult.skippedCount }}</div>
              <div class="stat-card__label">{{ t('workflow.instanceMigration.step4.status.SKIPPED') }}</div>
            </div>
            <div class="stat-card stat-card--danger">
              <div class="stat-card__value">{{ executeResult.failedCount }}</div>
              <div class="stat-card__label">{{ t('workflow.instanceMigration.step4.status.FAILED') }}</div>
            </div>
          </div>
          <el-table v-loading="executing" :data="executeResult.details" border stripe max-height="420">
            <el-table-column prop="instanceId" :label="t('workflow.instanceMigration.step4.columns.instanceId')" width="100" />
            <el-table-column prop="instanceTitle" :label="t('workflow.instanceMigration.step4.columns.instanceTitle')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="oldNodeCode" :label="t('workflow.instanceMigration.step4.columns.oldNodeCode')" min-width="130" />
            <el-table-column prop="newNodeCode" :label="t('workflow.instanceMigration.step4.columns.newNodeCode')" min-width="130" />
            <el-table-column :label="t('workflow.instanceMigration.step4.columns.status')" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusText(row.status, false) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('workflow.instanceMigration.step4.columns.reason')" min-width="180" show-overflow-tooltip />
          </el-table>
          <div class="step-toolbar">
            <el-button type="primary" @click="goInstanceList">
              {{ t('workflow.instanceMigration.step4.backToInstance') }}
            </el-button>
            <el-button @click="resetWizard">{{ t('workflow.instanceMigration.step4.restart') }}</el-button>
          </div>
        </template>
      </div>

      <!-- 底部导航按钮 -->
      <div class="wizard-footer">
        <el-button v-if="active > 0 && !(active === 3 && executed)" @click="prev">
          {{ t('workflow.instanceMigration.buttons.prev') }}
        </el-button>
        <el-button
          v-if="active < 3"
          type="primary"
          :disabled="active === 0 && sameDefinition"
          @click="next"
        >
          {{ t('workflow.instanceMigration.buttons.next') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page-instance-migration {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

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
  border-radius: 6px;
}

.wizard-steps {
  margin-bottom: 24px;
}

.step-panel {
  min-height: 280px;
}

.step-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: #1e293b;
}

.step-desc {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
}

.step-form {
  max-width: 640px;
}

.step-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.stat-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.stat-card {
  flex: 1 1 140px;
  min-width: 140px;
  padding: 16px;
  border-radius: 6px;
  background: #f1f5f9;
  text-align: center;

  &__value {
    font-size: 26px;
    font-weight: 600;
    color: #1e293b;
    line-height: 1.2;
  }

  &__label {
    margin-top: 4px;
    font-size: 13px;
    color: #64748b;
  }

  &--success {
    background: #dcfce7;

    .stat-card__value {
      color: #15803d;
    }
  }

  &--warning {
    background: #fef9c3;

    .stat-card__value {
      color: #a16207;
    }
  }

  &--danger {
    background: #fee2e2;

    .stat-card__value {
      color: #b91c1c;
    }
  }
}

.done-alert {
  margin-bottom: 16px;
}

.wizard-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}
</style>
