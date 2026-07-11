<!--
  @fileoverview 规则审批面板组件 (Vue 3)
  @description 规则审批工作流：
  - 待审批列表
  - 审批操作（通过/驳回/转委托）
  - 审批历史
  - 审批意见
  @module components/rule-engine/RuleApprovalPanel
  @author ydsz-pmis-team
  @since 2.0.0
-->
<script setup lang="ts">
/**
 * RuleApprovalPanel - 规则审批面板
 *
 * Props:
 *  - status: 审批状态过滤（PENDING/APPROVED/REJECTED/ALL）
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Share, Refresh, Clock } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine/index'
import type { ApprovalRecord } from '@/api/rule-engine/index'
import { logger } from '@/utils/logger'

interface Props {
  status?: string
}

const props = withDefaults(defineProps<Props>(), {
  status: 'PENDING'
})

// ===== 状态 =====
const loading = ref(false)
const records = ref<ApprovalRecord[]>([])
const activeTab = ref('pending')

// 审批对话框
const approvalDialog = reactive({
  visible: false,
  type: '' as 'approve' | 'reject' | 'delegate',
  record: null as ApprovalRecord | null,
  comment: '',
  delegateTo: ''
})

// ===== 计算属性 =====
const filteredRecords = computed(() => {
  if (activeTab.value === 'all') return records.value
  return records.value.filter((r: ApprovalRecord) => r.status === activeTab.value.toUpperCase())
})

// ===== 方法 =====
async function loadRecords() {
  loading.value = true
  try {
    const res = await ruleApi.getApprovalRecords(activeTab.value === 'all' ? '' : activeTab.value.toUpperCase())
    records.value = res.data || []
  } catch (err) {
    logger.error('加载审批记录失败', err)
  } finally {
    loading.value = false
  }
}

function openApproval(record: ApprovalRecord, type: 'approve' | 'reject' | 'delegate') {
  approvalDialog.record = record
  approvalDialog.type = type
  approvalDialog.comment = ''
  approvalDialog.delegateTo = ''
  approvalDialog.visible = true
}

async function submitApproval() {
  if (!approvalDialog.record) return
  if (approvalDialog.type !== 'approve' && !approvalDialog.comment) {
    ElMessage.warning('请填写审批意见')
    return
  }

  try {
    if (approvalDialog.type === 'approve') {
      await ruleApi.approveRule(approvalDialog.record.id, approvalDialog.comment)
      ElMessage.success('审批通过')
    } else if (approvalDialog.type === 'reject') {
      await ruleApi.rejectRule(approvalDialog.record.id, approvalDialog.comment)
      ElMessage.success('已驳回')
    } else if (approvalDialog.type === 'delegate') {
      if (!approvalDialog.delegateTo) {
        ElMessage.warning('请选择转委托人')
        return
      }
      await ruleApi.delegateRule(approvalDialog.record.id, approvalDialog.delegateTo, approvalDialog.comment)
      ElMessage.success('已转委托')
    }
    approvalDialog.visible = false
    await loadRecords()
  } catch (err) {
    logger.error('审批操作失败', err)
    ElMessage.error('操作失败')
  }
}

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function getStatusTag(status: string): { type: TagType; label: string } {
  switch (status) {
    case 'PENDING': return { type: 'warning', label: '待审批' }
    case 'APPROVED': return { type: 'success', label: '已通过' }
    case 'REJECTED': return { type: 'danger', label: '已驳回' }
    case 'DELEGATED': return { type: 'info', label: '已转委' }
    default: return { type: 'info', label: status }
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<template>
  <div class="approval-panel">
    <!-- 标签页 -->
    <el-tabs v-model="activeTab" @tab-change="loadRecords">
      <el-tab-pane label="待审批" name="pending">
        <template #label>
          <el-badge :value="records.filter((r: ApprovalRecord) => r.status === 'PENDING').length" :max="99" type="warning">
            待审批
          </el-badge>
        </template>
      </el-tab-pane>
      <el-tab-pane label="已通过" name="approved" />
      <el-tab-pane label="已驳回" name="rejected" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button :icon="Refresh" link @click="loadRecords">刷新</el-button>
    </div>

    <!-- 审批列表 -->
    <el-table :data="filteredRecords" v-loading="loading" stripe>
      <el-table-column prop="ruleCode" label="规则编码" width="150" />
      <el-table-column prop="ruleName" label="规则名称" min-width="200" />
      <el-table-column prop="submitter" label="提交人" width="120" />
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
      <el-table-column prop="changeType" label="变更类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.changeType === 'CREATE' ? 'success' : 'warning'">
            {{ row.changeType === 'CREATE' ? '新建' : row.changeType === 'UPDATE' ? '更新' : '删除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusTag(row.status).type" size="small">
            {{ getStatusTag(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button :icon="Check" size="small" type="success" @click="openApproval(row, 'approve')">通过</el-button>
            <el-button :icon="Close" size="small" type="danger" @click="openApproval(row, 'reject')">驳回</el-button>
            <el-button :icon="Share" size="small" @click="openApproval(row, 'delegate')">转委</el-button>
          </template>
          <span v-else class="processed-text">
            <el-icon><Clock /></el-icon>
            已处理
          </span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 审批对话框 -->
    <el-dialog
      v-model="approvalDialog.visible"
      :title="approvalDialog.type === 'approve' ? '审批通过' : approvalDialog.type === 'reject' ? '驳回规则' : '转委托'"
      width="500px"
    >
      <div v-if="approvalDialog.record" class="approval-info">
        <p><b>规则:</b> {{ approvalDialog.record.ruleCode }} - {{ approvalDialog.record.ruleName }}</p>
        <p><b>提交人:</b> {{ approvalDialog.record.submitter }}</p>
        <p><b>变更描述:</b> {{ approvalDialog.record.changeDesc || '—' }}</p>
      </div>

      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item v-if="approvalDialog.type === 'delegate'" label="转委人">
          <el-input v-model="approvalDialog.delegateTo" placeholder="输入转委托人用户名" />
        </el-form-item>
        <el-form-item label="审批意见" :required="approvalDialog.type !== 'approve'">
          <el-input
            v-model="approvalDialog.comment"
            type="textarea"
            :rows="4"
            placeholder="输入审批意见..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="approvalDialog.visible = false">取消</el-button>
        <el-button
          :type="approvalDialog.type === 'approve' ? 'success' : approvalDialog.type === 'reject' ? 'danger' : 'primary'"
          @click="submitApproval"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.approval-panel {
  padding: 16px;
}

.toolbar {
  margin-bottom: 12px;
  text-align: right;
}

.approval-info {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
}

.approval-info p {
  margin: 4px 0;
  font-size: 13px;
}

.processed-text {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
