<!--
  @fileoverview SLA 规则配置组件
  @description
    BPMN 用户任务节点的 SLA 规则配置：超时阈值、超时动作（REMIND / ESCALATE /
    AUTO_PASS / AUTO_REJECT）、提醒间隔、最大提醒次数、升级审批人、自动意见模板。
    与 BpmnDesigner.vue 的属性面板集成。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/SlaRuleConfig
  @author ydsz-pmis-team
  @since 1.0.0
-->
<template>
  <div class="sla-rule-config">
    <div class="sla__header">
      <span class="sla__title">SLA 超时策略</span>
      <div class="sla__actions">
        <el-switch
          v-model="enabled"
          size="small"
          inline-prompt
          active-text="启用"
          inactive-text="停用"
          @change="onToggleEnabled"
        />
        <el-button size="small" type="primary" :loading="saving" :disabled="!enabled" @click="save">
          保存
        </el-button>
      </div>
    </div>

    <el-form
      v-if="enabled"
      :model="form"
      label-width="100px"
      size="small"
      class="sla__form"
    >
      <el-form-item label="超时阈值" required>
        <el-input-number
          v-model="form.timeoutMinutes"
          :min="1"
          :max="525600"
          controls-position="right"
          style="width: 140px"
        />
        <span class="sla__unit">分钟</span>
        <el-tooltip content="任务创建后多少分钟未处理视为超时" placement="top">
          <el-icon class="sla__hint"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-form-item>

      <el-form-item label="超时动作" required>
        <el-select v-model="form.action" style="width: 160px">
          <el-option
            v-for="opt in actionOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="sla__action-desc">{{ actionDescMap[form.action] }}</span>
      </el-form-item>

      <el-form-item label="提醒间隔">
        <el-input-number
          v-model="form.reminderIntervalMinutes"
          :min="1"
          :max="10080"
          controls-position="right"
          style="width: 140px"
        />
        <span class="sla__unit">分钟</span>
        <span class="sla__sub-hint">默认 60 分钟</span>
      </el-form-item>

      <el-form-item label="最大提醒">
        <el-input-number
          v-model="form.maxReminders"
          :min="0"
          :max="20"
          controls-position="right"
          style="width: 140px"
        />
        <span class="sla__unit">次</span>
        <span class="sla__sub-hint">达到后执行最终动作，默认 3 次</span>
      </el-form-item>

      <el-form-item v-if="form.action === 'ESCALATE'" label="升级给">
        <el-input-number
          v-model="form.escalateUserId"
          :min="1"
          controls-position="right"
          style="width: 160px"
          placeholder="用户 ID"
        />
        <span class="sla__sub-hint">为空时升级给默认管理员（ID=1）</span>
      </el-form-item>

      <el-form-item
        v-if="form.action === 'AUTO_PASS' || form.action === 'AUTO_REJECT'"
        label="自动备注"
      >
        <el-input
          v-model="form.autoComment"
          type="textarea"
          :rows="2"
          placeholder="写入审批意见的备注内容"
          style="max-width: 280px"
        />
      </el-form-item>
    </el-form>

    <div v-else class="sla__disabled-tip">
      未启用 SLA — 启用后任务创建时将按超时阈值设置截止时间
    </div>

    <div class="sla__preview">
      <el-divider content-position="left"><span class="sla__preview-title">效果预览</span></el-divider>
      <div v-if="enabled" class="sla__preview-text">
        任务创建后
        <strong>{{ form.timeoutMinutes }}</strong> 分钟未处理 →
        每 <strong>{{ form.reminderIntervalMinutes }}</strong> 分钟提醒一次，最多
        <strong>{{ form.maxReminders }}</strong> 次 →
        <el-tag :type="actionTagType(form.action)" size="small">{{ actionLabelMap[form.action] }}</el-tag>
        <template v-if="form.action === 'ESCALATE' && form.escalateUserId">
          （转给用户 {{ form.escalateUserId }}）
        </template>
      </div>
      <div v-else class="sla__preview-text sla__preview-text--muted">
        无超时策略
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSlaConfig, saveSlaConfig } from '@/api/workflow'
import type { SlaRuleConfigDTO, SlaStrategy } from '@/api/workflow/types'

const props = defineProps<{
  definitionId?: number | null
  nodeCode?: string | null
}>()

const actionOptions: { label: string; value: SlaStrategy }[] = [
  { label: '提醒', value: 'REMIND' },
  { label: '升级', value: 'ESCALATE' },
  { label: '自动通过', value: 'AUTO_PASS' },
  { label: '自动驳回', value: 'AUTO_REJECT' },
]

