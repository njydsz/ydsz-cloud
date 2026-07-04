<!--
  @file 规则目录树侧边栏
  @description 左侧展示基于 category_path 的多级分类树，支持点击节点过滤规则列表，
               树节点展示规则数与 Owner 数量徽标。
  @module components/common/RuleCategoryTreeSidebar
-->
<script setup lang="ts">
/**
 * 规则目录树侧边栏（P1-9）
 *
 * 左侧面板：多级分类树 + Owner 标签。选中节点后通过 `select` 事件抛出 path。
 * 数据来源：ruleApi.getCategoryTree。
 */
import { ref, onMounted } from 'vue'
import { Folder, FolderOpened, User } from '@element-plus/icons-vue'
import * as ruleApi from '@/api/execution/rule-engine'
import type { CategoryNode } from '@/api/execution/rule-engine'

/** 选中的分类路径（空字符串 = 全部） */
const selectedPath = defineModel<string>('selectedPath', { default: '' })

/** emit：节点点击（用于上层联动规则列表） */
const emit = defineEmits<{
  (e: 'select', path: string): void
}>()

/** 目录树根节点 */
const treeData = ref<CategoryNode[]>([])
/** 加载状态 */
const loading = ref(false)

/** 拉取目录树 */
async function loadTree() {
  loading.value = true
  try {
    const { data } = await ruleApi.getCategoryTree()
    // 移除虚拟根，仅展示一级 children
    treeData.value = data?.children || []
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

/** 节点点击：选中并 emit */
function handleNodeClick(node: CategoryNode) {
  const path = node.path === '/' ? '' : node.path
  selectedPath.value = path
  emit('select', path)
}

defineExpose({ reload: loadTree })

onMounted(() => {
  loadTree()
})
</script>

<template>
  <div class="category-tree-sidebar" v-loading="loading">
    <div class="tree-header">
      <el-icon><Folder /></el-icon>
      <span>规则目录</span>
    </div>
    <el-input
      v-model="selectedPath"
      placeholder="当前分类路径"
      size="small"
      readonly
      clearable
      @clear="emit('select', '')"
    />
    <el-tree
      :data="treeData"
      :props="{ label: 'name', children: 'children' }"
      node-key="path"
      :highlight-current="true"
      :expand-on-click-node="false"
      :default-expand-all="false"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <span class="tree-node-content">
          <el-icon class="tree-node-icon">
            <component :is="data.children && data.children.length > 0 ? FolderOpened : Folder" />
          </el-icon>
          <span class="tree-node-name">{{ node.label }}</span>
          <el-tag v-if="data.ruleCount > 0" type="info" size="small" effect="plain" round>
            {{ data.ruleCount }}
          </el-tag>
          <el-tooltip
            v-if="data.owners && data.owners.length > 0"
            :content="`责任人: ${data.owners.join(', ')}`"
            placement="top"
          >
            <span class="tree-node-owner">
              <el-icon><User /></el-icon>
              <span>{{ data.owners.length }}</span>
            </span>
          </el-tooltip>
        </span>
      </template>
    </el-tree>
    <el-empty v-if="!loading && treeData.length === 0" description="暂无规则目录" :image-size="60" />
  </div>
</template>

<style scoped lang="scss">
.category-tree-sidebar {
  padding: 12px;
  background: #fafbfc;
  border-right: 1px solid #ebeef5;
  min-width: 240px;
  height: 100%;
  overflow-y: auto;

  .tree-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }

  :deep(.el-tree) {
    background: transparent;
    margin-top: 8px;
  }

  :deep(.el-tree-node__content) {
    height: 32px;
  }

  .tree-node-content {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding-right: 8px;
  }

  .tree-node-icon {
    color: #909399;
    font-size: 14px;
  }

  .tree-node-name {
    flex: 1;
    font-size: 13px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tree-node-owner {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    color: #909399;
    font-size: 11px;

    .el-icon {
      font-size: 12px;
    }
  }
}
</style>
