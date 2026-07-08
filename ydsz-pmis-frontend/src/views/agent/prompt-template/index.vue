<!--
  @fileoverview Prompt 模板管理页面

  - 关键能力: 模板 CRUD + 版本激活 + 变量预览 + 代码高亮编辑
  - 关联后端: @/api/agent/prompt-template

  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { activate, create, page, remove } from '@/api/agent/prompt-template'
import type { PromptTemplate, PromptTemplateQueryDTO } from '@/api/agent/prompt-template/types'
import type { PageResult } from '@/utils/request'
import { PC } from '@/constants/permissionCodes'

const { t } = useI18n()

const loading = ref(false)
const list = ref<PromptTemplate[]>([])
const total = ref(0)
const query = reactive<PromptTemplateQueryDTO>({
  page: 1,
  size: 20,
  code: '',
  name: '',
  active: undefined,
})

async function loadList() {
  loading.value = true
  try {
    const { data } = await page(query)
    const result = data as PageResult<PromptTemplate> | undefined
    list.value = result?.list ?? result?.records ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.prompt.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadList()
}

function onPageChange(p: number) { query.page = p; loadList() }
function onSizeChange(s: number) { query.size = s; query.page = 1; loadList() }

// ============= 创建/编辑 =============
const dialogVisible = ref(false)
const editForm = reactive({
  code: '',
  name: '',
  content: '',
  description: '',
})
const saving = ref(false)
const isEdit = ref(false)

function openCreateDialog() {
  isEdit.value = false
  editForm.code = ''
  editForm.name = ''
  editForm.content = ''
  editForm.description = ''
  dialogVisible.value = true
}

function openEditDialog(row: PromptTemplate) {
  isEdit.value = true
  editForm.code = row.code
  editForm.name = row.name
  editForm.content = row.content
  editForm.description = row.description || ''
  dialogVisible.value = true
}

async function handleSave() {
  if (!editForm.code || !editForm.name || !editForm.content) {
    ElMessage.warning(t('agent.prompt.messages.required'))
    return
  }
  saving.value = true
  try {
    await create({
      code: editForm.code,
      name: editForm.name,
      content: editForm.content,
      description: editForm.description,
    })
    ElMessage.success(t('agent.prompt.messages.saveSuccess'))
    dialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.prompt.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

// ============= 激活 =============
async function handleActivate(row: PromptTemplate) {
  try {
    await ElMessageBox.confirm(
      t('agent.prompt.messages.activateConfirm', { name: row.name, version: row.version }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await activate(row.id)
    ElMessage.success(t('agent.prompt.messages.activateSuccess'))
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || t('agent.prompt.messages.activateFailed'))
    }
  }
}

// ============= 删除 =============
async function handleDelete(row: PromptTemplate) {
  try {
    await ElMessageBox.confirm(
      t('agent.prompt.messages.deleteConfirm', { name: row.name }),
      t('common.warning'),
      { type: 'warning' },
    )
    await remove(row.id)
    ElMessage.success(t('agent.prompt.messages.deleteSuccess'))
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || t('agent.prompt.messages.deleteFailed'))
    }
  }
}

// ============= 预览 =============
const previewDialogVisible = ref(false)
const previewTemplate = ref<PromptTemplate | null>(null)
const previewVars = ref<Array<{ key: string; value: string }>>([])
const previewResult = ref('')

const detectedVars = computed(() => {
  if (!previewTemplate.value) return []
  const matches = previewTemplate.value.content.match(/\$\{(\w+)\}/g) || []
  return [...new Set(matches.map((m) => m.replace(/\$\{(\w+)\}/, '$1')))]
})

watch(detectedVars, (vars) => {
  previewVars.value = vars.map((v) => ({ key: v, value: '' }))
}, { immediate: true })

function openPreview(row: PromptTemplate) {
  previewTemplate.value = row
  previewResult.value = ''
  previewDialogVisible.value = true
}

function renderPreview() {
  if (!previewTemplate.value) return
  let result = previewTemplate.value.content
  for (const v of previewVars.value) {
    result = result.replaceAll(`\${${v.key}}`, v.value || `\${${v.key}}`)
  }
  previewResult.value = result
}

// ============= 详情 =============
const detailDrawerVisible = ref(false)
const detailRow = ref<PromptTemplate | null>(null)

function openDetail(row: PromptTemplate) {
  detailRow.value = row
  detailDrawerVisible.value = true
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="prompt-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item :label="t('agent.prompt.search.code')">
          <el-input v-model="query.code" clearable :placeholder="t('agent.prompt.search.codePlaceholder')" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('agent.prompt.search.name')">
          <el-input v-model="query.name" clearable :placeholder="t('agent.prompt.search.namePlaceholder')" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('agent.prompt.search.active')">
          <el-select v-model="query.active" clearable style="width: 120px">
            <el-option :value="true" :label="t('agent.prompt.active.yes')" />
            <el-option :value="false" :label="t('agent.prompt.active.no')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="onSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="'Refresh'" @click="loadList">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <div class="toolbar">
        <el-button v-permission="[PC.AGENT_PROMPT_MANAGE]" type="primary" :icon="'Plus'" @click="openCreateDialog">
          {{ t('agent.prompt.buttons.create') }}
        </el-button>
      </div>
      <vxe-table :data="list" :loading="loading" stripe style="margin-top: 12px">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="code" :title="t('agent.prompt.columns.code')" width="200" show-overflow />
        <vxe-column field="name" :title="t('agent.prompt.columns.name')" min-width="200" show-overflow />
        <vxe-column field="version" :title="t('agent.prompt.columns.version')" width="80" />
        <vxe-column field="active" :title="t('agent.prompt.columns.active')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small" effect="dark">
              {{ row.active ? t('agent.prompt.active.yes') : t('agent.prompt.active.no') }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="description" :title="t('agent.prompt.columns.description')" min-width="200" show-overflow />
        <vxe-column field="updatedAt" :title="t('agent.prompt.columns.updatedAt')" width="170" />
        <vxe-column :title="t('agent.prompt.columns.action')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">{{ t('agent.prompt.buttons.detail') }}</el-button>
            <el-button link type="info" size="small" @click="openPreview(row)">{{ t('agent.prompt.buttons.preview') }}</el-button>
            <el-button v-if="!row.active" v-permission="[PC.AGENT_PROMPT_MANAGE]" link type="success" size="small"
              :icon="'Check'" @click="handleActivate(row)">
              {{ t('agent.prompt.buttons.activate') }}
            </el-button>
            <el-button v-permission="[PC.AGENT_PROMPT_MANAGE]" link type="warning" size="small" @click="openEditDialog(row)">
              {{ t('agent.prompt.buttons.edit') }}
            </el-button>
            <el-button v-permission="[PC.AGENT_PROMPT_MANAGE]" link type="danger" size="small" :icon="'Delete'" @click="handleDelete(row)">
              {{ t('agent.prompt.buttons.delete') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('agent.prompt.dialog.editTitle') : t('agent.prompt.dialog.createTitle')" width="800px">
      <el-form label-width="80px">
        <el-form-item :label="t('agent.prompt.form.code')">
          <el-input v-model="editForm.code" :disabled="isEdit" :placeholder="t('agent.prompt.form.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.prompt.form.name')">
          <el-input v-model="editForm.name" :placeholder="t('agent.prompt.form.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.prompt.form.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('agent.prompt.form.content')">
          <el-input v-model="editForm.content" type="textarea" :rows="12"
            style="font-family: monospace; font-size: 13px"
            :placeholder="t('agent.prompt.form.contentPlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-alert type="info" :closable="false">
            {{ t('agent.prompt.form.varHint') }}
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewDialogVisible" :title="t('agent.preview.title')" width="800px">
      <el-form v-if="previewTemplate" label-width="80px">
        <el-form-item :label="t('agent.preview.template')">
          <el-tag>{{ previewTemplate.code }}</el-tag>
          <span style="margin-left: 8px">{{ previewTemplate.name }}</span>
        </el-form-item>
        <el-form-item v-if="detectedVars.length > 0" :label="t('agent.preview.vars')">
          <div v-for="v in previewVars" :key="v.key" class="var-row">
            <el-tag size="small" type="info">{{ v.key }}</el-tag>
            <el-input v-model="v.value" :placeholder="t('agent.preview.varPlaceholder')" style="width: 300px; margin-left: 8px" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'View'" @click="renderPreview">{{ t('agent.preview.buttons.render') }}</el-button>
        </el-form-item>
        <el-form-item v-if="previewResult" :label="t('agent.preview.result')">
          <pre class="preview-result">{{ previewResult }}</pre>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" :title="t('agent.prompt.detail.title')" size="600px">
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('agent.prompt.detail.code')">{{ detailRow.code }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.name')">{{ detailRow.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.version')">{{ detailRow.version }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.active')">
            <el-tag :type="detailRow.active ? 'success' : 'info'" size="small">
              {{ detailRow.active ? t('agent.prompt.active.yes') : t('agent.prompt.active.no') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.description')">{{ detailRow.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.createdAt')">{{ detailRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('agent.prompt.detail.updatedAt')">{{ detailRow.updatedAt }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">{{ t('agent.prompt.detail.content') }}</el-divider>
        <pre class="detail-content">{{ detailRow.content }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.prompt-page {
  .filter-card { margin-bottom: 0; }
  .toolbar { display: flex; justify-content: flex-end; }
  .var-row { display: flex; align-items: center; margin-bottom: 8px; }
  .preview-result {
    background: var(--el-fill-color-light);
    padding: 12px;
    border-radius: 6px;
    font-size: 13px;
    max-height: 400px;
    overflow: auto;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
    border: 1px solid var(--el-border-color-light);
  }
  .detail-content {
    background: var(--el-fill-color-light);
    padding: 12px;
    border-radius: 6px;
    font-size: 13px;
    max-height: 500px;
    overflow: auto;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
    border: 1px solid var(--el-border-color-light);
  }
}
</style>
