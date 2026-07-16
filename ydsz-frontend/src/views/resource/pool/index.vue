<!--
  @file 资源池管理
  @description 资源池管理页面：提供资源池分页查询（按类型/状态筛选）及新增/编辑/删除。资源池类型分为总部池/事业部池/备用池，后端 PoolType.inferByLevel() 按职级推断归属：L1-L3→储备 / L4-L12→事业部 / L13+→总部。对应路由 /resource/pool，后端服务 ydsz-userinfo（端口 9002）。
  @module views/resource/pool
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  pageResourcePools,
  createResourcePool,
  updateResourcePool,
  deleteResourcePool,
} from '@/api/resource/pool'
import type { ResourcePoolVO, ResourcePoolCreateDTO } from '@/api/resource/pool/types'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const list = ref<ResourcePoolVO[]>([])
const total = ref(0)
// 分页查询条件：资源池类型 / 状态
const query = reactive({ page: 1, size: 10, poolType: '', status: '' })

// 资源池类型中文映射
const poolTypeMap = computed<Record<string, string>>(() => ({
  HEADQUARTER: t('resource.pool.poolType.HEADQUARTER'),
  DIVISION: t('resource.pool.poolType.DIVISION'),
  BACKUP: t('resource.pool.poolType.BACKUP'),
}))

/** 拉取资源池分页列表 */
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

const formRules = computed(() => ({
  poolCode: [{ required: true, message: t('resource.pool.rules.codeRequired'), trigger: 'blur' }],
  poolName: [{ required: true, message: t('resource.pool.rules.nameRequired'), trigger: 'blur' }],
  poolType: [{ required: true, message: t('resource.pool.rules.typeRequired'), trigger: 'change' }],
}))

/** 打开新增资源池弹窗，初始化表单默认值 */
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

/**
 * 打开编辑弹窗，回填行数据到表单
 * @param row 待编辑的资源池行数据
 */
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

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createResourcePool(form)
      ElMessage.success(t('resource.pool.messages.createSuccess'))
    } else if (form.id) {
      await updateResourcePool(form.id, form)
      ElMessage.success(t('resource.pool.messages.updateSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

/** 二次确认后删除资源池 */
async function handleDelete(row: ResourcePoolVO) {
  try {
    await ElMessageBox.confirm(t('resource.pool.messages.deletePrompt', { name: row.poolName }), t('common.tip'), { type: 'warning' })
    deleting.value = true
    try {
      await deleteResourcePool(row.id)
      ElMessage.success(t('resource.pool.messages.deleted'))
      fetchList()
    } finally {
      deleting.value = false
    }
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
        <el-form-item :label="t('resource.pool.search.type')">
          <el-select v-model="query.poolType" :placeholder="t('common.all')" clearable style="width: 140px">
            <el-option v-for="(label, val) in poolTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.pool.search.status')">
          <el-select v-model="query.status" :placeholder="t('common.all')" clearable style="width: 140px">
            <el-option :label="t('resource.pool.status.ENABLED')" value="ENABLED" />
            <el-option :label="t('resource.pool.status.DISABLED')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="query.page = 1; fetchList()">{{ t('resource.pool.buttons.query') }}</el-button>
          <el-button @click="query.poolType = ''; query.status = ''; fetchList()">{{ t('resource.pool.buttons.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['resource:pool:create']" type="primary" :icon="'Plus'" @click="openCreate">{{ t('resource.pool.buttons.create') }}</el-button>
        <el-button :icon="'Refresh'" @click="fetchList">{{ t('resource.pool.buttons.refresh') }}</el-button>
      </div>

      <vxe-table :data="list" :loading="loading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="poolCode" :title="t('resource.pool.columns.code')" width="140" />
        <vxe-column field="poolName" :title="t('resource.pool.columns.name')" min-width="160" />
        <vxe-column field="poolType" :title="t('resource.pool.columns.type')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.poolType === 'HEADQUARTER' ? 'danger' : row.poolType === 'DIVISION' ? 'success' : 'info'">
              {{ poolTypeMap[row.poolType] || row.poolType }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="levelRange" :title="t('resource.pool.columns.levelRange')" width="140" />
        <vxe-column field="departmentName" :title="t('resource.pool.columns.department')" min-width="160" />
        <vxe-column field="capacity" :title="t('resource.pool.columns.capacity')" width="80" align="center" />
        <vxe-column field="occupiedCount" :title="t('resource.pool.columns.occupied')" width="80" align="center" />
        <vxe-column field="managerName" :title="t('resource.pool.columns.manager')" width="120" />
        <vxe-column field="status" :title="t('resource.pool.columns.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? t('resource.pool.status.ENABLED') : t('resource.pool.status.DISABLED') }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="t('resource.pool.columns.action')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['resource:pool:update']" link type="primary" size="small" @click="openEdit(row)">{{ t('resource.pool.buttons.edit') }}</el-button>
            <el-button v-permission="['resource:pool:delete']" link type="danger" size="small" :loading="deleting" @click="handleDelete(row)">{{ t('resource.pool.buttons.delete') }}</el-button>
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
      :title="dialogMode === 'create' ? t('resource.pool.dialog.createTitle') : t('resource.pool.dialog.editTitle')"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('resource.pool.form.code')" prop="poolCode">
          <el-input v-model="form.poolCode" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.name')" prop="poolName">
          <el-input v-model="form.poolName" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.type')" prop="poolType">
          <el-select v-model="form.poolType" style="width: 100%">
            <el-option v-for="(label, val) in poolTypeMap" :key="val" :label="label" :value="val" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.levelRange')">
          <el-input v-model="form.levelRange" :placeholder="t('resource.pool.form.levelRangePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.departmentId')">
          <el-input-number v-model="form.departmentId" :min="0" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.capacity')">
          <el-input-number v-model="form.capacity" :min="0" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.managerId')">
          <el-input-number v-model="form.managerId" :min="0" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('resource.pool.form.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">{{ t('resource.pool.status.ENABLED') }}</el-radio>
            <el-radio value="DISABLED">{{ t('resource.pool.status.DISABLED') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ t('common.ok') }}</el-button>
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
