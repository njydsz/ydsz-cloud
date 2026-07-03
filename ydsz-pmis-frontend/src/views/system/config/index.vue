<!--
  @file 系统参数配置
  @description 系统参数配置页面：提供参数分页查询（按分组/状态/可见性/关键字筛选）、新增/编辑/删除，以及按分组批量启停、清空分组、刷新缓存等运维操作。对应路由 /system/config。
  @module views/system/config
-->
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageConfigs,
  createConfig,
  updateConfig,
  deleteConfig,
  deleteByGroup,
  updateStatusByGroup,
  refreshConfigCache,
} from '@/api/system/config'
import type { ConfigVO, ConfigFormDTO } from '@/api/system/config/types'

const { t } = useI18n()

const loading = ref(false)
const list = ref<ConfigVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  configGroup: '',
  status: '',
  isPublic: undefined as number | undefined,
})

// 预置分组
const presetGroups = computed(() => [
  { value: 'system', label: t('system.config.presetGroups.system') },
  { value: 'rate', label: t('system.config.presetGroups.rate') },
  { value: 'workflow', label: t('system.config.presetGroups.workflow') },
  { value: 'alert', label: t('system.config.presetGroups.alert') },
  { value: 'business', label: t('system.config.presetGroups.business') },
  { value: 'integration', label: t('system.config.presetGroups.integration') },
])

// 表单
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref()
const form = reactive<ConfigFormDTO>({
  id: undefined,
  configGroup: '',
  configKey: '',
  configValue: '',
  defaultValue: '',
  valueType: 'STRING',
  description: '',
  isPublic: 0,
  sortOrder: 0,
  status: 'ENABLED',
})