const actionLabelMap: Record<SlaStrategy, string> = {
  REMIND: '提醒',
  ESCALATE: '升级',
  AUTO_PASS: '自动通过',
  AUTO_REJECT: '自动驳回',
}

const actionDescMap: Record<SlaStrategy, string> = {
  REMIND: '超时后发提醒通知，达上限后仅标记超时',
  ESCALATE: '超时后转办给升级目标用户',
  AUTO_PASS: '超时后系统自动通过任务',
  AUTO_REJECT: '超时后系统自动驳回任务',
}

const actionTagTypeMap: Record<SlaStrategy, 'warning' | 'danger' | 'success' | 'info'> = {
  REMIND: 'warning',
  ESCALATE: 'danger',
  AUTO_PASS: 'success',
  AUTO_REJECT: 'danger',
}

function actionTagType(action: SlaStrategy): 'warning' | 'danger' | 'success' | 'info' {
  return actionTagTypeMap[action]
}

const enabled = ref(false)
const saving = ref(false)
const loaded = ref(false)

const form = reactive<Required<SlaRuleConfigDTO>>({
  timeoutMinutes: 1440,
  action: 'REMIND',
  reminderIntervalMinutes: 60,
  maxReminders: 3,
  escalateUserId: null,
  autoComment: '',
})

watch(
  () => [props.definitionId, props.nodeCode],
  ([defId, code]) => {
    if (defId && code) {
      loadConfig(Number(defId), String(code))
    } else {
      enabled.value = false
      loaded.value = false
    }
  },
  { immediate: true },
)

async function loadConfig(defId: number, code: string) {
  try {
    const res = await getSlaConfig(defId, code)
    const jsonStr = res.data?.data
    if (jsonStr) {
      const parsed = JSON.parse(jsonStr) as Partial<SlaRuleConfigDTO>
      form.timeoutMinutes = parsed.timeoutMinutes ?? 1440
      form.action = parsed.action ?? 'REMIND'
      form.reminderIntervalMinutes = parsed.reminderIntervalMinutes ?? 60
      form.maxReminders = parsed.maxReminders ?? 3
      form.escalateUserId = parsed.escalateUserId ?? null
      form.autoComment = parsed.autoComment ?? ''
      enabled.value = true
    } else {
      resetForm()
      enabled.value = false
    }
    loaded.value = true
  } catch {
    resetForm()
    enabled.value = false
    loaded.value = false
  }
}

function resetForm() {
  form.timeoutMinutes = 1440
  form.action = 'REMIND'
  form.reminderIntervalMinutes = 60
  form.maxReminders = 3
  form.escalateUserId = null
  form.autoComment = ''
}

function onToggleEnabled(val: boolean | string | number) {
  if (val && !loaded.value) {
    // 首次启用时使用默认值
    resetForm()
  }
}

async function save() {
  if (!props.definitionId || !props.nodeCode) {
    ElMessage.warning('请先选择节点')
    return
  }
  if (!form.timeoutMinutes || form.timeoutMinutes <= 0) {
    ElMessage.warning('超时阈值必须大于 0')
    return
  }
  saving.value = true
  try {
    const payload: Partial<SlaRuleConfigDTO> = {
      timeoutMinutes: form.timeoutMinutes,
      action: form.action,
      reminderIntervalMinutes: form.reminderIntervalMinutes,
      maxReminders: form.maxReminders,
    }
    if (form.action === 'ESCALATE' && form.escalateUserId) {
      payload.escalateUserId = form.escalateUserId
    }
    if ((form.action === 'AUTO_PASS' || form.action === 'AUTO_REJECT') && form.autoComment) {
      payload.autoComment = form.autoComment
    }
    await saveSlaConfig(Number(props.definitionId), String(props.nodeCode), payload)
    ElMessage.success('SLA 配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sla-rule-config {
  width: 100%;
}

.sla__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.sla__title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.sla__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sla__form {
  margin-top: 4px;
}

.sla__unit {
  margin-left: 6px;
  font-size: 12px;
  color: #909399;
}

.sla__hint {
  margin-left: 4px;
  color: #909399;
  cursor: help;
  font-size: 14px;
}

.sla__sub-hint {
  margin-left: 8px;
  font-size: 11px;
  color: #909399;
}

.sla__action-desc {
  margin-left: 8px;
  font-size: 11px;
  color: #909399;
}

.sla__disabled-tip {
  padding: 8px 12px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

.sla__preview-title {
  font-size: 12px;
  color: #909399;
}

.sla__preview-text {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  padding: 4px 0;
}

.sla__preview-text--muted {
  color: #c0c4cc;
}
</style>
