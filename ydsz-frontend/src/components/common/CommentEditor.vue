<!--
  @fileoverview 意见编辑器（常用语 / @人 / 图片附件 一体化）
  @description P1-9 审批意见增强编辑器：
  - Props: modelValue/placeholder/rows/disabled/readonly/maxlength
            /enableImage/enableMention/enableQuickPhrases 等
  - Emits: update:modelValue / submit / cancel / change / exceed
  - 场景: 流程审批 / 转办 / 委派 / 抄送等需要填写意见的场景
  @module components/common/CommentEditor
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 意见编辑器 - 常用语 / @人 / 图片附件 一体化
 */
import { ref, computed, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  ChatLineSquare,
  Promotion,
  Picture,
  Delete,
  View,
} from '@element-plus/icons-vue'
import { UserPicker } from '@/components/common'
import { onKeyActivate } from '@/composables/useKeyboardA11y'

const { t } = useI18n()

/** 附件项 */
export interface CommentAttachment {
  /** 唯一 key */
  uid: string
  /** 远端文件 ID（上传后由后端返回） */
  fileId?: string | number
  /** 文件名 */
  name: string
  /** 文件 URL（用于预览） */
  url: string
  /** 文件大小（字节） */
  size?: number
  /** mime type */
  type?: string
}

/** @ 提及项 */
export interface CommentMention {
  /** 用户 ID */
  userId: string
  /** 用户名（显示用） */
  name: string
}

const props = withDefaults(
  defineProps<{
    /** v-model 绑定值（纯文本，图片引用以 [图片:name](url) 形式内联） */
    modelValue?: string
    /** 占位文案 */
    placeholder?: string
    /** 行数 */
    rows?: number
    /** 是否禁用 */
    disabled?: boolean
    /** 是否只读 */
    readonly?: boolean
    /** 最大字符数（0 表示不限制） */
    maxlength?: number
    /** 是否启用图片上传 */
    enableImage?: boolean
    /** 是否启用 @人 */
    enableMention?: boolean
    /** 是否启用常用语 */
    enablePhrases?: boolean
    /** 自定义常用语列表（缺省使用系统默认） */
    phrases?: string[]
    /** 单文件大小限制（MB） */
    maxSize?: number
    /** 允许的图片 mime */
    accept?: string
    /** 附件 v-model */
    attachments?: CommentAttachment[]
    /** @ 提及列表 v-model（v-model:mentions） */
    mentions?: CommentMention[]
    /** 自定义上传函数，返回 Promise<{ fileId, url, name, size, type }> */
    customUpload?: (file: File) => Promise<{
      fileId: string | number
      url: string
      name: string
      size: number
      type: string
    }>
  }>(),
  {
    modelValue: '',
    placeholder: '',
    rows: 4,
    disabled: false,
    readonly: false,
    maxlength: 1000,
    enableImage: true,
    enableMention: true,
    enablePhrases: true,
    phrases: () => [],
    maxSize: 10,
    accept: 'image/png,image/jpeg,image/gif,image/webp',
    attachments: () => [],
    mentions: () => [],
    customUpload: undefined,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'update:attachments', v: CommentAttachment[]): void
  (e: 'update:mentions', v: CommentMention[]): void
  (e: 'mention', v: CommentMention): void
  (e: 'upload-success', v: CommentAttachment): void
  (e: 'upload-error', err: Error): void
}>()

// ===========================================
// 状态
// ===========================================
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const internalAttachments = ref<CommentAttachment[]>([])
const internalMentions = ref<CommentMention[]>([])

watch(
  () => props.attachments,
  (val) => {
    if (val && val.length >= 0) internalAttachments.value = [...val]
  },
  { immediate: true, deep: true },
)

watch(
  () => props.mentions,
  (val) => {
    if (val && val.length >= 0) internalMentions.value = [...val]
  },
  { immediate: true, deep: true },
)

const charCount = computed(() => (props.modelValue || '').length)

const placeholderText = computed(() => props.placeholder || t('common.comment.placeholder'))

