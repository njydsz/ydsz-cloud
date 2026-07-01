<template>
  <div class="import-export-page">
    <el-card class="page-header" shadow="never">
      <h2>统一数据导入导出</h2>
      <p class="desc">
        支持 PMIS 3 类核心业务数据批量导入：职级费率 / 内部费率 / 工时数据。
        下载空白模板 → 填写数据 → 上传文件 → 系统校验 → 反馈结果。
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
            <el-tag v-for="t in item.tags" :key="t" size="small" effect="plain" class="biz-tag">
              {{ t }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 模板下载区 -->
    <el-card v-if="selectedBiz" class="action-card" shadow="never">
      <template #header>
        <span>第一步：下载模板</span>
      </template>
      <el-row :gutter="20" align="middle">
        <el-col :span="16">
          <p>
            下载
            <strong>{{ currentBiz?.name }}</strong>
            模板（.xlsx），模板包含表头 + 1 行样例数据 + 数据格式说明。
          </p>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button type="primary" :loading="downloading" @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon>
            下载模板
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 文件上传区 -->
    <el-card v-if="selectedBiz" class="action-card" shadow="never">
      <template #header>
        <span>第二步：上传文件</span>
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
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 .xlsx / .xls 格式，文件大小不超过 20MB，最多 10,000 行数据
          </div>
        </template>
      </el-upload>
    </el-card>

    <!-- 导入操作 -->
    <el-card v-if="selectedBiz && fileList.length" class="action-card" shadow="never">
      <template #header>
        <span>第三步：执行导入</span>
      </template>
      <el-row :gutter="20" align="middle">
        <el-col :span="16">
          <p>
            已选择文件：<strong>{{ fileList[0].name }}</strong>
            （{{ formatSize(fileList[0].size) }}）
          </p>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button :loading="importing" type="success" @click="handleImport">
            <el-icon><Check /></el-icon>
            开始导入
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
        <span>导入结果</span>
      </template>
      <el-result
        :icon="importResult.success ? 'success' : 'warning'"
        :title="importResult.success ? '导入成功' : '部分失败'"
        :sub-title="`成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条`"
      >
        <template #extra>
          <el-button v-if="importResult.failCount > 0" type="primary" @click="downloadErrorFile">
            下载错误报告
          </el-button>
        </template>
      </el-result>

      <!-- 错误详情 -->
      <el-table
        v-if="importResult.errors?.length"
        :data="importResult.errors"
        :max-height="300"
        style="margin-top: 20px"
      >
        <el-table-column prop="rowIndex" label="行号" width="80" />
        <el-table-column prop="field" label="字段" width="180" />
        <el-table-column prop="message" label="错误信息" />
        <el-table-column prop="value" label="原值">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.value }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled, Check, Money, Timer, User } from '@element-plus/icons-vue'
import { downloadTemplate, importData, type ImportResult } from '@/api/system/import-export'

defineOptions({ name: 'ImportExportIndex' })

interface BizType {
  code: 'rate-card' | 'rate-internal' | 'time-entry'
  name: string
  description: string
  icon: typeof Money | typeof Timer | typeof User
  tags: string[]
}

const bizTypes: BizType[] = [
  {
    code: 'rate-card',
    name: '职级费率',
    description: '按职级/项目/客户维护对外报价费率',
    icon: Money,
    tags: ['报价', '职级', '项目', '客户']
  },
  {
    code: 'rate-internal',
    name: '内部费率',
    description: '按职级/部门维护内部核算费率',
    icon: Money,
    tags: ['成本', '职级', '部门']
  },
  {
    code: 'time-entry',
    name: '工时数据',
    description: '批量导入历史工时数据（仅管理员）',
    icon: Timer,
    tags: ['工时', '项目', '员工']
  }
]

const selectedBiz = ref<typeof bizTypes[number]['code'] | null>(null)
const currentBiz = computed(() => bizTypes.find((b) => b.code === selectedBiz.value))
const downloading = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const progressText = ref('')
const fileList = ref<{ name: string; size: number; raw: File }[]>([])
const importResult = ref<ImportResult | null>(null)

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

async function handleDownloadTemplate() {
  if (!selectedBiz.value) return
  downloading.value = true
  try {
    await downloadTemplate(selectedBiz.value)
    ElMessage.success('模板下载完成')
  } catch (e) {
    ElMessage.error('模板下载失败：' + (e as Error).message)
  } finally {
    downloading.value = false
  }
}

function handleFileChange(file: { name: string; size: number; raw: File }) {
  fileList.value = [file]
  importResult.value = null
}

function handleFileRemove() {
  fileList.value = []
  importResult.value = null
}

async function handleImport() {
  if (!selectedBiz.value || !fileList.value.length) return
  importing.value = true
  importProgress.value = 0
  progressText.value = '正在解析文件...'
  try {
    // 模拟进度
    const timer = setInterval(() => {
      if (importProgress.value < 90) {
        importProgress.value += 10
        progressText.value = `已处理 ${Math.floor(importProgress.value * 12.5)} 行...`
      }
    }, 200)

    const resp = await importData(selectedBiz.value, fileList.value[0].raw)
    clearInterval(timer)
    importProgress.value = 100
    progressText.value = '导入完成'
    const result = (resp as any)?.data ?? resp
    importResult.value = result
    if (result.success) {
      ElMessage.success(`导入成功 ${result.successCount} 条`)
    } else {
      ElMessage.warning(`部分失败：成功 ${result.successCount}，失败 ${result.failCount}`)
    }
  } catch (e) {
    ElMessage.error('导入失败：' + (e as Error).message)
    importProgress.value = 0
  } finally {
    importing.value = false
  }
}

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
