<!--
 * status-badge 通用组件
 *
 * @path comm\effects\shared-business\src\components\status-badge.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 状态徽章组件 — 统一的状态展示组件
 *
 * 支持：项目阶段、任务状态、审批状态等
 */
import { computed } from 'vue';

interface Props {
  status: string;
  statusMap?: Record<string, { color: string; label: string }>;
}

const props = withDefaults(defineProps<Props>(), {
  statusMap: () => ({
    DRAFT: { color: 'info', label: '草稿' },
    PENDING: { color: 'warning', label: '待处理' },
    RUNNING: { color: 'primary', label: '进行中' },
    SUCCESS: { color: 'success', label: '成功' },
    FAILED: { color: 'danger', label: '失败' },
    APPROVED: { color: 'success', label: '已通过' },
    REJECTED: { color: 'danger', label: '已驳回' },
    ENABLED: { color: 'success', label: '启用' },
    DISABLED: { color: 'info', label: '禁用' },
  }),
});

const config = computed(() => props.statusMap[props.status] || { color: 'info', label: props.status });
</script>

<template>
  <el-tag :type="config.color" size="small" effect="light">
    {{ config.label }}
  </el-tag>
</template>
