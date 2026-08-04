<!--
 * approval-timeline 通用组件 — 审批历史时间轴
 *
 * @path comm\effects\shared-business\src\components\approval-timeline.vue
 * @author ydsz-team
 * @since 1.1.0
-->
<script lang="ts" setup>
/**
 * 审批历史时间轴 — 展示审批流转记录
 */
import { ElTag, ElTimeline, ElTimelineItem } from 'element-plus';

/** 审批记录 */
export interface ApprovalRecord {
  id: string;
  /** 审批人 */
  operator: string;
  /** 审批动作：SUBMIT/APPROVE/REJECT/RETURN/TRANSFER */
  action: string;
  /** 审批意见 */
  comment?: string;
  /** 审批时间 */
  time: string;
}

interface Props {
  /** 审批记录 */
  records: ApprovalRecord[];
}

defineProps<Props>();

/** 动作 → 标签样式 */
function actionMeta(action: string): {
  type: 'success' | 'danger' | 'warning' | 'info' | 'primary';
  text: string;
} {
  const map: Record<string, { type: 'success' | 'danger' | 'warning' | 'info' | 'primary'; text: string }> = {
    APPROVE: { type: 'success', text: '通过' },
    REJECT: { type: 'danger', text: '驳回' },
    SUBMIT: { type: 'primary', text: '提交' },
    RETURN: { type: 'warning', text: '退回' },
    TRANSFER: { type: 'info', text: '转办' },
  };
  return map[action] || { type: 'info', text: action };
}
</script>

<template>
  <el-timeline v-if="records.length > 0">
    <el-timeline-item
      v-for="record in records"
      :key="record.id"
      :timestamp="record.time"
      placement="top"
    >
      <div class="approval-timeline__item">
        <div class="approval-timeline__head">
          <span class="approval-timeline__operator">{{ record.operator }}</span>
          <el-tag :type="actionMeta(record.action).type" size="small">
            {{ actionMeta(record.action).text }}
          </el-tag>
        </div>
        <p v-if="record.comment" class="approval-timeline__comment">
          {{ record.comment }}
        </p>
      </div>
    </el-timeline-item>
  </el-timeline>
  <p v-else class="approval-timeline__empty">暂无审批记录</p>
</template>

<style scoped>
.approval-timeline__item {
  padding-bottom: 4px;
}
.approval-timeline__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.approval-timeline__operator {
  font-weight: 600;
  font-size: 14px;
}
.approval-timeline__comment {
  margin: 0;
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
}
.approval-timeline__empty {
  text-align: center;
  color: #909399;
  padding: 16px 0;
  font-size: 13px;
}
</style>
