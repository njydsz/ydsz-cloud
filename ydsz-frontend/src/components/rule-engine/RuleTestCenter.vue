<!--
  @fileoverview 规则测试中心组件 (Vue 3)
  @description 规则回归测试管理面板：
  - 测试用例 CRUD + 批量执行 + 通过率统计
  - 误触发/漏触发详情展示
  - CI/CD 集成：通过率 < 100% 时红色高亮
  - 支持 JSON 事实数据编辑
  @module components/rule-engine/RuleTestCenter
  @author ydsz-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * RuleTestCenter - 规则测试中心
 *
 * Props:
 *  - ruleCode: 关联规则编码（可选，空则全量测试用例）
 *
 * Events:
 *  - run-complete: 测试运行完成，传出报告
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, VideoPlay, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type { RuleTestCase, RegressionReport } from '@/api/rule-engine'
import { logger } from '@/utils/logger'

interface Props {
  ruleCode?: string
}

const props = withDefaults(defineProps<Props>(), {
  ruleCode: ''
})

const emit = defineEmits<{
  (e: 'run-complete', report: RegressionReport): void
}>()

// ===== 状态 =====
const loading = ref(false)
const testCases = ref<RuleTestCase[]>([])
/** 选中的测试用例 ID 列表 */
const selectedIds = ref<string[]>([])
/** 最近一次回归测试报告 */
const lastReport = ref<RegressionReport | null>(null)

/** 编辑/新建测试用例对话框 */
const editDialog = reactive({
  visible: false,
  isEdit: false,
  form: {
    id: '',
    name: '',
    ruleCode: '',
    description: '',
    factsData: '{}' as string,
    expectedTriggered: [] as string[]
  }
})

// ===== 计算属性 =====
/** 回归测试通过率（百分比） */
const passRate = computed(() => {
  if (!lastReport.value) return 0
  const { passed, total } = lastReport.value
  return total === 0 ? 100 : Math.round((passed / total) * 1000) / 10
})

/** 通过率对应颜色：100% 绿，>=80% 橙，<80% 红 */
const passRateColor = computed(() => {
  if (passRate.value === 100) return '#67c23a'
  if (passRate.value >= 80) return '#e6a23c'
  return '#f56c6c'
})

// ===== 方法 =====
/** 加载测试用例列表 */
async function loadTestCases() {
  loading.value = true
  try {
    const res = await ruleApi.getTestCases(props.ruleCode || undefined)
    testCases.value = res.data || []
  } catch (err) {
    logger.error('加载测试用例失败', err)
    ElMessage.error('加载测试用例失败')
  } finally {
    loading.value = false
  }
}

/** 批量执行选中的测试用例 */
async function batchRun() {
  if (selectedIds.value.length === 0 && testCases.value.length === 0) {
    ElMessage.warning('请先选择测试用例')
    return
  }

  loading.value = true
  try {
    const ids = selectedIds.value.length > 0
      ? selectedIds.value.map(id => Number(id))
      : []
    const res = await ruleApi.batchRunTestCases(ids)
    lastReport.value = res.data
    emit('run-complete', res.data)

    if (res.data.passRate === '100%' || res.data.passed === res.data.total) {
      ElMessage.success(`回归测试通过: ${res.data.passed}/${res.data.total}`)
    } else {
      ElMessage.warning(`回归测试未通过: ${res.data.passed}/${res.data.total} (${res.data.passRate})`)
    }
  } catch (err) {
    logger.error('批量执行测试失败', err)
    ElMessage.error('批量执行测试失败')
  } finally {
    loading.value = false
  }
}

/** 打开新建测试用例对话框 */
function openCreateDialog() {
  editDialog.isEdit = false
  editDialog.form = {
    id: '',
    name: '',
    ruleCode: props.ruleCode || '',
    description: '',
    factsData: '{\n  \n}',
    expectedTriggered: []
  }
  editDialog.visible = true
}

/** 打开编辑测试用例对话框 */
function openEditDialog(tc: RuleTestCase) {
  editDialog.isEdit = true
  editDialog.form = {
    id: tc.id,
    name: tc.name,
    ruleCode: tc.ruleCode || '',
    description: tc.description || '',
    factsData: JSON.stringify(tc.factsData, null, 2),
    expectedTriggered: tc.expectedTriggered || []
  }
  editDialog.visible = true
}

