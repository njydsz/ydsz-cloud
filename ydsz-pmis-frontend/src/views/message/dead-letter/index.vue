<!--
  @fileoverview 消息通知引擎 - 死信管理页面
  @description 死信消息（status=DEAD）的核心管控页面：
  - 顶部筛选栏：channel / bizType / receiver
  - 死信列表：消息ID/通道/业务类型/接收人/模板编码/错误信息/重试次数/创建时间/操作
  - 操作列：重发（确认对话框 → POST /dead-letter/{logId}/resend）
  - 分页
  @module views/message/dead-letter
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getDeadLetterPage, resendDeadLetter } from '@/api/message'
import type {
  MessageLogVO,
  MessageLogPageQuery,
  MessageChannel,
} from '@/api/message/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 查询参数 */
const query = reactive<MessageLogPageQuery>({
  page: 1,
  size: 10,
  channel: undefined,
  bizType: undefined,
  receiver: undefined,
})

/** 列表数据 */
const list = ref<MessageLogVO[]>([])
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
  { label: t('message.channelInApp'), value: 'IN_APP' },
  { label: t('message.channelWebhook'), value: 'WEBHOOK' },
  { label: t('message.channelDingtalk'), value: 'DINGTALK' },
  { label: t('message.channelWecom'), value: 'WECOM' },
  { label: t('message.channelFeishu'), value: 'FEISHU' },
]

/** 通道文案映射 */
const channelLabelMap: Record<MessageChannel, string> = {
  SMS: t('message.channelSms'),
  EMAIL: t('message.channelEmail'),
  PUSH: t('message.channelPush'),
  IN_APP: t('message.channelInApp'),
  WEBHOOK: t('message.channelWebhook'),
  DINGTALK: t('message.channelDingtalk'),
  WECOM: t('message.channelWecom'),
  FEISHU: t('message.channelFeishu'),
}

/** 通道 Tag 类型映射 */
const channelTagType: Record<MessageChannel, TagType> = {
  SMS: 'primary',
  EMAIL: 'success',
  PUSH: 'warning',
  IN_APP: 'info',
  WEBHOOK: 'info',
  DINGTALK: 'primary',
  WECOM: 'success',
  FEISHU: 'warning',
}

/** 获取通道 Tag 类型 */
const getChannelTagType = (channel: string): TagType => {
  return channelTagType[channel as MessageChannel] ?? 'info'
}

/** 获取通道文案 */
const getChannelLabel = (channel: string): string => {
  return channelLabelMap[channel as MessageChannel] ?? channel
}

/** 拉取列表 */
const fetchList = async () => {
  loading.value = true
  try {
    const resp = await getDeadLetterPage(query)
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
  query.channel = undefined
  query.bizType = undefined
  query.receiver = undefined
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

/** 重发死信 */
const handleResend = (row: MessageLogVO) => {
  ElMessageBox.confirm(t('message.resendConfirm'), t('common.confirm'), {
    type: 'warning',
  })
    .then(async () => {
      try {
        await resendDeadLetter(row.id)
        ElMessage.success(t('message.resendSuccess'))
        fetchList()
      } catch {
        // 静默失败
      }
    })
    .catch(() => {
      // 取消
    })
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="dead-letter-list">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item :label="t('message.channel')">
          <el-select
            v-model="query.channel"
            :placeholder="t('common.all')"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.bizType')">
          <el-input
            v-model="query.bizType"
            :placeholder="t('message.bizType')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('message.receiver')">
          <el-input
            v-model="query.receiver"
            :placeholder="t('message.receiver')"
            clearable
            style="width: 160px"
          />
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
        <el-button @click="fetchList">{{ t('common.refresh') }}</el-button>
      </div>
      <span class="total-text">{{ t('message.total', { n: total }) }}</span>
    </div>

    <!-- 列表 -->
    <el-table
      v-loading="loading"
      :data="list"
      style="width: 100%"
    >
      <el-table-column :label="t('message.msgId')" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MessageLogVO).msgId || (scope.row as MessageLogVO).id }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.channel')" width="100">
        <template #default="scope">
          <el-tag size="small" :type="getChannelTagType((scope.row as MessageLogVO).channel)">
            {{ getChannelLabel((scope.row as MessageLogVO).channel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('message.bizType')" prop="bizType" min-width="120" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MessageLogVO).bizType || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.receiver')" prop="receiver" min-width="140" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MessageLogVO).receiver || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.templateCode')" prop="templateCode" min-width="140" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MessageLogVO).templateCode || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.errorMessage')" prop="errorMessage" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          <span class="error-text">{{ (scope.row as MessageLogVO).errorMessage || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('message.retryCount')" prop="retryCount" width="90" />
      <el-table-column :label="t('message.createdAt')" prop="createdAt" width="170" />
      <el-table-column :label="t('common.more')" fixed="right" width="100">
        <template #default="scope">
          <el-button
            v-permission="PC.MESSAGE_DEAD_LETTER_RESEND"
            type="primary"
            link
            size="small"
            @click="handleResend(scope.row as MessageLogVO)"
          >
            {{ t('message.resend') }}
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
  </div>
</template>

<style lang="scss" scoped>
.dead-letter-list {
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

.error-text {
  color: var(--el-color-danger);
}
</style>
