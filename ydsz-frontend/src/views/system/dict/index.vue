<!--
  @file 数据字典
  @description 数据字典管理页面：左侧维护字典类型，右侧维护所选类型下的字典项，支持新增/编辑/删除及缓存刷新。对应路由 /system/dict。
  @module views/system/dict
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { useModalA11y } from '@/composables/useModalA11y'

const { t } = useI18n()

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
  typeCode: [{ required: true, message: t('system.dict.type.rules.typeCodeRequired'), trigger: 'blur' }],
  typeName: [{ required: true, message: t('system.dict.type.rules.typeNameRequired'), trigger: 'blur' }],
}

// 无障碍访问增强：对话框关闭后恢复焦点到打开前的触发元素（focus trap 由 Element Plus 内置）
useModalA11y(typeDialogVisible)
useModalA11y(itemDialogVisible)
const itemFormRules = {
  itemCode: [{ required: true, message: t('system.dict.item.rules.itemCodeRequired'), trigger: 'blur' }],
  itemValue: [{ required: true, message: t('system.dict.item.rules.itemValueRequired'), trigger: 'blur' }],
}

/** 拉取字典类型列表，并默认选中首个类型 */
async function fetchTypes() {
  const { data } = await listDictTypes()
  types.value = data || []
  if (types.value.length > 0 && !currentType.value) {
    selectType(types.value[0])
  }
}

/** 选中某个字典类型，拉取其下的字典项 */
async function selectType(selected: DictTypeVO) {
  currentType.value = selected
  itemsLoading.value = true
  try {
    const { data } = await listDictItems(selected.typeCode)
    items.value = data || []
  } finally {
    itemsLoading.value = false
  }
}

/** 打开字典类型新增弹窗 */
function openTypeCreate() {
  Object.assign(typeForm, { typeCode: '', typeName: '', description: '' })
  typeDialogVisible.value = true
}

/** 打开字典类型编辑弹窗，回填数据 */
function openTypeEdit(selected: DictTypeVO) {
  Object.assign(typeForm, { typeCode: selected.typeCode, typeName: selected.typeName, description: selected.description })
  typeDialogVisible.value = true
}

