<!--
  @fileoverview 委托授权管理页
  @description
    委托授权双视角管理：'我设置的'、'代理给我的'、'代理处理记录'、'被代理记录'。
    支持创建 / 撤回 / 启停授权，以及代理范围（ALL/FLOW/TASK）与生效时间配置。
    适用 PC 端后台办公场景。
  @module views/workflow/delegate-auth
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 委托授权管理页
 * @module views/workflow/delegate-auth
 * @description P1-2: 委托授权管理，包含"我设置的"和"代理给我的"两个 Tab，
 *   支持创建/撤回/启停授权，以及查看代理处理记录和被代理记录。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import {
  pageMyDelegateAuth,
  pageDelegateAuthToMe,
  pageDelegateLogs,
  pageOwnerLogs,
  createDelegateAuth,
  revokeDelegateAuth,
  toggleDelegateAuth,
} from '@/api/workflow'
import type {
  DelegateAuthDTO,
  DelegateLogDTO,
  CreateDelegateAuthDTO,
  DelegateScopeType,
} from '@/api/workflow/types'
import UserPicker from '@/components/common/UserPicker.vue'
import type { UserVO } from '@/api/system/user/types'

// ==================== Tab 切换 ====================
const activeTab = ref<'mine' | 'toMe' | 'delegateLog' | 'ownerLog'>('mine')

// ==================== 列表数据 ====================
/** "我设置的"授权列表 */
const myList = ref<DelegateAuthDTO[]>([])
/** "代理给我的"授权列表 */
const toMeList = ref<DelegateAuthDTO[]>([])
/** 代理处理记录列表 */
const delegateLogList = ref<DelegateLogDTO[]>([])
/** 被代理记录列表 */
const ownerLogList = ref<DelegateLogDTO[]>([])
/** 列表加载状态 */
const loading = ref(false)
/** 列表数据总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页条数 */
const pageSize = ref(10)

// ==================== 创建授权弹窗 ====================
const createDialog = ref(false)
const creating = ref(false)
const createForm = reactive<Omit<CreateDelegateAuthDTO, 'delegateId'> & { delegateId?: string }>({
  delegateId: undefined,
  delegateName: '',
  scopeType: 'ALL',
  scopeValue: '',
  startTime: '',
  endTime: '',
})
const dateRange = ref<[string, string] | null>(null)

// ==================== 范围类型映射 ====================
const { t } = useI18n()

const scopeTypeMap = computed<Record<DelegateScopeType, string>>(() => ({
  ALL: t('workflow.delegate.scope.all'),
  FLOW: t('workflow.delegate.scope.flow'),
  FLOW_NODE: t('workflow.delegate.scope.flowNode'),
  ROLE: t('workflow.delegate.scope.role'),
}))

const scopeTypeOptions = computed(() => [
  { label: t('workflow.delegate.scope.all'), value: 'ALL' },
  { label: t('workflow.delegate.scope.flow'), value: 'FLOW' },
  { label: t('workflow.delegate.scope.flowNode'), value: 'FLOW_NODE' },
  { label: t('workflow.delegate.scope.role'), value: 'ROLE' },
])

