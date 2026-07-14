<!--
  @fileoverview 分享链接生成弹窗
  @description 提供文件分享配置表单（分享类型、有效期、访问次数、密码），生成分享链接后可一键复制。
  - Props: modelValue（v-model 弹窗显隐）、fileNodeId（文件 ID）、fileName（文件名）
  - Emits: update:modelValue、created（分享创建成功回调）
  @module components/common/ShareLinkDialog
-->
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { createShare } from '@/api/nextwiki/share'
import type { ShareLinkVO } from '@/api/nextwiki/types'

const { t } = useI18n()

const props = defineProps<{
  /** 弹窗可见性（v-model） */
  modelValue: boolean
  /** 文件节点 ID */
  fileNodeId: string
  /** 文件名（仅展示用） */
  fileName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'created', share: ShareLinkVO): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
/** 已生成的分享链接 */
const shareLink = ref<ShareLinkVO | null>(null)

const form = reactive({
  shareType: 'view' as 'view' | 'download' | 'edit',
  expireDays: 7,
  maxAccessCount: 0,
  password: '',
})

/** 有效期选项 */
const expireOptions = [
  { label: t('nextwiki.shares.forever'), value: 0 },
  { label: `1 ${t('nextwiki.shares.days')}`, value: 1 },
  { label: `7 ${t('nextwiki.shares.days')}`, value: 7 },
  { label: `30 ${t('nextwiki.shares.days')}`, value: 30 },
]

/** 分享类型选项 */
const shareTypeOptions = computed(() => [
  { label: t('nextwiki.shares.view'), value: 'view' },
  { label: t('nextwiki.shares.download'), value: 'download' },
  { label: t('nextwiki.shares.edit'), value: 'edit' },
])

/** 完整分享链接 */
const fullLink = computed(() => {
  if (!shareLink.value) return ''
  const base = window.location.origin
  return `${base}/s/${shareLink.value.shareCode}`
})

/** 弹窗打开时重置表单 */
watch(visible, (val) => {
  if (val) {
    Object.assign(form, { shareType: 'view', expireDays: 7, maxAccessCount: 0, password: '' })
    shareLink.value = null
  }
})

/** 提交创建分享 */
async function handleSubmit() {
  submitting.value = true
  try {
    const expireTime = form.expireDays > 0
      ? new Date(Date.now() + form.expireDays * 86400000).toISOString()
      : undefined
    const { data } = await createShare({
      fileNodeId: props.fileNodeId,
      shareType: form.shareType,
      expireTime,
      maxAccessCount: form.maxAccessCount > 0 ? form.maxAccessCount : undefined,
      password: form.password || undefined,
    })
    shareLink.value = data
    emit('created', data)
    ElMessage.success(t('nextwiki.shares.create'))
  } finally {
    submitting.value = false
  }
}

/** 复制链接到剪贴板 */
async function copyLink() {
  if (!fullLink.value) return
  try {
    await navigator.clipboard.writeText(fullLink.value)
    ElMessage.success(t('nextwiki.shares.linkCopied'))
  } catch {
    ElMessage.warning(t('nextwiki.shares.copyLink'))
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="$t('nextwiki.shares.create')" width="480px">
    <!-- 分享表单 -->
    <el-form v-if="!shareLink" ref="formRef" :model="form" label-width="100px">
      <el-form-item v-if="fileName" :label="$t('nextwiki.files.name')">
        <span>{{ fileName }}</span>
      </el-form-item>
      <el-form-item :label="$t('nextwiki.shares.shareType')">
        <el-select v-model="form.shareType" style="width: 100%">
          <el-option v-for="opt in shareTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('nextwiki.shares.expireTime')">
        <el-select v-model="form.expireDays" style="width: 100%">
          <el-option v-for="opt in expireOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('nextwiki.shares.accessCount')">
        <el-input-number v-model="form.maxAccessCount" :min="0" :controls="false" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('nextwiki.shares.password')">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
    </el-form>

    <!-- 分享结果 -->
    <div v-else class="share-result">
      <el-alert type="success" :closable="false" show-icon :title="$t('nextwiki.shares.create')" />
      <el-input :model-value="fullLink" readonly class="share-result__link">
        <template #append>
          <el-button @click="copyLink">{{ $t('nextwiki.shares.copyLink') }}</el-button>
        </template>
      </el-input>
      <div v-if="shareLink.hasPassword" class="share-result__info">
        {{ $t('nextwiki.shares.extractCode') }}: {{ form.password || '******' }}
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $t('common.close') }}</el-button>
      <el-button v-if="!shareLink" type="primary" :loading="submitting" @click="handleSubmit">
        {{ $t('nextwiki.shares.create') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.share-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.share-result__link {
  margin-top: 8px;
}
.share-result__info {
  color: #909399;
  font-size: 13px;
}
</style>
