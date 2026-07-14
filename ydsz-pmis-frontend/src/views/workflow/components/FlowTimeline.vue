<!--
  @fileoverview 审批轨迹时间线组件
  @description
    消费后端 getTimeline 接口，按时间轴展示流程完整生命周期事件。
    P0-08：对标钉钉 / 飞书 / Activiti History Service。
    支持事件类型：START / TASK_COMPLETED / URGE / TRANSFER / DELEGATE / COUNTERSIGN /
    TIMEOUT / COMPLETE / REJECT / SUSPEND / ACTIVATE / RECALL / JUMP / CC。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FlowTimeline
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 审批轨迹时间线组件
 * @description 消费后端 getTimeline 接口，按时间轴展示流程完整生命周期事件。
 * P0-8: 审批轨迹时间线 UI（对标钉钉/飞书/Activiti History Service）。
 * 支持事件类型：START/TASK_COMPLETED/URGE/TRANSFER/DELEGATE/COUNTERSIGN/TIMEOUT/COMPLETE/REJECT/SUSPEND/ACTIVATE/RECALL/JUMP/CC
 */
import { computed, ref, watch, nextTick } from 'vue'
import dayjs from 'dayjs'
import type { FlowTimelineDTO, FlowTimelineEventDTO } from '@/api/workflow/types'

const props = defineProps<{
  timeline: FlowTimelineDTO
  /** P0-4: 高亮指定节点编码对应的事件（点击流程图节点跳转时设置） */
  highlightNodeCode?: string | null
}>()

const events = computed<FlowTimelineEventDTO[]>(() => props.timeline.events || [])

// P0-4: 高亮控制 — 当 highlightNodeCode 变化时找到第一个匹配事件并滚动+高亮
const highlightActive = ref(false)
const highlightIndex = ref(-1)
let highlightTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.highlightNodeCode,
  async (code) => {
    if (!code) {
      highlightIndex.value = -1
      return
    }
    const idx = events.value.findIndex((e) => e.nodeCode === code)
    if (idx < 0) {
      highlightIndex.value = -1
      return
    }
    highlightIndex.value = idx
    highlightActive.value = true
    await nextTick()
    // 滚动到对应事件
    const el = document.querySelector(`[data-tl-idx="${idx}"]`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
    // 3 秒后取消高亮
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => {
      highlightActive.value = false
    }, 3000)
  },
)

/** 事件类型 → 颜色 + 图标 + 文案 */
const eventConfig: Record<
  string,
  { color: string; icon: string; label: string }
> = {
  START: { color: '#52c41a', icon: 'CirclePlus', label: '流程发起' },
  TASK_CREATED: { color: '#1890ff', icon: 'Bell', label: '创建待办' },
  TASK_COMPLETED: { color: '#1890ff', icon: 'Select', label: '完成审批' },
  URGE: { color: '#faad14', icon: 'BellFilled', label: '催办' },
  TRANSFER: { color: '#722ed1', icon: 'Share', label: '转办' },
  DELEGATE: { color: '#722ed1', icon: 'Promotion', label: '委派' },
  COUNTERSIGN: { color: '#13c2c2', icon: 'Connection', label: '加签' },
  TIMEOUT: { color: '#fa541c', icon: 'Timer', label: '超时' },
  TERMINATE: { color: '#f5222d', icon: 'CircleClose', label: '终止' },
  COMPLETE: { color: '#52c41a', icon: 'CircleCheck', label: '流程完成' },
  REJECT: { color: '#f5222d', icon: 'CircleClose', label: '驳回' },
  SUSPEND: { color: '#faad14', icon: 'VideoPause', label: '挂起' },
  ACTIVATE: { color: '#1890ff', icon: 'VideoPlay', label: '激活' },
  RECALL: { color: '#fa8c16', icon: 'RefreshLeft', label: '撤回' },
  JUMP: { color: '#722ed1', icon: 'Position', label: '自由跳转' },
  CC: { color: '#13c2c2', icon: 'UserFilled', label: '抄送' },
}

function config(type: string) {
  return eventConfig[type] || { color: '#999', icon: 'MoreFilled', label: type }
}

function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分${s % 60}秒`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}时${m % 60}分`
  return `${Math.floor(h / 24)}天${h % 24}时`
}
</script>

<template>
  <div class="flow-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="(e, idx) in events"
        :key="`${e.eventType}-${idx}-${e.createdAt}`"
        :timestamp="dayjs(e.createdAt).format('YYYY-MM-DD HH:mm:ss')"
        :color="config(e.eventType).color"
        placement="top"
      >
        <div
          class="timeline-card"
          :class="{ 'timeline-card--highlight': highlightActive && highlightIndex === idx }"
          :data-tl-idx="idx"
        >
          <div class="timeline-card__header">
            <el-icon :size="16" :color="config(e.eventType).color">
              <component :is="config(e.eventType).icon" />
            </el-icon>
            <span class="timeline-card__title">{{ config(e.eventType).label }}</span>
            <span v-if="e.nodeName" class="timeline-card__node">
              {{ e.nodeName }}
            </span>
          </div>
          <div class="timeline-card__body">
            <div v-if="e.userName" class="timeline-card__row">
              <span class="row-label">{{ $t('common.operatorLabel') }}</span>
              <span>{{ e.userName }}</span>
            </div>
            <div v-if="e.targetUserName" class="timeline-card__row">
              <span class="row-label">{{ $t('common.targetLabel') }}</span>
              <span>{{ e.targetUserName }}</span>
            </div>
            <div v-if="e.action" class="timeline-card__row">
              <span class="row-label">{{ $t('common.actionLabel') }}</span>
              <el-tag size="small" :type="e.action === 'AGREE' ? 'success' : 'danger'">
                {{ e.action }}
              </el-tag>
            </div>
            <div v-if="e.comment" class="timeline-card__row">
              <span class="row-label">{{ $t('common.commentLabel') }}</span>
              <span class="comment">{{ e.comment }}</span>
            </div>
            <div v-if="e.durationMs" class="timeline-card__row">
              <span class="row-label">{{ $t('common.durationLabel') }}</span>
              <span class="duration">{{ formatDuration(e.durationMs) }}</span>
            </div>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-if="!events.length" description="暂无审批轨迹" />
  </div>
</template>

<style scoped lang="scss">
.flow-timeline {
  padding: 16px;
  background: #fff;
  border-radius: 6px;
}

.timeline-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px 16px;
  max-width: 600px;
  transition: box-shadow 0.3s, border-color 0.3s, background 0.3s;

  /* P0-4: 节点点击高亮 */
  &--highlight {
    border-color: #1890ff;
    background: #eff6ff;
    box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.2);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }

  &__title {
    font-weight: 600;
    font-size: 14px;
    color: #1e293b;
  }

  &__node {
    color: #64748b;
    font-size: 12px;
    padding: 2px 8px;
    background: #fff;
    border-radius: 4px;
    border: 1px solid #e2e8f0;
  }

  &__body {
    font-size: 13px;
    color: #475569;
  }

  &__row {
    margin-top: 4px;
    display: flex;
    align-items: flex-start;
    gap: 4px;

    .row-label {
      color: #94a3b8;
      flex-shrink: 0;
    }

    .comment {
      flex: 1;
      word-break: break-word;
    }

    .duration {
      color: #1890ff;
      font-weight: 500;
    }
  }
}
</style>
