<!--
  @fileoverview Agent 在线调试控制台

  - 业务模块归属: AI Agent 智能体调试
  - 关键能力: SSE 流式输出 ReAct 推理过程 + 多轮对话 + 实时打字机效果
  - 关联的后端接口: @/api/agent/debug

  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * Agent 在线调试控制台
 *
 * 对标 Coze Bot 调试面板 / Dify Chat 调试工具：
 * - 选择 Agent 类型，输入调试参数
 * - SSE 实时展示 ReAct 推理步骤（Thought → Action → Observation → Final Answer）
 * - 支持多轮对话（sessionId）
 * - 打字机效果，逐 token 展示
 * - 历史步骤折叠展示
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { executeInMemory, executeStream } from '@/api/agent/debug'
import type { ReActStep, SseEvent } from '@/api/agent/debug/types'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const AGENT_TYPE_OPTIONS = computed(() => [
  { code: 'RISK_WARNING', desc: t('agent.debug.agentType.RISK_WARNING') },
  { code: 'RESOURCE_RECOMMEND', desc: t('agent.debug.agentType.RESOURCE_RECOMMEND') },
  { code: 'PROFIT_FORECAST', desc: t('agent.debug.agentType.PROFIT_FORECAST') },
  { code: 'WIN_RATE_PREDICT', desc: t('agent.debug.agentType.WIN_RATE_PREDICT') },
  { code: 'TIMESHEET_ANOMALY', desc: t('agent.debug.agentType.TIMESHEET_ANOMALY') },
  { code: 'APPROVER_RECOMMEND', desc: t('agent.debug.agentType.APPROVER_RECOMMEND') },
  { code: 'COMMENT_DRAFT', desc: t('agent.debug.agentType.COMMENT_DRAFT') },
  { code: 'FLOW_GENERATOR', desc: t('agent.debug.agentType.FLOW_GENERATOR') },
])

const form = reactive({
  agentType: 'RISK_WARNING',
  bizType: 'PROJECT',
  bizId: '',
  bizRef: '',
  sessionId: '',
  userInput: '',
  facts: '' as string,
})

const streaming = ref(false)
const abortFn = ref<(() => void) | null>(null)
const steps = ref<ReActStep[]>([])
const currentStep = ref<ReActStep | null>(null)
const finalAnswer = ref('')
const events = ref<SseEvent[]>([])
const consoleEl = ref<HTMLDivElement | null>(null)
const elapsed = ref(0)
let startTime = 0
let timer: ReturnType<typeof setInterval> | null = null

const isStreaming = computed(() => streaming.value)

function buildContext(): Record<string, unknown> {
  const ctx: Record<string, unknown> = {
    bizType: form.bizType,
    bizId: form.bizId || '0',
    bizRef: form.bizRef,
    source: 'DEBUG_CONSOLE',
  }
  if (form.sessionId) {
    ctx.sessionId = form.sessionId
  }
  const params: Record<string, unknown> = {}
  if (form.userInput) {
    params.userInput = form.userInput
  }
  if (form.facts) {
    try {
      const parsed = JSON.parse(form.facts)
      Object.assign(params, parsed)
    } catch {
      params.facts = form.facts
    }
  }
  ctx.params = params
  return ctx
}

async function runStream() {
  if (!form.agentType) {
    ElMessage.warning(t('agent.debug.messages.agentRequired'))
    return
  }
  resetExecution()
  streaming.value = true
  startTime = Date.now()
  timer = setInterval(() => {
    elapsed.value = Date.now() - startTime
  }, 100)

  const ctx = buildContext()
  try {
    const close = await executeStream(
      form.agentType,
      ctx,
      (eventType, data) => {
        const event: SseEvent = {
          type: eventType as SseEvent['type'],
          data,
          step: currentStep.value?.step,
          timestamp: Date.now(),
        }
        events.value.push(event)
        handleSseEvent(eventType, data)
        scrollToBottom()
      },
      (err) => {
        ElMessage.error(err.message || t('agent.debug.messages.streamFailed'))
        streaming.value = false
      },
    )
    abortFn.value = close
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.debug.messages.streamFailed'))
    streaming.value = false
  }
}

