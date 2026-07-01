<!--
  @file 资源池管理
  @description 资源池管理页面：提供资源池分页查询（按类型/状态筛选）及新增/编辑/删除。资源池类型分为总部池/事业部池/备用池，后端 PoolType.inferByLevel() 按职级推断归属：L1-L3→储备 / L4-L12→事业部 / L13+→总部。对应路由 /resource/pool，后端服务 ydsz-pmis-user（端口 9002）。
  @module views/resource/pool
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageResourcePools,
  createResourcePool,
  updateResourcePool,
  deleteResourcePool,
} from '@/api/resource/pool'
import type { ResourcePoolVO, ResourcePoolCreateDTO } from '@/api/resource/pool/types'

const loading = ref(false)
const list = ref<ResourcePoolVO[]>([])
const total = ref(0)
// 分页查询条件：资源池类型 / 状态
const query = reactive({ page: 1, size: 10, poolType: '', status: '' })

// 资源池类型中文映射
const poolTypeMap: Record<string, string> = {
  HEADQUARTER: '总部池',
  DIVISION: '事业部池',
  BACKUP: '备用池',
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageResourcePools(query.page, query.size, query.poolType, query.status)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<ResourcePoolCreateDTO & { id?: number }>({
  poolCode: '',
  poolName: '',
  poolType: 'DIVISION',
  levelRange: '',
  departmentId: undefined,
  capacity: 0,
  managerId: undefined,
  description: '',
  status: 'ENABLED',
})

const formRules = {
  poolCode: [{ required: true, message: '资源池编码必填', trigger: 'blur' }],
  poolName: [{ required: true, message: '资源池名称必填', trigger: 'blur' }],
  poolType: [{ required: true, message: '类型必填', trigger: 'change' }],
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    poolCode: '',
    poolName: '',
    poolType: 'DIVISION',
    levelRange: '',
    departmentId: undefined,
    capacity: 0,
    managerId: undefined,
    description: '',
    status: 'ENABLED',
  })
  dialogVisible.value = true
}

function openEdit(row: ResourcePoolVO) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    poolCode: row.poolCode,
    poolName: row.poolName,
    poolType: row.poolType,
    levelRange: row.levelRange,
    departmentId: row.departmentId,
    capacity: row.capacity,
    managerId: row.managerId,
    description: row.description,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (dialogMode.value === 'create') {
    await createResourcePool(form)
    ElMessage.success('创建成功')
  } else if (form.id) {
    await updateResourcePool(form.id, form)
    ElMessage.success('更新成功')
  }
  dialogVisible.value = false
  fetchList()
}

/** 二次确认后删除资源池 */
async function handleDelete(row: ResourcePoolVO) {
  try {
    await ElMessageBox.confirm(`确认删除资源池「${row.poolName}」?`, '提示', { type: 'warning' })
    await deleteResourcePool(row.id)
    ElMessage.success('已删除')
    fetchList()
  } catch {
    /* 取消 */
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="pool-page">
    <!-- 资源池列表（含查询/工具栏/表格/分页） -->
    <el-card shadow="never">
      <el-form inline :model="query" class="search-form">
        <el-form-item label="类型">
          <el-select v-model="query.poolType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(label, val) in poolTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">查询</el-button>
          <el-button @click="query.poolType = ''; query.status = ''; fetchList()">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['resource:pool:create']" type="primary" :icon="'Plus'" @click="openCreate">新增资源池</el-button>
        <el-button :icon="'Refresh'" @click="fetchList">刷新</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="poolCode" title="编码" width="140" />
        <vxe-column field="poolName" title="名称" min-width="160" />
        <vxe-column field="poolType" title="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.poolType === 'HEADQUARTER' ? 'danger' : row.poolType === 'DIVISION' ? 'success' : 'info'">
              {{ poolTypeMap[row.poolType] || row.poolType }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="levelRange" title="职级范围" width="140" />
        <vxe-column field="departmentName" title="所属部门" min-width="160" />
        <vxe-column field="capacity" title="容量" width="80" align="center" />
        <vxe-column field="occupiedCount" title="已用" width="80" align="center" />
        <vxe-column field="managerName" title="负责人" width="120" />
        <vxe-column field="status" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['resource:pool:update']" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['resource:pool:delete']" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增资源池' : '编辑资源池'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="编码" prop="poolCode">
          <el-input v-model="form.poolCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="名称" prop="poolName">
          <el-input v-model="form.poolName" />
        </el-form-item>
        <el-form-item label="类型" prop="poolType">
          <el-select v-model="form.poolType" style="width: 100%">
            <el-option v-for="(label, val) in poolTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item label="职级范围">
          <el-input v-model="form.levelRange" placeholder="例如: L4-L12" />
        </el-form-item>
        <el-form-item label="部门 ID">
          <el-input-number v-model="form.departmentId" :min="0" />
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="0" />
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="form.managerId" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
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
.pool-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar { margin-bottom: $spacing-md; }
  .pagination { margin-top: $spacing-md; display: flex; justify-content: flex-end; }
}
</style>
