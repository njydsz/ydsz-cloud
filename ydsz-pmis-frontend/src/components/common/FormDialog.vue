<!--
  @file 通用表单弹窗
  @description 集中处理 el-dialog 的打开/关闭/提交/loading 状态，避免每个页面重复实现弹窗样板代码
  @module components/common/FormDialog
-->
<script setup lang="ts">
/**
 * 通用表单弹窗
 *
 * 集中处理 el-dialog 的打开/关闭/提交/loading 状态，
 * 避免每个页面重复实现弹窗样板代码。
 */
import { ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  title: string
  width?: string | number
  loading?: boolean
  /** 关闭前确认 */
  beforeClose?: () => boolean | Promise<boolean>
  /** 是否允许点击空白处关闭 */
  closeOnClickModal?: boolean
  /** 是否显示底部 */
  showFooter?: boolean
  /** 是否启用未保存修改检查（关闭前弹确认框，默认 false） */
  checkDirty?: boolean
  /** 表单是否存在未保存修改，配合 checkDirty 使用，由调用方维护 */
  dirty?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  'submit': []
  'cancel': []
  'opened': []
  'closed': []
}>()

const formRef = ref()

defineExpose({
  formRef: formRef as Ref<any>,
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

function onOpened() {
  emit('opened')
}
function onClosed() {
  emit('closed')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width || '640px'"
    :close-on-click-modal="closeOnClickModal !== false"
    :before-close="handleClose"
    @opened="onOpened"
    @closed="onClosed"
  >
    <div v-loading="loading ?? false">
      <slot />
    </div>
    <template v-if="showFooter !== false" #footer>
      <slot name="footer">
        <el-button @click="onCancel">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="onSubmit">{{ t('common.ok') }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>
