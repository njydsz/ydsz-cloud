<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictTypes,
  listDictItems,
  createDictType,
  deleteDictType,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  refreshDictCache,
} from '@/api/system/dict'
import type { DictTypeVO, DictItemVO, DictTypeFormDTO, DictItemFormDTO } from '@/api/system/dict/types'

const types = ref<DictTypeVO[]>([])
const items = ref<DictItemVO[]>([])
const currentType = ref<DictTypeVO | null>(null)
const itemsLoading = ref(false)

// 字典类型表单
const typeDialogVisible = ref(false)
const typeFormRef = ref()
const typeForm = reactive<DictTypeFormDTO>({
  typeCode: '',
  typeName: '',
  description: '',
})

// 字典项表单
const itemDialogVisible = ref(false)
const itemDialogMode = ref<'create' | 'edit'>('create')
const itemFormRef = ref()
const itemForm = reactive<DictItemFormDTO>({
  id: undefined,
  typeCode: '',
  itemCode: '',
  itemValue: '',
  sortOrder: 0,
  status: 'ENABLED',
})

const typeFormRules = {
  typeCode: [{ required: true, message: '类型编码必填', trigger: 'blur' }],
  typeName: [{ required: true, message: '类型名称必填', trigger: 'blur' }],
}
const itemFormRules = {
  itemCode: [{ required: true, message: '项编码必填', trigger: 'blur' }],
  itemValue: [{ required: true, message: '项值必填', trigger: 'blur' }],
}

async function fetchTypes() {
  const { data } = await listDictTypes()
  types.value = data || []
  if (types.value.length > 0 && !currentType.value) {
    selectType(types.value[0])
  }
}

async function selectType(t: DictTypeVO) {
  currentType.value = t
  itemsLoading.value = true
  try {
    const { data } = await listDictItems(t.typeCode)
    items.value = data || []
  } finally {
    itemsLoading.value = false
  }
}

function openTypeCreate() {
  Object.assign(typeForm, { typeCode: '', typeName: '', description: '' })
  typeDialogVisible.value = true
}

function openTypeEdit(t: DictTypeVO) {
  Object.assign(typeForm, { typeCode: t.typeCode, typeName: t.typeName, description: t.description })
  typeDialogVisible.value = true
}

async function submitType() {
  await typeFormRef.value?.validate()
  if (!typeForm.typeCode) return
  try {
    await createDictType(typeForm)
    ElMessage.success('创建成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
    return
  }
  typeDialogVisible.value = false
  fetchTypes()
}

async function handleDeleteType(t: DictTypeVO) {
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${t.typeName}」?该项下的字典项也会被删除`, '提示', { type: 'warning' })
    await deleteDictType(t.typeCode)
    ElMessage.success('删除成功')
    if (currentType.value?.typeCode === t.typeCode) {
      currentType.value = null
      items.value = []
    }
    fetchTypes()
  } catch {
    /* 取消 */
  }
}

function openItemCreate() {
  if (!currentType.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  itemDialogMode.value = 'create'
  Object.assign(itemForm, {
    id: undefined,
    typeCode: currentType.value.typeCode,
    itemCode: '',
    itemValue: '',
    sortOrder: items.value.length + 1,
    status: 'ENABLED',
  })
  itemDialogVisible.value = true
}

function openItemEdit(item: DictItemVO) {
  itemDialogMode.value = 'edit'
  Object.assign(itemForm, {
    id: item.id,
    typeCode: item.typeCode,
    itemCode: item.itemCode,
    itemValue: item.itemValue,
    sortOrder: item.sortOrder ?? 0,
    status: item.status,
  })
  itemDialogVisible.value = true
}

async function submitItem() {
  await itemFormRef.value?.validate()
  if (itemDialogMode.value === 'create') {
    await createDictItem(itemForm)
    ElMessage.success('创建成功')
  } else {
    await updateDictItem(itemForm.id!, itemForm)
    ElMessage.success('更新成功')
  }
  itemDialogVisible.value = false
  if (currentType.value) selectType(currentType.value)
}

async function handleDeleteItem(item: DictItemVO) {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${item.itemValue}」?`, '提示', { type: 'warning' })
    await deleteDictItem(item.id)
    ElMessage.success('删除成功')
    if (currentType.value) selectType(currentType.value)
  } catch {
    /* 取消 */
  }
}

async function handleRefresh() {
  if (!currentType.value) return
  await refreshDictCache(currentType.value.typeCode)
  ElMessage.success('缓存已刷新')
}

onMounted(fetchTypes)
</script>

<template>
  <div class="dict-page">
    <el-card shadow="never" class="left-card">
      <template #header>
        <div class="card-header">
          <span>字典类型</span>
          <el-button type="primary" link :icon="'Plus'" @click="openTypeCreate">新增</el-button>
        </div>
      </template>
      <div
        v-for="t in types"
        :key="t.typeCode"
        class="type-item"
        :class="{ active: currentType?.typeCode === t.typeCode }"
        @click="selectType(t)"
      >
        <span class="type-name">{{ t.typeName }}</span>
        <el-tag size="small" type="info">{{ t.typeCode }}</el-tag>
        <span class="type-actions">
          <el-button type="primary" link size="small" @click.stop="openTypeEdit(t)">编辑</el-button>
          <el-button type="danger" link size="small" @click.stop="handleDeleteType(t)">删除</el-button>
        </span>
      </div>
      <el-empty v-if="types.length === 0" description="暂无字典类型" :image-size="60" />
    </el-card>

    <el-card shadow="never" class="right-card">
      <template #header>
        <div class="card-header">
          <span>{{ currentType ? currentType.typeName + ' / 字典项' : '字典项' }}</span>
          <div>
            <el-button :icon="'Refresh'" @click="handleRefresh" :disabled="!currentType">刷新缓存</el-button>
            <el-button type="primary" :icon="'Plus'" @click="openItemCreate" :disabled="!currentType">新增项</el-button>
          </div>
        </div>
      </template>

      <vxe-table :data="items" :loading="itemsLoading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="itemCode" title="项编码" width="200" />
        <vxe-column field="itemValue" title="项值" />
        <vxe-column field="sortOrder" title="排序" width="80" align="center" />
        <vxe-column field="status" title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openItemEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDeleteItem(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 字典类型表单 -->
    <el-dialog v-model="typeDialogVisible" title="字典类型" width="500px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeFormRules" label-width="100px">
        <el-form-item label="类型编码" prop="typeCode">
          <el-input v-model="typeForm.typeCode" placeholder="例如: project_type" />
        </el-form-item>
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="typeForm.typeName" placeholder="例如: 项目类型" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典项表单 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogMode === 'create' ? '新增字典项' : '编辑字典项'"
      width="500px"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemFormRules" label-width="100px">
        <el-form-item label="所属类型">
          <el-input v-model="itemForm.typeCode" disabled />
        </el-form-item>
        <el-form-item label="项编码" prop="itemCode">
          <el-input v-model="itemForm.itemCode" :disabled="itemDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="项值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitItem">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dict-page {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: $spacing-md;

  .left-card { min-height: 600px; }
  .right-card { min-height: 600px; }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .type-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    cursor: pointer;
    border-radius: 4px;
    margin-bottom: 4px;

    &:hover { background: var(--el-fill-color-light); }
    &.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
  }
}
</style>
