<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  actResourceAssignment,
  pageAssignments,
  activeCount,
  utilization,
} from '@/api/resource/assignment'
import type { ResourceAssignmentVO, ResourceAssignmentCreateDTO } from '@/api/resource/assignment/types'

const loading = ref(false)
const list = ref<ResourceAssignmentVO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, employeeId: undefined as number | undefined, initiationId: undefined as number | undefined, status: '' })

const actionMap: Record<string, { label: string; type: string }> = {
  RESERVE: { label: '预占', type: 'info' },
  START: { label: '入场', type: 'success' },
  TRANSFER: { label: '调岗', type: 'warning' },
  RELEASE: { label: '离场', type: 'danger' },
  CANCEL: { label: '取消', type: 'info' },
}

const statusMap: Record<string, { label: string; type: string }> = {
  ACTIVE: { label: '生效中', type: 'success' },
  RELEASED: { label: '已离场', type: 'info' },
  CANCELLED: { label: '已取消', type: 'warning' },
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageAssignments(query.page, query.size, {
      employeeId: query.employeeId,
      initiationId: query.initiationId,
      status: query.status,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const form = reactive<ResourceAssignmentCreateDTO>({
  employeeId: 0,
  initiationId: 0,
  action: 'RESERVE',
  startDate: '',
  endDate: '',
  allocation: 1,
  levelCode: '',
  remark: '',
})

const formRules = {
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  initiationId: [{ required: true, message: '项目 ID 必填', trigger: 'blur' }],
  action: [{ required: true, message: '动作必填', trigger: 'change' }],
}

function openAct(action: string) {
  Object.assign(form, {
    employeeId: query.employeeId ?? 0,
    initiationId: query.initiationId ?? 0,
    action,
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
    allocation: 1,
    levelCode: '',
    remark: '',
  })
  dialogVisible.value = true
}

async function submitForm() {
  await actResourceAssignment(form)
  ElMessage.success('操作成功')
  dialogVisible.value = false
  fetchList()
}

const utilEmployeeId = ref<number | null>(null)
const utilResult = ref<Record<string, unknown> | null>(null)
const activeProjectCount = ref<number | null>(null)

async function checkUtilization() {
  if (!utilEmployeeId.value) return
  try {
    const { data } = await utilization(utilEmployeeId.value)
    utilResult.value = data
  } catch {
    utilResult.value = null
  }
  try {
    const { data } = await activeCount(utilEmployeeId.value)
    activeProjectCount.value = data
  } catch {
    activeProjectCount.value = null
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="assignment-page">
    <el-card shadow="never" class="util-card">
      <template #header>
        <span>员工利用率查询</span>
      </template>
      <div class="util-row">
        <el-input-number v-model="utilEmployeeId" :min="1" placeholder="员工 ID" />
        <el-button type="primary" @click="checkUtilization">查询</el-button>
        <el-tag v-if="activeProjectCount !== null" :type="activeProjectCount >= 3 ? 'danger' : 'success'">
          活跃项目数: {{ activeProjectCount }}
          {{ activeProjectCount >= 3 ? ' (过载预警)' : '' }}
        </el-tag>
        <span v-if="utilResult" class="util-detail">
          <el-tag v-for="(v, k) in utilResult" :key="k" size="small" type="info" effect="plain">
            {{ k }}: {{ v }}
          </el-tag>
        </span>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <el-form inline :model="query" class="search-form">
        <el-form-item label="员工 ID">
          <el-input-number v-model="query.employeeId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="项目 ID">
          <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">查询</el-button>
          <el-button @click="query.employeeId = undefined; query.initiationId = undefined; query.status = ''; fetchList()">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['resource:assign:act']" type="primary" @click="openAct('RESERVE')">预占</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('START')">入场</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('TRANSFER')">调岗</el-button>
        <el-button v-permission="['resource:assign:act']" type="danger" @click="openAct('RELEASE')">离场</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('CANCEL')">取消</el-button>
        <el-button :icon="'Refresh'" @click="fetchList">刷新</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="employeeName" title="员工" width="120" />
        <vxe-column field="employeeId" title="员工 ID" width="100" />
        <vxe-column field="initiationName" title="项目" min-width="180" />
        <vxe-column field="initiationId" title="项目 ID" width="100" />
        <vxe-column field="action" title="动作" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="(actionMap[row.action]?.type as any) || 'info'">
              {{ actionMap[row.action]?.label || row.action }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as any) || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="startDate" title="开始" width="120" />
        <vxe-column field="endDate" title="结束" width="120" />
        <vxe-column field="allocation" title="分配比例" width="100" align="center" />
        <vxe-column field="levelCode" title="职级" width="80" align="center" />
        <vxe-column field="remark" title="备注" min-width="160" />
      </vxe-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="分配动作" width="500px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="员工 ID" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" />
        </el-form-item>
        <el-form-item label="项目 ID" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" />
        </el-form-item>
        <el-form-item label="动作" prop="action">
          <el-select v-model="form.action" style="width: 100%">
            <el-option v-for="(v, k) in actionMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分配比例">
          <el-input-number v-model="form.allocation" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input v-model="form.levelCode" placeholder="例如: L8" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.assignment-page {
  .util-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  .util-detail { display: inline-flex; gap: 8px; flex-wrap: wrap; }
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
}
</style>
