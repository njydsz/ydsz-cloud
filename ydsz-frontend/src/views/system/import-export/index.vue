<!--
  @file 数据导入导出
  @description 统一数据导入导出页面：支持职级费率/内部费率/工时数据三类业务数据的批量导入，流程为「下载模板 → 上传文件 → 系统校验 → 反馈结果」，并提供错误报告下载。对应路由 /system/import-export。
  @module views/system/import-export
-->
<template>
  <div class="import-export-page">
    <el-card class="page-header" shadow="never">
      <h2>{{ t('system.importExport.title') }}</h2>
      <p class="desc">
        {{ t('system.importExport.description') }}
      </p>
    </el-card>

    <!-- 业务类型选择 -->
    <el-row :gutter="20" class="biz-type-row">
      <el-col v-for="item in bizTypes" :key="item.code" :xs="24" :sm="12" :md="8">
        <el-card
          :class="['biz-card', { active: selectedBiz === item.code }]"
          shadow="hover"
          @click="selectedBiz = item.code"
        >
          <div class="biz-icon">
            <el-icon size="40"><component :is="item.icon" /></el-icon>
          </div>
          <div class="biz-info">
            <h3>{{ item.name }}</h3>
            <p>{{ item.description }}</p>
            <el-tag v-for="tp in item.tags" :key="tp" size="small" effect="plain" class="biz-tag">
              {{ tp }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 模板下载区 -->
    <el-card v-if="selectedBiz" class="action-card" shadow="never">
      <template #header>
        <span>{{ t('system.importExport.step1.title') }}</span>
      </template>
      <el-row :gutter="20" align="middle">
        <el-col :span="16">
          <p>
            {{ t('system.importExport.step1.description', { biz: currentBiz?.name }) }}
          </p>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button type="primary" :loading="downloading" @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon>
            {{ t('system.importExport.step1.button') }}
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 文件上传区 -->
    <el-card v-if="selectedBiz" class="action-card" shadow="never">
      <template #header>
        <span>{{ t('system.importExport.step2.title') }}</span>
      </template>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :file-list="fileList"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">{{ t('system.importExport.step2.uploadText') }}<em>{{ t('system.importExport.step2.uploadTextClick') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">
            {{ t('system.importExport.step2.tip') }}
          </div>
        </template>
      </el-upload>
    </el-card>

    <!-- 导入操作 -->
    <el-card v-if="selectedBiz && fileList.length" class="action-card" shadow="never">
      <template #header>
        <span>{{ t('system.importExport.step3.title') }}</span>
      </template>
      <el-row :gutter="20" align="middle">
        <el-col :span="16">
          <p>
            {{ t('system.importExport.step3.selected') }}<strong>{{ fileList[0]?.name }}</strong>
            （{{ formatSize(fileList[0]?.size ?? 0) }}）
          </p>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button :loading="importing" type="success" @click="handleImport">
            <el-icon><Check /></el-icon>
            {{ t('system.importExport.step3.button') }}
          </el-button>
        </el-col>
      </el-row>

      <!-- 进度条 -->
      <div v-if="importProgress > 0" class="progress-section">
        <el-progress
          :percentage="importProgress"
          :status="importProgress === 100 ? 'success' : undefined"
        />
        <p class="progress-text">{{ progressText }}</p>
      </div>
    </el-card>

    <!-- 导入结果 -->
    <el-card v-if="importResult" class="result-card" shadow="never">
      <template #header>
        <span>{{ t('system.importExport.result.title') }}</span>
      </template>
      <el-result
        :icon="importResult.success ? 'success' : 'warning'"
        :title="importResult.success ? t('system.importExport.result.success') : t('system.importExport.result.partialFail')"
        :sub-title="t('system.importExport.result.subTitle', { success: importResult.successCount, fail: importResult.failCount })"
      >
        <template #extra>
          <el-button v-if="importResult.failCount > 0" type="primary" @click="downloadErrorFile">
            {{ t('system.importExport.result.downloadError') }}
          </el-button>
        </template>
      </el-result>

      <!-- 错误详情（P3-1: 已迁移到 VirtualTable，支持虚拟滚动 + 自定义插槽） -->
      <VirtualTable
        v-if="importResult.errors?.length"
        :data="importResult.errors as Record<string, unknown>[]"
        :columns="errorColumns"
        :height="300"
        style="margin-top: 20px"
      >
        <template #col-value="{ row }">
          <el-tag type="danger" size="small">{{ (row as ImportError).value }}</el-tag>
        </template>
      </VirtualTable>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { Download, UploadFilled, Check, Money, Timer, User } from '@element-plus/icons-vue'
import { downloadTemplate, importData, type ImportResult, type ImportError } from '@/api/system/import-export'
import VirtualTable from '@/components/common/VirtualTable.vue'
import type { ColumnConfig } from '@/components/common/VirtualTable.vue'

defineOptions({ name: 'ImportExportIndex' })

const { t } = useI18n()

interface BizType {
  code: 'rate-card' | 'rate-internal' | 'time-entry'
  name: string
  description: string
  icon: typeof Money | typeof Timer | typeof User
  tags: string[]
}

const bizTypes = computed<BizType[]>(() => [
  {
    code: 'rate-card',
    name: t('system.importExport.bizTypes.rateCard.name'),
    description: t('system.importExport.bizTypes.rateCard.description'),
    icon: Money,
    tags: [
      t('system.importExport.bizTypes.rateCard.tags.quote'),
      t('system.importExport.bizTypes.rateCard.tags.level'),
      t('system.importExport.bizTypes.rateCard.tags.project'),
      t('system.importExport.bizTypes.rateCard.tags.customer'),
    ],
  },
  {
    code: 'rate-internal',
    name: t('system.importExport.bizTypes.rateInternal.name'),
    description: t('system.importExport.bizTypes.rateInternal.description'),
    icon: Money,
    tags: [
      t('system.importExport.bizTypes.rateInternal.tags.cost'),
      t('system.importExport.bizTypes.rateInternal.tags.level'),
      t('system.importExport.bizTypes.rateInternal.tags.dept'),
    ],
  },
  {
    code: 'time-entry',
    name: t('system.importExport.bizTypes.timeEntry.name'),
    description: t('system.importExport.bizTypes.timeEntry.description'),
    icon: Timer,
    tags: [
      t('system.importExport.bizTypes.timeEntry.tags.hours'),
      t('system.importExport.bizTypes.timeEntry.tags.project'),
      t('system.importExport.bizTypes.timeEntry.tags.employee'),
    ],
  },
])

/** 当前选中的业务类型 */
const selectedBiz = ref<typeof bizTypes.value[number]['code'] | null>(null)
/** 当前选中的业务类型对象 */
const currentBiz = computed(() => bizTypes.value.find((b) => b.code === selectedBiz.value))
/** 模板下载中状态 */
const downloading = ref(false)
/** 导入执行中状态 */
const importing = ref(false)
/** 导入进度百分比 */
const importProgress = ref(0)
/** 导入进度文案 */
const progressText = ref('')
/** 上传文件列表 */
const fileList = ref<UploadUserFile[]>([])
/** 导入结果数据 */
const importResult = ref<ImportResult | null>(null)

/** 导入错误明细列配置 */
const errorColumns = computed<ColumnConfig[]>(() => [
  { field: 'rowIndex', title: t('system.importExport.columns.rowIndex'), width: 80 },
  { field: 'field', title: t('system.importExport.columns.field'), width: 180 },
  { field: 'message', title: t('system.importExport.columns.message') },
  { field: 'value', title: t('system.importExport.columns.value'), slot: true },
])

/**
 * 文件大小格式化（B / KB / MB）
 * @param bytes 字节数
 * @returns 格式化后的字符串
 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/** 下载当前所选业务类型的导入模板（.xlsx） */
async function handleDownloadTemplate() {
  if (!selectedBiz.value) return
  downloading.value = true
  try {
    await downloadTemplate(selectedBiz.value)
    ElMessage.success(t('system.importExport.messages.templateDownloaded'))
  } catch (e) {
    ElMessage.error(t('system.importExport.messages.templateDownloadFailed', { message: (e as Error).message }))
  } finally {
    downloading.value = false
  }
}

/**
 * 文件选择变化回调：更新文件列表并清空历史导入结果
 * @param _uploadFile 当前变化的文件（未使用）
 * @param uploadFiles 当前全部文件列表
 */
function handleFileChange(_uploadFile: UploadFile, uploadFiles: UploadFiles) {
  // P0 修复: 类型对齐 UploadFile (el-upload 传的是 UploadFile, 但只需要这几个字段)
  fileList.value = uploadFiles
  importResult.value = null
}

/** 文件移除回调：清空文件列表与导入结果 */
function handleFileRemove() {
  fileList.value = []
  importResult.value = null
}

/** 执行导入：上传文件并展示进度，完成后反馈成功/失败明细 */
async function handleImport() {
  if (!selectedBiz.value || !fileList.value.length) return
  importing.value = true
  importProgress.value = 0
  progressText.value = t('system.importExport.messages.parsing')
  try {
    // 模拟进度
    const timer = setInterval(() => {
      if (importProgress.value < 90) {
        importProgress.value += 10
        progressText.value = t('system.importExport.messages.processed', { count: Math.floor(importProgress.value * 12.5) })
      }
    }, 200)

    const rawFile = fileList.value[0]?.raw
    if (!rawFile) {
      ElMessage.error(t('system.importExport.messages.noFile'))
      return
    }
    const resp = await importData(selectedBiz.value, rawFile)
    clearInterval(timer)
    importProgress.value = 100
    progressText.value = t('system.importExport.messages.importComplete')
    const result = (resp as any)?.data ?? resp
    importResult.value = result
    if (result.success) {
      ElMessage.success(t('system.importExport.messages.importSuccess', { count: result.successCount }))
    } else {
      ElMessage.warning(t('system.importExport.messages.importPartialFail', { success: result.successCount, fail: result.failCount }))
    }
  } catch (e) {
    ElMessage.error(t('system.importExport.messages.importFailed', { message: (e as Error).message }))
    importProgress.value = 0
  } finally {
    importing.value = false
  }
}

/** 下载错误报告：将导入失败明细生成 CSV 文件并触发浏览器下载 */
function downloadErrorFile() {
  if (!importResult.value || !importResult.value.errors?.length) return
  // 生成 CSV 错误报告
  const lines = ['行号,字段,错误信息,原值']
  for (const err of importResult.value.errors) {
    lines.push(`${err.rowIndex},${err.field},"${err.message}","${err.value}"`)
  }
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `import-errors-${selectedBiz.value}-${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.import-export-page {
  padding: 20px;

  .page-header {
    margin-bottom: 20px;
    h2 { margin: 0 0 8px; }
    .desc { color: #909399; margin: 0; }
  }

  .biz-type-row {
    margin-bottom: 20px;

    .biz-card {
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;

      &:hover, &.active {
        border-color: #1890ff;
        box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
      }

      @include mobile {
        margin-bottom: 12px;
      }

      .biz-icon {
        margin-right: 16px;
        color: #1890ff;
      }

      .biz-info h3 {
        margin: 0 0 4px;
        font-size: 16px;
      }

      .biz-info p {
        color: #909399;
        margin: 0 0 8px;
        font-size: 13px;
      }

      .biz-tag {
        margin-right: 6px;
      }
    }
  }

  .action-card {
    margin-bottom: 20px;
  }

  .progress-section {
    margin-top: 20px;

    .progress-text {
      color: #909399;
      margin: 8px 0 0;
      font-size: 13px;
    }
  }

  .result-card {
    margin-bottom: 20px;
  }
}
</style>
