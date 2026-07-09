<!--
  @fileoverview DAG 工作流编排引擎管理页面

  - 业务模块归属: AI Agent DAG 工作流编排
  - 关键能力: DAG 定义列表 + 执行监控 + 实例详情 + 节点状态可视化
  - 关联的后端接口: @/api/agent/dag

  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createDefinition,
  executeDag,
  listNodeInstances,
  pageDefinitions,
  pageInstances,
} from '@/api/agent/dag'
import type {
  DagDefinitionDO,
  DagExecutionResult,
  DagInstanceDO,
  DagNodeInstanceDO,
  DagNodeStatus,
} from '@/api/agent/dag/types'
import { PC } from '@/constants/permissionCodes'
import type { PageResult } from '@/utils/request'

const { t } = useI18n()

// ============= 定义列表 =============
const loading = ref(false)
const list = ref<DagDefinitionDO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

async function loadList() {
  loading.value = true
  try {
    const { data } = await pageDefinitions(pageNo.value, pageSize.value)
    const result = data as PageResult<DagDefinitionDO> | undefined
    list.value = result?.list ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) { pageNo.value = p; loadList() }
function onSizeChange(s: number) { pageSize.value = s; pageNo.value = 1; loadList() }

// ============= 创建 DAG =============
const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  dagJson: '',
})
const creating = ref(false)

// ============= DAG 可视化设计器 =============
/** 设计器模式：visual / json */
const designerMode = ref<'visual' | 'json'>('visual')
/** DAG 节点定义（可视化设计器用） */
interface DagNodeDef {
  id: string
  name: string
  agentType: string
  x: number
  y: number
  dependencies: string[]
  inputs: Record<string, unknown>
}
/** 设计器节点列表 */
const designerNodes = ref<DagNodeDef[]>([])
/** 选中的节点 ID */
const selectedNodeId = ref<string | null>(null)
/** 连线模式：点击源节点后进入连线模式，再点击目标节点完成连线 */
const linkingFrom = ref<string | null>(null)
/** 拖拽中的节点 ID */
const draggingNodeId = ref<string | null>(null)
/** 拖拽偏移量 */
const dragOffset = reactive({ x: 0, y: 0 })
/** 画布 SVG ref */
const canvasRef = ref<SVGSVGElement | null>(null)

