<script setup lang="ts">
/**
 * @file 统一审批中心（薄容器组件）
 * @module views/workflow/approval-center
 * @description
 *   重构后的审批中心仅负责 Tab 切换、子组件加载与实时角标轮询。
 *   各 Tab 的列表/搜索/分页/操作逻辑已拆分至 tabs/ 子组件：
 *     - TodoTab      我的待办（含筛选/置顶/列设置/操作弹窗）
 *     - DoneTab      我的已办
 *     - InitiatedTab 我发起的
 *     - CCTab        抄送我的
 *   审批操作策略见 composables/useApprovalActions.ts。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pageTodoTasks, ccUnreadCount } from '@/api/workflow'
import TodoTab from './tabs/TodoTab.vue'
import DoneTab from './tabs/DoneTab.vue'
import InitiatedTab from './tabs/InitiatedTab.vue'
import CCTab from './tabs/CCTab.vue'

const { t } = useI18n()

const activeTab = ref<'todo' | 'done' | 'mine' | 'cc'>('todo')

// ===========================================
// 实时角标：60 秒轮询
// ===========================================
const todoTotal = ref(0)
const ccUnread = ref(0)

const tabBadge = computed(() => ({
  todo: todoTotal.value > 0 ? todoTotal.value : undefined,
  cc: ccUnread.value > 0 ? ccUnread.value : undefined,
}))

/** 轻量请求：仅刷新待办总数 */
async function refreshTodoBadge() {
  try {
    const res = await pageTodoTasks({ pageNum: 1, pageSize: 1 })
    if (res.data?.code === 0) {
      todoTotal.value = res.data.data?.total || 0
    }
  } catch {
    // 静默失败
  }
}

/** 轻量请求：仅刷新抄送未读数 */
async function refreshCcUnread() {
  try {
    const res = await ccUnreadCount()
    if (res.data?.code === 0) {
      ccUnread.value = res.data.data || 0
    }
  } catch {
    // 静默失败
  }
}

/** 子组件数据变更后立即刷新角标 */
function refreshBadges() {
  refreshTodoBadge()
  refreshCcUnread()
}

let pollingTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  refreshBadges()
  pollingTimer = setInterval(refreshBadges, 60000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

onMounted(startPolling)
onUnmounted(stopPolling)
</script>

<template>
  <div class="approval-center">
    <div class="page-header">
      <h2>
        {{ t('workflow.approval.title') }}
        <el-badge
          v-if="tabBadge.todo"
          :value="tabBadge.todo"
          :max="999"
          class="header-badge"
        />
      </h2>
      <p class="page-header__sub">{{ t('workflow.approval.subtitle') }}</p>
    </div>

    <el-tabs v-model="activeTab" class="approval-tabs">
      <!-- 我的待办 -->
      <el-tab-pane name="todo" lazy>
        <template #label>
          <span class="tab-label">
            <el-icon><Bell /></el-icon>
            {{ t('workflow.approval.tabs.todo') }}
            <el-badge
              v-if="tabBadge.todo"
              :value="tabBadge.todo"
              :max="99"
              class="tab-badge"
            />
          </span>
        </template>
        <TodoTab @refresh-badge="refreshBadges" />
      </el-tab-pane>

      <!-- 我的已办 -->
      <el-tab-pane name="done" lazy>
        <template #label>
          <span class="tab-label">
            <el-icon><Select /></el-icon>
            {{ t('workflow.approval.tabs.done') }}
          </span>
        </template>
        <DoneTab />
      </el-tab-pane>

      <!-- 我发起的 -->
      <el-tab-pane name="mine" lazy>
        <template #label>
          <span class="tab-label">
            <el-icon><Promotion /></el-icon>
            {{ t('workflow.approval.tabs.mine') }}
          </span>
        </template>
        <InitiatedTab />
      </el-tab-pane>

      <!-- 抄送我的 -->
      <el-tab-pane name="cc" lazy>
        <template #label>
          <span class="tab-label">
            <el-icon><Share /></el-icon>
            {{ t('workflow.approval.tabs.cc') }}
            <el-badge
              v-if="tabBadge.cc"
              :value="tabBadge.cc"
              :max="99"
              class="tab-badge"
              type="danger"
            />
          </span>
        </template>
        <CCTab @refresh-badge="refreshBadges" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.approval-center {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 20px;
    color: #1e293b;
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }
  &__sub {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}
.header-badge :deep(.el-badge__content) {
  font-size: 12px;
}
.approval-tabs {
  background: #fff;
  border-radius: 6px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
.tab-badge {
  margin-left: 4px;
}
</style>
