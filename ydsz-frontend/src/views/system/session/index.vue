<!--
  @file 会话管理
  @description 会话管理页面（管理员视角）：提供全量会话分页查询（按用户/状态/IP 过滤）、UA 解析可视化（设备/操作系统/浏览器）及强制下线功能。对应路由 /system/session。
  @module views/system/session
-->
<script setup lang="ts">
/**
 * 会话管理（管理员视角）
 *
 * 1) 全量会话分页（按用户/状态/IP 过滤）
 * 2) UA 解析 → 设备 / 操作系统 / 浏览器 可视化
 * 3) 强制下线任意会话
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminPageSessions,
  adminKickSession,
} from '@/api/user/session'
import type { UserSessionVO } from '@/api/user/session'
import { parseUserAgent } from '@/utils/device'

const { t } = useI18n()

/** 列表加载状态 */
const loading = ref(false)
/** 会话列表数据 */
const list = ref<UserSessionVO[]>([])
/** 会话列表总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({
  page: 1,
  size: 20,
  userId: undefined as number | undefined,
  status: '',
  clientIp: '',
})

const statusMap = computed<Record<string, { label: string; type: 'success' | 'info' | 'warning' | 'danger' }>>(() => ({
  ACTIVE: { label: t('system.session.status.ACTIVE'), type: 'success' },
  LOGOUT: { label: t('system.session.status.LOGOUT'), type: 'info' },
  EXPIRED: { label: t('system.session.status.EXPIRED'), type: 'warning' },
  KICKED: { label: t('system.session.status.KICKED'), type: 'danger' },
}))

/**
 * 时间字符串格式化（ISO → 'YYYY-MM-DD HH:mm:ss'）
 * @param s ISO 时间字符串
 * @returns 格式化后的时间，空值返回 '-'
 */
function fmt(s?: string) {
  return s ? s.replace('T', ' ').slice(0, 19) : '-'
}

