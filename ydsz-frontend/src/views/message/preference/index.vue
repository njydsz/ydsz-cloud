<!--
  @fileoverview 消息通知引擎 - 用户偏好管理页面
  @description 管理用户消息偏好的核心页面：
  - 顶部查询栏：输入用户 ID 查询其所有偏好配置
  - 偏好列表：通道/业务类型/通道开关/免打扰/免打扰时段/频率上限/聚合/语言/操作
  - 操作列：编辑（弹窗 upsert）/删除（确认对话框）
  - 新增偏好按钮
  @module views/message/preference
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getPreferences, upsertPreference, deletePreference } from '@/api/message'
import type { MsgPreferenceVO, PreferenceUpsertDTO, MessageChannel, DigestFrequency } from '@/api/message/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

/** 查询参数 */
const queryUserId = ref('')

/** 列表数据 */
const list = ref<MsgPreferenceVO[]>([])
/** 加载中 */
const loading = ref(false)

/** Element Plus el-tag type 联合类型 */
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 通道选项 */
const channelOptions: { label: string; value: MessageChannel }[] = [
  { label: 'SMS', value: 'SMS' },
  { label: 'EMAIL', value: 'EMAIL' },
  { label: 'PUSH', value: 'PUSH' },
  { label: 'INAPP', value: 'INAPP' },
  { label: 'WEBHOOK', value: 'WEBHOOK' },
  { label: 'DINGTALK', value: 'DINGTALK' },
  { label: 'WECOM', value: 'WECOM' },
  { label: 'FEISHU', value: 'FEISHU' },
]

/** 聚合频率选项 */
const digestFrequencyOptions: { label: string; value: DigestFrequency }[] = [
  { label: 'HOURLY', value: 'HOURLY' },
  { label: 'DAILY', value: 'DAILY' },
  { label: 'WEEKLY', value: 'WEEKLY' },
]

/** 语言选项 */
const localeOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
]

/** 弹窗可见 */
const dialogVisible = ref(false)
/** 弹窗标题 */
const dialogTitle = ref('')
/** 提交中 */
const submitting = ref(false)

/** 表单数据 */
const form = reactive<PreferenceUpsertDTO & { id?: string }>({
  userId: '',
  channel: 'SMS',
  bizType: '__DEFAULT__',
  enabled: 1,
  dndEnabled: 0,
  dndStart: '22:00',
  dndEnd: '08:00',
  dailyLimit: 0,
  hourlyLimit: 0,
  digestEnabled: 0,
  digestFrequency: 'DAILY',
  locale: 'zh-CN',
  extra: '',
})

/** 重置表单 */
function resetForm() {
  form.id = undefined
  form.userId = queryUserId.value || ''
  form.channel = 'SMS'
  form.bizType = '__DEFAULT__'
  form.enabled = 1
  form.dndEnabled = 0
  form.dndStart = '22:00'
  form.dndEnd = '08:00'
  form.dailyLimit = 0
  form.hourlyLimit = 0
  form.digestEnabled = 0
  form.digestFrequency = 'DAILY'
  form.locale = 'zh-CN'
  form.extra = ''
}

