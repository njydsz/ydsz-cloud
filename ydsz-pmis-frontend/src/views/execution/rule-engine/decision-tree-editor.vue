<!--
  @file 决策树可视化编辑器（P1-4）
  @description 树形编辑器：条件节点（菱形/圆角矩形）与决策节点（按严重度着色）的增删改查、
               拖拽调整、表达式校验、dry-run 预览与 JSON 导出。
  @module views/execution/rule-engine/decision-tree-editor
  @author ydsz-pmis-team
  @since 1.5.0
-->
<template>
  <div class="decision-tree-editor">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">决策树编辑器 · {{ treeData.ruleName || ruleCode }}</span>
          <div class="actions">
            <el-button :icon="Refresh" @click="loadTree" :loading="loading">刷新</el-button>
            <el-button :icon="CircleCheck" @click="validateTree" type="warning" plain>校验完整性</el-button>
            <el-button :icon="VideoPlay" @click="dryRun" type="success" plain>命中预览</el-button>
            <el-button :icon="Download" @click="exportJson">导出 JSON</el-button>
            <el-button :icon="Check" @click="save" type="primary" :loading="saving">保存</el-button>
            <el-button :icon="Close" @click="goBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 元信息 -->
      <el-form :inline="true" class="meta-form">
        <el-form-item label="规则编码">
          <el-input v-model="treeData.ruleCode" :disabled="!!ruleCode" style="width: 180px" />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="treeData.ruleName" style="width: 200px" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="treeData.category" style="width: 140px" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="treeData.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="作用域">
          <el-input v-model="treeData.scope" style="width: 140px" placeholder="可选" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="treeData.enabled" />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">决策树结构（右键节点可添加 / 编辑 / 删除）</el-divider>

      <div class="tree-toolbar">
        <el-button :icon="Plus" type="primary" plain size="small" @click="addRootCondition">
          初始化根条件
        </el-button>
        <el-button :icon="Expand" size="small" @click="expandAll">全部展开</el-button>
        <el-button :icon="Fold" size="small" @click="collapseAll">全部折叠</el-button>
        <el-tooltip content="条件节点（蓝色）含 true/false 两个分支；决策节点（叶子）按严重度着色" placement="right">
          <el-icon class="help-tip"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>

      <!-- 树形展示 -->
      <div class="tree-wrap" @contextmenu.prevent>
        <el-tree
          ref="treeRef"
          :data="treeNodes"
          node-key="id"
          :props="treeProps"
          draggable
          :allow-drop="allowDrop"
          :expand-on-click-node="false"
          :default-expanded-keys="expandedKeys"
          @node-contextmenu="onContextMenu"
          @node-click="onNodeClick"
          @node-drop="onNodeDrop"
        >
          <template #default="{ data }">
            <div :class="['tree-node', `node-${data.nodeType}`]">
              <el-tag v-if="data.branchLabel" :type="data.branchLabel === '是' ? 'success' : 'danger'" size="small" effect="plain" class="branch-tag">
                {{ data.branchLabel }}
              </el-tag>
              <el-icon v-if="data.nodeType === 'condition'" class="node-icon"><Operation /></el-icon>
              <el-icon v-else class="node-icon"><DocumentChecked /></el-icon>
              <span v-if="data.nodeType === 'condition'" class="node-label" :title="data.conditionExpression">
                条件：{{ data.conditionExpression || '（未设置）' }}
              </span>
              <span v-else class="node-label">
                <el-tag :type="severityOf(data.severity).type" size="small" effect="dark">
                  {{ severityOf(data.severity).label }}
                </el-tag>
                <span class="decision-title">{{ data.title || '（未设置标题）' }}</span>
              </span>
            </div>
          </template>
        </el-tree>

        <el-empty v-if="treeNodes.length === 0" description="暂无决策树，点击「初始化根条件」开始" />
      </div>
    </el-card>

    <!-- 节点编辑对话框 -->
    <el-dialog v-model="editDialogVisible" :title="editDialogTitle" width="640px" :close-on-click-modal="false">
      <el-form v-if="editingNode" :model="editingNode" label-width="120px">
        <!-- 条件节点表单 -->
        <template v-if="editingNode.nodeType === 'condition'">
          <el-form-item label="条件表达式" required>
            <el-input
              v-model="editingNode.conditionExpression"
              type="textarea"
              :rows="2"
              placeholder="如 budgetUsedRatio > 0.9 && spi < 0.9"
            />
          </el-form-item>
          <el-form-item label="变量名">
            <el-select
              v-model="editingNode._varHint"
              filterable
              allow-create
              clearable
              placeholder="选择或输入变量名（辅助提示，不参与保存）"
              style="width: 100%"
            >
              <el-option v-for="f in availableFields" :key="f" :label="f" :value="f" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button size="small" :loading="validating" @click="validateNodeExpression(editingNode)">
              <el-icon><Check /></el-icon>校验表达式
            </el-button>
            <el-tag v-if="exprValid === true" type="success" size="small">语法合法</el-tag>
            <el-tag v-else-if="exprValid === false" type="danger" size="small">语法不合法</el-tag>
          </el-form-item>
        </template>
        <!-- 决策节点表单 -->
        <template v-else>
          <el-form-item label="严重度" required>
            <el-select v-model="editingNode.severity" style="width: 200px">
              <el-option v-for="opt in severityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标题" required>
            <el-input v-model="editingNode.title" placeholder="如 严重超支" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="editingNode.description" type="textarea" :rows="3" placeholder="如 预算使用率超过 90%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenu.visible"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <div class="ctx-item" @click="onCtxEdit">
        <el-icon><Edit /></el-icon>编辑节点
      </div>
      <div v-if="contextMenu.node?.nodeType === 'decision'" class="ctx-item" @click="onCtxConvertToCondition">
        <el-icon><Operation /></el-icon>转为条件节点
      </div>
      <div v-if="contextMenu.node?.nodeType === 'condition'" class="ctx-item" @click="onCtxConvertToDecision">
        <el-icon><DocumentChecked /></el-icon>转为决策节点
      </div>
      <div v-if="contextMenu.node?.nodeType === 'condition'" class="ctx-item" @click="onCtxSetBranch('是')">
        <el-icon><CircleCheck /></el-icon>设置「是」分支为决策
      </div>
      <div v-if="contextMenu.node?.nodeType === 'condition'" class="ctx-item" @click="onCtxSetBranch('否')">
        <el-icon><CircleClose /></el-icon>设置「否」分支为决策
      </div>
      <div class="ctx-item danger" @click="onCtxDelete">
        <el-icon><Delete /></el-icon>删除节点（含子树）
      </div>
    </div>

    <!-- 校验结果对话框 -->
    <el-dialog v-model="validateResultVisible" title="决策树校验结果" width="640px">
      <el-alert
        v-if="validateResult"
        :title="validateResult.valid ? '校验通过' : '校验未通过'"
        :type="validateResult.valid ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table v-if="validateResult?.issues.length" :data="validateResult.issues" border stripe size="small">
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="160" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="nodePath" label="节点路径" width="140" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="validateResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 命中预览对话框 -->
    <el-dialog v-model="previewVisible" title="决策树命中预览（Dry-run）" width="720px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="事实数据">
          <el-input
            v-model="previewFactsText"
            type="textarea"
            :rows="8"
            placeholder='如 {"budgetUsedRatio": 0.95, "spi": 0.85}'
            class="json-input"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="previewLoading" @click="runPreview">
            <el-icon><VideoPlay /></el-icon>执行预览
          </el-button>
        </el-form-item>
      </el-form>
      <el-divider content-position="left">预览结果</el-divider>
      <pre class="json-view">{{ formatJson(previewResult) }}</pre>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree, AllowDropFunction } from 'element-plus'
