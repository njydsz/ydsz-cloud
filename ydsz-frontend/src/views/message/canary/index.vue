<!--
  @fileoverview 消息通知引擎 - 灰度实验管理页面
  @description 灰度发布与 A/B 实验的核心管控页面：
  - Tab 1 灰度桶管理：按 canaryKey 筛选，列表展示 canaryKey/percentage/experimentTemplateCode/
    experimentChannel/status/description，支持新增/编辑（Upsert）
  - Tab 2 A/B 报表：输入 canaryKey + 日期范围，拉取报表，对照组与实验组并排对比展示
  - 命中检查工具：输入 canaryKey + bucketValue 检查是否命中灰度
  @module views/message/canary
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getCanaryPage,
  upsertCanary,
  getCanaryReport,
  checkCanaryHit,
} from '@/api/message'
import type {
  MsgCanaryVO,
  CanaryUpsertDTO,
  CanaryReportVO,
  MessageChannel,
  EnableStatus,
} from '@/api/message/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 当前激活的 Tab */
const activeTab = ref<'bucket' | 'report'>('bucket')

// ==================== Tab 1: 灰度桶管理 ====================

/** 查询参数 */
const query = reactive({
  page: 1,
  size: 10,
  canaryKey: undefined as string | undefined,
  status: undefined as string | undefined,
})

/** 列表数据 */
const list = ref<MsgCanaryVO[]>([])
/** 总数 */
const total = ref(0)
/** 加载中 */
const loading = ref(false)

/** Element Plus el-tag type 联合类型 */
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 通道选项 */
const channelOptions: { label: string; value: MessageChannel }[] = [
  { label: t('message.channelSms'), value: 'SMS' },
  { label: t('message.channelEmail'), value: 'EMAIL' },
  { label: t('message.channelPush'), value: 'PUSH' },
  { label: t('message.channelInApp'), value: 'INAPP' },
  { label: t('message.channelWebhook'), value: 'WEBHOOK' },
  { label: t('message.channelDingtalk'), value: 'DINGTALK' },
  { label: t('message.channelWecom'), value: 'WECOM' },
  { label: t('message.channelFeishu'), value: 'FEISHU' },
]

/** 状态选项 */
const statusOptions: { label: string; value: EnableStatus }[] = [
  { label: t('message.statusEnabled'), value: 'ENABLED' },
  { label: t('message.statusDisabled'), value: 'DISABLED' },
]

/** 通道文案映射 */
const channelLabelMap: Record<MessageChannel, string> = {
  SMS: t('message.channelSms'),
  EMAIL: t('message.channelEmail'),
  PUSH: t('message.channelPush'),
  INAPP: t('message.channelInApp'),
  WEBHOOK: t('message.channelWebhook'),
  DINGTALK: t('message.channelDingtalk'),
  WECOM: t('message.channelWecom'),
  FEISHU: t('message.channelFeishu'),
}

/** 状态 Tag 类型映射 */
const statusTagType: Record<EnableStatus, TagType> = {
  ENABLED: 'success',
  DISABLED: 'info',
}

/** 状态文案映射 */
const statusLabelMap: Record<EnableStatus, string> = {
  ENABLED: t('message.statusEnabled'),
  DISABLED: t('message.statusDisabled'),
}

/** 获取通道文案 */
const getChannelLabel = (channel: string): string => {
  return channelLabelMap[channel as MessageChannel] ?? channel
}

/** 获取状态 Tag 类型 */
const getStatusTagType = (status: string): TagType => {
  return statusTagType[status as EnableStatus] ?? 'info'
}

/** 获取状态文案 */
const getStatusLabel = (status: string): string => {
  return statusLabelMap[status as EnableStatus] ?? status
}

// ==================== Upsert 弹窗 ====================

/** 弹窗显示 */
const dialogVisible = ref(false)
/** 弹窗标题 */
const dialogTitle = ref('')
/** 提交中 */
const submitting = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)

/** 表单数据 */
const form = reactive<CanaryUpsertDTO>({
  id: undefined,
  canaryKey: '',
  bucketTotal: 100,
  percentage: 10,
  experimentTemplateCode: '',
  experimentChannel: undefined,
  status: 'ENABLED',
  description: '',
})

