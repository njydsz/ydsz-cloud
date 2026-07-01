/**
 * @file 路由表定义
 * @description 定义前端静态路由（constantRoutes）与动态业务路由（asyncRoutes），
 *              静态路由包含登录/404/根布局默认页，动态路由按业务模块分组并通过权限码控制访问。
 * @module router/routes
 */
import type { RouteRecordRaw } from 'vue-router'
import { PC } from '@/constants/permissionCodes'

/**
 * 静态路由（无需权限）
 *
 * 仅包含登录页、404 页、根布局（仪表盘/安全设置/经营驾驶舱 3 个登录用户默认可访问的基础页面）和通配符兜底。
 * 所有业务路由全部走 asyncRoutes + 后端菜单动态注册，避免 URL 直接访问绕过权限。
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true },
  },
  {
    path: '/',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer', affix: true },
      },
      {
        path: 'profile/security',
        name: 'ProfileSecurity',
        component: () => import('@/views/profile/security.vue'),
        meta: { title: '安全设置', icon: 'Lock', hidden: true },
      },
      {
        path: 'cockpit',
        name: 'Cockpit',
        component: () => import('@/views/cockpit/index.vue'),
        meta: { title: '经营驾驶舱', icon: 'DataLine', permCode: PC.COCKPIT_OVERVIEW_VIEW },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true },
  },
]

/**
 * 动态路由（基于权限）
 *
 * 用作"后端菜单服务不可用时的本地兜底"，与后端菜单返回的路由合并注册。
 * 每个路由必须配置 meta.permCode，路由守卫会校验该权限码，缺失权限时跳转 /404。
 *
 * 注意：constantRoutes 中除根布局的 dashboard/profile/cockpit 外，所有业务路由必须在此声明。
 */
