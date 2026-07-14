<!--
  @fileoverview 文件目录树组件
  @description 支持懒加载子目录、节点点击选中、右键菜单（新建/重命名/删除/移动）。
  - Props: data（顶层节点列表）、lazy（是否懒加载）、load（懒加载回调）
  - Emits: node-click、node-contextmenu
  @module components/common/FileTree
-->
<script setup lang="ts">
import { ref } from 'vue'
import type { FileNodeVO } from '@/api/nextwiki/types'

/** 树节点数据结构（扩展 children） */
interface TreeNode extends FileNodeVO {
  children?: TreeNode[]
  isLeaf?: boolean
}

const props = withDefaults(defineProps<{
  /** 顶层节点列表 */
  data: TreeNode[]
  /** 是否懒加载子节点 */
  lazy?: boolean
  /** 懒加载回调 */
  load?: (node: TreeNode) => Promise<TreeNode[]>
  /** 当前选中节点 ID */
  currentId?: string
}>(), {
  lazy: false,
})

const emit = defineEmits<{
  (e: 'node-click', node: TreeNode): void
  (e: 'node-contextmenu', node: TreeNode, event: MouseEvent): void
}>()

const treeRef = ref()
/** 右键菜单位置 */
const contextMenuVisible = ref(false)
const contextMenuStyle = ref({ left: '0px', top: '0px' })

/** 树节点 props 配置 */
const treeProps = {
  label: 'name',
  children: 'children',
  isLeaf: (data: TreeNode) => data.isLeaf === true,
}

/** 懒加载处理 */
async function handleLoad(node: { data?: TreeNode }, resolve: (data: TreeNode[]) => void) {
  if (props.load && node.data) {
    const children = await props.load(node.data)
    resolve(children)
  } else {
    resolve([])
  }
}

/** 节点点击 */
function handleNodeClick(node: TreeNode) {
  emit('node-click', node)
}

/** 节点右键 */
function handleNodeContextmenu(event: MouseEvent, node: TreeNode) {
  event.preventDefault()
  contextMenuStyle.value = {
    left: `${event.clientX}px`,
    top: `${event.clientY}px`,
  }
  contextMenuVisible.value = true
  emit('node-contextmenu', node, event)
}

/** 关闭右键菜单 */
function closeContextMenu() {
  contextMenuVisible.value = false
}
</script>

<template>
  <div class="file-tree" @click="closeContextMenu">
    <el-tree
      ref="treeRef"
      :data="data"
      :props="treeProps"
      :lazy="lazy"
      :load="handleLoad"
      node-key="id"
      :highlight-current="true"
      :default-expanded-keys="currentId ? [currentId] : []"
      :current-node-key="currentId"
      @node-click="handleNodeClick"
      @node-contextmenu="handleNodeContextmenu"
    >
      <template #default="{ data: node }">
        <span class="file-tree__node">
          <el-icon class="file-tree__icon"><component :is="node.nodeType === 'folder' ? 'Folder' : 'Document'" /></el-icon>
          <span class="file-tree__label">{{ node.name }}</span>
          <el-icon v-if="node.starred" class="file-tree__star"><Star /></el-icon>
        </span>
      </template>
    </el-tree>

    <!-- 右键菜单 -->
    <teleport to="body">
      <div
        v-if="contextMenuVisible"
        class="file-tree__context-menu"
        :style="contextMenuStyle"
        @click.stop
      >
        <div class="context-menu__item" @click="closeContextMenu">{{ $t('nextwiki.files.createFolder') }}</div>
        <div class="context-menu__item" @click="closeContextMenu">{{ $t('nextwiki.files.rename') }}</div>
        <div class="context-menu__item" @click="closeContextMenu">{{ $t('nextwiki.files.move') }}</div>
        <div class="context-menu__item context-menu__item--danger" @click="closeContextMenu">{{ $t('nextwiki.files.delete') }}</div>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.file-tree {
  height: 100%;
  overflow: auto;
}
.file-tree__node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}
.file-tree__icon {
  font-size: 14px;
  color: #E6A23C;
}
.file-tree__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-tree__star {
  color: #F7BA2A;
  font-size: 12px;
}
.file-tree__context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 120px;
}
.context-menu__item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
}
.context-menu__item:hover {
  background: #f5f7fa;
}
.context-menu__item--danger {
  color: #F56C6C;
}
</style>
