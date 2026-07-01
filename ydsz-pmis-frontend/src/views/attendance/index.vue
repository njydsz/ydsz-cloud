<!--
  @file 考勤管理
  @description 考勤管理页面，整合出勤记录登记/查询、加班申请与审批、请假申请与审批三个 Tab，对接 @/api/attendance 模块。
  @module views/attendance
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

const statusMap: Record<string, { label: string; type: string }> = {
  NORMAL: { label: '正常', type: 'success' },
  LATE: { label: '迟到', type: 'warning' },
  EARLY: { label: '早退', type: 'warning' },
  ABSENT: { label: '缺勤', type: 'danger' },
  LEAVE: { label: '请假', type: 'info' },
  OVERTIME: { label: '加班', type: 'primary' },
}

const workTypeMap: Record<string, string> = { WORKDAY: '工作日', WEEKEND: '周末', HOLIDAY: '节假日' }

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
/** 出勤登记表单数据 */
const attForm = reactive<AttendanceCreateDTO>({
  employeeId: 0,
  employeeName: '',
  attendanceDate: new Date().toISOString().slice(0, 10),
  status: 'NORMAL',
  workType: 'WORKDAY',
  remark: '',
})
const attFormRules = {
  employeeId: [{ required: true, message: '员工 ID 必填', trigger: 'blur' }],
  attendanceDate: [{ required: true, message: '日期必填', trigger: 'change' }],
}

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
  await recordAttendance(attForm)
  ElMessage.success('登记成功')
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

const otTypeMap: Record<string, string> = { WORKDAY: '工作日', WEEKEND: '周末', HOLIDAY: '节假日' }
const otStatusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '已提交', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

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
  await submitOvertime(otForm)
  ElMessage.success('已提交')
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
    await ElMessageBox.confirm(`确认${action === 'APPROVED' ? '通过' : '驳回'}该加班申请?`, '提示', { type: 'warning' })
    await approveOvertime(row.id, action)
    ElMessage.success('已审批')
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