/** 保存测试用例（新建/更新） */
async function saveTestCase() {
  try {
    const facts = JSON.parse(editDialog.form.factsData)
    const tc: Partial<RuleTestCase> = {
      id: editDialog.form.id || undefined,
      name: editDialog.form.name,
      ruleCode: editDialog.form.ruleCode || undefined,
      description: editDialog.form.description,
      factsData: facts,
      expectedTriggered: editDialog.form.expectedTriggered
    }
    await ruleApi.saveTestCase(tc)
    ElMessage.success(editDialog.isEdit ? '更新成功' : '创建成功')
    editDialog.visible = false
    await loadTestCases()
  } catch (err) {
    if (err instanceof SyntaxError) {
      ElMessage.error('事实数据 JSON 格式错误: ' + err.message)
    } else {
      ElMessage.error('保存失败')
    }
  }
}

/** 删除指定测试用例 */
async function deleteTestCase(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该测试用例?', '提示', { type: 'warning' })
    await ruleApi.deleteTestCase(id)
    ElMessage.success('删除成功')
    await loadTestCases()
  } catch {
    // cancelled
  }
}

function handleSelectionChange(rows: RuleTestCase[]) {
  selectedIds.value = rows.map(r => r.id)
}

// ===== 初始化 =====
onMounted(() => {
  loadTestCases()
})
</script>

<template>
  <div class="rule-test-center">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="left">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建用例</el-button>
        <el-button type="success" :icon="VideoPlay" :loading="loading" @click="batchRun">
          批量执行
        </el-button>
        <el-button :icon="Refresh" @click="loadTestCases">刷新</el-button>
      </div>
      <div v-if="lastReport" class="report-summary">
        <el-tag :color="passRateColor" effect="dark" size="large">
          通过率: {{ passRate }}%
        </el-tag>
        <span class="stats">
          总计 {{ lastReport.total }} | 通过 {{ lastReport.passed }} | 失败 {{ lastReport.failed }}
        </span>
      </div>
    </div>

    <!-- 测试用例表格列表 -->
    <el-table
      :data="testCases"
      v-loading="loading"
      @selection-change="handleSelectionChange"
      stripe
      style="width: 100%"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="name" label="用例名称" min-width="180" />
      <el-table-column prop="ruleCode" label="关联规则" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="预期触发" width="200">
        <template #default="{ row }">
          <el-tag
            v-for="code in (row.expectedTriggered || [])"
            :key="code"
            size="small"
            type="info"
            class="trigger-tag"
          >
            {{ code }}
          </el-tag>
          <span v-if="!row.expectedTriggered?.length" class="empty-text">无触发</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="deleteTestCase(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 失败详情 -->
    <div v-if="lastReport && lastReport.caseResults" class="failure-details">
      <h4>失败用例详情</h4>
      <el-table :data="lastReport.caseResults.filter((c: any) => !c.pass)" stripe size="small">
        <el-table-column prop="caseName" label="用例名称" min-width="150" />
        <el-table-column label="误触发" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="code in (row.falsePositives || [])" :key="code" type="danger" size="small" class="trigger-tag">
              {{ code }}
            </el-tag>
            <span v-if="!row.falsePositives?.length" class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="漏触发" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="code in (row.falseNegatives || [])" :key="code" type="warning" size="small" class="trigger-tag">
              {{ code }}
            </el-tag>
            <span v-if="!row.falseNegatives?.length" class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="failureReason" label="原因" min-width="200" show-overflow-tooltip />
      </el-table>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="editDialog.visible"
      :title="editDialog.isEdit ? '编辑测试用例' : '新建测试用例'"
      width="700px"
    >
      <el-form :model="editDialog.form" label-width="100px">
        <el-form-item label="用例名称" required>
          <el-input v-model="editDialog.form.name" placeholder="输入测试用例名称" />
        </el-form-item>
        <el-form-item label="关联规则">
          <el-input v-model="editDialog.form.ruleCode" placeholder="规则编码（可选）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editDialog.form.description" type="textarea" :rows="2" placeholder="用例描述" />
        </el-form-item>
        <el-form-item label="事实数据" required>
          <el-input
            v-model="editDialog.form.factsData"
            type="textarea"
            :rows="8"
            placeholder='{"amount": 15000, "score": 800}'
            class="json-editor"
          />
        </el-form-item>
        <el-form-item label="预期触发">
          <el-select
            v-model="editDialog.form.expectedTriggered"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入预期触发的规则编码"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveTestCase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rule-test-center {
  padding: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar .left {
  display: flex;
  gap: 8px;
}

.report-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stats {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.trigger-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.empty-text {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.failure-details {
  margin-top: 20px;
}

.failure-details h4 {
  margin-bottom: 10px;
  color: var(--el-color-danger);
}

.json-editor :deep(textarea) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}
</style>