const formRules = {
  configGroup: [{ required: true, message: t('system.config.rules.configGroupRequired'), trigger: 'blur' }],
  configKey: [
    { required: true, message: t('system.config.rules.configKeyRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9._-]+$/, message: t('system.config.rules.configKeyPattern'), trigger: 'blur' },
  ],
  valueType: [{ required: true, message: t('system.config.rules.valueTypeRequired'), trigger: 'change' }],
}

const valueTypeOptions = computed(() => [
  { label: t('system.config.valueType.STRING'), value: 'STRING' },
  { label: t('system.config.valueType.NUMBER'), value: 'NUMBER' },
  { label: t('system.config.valueType.BOOLEAN'), value: 'BOOLEAN' },
  { label: t('system.config.valueType.JSON'), value: 'JSON' },
])

const statusOptions = computed(() => [
  { label: t('system.config.form.statusEnabled'), value: 'ENABLED' },
  { label: t('system.config.form.statusDisabled'), value: 'DISABLED' },
])

const publicOptions = computed(() => [
  { label: t('system.config.visibility.public'), value: 1 },
  { label: t('system.config.visibility.private'), value: 0 },
])

// 当前选中 group (用于批量操作)
const selectedGroup = ref<string>('')

/** 拉取系统参数分页列表，并默认选中首个分组用于批量操作 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await pageConfigs(query)
    list.value = data.list
    total.value = data.total
    if (data.list.length > 0 && !selectedGroup.value) {
      selectedGroup.value = data.list[0].configGroup
    }
  } finally {
    loading.value = false
  }
}

/** 重置查询条件并刷新列表 */
function reset() {
  Object.assign(query, {
    page: 1,
    size: 10,
    keyword: '',
    configGroup: '',
    status: '',
    isPublic: undefined,
  })
  fetchList()
}

/** 打开新增配置弹窗，默认分组取当前选中分组 */
function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    configGroup: selectedGroup.value || 'system',
    configKey: '',
    configValue: '',
    defaultValue: '',
    valueType: 'STRING',
    description: '',
    isPublic: 0,
    sortOrder: 0,
    status: 'ENABLED',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗，回填行数据到表单
 * @param row 待编辑的配置行数据
 */
function openEdit(row: ConfigVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

/** 提交表单：根据 dialogMode 执行创建或更新，成功后刷新列表 */
async function submitForm() {
  await formRef.value?.validate()
  try {
    if (dialogMode.value === 'create') {
      await createConfig(form)
      ElMessage.success(t('system.config.messages.createSuccess'))
    } else {
      await updateConfig(form)
      ElMessage.success(t('system.config.messages.updateSuccess'))
    }
    dialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('system.config.messages.operationFailed'))
  }
}

/**
 * 删除单条配置，二次确认后执行
 * @param row 待删除的配置行数据
 */
async function handleDelete(row: ConfigVO) {
  try {
    await ElMessageBox.confirm(
      t('system.config.messages.confirmDelete', { group: row.configGroup, key: row.configKey }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteConfig(row.id)
    ElMessage.success(t('system.config.messages.deleteSuccess'))
    fetchList()
  } catch {
    /* 取消 */
  }
}

/**
 * 按分组批量删除配置，二次确认后执行（不可恢复）
 * @param group 配置分组名
 */
async function handleDeleteByGroup(group: string) {
  if (!group) return
  try {
    await ElMessageBox.confirm(
      t('system.config.messages.confirmDeleteByGroup', { group }),
      t('common.tip'),
      { type: 'error' }
    )
    const { data } = await deleteByGroup(group)
    ElMessage.success(t('system.config.messages.deletedByGroup', { count: data }))
    fetchList()
  } catch {
    /* 取消 */
  }
}

/**
 * 切换指定分组下全部配置的启停状态
 * @param group 配置分组名
 * @param currentStatus 当前状态（ENABLED / DISABLED），将切换为相反状态
 */
async function handleToggleGroupStatus(group: string, currentStatus: string) {
  if (!group) return
  const next = currentStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    const { data } = await updateStatusByGroup(group, next)
    ElMessage.success(
      next === 'ENABLED'
        ? t('system.config.messages.toggledGroupEnabled', { count: data })
        : t('system.config.messages.toggledGroupDisabled', { count: data })
    )
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('system.config.messages.operationFailed'))
  }
}

/** 刷新配置缓存 */
async function handleRefresh() {
  await refreshConfigCache()
  ElMessage.success(t('system.config.messages.cacheRefreshed'))
}

/**
 * 解析配置值用于表格展示（布尔/JSON 截断处理）
 * @param row 配置行数据
 * @returns 解析后的展示字符串
 */
// 解析后的显示值
function displayValue(row: ConfigVO): string {
  if (row.configValue === null || row.configValue === undefined) return ''
  if (row.valueType === 'BOOLEAN') {
    return row.configValue === 'true' ? 'true' : 'false'
  }
  if (row.valueType === 'JSON') {
    const s = row.configValue
    return s.length > 80 ? s.slice(0, 80) + '…' : s
  }
  return row.configValue
}

/**
 * 将值类型枚举转换为中文标签
 * @param type 值类型枚举（STRING/NUMBER/BOOLEAN/JSON）
 * @returns 中文标签
 */
// 解析后的值类型中文
function valueTypeLabel(type: string): string {
  return valueTypeOptions.value.find((o) => o.value === type)?.label || type
}

onMounted(fetchList)
</script>

<template>
  <div class="config-page">
    <el-card shadow="never">
      <el-form inline :model="query" class="search-form">
        <el-form-item :label="t('system.config.search.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="t('system.config.search.keywordPlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="t('system.config.search.group')">
          <el-select
            v-model="query.configGroup"
            clearable
            filterable
            allow-create
            :placeholder="t('common.all')"
            style="width: 160px"
          >
            <el-option
              v-for="g in presetGroups"
              :key="g.value"
              :label="g.label"
              :value="g.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.config.search.status')">
          <el-select v-model="query.status" clearable :placeholder="t('common.all')" style="width: 120px">
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.config.search.visibility')">
          <el-select v-model="query.isPublic" clearable :placeholder="t('common.all')" style="width: 130px">
            <el-option
              v-for="p in publicOptions"
              :key="String(p.value)"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="query.page = 1; fetchList()">{{ t('common.search') }}</el-button>
          <el-button @click="reset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['sys:config:create']" type="primary" :icon="'Plus'" @click="openCreate">
          {{ t('system.config.buttons.create') }}
        </el-button>
        <el-button :icon="'Refresh'" @click="handleRefresh">{{ t('system.config.buttons.refreshCache') }}</el-button>
        <el-button
          v-if="selectedGroup"
          v-permission="['sys:config:update']"
          :icon="(list.find((c) => c.configGroup === selectedGroup)?.status || 'ENABLED') === 'ENABLED' ? 'VideoPause' : 'VideoPlay'"
          @click="handleToggleGroupStatus(selectedGroup, (list.find((c) => c.configGroup === selectedGroup)?.status || 'ENABLED'))"
        >
          {{ t('system.config.buttons.toggleGroup') }}
        </el-button>
        <el-button
          v-if="selectedGroup"
          v-permission="['sys:config:delete']"
          type="danger"
          :icon="'Delete'"
          @click="handleDeleteByGroup(selectedGroup)"
        >
          {{ t('system.config.buttons.clearGroup') }}
        </el-button>
        <span v-if="selectedGroup" class="group-tag">
          {{ t('system.config.groupTag') }}
          <el-tag size="small" type="info">{{ selectedGroup }}</el-tag>
        </span>
      </div>

      <vxe-table
        :data="list"
        :loading="loading"
        border
        stripe
        highlight-current-row
        @row-click="(row: ConfigVO) => (selectedGroup = row.configGroup)"
      >
        <vxe-column type="seq" title="#" width="50" />
        <vxe-column field="configGroup" :title="t('system.config.columns.configGroup')" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.configGroup }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="configKey" :title="t('system.config.columns.configKey')" width="220" />
        <vxe-column field="configValue" :title="t('system.config.columns.configValue')" min-width="200">
          <template #default="{ row }">
            <span class="value-text" :title="row.configValue">{{ displayValue(row) }}</span>
          </template>
        </vxe-column>
        <vxe-column field="valueType" :title="t('system.config.columns.valueType')" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.valueType === 'JSON' ? 'warning' : row.valueType === 'NUMBER' ? 'success' : row.valueType === 'BOOLEAN' ? 'danger' : 'info')">
              {{ valueTypeLabel(row.valueType) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="isPublic" :title="t('system.config.columns.isPublic')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isPublic === 1 ? 'success' : 'info'">
              {{ row.isPublic === 1 ? t('system.config.visibility.public') : t('system.config.visibility.private') }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="description" :title="t('system.config.columns.description')" min-width="180" show-overflow />
        <vxe-column field="status" :title="t('system.config.columns.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? t('system.config.form.statusEnabled') : t('system.config.form.statusDisabled') }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="t('system.config.columns.action')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['sys:config:update']" type="primary" link @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-permission="['sys:config:delete']" type="danger" link @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </el-card>

    <!-- 创建/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('system.config.dialog.createTitle') : t('system.config.dialog.editTitle')"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="t('system.config.form.configGroup')" prop="configGroup">
          <el-input
            v-model="form.configGroup"
            :placeholder="t('system.config.form.configGroupPlaceholder')"
            :disabled="dialogMode === 'edit'"
          >
            <template #append>
              <el-select
                v-model="form.configGroup"
                :disabled="dialogMode === 'edit'"
                style="width: 120px"
              >
                <el-option
                  v-for="g in presetGroups"
                  :key="g.value"
                  :label="g.label"
                  :value="g.value"
                />
              </el-select>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('system.config.form.configKey')" prop="configKey">
          <el-input
            v-model="form.configKey"
            :placeholder="t('system.config.form.configKeyPlaceholder')"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item :label="t('system.config.form.valueType')" prop="valueType">
          <el-radio-group v-model="form.valueType">
            <el-radio
              v-for="o in valueTypeOptions"
              :key="o.value"
              :value="o.value"
              :disabled="dialogMode === 'edit'"
            >
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('system.config.form.configValue')">
          <el-input
            v-if="form.valueType === 'BOOLEAN'"
            v-model="form.configValue"
            :placeholder="t('system.config.form.booleanPlaceholder')"
          />
          <el-input-number
            v-else-if="form.valueType === 'NUMBER'"
            :model-value="Number(form.configValue || 0)"
            :controls="true"
            style="width: 100%"
            @update:model-value="(v) => (form.configValue = String(v))"
          />
          <el-input
            v-else-if="form.valueType === 'JSON'"
            v-model="form.configValue"
            type="textarea"
            :rows="4"
            placeholder='{"key": "value"}'
          />
          <el-input
            v-else
            v-model="form.configValue"
            :placeholder="t('system.config.form.stringPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('system.config.form.defaultValue')">
          <el-input v-model="form.defaultValue" :placeholder="t('system.config.form.defaultValuePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.config.form.visibility')">
          <el-radio-group v-model="form.isPublic">
            <el-radio :value="0">{{ t('system.config.visibility.private') }}</el-radio>
            <el-radio :value="1">{{ t('system.config.visibility.public') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('system.config.form.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('system.config.form.status')">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">{{ t('system.config.form.statusEnabled') }}</el-radio>
            <el-radio value="DISABLED">{{ t('system.config.form.statusDisabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('system.config.form.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitForm">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.config-page {
  .search-form { margin-bottom: $spacing-md; }
  .toolbar {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-md;
    flex-wrap: wrap;
  }
  .group-tag {
    margin-left: auto;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
  .pagination {
    margin-top: $spacing-md;
    justify-content: flex-end;
  }
  .value-text {
    font-family: 'SFMono-Regular', Consolas, monospace;
    color: var(--el-color-primary);
  }
}
</style>
