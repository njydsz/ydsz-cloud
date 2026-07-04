<!--
  @file 考勤管理
  @description 考勤管理页面，整合出勤记录登记/查询、加班申请与审批、请假申请与审批三个 Tab，对接 @/api/attendance 模块。
  @module views/attendance
-->
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  recordAttendance,
  pageAttendance,
  statByStatus,
  submitOvertime,
  approveOvertime,
  pageOvertime,
  submitLeave,
  approveLeave,
  pageLeave,
} from '@/api/attendance'
import type { AttendanceCreateDTO, AttendanceVO, OvertimeCreateDTO, OvertimeVO, LeaveCreateDTO, LeaveVO } from '@/api/attendance/types'

const { t } = useI18n()

/** 当前激活的 Tab：attendance-出勤 / overtime-加班 / leave-请假 */
const tab = ref<'attendance' | 'overtime' | 'leave'>('attendance')

// ============== 出勤 ==============
/** 出勤列表加载状态 */
const attLoading = ref(false)
/** 出勤记录列表 */
const attList = ref<AttendanceVO[]>([])
/** 出勤记录总条数（用于分页） */
const attTotal = ref(0)
/** 出勤查询条件 */
const attQuery = reactive({ employeeId: undefined as number | undefined, startDate: '', endDate: '', page: 1, size: 10 })
/** 出勤状态聚合统计 */
const attStat = ref<Array<Record<string, unknown>>>([])

const statusMap = computed<Record<string, { label: string; type: string }>>(() => ({
  NORMAL: { label: t('attendance.attendance.status.NORMAL'), type: 'success' },
  LATE: { label: t('attendance.attendance.status.LATE'), type: 'warning' },
  EARLY: { label: t('attendance.attendance.status.EARLY'), type: 'warning' },
  ABSENT: { label: t('attendance.attendance.status.ABSENT'), type: 'danger' },
  LEAVE: { label: t('attendance.attendance.status.LEAVE'), type: 'info' },
  OVERTIME: { label: t('attendance.attendance.status.OVERTIME'), type: 'primary' },
}))

const workTypeMap = computed<Record<string, string>>(() => ({
  WORKDAY: t('attendance.attendance.workType.WORKDAY'),
  WEEKEND: t('attendance.attendance.workType.WEEKEND'),
  HOLIDAY: t('attendance.attendance.workType.HOLIDAY'),
}))

/** 拉取出勤分页列表及按状态聚合统计 */
async function fetchAttendance() {
  attLoading.value = true
  try {
    const { data } = await pageAttendance(attQuery)
    attList.value = data.list
    attTotal.value = data.total
  } finally {
    attLoading.value = false
  }
  statByStatus({
    employeeId: attQuery.employeeId,
    startDate: attQuery.startDate,
    endDate: attQuery.endDate,
  }).then(({ data }) => {
    attStat.value = data || []
  }).catch(() => (attStat.value = []))
}

/** 出勤登记弹窗显隐 */
const attDialogVisible = ref(false)
/** 出勤登记表单引用 */
const attFormRef = ref()
/** 出勤登记表单数据 */
const attForm = reactive<AttendanceCreateDTO>({
  employeeId: 0,
  employeeName: '',
  attendanceDate: new Date().toISOString().slice(0, 10),
  status: 'NORMAL',
  workType: 'WORKDAY',
  remark: '',
})
const attFormRules = computed(() => ({
  employeeId: [{ required: true, message: t('attendance.attendance.rules.employeeIdRequired'), trigger: 'blur' }],
  attendanceDate: [{ required: true, message: t('attendance.attendance.rules.dateRequired'), trigger: 'change' }],
}))

/** 打开出勤登记弹窗，重置表单为默认值 */
function openAttCreate() {
  Object.assign(attForm, {
    employeeId: attQuery.employeeId ?? 0,
    employeeName: '',
    attendanceDate: new Date().toISOString().slice(0, 10),
    status: 'NORMAL',
    workType: 'WORKDAY',
    remark: '',
  })
  attDialogVisible.value = true
}

