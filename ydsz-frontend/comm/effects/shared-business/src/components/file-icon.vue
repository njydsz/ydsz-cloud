<!--
 * file-icon 通用组件
 *
 * @path comm\effects\shared-business\src\components\file-icon.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 文件类型图标组件 — 根据文件扩展名显示对应图标
 */
import { computed } from 'vue';

interface Props {
  fileName?: string;
  fileType?: string;
}

const props = defineProps<Props>();

const iconClass = computed(() => {
  const ext = (props.fileName?.split('.').pop() || props.fileType || '').toLowerCase();
  if (['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp'].includes(ext)) return 'icon-image';
  if (['pdf'].includes(ext)) return 'icon-pdf';
  if (['doc', 'docx'].includes(ext)) return 'icon-word';
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'icon-excel';
  if (['ppt', 'pptx'].includes(ext)) return 'icon-ppt';
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'icon-archive';
  if (['mp4', 'avi', 'mov', 'mkv'].includes(ext)) return 'icon-video';
  if (['mp3', 'wav', 'flac'].includes(ext)) return 'icon-audio';
  if (['js', 'ts', 'java', 'py', 'go', 'json', 'xml', 'yaml', 'yml'].includes(ext)) return 'icon-code';
  return 'icon-file';
});
</script>

<template>
  <span class="file-icon" :class="iconClass" />
</template>

<style scoped>
.file-icon {
  display: inline-block;
  width: 20px;
  height: 20px;
  background-size: contain;
  background-repeat: no-repeat;
  vertical-align: middle;
}
</style>
