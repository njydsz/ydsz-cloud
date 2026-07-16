<!--
  @fileoverview 文件上传组件
  @description 支持拖拽上传、多文件、进度条展示，基于 el-upload drag 模式。
  - Props: parentId（父目录 ID）、visible（v-model 控制弹窗显隐）、accept（接受的文件类型）
  - Emits: success（上传成功回调）、update:visible
  @module components/common/FileUploader
-->
<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { uploadFile } from '@/api/nextwiki/file'

const { t } = useI18n()

const props = defineProps<{
  /** 父目录 ID（根目录传 '0'） */
  parentId: string
  /** 弹窗可见性（v-model） */
  modelValue: boolean
  /** 接受的文件类型，如 '.pdf,.docx' */
  accept?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/** 已上传文件数 */
const uploadedCount = ref(0)
/** 总文件数 */
const totalCount = ref(0)

/** 自定义上传逻辑 */
async function handleUpload(options: { file: File }) {
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('parentId', props.parentId)
  try {
    await uploadFile(formData)
    uploadedCount.value++
    ElMessage.success(t('nextwiki.files.uploadSuccess'))
    if (uploadedCount.value >= totalCount.value) {
      emit('success')
      visible.value = false
    }
  } catch {
    // 拦截器已弹错
  }
}

/** 文件选择变化时重置计数 */
function handleChange(_file: unknown, fileList: unknown[]) {
  totalCount.value = fileList.length
}

/** 关闭弹窗时重置 */
function handleClose() {
  uploadedCount.value = 0
  totalCount.value = 0
}
</script>

<template>
  <el-dialog v-model="visible" :title="$t('nextwiki.files.upload')" width="520px" @close="handleClose">
    <el-upload
      drag
      multiple
      :accept="accept"
      :http-request="handleUpload"
      :on-change="handleChange"
      :show-file-list="true"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        {{ $t('nextwiki.files.upload') }}
      </div>
    </el-upload>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>
