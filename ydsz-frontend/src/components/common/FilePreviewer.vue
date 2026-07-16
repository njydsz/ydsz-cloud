<!--
  @fileoverview 文件预览器组件
  @description 支持图片 / PDF / 文本 / 视频预览，其他类型显示下载按钮。
  - Props: modelValue（v-model 弹窗显隐）、preview（FilePreviewVO 预览信息）、fileName（文件名）
  - Emits: update:modelValue、download
  @module components/common/FilePreviewer
-->
<script setup lang="ts">
import { computed } from 'vue'
import type { FilePreviewVO } from '@/api/nextwiki/types'

const props = defineProps<{
  /** 弹窗可见性（v-model） */
  modelValue: boolean
  /** 预览信息 */
  preview: FilePreviewVO | null
  /** 文件名（展示用） */
  fileName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'download'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/** 是否正在生成预览 */
const generating = computed(() => props.preview && !props.preview.ready)

/** 是否支持预览 */
const canPreview = computed(() => {
  if (!props.preview) return false
  return props.preview.previewType !== 'unsupported'
})
</script>

<template>
  <el-dialog v-model="visible" :title="fileName || $t('nextwiki.preview.title')" width="800px" top="5vh">
    <div class="file-previewer">
      <!-- 正在生成预览 -->
      <div v-if="generating" class="file-previewer__loading">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>{{ $t('nextwiki.preview.generating') }}</p>
      </div>

      <!-- 图片预览 -->
      <div v-else-if="preview?.previewType === 'image' && preview.previewUrl" class="file-previewer__image">
        <el-image :src="preview.previewUrl" fit="contain" :preview-src-list="[preview.previewUrl]" />
      </div>

      <!-- PDF 预览 -->
      <div v-else-if="preview?.previewType === 'pdf' && preview.previewUrl" class="file-previewer__pdf">
        <iframe :src="preview.previewUrl" width="100%" height="600px" frameborder="0" />
      </div>

      <!-- 文本预览 -->
      <div v-else-if="preview?.previewType === 'text' && preview.content" class="file-previewer__text">
        <pre>{{ preview.content }}</pre>
      </div>

      <!-- 视频预览 -->
      <div v-else-if="preview?.previewType === 'video' && preview.previewUrl" class="file-previewer__video">
        <video controls width="100%">
          <source :src="preview.previewUrl" :type="preview.mimeType || 'video/mp4'" />
        </video>
      </div>

      <!-- 不支持预览 -->
      <div v-else class="file-previewer__unsupported">
        <el-icon :size="48"><Document /></el-icon>
        <p>{{ $t('nextwiki.preview.notSupported') }}</p>
        <el-button type="primary" @click="emit('download')">
          {{ $t('nextwiki.preview.downloadOriginal') }}
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button v-if="canPreview" type="primary" @click="emit('download')">
        {{ $t('nextwiki.preview.downloadOriginal') }}
      </el-button>
      <el-button @click="visible = false">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.file-previewer {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.file-previewer__loading,
.file-previewer__unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: #909399;
}
.file-previewer__image {
  width: 100%;
  text-align: center;
}
.file-previewer__image :deep(.el-image) {
  max-height: 600px;
}
.file-previewer__pdf,
.file-previewer__video {
  width: 100%;
}
.file-previewer__text {
  width: 100%;
  max-height: 600px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 16px;
}
.file-previewer__text pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
