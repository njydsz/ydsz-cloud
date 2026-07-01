<!--
  @file 审计中心
  @description 审计中心页面，整合操作日志、登录审计、敏感操作、数据导出 4 个 Tab，对接 @/api/audit 模块。
  @module views/audit
-->
<script setup lang="ts">
/**
 * 审计中心
 *
 * 整合 4 个 Tab：
 * 1) 操作日志 — 来自 OperationLogAspect
 * 2) 登录审计 — 来自 LoginAuditListener
 * 3) 敏感操作 — 来自 RequireReAuthAspect
 * 4) 数据导出 — 来自 DataExportAuditAspect
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageOperationLog,
  pageLoginAudit,
  pageSensitiveOp,
  pageDataExport,
  cleanOperationLog,
} from '@/api/audit'
import type {
  OperationLogVO,
  LoginAuditVO,
  SensitiveOperationVO,
  DataExportAuditVO,
} from '@/api/audit'

/** 审计 Tab 类型：operation-操作日志 / login-登录审计 / sensitive-敏感操作 / export-数据导出 */
type Tab = 'operation' | 'login' | 'sensitive' | 'export'
/** 当前激活的 Tab */
const activeTab = ref<Tab>('operation')

/** 操作日志列表加载状态 */
const opLoading = ref(false)
/** 操作日志列表 */
const opList = ref<OperationLogVO[]>([])
/** 操作日志总条数 */
const opTotal = ref(0)
/** 操作日志查询条件 */
const opQuery = reactive({
  page: 1,
  size: 20,
  userId: undefined as number | undefined,
  module: '',
  bizType: '',
  status: '',
})

/** 登录审计列表加载状态 */
const loginLoading = ref(false)
/** 登录审计列表 */
const loginList = ref<LoginAuditVO[]>([])
/** 登录审计总条数 */
const loginTotal = ref(0)
/** 登录审计查询条件 */
const loginQuery = reactive({
  page: 1,
  size: 20,
  username: '',
  status: '',
  loginIp: '',
})

/** 敏感操作列表加载状态 */
const soLoading = ref(false)
/** 敏感操作列表 */
const soList = ref<SensitiveOperationVO[]>([])
/** 敏感操作总条数 */
const soTotal = ref(0)
/** 敏感操作查询条件 */
const soQuery = reactive({
  page: 1,
  size: 20,
  userId: undefined as number | undefined,
  opType: '',
})

/** 数据导出列表加载状态 */
const exLoading = ref(false)
/** 数据导出列表 */
const exList = ref<DataExportAuditVO[]>([])
/** 数据导出总条数 */
const exTotal = ref(0)
/** 数据导出查询条件 */
const exQuery = reactive({
  page: 1,
  size: 20,
  userId: undefined as number | undefined,
  exportModule: '',
  exportAction: '',
})

/** 拉取操作日志分页列表 */
async function fetchOperation() {
  opLoading.value = true
  try {
    const { data } = await pageOperationLog(opQuery.page, opQuery.size, {
      userId: opQuery.userId,
      module: opQuery.module || undefined,
      bizType: opQuery.bizType || undefined,
      status: opQuery.status || undefined,
    })
    opList.value = data?.list || []
    opTotal.value = data?.total || 0
  } finally {
    opLoading.value = false
  }
}

/** 拉取登录审计分页列表 */
async function fetchLogin() {
  loginLoading.value = true
  try {
    const { data } = await pageLoginAudit(loginQuery.page, loginQuery.size, {
      username: loginQuery.username || undefined,
      status: loginQuery.status || undefined,
      loginIp: loginQuery.loginIp || undefined,
    })
    loginList.value = data?.list || []
    loginTotal.value = data?.total || 0
  } finally {
    loginLoading.value = false
  }
}

/** 拉取敏感操作分页列表 */
async function fetchSensitive() {
  soLoading.value = true
  try {
    const { data } = await pageSensitiveOp(soQuery.page, soQuery.size, {
      userId: soQuery.userId,
      opType: soQuery.opType || undefined,
    })
    soList.value = data?.list || []
    soTotal.value = data?.total || 0
  } finally {
    soLoading.value = false
  }
}