export const asyncRoutes: RouteRecordRaw[] = [
  {
    path: '/system',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', keepAlive: true, permCode: PC.AUTH_USER_LIST },
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', keepAlive: true, permCode: PC.AUTH_ROLE_LIST },
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'Menu', keepAlive: true, permCode: PC.AUTH_PERM_CREATE },
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', keepAlive: true, permCode: PC.ORG_DEPT_CREATE },
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '枚举值管理', icon: 'Collection', keepAlive: true, permCode: PC.SYS_CONFIG_LIST },
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '参数配置', icon: 'Tools', keepAlive: true, permCode: PC.SYS_CONFIG_LIST },
      },
      {
        path: 'feature-flag',
        name: 'SystemFeatureFlag',
        component: () => import('@/views/system/feature-flag/index.vue'),
        meta: { title: '特性开关', icon: 'Flag', keepAlive: true, permCode: PC.SYS_FEATURE_FLAG_VIEW },
      },
      {
        path: 'session',
        name: 'SystemSession',
        component: () => import('@/views/system/session/index.vue'),
        meta: { title: '会话管理', icon: 'Connection', keepAlive: true, permCode: PC.AUTH_USER_SESSION_LIST },
      },
      {
        path: 'import-export',
        name: 'SystemImportExport',
        component: () => import('@/views/system/import-export/index.vue'),
        meta: { title: '数据导入导出', icon: 'Upload', keepAlive: true, permCode: PC.FILE_STORAGE_UPLOAD },
      },
      {
        path: 'chaos',
        name: 'SystemChaos',
        component: () => import('@/views/chaos/index.vue'),
        meta: { title: '混沌工程', icon: 'Aim', keepAlive: true, permCode: PC.SYS_CHAOS_VIEW },
      },
    ],
  },
  {
    path: '/project',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/project/opportunity',
    meta: { title: '项目管理', icon: 'Briefcase' },
    children: [
      {
        path: 'opportunity',
        name: 'ProjectOpportunity',
        component: () => import('@/views/project/opportunity/index.vue'),
        meta: { title: '商机管理', icon: 'Aim', keepAlive: true, permCode: PC.PROJECT_OPPORTUNITY_LIST },
      },
      {
        path: 'initiation',
        name: 'ProjectInitiation',
        component: () => import('@/views/project/initiation/index.vue'),
        meta: { title: '立项管理', icon: 'DocumentAdd', keepAlive: true, permCode: PC.PROJECT_INITIATION_LIST },
      },
      {
        path: 'contract',
        name: 'ProjectContract',
        component: () => import('@/views/project/contract/index.vue'),
        meta: { title: '合同管理', icon: 'Notebook', keepAlive: true, permCode: PC.PROJECT_CONTRACT_LIST },
      },
      {
        path: 'contract-template',
        name: 'ProjectContractTemplate',
        component: () => import('@/views/project/contract-template/index.vue'),
        meta: { title: '合同模板', icon: 'Files', keepAlive: true, permCode: PC.PROJECT_CONTRACT_TEMPLATE_LIST },
      },
      {
        path: 'contract-change',
        name: 'ProjectContractChange',
        component: () => import('@/views/project/contract-change/index.vue'),
        meta: { title: '合同变更', icon: 'Refresh', keepAlive: true, permCode: PC.PROJECT_CONTRACT_CHANGE_LIST },
      },
      {
        path: 'change',
        name: 'ProjectChange',
        component: () => import('@/views/change/index.vue'),
        meta: { title: '项目变更', icon: 'EditPen', keepAlive: true, permCode: PC.PROJECT_CHANGE_LIST },
      },
    ],
  },
  {
    path: '/execution',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/execution/wbs-task',
    meta: { title: '执行管理', icon: 'Operation' },
    children: [
      {
        path: 'wbs-task',
        name: 'ExecutionWbsTask',
        component: () => import('@/views/execution/wbs-task/index.vue'),
        meta: { title: 'WBS 任务', icon: 'List', keepAlive: true, permCode: PC.EXECUTION_WBS_LIST },
      },
      {
        path: 'time-entry',
        name: 'ExecutionTimeEntry',
        component: () => import('@/views/execution/time-entry/index.vue'),
        meta: { title: '工时管理', icon: 'Clock', keepAlive: true, permCode: PC.EXECUTION_TIME_LIST },
      },
      {
        path: 'purchase',
        name: 'ExecutionPurchase',
        component: () => import('@/views/execution/purchase/index.vue'),
        meta: { title: '采购管理', icon: 'ShoppingCart', keepAlive: true, permCode: PC.EXECUTION_PURCHASE_LIST },
      },
      {
        path: 'expense',
        name: 'ExecutionExpense',
        component: () => import('@/views/execution/expense/index.vue'),
        meta: { title: '费用管理', icon: 'Wallet', keepAlive: true, permCode: PC.EXECUTION_EXPENSE_LIST },
      },
      {
        path: 'risk',
        name: 'ExecutionRisk',
        component: () => import('@/views/execution/risk/index.vue'),
        meta: { title: '风险管理', icon: 'WarningFilled', keepAlive: true, permCode: PC.EXECUTION_RISK_LIST },
      },
      {
        path: 'profit',
        name: 'ExecutionProfit',
        component: () => import('@/views/execution/profit/index.vue'),
        meta: { title: '收入/利润', icon: 'TrendCharts', keepAlive: true, permCode: PC.EXECUTION_PROFIT_LIST },
      },
      {
        path: 'evm',
        name: 'ExecutionEvm',
        component: () => import('@/views/execution/evm/index.vue'),
        meta: { title: 'EVM 挣值管理', icon: 'DataAnalysis', keepAlive: true, permCode: PC.EXECUTION_EVM_LIST },
      },
      {
        path: 'utilization',
        name: 'ExecutionUtilization',
        component: () => import('@/views/execution/utilization/index.vue'),
        meta: { title: '可计费利用率', icon: 'PieChart', keepAlive: true, permCode: PC.EXECUTION_UTILIZATION_VIEW },
      },
      {
        path: 'rate-card',
        name: 'ExecutionRateCard',
        component: () => import('@/views/execution/rate-card/index.vue'),
        meta: { title: '对外报价费率', icon: 'PriceTag', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'rate-internal',
        name: 'ExecutionRateInternal',
        component: () => import('@/views/execution/rate-internal/index.vue'),
        meta: { title: '内部职级费率', icon: 'Coin', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'profit-simulation',
        name: 'ExecutionProfitSimulation',
        component: () => import('@/views/execution/profit-simulation/index.vue'),
        meta: { title: '利润模拟', icon: 'MagicStick', keepAlive: true, permCode: PC.EXECUTION_SIMULATION_LIST },
      },
      {
        path: 'delivery',
        name: 'ExecutionDelivery',
        component: () => import('@/views/execution/delivery/index.vue'),
        meta: { title: '交付物', icon: 'Box', keepAlive: true, permCode: PC.EXECUTION_DELIVERY_LIST },
      },
      {
        path: 'closure',
        name: 'ExecutionClosure',
        component: () => import('@/views/execution/closure/index.vue'),
        meta: { title: '项目结项', icon: 'CircleCheck', keepAlive: true, permCode: PC.CLOSURE_LIST },
      },
      {
        path: 'alert',
        name: 'ExecutionAlert',
        component: () => import('@/views/execution/alert/index.vue'),
        meta: { title: '预警中心', icon: 'Bell', keepAlive: true, permCode: PC.COCKPIT_ALERT_VIEW },
      },
      {
        path: 'reconcile',
        name: 'ExecutionReconcile',
        component: () => import('@/views/execution/reconcile/index.vue'),
        meta: { title: '每日对账', icon: 'Document', keepAlive: true, permCode: PC.EXECUTION_RECONCILE_VIEW },
      },
    ],
  },
  {
    path: '/aftersales',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/aftersales/warranty',
    meta: { title: '售后管理', icon: 'Service' },
    children: [
      {
        path: 'warranty',
        name: 'AftersalesWarranty',
        component: () => import('@/views/aftersales/warranty/index.vue'),
        meta: { title: '质保期', icon: 'Lock', keepAlive: true, permCode: PC.AFTERSALES_WARRANTY_LIST },
      },
      {
        path: 'ops-ticket',
        name: 'AftersalesOpsTicket',
        component: () => import('@/views/aftersales/ops-ticket/index.vue'),
        meta: { title: '运维工单', icon: 'Tickets', keepAlive: true, permCode: PC.AFTERSALES_OPS_TICKET_LIST },
      },
      {
        path: 'satisfaction',
        name: 'AftersalesSatisfaction',
        component: () => import('@/views/aftersales/satisfaction/index.vue'),
        meta: { title: '满意度评价', icon: 'Star', keepAlive: true, permCode: PC.AFTERSALES_SATISFACTION_LIST },
      },
    ],
  },
  {
    path: '/finance',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/finance/invoice',
    meta: { title: '财务管理', icon: 'Money' },
    children: [
      {
        path: 'invoice',
        name: 'FinanceInvoice',
        component: () => import('@/views/execution/invoice/index.vue'),
        meta: { title: '发票管理', icon: 'Tickets', keepAlive: true, permCode: PC.FINANCE_INVOICE_LIST },
      },
      {
        path: 'payment',
        name: 'FinancePayment',
        component: () => import('@/views/execution/payment/index.vue'),
        meta: { title: '回款管理', icon: 'CreditCard', keepAlive: true, permCode: PC.FINANCE_PAYMENT_LIST },
      },
      {
        path: 'customer-credit',
        name: 'FinanceCustomerCredit',
        component: () => import('@/views/execution/customer-credit/index.vue'),
        meta: { title: '客户信用', icon: 'Medal', keepAlive: true, permCode: PC.FINANCE_CREDIT_LIST },
      },
    ],
  },
  {
    path: '/resource',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/resource/job-level',
    meta: { title: '资源管理', icon: 'UserFilled' },
    children: [
      {
        path: 'job-level',
        name: 'ResourceJobLevel',
        component: () => import('@/views/resource/job-level/index.vue'),
        meta: { title: '职级费率', icon: 'DataLine', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'pool',
        name: 'ResourcePool',
        component: () => import('@/views/resource/pool/index.vue'),
        meta: { title: '资源池', icon: 'Files', keepAlive: true, permCode: PC.RESOURCE_POOL_CREATE },
      },
      {
        path: 'employee-tag',
        name: 'ResourceEmployeeTag',
        component: () => import('@/views/resource/employee-tag/index.vue'),
        meta: { title: '人员标签', icon: 'CollectionTag', keepAlive: true, permCode: PC.RESOURCE_TAG_CREATE },
      },
      {
        path: 'assignment',
        name: 'ResourceAssignment',
        component: () => import('@/views/resource/assignment/index.vue'),
        meta: { title: '资源分配', icon: 'Connection', keepAlive: true, permCode: PC.RESOURCE_ASSIGN_ACT },
      },
      {
        path: 'bench',
        name: 'ResourceBench',
        component: () => import('@/views/resource/bench/index.vue'),
        meta: { title: 'Bench 闲置池', icon: 'Coin', keepAlive: true, permCode: PC.RESOURCE_BENCH_LIST },
      },
    ],
  },
  {
    path: '/attendance',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/attendance/index',
    meta: { title: '考勤管理', icon: 'Clock' },
    children: [
      {
        path: 'index',
        name: 'Attendance',
        component: () => import('@/views/attendance/index.vue'),
        meta: { title: '考勤中心', icon: 'Calendar', keepAlive: true, permCode: PC.ATTENDANCE_RECORD_LIST },
      },
    ],
  },
  {
    path: '/report',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/report/index',
    meta: { title: '报表中心', icon: 'DataAnalysis' },
    children: [
      {
        path: 'index',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: '报表', icon: 'Document', keepAlive: true, permCode: PC.REPORT_PROFIT_VIEW },
      },
      {
        path: 'executive',
        name: 'ReportExecutive',
        component: () => import('@/views/report/executive/index.vue'),
        meta: { title: '高管看板', icon: 'TrendCharts', keepAlive: true, permCode: PC.REPORT_EXECUTIVE_VIEW },
      },
    ],
  },
  {
    path: '/audit',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/audit/index',
    meta: { title: '审计', icon: 'Lock' },
    children: [
      {
        path: 'index',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '审计日志', icon: 'Document', keepAlive: true, permCode: PC.AUDIT_LOG_VIEW },
      },
    ],
  },
  {
    path: '/agent',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/agent/orchestration',
    meta: { title: 'AI 智能体', icon: 'MagicStick' },
    children: [
      {
        path: 'orchestration',
        name: 'AgentOrchestration',
        component: () => import('@/views/agent/orchestration/index.vue'),
        meta: { title: '多智能体编排', icon: 'Share', keepAlive: true, permCode: PC.AGENT_ORCHESTRATION_VIEW },
      },
      {
        path: 'prediction',
        name: 'AgentPrediction',
        component: () => import('@/views/agent/prediction/index.vue'),
        meta: { title: '预测结果历史', icon: 'DataAnalysis', keepAlive: true, permCode: PC.AGENT_PREDICTION_VIEW },
      },
    ],
  },
]
