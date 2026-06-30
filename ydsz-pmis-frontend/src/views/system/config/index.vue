<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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
const presetGroups = [
  { value: 'system', label: '系统' },
  { value: 'rate', label: '费率' },
  { value: 'workflow', label: '工作流' },
  { value: 'alert', label: '预警阈值' },
  { value: 'business', label: '业务' },
  { value: 'integration', label: '集成' },
]

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
  configGroup: [{ required: true, message: '分组必填', trigger: 'blur' }],
  configKey: [
    { required: true, message: '键必填', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9._-]+$/, message: '仅支持字母数字 . _ -', trigger: 'blur' },
  ],
  valueType: [{ required: true, message: '类型必填', trigger: 'change' }],
}

const valueTypeOptions = [
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
]

const statusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
]

const publicOptions = [
  { label: '公开（前端可见）', value: 1 },
  { label: '私有', value: 0 },
]

// 当前选中 group (用于批量操作)
const selectedGroup = ref<string>('')

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

function openEdit(row: ConfigVO) {
  dialogMode.value = 'edit'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  try {
    if (dialogMode.value === 'create') {
      await createConfig(form)
      ElMessage.success('创建成功')
    } else {
      await updateConfig(form)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleDelete(row: ConfigVO) {
  try {
    await ElMessageBox.confirm(
      `确认删除配置「${row.configGroup}.${row.configKey}」?`,
      '提示',
      { type: 'warning' }
    )
    await deleteConfig(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    /* 取消 */
  }
}

async function handleDeleteByGroup(group: string) {
  if (!group) return
  try {
    await ElMessageBox.confirm(
      `确认删除分组「${group}」下的全部配置?此操作不可恢复`,
      '警告',
      { type: 'error' }
    )
    const { data } = await deleteByGroup(group)
    ElMessage.success(`已删除 ${data} 条配置`)
    fetchList()
  } catch {
    /* 取消 */
  }
}

async function handleToggleGroupStatus(group: string, currentStatus: string) {
  if (!group) return
  const next = currentStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    const { data } = await updateStatusByGroup(group, next)
    ElMessage.success(`已${next === 'ENABLED' ? '启用' : '停用'} ${data} 条配置`)
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleRefresh() {
  await refreshConfigCache()
  ElMessage.success('缓存已刷新')
}

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

// 解析后的值类型中文
function valueTypeLabel(type: string): string {
  return valueTypeOptions.find((o) => o.value === type)?.label || type
}

onMounted(fetchList)
</script>

<template>
  <div class="config-page">
    <el-card shadow="never">
      <el-form inline :model="query" class="search-form">
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="键/值/描述"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="分组">
          <el-select
            v-model="query.configGroup"
            clearable
            filterable
            allow-create
            placeholder="全部"
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
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="query.isPublic" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="p in publicOptions"
              :key="String(p.value)"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="query.page = 1; fetchList()">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="['sys:config:create']" type="primary" :icon="'Plus'" @click="openCreate">
          新增配置
        </el-button>
        <el-button :icon="'Refresh'" @click="handleRefresh">刷新缓存</el-button>
        <el-button
          v-permission="['sys:config:update']"
          v-if="selectedGroup"
          :icon="(list.find((c) => c.configGroup === selectedGroup)?.status || 'ENABLED') === 'ENABLED' ? 'VideoPause' : 'VideoPlay'"
          @click="handleToggleGroupStatus(selectedGroup, (list.find((c) => c.configGroup === selectedGroup)?.status || 'ENABLED'))"
        >
          启停当前分组
        </el-button>
        <el-button
          v-permission="['sys:config:delete']"
          v-if="selectedGroup"
          type="danger"
          :icon="'Delete'"
          @click="handleDeleteByGroup(selectedGroup)"
        >
          清空当前分组
        </el-button>
        <span v-if="selectedGroup" class="group-tag">
          当前分组:
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
        <vxe-column field="configGroup" title="分组" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.configGroup }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="configKey" title="配置键" width="220" />
        <vxe-column field="configValue" title="配置值" min-width="200">
          <template #default="{ row }">
            <span class="value-text" :title="row.configValue">{{ displayValue(row) }}</span>
          </template>
        </vxe-column>
        <vxe-column field="valueType" title="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.valueType === 'JSON' ? 'warning' : row.valueType === 'NUMBER' ? 'success' : row.valueType === 'BOOLEAN' ? 'danger' : 'info')">
              {{ valueTypeLabel(row.valueType) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="isPublic" title="可见" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isPublic === 1 ? 'success' : 'info'">
              {{ row.isPublic === 1 ? '公开' : '私有' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="description" title="描述" min-width="180" show-overflow />
        <vxe-column field="status" title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['sys:config:update']" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['sys:config:delete']" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
        class="pagination"
      />
    </el-card>

    <!-- 创建/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增配置' : '编辑配置'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="配置分组" prop="configGroup">
          <el-input
            v-model="form.configGroup"
            placeholder="例如: system / rate / workflow"
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
        <el-form-item label="配置键" prop="configKey">
          <el-input
            v-model="form.configKey"
            placeholder="例如: alert.cpi.yellow"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="值类型" prop="valueType">
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
        <el-form-item label="配置值">
          <el-input
            v-if="form.valueType === 'BOOLEAN'"
            v-model="form.configValue"
            placeholder="true / false"
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
            placeholder="字符串值"
          />
        </el-form-item>
        <el-form-item label="默认值">
          <el-input v-model="form.defaultValue" placeholder="回退时的默认值(可选)" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="form.isPublic">
            <el-radio :value="0">私有</el-radio>
            <el-radio :value="1">公开（前端可见）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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
