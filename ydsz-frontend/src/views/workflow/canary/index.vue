<!--
  @fileoverview 灰度发布管理页
  @description
    面向运维/管理员的灰度发布控制台：流程定义列表、启动灰度（百分比/白名单）、
    调整比例、全量发布、回滚、灰度日志全流程。
    配套自研工作流 v2 引擎（ydsz_flow_*）使用，PC 端专用。
  @module views/workflow/canary
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 灰度发布管理页
 * @module views/workflow/canary
 * @description P1-2: 灰度发布管理，显示流程定义列表，支持启动灰度/调整比例/全量发布/回滚操作，
 *   展示发布历史日志。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import {
  pageDefinitions,
  publishCanary,
  adjustCanary,
  promoteCanary,
  rollbackCanary,
  getCanaryRolloutLog,
} from '@/api/workflow'
import type {
  FlowDefinitionDTO,
  CanaryRolloutLogDTO,
  PublishCanaryDTO,
} from '@/api/workflow/types'

// ==================== 流程定义列表 ====================
/** 流程定义列表数据 */
const definitionList = ref<FlowDefinitionDTO[]>([])
/** 列表加载状态 */
const loading = ref(false)
/** 流程定义列表总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页条数 */
const pageSize = ref(10)
const searchForm = reactive({
  flowCode: '',
  flowName: '',
  status: '',
})

// ==================== 灰度操作弹窗 ====================
/** 发布灰度弹窗显隐 */
const publishDialog = ref(false)
/** 灰度发布提交状态 */
const publishing = ref(false)
/** 当前操作的流程定义 */
const currentDefinition = ref<FlowDefinitionDTO | null>(null)
const publishForm = reactive<PublishCanaryDTO>({
  strategy: 'PERCENTAGE',
  percentage: 10,
  whitelist: [],
})
const whitelistInput = ref('')

// ==================== 调整比例弹窗 ====================
const adjustDialog = ref(false)
const adjusting = ref(false)
const adjustPercentage = ref(10)

// ==================== 发布历史弹窗 ====================
const logDialog = ref(false)
const logLoading = ref(false)
const rolloutLogs = ref<CanaryRolloutLogDTO[]>([])

const { t } = useI18n()

const strategyOptions = computed(() => [
  { label: t('workflow.canary.strategy.percentage'), value: 'PERCENTAGE' },
  { label: t('workflow.canary.strategy.whitelist'), value: 'WHITELIST' },
  { label: t('workflow.canary.strategy.percentageAndWhitelist'), value: 'PERCENTAGE_AND_WHITELIST' },
])

const actionMap = computed<Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }>>(() => ({
  PUBLISH: { label: t('workflow.canary.action.publish'), type: 'primary' },
  ADJUST: { label: t('workflow.canary.action.adjust'), type: 'warning' },
  PROMOTE: { label: t('workflow.canary.action.promote'), type: 'success' },
  ROLLBACK: { label: t('workflow.canary.action.rollback'), type: 'danger' },
}))