const lvTypeMap: Record<string, string> = {
  ANNUAL: '年假',
  SICK: '病假',
  PERSONAL: '事假',
  MARRIAGE: '婚假',
  MATERNITY: '产假/陪产假',
  BEREAVEMENT: '丧假',
  OTHER: '其他',
}
const lvStatusMap: Record<string, { label: string; type: string }> = otStatusMap

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
  ElMessage.success('已提交')
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
    const actionText = action === 'SUBMITTED' ? '提交' : action === 'APPROVED' ? '通过' : '驳回'
    await ElMessageBox.confirm(`确认${actionText}该请假申请?`, '提示', { type: 'warning' })
    await approveLeave(row.id, action)
    ElMessage.success('已操作')
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
        <el-tab-pane label="出勤记录" name="attendance">
          <el-form inline :model="attQuery" class="search-form">
            <el-form-item label="员工 ID">
              <el-input-number v-model="attQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item label="开始">
              <el-date-picker v-model="attQuery.startDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="结束">
              <el-date-picker v-model="attQuery.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="attQuery.page = 1; fetchAttendance()">查询</el-button>
              <el-button @click="attQuery.employeeId = undefined; attQuery.startDate = ''; attQuery.endDate = ''; fetchAttendance()">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:record:create']" type="primary" :icon="'Plus'" @click="openAttCreate">登记出勤</el-button>
            <el-button :icon="'Refresh'" @click="fetchAttendance">刷新</el-button>
          </div>

          <div v-if="attStat.length > 0" class="stat-row">
            <el-tag v-for="(s, idx) in attStat" :key="idx" :type="(statusMap[(s.status as string) || '']?.type as any) || 'info'" effect="plain" size="large">
              {{ statusMap[(s.status as string) || '']?.label || s.status || '未知' }}: {{ s.count }}天 / {{ s.total_hours }}h
            </el-tag>
          </div>

          <vxe-table :data="attList" :loading="attLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="employeeName" title="员工" width="120" />
            <vxe-column field="employeeId" title="员工 ID" width="100" />
            <vxe-column field="attendanceDate" title="出勤日期" width="120" />
            <vxe-column field="checkInTime" title="签到" width="160" />
            <vxe-column field="checkOutTime" title="签退" width="160" />
            <vxe-column field="workHours" title="工作时长" width="100" align="right" />
            <vxe-column field="overtimeHours" title="加班时长" width="100" align="right" />
            <vxe-column field="workType" title="工作日类型" width="100">
              <template #default="{ row }">{{ workTypeMap[row.workType || ''] || row.workType }}</template>
            </vxe-column>
            <vxe-column field="status" title="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(statusMap[row.status]?.type as any) || 'info'">
                  {{ statusMap[row.status]?.label || row.status }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="remark" title="备注" min-width="160" />
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
        <el-tab-pane label="加班申请" name="overtime">
          <el-form inline :model="otQuery" class="search-form">
            <el-form-item label="员工 ID">
              <el-input-number v-model="otQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item label="审批状态">
              <el-select v-model="otQuery.approvalStatus" placeholder="全部" clearable style="width: 140px">
                <el-option v-for="(v, k) in otStatusMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="otQuery.page = 1; fetchOvertime()">查询</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:overtime:create']" type="primary" :icon="'Plus'" @click="openOtCreate">提交加班</el-button>
            <el-button :icon="'Refresh'" @click="fetchOvertime">刷新</el-button>
          </div>

          <vxe-table :data="otList" :loading="otLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="overtimeCode" title="编码" width="180" />
            <vxe-column field="employeeName" title="员工" width="100" />
            <vxe-column field="overtimeDate" title="加班日期" width="120" />
            <vxe-column field="startTime" title="开始" width="160" />
            <vxe-column field="endTime" title="结束" width="160" />
            <vxe-column field="overtimeHours" title="小时数" width="100" align="right" />
            <vxe-column field="overtimeType" title="类型" width="100">
              <template #default="{ row }">{{ otTypeMap[row.overtimeType] || row.overtimeType }}</template>
            </vxe-column>
            <vxe-column field="payRate" title="倍数" width="80" align="right" />
            <vxe-column field="approvalStatus" title="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(otStatusMap[row.approvalStatus]?.type as any) || 'info'">
                  {{ otStatusMap[row.approvalStatus]?.label || row.approvalStatus }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="approverName" title="审批人" width="100" />
            <vxe-column field="reason" title="原因" min-width="180" />
            <vxe-column title="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:overtime:approve']" link type="primary" size="small" @click="handleApproveOt(row, 'APPROVED')">通过</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:overtime:approve']" link type="danger" size="small" @click="handleApproveOt(row, 'REJECTED')">驳回</el-button>
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
        <el-tab-pane label="请假申请" name="leave">
          <el-form inline :model="lvQuery" class="search-form">
            <el-form-item label="员工 ID">
              <el-input-number v-model="lvQuery.employeeId" :min="0" :controls="false" />
            </el-form-item>
            <el-form-item label="审批状态">
              <el-select v-model="lvQuery.approvalStatus" placeholder="全部" clearable style="width: 140px">
                <el-option v-for="(v, k) in lvStatusMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="lvQuery.page = 1; fetchLeave()">查询</el-button>
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button v-permission="['attendance:leave:create']" type="primary" :icon="'Plus'" @click="openLvCreate">提交请假</el-button>
            <el-button :icon="'Refresh'" @click="fetchLeave">刷新</el-button>
          </div>

          <vxe-table :data="lvList" :loading="lvLoading" border>
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="leaveCode" title="编码" width="180" />
            <vxe-column field="employeeName" title="员工" width="100" />
            <vxe-column field="leaveType" title="类型" width="100">
              <template #default="{ row }">{{ lvTypeMap[row.leaveType] || row.leaveType }}</template>
            </vxe-column>
            <vxe-column field="startDate" title="开始" width="120" />
            <vxe-column field="endDate" title="结束" width="120" />
            <vxe-column field="leaveDays" title="天数" width="80" align="right" />
            <vxe-column field="approvalStatus" title="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(lvStatusMap[row.approvalStatus]?.type as any) || 'info'">
                  {{ lvStatusMap[row.approvalStatus]?.label || row.approvalStatus }}
                </el-tag>
              </template>
            </vxe-column>
            <vxe-column field="approverName" title="审批人" width="100" />
            <vxe-column field="approvalRemark" title="审批意见" min-width="160" />
            <vxe-column field="reason" title="原因" min-width="180" />
            <vxe-column title="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus === 'DRAFT'" v-permission="['attendance:leave:approve']" link type="primary" size="small" @click="handleApproveLv(row, 'SUBMITTED')">提交</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:leave:approve']" link type="primary" size="small" @click="handleApproveLv(row, 'APPROVED')">通过</el-button>
                <el-button v-if="row.approvalStatus === 'SUBMITTED'" v-permission="['attendance:leave:approve']" link type="danger" size="small" @click="handleApproveLv(row, 'REJECTED')">驳回</el-button>
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
    <el-dialog v-model="attDialogVisible" title="登记出勤" width="480px">
      <el-form ref="formRef" :model="attForm" :rules="attFormRules" label-width="100px">
        <el-form-item label="员工 ID" prop="employeeId">
          <el-input-number v-model="attForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item label="出勤日期" prop="attendanceDate">
          <el-date-picker v-model="attForm.attendanceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签到时间">
          <el-date-picker v-model="attForm.checkInTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签退时间">
          <el-date-picker v-model="attForm.checkOutTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工作时长">
          <el-input-number v-model="attForm.workHours" :min="0" :max="24" :step="0.5" />
        </el-form-item>
        <el-form-item label="加班时长">
          <el-input-number v-model="attForm.overtimeHours" :min="0" :max="24" :step="0.5" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="attForm.status" style="width: 100%">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作日类型">
          <el-select v-model="attForm.workType" style="width: 100%">
            <el-option v-for="(label, val) in workTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="attForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="attDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAtt">确定</el-button>
      </template>
    </el-dialog>

    <!-- 加班申请 -->
    <el-dialog v-model="otDialogVisible" title="提交加班" width="520px">
      <el-form :model="otForm" label-width="100px">
        <el-form-item label="员工 ID">
          <el-input-number v-model="otForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item label="加班日期">
          <el-date-picker v-model="otForm.overtimeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="otForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="otForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加班类型">
          <el-select v-model="otForm.overtimeType" style="width: 100%">
            <el-option v-for="(label, val) in otTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="倍数">
          <el-input-number v-model="otForm.payRate" :min="1" :max="3" :step="0.5" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="otForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="otDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOt">确定</el-button>
      </template>
    </el-dialog>

    <!-- 请假申请 -->
    <el-dialog v-model="lvDialogVisible" title="提交请假" width="520px">
      <el-form :model="lvForm" label-width="100px">
        <el-form-item label="员工 ID">
          <el-input-number v-model="lvForm.employeeId" :min="1" />
        </el-form-item>
        <el-form-item label="请假类型">
          <el-select v-model="lvForm.leaveType" style="width: 100%">
            <el-option v-for="(label, val) in lvTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="lvForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="lvForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="lvForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lvDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitLv">确定</el-button>
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
