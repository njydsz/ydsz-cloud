<!--
  @file 资源分配管理
  @description 资源分配管理页面：提供员工利用率/活跃项目数查询、分配记录分页筛选，并通过单一 act() 入口执行分配动作（预占 RESERVE / 入场 START / 调岗 TRANSFER / 离场 RELEASE / 取消 CANCEL）。对应路由 /resource/assignment，后端服务 ydsz-pmis-userinfo（端口 9002）。
  @module views/resource/assignment
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  actResourceAssignment,
  pageAssignments,
  activeCount,
  utilization,
} from '@/api/resource/assignment'
import type { ResourceAssignmentVO, ResourceAssignmentCreateDTO } from '@/api/resource/assignment/types'

const { t } = useI18n()

const loading = ref(false)
const list = ref<ResourceAssignmentVO[]>([])
const total = ref(0)
// 分页查询条件：员工 ID / 项目 initiation ID / 分配状态
const query = reactive({ page: 1, size: 10, employeeId: undefined as number | undefined, initiationId: undefined as number | undefined, status: '' })

// 分配动作映射：label 为中文文案，type 对应 el-tag 类型
const actionMap = computed<Record<string, { label: string; type: string }>>(() => ({
  RESERVE: { label: t('resource.assignment.action.RESERVE'), type: 'info' },
  START: { label: t('resource.assignment.action.START'), type: 'success' },
  TRANSFER: { label: t('resource.assignment.action.TRANSFER'), type: 'warning' },
  RELEASE: { label: t('resource.assignment.action.RELEASE'), type: 'danger' },
  CANCEL: { label: t('resource.assignment.action.CANCEL'), type: 'info' },
}))

// 分配状态映射：ACTIVE 生效中 / RELEASED 已离场 / CANCELLED 已取消
const statusMap = computed<Record<string, { label: string; type: string }>>(() => ({
  ACTIVE: { label: t('resource.assignment.status.ACTIVE'), type: 'success' },
  RELEASED: { label: t('resource.assignment.status.RELEASED'), type: 'info' },
  CANCELLED: { label: t('resource.assignment.status.CANCELLED'), type: 'warning' },
}))

/** 拉取分配记录分页列表 */
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
// 分配动作表单（与后端 ResourceAssignmentCreateDTO 对齐）
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

const formRules = computed(() => ({
  employeeId: [{ required: true, message: t('resource.assignment.rules.employeeIdRequired'), trigger: 'blur' }],
  initiationId: [{ required: true, message: t('resource.assignment.rules.initiationIdRequired'), trigger: 'blur' }],
  action: [{ required: true, message: t('resource.assignment.rules.actionRequired'), trigger: 'change' }],
}))

/** 打开分配动作弹窗，按动作类型初始化表单默认值 */
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

/** 提交分配动作，成功后关闭弹窗并刷新列表 */
async function submitForm() {
  await actResourceAssignment(form)
  ElMessage.success(t('resource.assignment.messages.success'))
  dialogVisible.value = false
  fetchList()
}

// 员工利用率查询相关状态：utilResult 为利用率明细，activeProjectCount 为活跃项目数（≥3 触发过载预警）
const utilEmployeeId = ref<number | null>(null)
const utilResult = ref<Record<string, unknown> | null>(null)
const activeProjectCount = ref<number | null>(null)

/** 查询指定员工的利用率与活跃项目数 */
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
    <!-- 员工利用率查询区 -->
    <el-card shadow="never" class="util-card">
      <template #header>
        <span>{{ t('resource.assignment.util.title') }}</span>
      </template>
      <div class="util-row">
        <el-input-number v-model="utilEmployeeId" :min="1" :placeholder="t('resource.assignment.util.employeeIdPlaceholder')" />
        <el-button type="primary" @click="checkUtilization">{{ t('resource.assignment.buttons.query') }}</el-button>
        <el-tag v-if="activeProjectCount !== null" :type="activeProjectCount >= 3 ? 'danger' : 'success'">
          {{ t('resource.assignment.util.activeCount', { count: activeProjectCount }) }}{{ activeProjectCount >= 3 ? t('resource.assignment.util.overloadWarn') : '' }}
        </el-tag>
        <span v-if="utilResult" class="util-detail">
          <el-tag v-for="(v, k) in utilResult" :key="k" size="small" type="info" effect="plain">
            {{ k }}: {{ v }}
          </el-tag>
        </span>
      </div>
    </el-card>

    <!-- 分配记录查询与操作区 -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-form inline :model="query" class="search-form">
        <el-form-item :label="t('resource.assignment.search.employeeId')">
          <el-input-number v-model="query.employeeId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.search.initiationId')">
          <el-input-number v-model="query.initiationId" :min="0" :controls="false" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.search.status')">
          <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 140px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">{{ t('resource.assignment.buttons.query') }}</el-button>
          <el-button @click="query.employeeId = undefined; query.initiationId = undefined; query.status = ''; fetchList()">{{ t('resource.assignment.buttons.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['resource:assign:act']" type="primary" @click="openAct('RESERVE')">{{ t('resource.assignment.buttons.reserve') }}</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('START')">{{ t('resource.assignment.buttons.start') }}</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('TRANSFER')">{{ t('resource.assignment.buttons.transfer') }}</el-button>
        <el-button v-permission="['resource:assign:act']" type="danger" @click="openAct('RELEASE')">{{ t('resource.assignment.buttons.release') }}</el-button>
        <el-button v-permission="['resource:assign:act']" @click="openAct('CANCEL')">{{ t('resource.assignment.buttons.cancel') }}</el-button>
        <el-button :icon="'Refresh'" @click="fetchList">{{ t('resource.assignment.buttons.refresh') }}</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="employeeName" :title="t('resource.assignment.columns.employee')" width="120" />
        <vxe-column field="employeeId" :title="t('resource.assignment.columns.employeeId')" width="100" />
        <vxe-column field="initiationName" :title="t('resource.assignment.columns.project')" min-width="180" />
        <vxe-column field="initiationId" :title="t('resource.assignment.columns.initiationId')" width="100" />
        <vxe-column field="action" :title="t('resource.assignment.columns.action')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="(actionMap[row.action]?.type as any) || 'info'">
              {{ actionMap[row.action]?.label || row.action }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="t('resource.assignment.columns.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as any) || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="startDate" :title="t('resource.assignment.columns.startDate')" width="120" />
        <vxe-column field="endDate" :title="t('resource.assignment.columns.endDate')" width="120" />
        <vxe-column field="allocation" :title="t('resource.assignment.columns.allocation')" width="100" align="center" />
        <vxe-column field="levelCode" :title="t('resource.assignment.columns.levelCode')" width="80" align="center" />
        <vxe-column field="remark" :title="t('resource.assignment.columns.remark')" min-width="160" />
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

    <!-- 分配动作弹窗 -->
    <el-dialog v-model="dialogVisible" :title="t('resource.assignment.dialog.title')" width="500px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('resource.assignment.form.employeeId')" prop="employeeId">
          <el-input-number v-model="form.employeeId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.initiationId')" prop="initiationId">
          <el-input-number v-model="form.initiationId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.action')" prop="action">
          <el-select v-model="form.action" style="width: 100%">
            <el-option v-for="(v, k) in actionMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.startDate')">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.endDate')">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.allocation')">
          <el-input-number v-model="form.allocation" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.levelCode')">
          <el-input v-model="form.levelCode" :placeholder="t('resource.assignment.form.levelCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resource.assignment.form.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.ok') }}</el-button>
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