import {
  Plus, Delete, Refresh, Check, Close, VideoPlay, Download, CircleCheck, CircleClose,
  Edit, Operation, DocumentChecked, Expand, Fold, InfoFilled,
} from '@element-plus/icons-vue'
import * as ruleApi from '@/api/rule-engine'
import type {
  DecisionTreeDefinition, DecisionNode, DecisionTreeValidateResult,
  RuleResult,
} from '@/api/rule-engine'

defineOptions({ name: 'DecisionTreeEditor' })

const route = useRoute()
const router = useRouter()

const ruleCode = computed(() => route.params.ruleCode as string)
const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()

// ==================== 严重度映射 ====================

const severityMap: Record<string, { label: string; type: 'danger' | 'warning' | 'info' | 'success' | 'primary' }> = {
  RED: { label: '红色', type: 'danger' },
  YELLOW: { label: '黄色', type: 'warning' },
  INFO: { label: '通知', type: 'info' },
  NORMAL: { label: '通知', type: 'info' },
}

const severityOptions = [
  { label: '红色 RED', value: 'RED' },
  { label: '黄色 YELLOW', value: 'YELLOW' },
  { label: '通知 INFO', value: 'INFO' },
]

function severityOf(severity?: string) {
  if (!severity) return { label: '-', type: 'info' as const }
  return severityMap[severity] || { label: severity, type: 'info' as const }
}

