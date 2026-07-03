<script setup lang="ts">
/**
 * @file 委托授权管理页
 * @module views/workflow/delegate-auth
 * @description P1-2: 委托授权管理，包含"我设置的"和"代理给我的"两个 Tab，
 *   支持创建/撤回/启停授权，以及查看代理处理记录和被代理记录。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const myList = ref<DelegateAuthDTO[]>([])
const toMeList = ref<DelegateAuthDTO[]>([])
const delegateLogList = ref<DelegateLogDTO[]>([])
const ownerLogList = ref<DelegateLogDTO[]>([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// ==================== 创建授权弹窗 ====================
const createDialog = ref(false)
const creating = ref(false)
const createForm = reactive<CreateDelegateAuthDTO>({
  delegateId: undefined,
  delegateName: '',
  scopeType: 'ALL',
  scopeValue: '',
  startTime: '',
  endTime: '',
})
const dateRange = ref<[string, string] | null>(null)

// ==================== 范围类型映射 ====================
const scopeTypeMap: Record<DelegateScopeType, string> = {
  ALL: '全部流程',
  FLOW: '指定流程',
  FLOW_NODE: '指定流程节点',
  ROLE: '指定角色',
}

const scopeTypeOptions = [
  { label: '全部流程', value: 'ALL' },
  { label: '指定流程', value: 'FLOW' },
  { label: '指定流程节点', value: 'FLOW_NODE' },
  { label: '指定角色', value: 'ROLE' },
]

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
    ElMessage.error('加载数据失败：' + (e as Error).message)
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
      createForm.delegateId = u.id
      createForm.delegateName = u.realName || u.username || ''
    } else {
      createForm.delegateId = undefined
      createForm.delegateName = ''
    }
    return
  }
  if (user && typeof user === 'object') {
    createForm.delegateId = user.id
    createForm.delegateName = user.realName || user.username || ''
  } else {
    createForm.delegateId = undefined
    createForm.delegateName = ''
  }
}

async function submitCreate() {
  if (!createForm.delegateId) {
    ElMessage.warning('请选择代理人')
    return
  }
  if (createForm.scopeType !== 'ALL' && !createForm.scopeValue?.trim()) {
    ElMessage.warning('请输入范围值')
    return
  }

  // 处理时间范围
  if (dateRange.value && dateRange.value.length === 2) {
    createForm.startTime = dayjs(dateRange.value[0]).format('YYYY-MM-DD HH:mm:ss')
    createForm.endTime = dayjs(dateRange.value[1]).format('YYYY-MM-DD HH:mm:ss')
  }

  creating.value = true
  try {
    const res = await createDelegateAuth(createForm)
    if (res.data?.code === 0) {
      ElMessage.success('授权创建成功')
      createDialog.value = false
      loadData()
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建失败：' + (e as Error).message)
  } finally {
    creating.value = false
  }
}

// ==================== 撤回授权 ====================
async function handleRevoke(row: DelegateAuthDTO) {
  try {
    await ElMessageBox.confirm(`确认撤回对「${row.delegateName || row.delegateId}」的授权？`, '撤回确认', {
      type: 'warning',
    })
    const res = await revokeDelegateAuth(row.id)
    if (res.data?.code === 0) {
      ElMessage.success('已撤回')
      loadData()
    } else {
      ElMessage.error(res.data?.message || '撤回失败')
    }
  } catch {
    // 用户取消
  }
}

// ==================== 启停授权 ====================
async function handleToggle(row: DelegateAuthDTO) {
  const action = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}对「${row.delegateName || row.delegateId}」的授权？`, `${action}确认`, {
      type: 'warning',
    })
    const res = await toggleDelegateAuth(row.id, !row.enabled)
    if (res.data?.code === 0) {
      ElMessage.success(`${action}成功`)
      loadData()
    } else {
      ElMessage.error(res.data?.message || `${action}失败`)
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
          <h2>委托授权管理</h2>
          <p class="page-header__sub">管理工作流审批委托授权，支持将审批任务委托给他人代理</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>创建授权
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="page-body">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 我设置的 -->
        <el-tab-pane label="我设置的" name="mine">
          <el-table v-loading="loading" :data="myList" border stripe>
            <el-table-column prop="delegateName" label="代理人" min-width="100">
              <template #default="{ row }">
                {{ row.delegateName || row.delegateId }}
              </template>
            </el-table-column>
            <el-table-column prop="scopeType" label="授权范围" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ scopeTypeMap[row.scopeType as DelegateScopeType] || row.scopeType }}</el-tag>
                <span v-if="row.scopeValue" class="scope-value">{{ row.scopeValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" min-width="150">
              <template #default="{ row }">
                {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" min-width="150">
              <template #default="{ row }">
                {{ row.endTime ? dayjs(row.endTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.revoked" type="info" size="small">已撤回</el-tag>
                <el-tag v-else-if="row.enabled" type="success" size="small">启用</el-tag>
                <el-tag v-else type="warning" size="small">已停用</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="150">
              <template #default="{ row }">
                {{ row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.revoked"
                  size="small"
                  :type="row.enabled ? 'warning' : 'success'"
                  link
                  @click="handleToggle(row)"
                >{{ row.enabled ? '停用' : '启用' }}</el-button>
                <el-button
                  v-if="!row.revoked"
                  size="small"
                  type="danger"
                  link
                  @click="handleRevoke(row)"
                >撤回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 代理给我的 -->
        <el-tab-pane label="代理给我的" name="toMe">
          <el-table v-loading="loading" :data="toMeList" border stripe>
            <el-table-column prop="ownerName" label="授权人" min-width="100">
              <template #default="{ row }">
                {{ row.ownerName || row.ownerId }}
              </template>
            </el-table-column>
            <el-table-column prop="scopeType" label="授权范围" min-width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ scopeTypeMap[row.scopeType as DelegateScopeType] || row.scopeType }}</el-tag>
                <span v-if="row.scopeValue" class="scope-value">{{ row.scopeValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" min-width="150">
              <template #default="{ row }">
                {{ row.startTime ? dayjs(row.startTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" min-width="150">
              <template #default="{ row }">
                {{ row.endTime ? dayjs(row.endTime).format('YYYY-MM-DD HH:mm') : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.revoked" type="info" size="small">已撤回</el-tag>
                <el-tag v-else-if="row.enabled" type="success" size="small">启用</el-tag>
                <el-tag v-else type="warning" size="small">已停用</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 代理处理记录 -->
        <el-tab-pane label="代理处理记录" name="delegateLog">
          <el-table v-loading="loading" :data="delegateLogList" border stripe>
            <el-table-column prop="ownerName" label="原授权人" min-width="100">
              <template #default="{ row }">
                {{ row.ownerName || row.ownerId }}
              </template>
            </el-table-column>
            <el-table-column prop="flowName" label="流程名称" min-width="120" />
            <el-table-column prop="nodeName" label="节点名称" min-width="120" />
            <el-table-column prop="action" label="操作" min-width="80" />
            <el-table-column prop="operateTime" label="操作时间" min-width="150">
              <template #default="{ row }">
                {{ row.operateTime ? dayjs(row.operateTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 被代理记录 -->
        <el-tab-pane label="被代理记录" name="ownerLog">
          <el-table v-loading="loading" :data="ownerLogList" border stripe>
            <el-table-column prop="delegateName" label="代理人" min-width="100">
              <template #default="{ row }">
                {{ row.delegateName || row.delegateId }}
              </template>
            </el-table-column>
            <el-table-column prop="flowName" label="流程名称" min-width="120" />
            <el-table-column prop="nodeName" label="节点名称" min-width="120" />
            <el-table-column prop="action" label="操作" min-width="80" />
            <el-table-column prop="operateTime" label="操作时间" min-width="150">
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
    <el-dialog v-model="createDialog" title="创建委托授权" width="520px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="代理人" required>
          <UserPicker
            :model-value="createForm.delegateId"
            placeholder="搜索并选择代理人"
            @change="(_v, user) => onDelegateUserPicked(user)"
          />
        </el-form-item>
        <el-form-item label="授权范围" required>
          <el-select v-model="createForm.scopeType" style="width: 100%">
            <el-option
              v-for="opt in scopeTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.scopeType !== 'ALL'" label="范围值" required>
          <el-input
            v-model="createForm.scopeValue"
            :placeholder="
              createForm.scopeType === 'FLOW' ? '流程编码（如 leave_approval）' :
              createForm.scopeType === 'FLOW_NODE' ? '流程编码:节点编码（如 leave_approval:manager_approve）' :
              '角色编码（如 dept_manager）'
            "
          />
        </el-form-item>
        <el-form-item label="有效时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确认创建</el-button>
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