const phraseList = computed(() => {
  if (props.phrases && props.phrases.length > 0) return props.phrases
  return [
    t('workflow.approval.phrases.agree'),
    t('workflow.approval.phrases.agreeProceed'),
    t('workflow.approval.phrases.agreeRisk'),
    t('workflow.approval.phrases.supplementLater'),
    t('workflow.approval.phrases.modifyResubmit'),
    t('workflow.approval.phrases.rejectInsufficient'),
    t('workflow.approval.phrases.acknowledged'),
  ]
})

// ===========================================
// 内部写入：在光标处插入文本
// ===========================================
function insertAtCursor(text: string) {
  const el = textareaRef.value
  if (!el) {
    emit('update:modelValue', (props.modelValue || '') + text)
    return
  }
  const start = el.selectionStart ?? (props.modelValue || '').length
  const end = el.selectionEnd ?? start
  const before = (props.modelValue || '').slice(0, start)
  const after = (props.modelValue || '').slice(end)
  const next = before + text + after
  emit('update:modelValue', next)
  nextTick(() => {
    el.focus()
    const pos = start + text.length
    el.setSelectionRange(pos, pos)
  })
}

function onInput(value: string) {
  emit('update:modelValue', value)
}

// ===========================================
// 常用语
// ===========================================
const phrasePopover = ref(false)
function pickPhrase(p: string) {
  insertAtCursor(p)
  phrasePopover.value = false
}

// ===========================================
// @ 人
// ===========================================
const mentionVisible = ref(false)
const mentionSearch = ref('')
function onMentionPick(v: unknown) {
  if (!v || typeof v !== 'object') return
  const u = v as { id: number; realName?: string; username?: string }
  const name = u.realName || u.username || ''
  if (!name || !u.id) return
  insertAtCursor(`@${name} `)
  const mention: CommentMention = { userId: u.id, name }
  if (!internalMentions.value.some((m) => m.userId === mention.userId)) {
    internalMentions.value = [...internalMentions.value, mention]
    emit('update:mentions', internalMentions.value)
  }
  emit('mention', mention)
  mentionVisible.value = false
  mentionSearch.value = ''
}

function removeMention(m: CommentMention) {
  internalMentions.value = internalMentions.value.filter((x) => x.userId !== m.userId)
  emit('update:mentions', internalMentions.value)
  // 同步移除文本中的 @xxx
  const re = new RegExp(`@${m.name}\\s?`, 'g')
  const v = (props.modelValue || '').replace(re, '')
  emit('update:modelValue', v)
}

// ===========================================
// 图片上传
// ===========================================
const uploading = ref(false)