// ==================== 加载流程定义列表 ====================
async function loadDefinitions() {
  loading.value = true
  try {
    const res = await pageDefinitions({
      flowCode: searchForm.flowCode || undefined,
      flowName: searchForm.flowName || undefined,
      status: searchForm.status || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    if (res.data?.code === 0 && res.data?.data) {
      definitionList.value = res.data.data.list || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error(t('workflow.canary.msg.loadFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadDefinitions()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadDefinitions()
}

// ==================== 启动灰度 ====================
function openPublishDialog(row: FlowDefinitionDTO) {
  currentDefinition.value = row
  publishForm.strategy = 'PERCENTAGE'
  publishForm.percentage = 10
  publishForm.whitelist = []
  whitelistInput.value = ''
  publishDialog.value = true
}

function addWhitelist() {
  const ids = whitelistInput.value
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  if (ids.length === 0) {
    ElMessage.warning(t('workflow.canary.msg.invalidUserId'))
    return
  }
  publishForm.whitelist = [...new Set([...(publishForm.whitelist || []), ...ids])]
  whitelistInput.value = ''
}

function removeWhitelist(id: string) {
  publishForm.whitelist = (publishForm.whitelist || []).filter((w) => w !== id)
}

async function submitPublish() {
  if (!currentDefinition.value) return
  if (publishForm.strategy !== 'WHITELIST' && (publishForm.percentage === undefined || publishForm.percentage < 0 || publishForm.percentage > 100)) {
    ElMessage.warning(t('workflow.canary.msg.invalidPercentage'))
    return
  }
  if (publishForm.strategy !== 'PERCENTAGE' && (!publishForm.whitelist || publishForm.whitelist.length === 0)) {
    ElMessage.warning(t('workflow.canary.msg.whitelistRequired'))
    return
  }

  publishing.value = true
  try {
    const res = await publishCanary(currentDefinition.value.id, publishForm)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.canary.msg.publishStarted'))
      publishDialog.value = false
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || t('workflow.canary.msg.publishFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.canary.msg.publishFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    publishing.value = false
  }
}

// ==================== 调整比例 ====================
function openAdjustDialog(row: FlowDefinitionDTO) {
  currentDefinition.value = row
  adjustPercentage.value = 10
  adjustDialog.value = true
}

async function submitAdjust() {
  if (!currentDefinition.value) return
  if (adjustPercentage.value < 0 || adjustPercentage.value > 100) {
    ElMessage.warning(t('workflow.canary.msg.invalidPercentage'))
    return
  }
  adjusting.value = true
  try {
    const res = await adjustCanary(currentDefinition.value.id, adjustPercentage.value)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.canary.msg.adjustSuccess'))
      adjustDialog.value = false
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || t('workflow.canary.msg.adjustFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.canary.msg.adjustFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    adjusting.value = false
  }
}

// ==================== 全量发布 ====================
async function handlePromote(row: FlowDefinitionDTO) {
  try {
    await ElMessageBox.confirm(
      t('workflow.canary.msg.promoteConfirm', { name: row.flowName || row.flowCode }),
      t('workflow.canary.msg.promoteConfirmTitle'),
      { type: 'warning' },
    )
    const res = await promoteCanary(row.id)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.canary.msg.promoteSuccess'))
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || t('workflow.canary.msg.promoteFailed'))
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('workflow.canary.msg.promoteFailedWithMsg', { reason: (e as Error).message }))
    }
  }
}

// ==================== 回滚 ====================
async function handleRollback(row: FlowDefinitionDTO) {
  try {
    await ElMessageBox.confirm(
      t('workflow.canary.msg.rollbackConfirm', { name: row.flowName || row.flowCode }),
      t('workflow.canary.msg.rollbackConfirmTitle'),
      { type: 'warning' },
    )
    const res = await rollbackCanary(row.id)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.canary.msg.rollbackSuccess'))
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || t('workflow.canary.msg.rollbackFailed'))
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('workflow.canary.msg.rollbackFailedWithMsg', { reason: (e as Error).message }))
    }
  }
}

// ==================== 查看发布历史 ====================
async function openLogDialog(row: FlowDefinitionDTO) {
  logDialog.value = true
  logLoading.value = true
  rolloutLogs.value = []
  try {
    const res = await getCanaryRolloutLog(row.flowCode)
    if (res.data?.code === 0 && res.data?.data) {
      rolloutLogs.value = res.data.data || []
    }
  } catch (e) {
    ElMessage.error(t('workflow.canary.msg.loadLogFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    logLoading.value = false
  }
}

onMounted(() => loadDefinitions())
</script>

<template>
  <div class="page-canary">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>{{ t('workflow.canary.title') }}</h2>
          <p class="page-header__sub">{{ t('workflow.canary.subtitle') }}</p>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="page-body">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.flowCode"
          :placeholder="t('workflow.canary.search.flowCode')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="searchForm.flowName"
          :placeholder="t('workflow.canary.search.flowName')"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="searchForm.status"
          :placeholder="t('workflow.canary.search.status')"
          clearable
          style="width: 140px"
        >
          <el-option :label="t('workflow.canary.status.draft')" value="DRAFT" />
          <el-option :label="t('workflow.canary.status.published')" value="PUBLISHED" />
          <el-option :label="t('workflow.canary.status.deprecated')" value="DEPRECATED" />
          <el-option :label="t('workflow.canary.status.offline')" value="OFFLINE" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>{{ t('workflow.canary.search.query') }}
        </el-button>
      </div>

      <!-- 流程定义列表 -->
      <el-table v-loading="loading" :data="definitionList" border stripe>
        <el-table-column prop="id" :label="t('workflow.canary.columns.id')" width="60" />
        <el-table-column prop="flowCode" :label="t('workflow.canary.columns.flowCode')" min-width="140" />
        <el-table-column prop="flowName" :label="t('workflow.canary.columns.flowName')" min-width="140" />
        <el-table-column prop="version" :label="t('workflow.canary.columns.version')" width="70" />
        <el-table-column prop="category" :label="t('workflow.canary.columns.category')" width="90" />
        <el-table-column prop="status" :label="t('workflow.canary.columns.status')" width="100">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'PUBLISHED' ? 'success' :
                row.status === 'DRAFT' ? 'info' :
                row.status === 'DEPRECATED' ? 'warning' :
                'danger'
              "
              size="small"
            >
              {{ row.status === 'PUBLISHED' ? t('workflow.canary.status.published') :
                 row.status === 'DRAFT' ? t('workflow.canary.status.draft') :
                 row.status === 'DEPRECATED' ? t('workflow.canary.status.deprecated') :
                 row.status === 'OFFLINE' ? t('workflow.canary.status.offline') : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('workflow.canary.columns.updateTime')" min-width="150">
          <template #default="{ row }">
            {{ row.updateTime ? dayjs(row.updateTime).format('YYYY-MM-DD HH:mm') : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('workflow.canary.columns.operation')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openPublishDialog(row as FlowDefinitionDTO)">{{ t('workflow.canary.action.publish') }}</el-button>
            <el-button size="small" type="warning" link @click="openAdjustDialog(row as FlowDefinitionDTO)">{{ t('workflow.canary.action.adjust') }}</el-button>
            <el-button size="small" type="success" link @click="handlePromote(row as FlowDefinitionDTO)">{{ t('workflow.canary.action.promote') }}</el-button>
            <el-button size="small" type="danger" link @click="handleRollback(row as FlowDefinitionDTO)">{{ t('workflow.canary.action.rollback') }}</el-button>
            <el-button size="small" link @click="openLogDialog(row as FlowDefinitionDTO)">{{ t('workflow.canary.action.log') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 启动灰度弹窗 -->
    <el-dialog v-model="publishDialog" :title="t('workflow.canary.publish.title')" width="520px">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item :label="t('workflow.canary.publish.flow')">
          <span>{{ currentDefinition?.flowName || currentDefinition?.flowCode }}</span>
        </el-form-item>
        <el-form-item :label="t('workflow.canary.publish.strategy')" required>
          <el-select v-model="publishForm.strategy" style="width: 100%">
            <el-option
              v-for="opt in strategyOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="publishForm.strategy !== 'WHITELIST'" :label="t('workflow.canary.publish.percentage')" required>
          <el-slider v-model="publishForm.percentage" :min="0" :max="100" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="publishForm.strategy !== 'PERCENTAGE'" :label="t('workflow.canary.publish.whitelist')" required>
          <div class="whitelist-section">
            <div class="whitelist-input-row">
              <el-input
                v-model="whitelistInput"
                :placeholder="t('workflow.canary.publish.whitelistPlaceholder')"
                @keyup.enter="addWhitelist"
              />
              <el-button @click="addWhitelist">{{ t('workflow.canary.publish.add') }}</el-button>
            </div>
            <div class="whitelist-tags">
              <el-tag
                v-for="id in publishForm.whitelist"
                :key="id"
                closable
                @close="removeWhitelist(id)"
                style="margin: 2px"
              >{{ id }}</el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialog = false">{{ t('workflow.canary.publish.cancel') }}</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">{{ t('workflow.canary.publish.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 调整比例弹窗 -->
    <el-dialog v-model="adjustDialog" :title="t('workflow.canary.adjust.title')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="t('workflow.canary.adjust.flow')">
          <span>{{ currentDefinition?.flowName || currentDefinition?.flowCode }}</span>
        </el-form-item>
        <el-form-item :label="t('workflow.canary.adjust.percentage')">
          <el-slider v-model="adjustPercentage" :min="0" :max="100" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialog = false">{{ t('workflow.canary.adjust.cancel') }}</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">{{ t('workflow.canary.adjust.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 发布历史弹窗 -->
    <el-dialog v-model="logDialog" :title="t('workflow.canary.log.title')" width="720px">
      <el-table v-loading="logLoading" :data="rolloutLogs" border stripe max-height="400">
        <el-table-column prop="action" :label="t('workflow.canary.columns.actionType')" width="120">
          <template #default="{ row }">
            <el-tag :type="actionMap[row.action]?.type || 'info'" size="small">
              {{ actionMap[row.action]?.label || row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="flowName" :label="t('workflow.canary.columns.flowName')" min-width="120">
          <template #default="{ row }">
            {{ row.flowName || row.flowCode }}
          </template>
        </el-table-column>
        <el-table-column prop="percentage" :label="t('workflow.canary.columns.percentage')" width="90">
          <template #default="{ row }">
            {{ row.percentage !== undefined ? row.percentage + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" :label="t('workflow.canary.columns.operator')" min-width="100">
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="detail" :label="t('workflow.canary.columns.detail')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="operateTime" :label="t('workflow.canary.columns.operateTime')" min-width="150">
          <template #default="{ row }">
            {{ row.operateTime ? dayjs(row.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logLoading && rolloutLogs.length === 0" :description="t('workflow.canary.log.empty')" />
      <template #footer>
        <el-button @click="logDialog = false">{{ t('workflow.canary.log.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-canary {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

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

.page-body {
  border-radius: 6px;
}

.search-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.whitelist-section {
  width: 100%;
}

.whitelist-input-row {
  display: flex;
  gap: 8px;
}

.whitelist-tags {
  margin-top: 8px;
  min-height: 28px;
}
</style>