/** 表单校验规则 */
const formRules = {
  canaryKey: [{ required: true, message: t('message.canaryKey'), trigger: 'blur' }],
  percentage: [{ required: true, message: t('message.percentage'), trigger: 'blur' }],
}

/** 表单引用 */
const formRef = ref()

/** 重置表单 */
const resetForm = () => {
  form.id = undefined
  form.canaryKey = ''
  form.bucketTotal = 100
  form.percentage = 10
  form.experimentTemplateCode = ''
  form.experimentChannel = undefined
  form.status = 'ENABLED'
  form.description = ''
}

/** 拉取列表 */
const fetchList = async () => {
  loading.value = true
  try {
    const resp = await getCanaryPage(query)
    list.value = resp.data?.records ?? []
    total.value = resp.data?.total ?? 0
  } catch {
    // 静默失败
  } finally {
    loading.value = false
  }
}

/** 搜索 */
const handleSearch = () => {
  query.page = 1
  fetchList()
}

/** 重置 */
const handleReset = () => {
  query.canaryKey = undefined
  query.status = undefined
  query.page = 1
  fetchList()
}

/** 翻页 */
const handlePageChange = (page: number) => {
  query.page = page
  fetchList()
}

/** 每页条数变化 */
const handleSizeChange = (size: number) => {
  query.size = size
  query.page = 1
  fetchList()
}

/** 新建 */
const handleCreate = () => {
  resetForm()
  isEdit.value = false
  dialogTitle.value = t('message.create')
  dialogVisible.value = true
}

/** 编辑 */
const handleEdit = (row: MsgCanaryVO) => {
  resetForm()
  isEdit.value = true
  dialogTitle.value = t('message.edit')
  form.id = row.id
  form.canaryKey = row.canaryKey
  form.bucketTotal = row.bucketTotal
  form.percentage = row.percentage
  form.experimentTemplateCode = row.experimentTemplateCode
  form.experimentChannel = row.experimentChannel
  form.status = row.status
  form.description = row.description
  dialogVisible.value = true
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) {
      ElMessage.warning(t('common.pleaseCheckForm'))
      return
    }
    submitting.value = true
    try {
      await upsertCanary(form)
      ElMessage.success(t('message.upsertSuccess'))
      dialogVisible.value = false
      fetchList()
    } catch {
      // 静默失败
    } finally {
      submitting.value = false
    }
  })
}

// ==================== 命中检查 ====================

/** 命中检查弹窗 */
const hitVisible = ref(false)
/** 命中检查参数 */
const hitForm = reactive({
  canaryKey: '',
  bucketValue: '',
})
/** 命中结果 */
const hitResult = ref<boolean | null>(null)

/** 打开命中检查 */
const handleHitCheck = (row?: MsgCanaryVO) => {
  hitForm.canaryKey = row?.canaryKey ?? ''
  hitForm.bucketValue = ''
  hitResult.value = null
  hitVisible.value = true
}

/** 执行命中检查 */
const handleHitCheckSubmit = async () => {
  if (!hitForm.canaryKey || !hitForm.bucketValue) {
    ElMessage.warning(t('common.pleaseCheckForm'))
    return
  }
  try {
    const resp = await checkCanaryHit({
      canaryKey: hitForm.canaryKey,
      bucketValue: hitForm.bucketValue,
    })
    hitResult.value = resp.data ?? false
  } catch {
    // 静默失败
  }
}

// ==================== Tab 2: A/B 报表 ====================

/** 报表查询参数 */
const reportQuery = reactive({
  canaryKey: '',
  start: '',
  end: '',
})

/** 报表日期范围 */
const reportDateRange = ref<[string, string] | null>(null)

/** 报表加载中 */
const reportLoading = ref(false)

/** 报表数据 */
const report = ref<CanaryReportVO | null>(null)

/** 格式化比率 */
const formatRate = (rate?: number): string => {
  if (rate === undefined || rate === null) return '-'
  return `${rate.toFixed(2)}%`
}

