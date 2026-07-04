<script setup lang="ts">
/**
 * @file 灰度发布管理页
 * @module views/workflow/canary
 * @description P1-2: 灰度发布管理，显示流程定义列表，支持启动灰度/调整比例/全量发布/回滚操作，
 *   展示发布历史日志。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const definitionList = ref<FlowDefinitionDTO[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchForm = reactive({
  flowCode: '',
  flowName: '',
  status: '',
})

// ==================== 灰度操作弹窗 ====================
const publishDialog = ref(false)
const publishing = ref(false)
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

const strategyOptions = [
  { label: '按比例', value: 'PERCENTAGE' },
  { label: '白名单', value: 'WHITELIST' },
  { label: '比例+白名单', value: 'PERCENTAGE_AND_WHITELIST' },
]

const actionMap: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  PUBLISH: { label: '启动灰度', type: 'primary' },
  ADJUST: { label: '调整比例', type: 'warning' },
  PROMOTE: { label: '全量发布', type: 'success' },
  ROLLBACK: { label: '回滚', type: 'danger' },
}

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
    ElMessage.error('加载流程定义失败：' + (e as Error).message)
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
    .map((s) => Number(s.trim()))
    .filter((n) => !isNaN(n) && n > 0)
  if (ids.length === 0) {
    ElMessage.warning('请输入有效的用户 ID')
    return
  }
  publishForm.whitelist = [...new Set([...(publishForm.whitelist || []), ...ids])]
  whitelistInput.value = ''
}

function removeWhitelist(id: number) {
  publishForm.whitelist = (publishForm.whitelist || []).filter((w) => w !== id)
}

async function submitPublish() {
  if (!currentDefinition.value) return
  if (publishForm.strategy !== 'WHITELIST' && (publishForm.percentage === undefined || publishForm.percentage < 0 || publishForm.percentage > 100)) {
    ElMessage.warning('请输入有效的灰度比例（0-100）')
    return
  }
  if (publishForm.strategy !== 'PERCENTAGE' && (!publishForm.whitelist || publishForm.whitelist.length === 0)) {
    ElMessage.warning('请添加白名单用户')
    return
  }

  publishing.value = true
  try {
    const res = await publishCanary(currentDefinition.value.id, publishForm)
    if (res.data?.code === 0) {
      ElMessage.success('灰度发布已启动')
      publishDialog.value = false
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || '启动失败')
    }
  } catch (e) {
    ElMessage.error('启动失败：' + (e as Error).message)
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
    ElMessage.warning('请输入有效的灰度比例（0-100）')
    return
  }
  adjusting.value = true
  try {
    const res = await adjustCanary(currentDefinition.value.id, adjustPercentage.value)
    if (res.data?.code === 0) {
      ElMessage.success('灰度比例已调整')
      adjustDialog.value = false
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || '调整失败')
    }
  } catch (e) {
    ElMessage.error('调整失败：' + (e as Error).message)
  } finally {
    adjusting.value = false
  }
}

// ==================== 全量发布 ====================
async function handlePromote(row: FlowDefinitionDTO) {
  try {
    await ElMessageBox.confirm(
      `确认对流程「${row.flowName || row.flowCode}」执行全量发布？全量发布后灰度将转为正式版本。`,
      '全量发布确认',
      { type: 'warning' },
    )
    const res = await promoteCanary(row.id)
    if (res.data?.code === 0) {
      ElMessage.success('全量发布成功')
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || '发布失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('发布失败：' + (e as Error).message)
    }
  }
}

// ==================== 回滚 ====================
async function handleRollback(row: FlowDefinitionDTO) {
  try {
    await ElMessageBox.confirm(
      `确认对流程「${row.flowName || row.flowCode}」执行灰度回滚？回滚后灰度版本将被废弃。`,
      '回滚确认',
      { type: 'warning' },
    )
    const res = await rollbackCanary(row.id)
    if (res.data?.code === 0) {
      ElMessage.success('回滚成功')
      loadDefinitions()
    } else {
      ElMessage.error(res.data?.message || '回滚失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('回滚失败：' + (e as Error).message)
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
    ElMessage.error('加载发布历史失败：' + (e as Error).message)
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
          <h2>灰度发布管理</h2>
          <p class="page-header__sub">管理流程定义的灰度发布，支持按比例/白名单灰度、调整、全量发布和回滚</p>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="page-body">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.flowCode"
          placeholder="流程编码"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="searchForm.flowName"
          placeholder="流程名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="searchForm.status"
          placeholder="状态"
          clearable
          style="width: 140px"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已停用" value="DEPRECATED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
      </div>

      <!-- 流程定义列表 -->
      <el-table v-loading="loading" :data="definitionList" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="flowCode" label="流程编码" min-width="140" />
        <el-table-column prop="flowName" label="流程名称" min-width="140" />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="category" label="类别" width="90" />
        <el-table-column prop="status" label="状态" width="100">
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
              {{ row.status === 'PUBLISHED' ? '已发布' :
                 row.status === 'DRAFT' ? '草稿' :
                 row.status === 'DEPRECATED' ? '已停用' :
                 row.status === 'OFFLINE' ? '已下线' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ row.updateTime ? dayjs(row.updateTime).format('YYYY-MM-DD HH:mm') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openPublishDialog(row as FlowDefinitionDTO)">启动灰度</el-button>
            <el-button size="small" type="warning" link @click="openAdjustDialog(row as FlowDefinitionDTO)">调整比例</el-button>
            <el-button size="small" type="success" link @click="handlePromote(row as FlowDefinitionDTO)">全量发布</el-button>
            <el-button size="small" type="danger" link @click="handleRollback(row as FlowDefinitionDTO)">回滚</el-button>
            <el-button size="small" link @click="openLogDialog(row as FlowDefinitionDTO)">发布历史</el-button>
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
    <el-dialog v-model="publishDialog" title="启动灰度发布" width="520px">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="流程">
          <span>{{ currentDefinition?.flowName || currentDefinition?.flowCode }}</span>
        </el-form-item>
        <el-form-item label="灰度策略" required>
          <el-select v-model="publishForm.strategy" style="width: 100%">
            <el-option
              v-for="opt in strategyOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="publishForm.strategy !== 'WHITELIST'" label="灰度比例" required>
          <el-slider v-model="publishForm.percentage" :min="0" :max="100" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="publishForm.strategy !== 'PERCENTAGE'" label="白名单用户" required>
          <div class="whitelist-section">
            <div class="whitelist-input-row">
              <el-input
                v-model="whitelistInput"
                placeholder="输入用户 ID，逗号分隔"
                @keyup.enter="addWhitelist"
              />
              <el-button @click="addWhitelist">添加</el-button>
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
        <el-button @click="publishDialog = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">确认启动</el-button>
      </template>
    </el-dialog>

    <!-- 调整比例弹窗 -->
    <el-dialog v-model="adjustDialog" title="调整灰度比例" width="420px">
      <el-form label-width="80px">
        <el-form-item label="流程">
          <span>{{ currentDefinition?.flowName || currentDefinition?.flowCode }}</span>
        </el-form-item>
        <el-form-item label="灰度比例">
          <el-slider v-model="adjustPercentage" :min="0" :max="100" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialog = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 发布历史弹窗 -->
    <el-dialog v-model="logDialog" title="灰度发布历史" width="720px">
      <el-table v-loading="logLoading" :data="rolloutLogs" border stripe max-height="400">
        <el-table-column prop="action" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="actionMap[row.action]?.type || 'info'" size="small">
              {{ actionMap[row.action]?.label || row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="flowName" label="流程名称" min-width="120">
          <template #default="{ row }">
            {{ row.flowName || row.flowCode }}
          </template>
        </el-table-column>
        <el-table-column prop="percentage" label="灰度比例" width="90">
          <template #default="{ row }">
            {{ row.percentage !== undefined ? row.percentage + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="100">
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="150" show-overflow-tooltip />
        <el-table-column prop="operateTime" label="操作时间" min-width="150">
          <template #default="{ row }">
            {{ row.operateTime ? dayjs(row.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logLoading && rolloutLogs.length === 0" description="暂无发布历史" />
      <template #footer>
        <el-button @click="logDialog = false">关闭</el-button>
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
