/**
 * use-flow-designer 组合式函数 — 工作流设计器辅助能力
 *
 * @path comm\effects\shared-business\src\composables\use-flow-designer.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 为工作流/流程设计器提供高频交互增强：
 * - 画布键盘快捷键：Delete 删除选中节点、Ctrl+C/V 复制粘贴节点
 * - 节点选中状态管理
 * - 画布缩放与居中（迷你地图前置能力）
 *
 * 设计器画布实现由各子应用负责（vue-flow / bpmn.js / 自研 canvas），
 * 本 Hook 提供与 UI 无关的通用交互逻辑。
 */
import { ref } from 'vue';

/** 设计器节点 */
export interface FlowNode {
  id: string;
  type: string;
  label: string;
  [key: string]: any;
}

/** 设计器画布控制（由画布实现注入） */
export interface FlowCanvasControls {
  /** 删除节点 */
  deleteNode: (nodeId: string) => void;
  /** 复制节点 */
  copyNode: (nodeId: string) => void;
  /** 粘贴节点 */
  pasteNode: (position?: { x: number; y: number }) => void;
  /** 缩放画布（delta > 0 放大） */
  zoom?: (delta: number) => void;
  /** 居中画布 */
  center?: () => void;
  /** 获取画布缩放比例 */
  getZoom?: () => number;
}

/** 设计器配置 */
export interface FlowDesignerOptions {
  /** 节点列表 */
  nodes: FlowNode[];
  /** 画布控制能力（由业务注入） */
  controls: FlowCanvasControls;
  /** 是否启用快捷键，默认 true */
  enableShortcuts?: boolean;
}

/** 剪贴板（模块级单例，跨组件复制粘贴） */
const clipboard: { node: FlowNode | null } = { node: null };

/**
 * 工作流设计器辅助 Hook
 *
 * @example
 * ```ts
 * const designer = useFlowDesigner({
 *   nodes: nodeList,
 *   controls: { deleteNode, copyNode, pasteNode, zoom, center },
 * });
 * // 模板：@click="designer.selectNode(node)"
 * // 选中后 Delete 键删除、Ctrl+C/V 复制粘贴由内部 keydown 监听处理
 * ```
 */
export function useFlowDesigner(options: FlowDesignerOptions) {
  const { nodes, controls, enableShortcuts = true } = options;

  const selectedNodeId = ref<string | null>(null);

  function selectNode(node: FlowNode) {
    selectedNodeId.value = node.id;
  }

  function clearSelection() {
    selectedNodeId.value = null;
  }

  function getSelectedNode(): FlowNode | null {
    return nodes.find((n) => n.id === selectedNodeId.value) ?? null;
  }

  // ===== 快捷键 =====
  if (enableShortcuts && typeof window !== 'undefined') {
    window.addEventListener('keydown', handleKeydown);
  }

  function handleKeydown(event: KeyboardEvent) {
    const selected = getSelectedNode();
    const isInput = (event.target as HTMLElement)?.tagName?.match(
      /INPUT|TEXTAREA|SELECT/,
    );

    // Delete 删除选中节点
    if (event.key === 'Delete' && selected && !isInput) {
      event.preventDefault();
      controls.deleteNode(selected.id);
      selectedNodeId.value = null;
      return;
    }

    // Ctrl+C 复制
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'c' && selected && !isInput) {
      event.preventDefault();
      clipboard.node = structuredClone(selected);
      return;
    }

    // Ctrl+V 粘贴
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'v' && clipboard.node && !isInput) {
      event.preventDefault();
      controls.pasteNode();
    }

    // Ctrl+0 居中 / Ctrl+'+'/'-' 缩放
    if ((event.ctrlKey || event.metaKey) && event.key === '0' && controls.center) {
      event.preventDefault();
      controls.center();
    }
    if ((event.ctrlKey || event.metaKey) && event.key === '=' && controls.zoom) {
      event.preventDefault();
      controls.zoom(0.1);
    }
    if ((event.ctrlKey || event.metaKey) && event.key === '-' && controls.zoom) {
      event.preventDefault();
      controls.zoom(-0.1);
    }
  }

  function destroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('keydown', handleKeydown);
    }
  }

  return {
    clearSelection,
    destroy,
    getSelectedNode,
    selectNode,
    selectedNodeId,
  };
}