/** 拉取报表 */
const fetchReport = async () => {
  if (!reportQuery.canaryKey) {
    ElMessage.warning(t('message.canaryKey'))
    return
  }
  if (!reportDateRange.value || reportDateRange.value.length !== 2) {
    ElMessage.warning(t('message.startDate') + '/' + t('message.endDate'))
    return
  }
  reportQuery.start = reportDateRange.value[0]
  reportQuery.end = reportDateRange.value[1]
  reportLoading.value = true
  try {
    const resp = await getCanaryReport({
      canaryKey: reportQuery.canaryKey,
      start: reportQuery.start,
      end: reportQuery.end,
    })
    report.value = resp.data ?? null
  } catch {
    // 静默失败
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="message-canary">
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 灰度桶管理 -->
      <el-tab-pane :label="t('message.canaryBucketManage')" name="bucket">
        <!-- 筛选栏 -->
        <el-card shadow="never" class="filter-card">
          <el-form inline @submit.prevent="handleSearch">
            <el-form-item :label="t('message.canaryKey')">
              <el-input
                v-model="query.canaryKey"
                :placeholder="t('message.canaryKey')"
                clearable
                style="width: 180px"
              />
            </el-form-item>
            <el-form-item :label="t('message.status')">
              <el-select
                v-model="query.status"
                :placeholder="t('common.all')"
                clearable
                style="width: 140px"
              >
                <el-option
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
              <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 操作栏 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button
              v-permission="PC.MESSAGE_CANARY_UPDATE"
              type="primary"
              @click="handleCreate"
            >
              {{ t('message.create') }}
            </el-button>
            <el-button @click="fetchList">{{ t('common.refresh') }}</el-button>
          </div>
          <span class="total-text">{{ t('message.total', { n: total }) }}</span>
        </div>

        <!-- 列表 -->
        <el-table v-loading="loading" :data="list" style="width: 100%">
          <el-table-column :label="t('message.canaryKey')" prop="canaryKey" min-width="160" show-overflow-tooltip />
          <el-table-column :label="t('message.percentage')" width="100">
            <template #default="scope">
              <el-tag size="small" type="warning">
                {{ (scope.row as MsgCanaryVO).percentage }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('message.experimentTemplateCode')" prop="experimentTemplateCode" min-width="160" show-overflow-tooltip>
            <template #default="scope">
              {{ (scope.row as MsgCanaryVO).experimentTemplateCode || '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('message.experimentChannel')" width="120">
            <template #default="scope">
              <span v-if="(scope.row as MsgCanaryVO).experimentChannel">
                {{ getChannelLabel((scope.row as MsgCanaryVO).experimentChannel as string) }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('message.status')" width="90">
            <template #default="scope">
              <el-tag size="small" :type="getStatusTagType((scope.row as MsgCanaryVO).status)">
                {{ getStatusLabel((scope.row as MsgCanaryVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('message.description')" prop="description" min-width="160" show-overflow-tooltip>
            <template #default="scope">
              {{ (scope.row as MsgCanaryVO).description || '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.more')" fixed="right" width="220">
            <template #default="scope">
              <el-button
                v-permission="PC.MESSAGE_CANARY_UPDATE"
                type="primary"
                link
                size="small"
                @click="handleEdit(scope.row as MsgCanaryVO)"
              >
                {{ t('message.edit') }}
              </el-button>
              <el-button
                v-permission="PC.MESSAGE_CANARY_VIEW"
                type="warning"
                link
                size="small"
                @click="handleHitCheck(scope.row as MsgCanaryVO)"
              >
                {{ t('message.hitCheck') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-tab-pane>

      <!-- Tab 2: A/B 报表 -->
      <el-tab-pane :label="t('message.canaryReport')" name="report">
        <el-card shadow="never" class="filter-card">
          <el-form inline @submit.prevent="fetchReport">
            <el-form-item :label="t('message.canaryKey')">
              <el-input
                v-model="reportQuery.canaryKey"
                :placeholder="t('message.canaryKey')"
                clearable
                style="width: 200px"
              />
            </el-form-item>
            <el-form-item :label="t('message.createdAt')">
              <el-date-picker
                v-model="reportDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('message.startDate')"
                :end-placeholder="t('message.endDate')"
                style="width: 280px"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                v-permission="PC.MESSAGE_CANARY_REPORT"
                type="primary"
                :loading="reportLoading"
                @click="fetchReport"
              >
                {{ t('message.fetchReport') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 报表对比 -->
        <el-row v-if="report" :gutter="16" v-loading="reportLoading">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="report-card">
              <template #header>
                <span>{{ t('message.controlGroup') }}</span>
              </template>
              <el-descriptions :column="2" border>
                <el-descriptions-item :label="t('message.totalSend')">
                  {{ report.control.total }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.successCount')">
                  {{ report.control.success }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.failedCount')">
                  {{ report.control.failed }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.retryCount')">
                  {{ report.control.retry }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deadCount')">
                  {{ report.control.dead }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deliveredCount')">
                  {{ report.control.delivered }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.readCount')">
                  {{ report.control.read }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.clickedCount')">
                  {{ report.control.clicked }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.successRate')">
                  {{ formatRate(report.control.successRate) }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deliveryRate')">
                  {{ formatRate(report.control.deliveryRate) }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.readRate')" :span="2">
                  {{ formatRate(report.control.readRate) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="report-card">
              <template #header>
                <span>{{ t('message.treatmentGroup') }}</span>
              </template>
              <el-descriptions :column="2" border>
                <el-descriptions-item :label="t('message.totalSend')">
                  {{ report.treatment.total }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.successCount')">
                  {{ report.treatment.success }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.failedCount')">
                  {{ report.treatment.failed }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.retryCount')">
                  {{ report.treatment.retry }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deadCount')">
                  {{ report.treatment.dead }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deliveredCount')">
                  {{ report.treatment.delivered }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.readCount')">
                  {{ report.treatment.read }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.clickedCount')">
                  {{ report.treatment.clicked }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.successRate')">
                  {{ formatRate(report.treatment.successRate) }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.deliveryRate')">
                  {{ formatRate(report.treatment.deliveryRate) }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('message.readRate')" :span="2">
                  {{ formatRate(report.treatment.readRate) }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else :description="t('common.empty')" />
      </el-tab-pane>
    </el-tabs>

    <!-- Upsert 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="140px"
        label-position="right"
      >
        <el-form-item :label="t('message.canaryKey')" prop="canaryKey">
          <el-input
            v-model="form.canaryKey"
            :placeholder="t('message.canaryKey')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('message.percentage')" prop="percentage">
          <el-input-number v-model="form.percentage" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('message.bucketTotal')">
          <el-input-number v-model="form.bucketTotal" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('message.experimentTemplateCode')">
          <el-input v-model="form.experimentTemplateCode" :placeholder="t('message.experimentTemplateCode')" />
        </el-form-item>
        <el-form-item :label="t('message.experimentChannel')">
          <el-select v-model="form.experimentChannel" clearable placeholder="-" style="width: 100%">
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="t('message.description')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 命中检查弹窗 -->
    <el-dialog
      v-model="hitVisible"
      :title="t('message.hitCheck')"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form :model="hitForm" label-width="100px" label-position="right">
        <el-form-item :label="t('message.canaryKey')">
          <el-input v-model="hitForm.canaryKey" :placeholder="t('message.canaryKey')" />
        </el-form-item>
        <el-form-item :label="t('message.bucketValue')">
          <el-input v-model="hitForm.bucketValue" :placeholder="t('message.bucketValue')" />
        </el-form-item>
        <el-form-item v-if="hitResult !== null" :label="t('message.hitResult')">
          <el-tag :type="hitResult ? 'success' : 'info'">
            {{ hitResult ? t('message.hit') : t('message.miss') }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hitVisible = false">{{ t('common.close') }}</el-button>
        <el-button type="primary" @click="handleHitCheckSubmit">
          {{ t('message.hitCheck') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.message-canary {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
  }

  .total-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.report-card {
  margin-bottom: 16px;
}
</style>
