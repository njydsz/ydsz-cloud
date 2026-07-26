<!--
  @file 存储配额页面
  @description 展示当前用户的存储配额使用情况（容量 + 文件数），支持管理员设置配额。
  @module views/nextwiki/quota
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getQuotaInfo, setQuota } from '@/api/nextwiki/quota'
import type { StorageQuotaVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import QuotaProgressBar from '@/components/common/QuotaProgressBar.vue'

const { t } = useI18n()

/** 配额查询加载状态 */
const loading = ref(false)
/** 存储配额信息 */
const quota = ref<StorageQuotaVO | null>(null)

/** 配额设置弹窗 */
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  scopeType: 'user' as 'user' | 'tenant' | 'project',
  scopeId: '',
  quotaLimit: 0,
  fileCountLimit: 0,
})

/** 查询配额信息 */
async function fetchQuota() {
  loading.value = true
  try {
    const { data } = await getQuotaInfo()
    quota.value = data
  } finally {
    loading.value = false
  }
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}

/** 打开设置配额弹窗 */
function openSetDialog() {
  if (quota.value) {
    form.scopeType = quota.value.scopeType
    form.scopeId = quota.value.scopeId
    form.quotaLimit = quota.value.quotaLimit
    form.fileCountLimit = quota.value.fileCountLimit
  }
  dialogVisible.value = true
}

/** 提交设置配额 */
async function submitForm() {
  submitting.value = true
  try {
    await setQuota({
      scopeType: form.scopeType,
      scopeId: form.scopeId,
      quotaLimit: form.quotaLimit,
      fileCountLimit: form.fileCountLimit > 0 ? form.fileCountLimit : undefined,
    })
    ElMessage.success(t('nextwiki.quota.saveSuccess'))
    dialogVisible.value = false
    fetchQuota()
  } finally {
    submitting.value = false
  }
}

/** 维度选项 */
const scopeOptions = [
  { label: t('nextwiki.quota.scopeUser'), value: 'user' },
  { label: t('nextwiki.quota.scopeTenant'), value: 'tenant' },
  { label: t('nextwiki.quota.scopeProject'), value: 'project' },
]

onMounted(fetchQuota)
</script>

<template>
  <div v-loading="loading" class="quota-page">
    <el-card shadow="never">
      <template #header>
        <div class="quota-header">
          <span>{{ $t('nextwiki.quota.title') }}</span>
          <el-button v-permission="[PC.NEXTWIKI_QUOTA_SET]" type="primary" size="small" @click="openSetDialog">
            {{ $t('nextwiki.quota.setQuota') }}
          </el-button>
        </div>
      </template>

      <QuotaProgressBar :quota="quota" />

      <!-- 详细信息 -->
      <el-descriptions v-if="quota" :column="2" border class="quota-detail">
        <el-descriptions-item :label="$t('nextwiki.quota.total')">{{ formatSize(quota.quotaLimit) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('nextwiki.quota.used')">{{ formatSize(quota.quotaUsed) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('nextwiki.quota.remaining')">{{ formatSize(quota.quotaLimit - quota.quotaUsed) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('nextwiki.quota.usage')">
          {{ quota.quotaLimit > 0 ? Math.round((quota.quotaUsed / quota.quotaLimit) * 100) : 0 }}%
        </el-descriptions-item>
        <el-descriptions-item :label="$t('nextwiki.quota.fileCount')">
          {{ quota.fileCountUsed }} / {{ quota.fileCountLimit }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('nextwiki.quota.title')">
          {{ scopeOptions.find(o => o.value === quota.scopeType)?.label || quota.scopeType }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 设置配额弹窗 -->
    <el-dialog v-model="dialogVisible" :title="$t('nextwiki.quota.setQuota')" width="480px">
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item :label="$t('nextwiki.quota.title')">
          <el-select v-model="form.scopeType" style="width: 100%">
            <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('nextwiki.quota.quotaLimit')">
          <el-input-number v-model="form.quotaLimit" :min="0" :controls="false" style="width: 100%" />
          <span class="form-hint">Bytes ({{ formatSize(form.quotaLimit) }})</span>
        </el-form-item>
        <el-form-item :label="$t('nextwiki.quota.fileCountLimit')">
          <el-input-number v-model="form.fileCountLimit" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.quota-page {
  max-width: 800px;
}
.quota-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.quota-detail {
  margin-top: 24px;
}
.form-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
</style>