// ==================== 可用字段 ====================

const availableFields = [
  'budgetUsedRatio', 'budgetTotal', 'budgetUsed', 'budgetRemaining',
  'spi', 'cpi', 'ev', 'pv', 'ac', 'sv', 'cv',
  'progress', 'daysRemaining', 'daysElapsed',
  'riskScore', 'utilizationRate', 'avgBillableUtilization',
  'overdueDays', 'activeProjects', 'evmRedCount',
  'confirmedRevenue', 'grossMargin', 'benchIdleCost',
  'actualCost', 'plannedCost', 'costVariance',
  'scheduleVariance', 'estimateAtCompletion',
]

// ==================== UI 树节点数据结构 ====================

/** UI 层树节点（将后端二叉树 trueBranch/falseBranch 展平为 children 数组，便于 el-tree 渲染） */
interface TreeNode {
  id: string
  /** condition=条件节点（内部），decision=决策节点（叶子） */
  nodeType: 'condition' | 'decision'
  /** 分支标签：'是' | '否'（条件节点的两个子节点标识，根节点无） */
  branchLabel?: string
  /** 条件表达式（条件节点） */
  conditionExpression?: string
  /** 严重度（决策节点） */
  severity?: string
  /** 标题（决策节点） */
  title?: string
  /** 描述（决策节点） */
  description?: string
  /** 子节点：条件节点有 2 个 [trueBranch, falseBranch]，决策节点无 */
  children?: TreeNode[]
  /** 表达式校验辅助字段（不参与保存） */
  _varHint?: string
}

const treeProps = { children: 'children', label: 'label' }

const treeNodes = ref<TreeNode[]>([])
const expandedKeys = ref<string[]>([])

function uuid() { return Math.random().toString(36).slice(2, 10) }

/** 决策树元信息 */
const treeData = reactive<DecisionTreeDefinition>({
  ruleCode: '',
  ruleName: '',
  category: '通用',
  description: '',
  enabled: true,
  priority: 50,
  scope: '',
  version: 0,
})

// ==================== 后端 ↔ UI 转换 ====================

/** 后端 DecisionNode → UI TreeNode（递归） */
function fromBackend(node: DecisionNode | undefined, branchLabel?: string): TreeNode | undefined {
  if (!node) return undefined
  if (node.leaf) {
    return {
      id: uuid(),
      nodeType: 'decision',
      branchLabel,
      severity: node.severity,
      title: node.title,
      description: node.description,
    }
  }
  const uiNode: TreeNode = {
    id: uuid(),
    nodeType: 'condition',
    branchLabel,
    conditionExpression: node.conditionExpression,
    children: [],
  }
  const trueChild = fromBackend(node.trueBranch, '是')
  const falseChild = fromBackend(node.falseBranch, '否')
  if (trueChild) uiNode.children!.push(trueChild)
  if (falseChild) uiNode.children!.push(falseChild)
  return uiNode
}

/** UI TreeNode → 后端 DecisionNode（递归） */
function toBackend(node: TreeNode): DecisionNode {
  if (node.nodeType === 'decision') {
    return {
      leaf: true,
      severity: node.severity,
      title: node.title,
      description: node.description,
    }
  }
  const children = node.children || []
  return {
    leaf: false,
    conditionExpression: node.conditionExpression,
    trueBranch: children.find((c) => c.branchLabel === '是') ? toBackend(children.find((c) => c.branchLabel === '是')!) : undefined,
    falseBranch: children.find((c) => c.branchLabel === '否') ? toBackend(children.find((c) => c.branchLabel === '否')!) : undefined,
  }
}

// ==================== 树操作 ====================

/** 创建默认条件节点（带两个空决策子节点） */
function createConditionNode(branchLabel?: string): TreeNode {
  return {
    id: uuid(),
    nodeType: 'condition',
    branchLabel,
    conditionExpression: '',
    children: [
      { id: uuid(), nodeType: 'decision', branchLabel: '是', severity: 'INFO', title: '', description: '' },
      { id: uuid(), nodeType: 'decision', branchLabel: '否', severity: 'INFO', title: '', description: '' },
    ],
  }
}