/** 拉取数据导出审计分页列表 */
async function fetchExport() {
  exLoading.value = true
  try {
    const { data } = await pageDataExport(exQuery.page, exQuery.size, {
      userId: exQuery.userId,
      exportModule: exQuery.exportModule || undefined,
      exportAction: exQuery.exportAction || undefined,
    })
    exList.value = data?.list || []
    exTotal.value = data?.total || 0
  } finally {
    exLoading.value = false
  }
}

/**
 * Tab 切换回调，按需懒加载对应数据
 * @param tab 切换后的 Tab 标识
 */
function onTabChange(tab: Tab) {
  activeTab.value = tab
  if (tab === 'operation') fetchOperation()
  else if (tab === 'login') fetchLogin()
  else if (tab === 'sensitive') fetchSensitive()
  else fetchExport()
}

/**
 * 格式化 ISO 时间字符串为 yyyy-MM-dd HH:mm:ss
 * @param s ISO 时间字符串
 * @returns 格式化后的时间，空值返回 '-'
 */
function fmtDate(s?: string) {
  return s ? s.replace('T', ' ').slice(0, 19) : '-'
}

/**
 * 根据审计状态文本推断 el-tag 类型
 * @param status 状态文本（SUCCESS/FAILED/FAIL_PASSWORD 等）
 * @returns el-tag type：success / danger / warning / info
 */
function statusTag(status?: string) {
  const t = (status || '').toUpperCase()
  if (t === 'SUCCESS' || t === 'OK' || t === 'PASS') return 'success'
  if (t === 'FAILED' || t === 'FAIL' || t === 'FAIL_PASSWORD' || t === 'FAIL_LOCKED')
    return 'danger'
  if (t.startsWith('WARN') || t === 'YELLOW') return 'warning'
  return 'info'
}

/** 清理指定天数前的操作日志，需输入天数并二次确认 */
async function handleClean() {
  try {
    const { value } = await ElMessageBox.prompt('清理 N 天前的操作日志', '清理日志', {
      inputValue: '90',
      inputValidator: (v) => /^\d+$/.test(v) || '请输入正整数',
    })
    const days = Number(value)
    const { data } = await cleanOperationLog(days)
    ElMessage.success(`已清理 ${data} 条日志`)
    fetchOperation()
  } catch {
    /* canceled */
  }
}

onMounted(fetchOperation)
</script>