/** 提交出勤登记，调用后端登记接口并刷新列表 */
async function submitAtt() {
  await attFormRef.value?.validate()
  await recordAttendance(attForm)
  ElMessage.success(t('attendance.attendance.messages.recorded'))
  attDialogVisible.value = false
  fetchAttendance()
}

// ============== 加班 ==============
/** 加班列表加载状态 */
const otLoading = ref(false)
/** 加班申请列表 */
const otList = ref<OvertimeVO[]>([])
/** 加班申请总条数（用于分页） */
const otTotal = ref(0)
/** 加班查询条件 */
const otQuery = reactive({ employeeId: undefined as number | undefined, approvalStatus: '', page: 1, size: 10 })

const otTypeMap = computed<Record<string, string>>(() => ({
  WORKDAY: t('attendance.overtime.type.WORKDAY'),
  WEEKEND: t('attendance.overtime.type.WEEKEND'),
  HOLIDAY: t('attendance.overtime.type.HOLIDAY'),
}))
const otStatusMap = computed<Record<string, { label: string; type: string }>>(() => ({
  DRAFT: { label: t('attendance.common.status.DRAFT'), type: 'info' },
  SUBMITTED: { label: t('attendance.common.status.SUBMITTED'), type: 'warning' },
  APPROVED: { label: t('attendance.common.status.APPROVED'), type: 'success' },
  REJECTED: { label: t('attendance.common.status.REJECTED'), type: 'danger' },
  CANCELLED: { label: t('attendance.common.status.CANCELLED'), type: 'info' },
}))

/** 拉取加班申请分页列表 */
async function fetchOvertime() {
  otLoading.value = true
  try {
    const { data } = await pageOvertime(otQuery)
    otList.value = data.list
    otTotal.value = data.total
  } finally {
    otLoading.value = false
  }
}

/** 加班申请弹窗显隐 */
const otDialogVisible = ref(false)
/** 加班申请表单引用 */
const otFormRef = ref()
/** 加班申请表单数据 */
const otForm = reactive<OvertimeCreateDTO>({
  employeeId: 0,
  overtimeDate: new Date().toISOString().slice(0, 10),
  startTime: '',
  endTime: '',
  overtimeType: 'WORKDAY',
  payRate: 1.5,
  reason: '',
})
/** 加班申请表单验证规则 */
const otFormRules = computed(() => ({
  employeeId: [{ required: true, message: t('attendance.overtime.rules.employeeIdRequired'), trigger: 'blur' }],
  overtimeDate: [{ required: true, message: t('attendance.overtime.rules.dateRequired'), trigger: 'change' }],
  startTime: [{ required: true, message: t('attendance.overtime.rules.startTimeRequired'), trigger: 'change' }],
  endTime: [{ required: true, message: t('attendance.overtime.rules.endTimeRequired'), trigger: 'change' }],
  overtimeType: [{ required: true, message: t('attendance.overtime.rules.typeRequired'), trigger: 'change' }],
  reason: [{ required: true, message: t('attendance.overtime.rules.reasonRequired'), trigger: 'blur' }],
}))

/** 打开加班申请弹窗，重置表单为默认值 */
function openOtCreate() {
  Object.assign(otForm, {
    employeeId: otQuery.employeeId ?? 0,
    overtimeDate: new Date().toISOString().slice(0, 10),
    startTime: '',
    endTime: '',
    overtimeType: 'WORKDAY',
    payRate: 1.5,
    reason: '',
  })
  otDialogVisible.value = true
}

/** 提交加班申请，调用后端提交接口并刷新列表 */
async function submitOt() {
  await otFormRef.value?.validate()
  await submitOvertime(otForm)
  ElMessage.success(t('attendance.overtime.messages.submitted'))
  otDialogVisible.value = false
  fetchOvertime()
}

/**
 * 加班审批操作（通过 / 驳回）
 * @param row 当前行加班申请
 * @param action 审批动作：APPROVED-通过 / REJECTED-驳回
 */