/** 查询偏好列表 */
async function handleSearch() {
  if (!queryUserId.value.trim()) {
    ElMessage.warning(t('message.pleaseFillField', { field: t('message.preferenceUserId') }))
    return
  }
  loading.value = true
  try {
    const { data } = await getPreferences(queryUserId.value.trim())
    list.value = data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 打开新增弹窗 */
function handleAdd() {
  resetForm()
  dialogTitle.value = t('message.preferenceAddTitle')
  dialogVisible.value = true
}

/** 打开编辑弹窗 */
function handleEdit(row: MsgPreferenceVO) {
  form.id = row.id
  form.userId = row.userId
  form.channel = row.channel
  form.bizType = row.bizType
  form.enabled = row.enabled
  form.dndEnabled = row.dndEnabled
  form.dndStart = row.dndStart || '22:00'
  form.dndEnd = row.dndEnd || '08:00'
  form.dailyLimit = row.dailyLimit ?? 0
  form.hourlyLimit = row.hourlyLimit ?? 0
  form.digestEnabled = row.digestEnabled
  form.digestFrequency = row.digestFrequency || 'DAILY'
  form.locale = row.locale || 'zh-CN'
  form.extra = row.extra || ''
  dialogTitle.value = t('message.preferenceEditTitle')
  dialogVisible.value = true
}

/** 提交表单 */
async function handleSubmit() {
  if (!form.userId.trim()) {
    ElMessage.warning(t('message.pleaseFillField', { field: t('message.preferenceUserId') }))
    return
  }
  submitting.value = true
  try {
    const { id, ...dto } = form
    await upsertPreference(dto)
    ElMessage.success(t('common.message.saveSuccess'))
    dialogVisible.value = false
    handleSearch()
  } catch (e) {
    // 错误由全局拦截器处理
  } finally {
    submitting.value = false
  }
}

/** 删除偏好 */
async function handleDelete(row: MsgPreferenceVO) {
  await ElMessageBox.confirm(t('message.preferenceConfirmDelete'), t('common.confirm.title'), {
    type: 'warning',
  })
  await deletePreference(row.id)
  ElMessage.success(t('common.message.deleteSuccess'))
  handleSearch()
}

/** 通道开关 tag 类型 */
function enabledTagType(val: number): TagType {
  return val === 1 ? 'success' : 'info'
}

/** 免打扰 tag 类型 */
function dndTagType(val: number): TagType {
  return val === 1 ? 'warning' : 'info'
}

/** 聚合 tag 类型 */
function digestTagType(val: number): TagType {
  return val === 1 ? 'primary' : 'info'
}

onMounted(() => {
  // 默认不加载，等用户输入 userId
})
</script>

<template>
  <div class="preference-page">
    <!-- 查询栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item :label="t('message.preferenceUserId')">
          <el-input
            v-model="queryUserId"
            :placeholder="t('message.preferenceUserIdPlaceholder')"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">
            {{ t('common.buttons.search') }}
          </el-button>
          <el-button
            v-permission="PC.MESSAGE_PREFERENCE_UPDATE"
            type="success"
            @click="handleAdd"
          >
            {{ t('common.buttons.add') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 偏好列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column :label="t('message.preferenceChannel')" prop="channel" width="100" />
        <el-table-column :label="t('message.preferenceBizType')" prop="bizType" min-width="120" show-overflow-tooltip />
        <el-table-column :label="t('message.preferenceEnabled')" width="100" align="center">
          <template #default="scope">
            <el-tag :type="enabledTagType((scope.row as MsgPreferenceVO).enabled)" size="small">
              {{ (scope.row as MsgPreferenceVO).enabled === 1 ? t('message.preferenceEnabledOn') : t('message.preferenceEnabledOff') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceDndEnabled')" width="90" align="center">
          <template #default="scope">
            <el-tag :type="dndTagType((scope.row as MsgPreferenceVO).dndEnabled)" size="small">
              {{ (scope.row as MsgPreferenceVO).dndEnabled === 1 ? t('message.preferenceDndOn') : t('message.preferenceDndOff') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceDndStart')" width="90" align="center">
          <template #default="scope">
            {{ (scope.row as MsgPreferenceVO).dndEnabled === 1 ? `${(scope.row as MsgPreferenceVO).dndStart || '-'} ~ ${(scope.row as MsgPreferenceVO).dndEnd || '-'}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceDailyLimit')" prop="dailyLimit" width="90" align="center">
          <template #default="scope">{{ (scope.row as MsgPreferenceVO).dailyLimit || 0 }}</template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceHourlyLimit')" prop="hourlyLimit" width="100" align="center">
          <template #default="scope">{{ (scope.row as MsgPreferenceVO).hourlyLimit || 0 }}</template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceDigestEnabled')" width="90" align="center">
          <template #default="scope">
            <el-tag :type="digestTagType((scope.row as MsgPreferenceVO).digestEnabled)" size="small">
              {{ (scope.row as MsgPreferenceVO).digestEnabled === 1 ? t('message.preferenceDigestOn') : t('message.preferenceDigestOff') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('message.preferenceLocale')" prop="locale" width="80" align="center" />
        <el-table-column :label="t('common.buttons.action')" width="140" fixed="right">
          <template #default="scope">
            <el-button
              v-permission="PC.MESSAGE_PREFERENCE_UPDATE"
              link
              type="primary"
              size="small"
              @click="handleEdit(scope.row as MsgPreferenceVO)"
            >
              {{ t('common.buttons.edit') }}
            </el-button>
            <el-button
              v-permission="PC.MESSAGE_PREFERENCE_DELETE"
              link
              type="danger"
              size="small"
              @click="handleDelete(scope.row as MsgPreferenceVO)"
            >
              {{ t('common.buttons.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="!loading && list.length === 0" :description="t('common.empty')" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('message.preferenceUserId')" required>
          <el-input v-model="form.userId" placeholder="user-id" />
        </el-form-item>
        <el-form-item :label="t('message.preferenceChannel')" required>
          <el-select v-model="form.channel" style="width: 100%">
            <el-option
              v-for="opt in channelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.preferenceBizType')">
          <el-input v-model="form.bizType" placeholder="__DEFAULT__" />
        </el-form-item>
        <el-form-item :label="t('message.preferenceEnabled')">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('message.preferenceDndEnabled')">
          <el-switch v-model="form.dndEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="form.dndEnabled === 1" :label="t('message.preferenceDndStart')">
          <el-time-picker
            v-model="form.dndStart"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="22:00"
            style="width: 120px"
          />
          <span style="margin: 0 8px">~</span>
          <el-time-picker
            v-model="form.dndEnd"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="08:00"
            style="width: 120px"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            {{ t('message.preferenceDndTimeHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('message.preferenceDailyLimit')">
          <el-input-number v-model="form.dailyLimit" :min="0" :max="10000" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">0 = 不限</span>
        </el-form-item>
        <el-form-item :label="t('message.preferenceHourlyLimit')">
          <el-input-number v-model="form.hourlyLimit" :min="0" :max="1000" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">0 = 不限</span>
        </el-form-item>
        <el-form-item :label="t('message.preferenceDigestEnabled')">
          <el-switch v-model="form.digestEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="form.digestEnabled === 1" :label="t('message.preferenceDigestFrequency')">
          <el-select v-model="form.digestFrequency" style="width: 200px">
            <el-option
              v-for="opt in digestFrequencyOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('message.preferenceLocale')">
          <el-select v-model="form.locale" style="width: 200px">
            <el-option
              v-for="opt in localeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.buttons.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.buttons.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.preference-page {
  padding: 16px;
}
.filter-card {
  margin-bottom: 16px;
}
.table-card {
  min-height: 400px;
}
</style>
