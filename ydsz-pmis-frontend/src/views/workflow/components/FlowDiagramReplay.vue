<!--
  @fileoverview 流程回放组件
  @description
    基于 FlowDiagramViewer + 步骤序列 + 播放控制，按时间顺序回放流程审批全过程。
    P2-4：配合 getReplaySteps 接口，依次高亮节点，展示每步操作人 / 动作 / 意见；
    P3-1 增强：自动滚屏到当前节点、步骤类型筛选、节点点击与坐标回滚联动。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/FlowDiagramReplay
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 流程回放组件
 * @description 基于 FlowDiagramViewer + 步骤序列 + 播放控制，按时间顺序回放流程审批全过程。
 *
 * <p>P2-4 落地：配合 P2-4 getReplaySteps 接口，依次高亮节点，展示每个步骤的操作人/动作/意见。
 * 支持播放/暂停/上一步/下一步/速度调节。
 *
 * <p>P3-1 增强：
 * <ul>
 *   <li>自动滚屏到当前节点（基于 step.coordinate 坐标）</li>
 *   <li>步骤类型筛选（按 START / HIS_TASK / AUDIT_LOG / CURRENT_TASK / END 过滤）</li>
 *   <li>节点点击跳转与坐标回滚联动</li>
 * </ul>
 *
 * @module views/workflow/components/FlowDiagramReplay
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshLeft, ArrowLeft } from '@element-plus/icons-vue'
import { getDiagram, getReplaySteps } from '@/api/workflow'
import type {
  FlowDiagramDTO,
  FlowReplayStepDTO,
  FlowDiagramNodeDTO,
} from '@/api/workflow/types'
import FlowDiagramViewer from './FlowDiagramViewer.vue'

const props = withDefaults(
  defineProps<{
    /** 流程实例 ID */
    instanceId: number
    /** 是否自动开始播放 */
    autoPlay?: boolean
  }>(),
  { autoPlay: false },
)

const { t } = useI18n()

const loading = ref(false)
const errorMsg = ref('')
const diagram = ref<FlowDiagramDTO | null>(null)
const steps = ref<FlowReplayStepDTO[]>([])
const currentIndex = ref(0)
const playing = ref(false)
const speed = ref(1) // 1x, 2x, 4x, 0.5x
const diagramContainer = ref<HTMLElement | null>(null)
const filterType = ref<string>('ALL') // 步骤类型筛选
let timer: ReturnType<typeof setInterval> | null = null

/** 速度枚举（毫秒/步） */
const SPEED_MAP: Record<number, number> = {
  0.5: 2000,
  1: 1000,
  2: 500,
  4: 250,
}

/** 步骤类型筛选下拉选项 */
const STEP_TYPE_OPTIONS = computed(() => [
  { value: 'ALL', label: t('workflow.replay.stepType.ALL') },
  { value: 'START', label: t('workflow.replay.stepType.START') },
  { value: 'HIS_TASK', label: t('workflow.replay.stepType.HIS_TASK') },
  { value: 'AUDIT_LOG', label: t('workflow.replay.stepType.AUDIT_LOG') },
  { value: 'CURRENT_TASK', label: t('workflow.replay.stepType.CURRENT_TASK') },
  { value: 'END', label: t('workflow.replay.stepType.END') },
])

/** 筛选后的步骤索引集合（用于步骤列表高亮） */
const visibleStepIndices = computed<number[]>(() => {
  if (filterType.value === 'ALL') {
    return steps.value.map((_, idx) => idx)
  }
  return steps.value
    .map((s, idx) => (s.type === filterType.value ? idx : -1))
    .filter((idx) => idx >= 0)
})

/** 当前步骤 */
const currentStep = computed<FlowReplayStepDTO | null>(() => {
  return steps.value[currentIndex.value] || null
})

/** 已回放步骤集合：已回放节点的最新状态 */
const replayedNodeStates = computed<Map<string, string>>(() => {
  const map = new Map<string, string>()
  if (!currentStep.value) return map
  for (let i = 0; i <= currentIndex.value; i++) {
    const s = steps.value[i]
    if (s?.nodeCode && s.nodeState) {
      // ACTIVE 状态允许被后续 PASSED 覆盖
      const cur = map.get(s.nodeCode)
      if (cur !== 'PASSED' && cur !== 'REJECTED') {
        map.set(s.nodeCode, s.nodeState)
      }
    }
  }
  return map
})

