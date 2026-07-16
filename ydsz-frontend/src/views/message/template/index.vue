<!--
  @fileoverview 消息通知引擎 - 消息模板管理页面
  @description 消息模板的核心管控页面：
  - 顶部筛选栏：templateCode / channel / status
  - 模板列表：模板编码/通道/主题/分类/语言/状态/审核状态/版本/创建时间/操作
  - 操作列：编辑/删除/审核（通过/驳回）
  - 新建/编辑对话框：模板编码/通道/主题/内容/分类/语言/状态
  - 审核对话框：审核状态/审核备注
  - 分页
  @module views/message/template
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  getTemplatePage,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  auditTemplate,
} from '@/api/message'
import type {
  MsgTemplateVO,
  TemplateCreateDTO,
  TemplateQueryDTO,
  TemplateAuditDTO,
  MessageChannel,
  EnableStatus,
  TemplateAuditStatus,
} from '@/api/message/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 查询参数 */
const query = reactive<TemplateQueryDTO>({
  page: 1,
  size: 10,
  templateCode: undefined,
  channel: undefined,
  status: undefined,
})

/** 列表数据 */
const list = ref<MsgTemplateVO[]>([])
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

/** 审核状态选项 */
const auditStatusOptions: { label: string; value: TemplateAuditStatus }[] = [
  { label: t('message.auditStatusDraft'), value: 'DRAFT' },
  { label: t('message.auditStatusAuditing'), value: 'AUDITING' },
  { label: t('message.auditStatusApproved'), value: 'APPROVED' },
  { label: t('message.auditStatusRejected'), value: 'REJECTED' },
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

/** 通道 Tag 类型映射 */
const channelTagType: Record<MessageChannel, TagType> = {
  SMS: 'primary',
  EMAIL: 'success',
  PUSH: 'warning',
  INAPP: 'info',
  WEBHOOK: 'info',
  DINGTALK: 'primary',
  WECOM: 'success',
  FEISHU: 'warning',
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

/** 审核状态 Tag 类型映射 */
const auditTagType: Record<TemplateAuditStatus, TagType> = {
  DRAFT: 'info',
  AUDITING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
}

/** 审核状态文案映射 */
const auditLabelMap: Record<TemplateAuditStatus, string> = {
  DRAFT: t('message.auditStatusDraft'),
  AUDITING: t('message.auditStatusAuditing'),
  APPROVED: t('message.auditStatusApproved'),
  REJECTED: t('message.auditStatusRejected'),
}

/** 获取通道 Tag 类型 */
const getChannelTagType = (channel: string): TagType => {
  return channelTagType[channel as MessageChannel] ?? 'info'
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

/** 获取审核状态 Tag 类型 */
const getAuditTagType = (status: string): TagType => {
  return auditTagType[status as TemplateAuditStatus] ?? 'info'
}

/** 获取审核状态文案 */
const getAuditLabel = (status: string): string => {
  return auditLabelMap[status as TemplateAuditStatus] ?? status
}

// ==================== 新建/编辑弹窗 ====================

/** 弹窗显示 */
const dialogVisible = ref(false)
/** 弹窗标题 */
const dialogTitle = ref('')
/** 提交中 */
const submitting = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)

/** 表单数据 */
const form = reactive<TemplateCreateDTO>({
  id: undefined,
  templateCode: '',
  channel: 'SMS',
  locale: 'zh-CN',
  version: '1.0.0',
  category: '',
  sceneCode: '',
  subject: '',
  content: '',
  provider: '',
  providerKey: '',
  signName: '',
  description: '',
})

/** 表单校验规则 */
const formRules = {
  templateCode: [{ required: true, message: t('message.templateCode'), trigger: 'blur' }],
  channel: [{ required: true, message: t('message.channel'), trigger: 'change' }],
  content: [{ required: true, message: t('message.content'), trigger: 'blur' }],
}

/** 表单引用 */
const formRef = ref()

/** 重置表单 */
const resetForm = () => {
  form.id = undefined
  form.templateCode = ''
  form.channel = 'SMS'
  form.locale = 'zh-CN'
  form.version = '1.0.0'
  form.category = ''
  form.sceneCode = ''
  form.subject = ''
  form.content = ''
  form.provider = ''
  form.providerKey = ''
  form.signName = ''
  form.description = ''
}

/** 拉取列表 */
const fetchList = async () => {
  loading.value = true
  try {
    const resp = await getTemplatePage(query)
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
  query.templateCode = undefined
  query.channel = undefined
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
const handleEdit = (row: MsgTemplateVO) => {
  resetForm()
  isEdit.value = true
  dialogTitle.value = t('message.edit')
  form.id = row.id
  form.templateCode = row.templateCode
  form.channel = row.channel
  form.locale = row.locale
  form.version = row.version
  form.category = row.category
  form.sceneCode = row.sceneCode
  form.subject = row.subject
  form.content = row.content
  form.provider = row.provider
  form.providerKey = row.providerKey
  form.signName = row.signName
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
      if (isEdit.value && form.id) {
        await updateTemplate(form.id, form)
        ElMessage.success(t('message.saveSuccess'))
      } else {
        await createTemplate(form)
        ElMessage.success(t('message.createSuccess'))
      }
      dialogVisible.value = false
      fetchList()
    } catch {
      // 静默失败
    } finally {
      submitting.value = false
    }
  })
}

/** 删除 */
const handleDelete = (row: MsgTemplateVO) => {
  ElMessageBox.confirm(t('message.deleteConfirm'), t('common.confirm'), {
    type: 'warning',
  })
    .then(async () => {
      try {
        await deleteTemplate(row.id)
        ElMessage.success(t('message.deleteSuccess'))
        fetchList()
      } catch {
        // 静默失败
      }
    })
    .catch(() => {
      // 取消
    })
}

// ==================== 审核弹窗 ====================

/** 审核弹窗显示 */
const auditVisible = ref(false)
/** 审核表单 */
const auditForm = reactive<TemplateAuditDTO>({
  id: '',
  auditStatus: 'APPROVED',
  auditRemark: '',
})

/** 打开审核弹窗 */
const handleAudit = (row: MsgTemplateVO) => {
  auditForm.id = row.id
  auditForm.auditStatus = 'APPROVED'
  auditForm.auditRemark = ''
  auditVisible.value = true
}

/** 提交审核 */
const handleAuditSubmit = async () => {
  submitting.value = true
  try {
    await auditTemplate(auditForm.id, auditForm)
    ElMessage.success(t('message.auditSuccess'))
    auditVisible.value = false
    fetchList()
  } catch {
    // 静默失败
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="message-template-list">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item :label="t('message.templateCode')">
          <el-input
            v-model="query.templateCode"
            :placeholder="t('message.templateCode')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
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
          v-permission="PC.MESSAGE_TEMPLATE_CREATE"
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
    <el-table
      v-loading="loading"
      :data="list"
      style="width: 100%"
    >
      <el-table-column :label="t('message.templateCode')" prop="templateCode" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('message.channel')" width="100">
        <template #default="scope">
          <el-tag size="small" :type="getChannelTagType((scope.row as MsgTemplateVO).channel)">
            {{ getChannelLabel((scope.row as MsgTemplateVO).channel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('message.subject')" prop="subject" min-width="160" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MsgTemplateVO).subject || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.category')" prop="category" width="110" show-overflow-tooltip>
        <template #default="scope">
          {{ (scope.row as MsgTemplateVO).category || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('message.locale')" prop="locale" width="100" />
      <el-table-column :label="t('message.status')" width="90">
        <template #default="scope">
          <el-tag size="small" :type="getStatusTagType((scope.row as MsgTemplateVO).status)">
            {{ getStatusLabel((scope.row as MsgTemplateVO).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('message.auditStatus')" width="100">
        <template #default="scope">
          <el-tag
            v-if="(scope.row as MsgTemplateVO).auditStatus"
            size="small"
            :type="getAuditTagType((scope.row as MsgTemplateVO).auditStatus as string)"
          >
            {{ getAuditLabel((scope.row as MsgTemplateVO).auditStatus as string) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('message.version')" prop="version" width="100" />
      <el-table-column :label="t('message.createdAt')" prop="createdAt" width="170" />
      <el-table-column :label="t('common.more')" fixed="right" width="220">
        <template #default="scope">
          <el-button
            v-permission="PC.MESSAGE_TEMPLATE_UPDATE"
            type="primary"
            link
            size="small"
            @click="handleEdit(scope.row as MsgTemplateVO)"
          >
            {{ t('message.edit') }}
          </el-button>
          <el-button
            v-permission="PC.MESSAGE_TEMPLATE_APPROVE"
            type="warning"
            link
            size="small"
            @click="handleAudit(scope.row as MsgTemplateVO)"
          >
            {{ t('message.audit') }}
          </el-button>
          <el-button
            v-permission="PC.MESSAGE_TEMPLATE_DELETE"
            type="danger"
            link
            size="small"
            @click="handleDelete(scope.row as MsgTemplateVO)"
          >
            {{ t('message.delete') }}
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

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="780px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
        label-position="right"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('message.templateCode')" prop="templateCode">
              <el-input
                v-model="form.templateCode"
                :placeholder="t('message.templateCode')"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('message.channel')" prop="channel">
              <el-select v-model="form.channel" style="width: 100%" :disabled="isEdit">
                <el-option
                  v-for="opt in channelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('message.locale')">
              <el-input v-model="form.locale" placeholder="zh-CN" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('message.version')">
              <el-input v-model="form.version" placeholder="1.0.0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('message.category')">
              <el-input v-model="form.category" :placeholder="t('message.category')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('message.sceneCode')">
              <el-input v-model="form.sceneCode" :placeholder="t('message.sceneCode')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('message.subject')">
          <el-input v-model="form.subject" :placeholder="t('message.subject')" />
        </el-form-item>
        <el-form-item :label="t('message.content')" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            :placeholder="t('message.content')"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('message.provider')">
              <el-input v-model="form.provider" :placeholder="t('message.provider')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('message.providerKey')">
              <el-input v-model="form.providerKey" :placeholder="t('message.providerKey')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('message.signName')">
          <el-input v-model="form.signName" :placeholder="t('message.signName')" />
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

    <!-- 审核对话框 -->
    <el-dialog
      v-model="auditVisible"
      :title="t('message.audit')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="auditForm" label-width="100px" label-position="right">
        <el-form-item :label="t('message.auditStatus')">
          <el-select v-model="auditForm.auditStatus" style="width: 100%">
            <el-option
              v-for="opt in auditStatusOptions.filter((o) => o.value === 'APPROVED' || o.value === 'REJECTED')"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.auditRemark')">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            :placeholder="t('message.auditRemark')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAuditSubmit">
          {{ t('common.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.message-template-list {
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
</style>