/** 创建默认决策节点 */
function createDecisionNode(branchLabel?: string): TreeNode {
  return {
    id: uuid(),
    nodeType: 'decision',
    branchLabel,
    severity: 'INFO',
    title: '',
    description: '',
  }
}

/** 初始化根条件 */
function addRootCondition() {
  if (treeNodes.value.length > 0) {
    ElMessage.warning('已存在根节点，请先删除')
    return
  }
  const root = createConditionNode()
  treeNodes.value = [root]
  expandedKeys.value = [root.id]
}

/** 递归查找节点及其父节点 */
function findNodeAndParent(
  nodes: TreeNode[],
  id: string,
  parent: TreeNode | null = null,
): { node: TreeNode; parent: TreeNode | null; list: TreeNode[] } | null {
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].id === id) {
      return { node: nodes[i], parent, list: nodes }
    }
    if (nodes[i].children) {
      const found = findNodeAndParent(nodes[i].children!, id, nodes[i])
      if (found) return found
    }
  }
  return null
}

/** 递归收集所有节点 id（用于展开） */
function collectIds(nodes: TreeNode[]): string[] {
  const ids: string[] = []
  for (const n of nodes) {
    ids.push(n.id)
    if (n.children) ids.push(...collectIds(n.children))
  }
  return ids
}

function expandAll() {
  expandedKeys.value = collectIds(treeNodes.value)
  // el-tree 默认展开通过 default-expanded-keys 控制，需重新设置数据触发
  forceTreeUpdate()
}

function collapseAll() {
  expandedKeys.value = []
  forceTreeUpdate()
}

/** 强制 el-tree 重新渲染（折叠/展开需要） */
function forceTreeUpdate() {
  const snapshot = treeNodes.value.slice()
  treeNodes.value = []
  setTimeout(() => { treeNodes.value = snapshot }, 0)
}

// ==================== 节点编辑 ====================

const editDialogVisible = ref(false)
const editingNode = ref<(TreeNode & { _varHint?: string }) | null>(null)
const editingNodeRef = ref<TreeNode | null>(null)
const exprValid = ref<null | boolean>(null)
const editDialogTitle = computed(() => editingNode.value?.nodeType === 'condition' ? '编辑条件节点' : '编辑决策节点')

function onNodeClick(data: TreeNode) {
  editingNodeRef.value = data
}

function openEdit(node: TreeNode) {
  editingNode.value = { ...node, _varHint: '' }
  editingNodeRef.value = node
  exprValid.value = null
  editDialogVisible.value = true
}

function confirmEdit() {
  if (!editingNode.value || !editingNodeRef.value) return
  if (editingNode.value.nodeType === 'condition') {
    if (!editingNode.value.conditionExpression?.trim()) {
      ElMessage.warning('请输入条件表达式')
      return
    }
    editingNodeRef.value.conditionExpression = editingNode.value.conditionExpression
  } else {
    if (!editingNode.value.severity) {
      ElMessage.warning('请选择严重度')
      return
    }
    if (!editingNode.value.title?.trim()) {
      ElMessage.warning('请输入标题')
      return
    }
    editingNodeRef.value.severity = editingNode.value.severity
    editingNodeRef.value.title = editingNode.value.title
    editingNodeRef.value.description = editingNode.value.description
  }
  editDialogVisible.value = false
  ElMessage.success('节点已更新')
}

async function validateNodeExpression(node: TreeNode & { _varHint?: string }) {
  if (!node.conditionExpression) {
    ElMessage.warning('请先输入条件表达式')
    return
  }
  validating.value = true
  try {
    const { data } = await ruleApi.validateExpression(node.conditionExpression)
    exprValid.value = data
    ElMessage[data ? 'success' : 'error'](data ? '表达式语法合法' : '表达式语法不合法')
  } finally {
    validating.value = false
  }
}

// ==================== 右键菜单 ====================

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  node: null as TreeNode | null,
})

function onContextMenu(evt: Event, _data: unknown, node: { data: Record<string, unknown> }) {
  contextMenu.visible = true
  const mouseEvt = evt as MouseEvent
  contextMenu.x = mouseEvt.clientX
  contextMenu.y = mouseEvt.clientY
  contextMenu.node = node.data as unknown as TreeNode
  // 点击任意位置关闭
  setTimeout(() => {
    document.addEventListener('click', closeContextMenu, { once: true })
  }, 0)
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.node = null
}

function onCtxEdit() {
  if (contextMenu.node) openEdit(contextMenu.node)
  closeContextMenu()
}