/** 转换后的 diagram，覆盖 activeNodeCodes / completedNodeCodes */
const replayedDiagram = computed<FlowDiagramDTO | null>(() => {
  if (!diagram.value) return null
  const activeNodeCodes: string[] = []
  const completedNodeCodes: string[] = []
  for (const [code, state] of replayedNodeStates.value.entries()) {
    if (state === 'ACTIVE') {
      activeNodeCodes.push(code)
    } else if (state === 'PASSED' || state === 'SKIPPED' || state === 'REJECTED') {
      completedNodeCodes.push(code)
    }
  }
  return {
    ...diagram.value,
    activeNodeCodes,
    completedNodeCodes,
  }
})

/** 总步数 */
const totalSteps = computed(() => steps.value.length)

/** 当前节点的回放状态（决定高亮颜色） */
const currentNodeState = computed(() => currentStep.value?.nodeState || '')

/** 加载数据 */
async function loadData() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [dResp, sResp] = await Promise.all([
      getDiagram(props.instanceId),
      getReplaySteps(props.instanceId),
    ])
    const dPayload = dResp.data || dResp
    const sPayload = sResp.data || sResp
    diagram.value = (dPayload as { data?: unknown }).data
      ? (dPayload as { data: FlowDiagramDTO }).data
      : (dPayload as unknown as FlowDiagramDTO)
    steps.value = (sPayload as { data?: unknown }).data
      ? (sPayload as { data: FlowReplayStepDTO[] }).data
      : (sPayload as unknown as FlowReplayStepDTO[])
    currentIndex.value = 0
  } catch (e) {
    errorMsg.value = (e as Error).message || t('workflow.replay.loadError')
  } finally {
    loading.value = false
  }
  if (props.autoPlay && totalSteps.value > 0) {
    play()
  }
}

/** 播放 */
function play() {
  if (timer) {
    return
  }
  playing.value = true
  timer = setInterval(() => {
    if (currentIndex.value >= totalSteps.value - 1) {
      pause()
      return
    }
    currentIndex.value++
  }, SPEED_MAP[speed.value] || 1000)
}

/** 暂停 */
function pause() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  playing.value = false
}

/** 停止 */
function stop() {
  pause()
  currentIndex.value = 0
}

/** 上一步 */
function prev() {
  pause()
  if (currentIndex.value > 0) {
    currentIndex.value--
  }
}

/** 下一步 */
function next() {
  pause()
  if (currentIndex.value < totalSteps.value - 1) {
    currentIndex.value++
  }
}

/** 跳转到指定步 */
function jumpTo(idx: number) {
  pause()
  currentIndex.value = Math.max(0, Math.min(idx, totalSteps.value - 1))
}

/** 切换速度 */
function changeSpeed(s: number) {
  speed.value = s
  if (playing.value) {
    pause()
    play()
  }
}

/** P3-1: 切换步骤类型筛选 */
function changeFilter(type: string) {
  filterType.value = type
}

/** P3-1: 自动滚屏到当前节点（基于 step.coordinate） */
function scrollToCurrentNode() {
  if (!currentStep.value?.coordinate || !diagramContainer.value) {
    return
  }
  const { x, y } = currentStep.value.coordinate
  // 简化处理：滚动到容器可视区中央
  // 真实场景中应当通过 FlowDiagramViewer 的内部 ref 定位节点 DOM
  // 这里使用通用 scrollIntoView 兼容 SVG 渲染模式
  const targetLeft = x - 200
  const targetTop = y - 100
  diagramContainer.value.scrollTo({
    left: Math.max(0, targetLeft),
    top: Math.max(0, targetTop),
    behavior: playing.value ? 'smooth' : 'auto',
  })
}

/** 节点状态标签 */
function nodeStateLabel(state: string | undefined): string {
  if (!state) return ''
  const map: Record<string, string> = {
    ENTERED: t('workflow.replay.nodeState.ENTERED'),
    PASSED: t('workflow.replay.nodeState.PASSED'),
    REJECTED: t('workflow.replay.nodeState.REJECTED'),
    ACTIVE: t('workflow.replay.nodeState.ACTIVE'),
    SKIPPED: t('workflow.replay.nodeState.SKIPPED'),
    OBSERVED: t('workflow.replay.nodeState.OBSERVED'),
    FINISHED: t('workflow.replay.nodeState.FINISHED'),
  }
  return map[state] || state
}

