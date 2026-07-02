/**
 * @file 仪表盘 Widget 类型定义 (P2-12 可定制仪表盘)
 * @description 定义可拖拽仪表盘小部件的数据结构与按角色预设的布局模板,
 *              供 CustomDashboard 组件与业务页面共享.
 * @module types/dashboard
 */

/** 小部件类型: KPI 指标卡 / 图表 / 表格 / 待办 / 风险预警 */
export interface DashboardWidget {
  id: string
  type: 'kpi' | 'chart' | 'table' | 'todo' | 'alert'
  title: string
  x: number
  y: number
  w: number
  h: number
  config?: Record<string, unknown>
}

/** 用户级仪表盘布局 */
export interface DashboardLayout {
  id: string
  userId: number
  name: string
  widgets: DashboardWidget[]
  isDefault: boolean
}

/**
 * 按角色预设的 Widget 布局模板
 * - PM: 项目经理关注项目数 / 待办 / 工时 / 风险
 * - FINANCE: 财务关注合同 / 回款 / 发票 / 收入成本
 * - EXECUTIVE: 高管关注活跃项目 / 合同额 / 毛利率 / 利用率 / 经营驾驶舱
 */
export const WIDGET_PRESETS: Record<string, DashboardWidget[]> = {
  PM: [
    { id: 'w1', type: 'kpi', title: '我的项目', x: 0, y: 0, w: 6, h: 3 },
    { id: 'w2', type: 'kpi', title: '待办任务', x: 6, y: 0, w: 6, h: 3 },
    { id: 'w3', type: 'chart', title: '工时统计', x: 0, y: 3, w: 12, h: 6 },
    { id: 'w4', type: 'todo', title: '待办事项', x: 0, y: 9, w: 6, h: 6 },
    { id: 'w5', type: 'alert', title: '风险预警', x: 6, y: 9, w: 6, h: 6 },
  ],
  FINANCE: [
    { id: 'w1', type: 'kpi', title: '合同总额', x: 0, y: 0, w: 4, h: 3 },
    { id: 'w2', type: 'kpi', title: '回款总额', x: 4, y: 0, w: 4, h: 3 },
    { id: 'w3', type: 'kpi', title: '发票总额', x: 8, y: 0, w: 4, h: 3 },
    { id: 'w4', type: 'chart', title: '收入成本趋势', x: 0, y: 3, w: 12, h: 6 },
    { id: 'w5', type: 'table', title: '近期发票', x: 0, y: 9, w: 12, h: 6 },
  ],
  EXECUTIVE: [
    { id: 'w1', type: 'kpi', title: '活跃项目', x: 0, y: 0, w: 3, h: 3 },
    { id: 'w2', type: 'kpi', title: '总合同额', x: 3, y: 0, w: 3, h: 3 },
    { id: 'w3', type: 'kpi', title: '毛利率', x: 6, y: 0, w: 3, h: 3 },
    { id: 'w4', type: 'kpi', title: '利用率', x: 9, y: 0, w: 3, h: 3 },
    { id: 'w5', type: 'chart', title: '经营驾驶舱', x: 0, y: 3, w: 8, h: 8 },
    { id: 'w6', type: 'alert', title: '告警中心', x: 8, y: 3, w: 4, h: 8 },
  ],
}
