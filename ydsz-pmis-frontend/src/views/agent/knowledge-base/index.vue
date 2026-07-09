<!--
  @fileoverview 知识库管理页面
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  create,
  listDocuments,
  page,
  search,
  uploadDocument,
} from '@/api/agent/knowledge-base'
import type { AgentDocument, KnowledgeBase, RetrievedChunk } from '@/api/agent/knowledge-base/types'
import { PC } from '@/constants/permissionCodes'
import { useWebSocket } from '@/composables/useWebSocket'
import type { PageResult } from '@/utils/request'

const { t } = useI18n()
const { on: wsOn, off: wsOff } = useWebSocket()

/** 上传进度信息 */
interface UploadProgress {
  documentId: string
  documentName: string
  status: 'UPLOADING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'READY' | 'FAILED'
  progress: number
  step: string
  message?: string
}

// ============= 知识库列表 =============
const loading = ref(false)
const list = ref<KnowledgeBase[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

async function loadList() {
  loading.value = true
  try {
    const { data } = await page(pageNo.value, pageSize.value)
    const result = data as PageResult<KnowledgeBase> | undefined
    list.value = result?.list ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.kb.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) { pageNo.value = p; loadList() }
function onSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; loadList() }

// ============= 创建知识库 =============
const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  embeddingModel: 'text-embedding-v2',
  chunkSize: 500,
  chunkOverlap: 50,
})
const creating = ref(false)

function openCreateDialog() {
  createForm.name = ''
  createForm.description = ''
  createForm.embeddingModel = 'text-embedding-v2'
  createForm.chunkSize = 500
  createForm.chunkOverlap = 50
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning(t('agent.kb.messages.nameRequired'))
    return
  }
  creating.value = true
  try {
    await create({ ...createForm })
    ElMessage.success(t('agent.kb.messages.createSuccess'))
    createDialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.kb.messages.createFailed'))
  } finally {
    creating.value = false
  }
}

// ============= 知识库详情面板 =============
const detailVisible = ref(false)
const currentKb = ref<KnowledgeBase | null>(null)
const documents = ref<AgentDocument[]>([])
const docLoading = ref(false)
const uploadDialogVisible = ref(false)
const uploadForm = reactive({ name: '', sourceType: 'TEXT', content: '' })
const uploading = ref(false)

// ============= 上传进度追踪 =============
/** 当前上传进度 */
const uploadProgress = ref<UploadProgress | null>(null)
/** 正在处理中的文档进度 Map（documentId -> progress） */
const processingDocs = ref<Map<string, UploadProgress>>(new Map())

/** WebSocket 消息处理函数引用（用于组件卸载时移除） */
const wsProgressHandler = (data: unknown) => {
  const progress = data as UploadProgress
  if (!progress?.documentId) return
  processingDocs.value.set(progress.documentId, progress)
  // 如果是当前上传对话框中的文档，更新进度条
  if (uploadProgress.value && progress.documentId === uploadProgress.value.documentId) {
    uploadProgress.value = progress
  }
  // 当状态为 READY 或 FAILED 时，刷新文档列表
  if (progress.status === 'READY' || progress.status === 'FAILED') {
    if (currentKb.value) {
      loadDocuments(currentKb.value.id)
    }
    // 延迟清除处理状态
    setTimeout(() => {
      processingDocs.value.delete(progress.documentId)
    }, 3000)
  }
}

// 注册 WebSocket 进度推送监听
wsOn('KB_UPLOAD_PROGRESS', wsProgressHandler)

// 检索
const searchQuery = ref('')
const searchResults = ref<RetrievedChunk[]>([])
const searching = ref(false)

async function openDetail(row: KnowledgeBase) {
  currentKb.value = row
  detailVisible.value = true
  await loadDocuments(row.id)
}