/** 决策节点 → 条件节点（保留原决策作为「是」分支，新建「否」分支） */
function onCtxConvertToCondition() {
  const target = contextMenu.node
  if (!target || target.nodeType !== 'decision') return
  const found = findNodeAndParent(treeNodes.value, target.id)
  if (!found) return
  const newCondition = createConditionNode(target.branchLabel)
  // 原「决策」作为「是」分支
  newCondition.children![0] = { ...target, branchLabel: '是', id: uuid() }
  // 「否」分支为空决策
  newCondition.children![1] = createDecisionNode('否')
  // 替换
  const idx = found.list.findIndex((n) => n.id === target.id)
  found.list[idx] = newCondition
  expandedKeys.value = [...expandedKeys.value, newCondition.id]
  forceTreeUpdate()
  closeContextMenu()
  ElMessage.success('已转为条件节点')
}

/** 条件节点 → 决策节点（子树丢失，需确认） */
async function onCtxConvertToDecision() {
  const target = contextMenu.node
  if (!target || target.nodeType !== 'condition') return
  try {
    await ElMessageBox.confirm('转为决策节点将丢失当前子树，是否继续？', '提示', { type: 'warning' })
  } catch {
    closeContextMenu()
    return
  }
  const found = findNodeAndParent(treeNodes.value, target.id)
  if (!found) return
  const newDecision = createDecisionNode(target.branchLabel)
  const idx = found.list.findIndex((n) => n.id === target.id)
  found.list[idx] = newDecision
  forceTreeUpdate()
  closeContextMenu()
  ElMessage.success('已转为决策节点')
}

/** 为条件节点的指定分支（是/否）设置决策节点（若已有则编辑，若无则创建） */
function onCtxSetBranch(branch: '是' | '否') {
  const target = contextMenu.node
  if (!target || target.nodeType !== 'condition') return
  if (!target.children) target.children = []
  let branchNode = target.children.find((c) => c.branchLabel === branch)
  if (!branchNode) {
    branchNode = createDecisionNode(branch)
    target.children.push(branchNode)
  }
  openEdit(branchNode)
  closeContextMenu()
}

function onCtxDelete() {
  const target = contextMenu.node
  if (!target) return
  ElMessageBox.confirm('确认删除该节点及其子树？', '删除确认', { type: 'warning' })
    .then(() => {
      const found = findNodeAndParent(treeNodes.value, target.id)
      if (!found) return
      const idx = found.list.findIndex((n) => n.id === target.id)
      found.list.splice(idx, 1)
      forceTreeUpdate()
      closeContextMenu()
      ElMessage.success('节点已删除')
    })
    .catch(() => closeContextMenu())
}

// ==================== 拖拽 ====================

/**
 * 拖拽放置校验：仅允许拖入条件节点的子级（inner），且条件节点最多 2 个子节点。
 * 决策节点不允许放置子节点。
 */
const allowDrop: AllowDropFunction = (draggingNode, dropNode, type) => {
  if (type !== 'inner') return false
  const dropData = dropNode.data as TreeNode
  const dragData = draggingNode.data as TreeNode
  // 只能放入条件节点
  if (dropData.nodeType !== 'condition') return false
  // 条件节点最多 2 个子节点
  const childCount = dropData.children?.length || 0
  // 拖拽节点如果本就是该条件节点的子节点，不占新增名额
  const isOwnChild = dropData.children?.some((c) => c.id === dragData.id)
  if (isOwnChild) return true
  return childCount < 2
}

/** 拖拽结束后重新标记 branchLabel（保证第 1 个=是，第 2 个=否） */
function onNodeDrop() {
  function fixBranchLabels(nodes: TreeNode[]) {
    for (const n of nodes) {
      if (n.nodeType === 'condition' && n.children) {
        // 按 branchLabel 排序：是在前，否在后；无标签的补齐
        n.children.sort((a, b) => {
          const av = a.branchLabel === '是' ? 0 : 1
          const bv = b.branchLabel === '是' ? 0 : 1
          return av - bv
        })
        n.children.forEach((c, i) => {
          c.branchLabel = i === 0 ? '是' : '否'
        })
        fixBranchLabels(n.children)
      }
    }
  }
  fixBranchLabels(treeNodes.value)
}

// ==================== 校验 / 预览 / 导出 ====================

const validateResultVisible = ref(false)
const validateResult = ref<DecisionTreeValidateResult | null>(null)