function handleSseEvent(eventType: string, data: string) {
  let parsed: any = data
  try {
    parsed = JSON.parse(data)
  } catch {
    // 非 JSON，按纯文本处理
  }

  switch (eventType) {
    case 'STEP_START':
      currentStep.value = { step: parsed.step || steps.value.length + 1 }
      break
    case 'THOUGHT':
      if (currentStep.value) {
        currentStep.value.thought = typeof parsed === 'string' ? parsed : parsed.thought || parsed.text || ''
      }
      break
    case 'ACTION':
      if (currentStep.value) {
        currentStep.value.action = typeof parsed === 'string' ? parsed : parsed.action || parsed.tool || ''
        currentStep.value.actionInput = typeof parsed === 'object' ? JSON.stringify(parsed.actionInput || parsed.arguments || parsed) : parsed
      }
      break
    case 'OBSERVATION':
      if (currentStep.value) {
        currentStep.value.observation = typeof parsed === 'string' ? parsed : JSON.stringify(parsed)
      }
      break
    case 'FINAL_ANSWER':
      finalAnswer.value = typeof parsed === 'string' ? parsed : (parsed.answer || parsed.text || JSON.stringify(parsed))
      break
    case 'STEP_END':
      if (currentStep.value) {
        steps.value.push({ ...currentStep.value })
        currentStep.value = null
      }
      break
    case 'DONE':
      streaming.value = false
      if (timer) clearInterval(timer)
      break
    case 'ERROR':
      ElMessage.error(typeof parsed === 'string' ? parsed : (parsed.message || 'Execution error'))
      streaming.value = false
      if (timer) clearInterval(timer)
      break
  }
}

