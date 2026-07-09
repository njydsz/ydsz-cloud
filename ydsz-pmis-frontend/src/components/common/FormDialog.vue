<!--
  @fileoverview 通用表单弹窗（批次 29-4 增强）
  @description 集中处理 el-dialog 的打开/关闭/提交/loading 状态：
  - Props: modelValue / title / width / loading / beforeClose / closeOnClickModal
            / showFooter / checkDirty / dirty / confirmText / cancelText
            / fullscreen / draggable / autofocus
  - Emits: update:modelValue / submit / cancel / opened / closed
  - Expose: formRef / validate / clearValidate / resetFields / focusFirstInput
  - 支持未保存修改确认、全屏切换、自动聚焦、按钮文案可配、可拖拽
  @module components/common/FormDialog
  @author ydsz-pmis-team
  @since 1.4.0
-->
<script setup lang="ts">
/**
 * 通用表单弹窗
 *
 * 集中处理 el-dialog 的打开/关闭/提交/loading 状态，
 * 避免每个页面重复实现弹窗样板代码。
 *
 * 批次 29-4 增强（对齐企业级表单弹窗规范）：
 * 1. 默认禁止遮罩关闭（closeOnClickModal 默认 false），避免误触丢失数据
 * 2. 全屏切换（fullscreen prop），内容多时可切换全屏编辑
 * 3. 自动聚焦（autofocus prop），打开后自动聚焦第一个可聚焦元素
 * 4. 按钮文案可配（confirmText / cancelText props）
 * 5. 可拖拽（draggable prop，默认 true）
 */
import { ref, computed, nextTick, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox, ElDialog } from 'element-plus'
import { FullScreen, Aim } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'

const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    width?: string | number
    loading?: boolean
    /** 关闭前确认 */
    beforeClose?: () => boolean | Promise<boolean>
    /** 是否允许点击空白处关闭（默认 false，避免误触丢失数据） */
    closeOnClickModal?: boolean
    /** 是否显示底部 */
    showFooter?: boolean
    /** 是否启用未保存修改检查（关闭前弹确认框，默认 false） */
    checkDirty?: boolean
    /** 表单是否存在未保存修改，配合 checkDirty 使用，由调用方维护 */
    dirty?: boolean
    /** 确认按钮文案（默认取 common.ok） */
    confirmText?: string
    /** 取消按钮文案（默认取 common.cancel） */
    cancelText?: string
    /** 是否显示全屏切换按钮（批次 29-4） */
    fullscreen?: boolean
    /** 是否默认全屏（批次 29-4） */
    defaultFullscreen?: boolean
    /** 是否可拖拽（批次 29-4，默认 true） */
    draggable?: boolean
    /** 是否自动聚焦第一个可聚焦元素（批次 29-4，默认 true） */
    autofocus?: boolean
  }>(),
  {
    closeOnClickModal: false,
    confirmText: '',
    cancelText: '',
    fullscreen: false,
    defaultFullscreen: false,
    draggable: true,
    autofocus: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  'submit': []
  'cancel': []
  'opened': []
  'closed': []
}>()

const formRef = ref<FormInstance>()
const dialogRef = ref<InstanceType<typeof ElDialog>>()

/** 当前是否全屏（批次 29-4） */
const isFullscreen = ref(props.defaultFullscreen)

watch(() => props.modelValue, (v) => {
  if (v) {
    // 打开时重置全屏状态为默认值
    isFullscreen.value = props.defaultFullscreen
  }
})

/** 切换全屏 */
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
}

/** 自动聚焦第一个可聚焦元素（批次 29-4：UX-C4） */
async function focusFirstInput() {
  if (!props.autofocus) return
  await nextTick()
  const dialogEl = dialogRef.value?.$el as HTMLElement | undefined
  if (!dialogEl) return
  // 查找第一个可见的可聚焦元素（input / textarea / select）
  const focusable = dialogEl.querySelector<HTMLElement>(
    'input:not([type="hidden"]):not([disabled]):not([readonly]), textarea:not([disabled]):not([readonly]), select:not([disabled])',
  )
  if (focusable) {
    focusable.focus()
    // 如果是 input/textarea，将光标移到末尾
    if (focusable instanceof HTMLInputElement || focusable instanceof HTMLTextAreaElement) {
      const len = focusable.value.length
      focusable.setSelectionRange(len, len)
    }
  }
}

defineExpose({
  formRef: formRef as Ref<FormInstance | undefined>,
  /** 触发表单校验 */
  validate: async () => {
    if (!formRef.value) return true
    try {
      await formRef.value.validate()
      return true
    } catch {
      return false
    }
  },
  /** 清除校验 */
  clearValidate: () => formRef.value?.clearValidate?.(),
  /** 重置表单 */
  resetFields: () => formRef.value?.resetFields?.(),
  /** 手动触发聚焦第一个输入框 */
  focusFirstInput,
})

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => (visible.value = v))
watch(visible, (v) => emit('update:modelValue', v))

async function handleClose(done: () => void) {
  // 启用 dirty check 且存在未保存修改时，弹出确认框
  if (props.checkDirty && props.dirty) {
    try {
      await ElMessageBox.confirm(
        t('common.msg_discard_confirm'),
        t('common.msg_unsaved_changes'),
        {
          confirmButtonText: t('common.ok'),
          cancelButtonText: t('common.cancel'),
          type: 'warning',
        },
      )
    } catch {
      // 用户取消，保持弹窗打开
      return
    }
  }
  if (props.beforeClose) {
    const ok = await props.beforeClose()
    if (!ok) return
  }
  done()
}

function onSubmit() {
  emit('submit')
}

function onCancel() {
  emit('cancel')
}

async function onOpened() {
  emit('opened')
  // 批次 29-4：弹窗打开后自动聚焦第一个输入框
  await focusFirstInput()
}

function onClosed() {
  emit('closed')
}

/** 确认按钮文案（优先使用 prop，回退 i18n） */
const resolvedConfirmText = computed(() => props.confirmText || t('common.ok'))
/** 取消按钮文案（优先使用 prop，回退 i18n） */
const resolvedCancelText = computed(() => props.cancelText || t('common.cancel'))
</script>

<template>
  <el-dialog
    ref="dialogRef"
    v-model="visible"
    :title="title"
    :width="width || '640px'"
    :close-on-click-modal="closeOnClickModal"
    :before-close="handleClose"
    :fullscreen="isFullscreen"
    :draggable="draggable"
    @opened="onOpened"
    @closed="onClosed"
  >
    <template #header>
      <div class="form-dialog__header">
        <span class="form-dialog__title">{{ title }}</span>
        <el-button
          v-if="fullscreen"
          link
          :icon="isFullscreen ? Aim : FullScreen"
          :aria-label="isFullscreen ? t('common.aria.exitFullscreen') : t('common.aria.enterFullscreen')"
          class="form-dialog__fullscreen-btn"
          @click="toggleFullscreen"
        />
      </div>
    </template>
    <div v-loading="loading ?? false">
      <slot />
    </div>
    <template v-if="showFooter !== false" #footer>
      <slot name="footer">
        <el-button @click="onCancel">{{ resolvedCancelText }}</el-button>
        <el-button type="primary" :loading="loading" @click="onSubmit">{{ resolvedConfirmText }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.form-dialog {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-right: $spacing-md;
    margin-right: -$spacing-md;
  }

  &__title {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
  }

  &__fullscreen-btn {
    color: $text-secondary;
    transition: color 0.2s;

    &:hover {
      color: $primary-color;
    }
  }
}
</style>