// ==================== 加载数据 ====================
async function loadData() {
  loading.value = true
  try {
    if (activeTab.value === 'mine') {
      const res = await pageMyDelegateAuth({ page: currentPage.value, size: pageSize.value })
      if (res.data?.code === 0 && res.data?.data) {
        myList.value = res.data.data.list || []
        total.value = res.data.data.total || 0
      }
    } else if (activeTab.value === 'toMe') {
      const res = await pageDelegateAuthToMe({ page: currentPage.value, size: pageSize.value })
      if (res.data?.code === 0 && res.data?.data) {
        toMeList.value = res.data.data.list || []
        total.value = res.data.data.total || 0
      }
    } else if (activeTab.value === 'delegateLog') {
      const res = await pageDelegateLogs({ page: currentPage.value, size: pageSize.value })
      if (res.data?.code === 0 && res.data?.data) {
        delegateLogList.value = res.data.data.list || []
        total.value = res.data.data.total || 0
      }
    } else if (activeTab.value === 'ownerLog') {
      const res = await pageOwnerLogs({ page: currentPage.value, size: pageSize.value })
      if (res.data?.code === 0 && res.data?.data) {
        ownerLogList.value = res.data.data.list || []
        total.value = res.data.data.total || 0
      }
    }
  } catch (e) {
    ElMessage.error(t('workflow.delegate.msg.loadFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

// ==================== 创建授权 ====================
function openCreateDialog() {
  createForm.delegateId = undefined
  createForm.delegateName = ''
  createForm.scopeType = 'ALL'
  createForm.scopeValue = ''
  createForm.startTime = ''
  createForm.endTime = ''
  dateRange.value = null
  createDialog.value = true
}

// 代理人选择回调
function onDelegateUserPicked(user: UserVO | UserVO[] | null) {
  if (Array.isArray(user)) {
    const u = user[0]
    if (u) {
      createForm.delegateId = String(u.id)
      createForm.delegateName = u.realName || u.username || ''
    } else {
      createForm.delegateId = undefined
      createForm.delegateName = ''
    }
    return
  }
  if (user && typeof user === 'object') {
    createForm.delegateId = String(user.id)
    createForm.delegateName = user.realName || user.username || ''
  } else {
    createForm.delegateId = undefined
    createForm.delegateName = ''
  }
}

async function submitCreate() {
  if (!createForm.delegateId) {
    ElMessage.warning(t('workflow.delegate.msg.delegateRequired'))
    return
  }
  if (createForm.scopeType !== 'ALL' && !createForm.scopeValue?.trim()) {
    ElMessage.warning(t('workflow.delegate.msg.scopeValueRequired'))
    return
  }

  // 处理时间范围
  if (dateRange.value && dateRange.value.length === 2) {
    createForm.startTime = dayjs(dateRange.value[0]).format('YYYY-MM-DD HH:mm:ss')
    createForm.endTime = dayjs(dateRange.value[1]).format('YYYY-MM-DD HH:mm:ss')
  }

  creating.value = true
  try {
    const res = await createDelegateAuth(createForm as CreateDelegateAuthDTO)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.delegate.msg.createSuccess'))
      createDialog.value = false
      loadData()
    } else {
      ElMessage.error(res.data?.message || t('workflow.delegate.msg.createFailed'))
    }
  } catch (e) {
    ElMessage.error(t('workflow.delegate.msg.createFailedWithMsg', { reason: (e as Error).message }))
  } finally {
    creating.value = false
  }
}

// ==================== 撤回授权 ====================
async function handleRevoke(row: DelegateAuthDTO) {
  try {
    await ElMessageBox.confirm(
      t('workflow.delegate.msg.revokeConfirm', { name: row.delegateName || row.delegateId }),
      t('workflow.delegate.msg.revokeConfirmTitle'),
      { type: 'warning' },
    )
    const res = await revokeDelegateAuth(row.id)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.delegate.msg.revokeSuccess'))
      loadData()
    } else {
      ElMessage.error(res.data?.message || t('workflow.delegate.msg.revokeFailed'))
    }
  } catch {
    // 用户取消
  }
}

// ==================== 启停授权 ====================
async function handleToggle(row: DelegateAuthDTO) {
  const action = row.enabled ? t('workflow.delegate.action.disable') : t('workflow.delegate.action.enable')
  try {
    await ElMessageBox.confirm(
      t('workflow.delegate.msg.toggleConfirm', { action, name: row.delegateName || row.delegateId }),
      t('workflow.delegate.msg.toggleConfirmTitle', { action }),
      { type: 'warning' },
    )
    const res = await toggleDelegateAuth(row.id, !row.enabled)
    if (res.data?.code === 0) {
      ElMessage.success(t('workflow.delegate.msg.toggleSuccess', { action }))
      loadData()
    } else {
      ElMessage.error(res.data?.message || t('workflow.delegate.msg.toggleFailed', { action }))
    }
  } catch {
    // 用户取消
  }
}

onMounted(() => loadData())
</script>

<template>
  <div class="page-delegate-auth">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h2>{{ t('workflow.delegate.title') }}</h2>
          <p class="page-header__sub">{{ t('workflow.delegate.subtitle') }}</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('workflow.delegate.create') }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="page-body">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 我设置的 -->
        <el-tab-pane :label="t('workflow.delegate.tabs.mine')" name="mine">
          <el-table v-loading="loading" :data="myList" border stripe>
            <el-table-column prop="delegateName" :label="t('workflow.delegate.columns.delegate')" min-width="100">
              <template #default="{ row }">
                {{ row.delegateName || row.delegateId }}
              </template>
            </el-table-column>
            <el-table-column prop="scopeType" :label="t('workflow.delegate.columns.scope')" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ scopeTypeMap[row.scopeType as DelegateScopeType] || row.scopeType }}</el-tag>
                <span v-if="row.scopeValue" class="scope-value">{{ row.scopeValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" :label="t('workflow.delegate.columns.startTime')" min-width="150">
              <template #default="{ row }">
                {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" :label="t('workflow.delegate.columns.endTime')" min-width="150">
              <template #default="{ row }">
                {{ row.endTime ? dayjs(row.endTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" :label="t('workflow.delegate.columns.status')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.revoked" type="info" size="small">{{ t('workflow.delegate.status.revoked') }}</el-tag>
                <el-tag v-else-if="row.enabled" type="success" size="small">{{ t('workflow.delegate.status.enabled') }}</el-tag>
                <el-tag v-else type="warning" size="small">{{ t('workflow.delegate.status.disabled') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('workflow.delegate.columns.createTime')" min-width="150">
              <template #default="{ row }">
                {{ row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.delegate.columns.operation')" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.revoked"
                  size="small"
                  :type="row.enabled ? 'warning' : 'success'"
                  link
                  @click="handleToggle(row as DelegateAuthDTO)"
                >{{ row.enabled ? t('workflow.delegate.action.disable') : t('workflow.delegate.action.enable') }}</el-button>
                <el-button
                  v-if="!row.revoked"
                  size="small"
                  type="danger"
                  link
                  @click="handleRevoke(row as DelegateAuthDTO)"
                >{{ t('workflow.delegate.action.revoke') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 代理给我的 -->
        <el-tab-pane :label="t('workflow.delegate.tabs.toMe')" name="toMe">
          <el-table v-loading="loading" :data="toMeList" border stripe>
            <el-table-column prop="ownerName" :label="t('workflow.delegate.columns.owner')" min-width="100">
              <template #default="{ row }">
                {{ row.ownerName || row.ownerId }}
              </template>
            </el-table-column>
            <el-table-column prop="scopeType" :label="t('workflow.delegate.columns.scope')" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ scopeTypeMap[row.scopeType as DelegateScopeType] || row.scopeType }}</el-tag>
                <span v-if="row.scopeValue" class="scope-value">{{ row.scopeValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" :label="t('workflow.delegate.columns.startTime')" min-width="150">
              <template #default="{ row }">
                {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" :label="t('workflow.delegate.columns.endTime')" min-width="150">
              <template #default="{ row }">
                {{ row.endTime ? dayjs(row.endTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" :label="t('workflow.delegate.columns.status')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.revoked" type="info" size="small">{{ t('workflow.delegate.status.revoked') }}</el-tag>
                <el-tag v-else-if="row.enabled" type="success" size="small">{{ t('workflow.delegate.status.enabled') }}</el-tag>
                <el-tag v-else type="warning" size="small">{{ t('workflow.delegate.status.disabled') }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 代理处理记录 -->
        <el-tab-pane :label="t('workflow.delegate.tabs.delegateLog')" name="delegateLog">
          <el-table v-loading="loading" :data="delegateLogList" border stripe>
            <el-table-column prop="ownerName" :label="t('workflow.delegate.columns.originalOwner')" min-width="100">
              <template #default="{ row }">
                {{ row.ownerName || row.ownerId }}
              </template>
            </el-table-column>
            <el-table-column prop="flowName" :label="t('workflow.delegate.columns.flowName')" min-width="120" />
            <el-table-column prop="nodeName" :label="t('workflow.delegate.columns.nodeName')" min-width="120" />
            <el-table-column prop="action" :label="t('workflow.delegate.columns.action')" min-width="80" />
            <el-table-column prop="operateTime" :label="t('workflow.delegate.columns.operateTime')" min-width="150">
              <template #default="{ row }">
                {{ row.operateTime ? dayjs(row.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 被代理记录 -->
        <el-tab-pane :label="t('workflow.delegate.tabs.ownerLog')" name="ownerLog">
          <el-table v-loading="loading" :data="ownerLogList" border stripe>
            <el-table-column prop="delegateName" :label="t('workflow.delegate.columns.delegate')" min-width="100">
              <template #default="{ row }">
                {{ row.delegateName || row.delegateId }}
              </template>
            </el-table-column>
            <el-table-column prop="flowName" :label="t('workflow.delegate.columns.flowName')" min-width="120" />
            <el-table-column prop="nodeName" :label="t('workflow.delegate.columns.nodeName')" min-width="120" />
            <el-table-column prop="action" :label="t('workflow.delegate.columns.action')" min-width="80" />
            <el-table-column prop="operateTime" :label="t('workflow.delegate.columns.operateTime')" min-width="150">
              <template #default="{ row }">
                {{ row.operateTime ? dayjs(row.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 创建授权弹窗 -->
    <el-dialog v-model="createDialog" :title="t('workflow.delegate.dialog.createTitle')" width="520px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('workflow.delegate.dialog.delegate')" required>
          <UserPicker
            :model-value="createForm.delegateId"
            :placeholder="t('workflow.delegate.dialog.delegatePlaceholder')"
            @change="(_v, user) => onDelegateUserPicked(user)"
          />
        </el-form-item>
        <el-form-item :label="t('workflow.delegate.dialog.scope')" required>
          <el-select v-model="createForm.scopeType" style="width: 100%">
            <el-option
              v-for="opt in scopeTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.scopeType !== 'ALL'" :label="t('workflow.delegate.dialog.scopeValue')" required>
          <el-input
            v-model="createForm.scopeValue"
            :placeholder="
              createForm.scopeType === 'FLOW' ? t('workflow.delegate.dialog.scopeValueFlowPlaceholder') :
              createForm.scopeType === 'FLOW_NODE' ? t('workflow.delegate.dialog.scopeValueFlowNodePlaceholder') :
              t('workflow.delegate.dialog.scopeValueRolePlaceholder')
            "
          />
        </el-form-item>
        <el-form-item :label="t('workflow.delegate.dialog.validTime')">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            :range-separator="t('workflow.delegate.dialog.dateSeparator')"
            :start-placeholder="t('workflow.delegate.dialog.startDate')"
            :end-placeholder="t('workflow.delegate.dialog.endDate')"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">{{ t('workflow.delegate.dialog.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">{{ t('workflow.delegate.dialog.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-delegate-auth {
  padding: 16px;
}

.page-header {
  margin-bottom: 16px;

  &-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
  }

  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.page-body {
  border-radius: 6px;
}

.scope-value {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