/** 拉取会话分页列表（按用户/状态/IP 过滤） */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await adminPageSessions(query.page, query.size, {
      userId: query.userId,
      status: query.status || undefined,
      clientIp: query.clientIp || undefined,
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

/**
 * 强制下线指定会话，二次确认后执行
 * @param row 待下线的会话行数据
 */
async function onKick(row: UserSessionVO) {
  if (row.status !== 'ACTIVE') {
    ElMessage.warning(t('system.session.messages.notActive'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('system.session.messages.confirmKick', { user: row.username || row.userId, ip: row.clientIp || '-' }),
      t('system.session.messages.kickTitle'),
      { type: 'warning' },
    )
    await adminKickSession(row.sessionId)
    ElMessage.success(t('system.session.messages.kicked'))
    await fetchList()
  } catch { /* 用户取消 */ }
}

/** 重置查询条件并刷新列表 */
function onReset() {
  query.userId = undefined
  query.status = ''
  query.clientIp = ''
  query.page = 1
  fetchList()
}

// 概览统计（按当前页统计）
const summary = computed(() => {
  const all = list.value
  return {
    total: all.length,
    active: all.filter((s) => s.status === 'ACTIVE').length,
    devices: new Set(all.map((s) => parseUserAgent(s.userAgent).device)).size,
    ips: new Set(all.map((s) => s.clientIp).filter(Boolean)).size,
  }
})

onMounted(fetchList)
</script>

<template>
  <div class="session-mgmt">
    <!-- 概览 -->
    <el-row :gutter="12" class="summary-row">
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="num">{{ summary.total }}</div>
          <div class="label">{{ t('system.session.summary.total') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card active">
          <div class="num">{{ summary.active }}</div>
          <div class="label">{{ t('system.session.summary.active') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="num">{{ summary.devices }}</div>
          <div class="label">{{ t('system.session.summary.devices') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="summary-card">
          <div class="num">{{ summary.ips }}</div>
          <div class="label">{{ t('system.session.summary.ips') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 过滤栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline :model="query" @submit.prevent>
        <el-form-item :label="t('system.session.search.userId')">
          <el-input-number v-model="query.userId" :min="0" :controls="false" style="width: 140px" />
        </el-form-item>
        <el-form-item :label="t('system.session.search.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option value="ACTIVE" :label="t('system.session.status.ACTIVE')" />
            <el-option value="LOGOUT" :label="t('system.session.status.LOGOUT')" />
            <el-option value="EXPIRED" :label="t('system.session.status.EXPIRED')" />
            <el-option value="KICKED" :label="t('system.session.status.KICKED')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.session.search.ip')">
          <el-input v-model="query.clientIp" :placeholder="t('system.session.search.ipPlaceholder')" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">{{ t('system.session.buttons.query') }}</el-button>
          <el-button @click="onReset">{{ t('system.session.buttons.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="55" />
        <vxe-column field="userId" :title="t('system.session.columns.user')" width="100">
          <template #default="{ row }">
            <span>{{ row.username || row.userId }}</span>
          </template>
        </vxe-column>
        <vxe-column :title="t('system.session.columns.device')" width="120">
          <template #default="{ row }">
            <el-icon v-if="parseUserAgent(row.userAgent).device === 'DESKTOP'"><Monitor /></el-icon>
            <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'MOBILE'"><Iphone /></el-icon>
            <el-icon v-else-if="parseUserAgent(row.userAgent).device === 'TABLET'"><Tablet /></el-icon>
            <el-icon v-else><QuestionFilled /></el-icon>
            <span class="device-label">
              {{
                parseUserAgent(row.userAgent).device === 'DESKTOP'
                  ? t('system.session.device.DESKTOP')
                  : parseUserAgent(row.userAgent).device === 'MOBILE'
                  ? t('system.session.device.MOBILE')
                  : parseUserAgent(row.userAgent).device === 'TABLET'
                  ? t('system.session.device.TABLET')
                  : t('system.session.device.unknown')
              }}
            </span>
          </template>
        </vxe-column>
        <vxe-column :title="t('system.session.columns.osBrowser')" min-width="200">
          <template #default="{ row }">
            <div class="ua-line">
              <el-tag size="small" type="info">{{ parseUserAgent(row.userAgent).os }}</el-tag>
              <el-tag size="small" type="info">{{ parseUserAgent(row.userAgent).browser }}</el-tag>
            </div>
            <div class="ua-raw">{{ row.userAgent || '-' }}</div>
          </template>
        </vxe-column>
        <vxe-column field="clientIp" :title="t('system.session.columns.ip')" width="130" />
        <vxe-column field="loginAt" :title="t('system.session.columns.loginAt')" width="170">
          <template #default="{ row }">{{ fmt(row.loginAt) }}</template>
        </vxe-column>
        <vxe-column field="lastActiveAt" :title="t('system.session.columns.lastActiveAt')" width="170">
          <template #default="{ row }">{{ fmt(row.lastActiveAt) }}</template>
        </vxe-column>
        <vxe-column field="expireAt" :title="t('system.session.columns.expireAt')" width="170">
          <template #default="{ row }">{{ fmt(row.expireAt) }}</template>
        </vxe-column>
        <vxe-column field="status" :title="t('system.session.columns.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type || 'info') as any" size="small">
              {{ statusMap[row.status]?.label || row.status || '-' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="logoutReason" :title="t('system.session.columns.logoutReason')" min-width="160" show-overflow="tooltip" />
        <vxe-column :title="t('system.session.columns.action')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.status !== 'ACTIVE'"
              @click="onKick(row)"
            >
              {{ t('system.session.buttons.kick') }}
            </el-button>
          </template>
        </vxe-column>
        <template #empty>
          <el-empty :description="t('system.session.messages.empty')" />
        </template>
      </vxe-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.session-mgmt {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  .summary-row {
    .summary-card {
      text-align: center;
      .num {
        font-size: 26px;
        font-weight: 600;
        color: #303133;
      }
      .label {
        color: #909399;
        font-size: 13px;
        margin-top: 4px;
      }
      &.active {
        background: #f0f9eb;
        .num { color: #67c23a; }
      }
    }
  }
  .filter-card { background: #fff; }
  .device-label {
    margin-left: 4px;
    color: #606266;
    font-size: 12px;
  }
  .ua-line {
    display: flex; gap: 6px;
  }
  .ua-raw {
    color: #909399;
    font-size: 12px;
    margin-top: 4px;
    word-break: break-all;
    max-width: 320px;
  }
  .pager {
    margin-top: 12px;
    justify-content: flex-end;
    display: flex;
  }
}
</style>
