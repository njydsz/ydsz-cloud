<!--
  @file 商机跟进管理
  @description 商机跟进记录管理页面，支持记录跟进动态、查看跟进历史时间线；
               跟进方式包括: 电话/邮件/拜访/线上会议/其他。
  @module views/project/opportunity-follow
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useI18n } from 'vue-i18n'
import PageLayout from '@/components/common/PageLayout.vue'
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()
const userStore = useUserStore()

/** 列表加载状态 */
const loading = ref(false)
/** 跟进记录列表数据 */
const list = ref<any[]>([])
/** 跟进记录总数 */
const total = ref(0)
/** 分页查询参数 */
const query = reactive({
  page: 1,
  size: 10,
  opportunityId: undefined as number | undefined,
  followMethod: '',
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  opportunityId: undefined as number | undefined,
  followMethod: '',
  content: '',
  nextFollowDate: '',
})

const followMethodMap: Record<string, { label: string; type: string }> = {
  PHONE: { label: '电话', type: 'primary' },
  EMAIL: { label: '邮件', type: 'success' },
  VISIT: { label: '拜访', type: 'warning' },
  ONLINE: { label: '线上会议', type: 'info' },
  OTHER: { label: '其他', type: 'info' },
}

async function loadData() {
  loading.value = true
  try {
    // TODO: 替换为实际 API 调用
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  ElMessage.success('跟进记录已保存')
  dialogVisible.value = false
  loadData()
}

function handlePageChange(page: number) {
  query.page = page
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <PageLayout>
    <template #header>
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ t('route.projectOpportunityFollow', '商机跟进') }}</h2>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          {{ t('common.followUp') }}
        </el-button>
      </div>
    </template>

    <div class="mb-4 flex gap-3">
      <el-input v-model="query.opportunityId" placeholder="商机ID" clearable style="width: 160px" />
      <el-select v-model="query.followMethod" placeholder="跟进方式" clearable style="width: 140px">
        <el-option v-for="(v, k) in followMethodMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="opportunityName" label="商机名称" min-width="180" />
      <el-table-column prop="followMethod" label="跟进方式" width="120">
        <template #default="{ row }">
          <el-tag :type="followMethodMap[row.followMethod]?.type || 'info'">
            {{ followMethodMap[row.followMethod]?.label || row.followMethod }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="跟进内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="followerName" label="跟进人" width="120" />
      <el-table-column prop="nextFollowDate" label="下次跟进" width="120" />
      <el-table-column prop="createdAt" label="记录时间" width="170" />
    </el-table>

    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="记录跟进" width="520px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="商机ID" prop="opportunityId" :rules="{ required: true, message: '请输入商机ID' }">
          <el-input-number v-model="form.opportunityId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="跟进方式" prop="followMethod" :rules="{ required: true, message: '请选择跟进方式' }">
          <el-select v-model="form.followMethod" placeholder="请选择">
            <el-option v-for="(v, k) in followMethodMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容" prop="content" :rules="{ required: true, message: '请输入跟进内容' }">
          <el-input v-model="form.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="form.nextFollowDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </PageLayout>
</template>