async function handleApproveOt(row: OvertimeVO, action: 'APPROVED' | 'REJECTED') {
  try {
    await ElMessageBox.confirm(
      t('attendance.overtime.messages.confirmApprove', {
        action: action === 'APPROVED' ? t('attendance.overtime.messages.actionPass') : t('attendance.overtime.messages.actionReject'),
      }),
      t('common.tip'),
      { type: 'warning' },
    )
    await approveOvertime(row.id, action)
    ElMessage.success(t('attendance.overtime.messages.approved'))
    fetchOvertime()
  } catch {
    /* 取消 */
  }
}

// ============== 请假 ==============
/** 请假列表加载状态 */
const lvLoading = ref(false)
/** 请假申请列表 */
const lvList = ref<LeaveVO[]>([])
/** 请假申请总条数（用于分页） */
const lvTotal = ref(0)
/** 请假查询条件 */
const lvQuery = reactive({ employeeId: undefined as number | undefined, approvalStatus: '', page: 1, size: 10 })

const lvTypeMap = computed<Record<string, string>>(() => ({
  ANNUAL: t('attendance.leave.type.ANNUAL'),
  SICK: t('attendance.leave.type.SICK'),
  PERSONAL: t('attendance.leave.type.PERSONAL'),
  MARRIAGE: t('attendance.leave.type.MARRIAGE'),
  MATERNITY: t('attendance.leave.type.MATERNITY'),
  BEREAVEMENT: t('attendance.leave.type.BEREAVEMENT'),
  OTHER: t('attendance.leave.type.OTHER'),
}))
const lvStatusMap = computed<Record<string, { label: string; type: string }>>(() => otStatusMap.value)

/** 拉取请假申请分页列表 */
async function fetchLeave() {
  lvLoading.value = true
  try {
    const { data } = await pageLeave(lvQuery)
    lvList.value = data.list
    lvTotal.value = data.total
  } finally {
    lvLoading.value = false
  }
}

/** 请假申请弹窗显隐 */
const lvDialogVisible = ref(false)
/** 请假申请表单数据 */
const lvForm = reactive<LeaveCreateDTO>({
  employeeId: 0,
  leaveType: 'ANNUAL',
  startDate: '',
  endDate: '',
  reason: '',
})

/** 打开请假申请弹窗，重置表单为默认值 */
function openLvCreate() {
  Object.assign(lvForm, {
    employeeId: lvQuery.employeeId ?? 0,
    leaveType: 'ANNUAL',
    startDate: '',
    endDate: '',
    reason: '',
  })
  lvDialogVisible.value = true
}

/** 提交请假申请，调用后端提交接口并刷新列表 */
async function submitLv() {
  await submitLeave(lvForm)
  ElMessage.success(t('attendance.leave.messages.submitted'))
  lvDialogVisible.value = false
  fetchLeave()
}

/**
 * 请假审批操作（提交 / 通过 / 驳回）
 * @param row 当前行请假申请
 * @param action 审批动作：SUBMITTED-提交 / APPROVED-通过 / REJECTED-驳回
 */
async function handleApproveLv(row: LeaveVO, action: 'SUBMITTED' | 'APPROVED' | 'REJECTED') {
  try {
    const actionText = action === 'SUBMITTED'
      ? t('attendance.leave.messages.actionSubmit')
      : action === 'APPROVED'
        ? t('attendance.leave.messages.actionPass')
        : t('attendance.leave.messages.actionReject')
    await ElMessageBox.confirm(
      t('attendance.leave.messages.confirmAction', { action: actionText }),
      t('common.tip'),
      { type: 'warning' },
    )
    await approveLeave(row.id, action)
    ElMessage.success(t('attendance.leave.messages.operated'))
    fetchLeave()
  } catch {
    /* 取消 */
  }
}

onMounted(() => {
  fetchAttendance()
  fetchOvertime()
  fetchLeave()
})
</script>