/** 提交字典类型（仅新增），成功后刷新类型列表 */
async function submitType() {
  await typeFormRef.value?.validate()
  if (!typeForm.typeCode) return
  try {
    await createDictType(typeForm)
    ElMessage.success(t('system.dict.type.messages.createSuccess'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('system.dict.type.messages.createFailed'))
    return
  }
  typeDialogVisible.value = false
  fetchTypes()
}

/** 删除字典类型（其下字典项一并删除），二次确认后执行 */
async function handleDeleteType(selected: DictTypeVO) {
  try {
    await ElMessageBox.confirm(t('system.dict.type.messages.confirmDelete', { name: selected.typeName }), t('common.tip'), { type: 'warning' })
    await deleteDictType(selected.typeCode)
    ElMessage.success(t('system.dict.type.messages.deleteSuccess'))
    if (currentType.value?.typeCode === selected.typeCode) {
      currentType.value = null
      items.value = []
    }
    fetchTypes()
  } catch {
    /* 取消 */
  }
}

/** 打开字典项新增弹窗（需先选中字典类型） */
function openItemCreate() {
  if (!currentType.value) {
    ElMessage.warning(t('system.dict.item.messages.selectTypeFirst'))
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

/** 打开字典项编辑弹窗，回填数据 */
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

/** 提交字典项：根据 dialogMode 执行创建或更新，成功后刷新当前类型字典项 */
async function submitItem() {
  await itemFormRef.value?.validate()
  if (itemDialogMode.value === 'create') {
    await createDictItem(itemForm)
    ElMessage.success(t('system.dict.item.messages.createSuccess'))
  } else {
    await updateDictItem(itemForm.id!, itemForm)
    ElMessage.success(t('system.dict.item.messages.updateSuccess'))
  }
  itemDialogVisible.value = false
  if (currentType.value) selectType(currentType.value)
}

/** 删除字典项，二次确认后执行 */
async function handleDeleteItem(item: DictItemVO) {
  try {
    await ElMessageBox.confirm(t('system.dict.item.messages.confirmDelete', { name: item.itemValue }), t('common.tip'), { type: 'warning' })
    await deleteDictItem(item.id)
    ElMessage.success(t('system.dict.item.messages.deleteSuccess'))
    if (currentType.value) selectType(currentType.value)
  } catch {
    /* 取消 */
  }
}

/** 刷新当前字典类型的缓存 */
async function handleRefresh() {
  if (!currentType.value) return
  await refreshDictCache(currentType.value.typeCode)
  ElMessage.success(t('system.dict.item.messages.cacheRefreshed'))
}

onMounted(fetchTypes)
</script>

<template>
  <div class="dict-page">
    <el-card shadow="never" class="left-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('system.dict.type.title') }}</span>
          <el-button type="primary" link :icon="'Plus'" @click="openTypeCreate">{{ t('system.dict.type.add') }}</el-button>
        </div>
      </template>
      <div
        v-for="tp in types"
        :key="tp.typeCode"
        class="type-item"
        :class="{ active: currentType?.typeCode === tp.typeCode }"
        @click="selectType(tp)"
      >
        <span class="type-name">{{ tp.typeName }}</span>
        <el-tag size="small" type="info">{{ tp.typeCode }}</el-tag>
        <span class="type-actions">
          <el-button type="primary" link size="small" @click.stop="openTypeEdit(tp)">{{ t('common.edit') }}</el-button>
          <el-button type="danger" link size="small" @click.stop="handleDeleteType(tp)">{{ t('common.delete') }}</el-button>
        </span>
      </div>
      <el-empty v-if="types.length === 0" :description="t('system.dict.type.empty')" :image-size="60" />
    </el-card>

    <el-card shadow="never" class="right-card">
      <template #header>
        <div class="card-header">
          <span>{{ currentType ? currentType.typeName + ' / ' + t('system.dict.item.title') : t('system.dict.item.title') }}</span>
          <div>
            <el-button :icon="'Refresh'" :disabled="!currentType" @click="handleRefresh">{{ t('system.dict.item.refreshCache') }}</el-button>
            <el-button type="primary" :icon="'Plus'" :disabled="!currentType" @click="openItemCreate">{{ t('system.dict.item.add') }}</el-button>
          </div>
        </div>
      </template>

      <vxe-table :data="items" :loading="itemsLoading" border stripe>
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="itemCode" :title="t('system.dict.item.itemCode')" width="200" />
        <vxe-column field="itemValue" :title="t('system.dict.item.itemValue')" />
        <vxe-column field="sortOrder" :title="t('system.dict.item.sortOrder')" width="80" align="center" />
        <vxe-column field="status" :title="t('system.dict.item.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? t('system.dict.item.statusEnabled') : t('system.dict.item.statusDisabled') }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="t('system.dict.item.action')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openItemEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button type="danger" link @click="handleDeleteItem(row)">{{ t('common.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 字典类型表单 -->
    <el-dialog v-model="typeDialogVisible" :title="t('system.dict.type.dialogTitle')" width="500px" :close-on-click-modal="false" :close-on-press-escape="true">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeFormRules" label-width="100px">
        <el-form-item :label="t('system.dict.type.typeCode')" prop="typeCode">
          <el-input v-model="typeForm.typeCode" :placeholder="t('system.dict.type.typeCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.dict.type.typeName')" prop="typeName">
          <el-input v-model="typeForm.typeName" :placeholder="t('system.dict.type.typeNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.dict.type.description')">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitType">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典项表单 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogMode === 'create' ? t('system.dict.item.createTitle') : t('system.dict.item.editTitle')"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemFormRules" label-width="100px">
        <el-form-item :label="t('system.dict.item.belongType')">
          <el-input v-model="itemForm.typeCode" disabled />
        </el-form-item>
        <el-form-item :label="t('system.dict.item.itemCode')" prop="itemCode">
          <el-input v-model="itemForm.itemCode" :disabled="itemDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item :label="t('system.dict.item.itemValue')" prop="itemValue">
          <el-input v-model="itemForm.itemValue" />
        </el-form-item>
        <el-form-item :label="t('system.dict.item.sortOrder')">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('system.dict.item.status')">
          <el-radio-group v-model="itemForm.status">
            <el-radio value="ENABLED">{{ t('system.dict.item.statusEnabled') }}</el-radio>
            <el-radio value="DISABLED">{{ t('system.dict.item.statusDisabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitItem">{{ t('common.ok') }}</el-button>
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
