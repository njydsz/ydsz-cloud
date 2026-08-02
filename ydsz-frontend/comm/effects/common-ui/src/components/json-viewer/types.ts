/**
 * types 模块
 *
 * @path comm\effects\common-ui\src\components\json-viewer\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export interface JsonViewerProps {
  /** 要展示的结构数据 */
  value: any;
  /** 展开深度 */
  expandDepth?: number;
  /** 是否可复制 */
  copyable?: boolean;
  /** 是否排序 */
  sort?: boolean;
  /** 显示边框 */
  boxed?: boolean;
  /** 主题 */
  theme?: string;
  /** 是否展开 */
  expanded?: boolean;
  /** 时间格式化函数 */
  timeformat?: (time: Date | number | string) => string;
  /** 预览模式 */
  previewMode?: boolean;
  /** 显示数组索引 */
  showArrayIndex?: boolean;
  /** 显示双引号 */
  showDoubleQuotes?: boolean;
}

/**
 * 复制完成（`copied`）事件的载荷，由底层 `vue-json-viewer` 透传。
 *
 * @remarks
 * 仅在 `copyable` 开启且复制成功后派发；复制失败不会触发该事件。
 */
export interface JsonViewerAction {
  /** 剪贴板动作类型，通常为 `'copy'` */
  action: string;
  /** 实际写入剪贴板的文本（序列化后的 JSON 字符串） */
  text: string;
  /** 触发复制的 DOM 元素，可用于定位提示气泡 */
  trigger: HTMLElement;
}

/**
 * 点击 JSON 值节点（`valueClick`）事件的载荷。
 *
 * @remarks
 * 组件通过事件委托从 DOM 的 `path` / `depth` 属性反解出节点信息，
 * 并对节点文本做 `JSON.parse`。因此：
 * - 只有点击到值节点（`.jv-item`）才会派发，点击 key 或空白区域不会；
 * - 若节点文本不是合法 JSON，`JSON.parse` 会抛错并中断本次事件派发。
 */
export interface JsonViewerValue {
  /** 解析后的节点值；文本无法解析时不会派发事件 */
  value: any;
  /** 该节点在 JSON 中的访问路径，如 `'data.list[0].id'`；取不到时为空字符串 */
  path: string;
  /** 节点所处的嵌套层级，根层为 0；取不到时兜底为 0 */
  depth: number;
  /** 被点击的 DOM 元素 */
  el: HTMLElement;
}

/**
 * 展开 / 收起节点（`toggle`）事件的载荷。
 */
export interface JsonViewerToggle {
  /** 触发展开或收起的鼠标事件 */
  event: MouseEvent;
  /** 切换后的展开状态：true 表示已展开 */
  open: boolean;
}