/** 生成唯一节点 ID */
function genNodeId(): string {
  return `node-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
}

/** 添加新节点 */
function addNode() {
  const node: DagNodeDef = {
    id: genNodeId(),
    name: `node${designerNodes.value.length + 1}`,
    agentType: 'RISK_WARNING',
    x: 80 + (designerNodes.value.length % 4) * 200,
    y: 60 + Math.floor(designerNodes.value.length / 4) * 120,
    dependencies: [],
    inputs: {},
  }
  designerNodes.value.push(node)
  selectedNodeId.value = node.id
  syncDesignerToJson()
}

/** 删除节点 */
function removeNode(id: string) {
  designerNodes.value = designerNodes.value.filter(n => n.id !== id && n.dependencies.indexOf(id) === -1)
  designerNodes.value.forEach(n => {
    n.dependencies = n.dependencies.filter(d => d !== id)
  })
  if (selectedNodeId.value === id) selectedNodeId.value = null
  syncDesignerToJson()
}

/** 选中节点 */
function selectNode(id: string) {
  // 如果在连线模式，完成连线
  if (linkingFrom.value && linkingFrom.value !== id) {
    const target = designerNodes.value.find(n => n.id === id)
    if (target && !target.dependencies.includes(linkingFrom.value)) {
      target.dependencies.push(linkingFrom.value)
      syncDesignerToJson()
    }
    linkingFrom.value = null
    return
  }
  selectedNodeId.value = id
}

/** 开始连线模式 */
function startLink(id: string) {
  linkingFrom.value = id
}

/** 取消连线模式 */
function cancelLink() {
  linkingFrom.value = null
}

/** 删除连线 */
function removeDependency(nodeId: string, depId: string) {
  const node = designerNodes.value.find(n => n.id === nodeId)
  if (node) {
    node.dependencies = node.dependencies.filter(d => d !== depId)
    syncDesignerToJson()
  }
}

/** SVG 画布鼠标移动：拖拽节点 */
function onCanvasMouseMove(e: MouseEvent) {
  if (!draggingNodeId.value || !canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left - dragOffset.x
  const y = e.clientY - rect.top - dragOffset.y
  const node = designerNodes.value.find(n => n.id === draggingNodeId.value)
  if (node) {
    node.x = Math.max(10, Math.min(x, rect.width - 120))
    node.y = Math.max(10, Math.min(y, rect.height - 60))
  }
}

/** SVG 画布鼠标松开：结束拖拽 */
function onCanvasMouseUp() {
  draggingNodeId.value = null
}

/** 节点鼠标按下：开始拖拽 */
function onNodeMouseDown(e: MouseEvent, node: DagNodeDef) {
  if (linkingFrom.value) return // 连线模式不拖拽
  draggingNodeId.value = node.id
  const rect = canvasRef.value?.getBoundingClientRect()
  if (rect) {
    dragOffset.x = e.clientX - rect.left - node.x
    dragOffset.y = e.clientY - rect.top - node.y
  }
  e.preventDefault()
}

/** 计算连线路径（贝塞尔曲线） */
function edgePath(from: DagNodeDef, to: DagNodeDef): string {
  const x1 = from.x + 120
  const y1 = from.y + 25
  const x2 = to.x
  const y2 = to.y + 25
  const cx = (x1 + x2) / 2
  return `M ${x1},${y1} C ${cx},${y1} ${cx},${y2} ${x2},${y2}`
}

/** 将可视化设计器同步到 JSON */
function syncDesignerToJson() {
  const dag = {
    name: createForm.name,
    description: createForm.description,
    nodes: designerNodes.value.map(n => ({
      name: n.name,
      agentType: n.agentType,
      dependencies: n.dependencies.map(depId => {
        const dep = designerNodes.value.find(d => d.id === depId)
        return dep ? dep.name : depId
      }),
      inputs: n.inputs,
    })),
    failureStrategy: 'ABORT',
    defaultTimeoutMs: 30000,
    maxRetries: 3,
  }
  createForm.dagJson = JSON.stringify(dag, null, 2)
}

/** 从 JSON 同步到可视化设计器 */
function syncJsonToDesigner() {
  try {
    const dag = JSON.parse(createForm.dagJson)
    if (!dag.nodes || !Array.isArray(dag.nodes)) return
    // 先创建所有节点（无依赖），再填充依赖
    const nameToId = new Map<string, string>()
    designerNodes.value = dag.nodes.map((n: any, idx: number) => {
      const id = genNodeId()
      nameToId.set(n.name, id)
      return {
        id,
        name: n.name || `node${idx + 1}`,
        agentType: n.agentType || 'RISK_WARNING',
        x: 80 + (idx % 4) * 200,
        y: 60 + Math.floor(idx / 4) * 120,
        dependencies: [],
        inputs: n.inputs || {},
      }
    })
    // 填充依赖
    dag.nodes.forEach((n: any) => {
      const node = designerNodes.value.find(d => d.name === n.name)
      if (node && n.dependencies) {
        node.dependencies = (n.dependencies as string[])
          .map(depName => nameToId.get(depName))
          .filter((id): id is string => !!id)
      }
    })
  } catch {
    // JSON 无效时不做同步
  }
}

/** 监听 JSON 变化（仅在 json 模式编辑时同步到设计器） */
watch(() => createForm.dagJson, () => {
  if (designerMode.value === 'json') {
    // 延迟同步，避免频繁解析
  }
})

/** 切换设计器模式时同步 */
function switchDesignerMode(mode: 'visual' | 'json') {
  if (mode === 'json' && designerMode.value === 'visual') {
    syncDesignerToJson()
  } else if (mode === 'visual' && designerMode.value === 'json') {
    syncJsonToDesigner()
  }
  designerMode.value = mode
}

/** 当前选中的节点对象 */
const selectedNode = computed(() => {
  return designerNodes.value.find(n => n.id === selectedNodeId.value) || null
})

/** Agent 类型选项 */
const AGENT_TYPES = ['RISK_WARNING', 'RESOURCE_RECOMMEND', 'PROFIT_FORECAST', 'WIN_RATE_PREDICT', 'TIMESHEET_ANOMALY', 'APPROVER_RECOMMEND', 'COMMENT_DRAFT', 'FLOW_GENERATOR']

async function handleCreate() {
  if (!createForm.name) {
    ElMessage.warning(t('agent.dag.messages.nameRequired'))
    return
  }
  // 从可视化模式创建时，先同步到 JSON
  if (designerMode.value === 'visual') {
    syncDesignerToJson()
  }
  let dagDef
  try {
    dagDef = JSON.parse(createForm.dagJson)
  } catch {
    ElMessage.error(t('agent.dag.messages.invalidJson'))
    return
  }
  dagDef.name = createForm.name
  dagDef.description = createForm.description
  creating.value = true
  try {
    await createDefinition(dagDef)
    ElMessage.success(t('agent.dag.messages.createSuccess'))
    createDialogVisible.value = false
    createForm.name = ''
    createForm.description = ''
    createForm.dagJson = ''
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.createFailed'))
  } finally {
    creating.value = false
  }
}

function openCreateDialog() {
  designerNodes.value = []
  selectedNodeId.value = null
  linkingFrom.value = null
  designerMode.value = 'visual'
  createForm.name = ''
  createForm.description = ''
  createForm.dagJson = ''
  // 添加一个示例节点
  addNode()
  createDialogVisible.value = true
}

// ============= 执行 DAG =============
const executing = ref(false)
const executionResult = ref<DagExecutionResult | null>(null)
const executeDialogVisible = ref(false)
const executeInputs = ref('')

async function handleExecute(row: DagDefinitionDO) {
  executing.value = true
  executionResult.value = null
  executeDialogVisible.value = true
  try {
    let inputs: Record<string, unknown> | undefined
    if (executeInputs.value) {
      try {
        inputs = JSON.parse(executeInputs.value)
      } catch {
        ElMessage.warning(t('agent.dag.messages.invalidInputs'))
        executing.value = false
        return
      }
    }
    const { data } = await executeDag(row.id!, inputs)
    executionResult.value = data as DagExecutionResult
    ElMessage.success(t('agent.dag.messages.executeSuccess'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.executeFailed'))
  } finally {
    executing.value = false
  }
}

// ============= 实例历史 =============
const instanceDialogVisible = ref(false)
const instanceList = ref<DagInstanceDO[]>([])
const instanceTotal = ref(0)
const instancePageNo = ref(1)
const currentDefinitionId = ref('')
const instanceLoading = ref(false)

async function openInstanceHistory(row: DagDefinitionDO) {
  currentDefinitionId.value = row.id!
  instanceDialogVisible.value = true
  instancePageNo.value = 1
  await loadInstances()
}

async function loadInstances() {
  instanceLoading.value = true
  try {
    const { data } = await pageInstances(currentDefinitionId.value, instancePageNo.value, 20)
    const result = data as PageResult<DagInstanceDO> | undefined
    instanceList.value = result?.list ?? []
    instanceTotal.value = result?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    instanceLoading.value = false
  }
}

// ============= 节点详情 =============
const nodeDialogVisible = ref(false)
const nodeList = ref<DagNodeInstanceDO[]>([])
const nodeLoading = ref(false)

async function openNodeDetail(row: DagInstanceDO) {
  nodeDialogVisible.value = true
  nodeLoading.value = true
  try {
    const { data } = await listNodeInstances(row.id)
    nodeList.value = (data as DagNodeInstanceDO[]) || []
  } catch (e: any) {
    ElMessage.error(e?.message || t('agent.dag.messages.loadFailed'))
  } finally {
    nodeLoading.value = false
  }
}

// ============= 工具方法 =============
function statusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'danger'
    case 'PARTIAL': return 'warning'
    case 'PENDING': return 'info'
    case 'SKIPPED': return 'info'
    default: return 'info'
  }
}

function nodeStatusColor(status: DagNodeStatus): string {
  switch (status) {
    case 'SUCCESS': return '#67C23A'
    case 'RUNNING': return '#E6A23C'
    case 'FAILED': return '#F56C6C'
    case 'SKIPPED': return '#909399'
    default: return '#C0C4CC'
  }
}

const SAMPLE_DAG_TEMPLATE = computed(() => t('agent.dag.create.template'))

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="dag-page">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button v-permission="[PC.AGENT_DAG_CREATE]" type="primary" :icon="'Plus'" @click="openCreateDialog">
            {{ t('agent.dag.buttons.create') }}
          </el-button>
          <el-button :icon="'Refresh'" @click="loadList">{{ t('agent.dag.buttons.refresh') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- DAG 定义列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <vxe-table :data="list" :loading="loading" stripe height="auto">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="name" :title="t('agent.dag.columns.name')" min-width="180" show-overflow />
        <vxe-column field="description" :title="t('agent.dag.columns.description')" min-width="200" show-overflow />
        <vxe-column field="failureStrategy" :title="t('agent.dag.columns.strategy')" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.failureStrategy || 'ABORT' }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="defaultTimeoutMs" :title="t('agent.dag.columns.timeout')" width="100">
          <template #default="{ row }">{{ row.defaultTimeoutMs || 30000 }}ms</template>
        </vxe-column>
        <vxe-column field="maxRetries" :title="t('agent.dag.columns.retries')" width="80" />
        <vxe-column field="createdAt" :title="t('agent.dag.columns.createdAt')" width="170" />
        <vxe-column :title="t('agent.dag.columns.action')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="[PC.AGENT_DAG_RUN]" link type="primary" size="small"
              :icon="'VideoPlay'" @click="handleExecute(row)">
              {{ t('agent.dag.buttons.execute') }}
            </el-button>
            <el-button link type="info" size="small" :icon="'Files'" @click="openInstanceHistory(row)">
              {{ t('agent.dag.buttons.history') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 创建 DAG 对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('agent.dag.create.title')" width="900px" top="5vh">
      <el-form label-width="100px">
        <el-form-item :label="t('agent.dag.create.name')">
          <el-input v-model="createForm.name" :placeholder="t('agent.dag.create.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.dag.create.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('agent.dag.create.json')">
          <el-radio-group v-model="designerMode" @change="switchDesignerMode(designerMode)" style="margin-bottom: 8px">
            <el-radio-button value="visual">{{ t('agent.dag.designer.visual') }}</el-radio-button>
            <el-radio-button value="json">{{ t('agent.dag.designer.json') }}</el-radio-button>
          </el-radio-group>
          <!-- 可视化设计器 -->
          <div v-if="designerMode === 'visual'" class="dag-designer">
            <div class="designer-toolbar">
              <el-button type="primary" size="small" :icon="'Plus'" @click="addNode">{{ t('agent.dag.designer.addNode') }}</el-button>
              <el-button v-if="linkingFrom" type="warning" size="small" @click="cancelLink">{{ t('agent.dag.designer.cancelLink') }}</el-button>
              <span v-if="linkingFrom" class="link-hint">{{ t('agent.dag.designer.linkHint') }}</span>
            </div>
            <div class="designer-body">
              <!-- SVG 画布 -->
              <svg
                ref="canvasRef"
                class="dag-canvas"
                @mousemove="onCanvasMouseMove"
                @mouseup="onCanvasMouseUp"
                @mouseleave="onCanvasMouseUp"
                @click.self="selectedNodeId = null; linkingFrom = null"
              >
                <!-- 连线 -->
                <g v-for="node in designerNodes" :key="`edges-${node.id}`">
                  <template v-for="depId in node.dependencies" :key="`${depId}-${node.id}`">
                    <path
                      v-if="designerNodes.find(d => d.id === depId)"
                      :d="edgePath(designerNodes.find(d => d.id === depId)!, node)"
                      class="dag-edge"
                      @click="removeDependency(node.id, depId)"
                    />
                  </template>
                </g>
                <!-- 节点 -->
                <g
                  v-for="node in designerNodes"
                  :key="node.id"
                  :transform="`translate(${node.x}, ${node.y})`"
                  :class="['dag-node-group', { selected: selectedNodeId === node.id, linking: linkingFrom === node.id }]"
                  @mousedown="onNodeMouseDown($event, node)"
                  @click.stop="selectNode(node.id)"
                >
                  <rect width="120" height="50" rx="6" class="dag-node-rect" />
                  <text x="60" y="20" text-anchor="middle" class="dag-node-name">{{ node.name }}</text>
                  <text x="60" y="38" text-anchor="middle" class="dag-node-type">{{ node.agentType }}</text>
                  <!-- 连线按钮 -->
                  <circle cx="120" cy="25" r="5" class="dag-link-dot" @click.stop="startLink(node.id)" />
                  <!-- 删除按钮 -->
                  <text x="110" y="12" class="dag-delete-btn" @click.stop="removeNode(node.id)">✕</text>
                </g>
              </svg>
              <!-- 属性面板 -->
              <div class="property-panel">
                <div v-if="selectedNode" class="property-form">
                  <h4>{{ t('agent.dag.designer.nodeProps') }}</h4>
                  <el-form size="small" label-width="80px">
                    <el-form-item label="Name">
                      <el-input v-model="selectedNode.name" @input="syncDesignerToJson" />
                    </el-form-item>
                    <el-form-item label="AgentType">
                      <el-select v-model="selectedNode.agentType" @change="syncDesignerToJson" style="width: 100%">
                        <el-option v-for="at in AGENT_TYPES" :key="at" :value="at" :label="at" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="Deps">
                      <div v-if="selectedNode.dependencies.length === 0" class="empty-deps">{{ t('agent.dag.nodeDetail.noDeps') }}</div>
                      <el-tag
                        v-for="depId in selectedNode.dependencies"
                        :key="depId"
                        closable
                        size="small"
                        style="margin: 2px"
                        @close="removeDependency(selectedNode.id, depId)"
                      >
                        {{ designerNodes.find(d => d.id === depId)?.name || depId }}
                      </el-tag>
                    </el-form-item>
                  </el-form>
                </div>
                <div v-else class="property-empty">
                  <el-empty :description="t('agent.dag.designer.selectNode')" :image-size="60" />
                </div>
              </div>
            </div>
          </div>
          <!-- JSON 编辑器 -->
          <el-input v-else v-model="createForm.dagJson" type="textarea" :rows="15"
            :placeholder="SAMPLE_DAG_TEMPLATE" style="font-family: monospace; font-size: 12px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果对话框 -->
    <el-dialog v-model="executeDialogVisible" :title="t('agent.dag.execute.title')" width="900px" top="5vh">
      <el-form v-if="!executionResult" label-width="80px">
        <el-form-item :label="t('agent.dag.execute.inputs')">
          <el-input v-model="executeInputs" type="textarea" :rows="5"
            placeholder='{"key":"value"}' style="font-family: monospace" />
        </el-form-item>
      </el-form>

      <div v-if="executionResult" class="exec-result">
        <!-- KPI -->
        <el-row :gutter="12" class="kpi-row">
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.status') }}</div>
              <el-tag :type="statusTagType(executionResult.status)" effect="dark" size="large">
                {{ executionResult.status }}
              </el-tag>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.totalCost') }}</div>
              <div class="kpi-value">{{ executionResult.totalCostMs }}ms</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card success">
              <div class="kpi-title">{{ t('agent.dag.execute.success') }}</div>
              <div class="kpi-value">{{ executionResult.successCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card danger">
              <div class="kpi-title">{{ t('agent.dag.execute.failed') }}</div>
              <div class="kpi-value">{{ executionResult.failedCount }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-title">{{ t('agent.dag.execute.skipped') }}</div>
              <div class="kpi-value">{{ executionResult.skippedCount }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 节点状态 -->
        <el-card shadow="never" :header="t('agent.dag.execute.nodeStatus')" style="margin-top: 12px">
          <div class="node-status-grid">
            <div v-for="(status, name) in executionResult.nodeStatuses" :key="name" class="node-status-item">
              <span class="node-dot" :style="{ background: nodeStatusColor(status) }" />
              <span class="node-name">{{ name }}</span>
              <el-tag :type="statusTagType(status)" size="small">{{ status }}</el-tag>
              <span v-if="executionResult.nodeRetryCounts[name] > 0" class="retry-badge">
                ↻ {{ executionResult.nodeRetryCounts[name] }}
              </span>
            </div>
          </div>
        </el-card>

        <!-- Trace 日志 -->
        <el-card shadow="never" :header="t('agent.dag.execute.trace')" style="margin-top: 12px">
          <el-timeline>
            <el-timeline-item
              v-for="(trace, idx) in executionResult.traces"
              :key="idx"
              :type="trace.event === 'FAILED' ? 'danger' : trace.event === 'SUCCESS' ? 'success' : 'primary'"
              :timestamp="trace.timestamp || ''"
              size="large"
            >
              <div class="trace-item">
                <el-tag size="small" :type="trace.event === 'FAILED' ? 'danger' : 'info'">{{ trace.event }}</el-tag>
                <span v-if="trace.nodeName" class="trace-node">{{ trace.nodeName }}</span>
                <span class="trace-msg">{{ trace.message }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 错误信息 -->
        <el-card v-if="Object.keys(executionResult.nodeErrors).length > 0" shadow="never"
          :header="t('agent.dag.execute.errors')" style="margin-top: 12px">
          <el-alert v-for="(err, node) in executionResult.nodeErrors" :key="node"
            :title="`${node}: ${err}`" type="error" :closable="false" style="margin-bottom: 8px" />
        </el-card>
      </div>

      <template #footer>
        <el-button @click="executeDialogVisible = false">{{ t('common.close') }}</el-button>
        <el-button v-if="!executionResult" type="primary" :loading="executing"
          @click="handleExecute({ id: currentDefinitionId } as DagDefinitionDO)">
          {{ t('agent.dag.buttons.execute') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 执行历史对话框 -->
    <el-dialog v-model="instanceDialogVisible" :title="t('agent.dag.history.title')" width="900px">
      <vxe-table :data="instanceList" :loading="instanceLoading" stripe max-height="400">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="id" :title="t('agent.dag.history.instanceId')" width="200" show-overflow />
        <vxe-column field="status" :title="t('agent.dag.history.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="totalCostMs" :title="t('agent.dag.history.cost')" width="100">
          <template #default="{ row }">{{ row.totalCostMs ?? '-' }}ms</template>
        </vxe-column>
        <vxe-column field="successCount" :title="t('agent.dag.history.success')" width="80" />
        <vxe-column field="failedCount" :title="t('agent.dag.history.failed')" width="80" />
        <vxe-column field="createdAt" :title="t('agent.dag.history.createdAt')" width="170" />
        <vxe-column :title="t('agent.dag.columns.action')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openNodeDetail(row)">
              {{ t('agent.dag.buttons.nodeDetail') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination
        v-model:current-page="instancePageNo"
        :total="instanceTotal"
        :page-size="20"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadInstances"
      />
    </el-dialog>

    <!-- 节点明细对话框 -->
    <el-dialog v-model="nodeDialogVisible" :title="t('agent.dag.nodeDetail.title')" width="900px">
      <vxe-table :data="nodeList" :loading="nodeLoading" stripe max-height="500">
        <vxe-column type="seq" width="56" title="#" />
        <vxe-column field="nodeName" :title="t('agent.dag.nodeDetail.name')" min-width="150" />
        <vxe-column field="agentType" :title="t('agent.dag.nodeDetail.agentType')" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.agentType" size="small">{{ row.agentType }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="status" :title="t('agent.dag.nodeDetail.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="retryCount" :title="t('agent.dag.nodeDetail.retries')" width="80" />
        <vxe-column field="costMs" :title="t('agent.dag.nodeDetail.cost')" width="100">
          <template #default="{ row }">{{ row.costMs ?? '-' }}ms</template>
        </vxe-column>
        <vxe-column field="startedAt" :title="t('agent.dag.nodeDetail.startedAt')" width="170" />
        <vxe-column field="errorMsg" :title="t('agent.dag.nodeDetail.error')" min-width="200" show-overflow>
          <template #default="{ row }">
            <el-text v-if="row.errorMsg" type="danger" size="small">{{ row.errorMsg }}</el-text>
            <span v-else>-</span>
          </template>
        </vxe-column>
      </vxe-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dag-page {
  .toolbar-card { margin-bottom: 0; }
  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .dag-designer {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    overflow: hidden;
    .designer-toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: var(--el-fill-color-light);
      border-bottom: 1px solid var(--el-border-color-lighter);
      .link-hint {
        font-size: 12px;
        color: var(--el-color-warning);
      }
    }
    .designer-body {
      display: flex;
      height: 400px;
    }
    .dag-canvas {
      flex: 1;
      background: var(--el-fill-color-blank);
      background-image: radial-gradient(circle, var(--el-border-color-lighter) 1px, transparent 1px);
      background-size: 20px 20px;
      cursor: default;
    }
    .dag-node-group {
      cursor: move;
      .dag-node-rect {
        fill: var(--el-color-primary-light-9);
        stroke: var(--el-color-primary);
        stroke-width: 1.5;
        rx: 6;
      }
      .dag-node-name {
        font-size: 12px;
        font-weight: 600;
        fill: var(--el-text-color-primary);
        pointer-events: none;
        user-select: none;
      }
      .dag-node-type {
        font-size: 10px;
        fill: var(--el-text-color-secondary);
        pointer-events: none;
        user-select: none;
      }
      .dag-link-dot {
        fill: var(--el-color-success);
        stroke: white;
        stroke-width: 1;
        cursor: crosshair;
        opacity: 0;
        transition: opacity 0.2s;
      }
      .dag-delete-btn {
        font-size: 10px;
        fill: var(--el-color-danger);
        cursor: pointer;
        opacity: 0;
        transition: opacity 0.2s;
      }
      &:hover .dag-link-dot,
      &:hover .dag-delete-btn {
        opacity: 1;
      }
      &.selected .dag-node-rect {
        stroke-width: 2.5;
        filter: drop-shadow(0 0 4px var(--el-color-primary));
      }
      &.linking .dag-node-rect {
        stroke: var(--el-color-warning);
        fill: var(--el-color-warning-light-9);
      }
    }
    .dag-edge {
      fill: none;
      stroke: var(--el-color-primary-light-5);
      stroke-width: 2;
      cursor: pointer;
      marker-end: url(#arrowhead);
      &:hover {
        stroke: var(--el-color-danger);
        stroke-width: 2.5;
      }
    }
    .property-panel {
      width: 280px;
      border-left: 1px solid var(--el-border-color-lighter);
      padding: 12px;
      overflow-y: auto;
      .property-form h4 {
        margin: 0 0 12px;
        font-size: 14px;
      }
      .empty-deps {
        font-size: 12px;
        color: var(--el-text-color-placeholder);
      }
      .property-empty {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
      }
    }
  }
  .kpi-row { margin-bottom: 8px; }
  .kpi-card {
    text-align: center;
    .kpi-title { font-size: 12px; color: var(--el-text-color-secondary); }
    .kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
    &.success .kpi-value { color: var(--el-color-success); }
    &.danger .kpi-value { color: var(--el-color-danger); }
  }
  .node-status-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 8px;
  }
  .node-status-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: var(--el-fill-color-light);
    border-radius: 6px;
    .node-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;
    }
    .node-name { flex: 1; font-size: 13px; }
    .retry-badge { font-size: 12px; color: var(--el-color-warning); }
  }
  .trace-item {
    display: flex;
    align-items: center;
    gap: 8px;
    .trace-node { font-weight: 600; font-size: 13px; }
    .trace-msg { font-size: 13px; color: var(--el-text-color-secondary); }
  }
}
</style>