<template>
  <div class="audit-center">
    <el-tabs v-model="activeTab" type="card" class="audit-tabs" @tab-change="(t: any) => onTabChange(t as Tab)">
      <!-- Tab 1: 操作日志 -->
      <el-tab-pane name="operation" label="操作日志">
        <div class="search-bar">
          <el-form inline :model="opQuery" @submit.prevent>
            <el-form-item label="模块">
              <el-input v-model="opQuery.module" placeholder="如 project" clearable />
            </el-form-item>
            <el-form-item label="业务类型">
              <el-input v-model="opQuery.bizType" placeholder="如 opportunity" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="opQuery.status" clearable placeholder="全部" style="width: 110px">
                <el-option value="SUCCESS" label="成功" />
                <el-option value="FAILED" label="失败" />
              </el-select>
            </el-form-item>
            <el-form-item label="用户 ID">
              <el-input-number v-model="opQuery.userId" :min="0" :controls="false" style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="opQuery.page = 1; fetchOperation()">查询</el-button>
              <el-button @click="opQuery.module = ''; opQuery.bizType = ''; opQuery.status = ''; opQuery.userId = undefined; opQuery.page = 1; fetchOperation()">重置</el-button>
              <el-button type="danger" plain @click="handleClean">清理 90 天前</el-button>
            </el-form-item>
          </el-form>
        </div>

        <vxe-table :data="opList" :loading="opLoading" border stripe>
          <vxe-column type="seq" title="#" width="55" />
          <vxe-column field="createdAt" title="时间" width="170">
            <template #default="{ row }">{{ fmtDate(row.createdAt) }}</template>
          </vxe-column>
          <vxe-column field="module" title="模块" width="100" />
          <vxe-column field="action" title="操作" width="120" />
          <vxe-column field="bizType" title="业务" width="120" />
          <vxe-column field="bizId" title="业务ID" width="120" />
          <vxe-column field="username" title="用户" width="110" />
          <vxe-column field="httpMethod" title="方法" width="80" />
          <vxe-column field="requestUrl" title="URL" min-width="200" show-overflow="tooltip" />
          <vxe-column field="status" title="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status) as any" size="small">{{ row.status }}</el-tag>
            </template>
          </vxe-column>
          <vxe-column field="costMs" title="耗时(ms)" width="100" />
          <vxe-column field="clientIp" title="IP" width="120" />
          <vxe-column field="errorMessage" title="错误" min-width="160" show-overflow="tooltip" />
        </vxe-table>

        <el-pagination
          v-model:current-page="opQuery.page"
          v-model:page-size="opQuery.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="opTotal"
          layout="total, sizes, prev, pager, next, jumper"
          class="pager"
          @size-change="fetchOperation"
          @current-change="fetchOperation"
        />
      </el-tab-pane>

      <!-- Tab 2: 登录审计 -->
      <el-tab-pane name="login" label="登录审计">
        <div class="search-bar">
          <el-form inline :model="loginQuery" @submit.prevent>
            <el-form-item label="用户名">
              <el-input v-model="loginQuery.username" placeholder="登录账号" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="loginQuery.status" clearable placeholder="全部" style="width: 130px">
                <el-option value="SUCCESS" label="成功" />
                <el-option value="FAIL_PASSWORD" label="密码错误" />
                <el-option value="FAIL_LOCKED" label="已锁定" />
                <el-option value="FAIL_2FA" label="2FA 失败" />
                <el-option value="FAIL_DISABLED" label="已禁用" />
                <el-option value="LOGOUT" label="登出" />
              </el-select>
            </el-form-item>
            <el-form-item label="IP">
              <el-input v-model="loginQuery.loginIp" placeholder="客户端 IP" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loginQuery.page = 1; fetchLogin()">查询</el-button>
              <el-button @click="loginQuery.username = ''; loginQuery.status = ''; loginQuery.loginIp = ''; loginQuery.page = 1; fetchLogin()">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <vxe-table :data="loginList" :loading="loginLoading" border stripe>
          <vxe-column type="seq" title="#" width="55" />
          <vxe-column field="loginAt" title="时间" width="170">
            <template #default="{ row }">{{ fmtDate(row.loginAt) }}</template>
          </vxe-column>
          <vxe-column field="username" title="用户名" width="120" />
          <vxe-column field="status" title="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status) as any" size="small">{{ row.status }}</el-tag>
            </template>
          </vxe-column>
          <vxe-column field="loginIp" title="IP" width="120" />
          <vxe-column field="userAgent" title="UA" min-width="220" show-overflow="tooltip" />
          <vxe-column field="mfaUsed" title="2FA" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.mfaUsed" :type="row.mfaSuccess ? 'success' : 'danger'" size="small">
                {{ row.mfaSuccess ? '通过' : '失败' }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </vxe-column>
          <vxe-column field="failReason" title="失败原因" min-width="180" show-overflow="tooltip" />
          <vxe-column field="traceId" title="Trace" width="220" show-overflow="tooltip" />
        </vxe-table>

        <el-pagination
          v-model:current-page="loginQuery.page"
          v-model:page-size="loginQuery.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="loginTotal"
          layout="total, sizes, prev, pager, next, jumper"
          class="pager"
          @size-change="fetchLogin"
          @current-change="fetchLogin"
        />
      </el-tab-pane>

      <!-- Tab 3: 敏感操作 -->
      <el-tab-pane name="sensitive" label="敏感操作">
        <div class="search-bar">
          <el-form inline :model="soQuery" @submit.prevent>
            <el-form-item label="操作类型">
              <el-input v-model="soQuery.opType" placeholder="如 BATCH_DELETE" clearable />
            </el-form-item>
            <el-form-item label="用户 ID">
              <el-input-number v-model="soQuery.userId" :min="0" :controls="false" style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="soQuery.page = 1; fetchSensitive()">查询</el-button>
              <el-button @click="soQuery.opType = ''; soQuery.userId = undefined; soQuery.page = 1; fetchSensitive()">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <vxe-table :data="soList" :loading="soLoading" border stripe>
          <vxe-column type="seq" title="#" width="55" />
          <vxe-column field="operatedAt" title="时间" width="170">
            <template #default="{ row }">{{ fmtDate(row.operatedAt) }}</template>
          </vxe-column>
          <vxe-column field="username" title="用户" width="110" />
          <vxe-column field="opType" title="操作类型" width="160" />
          <vxe-column field="opTarget" title="目标" width="160" />
          <vxe-column field="targetId" title="目标 ID" width="120" />
          <vxe-column field="opResult" title="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.opResult) as any" size="small">{{ row.opResult }}</el-tag>
            </template>
          </vxe-column>
          <vxe-column field="reAuthUsed" title="二次认证" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.reAuthUsed" type="success" size="small">已验证</el-tag>
              <span v-else>-</span>
            </template>
          </vxe-column>
          <vxe-column field="clientIp" title="IP" width="120" />
          <vxe-column field="traceId" title="Trace" min-width="180" show-overflow="tooltip" />
        </vxe-table>

        <el-pagination
          v-model:current-page="soQuery.page"
          v-model:page-size="soQuery.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="soTotal"
          layout="total, sizes, prev, pager, next, jumper"
          class="pager"
          @size-change="fetchSensitive"
          @current-change="fetchSensitive"
        />
      </el-tab-pane>

      <!-- Tab 4: 数据导出 -->
      <el-tab-pane name="export" label="数据导出">
        <div class="search-bar">
          <el-form inline :model="exQuery" @submit.prevent>
            <el-form-item label="导出模块">
              <el-input v-model="exQuery.exportModule" placeholder="如 report" clearable />
            </el-form-item>
            <el-form-item label="导出动作">
              <el-input v-model="exQuery.exportAction" placeholder="如 export_excel" clearable />
            </el-form-item>
            <el-form-item label="用户 ID">
              <el-input-number v-model="exQuery.userId" :min="0" :controls="false" style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="exQuery.page = 1; fetchExport()">查询</el-button>
              <el-button @click="exQuery.exportModule = ''; exQuery.exportAction = ''; exQuery.userId = undefined; exQuery.page = 1; fetchExport()">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <vxe-table :data="exList" :loading="exLoading" border stripe>
          <vxe-column type="seq" title="#" width="55" />
          <vxe-column field="exportedAt" title="时间" width="170">
            <template #default="{ row }">{{ fmtDate(row.exportedAt) }}</template>
          </vxe-column>
          <vxe-column field="username" title="用户" width="110" />
          <vxe-column field="exportModule" title="模块" width="120" />
          <vxe-column field="exportAction" title="动作" width="160" />
          <vxe-column field="bizType" title="业务" width="120" />
          <vxe-column field="rowCount" title="行数" width="100" />
          <vxe-column field="fileName" title="文件" min-width="200" show-overflow="tooltip" />
          <vxe-column field="fileSize" title="大小(B)" width="120" />
          <vxe-column field="exportFormat" title="格式" width="80" />
          <vxe-column field="clientIp" title="IP" width="120" />
          <vxe-column field="traceId" title="Trace" min-width="180" show-overflow="tooltip" />
        </vxe-table>

        <el-pagination
          v-model:current-page="exQuery.page"
          v-model:page-size="exQuery.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="exTotal"
          layout="total, sizes, prev, pager, next, jumper"
          class="pager"
          @size-change="fetchExport"
          @current-change="fetchExport"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.audit-center {
  padding: 16px;
  background: #fff;
  border-radius: 6px;
  min-height: calc(100vh - 110px);
}
.audit-tabs {
  margin-top: 4px;
}
.search-bar {
  background: #f7f8fa;
  padding: 12px 16px;
  border-radius: 4px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
  display: flex;
}
:deep(.el-tabs__nav) {
  margin-left: 12px;
}
</style>