async function validateTree() {
  if (!treeNodes.value[0]) {
    ElMessage.warning('请先初始化根条件')
    return
  }
  const payload = buildPayload()
  try {
    const { data } = await ruleApi.validateDecisionTree(payload)
    validateResult.value = data
    validateResultVisible.value = true
    if (data.valid) ElMessage.success('决策树校验通过')
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '校验失败')
  }
}

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewFactsText = ref('{\n  "budgetUsedRatio": 0.95,\n  "spi": 0.85\n}')
const previewResult = ref<RuleResult[] | null>(null)

function dryRun() {
  previewResult.value = null
  previewVisible.value = true
}

async function runPreview() {
  let facts: Record<string, unknown>
  try {
    facts = JSON.parse(previewFactsText.value)
  } catch {
    ElMessage.error('事实数据 JSON 格式不正确')
    return
  }
  previewLoading.value = true
  try {
    const { data } = await ruleApi.dryRun(ruleCode.value, facts)
    previewResult.value = data
    ElMessage.success('预览完成')
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

function exportJson() {
  const payload = buildPayload()
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `decision-tree-${treeData.ruleCode || 'untitled'}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('JSON 已导出')
}

// ==================== 数据加载 / 保存 ====================

function buildPayload(): DecisionTreeDefinition {
  return {
    ruleCode: treeData.ruleCode,
    ruleName: treeData.ruleName,
    category: treeData.category,
    description: treeData.description,
    enabled: treeData.enabled,
    priority: treeData.priority,
    scope: treeData.scope,
    version: treeData.version,
    root: treeNodes.value[0] ? toBackend(treeNodes.value[0]) : undefined,
  }
}

async function loadTree() {
  if (!ruleCode.value) return
  loading.value = true
  try {
    const res = await ruleApi.getDecisionTree(ruleCode.value)
    if (res.code === 0 && res.data) {
      const def = res.data
      treeData.ruleCode = def.ruleCode || ruleCode.value
      treeData.ruleName = def.ruleName || ''
      treeData.category = def.category || '通用'
      treeData.description = def.description || ''
      treeData.enabled = def.enabled ?? true
      treeData.priority = def.priority ?? 50
      treeData.scope = def.scope || ''
      treeData.version = def.version ?? 0
      if (def.root) {
        const uiRoot = fromBackend(def.root)
        if (uiRoot) {
          treeNodes.value = [uiRoot]
          expandedKeys.value = [uiRoot.id]
        }
      }
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载决策树失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!treeData.ruleCode) {
    ElMessage.warning('请输入规则编码')
    return
  }
  if (!treeData.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!treeNodes.value[0]) {
    ElMessage.warning('请先初始化根条件')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const res = await ruleApi.saveDecisionTree(payload)
    if (res.code === 0) {
      ElMessage.success('保存成功')
      treeData.version = res.data?.version ?? treeData.version
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/rule-engine')
}

function formatJson(obj: unknown): string {
  if (!obj) return '（空）'
  return JSON.stringify(obj, null, 2)
}

onMounted(() => {
  if (ruleCode.value) loadTree()
})
</script>

<style scoped lang="scss">
.decision-tree-editor { padding: 16px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-weight: 600; font-size: 16px; }
  .actions { display: flex; gap: 8px; }
}
.meta-form { margin-bottom: 8px; }
.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  .help-tip { color: #909399; cursor: help; font-size: 16px; }
}
.tree-wrap {
  min-height: 360px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  position: relative;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  .branch-tag { margin-right: 2px; }
  .node-icon { font-size: 14px; }
  .node-label { font-size: 13px; }
  .decision-title { margin-left: 4px; }

  &.node-condition {
    .node-icon { color: #409eff; }
    .node-label {
      font-family: 'JetBrains Mono', 'Courier New', monospace;
      color: #303133;
    }
    border-left: 3px solid #409eff;
    background: #f0f7ff;
  }
  &.node-decision {
    .node-icon { color: #909399; }
    border-left: 3px solid #c0c4cc;
    background: #f9fafb;
  }
}
.context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  min-width: 180px;
  .ctx-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    cursor: pointer;
    font-size: 13px;
    color: #303133;
    &:hover { background: #f5f7fa; }
    &.danger { color: #f56c6c; }
  }
}
.json-view {
  background: #1e293b;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}
.json-input {
  :deep(textarea) {
    font-family: 'Courier New', Consolas, monospace;
    font-size: 13px;
  }
}
.mb-3 { margin-bottom: 12px; }
</style>