function defaultUpload(file: File): Promise<{
  fileId: string | number
  url: string
  name: string
  size: number
  type: string
}> {
  // 兜底：转 dataURL（仅用于本地预览）
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () =>
      resolve({
        fileId: `local-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        url: reader.result as string,
        name: file.name,
        size: file.size,
        type: file.type,
      })
    reader.onerror = () => reject(new Error(t('common.comment.fileReadFailed')))
    reader.readAsDataURL(file)
  })
}

async function handleFile(file: File) {
  if (props.maxSize > 0 && file.size > props.maxSize * 1024 * 1024) {
    ElMessage.error(t('common.comment.fileTooLarge', { size: props.maxSize }))
    return
  }
  uploading.value = true
  try {
    const uploadFn = props.customUpload || defaultUpload
    const result = await uploadFn(file)
    const att: CommentAttachment = {
      uid: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      fileId: result.fileId,
      name: result.name,
      url: result.url,
      size: result.size,
      type: result.type,
    }
    internalAttachments.value = [...internalAttachments.value, att]
    emit('update:attachments', internalAttachments.value)
    // 内联插入 markdown 图片引用
    insertAtCursor(`![${att.name}](${att.url})`)
    emit('upload-success', att)
  } catch (e) {
    const err = e as Error
    ElMessage.error(t('common.comment.uploadFailed', { message: err.message }))
    emit('upload-error', err)
  } finally {
    uploading.value = false
  }
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return
  for (let i = 0; i < files.length; i++) {
    handleFile(files[i])
  }
  // 重置 input 以便下次选择同一文件
  target.value = ''
}

function removeAttachment(att: CommentAttachment) {
  internalAttachments.value = internalAttachments.value.filter((x) => x.uid !== att.uid)
  emit('update:attachments', internalAttachments.value)
  // 同步移除文本中的图片引用
  const re = new RegExp(`!\\[[^\\]]*\\]\\(${escapeRegex(att.url)}\\)`, 'g')
  const v = (props.modelValue || '').replace(re, '')
  emit('update:modelValue', v)
}

function escapeRegex(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')
function previewAttachment(att: CommentAttachment) {
  previewUrl.value = att.url
  previewName.value = att.name
  previewVisible.value = true
}

function sizeLabel(bytes?: number) {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function clearAll() {
  ElMessageBox.confirm(t('common.comment.clearConfirm'), t('common.comment.clearTitle'), {
    type: 'warning',
  })
    .then(() => {
      emit('update:modelValue', '')
      internalAttachments.value = []
      internalMentions.value = []
      emit('update:attachments', internalAttachments.value)
      emit('update:mentions', internalMentions.value)
    })
    .catch(() => {})
}

defineExpose({
  insertAtCursor,
  clearAll,
  focus: () => textareaRef.value?.focus(),
})
</script>

<template>
  <div class="comment-editor" :class="{ 'is-disabled': disabled, 'is-readonly': readonly }">
    <!-- 工具栏 -->
    <div class="comment-toolbar" v-if="!readonly">
      <!-- 常用语 -->
      <el-popover
        v-if="enablePhrases"
        v-model:visible="phrasePopover"
        placement="bottom-start"
        :width="220"
        trigger="click"
      >
        <template #reference>
          <el-button size="small" :disabled="disabled">
            <el-icon><ChatLineSquare /></el-icon>
            {{ t('common.comment.phrases') }}
          </el-button>
        </template>
        <div class="phrase-list">
          <div
            v-for="p in phraseList"
            :key="p"
            class="phrase-item"
            @click="pickPhrase(p)"
          >
            {{ p }}
          </div>
        </div>
      </el-popover>

      <!-- @ 人 -->
      <el-popover
        v-if="enableMention"
        v-model:visible="mentionVisible"
        placement="bottom-start"
        :width="320"
        trigger="click"
      >
        <template #reference>
          <el-button size="small" :disabled="disabled">
            <el-icon><Promotion /></el-icon>
            {{ t('common.comment.mention') }}
          </el-button>
        </template>
        <div class="mention-picker">
          <UserPicker
            v-model="mentionSearch"
            :placeholder="t('common.comment.searchUserPlaceholder')"
            :show-dialog="false"
            @change="onMentionPick"
          />
        </div>
      </el-popover>

      <!-- 图片上传 -->
      <template v-if="enableImage">
        <el-button size="small" :disabled="disabled || uploading" :loading="uploading">
          <label class="upload-trigger">
            <el-icon><Picture /></el-icon>
            {{ t('common.comment.image') }}
            <input
              type="file"
              :accept="accept"
              :multiple="true"
              :disabled="disabled"
              style="display: none"
              @change="onFileChange"
            />
          </label>
        </el-button>
      </template>

      <div class="toolbar-spacer" />

      <el-button
        v-if="modelValue || internalAttachments.length || internalMentions.length"
        size="small"
        text
        type="danger"
        :disabled="disabled"
        @click="clearAll"
      >
        <el-icon><Delete /></el-icon>
        {{ t('common.comment.clear') }}
      </el-button>
    </div>

    <!-- 文本编辑区 -->
    <el-input
      ref="textareaRef"
      :model-value="modelValue"
      type="textarea"
      :rows="rows"
      :placeholder="placeholderText"
      :disabled="disabled"
      :readonly="readonly"
      :maxlength="maxlength > 0 ? maxlength : undefined"
      :show-word-limit="false"
      resize="vertical"
      class="comment-textarea"
      @input="onInput"
    >
      <template v-if="$slots.prefix" #prefix>
        <slot name="prefix" />
      </template>
    </el-input>

    <!-- 字数统计 -->
    <div v-if="maxlength > 0" class="char-counter">
      {{ charCount }} / {{ maxlength }}
    </div>

    <!-- 提及列表 -->
    <div v-if="enableMention && internalMentions.length > 0" class="mention-bar">
      <span class="mention-bar__label">{{ t('common.comment.mentioned') }}</span>
      <el-tag
        v-for="m in internalMentions"
        :key="m.userId"
        closable
        size="small"
        class="mention-tag"
        @close="removeMention(m)"
      >
        @{{ m.name }}
      </el-tag>
    </div>

    <!-- 附件列表 -->
    <div v-if="enableImage && internalAttachments.length > 0" class="attachment-bar">
      <div class="attachment-bar__title">{{ t('common.comment.attachments', { n: internalAttachments.length }) }}</div>
      <div class="attachment-list">
        <div
          v-for="att in internalAttachments"
          :key="att.uid"
          class="attachment-item"
        >
          <div
            class="attachment-thumb"
            role="button"
            tabindex="0"
            :aria-label="`预览附件 ${att.name}`"
            @click="previewAttachment(att)"
            @keydown="onKeyActivate(() => previewAttachment(att))"
          >
            <img v-if="att.type?.startsWith('image/')" :src="att.url" :alt="att.name" loading="lazy" />
            <el-icon v-else><Picture /></el-icon>
          </div>
          <div class="attachment-meta">
            <div class="attachment-name" :title="att.name">{{ att.name }}</div>
            <div class="attachment-size">{{ sizeLabel(att.size) }}</div>
          </div>
          <div class="attachment-actions">
            <el-button size="small" text @click="previewAttachment(att)">
              <el-icon><View /></el-icon>
            </el-button>
            <el-button
              v-if="!readonly"
              size="small"
              text
              type="danger"
              @click="removeAttachment(att)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewName"
      width="600px"
      append-to-body
    >
      <img v-if="previewUrl" :src="previewUrl" :alt="previewName" class="preview-image" loading="lazy" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.comment-editor {
  position: relative;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  transition: border-color 0.2s;

  &:hover:not(.is-disabled):not(.is-readonly) {
    border-color: #c0c4cc;
  }

  &:focus-within {
    border-color: #409eff;
  }

  &.is-disabled {
    background: #f5f7fa;
    border-color: #e4e7ed;
  }

  &.is-readonly {
    background: #fafbfc;
  }
}

.comment-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  border-bottom: 1px solid #ebeef5;
  background: #fafbfc;
  border-radius: 4px 4px 0 0;
}

.toolbar-spacer {
  flex: 1;
}

.upload-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.comment-textarea {
  :deep(.el-textarea__inner) {
    border: none !important;
    box-shadow: none !important;
    border-radius: 0;
    padding: 8px 10px;
    resize: none;
  }
}

.char-counter {
  text-align: right;
  font-size: 12px;
  color: #94a3b8;
  padding: 0 10px 6px;
}

.phrase-list {
  max-height: 240px;
  overflow-y: auto;
}

.phrase-item {
  padding: 6px 10px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 3px;
  transition: background 0.15s;

  &:hover {
    background: #ecf5ff;
    color: #409eff;
  }
}

.mention-picker {
  padding: 4px;
}

.mention-bar {
  padding: 6px 10px;
  border-top: 1px dashed #ebeef5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;

  &__label {
    font-size: 12px;
    color: #64748b;
  }
}

.mention-tag {
  margin: 0;
}

.attachment-bar {
  border-top: 1px solid #ebeef5;
  padding: 8px 10px;
  background: #fafbfc;

  &__title {
    font-size: 12px;
    color: #64748b;
    margin-bottom: 6px;
  }
}

.attachment-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 6px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 6px;
  font-size: 12px;
}

.attachment-thumb {
  width: 32px;
  height: 32px;
  border-radius: 3px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  cursor: pointer;
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.attachment-meta {
  flex: 1;
  min-width: 0;
}

.attachment-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #1e293b;
}

.attachment-size {
  font-size: 11px;
  color: #94a3b8;
}

.attachment-actions {
  display: flex;
  gap: 2px;
}

.preview-image {
  width: 100%;
  display: block;
  border-radius: 4px;
}
</style>