async function loadDocuments(kbId: string) {
  docLoading.value = true
  try {
    const { data } = await listDocuments(kbId)
    documents.value = (data as AgentDocument[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.kb.messages.loadFailed'))
  } finally {
    docLoading.value = false
  }
}

function openUploadDialog() {
  uploadForm.name = ''
  uploadForm.sourceType = 'TEXT'
  uploadForm.content = ''
  uploadDialogVisible.value = true
}

async function handleUpload() {
  if (!uploadForm.name || !uploadForm.content) {
    ElMessage.warning(t('agent.kb.messages.uploadRequired'))
    return
  }
  if (!currentKb.value) return
  uploading.value = true
  uploadProgress.value = {
    documentId: '',
    documentName: uploadForm.name,
    status: 'UPLOADING',
    progress: 0,
    step: t('agent.kb.upload.steps.uploading'),
  }
  try {
    const { data } = await uploadDocument(currentKb.value.id, { ...uploadForm })
    const docId = (data as any)?.id || ''
    if (docId) {
      uploadProgress.value.documentId = docId
    }
    ElMessage.success(t('agent.kb.messages.uploadSuccess'))
    // 不关闭对话框，等待 WebSocket 进度推送完成后再关闭
    // 如果 5 秒内没有收到 WebSocket 进度，自动关闭对话框
    setTimeout(() => {
      if (uploadProgress.value && uploadProgress.value.status !== 'READY' && uploadProgress.value.status !== 'FAILED') {
        uploadDialogVisible.value = false
        uploadProgress.value = null
        loadDocuments(currentKb.value!.id)
      }
    }, 5000)
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.kb.messages.uploadFailed'))
    uploadProgress.value = null
  } finally {
    uploading.value = false
  }
}

async function handleSearch() {
  if (!searchQuery.value || !currentKb.value) return
  searching.value = true
  searchResults.value = []
  try {
    const { data } = await search(currentKb.value.id, searchQuery.value)
    searchResults.value = (data as RetrievedChunk[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.kb.messages.searchFailed'))
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  loadList()
})

// 组件卸载时移除 WebSocket 监听
import { onBeforeUnmount } from 'vue'
onBeforeUnmount(() => {
  wsOff('KB_UPLOAD_PROGRESS', wsProgressHandler)
})
</script>

<template>
  <div class="kb-page">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-button v-permission="[PC.AGENT_KB_MANAGE]" type="primary" :icon="'Plus'" @click="openCreateDialog">
          {{ t('agent.kb.buttons.create') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="loadList">{{ t('agent.kb.buttons.refresh') }}</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <vxe-table :data="list" :loading="loading" stripe>
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="name" :title="t('agent.kb.columns.name')" min-width="200" show-overflow />
        <vxe-column field="description" :title="t('agent.kb.columns.description')" min-width="200" show-overflow />
        <vxe-column field="embeddingModel" :title="t('agent.kb.columns.embeddingModel')" width="160" />
        <vxe-column field="documentCount" :title="t('agent.kb.columns.docCount')" width="100" />
        <vxe-column field="chunkSize" :title="t('agent.kb.columns.chunkSize')" width="100" />
        <vxe-column field="createdAt" :title="t('agent.kb.columns.createdAt')" width="170" />
        <vxe-column :title="t('agent.kb.columns.action')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="'FolderOpened'" @click="openDetail(row)">
              {{ t('agent.kb.buttons.detail') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('agent.kb.create.title')" width="600px">
      <el-form label-width="100px">
        <el-form-item :label="t('agent.kb.create.name')">
          <el-input v-model="createForm.name" :placeholder="t('agent.kb.create.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.kb.create.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('agent.kb.create.embeddingModel')">
          <el-select v-model="createForm.embeddingModel" style="width: 100%">
            <el-option value="text-embedding-v2" label="DashScope text-embedding-v2" />
            <el-option value="bge-large-zh" label="BGE-large-zh" />
            <el-option value="mock" label="Mock (开发用)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.kb.create.chunkSize')">
          <el-input-number v-model="createForm.chunkSize" :min="100" :max="2000" :step="100" />
        </el-form-item>
        <el-form-item :label="t('agent.kb.create.chunkOverlap')">
          <el-input-number v-model="createForm.chunkOverlap" :min="0" :max="500" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="t('agent.kb.detail.title')" size="700px">
      <template v-if="currentKb">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('agent.kb.detail.name')">{{ currentKb.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.kb.detail.description')">{{ currentKb.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.kb.detail.embeddingModel')">{{ currentKb.embeddingModel || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.kb.detail.chunkSize')">{{ currentKb.chunkSize || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.kb.detail.docCount')">{{ currentKb.documentCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>

        <!-- 检索测试 -->
        <el-divider content-position="left">{{ t('agent.kb.detail.searchTest') }}</el-divider>
        <el-input v-model="searchQuery" :placeholder="t('agent.kb.detail.searchPlaceholder')" style="width: 70%">
          <template #append>
            <el-button :icon="'Search'" :loading="searching" @click="handleSearch" />
          </template>
        </el-input>

        <div v-if="searchResults.length > 0" class="search-results">
          <div v-for="(chunk, idx) in searchResults" :key="idx" class="result-chunk">
            <div class="chunk-header">
              <span v-if="chunk.documentName" class="doc-name">{{ chunk.documentName }}</span>
              <el-tag size="small" type="success">Score: {{ Number(chunk.score).toFixed(4) }}</el-tag>
            </div>
            <div class="chunk-content">{{ chunk.content }}</div>
          </div>
        </div>

        <!-- 文档列表 -->
        <el-divider content-position="left">
          <div style="display: flex; align-items: center; gap: 8px">
            {{ t('agent.kb.detail.documents') }}
            <el-button v-permission="[PC.AGENT_KB_MANAGE]" type="primary" size="small" :icon="'Upload'"
              @click="openUploadDialog">
              {{ t('agent.kb.buttons.upload') }}
            </el-button>
          </div>
        </el-divider>
        <vxe-table :data="documents" :loading="docLoading" stripe size="small">
          <vxe-column type="seq" width="56" title="#" />
          <vxe-column field="name" :title="t('agent.kb.docColumns.name')" min-width="200" show-overflow />
          <vxe-column field="sourceType" :title="t('agent.kb.docColumns.sourceType')" width="100" />
          <vxe-column field="chunkCount" :title="t('agent.kb.docColumns.chunkCount')" width="100" />
          <vxe-column field="status" :title="t('agent.kb.docColumns.status')" width="80" />
          <vxe-column field="createdAt" :title="t('agent.kb.docColumns.createdAt')" width="170" />
        </vxe-table>
      </template>
    </el-drawer>

    <!-- 上传文档对话框 -->
    <el-dialog v-model="uploadDialogVisible" :title="t('agent.kb.upload.title')" width="700px" :close-on-click-modal="!uploading && !uploadProgress">
      <el-form label-width="80px">
        <el-form-item :label="t('agent.kb.upload.name')">
          <el-input v-model="uploadForm.name" :placeholder="t('agent.kb.upload.namePlaceholder')" :disabled="uploading" />
        </el-form-item>
        <el-form-item :label="t('agent.kb.upload.sourceType')">
          <el-select v-model="uploadForm.sourceType" style="width: 100%" :disabled="uploading">
            <el-option value="TEXT" label="文本" />
            <el-option value="URL" label="URL" />
            <el-option value="FILE" label="文件" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('agent.kb.upload.content')">
          <el-input v-model="uploadForm.content" type="textarea" :rows="10"
            :placeholder="t('agent.kb.upload.contentPlaceholder')" style="font-family: monospace; font-size: 13px" :disabled="uploading" />
        </el-form-item>
      </el-form>
      <!-- 上传进度条 -->
      <div v-if="uploadProgress" class="upload-progress">
        <div class="progress-header">
          <el-tag :type="uploadProgress.status === 'FAILED' ? 'danger' : uploadProgress.status === 'READY' ? 'success' : 'warning'" size="small">
            {{ uploadProgress.status }}
          </el-tag>
          <span class="progress-step">{{ uploadProgress.step }}</span>
          <span class="progress-percent">{{ uploadProgress.progress }}%</span>
        </div>
        <el-progress
          :percentage="uploadProgress.progress"
          :status="uploadProgress.status === 'FAILED' ? 'exception' : uploadProgress.status === 'READY' ? 'success' : undefined"
          :stroke-width="10"
          :duration="0.3"
        />
        <p v-if="uploadProgress.message" class="progress-message">{{ uploadProgress.message }}</p>
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false" :disabled="uploading">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload" :disabled="uploading">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.kb-page {
  .toolbar-card { margin-bottom: 0; }
  .toolbar { display: flex; justify-content: flex-end; gap: 8px; }
  .upload-progress {
    margin-top: 16px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
    .progress-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      .progress-step {
        font-size: 13px;
        color: var(--el-text-color-regular);
      }
      .progress-percent {
        margin-left: auto;
        font-size: 13px;
        font-weight: 600;
        color: var(--el-color-primary);
      }
    }
    .progress-message {
      margin-top: 8px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
  .search-results { margin-top: 12px; }
  .result-chunk {
    background: var(--el-fill-color-light);
    border-radius: 6px;
    padding: 10px;
    margin-bottom: 8px;
    border-left: 3px solid var(--el-color-primary);
    .chunk-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
      .doc-name { font-size: 12px; color: var(--el-text-color-secondary); }
    }
    .chunk-content {
      font-size: 13px;
      line-height: 1.6;
      color: var(--el-text-color-primary);
    }
  }
}
</style>