function nodeStateType(state: string | undefined): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  if (!state) return 'primary'
  if (state === 'PASSED' || state === 'FINISHED') return 'success'
  if (state === 'REJECTED') return 'danger'
  if (state === 'ACTIVE') return 'warning'
  return 'info'
}

function stepTypeLabel(type: string | undefined): string {
  if (!type) return ''
  const map: Record<string, string> = {
    START: t('workflow.replay.stepType.START'),
    HIS_TASK: t('workflow.replay.stepType.HIS_TASK'),
    AUDIT_LOG: t('workflow.replay.stepType.AUDIT_LOG'),
    CURRENT_TASK: t('workflow.replay.stepType.CURRENT_TASK'),
    END: t('workflow.replay.stepType.END'),
  }
  return map[type] || type
}

/** 节点悬停回调 */
function onNodeClick(node: FlowDiagramNodeDTO) {
  // 找到该节点的第一个回放步骤
  const idx = steps.value.findIndex((s) => s.nodeCode === node.nodeCode)
  if (idx >= 0) {
    jumpTo(idx)
  }
}

watch(
  () => props.instanceId,
  () => {
    stop()
    loadData()
  },
  { immediate: true },
)

/** P3-1: 监听 currentIndex 变化，自动滚屏到当前节点 */
watch(currentIndex, () => {
  nextTick(() => {
    scrollToCurrentNode()
  })
})

onBeforeUnmount(() => {
  pause()
})
</script>