async function runSync() {
  if (!form.agentType) {
    ElMessage.warning(t('agent.debug.messages.agentRequired'))
    return
  }
  resetExecution()
  streaming.value = true
  startTime = Date.now()
  timer = setInterval(() => {
    elapsed.value = Date.now() - startTime
  }, 100)

  try {
    const ctx = buildContext()
    const { data } = await executeInMemory(form.agentType, ctx)
    const result = data as any
    if (result) {
      finalAnswer.value = result.suggestion || JSON.stringify(result, null, 2)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.debug.messages.syncFailed'))
  } finally {
    streaming.value = false
    if (timer) clearInterval(timer)
    elapsed.value = Date.now() - startTime
  }
}

function stopStream() {
  if (abortFn.value) {
    abortFn.value()
    abortFn.value = null
  }
  streaming.value = false
  if (timer) clearInterval(timer)
}

function resetExecution() {
  steps.value = []
  currentStep.value = null
  finalAnswer.value = ''
  events.value = []
  elapsed.value = 0
}

function scrollToBottom() {
  nextTick(() => {
    if (consoleEl.value) {
      consoleEl.value.scrollTop = consoleEl.value.scrollHeight
    }
  })
}

function clearAll() {
  resetExecution()
  form.userInput = ''
}

function formatTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

onMounted(() => {
  // 初始化
})
</script>

<template>
  <div class="agent-debug-page">
    <el-row :gutter="16" class="full-height">
      <!-- 左侧：配置面板 -->
      <el-col :xs="24" :md="8" :lg="6">
        <el-card shadow="never" class="config-card">
          <template #header>{{ t('agent.debug.title') }}</template>
          <el-form label-width="80px" size="small">
            <el-form-item :label="t('agent.debug.form.agentType')">
              <el-select v-model="form.agentType" style="width: 100%">
                <el-option
                  v-for="a in AGENT_TYPE_OPTIONS"
                  :key="a.code"
                  :value="a.code"
                  :label="`${a.code}（${a.desc}）`"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('agent.debug.form.bizType')">
              <el-select v-model="form.bizType" style="width: 100%">
                <el-option value="PROJECT" :label="t('agent.debug.bizType.PROJECT')" />
                <el-option value="OPPORTUNITY" :label="t('agent.debug.bizType.OPPORTUNITY')" />
                <el-option value="TIMESHEET" :label="t('agent.debug.bizType.TIMESHEET')" />
                <el-option value="STAFF" :label="t('agent.debug.bizType.STAFF')" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('agent.debug.form.bizId')">
              <el-input v-model="form.bizId" placeholder="如 1001" />
            </el-form-item>
            <el-form-item :label="t('agent.debug.form.bizRef')">
              <el-input v-model="form.bizRef" placeholder="如 PRJ-001" />
            </el-form-item>
            <el-form-item :label="t('agent.debug.form.sessionId')">
              <el-input v-model="form.sessionId" :placeholder="t('agent.debug.form.sessionIdPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('agent.debug.form.facts')">
              <el-input
                v-model="form.facts"
                type="textarea"
                :rows="3"
                placeholder='{"cpi":0.95,"spi":0.92}'
              />
            </el-form-item>
            <el-divider content-position="left">{{ t('agent.debug.form.input') }}</el-divider>
            <el-form-item :label="t('agent.debug.form.userInput')">
              <el-input
                v-model="form.userInput"
                type="textarea"
                :rows="4"
                :placeholder="t('agent.debug.form.userInputPlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <el-button-group style="width: 100%">
                <el-button
                  v-permission="[PC.AGENT_DEBUG_VIEW]"
                  type="primary"
                  :icon="'VideoPlay'"
                  :loading="streaming"
                  style="width: 50%"
                  @click="runStream"
                >
                  {{ t('agent.debug.buttons.runStream') }}
                </el-button>
                <el-button
                  v-permission="[PC.AGENT_DEBUG_VIEW]"
                  type="success"
                  :icon="'Check'"
                  :loading="streaming"
                  style="width: 30%"
                  @click="runSync"
                >
                  {{ t('agent.debug.buttons.runSync') }}
                </el-button>
                <el-button
                  v-if="streaming"
                  type="danger"
                  :icon="'VideoPause'"
                  style="width: 20%"
                  @click="stopStream"
                >
                  {{ t('agent.debug.buttons.stop') }}
                </el-button>
              </el-button-group>
            </el-form-item>
            <el-form-item>
              <el-button :icon="'Delete'" link type="info" @click="clearAll">
                {{ t('agent.debug.buttons.clear') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：推理过程控制台 -->
      <el-col :xs="24" :md="16" :lg="18">
        <el-card shadow="never" class="console-card">
          <template #header>
            <div class="console-header">
              <span>{{ t('agent.debug.console.title') }}</span>
              <div class="console-meta">
                <el-tag v-if="streaming" type="warning" size="small" effect="dark">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  {{ t('agent.debug.console.streaming') }}
                </el-tag>
                <el-tag v-else-if="finalAnswer" type="success" size="small" effect="dark">
                  {{ t('agent.debug.console.done') }}
                </el-tag>
                <el-tag v-if="elapsed > 0" type="info" size="small">
                  {{ t('agent.debug.console.elapsed') }}: {{ formatTime(elapsed) }}
                </el-tag>
                <el-tag v-if="steps.length > 0" type="info" size="small">
                  {{ t('agent.debug.console.steps') }}: {{ steps.length }}
                </el-tag>
              </div>
            </div>
          </template>

          <div ref="consoleEl" class="console-body">
            <!-- ReAct 步骤展示 -->
            <div v-for="(step, idx) in steps" :key="idx" class="react-step">
              <div class="step-header">
                <el-tag type="primary" size="small" effect="dark">{{ t('agent.debug.console.step') }} {{ step.step }}</el-tag>
              </div>
              <div v-if="step.thought" class="step-section thought">
                <div class="section-label">
                  <el-icon><ChatDotRound /></el-icon>
                  {{ t('agent.debug.console.thought') }}
                </div>
                <div class="section-content">{{ step.thought }}</div>
              </div>
              <div v-if="step.action" class="step-section action">
                <div class="section-label">
                  <el-icon><Tools /></el-icon>
                  {{ t('agent.debug.console.action') }}
                </div>
                <div class="section-content">
                  <el-tag size="small" type="warning">{{ step.action }}</el-tag>
                  <code v-if="step.actionInput" class="action-input">{{ step.actionInput }}</code>
                </div>
              </div>
              <div v-if="step.observation" class="step-section observation">
                <div class="section-label">
                  <el-icon><View /></el-icon>
                  {{ t('agent.debug.console.observation') }}
                </div>
                <pre class="section-content pre-content">{{ step.observation }}</pre>
              </div>
            </div>

            <!-- 当前执行中的步骤 -->
            <div v-if="currentStep" class="react-step current">
              <div class="step-header">
                <el-tag type="warning" size="small" effect="dark" class="blink">
                  {{ t('agent.debug.console.step') }} {{ currentStep.step }}
                </el-tag>
              </div>
              <div v-if="currentStep.thought" class="step-section thought">
                <div class="section-label">{{ t('agent.debug.console.thought') }}</div>
                <div class="section-content typing">{{ currentStep.thought }}<span class="cursor">▋</span></div>
              </div>
            </div>

            <!-- 最终答案 -->
            <div v-if="finalAnswer" class="final-answer">
              <el-divider content-position="left">
                <el-icon><CircleCheckFilled /></el-icon>
                {{ t('agent.debug.console.finalAnswer') }}
              </el-divider>
              <div class="answer-content">{{ finalAnswer }}</div>
            </div>

            <!-- 空状态 -->
            <el-empty v-if="!streaming && steps.length === 0 && !finalAnswer"
              :description="t('agent.debug.console.empty')"
              :image-size="80"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.agent-debug-page {
  height: calc(100vh - 120px);

  .full-height {
    height: 100%;
  }

  .config-card {
    height: 100%;
    overflow-y: auto;
  }

  .console-card {
    height: 100%;
    display: flex;
    flex-direction: column;

    :deep(.el-card__body) {
      flex: 1;
      overflow: hidden;
      padding: 0;
    }
  }

  .console-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .console-meta {
      display: flex;
      gap: 8px;
    }
  }

  .console-body {
    height: 100%;
    overflow-y: auto;
    padding: 16px;
  }

  .react-step {
    background: var(--el-fill-color-light);
    border-radius: 8px;
    padding: 12px;
    margin-bottom: 12px;
    border-left: 3px solid var(--el-color-primary);

    &.current {
      border-left-color: var(--el-color-warning);
      background: var(--el-color-warning-light-9);
    }

    .step-header {
      margin-bottom: 8px;
    }

    .step-section {
      margin-bottom: 8px;

      &:last-child {
        margin-bottom: 0;
      }

      .section-label {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin-bottom: 4px;
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .section-content {
        font-size: 13px;
        line-height: 1.6;
        color: var(--el-text-color-primary);
      }

      &.thought .section-content {
        color: var(--el-color-primary);
        font-style: italic;
      }

      &.action .section-content {
        display: flex;
        align-items: center;
        gap: 8px;

        .action-input {
          font-size: 12px;
          background: var(--el-fill-color);
          padding: 2px 6px;
          border-radius: 3px;
          color: var(--el-color-warning);
        }
      }

      &.observation .pre-content {
        background: var(--el-fill-color-dark);
        padding: 8px;
        border-radius: 4px;
        font-size: 12px;
        max-height: 200px;
        overflow: auto;
        margin: 0;
        white-space: pre-wrap;
        word-break: break-all;
      }
    }
  }

  .final-answer {
    .answer-content {
      background: var(--el-color-success-light-9);
      border: 1px solid var(--el-color-success-light-7);
      border-radius: 8px;
      padding: 16px;
      font-size: 14px;
      line-height: 1.8;
      color: var(--el-text-color-primary);
    }
  }

  .typing {
    .cursor {
      animation: blink 1s step-end infinite;
    }
  }

  .blink {
    animation: blink 1.5s ease-in-out infinite;
  }

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }
}
</style>