<template>
  <div class="attendance-page">
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <!-- 出勤 -->
        <el-tab-pane :label="t('attendance.tabs.attendance')" name="attendance">
          <el-form inline :model="attQuery" class="search-form">
            <el-form-item :label="t('attendance.common.search.employeeId')">
              <el-input-number v-model="attQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item :label="t('attendance.attendance.search.start')">
              <el-date-picker v-model="attQuery.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item :label="t('attendance.attendance.search.end')">
              <el-date-picker v-model="attQuery.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="attQuery.page = 1; fetchAttendance()">{{ t('attendance.common.buttons.query') }}</el-button>
              <el-button @click="attQuery.employeeId = undefined; attQuery.startDate = ''; attQuery.endDate = ''; fetchAttendance()">{{ t('attendance.common.buttons.reset') }}</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:record:create']" type="primary" :icon="'Plus'" @click="openAttCreate">{{ t('attendance.attendance.buttons.create') }}</el-button>
            <el-button :icon="'Refresh'" @click="fetchAttendance">{{ t('attendance.common.buttons.refresh') }}</el-button>
          </div>

          <div v-if="attStat.length > 0" class="stat-row">
            <el-tag v-for="(s, idx) in attStat" :key="idx" :type="(statusMap[(s.status as string) || '']?.type as any) || 'info'" effect="plain" size="large">
              {{ statusMap[(s.status as string) || '']?.label || s.status || t('attendance.attendance.status.unknown') }}{{ t('attendance.attendance.statSuffix', { days: s.count, hours: s.total_hours }) }}
            </el-tag>
          </div>

          <vxe-table :data="attList" :loading="attLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeName" :title="t('attendance.attendance.columns.employeeName')" width="120" />
            <vxe-column field="employeeId" :title="t('attendance.attendance.columns.employeeId')" width="100" />
            <vxe-column field="attendanceDate" :title="t('attendance.attendance.columns.attendanceDate')" width="120" />
            <vxe-column field="checkInTime" :title="t('attendance.attendance.columns.checkInTime')" width="160" />
            <vxe-column field="checkOutTime" :title="t('attendance.attendance.columns.checkOutTime')" width="160" />
            <vxe-column field="workHours" :title="t('attendance.attendance.columns.workHours')" width="100" align="right" />
            <vxe-column field="overtimeHours" :title="t('attendance.attendance.columns.overtimeHours')" width="100" align="right" />
            <vxe-column field="workType" :title="t('attendance.attendance.columns.workType')" width="100">
              <template #default="{ row }">{{ workTypeMap[row.workType || ''] || row.workType }}</template>
            </vxe-column>
            <vxe-column field="status" :title="t('attendance.attendance.columns.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="(statusMap[row.status]?.type as any) || 'info'">
                  {{ statusMap[row.status]?.label || row.status }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="remark" :title="t('attendance.attendance.columns.remark')" min-width="160" />
          </vxe-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="attQuery.page"
              v-model:page-size="attQuery.size"
              :total="attTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchAttendance"
              @size-change="fetchAttendance"
            />
          </div>
        </el-tab-pane>

        <!-- 加班 -->
        <el-tab-pane :label="t('attendance.tabs.overtime')" name="overtime">
          <el-form inline :model="otQuery" class="search-form">
            <el-form-item :label="t('attendance.common.search.employeeId')">
              <el-input-number v-model="otQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item :label="t('attendance.common.search.approvalStatus')">
              <el-select v-model="otQuery.approvalStatus" :placeholder="t('common.all')" clearable style="width: 140px">
                <el-option v-for="(v, k) in otStatusMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="otQuery.page = 1; fetchOvertime()">{{ t('attendance.common.buttons.query') }}</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:overtime:create']" type="primary" :icon="'Plus'" @click="openOtCreate">{{ t('attendance.overtime.buttons.create') }}</el-button>
            <el-button :icon="'Refresh'" @click="fetchOvertime">{{ t('attendance.common.buttons.refresh') }}</el-button>
          </div>

          <vxe-table :data="otList" :loading="otLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="overtimeCode" :title="t('attendance.overtime.columns.overtimeCode')" width="180" />
            <vxe-column field="employeeName" :title="t('attendance.overtime.columns.employeeName')" width="100" />
            <vxe-column field="overtimeDate" :title="t('attendance.overtime.columns.overtimeDate')" width="120" />
            <vxe-column field="startTime" :title="t('attendance.overtime.columns.startTime')" width="160" />
            <vxe-column field="endTime" :title="t('attendance.overtime.columns.endTime')" width="160" />
            <vxe-column field="overtimeHours" :title="t('attendance.overtime.columns.overtimeHours')" width="100" align="right" />
            <vxe-column field="overtimeType" :title="t('attendance.overtime.columns.overtimeType')" width="100">
              <template #default="{ row }">{{ otTypeMap[row.overtimeType] || row.overtimeType }}</template>
            </vxe-column>
            <vxe-column field="payRate" :title="t('attendance.overtime.columns.payRate')" width="80" align="right" />
            <vxe-column field="approvalStatus" :title="t('attendance.overtime.columns.approvalStatus')" width="100">
              <template #default="{ row }">
                <el-tag :type="(otStatusMap[row.approvalStatus]?.type as any) || 'info'">
                  {{ otStatusMap[row.approvalStatus]?.label || row.approvalStatus }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="approverName" :title="t('attendance.overtime.columns.approverName')" width="100" />
            <vxe-column field="reason" :title="t('attendance.overtime.columns.reason')" min-width="180" />
            <vxe-column :title="t('attendance.overtime.columns.action')" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:overtime:approve']" link type="primary" size="small" @click="handleApproveOt(row, 'APPROVED')">{{ t('attendance.common.buttons.pass') }}</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:overtime:approve']" link type="danger" size="small" @click="handleApproveOt(row, 'REJECTED')">{{ t('attendance.common.buttons.reject') }}</el-button>
              </template>
            </vxe-column>
          </vxe-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="otQuery.page"
              v-model:page-size="otQuery.size"
              :total="otTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchOvertime"
              @size-change="fetchOvertime"
            />
          </div>
        </el-tab-pane>

        <!-- 请假 -->
        <el-tab-pane :label="t('attendance.tabs.leave')" name="leave">
          <el-form inline :model="lvQuery" class="search-form">
            <el-form-item :label="t('attendance.common.search.employeeId')">
              <el-input-number v-model="lvQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item :label="t('attendance.common.search.approvalStatus')">
              <el-select v-model="lvQuery.approvalStatus" :placeholder="t('common.all')" clearable style="width: 140px">
                <el-option v-for="(v, k) in lvStatusMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="lvQuery.page = 1; fetchLeave()">{{ t('attendance.common.buttons.query') }}</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:leave:create']" type="primary" :icon="'Plus'" @click="openLvCreate">{{ t('attendance.leave.buttons.create') }}</el-button>
            <el-button :icon="'Refresh'" @click="fetchLeave">{{ t('attendance.common.buttons.refresh') }}</el-button>
          </div>

          <vxe-table :data="lvList" :loading="lvLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="leaveCode" :title="t('attendance.leave.columns.leaveCode')" width="180" />
            <vxe-column field="employeeName" :title="t('attendance.leave.columns.employeeName')" width="100" />
            <vxe-column field="leaveType" :title="t('attendance.leave.columns.leaveType')" width="100">
              <template #default="{ row }">{{ lvTypeMap[row.leaveType] || row.leaveType }}</template>
            </vxe-column>
            <vxe-column field="startDate" :title="t('attendance.leave.columns.startDate')" width="120" />
            <vxe-column field="endDate" :title="t('attendance.leave.columns.endDate')" width="120" />
            <vxe-column field="leaveDays" :title="t('attendance.leave.columns.leaveDays')" width="80" align="right" />
            <vxe-column field="approvalStatus" :title="t('attendance.leave.columns.approvalStatus')" width="100">
              <template #default="{ row }">
                <el-tag :type="(lvStatusMap[row.approvalStatus]?.type as any) || 'info'">
                  {{ lvStatusMap[row.approvalStatus]?.label || row.approvalStatus }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="approverName" :title="t('attendance.leave.columns.approverName')" width="100" />
            <vxe-column field="approvalRemark" :title="t('attendance.leave.columns.approvalRemark')" min-width="160" />
            <vxe-column field="reason" :title="t('attendance.leave.columns.reason')" min-width="180" />
            <vxe-column :title="t('attendance.leave.columns.action')" width="240" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus === 'DRAFT'" v-permission="['attendance:leave:approve']" link type="primary" size="small" @click="handleApproveLv(row, 'SUBMITTED')">{{ t('attendance.leave.actions.submit') }}</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:leave:approve']" link type="primary" size="small" @click="handleApproveLv(row, 'APPROVED')">{{ t('attendance.common.buttons.pass') }}</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:leave:approve']" link type="danger" size="small" @click="handleApproveLv(row, 'REJECTED')">{{ t('attendance.common.buttons.reject') }}</el-button>
              </template>
            </vxe-column>
          </vxe-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="lvQuery.page"
              v-model:page-size="lvQuery.size"
              :total="lvTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchLeave"
              @size-change="fetchLeave"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 出勤登记 -->
    <el-dialog v-model="attDialogVisible" :title="t('attendance.attendance.dialog.title')" width="480px">
      <el-form ref="formRef" :model="attForm" :rules="attFormRules" label-width="100px">
        <el-form-item :label="t('attendance.attendance.form.employeeId')" prop="employeeId">
          <el-input-number v-model="attForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.attendanceDate')" prop="attendanceDate">
          <el-date-picker v-model="attForm.attendanceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.checkInTime')">
          <el-date-picker v-model="attForm.checkInTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.checkOutTime')">
          <el-date-picker v-model="attForm.checkOutTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.workHours')">
          <el-input-number v-model="attForm.workHours" :min="0" :max="24" :step="0.5" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.overtimeHours')">
          <el-input-number v-model="attForm.overtimeHours" :min="0" :max="24" :step="0.5" />
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.status')">
          <el-select v-model="attForm.status" style="width: 100%">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.workType')">
          <el-select v-model="attForm.workType" style="width: 100%">
            <el-option v-for="(label, val) in workTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attendance.attendance.form.remark')">
          <el-input v-model="attForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="attDialogVisible = false">{{ t('attendance.common.buttons.cancel') }}</el-button>
        <el-button type="primary" @click="submitAtt">{{ t('attendance.common.buttons.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 加班申请 -->
    <el-dialog v-model="otDialogVisible" :title="t('attendance.overtime.dialog.title')" width="520px">
      <el-form ref="otFormRef" :model="otForm" :rules="otFormRules" label-width="100px">
        <el-form-item :label="t('attendance.overtime.form.employeeId')">
          <el-input-number v-model="otForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.overtimeDate')">
          <el-date-picker v-model="otForm.overtimeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.startTime')">
          <el-date-picker v-model="otForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.endTime')">
          <el-date-picker v-model="otForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.overtimeType')">
          <el-select v-model="otForm.overtimeType" style="width: 100%">
            <el-option v-for="(label, val) in otTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.payRate')">
          <el-input-number v-model="otForm.payRate" :min="1" :max="3" :step="0.5" />
        </el-form-item>
        <el-form-item :label="t('attendance.overtime.form.reason')">
          <el-input v-model="otForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="otDialogVisible = false">{{ t('attendance.common.buttons.cancel') }}</el-button>
        <el-button type="primary" @click="submitOt">{{ t('attendance.common.buttons.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 请假申请 -->
    <el-dialog v-model="lvDialogVisible" :title="t('attendance.leave.dialog.title')" width="520px">
      <el-form :model="lvForm" label-width="100px">
        <el-form-item :label="t('attendance.leave.form.employeeId')">
          <el-input-number v-model="lvForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('attendance.leave.form.leaveType')">
          <el-select v-model="lvForm.leaveType" style="width: 100%">
            <el-option v-for="(label, val) in lvTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('attendance.leave.form.startDate')">
          <el-date-picker v-model="lvForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.leave.form.endDate')">
          <el-date-picker v-model="lvForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('attendance.leave.form.reason')">
          <el-input v-model="lvForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lvDialogVisible = false">{{ t('attendance.common.buttons.cancel') }}</el-button>
        <el-button type="primary" @click="submitLv">{{ t('attendance.common.buttons.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.attendance-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
  .stat-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: $spacing-md; }
}
</style>
