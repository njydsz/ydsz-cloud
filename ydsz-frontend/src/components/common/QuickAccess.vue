<!--
  @fileoverview 快速访问组件 (P2-14 收藏/快速访问)
  @description 收藏与最近访问的快速入口面板，双 Tab 切换：
  - 收藏 Tab: 展示当前用户收藏的页面/项目/合同
  - 最近访问 Tab: 展示最近 10 条访问记录
  - 点击跳转并自动调用 recordAccess 记录访问
  - API 调用失败时降级为空列表，不阻断渲染
  @module components/common/QuickAccess
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * 快速访问面板
 *
 * 从 /favorites 与 /recent-access 拉取数据, 点击项目时调用 recordAccess 记录访问.
 * 接口异常时静默降级为空列表.
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Star, Clock } from '@element-plus/icons-vue'
import {
  getFavorites,
  getRecentAccess,
  recordAccess,
} from '@/api/favorite'
import type { FavoriteVO, RecentAccessVO } from '@/api/favorite'

const router = useRouter()
const favorites = ref<FavoriteVO[]>([])
const recentAccess = ref<RecentAccessVO[]>([])
const activeTab = ref('favorites')

const fetchData = async () => {
  try {
    const favResp = await getFavorites()
    favorites.value = favResp.data ?? []
  } catch {
    favorites.value = []
  }
  try {
    const recentResp = await getRecentAccess()
    recentAccess.value = recentResp.data ?? []
  } catch {
    recentAccess.value = []
  }
}

const navigate = (path: string, title: string) => {
  recordAccess(path, title)
  router.push(path)
}

onMounted(fetchData)
</script>

<template>
  <el-tabs v-model="activeTab" type="border-card">
    <el-tab-pane label="收藏" name="favorites">
      <template #label>
        <el-icon><Star /></el-icon> 收藏
      </template>
      <div v-if="favorites.length === 0" class="empty-text">暂无收藏</div>
      <div
        v-for="fav in favorites"
        :key="fav.id"
        class="access-item"
        @click="navigate(fav.targetPath || '/', fav.targetName)"
      >
        <el-icon class="access-icon"><Star /></el-icon>
        <span class="access-name">{{ fav.targetName }}</span>
      </div>
    </el-tab-pane>
    <el-tab-pane label="最近访问" name="recent">
      <template #label>
        <el-icon><Clock /></el-icon> 最近访问
      </template>
      <div v-if="recentAccess.length === 0" class="empty-text">暂无访问记录</div>
      <div
        v-for="item in recentAccess.slice(0, 10)"
        :key="item.id"
        class="access-item"
        @click="navigate(item.path, item.title)"
      >
        <el-icon class="access-icon"><Clock /></el-icon>
        <span class="access-name">{{ item.title }}</span>
        <span class="access-time">{{ item.accessedAt }}</span>
      </div>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts">
export default { name: 'QuickAccess' }
</script>

<style lang="scss" scoped>
.access-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;
}
.access-item:hover {
  background: var(--el-fill-color-light);
}
.access-icon {
  color: var(--el-color-primary);
}
.access-name {
  flex: 1;
  font-size: 14px;
}
.access-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.empty-text {
  text-align: center;
  padding: 20px;
  color: var(--el-text-color-placeholder);
}
</style>
