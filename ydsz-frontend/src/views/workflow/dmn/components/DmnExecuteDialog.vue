<!--
  @fileoverview DMN 决策表执行测试弹窗
  @description
    输入参数 JSON → 调用 execute API → 展示输出结果。
    用于发布前 / 上线后对决策表进行规则命中验证。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/dmn/components/DmnExecuteDialog
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  executeDmnTable,
  parseColumns,
  type FlowDmnTableDTO,
} from '@/api/workflow/dmn'
import { isHandledError } from '@/utils/error'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  /** 传入的决策表数据 */
  data: FlowDmnTableDTO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

/** 执行测试提交状态 */
const executing = ref(false)
/** 输入参数 JSON 字符串 */
const contextJson = ref('')
/** 执行结果数据 */
const result = ref<Record<string, unknown>[] | null>(null)
/** 执行错误信息 */
const resultError = ref('')

/** 输入列定义（用于生成示例 context） */
const inputColumns = computed(() => parseColumns(props.data?.inputsJson))

/** 根据输入列生成示例 context JSON */
function buildExampleContext(): string {
  const example: Record<string, unknown> = {}
  inputColumns.value.forEach((col) => {
    if (col.type === 'number') {
      example[col.name] = 0
    } else if (col.type === 'boolean') {
      example[col.name] = false
    } else if (col.type === 'date') {
      example[col.name] = '2026-01-01'
    } else {
      example[col.name] = ''
    }
  })
  return JSON.stringify(example, null, 2)
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    result.value = null
    resultError.value = ''
    contextJson.value = buildExampleContext()
  },
)

/** 校验并解析 context JSON */
function parseContext(): Record<string, unknown> | null {
  const text = contextJson.value.trim()
  if (!text) {
    ElMessage.warning(t('workflow.dmn.execute.contextRequired'))
    return null
  }
  try {
    const obj = JSON.parse(text)
    if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) {
      ElMessage.warning(t('workflow.dmn.execute.contextMustBeObject'))
      return null
    }
    return obj as Record<string, unknown>
  } catch (e) {
    ElMessage.warning(t('workflow.dmn.execute.contextInvalidJson') + (e as Error).message)
    return null
  }
}

/** 执行决策表测试 */
async function handleExecute() {
  if (!props.data?.tableKey) {
    ElMessage.warning(t('workflow.dmn.execute.noTableKey'))
    return
  }
  const ctx = parseContext()
  if (!ctx) return
  executing.value = true
  result.value = null
  resultError.value = ''
  try {
    const { data } = await executeDmnTable({ tableKey: props.data.tableKey, context: ctx })
    result.value = data || []
    if (result.value.length === 0) {
      resultError.value = t('workflow.dmn.execute.noMatch')
    }
  } catch (e) {
    resultError.value = (e as Error).message
    if (!isHandledError(e)) {
      ElMessage.error((e as Error).message)
    }
  } finally {
    executing.value = false
  }
}

/** 关闭弹窗 */
function handleClose() {
  visible.value = false
}

/** 格式化结果为可读 JSON */
const resultText = computed(() => {
  if (resultError.value) return resultError.value
  if (!result.value) return ''
  return JSON.stringify(result.value, null, 2)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('workflow.dmn.execute.title')"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <div class="dmn-execute">
      <!-- 决策表信息 -->
      <el-descriptions :column="2" border size="small" class="info-block">
        <el-descriptions-item :label="t('workflow.dmn.tableKey')">
          {{ props.data?.tableKey }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.dmn.tableName')">
          {{ props.data?.tableName }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.dmn.hitPolicy')">
          {{ props.data?.hitPolicy }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('workflow.dmn.version')">
          v{{ props.data?.version ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 输入参数 JSON -->
      <div class="section-title">
        {{ t('workflow.dmn.execute.contextTitle') }}
        <span v-if="inputColumns.length > 0" class="section-tip">
          {{ t('workflow.dmn.execute.contextTip') }}
        </span>
      </div>
      <el-input
        v-model="contextJson"
        type="textarea"
        :rows="8"
        :placeholder="t('workflow.dmn.execute.contextPlaceholder')"
        class="json-input"
      />

      <div class="action-bar">
        <el-button type="primary" :loading="executing" @click="handleExecute">
          {{ t('workflow.dmn.execute.button') }}
        </el-button>
      </div>

      <!-- 执行结果 -->
      <div class="section-title">{{ t('workflow.dmn.execute.resultTitle') }}</div>
      <pre v-if="resultText" class="result-area" :class="{ 'result-error': !!resultError }">{{ resultText }}</pre>
      <div v-else class="result-empty">{{ t('workflow.dmn.execute.resultEmpty') }}</div>
    </div>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.dmn-execute {
  .info-block {
    margin-bottom: 16px;
  }

  .section-title {
    margin: 12px 0 8px;
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }

  .section-tip {
    margin-left: 8px;
    font-size: 12px;
    font-weight: normal;
    color: #909399;
  }

  .json-input :deep(.el-textarea__inner) {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
  }

  .action-bar {
    margin-top: 12px;
    text-align: right;
  }

  .result-area {
    margin: 0;
    padding: 12px;
    background: #f5f7fa;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    line-height: 1.5;
    color: #303133;
    max-height: 280px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }

  .result-error {
    background: #fef0f0;
    border-color: #fbc4c4;
    color: #f56c6c;
  }

  .result-empty {
    padding: 24px;
    text-align: center;
    color: #909399;
    font-size: 13px;
    background: #fafafa;
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
  }
}
</style>