<template>
  <div class="flow-replay">
    <div v-if="loading" class="flow-replay__loading">
      <el-skeleton :rows="6" animated />
    </div>
    <el-empty v-else-if="errorMsg" :description="errorMsg" />
    <el-empty
      v-else-if="!steps.length"
      :description="t('workflow.replay.empty')"
    />
    <div v-else class="flow-replay__content">
      <!-- 流程图 -->
      <div
        ref="diagramContainer"
        class="flow-replay__diagram"
        :data-current-node="currentStep?.nodeCode || ''"
      >
        <FlowDiagramViewer
          v-if="replayedDiagram"
          :diagram="replayedDiagram"
          @node-click="onNodeClick"
        />
      </div>

      <!-- 播放控制条 -->
      <div class="flow-replay__controls">
        <div class="controls-left">
          <el-button-group>
            <el-button
              size="small"
              :icon="playing ? 'VideoPause' : 'VideoPlay'"
              :type="playing ? 'warning' : 'primary'"
              :disabled="totalSteps <= 1"
              @click="playing ? pause() : play()"
            >
              {{ playing ? t('workflow.replay.pause') : t('workflow.replay.play') }}
            </el-button>
            <el-button size="small" :icon="RefreshLeft" @click="stop">
              {{ t('workflow.replay.stop') }}
            </el-button>
            <el-button size="small" :icon="ArrowLeft" @click="prev">
              {{ t('workflow.replay.prev') }}
            </el-button>
            <el-button size="small" @click="next">
              {{ t('workflow.replay.next') }}
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </el-button-group>
        </div>
        <div class="controls-progress">
          <span class="progress-text">
            {{ t('workflow.replay.step') }} {{ currentIndex + 1 }}
            {{ t('workflow.replay.of') }} {{ totalSteps }}
          </span>
          <el-slider
            :model-value="currentIndex"
            :max="totalSteps - 1"
            :show-tooltip="false"
            class="progress-slider"
            @input="(v: number | number[]) => jumpTo(v as number)"
          />
        </div>
        <div class="controls-right">
          <!-- P3-1: 步骤类型筛选 -->
          <el-select
            v-model="filterType"
            size="small"
            style="width: 120px; margin-right: 12px"
            @change="changeFilter"
          >
            <el-option
              v-for="opt in STEP_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
            />
          </el-select>
          <span class="speed-label">{{ t('workflow.replay.speed') }}:</span>
          <el-select
            :model-value="speed"
            size="small"
            style="width: 90px"
            @change="(v: number) => changeSpeed(v)"
          >
            <el-option :value="0.5" label="0.5x" />
            <el-option :value="1" label="1x" />
            <el-option :value="2" label="2x" />
            <el-option :value="4" label="4x" />
          </el-select>
        </div>
      </div>

      <!-- 当前步骤详情卡 -->
      <div v-if="currentStep" class="flow-replay__current">
        <div class="current-header">
          <el-tag :type="nodeStateType(currentNodeState)" effect="dark" size="large">
            {{ stepTypeLabel(currentStep.type) }} · {{ nodeStateLabel(currentNodeState) }}
          </el-tag>
          <span class="current-step-no">#{{ currentIndex + 1 }}</span>
          <!-- P3-1: 节点坐标徽章（仅当 coordinate 存在时显示） -->
          <el-tag
            v-if="currentStep.coordinate"
            size="small"
            type="info"
            effect="plain"
            class="current-coord-tag"
          >
            📍 x:{{ Math.round(currentStep.coordinate.x) }} y:{{ Math.round(currentStep.coordinate.y) }}
          </el-tag>
        </div>
        <div class="current-body">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="t('workflow.instance.title')">
              {{ currentStep.nodeName || currentStep.nodeCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('common.search')">
              <el-tag size="small">{{ currentStep.action || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('workflow.replay.actor')">
              {{ currentStep.actorName || currentStep.actor || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('workflow.task.duration')">
              <span v-if="currentStep.durationMs">
                {{ Math.round(currentStep.durationMs / 1000) }}s
              </span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="currentStep.timestamp" :label="t('workflow.replay.time')" :span="2">
              {{ new Date(currentStep.timestamp).toLocaleString() }}
            </el-descriptions-item>
            <el-descriptions-item v-if="currentStep.comment" :label="t('workflow.task.comment')" :span="2">
              <span class="comment-text">{{ currentStep.comment }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 步骤列表 -->
      <div class="flow-replay__steps">
        <div class="steps-title">{{ t('workflow.replay.stepsTitle', { count: totalSteps }) }}</div>
        <el-timeline>
          <el-timeline-item
            v-for="(step, idx) in steps"
            v-show="visibleStepIndices.includes(idx)"
            :key="idx"
            :type="nodeStateType(step.nodeState)"
            :timestamp="step.timestamp ? new Date(step.timestamp).toLocaleString() : ''"
            :class="{ 'is-current': idx === currentIndex, 'is-hidden-step': !visibleStepIndices.includes(idx) }"
            placement="top"
            @click="jumpTo(idx)"
          >
            <div class="step-item">
              <span class="step-no">#{{ idx + 1 }}</span>
              <el-tag size="small" :type="nodeStateType(step.nodeState)">
                {{ nodeStateLabel(step.nodeState) }}
              </el-tag>
              <span class="step-node">{{ step.nodeName || step.nodeCode || '-' }}</span>
              <span class="step-actor">{{ step.actorName || step.actor || '-' }}</span>
              <span class="step-action">{{ step.action || '-' }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.flow-replay {
  background: #fff;
  border-radius: 6px;
  padding: 16px;

  &__loading,
  &__content {
    width: 100%;
  }

  &__diagram {
    margin-bottom: 12px;
    overflow: auto;
    max-height: 600px;
  }

  &__controls {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 12px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    margin-bottom: 16px;

    .controls-progress {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .progress-text {
      font-size: 12px;
      color: #475569;
      white-space: nowrap;
      min-width: 80px;
    }

    .progress-slider {
      flex: 1;
      margin: 0;
    }

    .speed-label {
      font-size: 12px;
      color: #475569;
    }
  }

  &__current {
    padding: 12px;
    background: #fffbeb;
    border: 1px solid #fde68a;
    border-radius: 6px;
    margin-bottom: 16px;

    .current-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      flex-wrap: wrap;
    }

    .current-step-no {
      font-weight: 600;
      color: #b45309;
    }

    .current-coord-tag {
      font-family: 'SF Mono', monospace;
      font-size: 11px;
    }

    .comment-text {
      color: #1e293b;
      font-style: italic;
    }
  }

  &__steps {
    .steps-title {
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 8px;
    }

    .step-item {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .step-no {
        font-family: 'SF Mono', monospace;
        color: #64748b;
        font-size: 12px;
        min-width: 28px;
      }

      .step-node {
        font-weight: 500;
        color: #1e293b;
      }

      .step-actor {
        color: #475569;
        font-size: 12px;
      }

      .step-action {
        color: #1890ff;
        font-size: 12px;
        font-family: 'SF Mono', monospace;
      }
    }

    :deep(.is-current) {
      .el-timeline-item__node {
        box-shadow: 0 0 0 4px rgba(24, 144, 255, 0.25);
      }
    }

    :deep(.is-hidden-step) {
      display: none;
    }

    :deep(.el-timeline-item__wrapper) {
      cursor: pointer;
    }
  }
}
</style>
