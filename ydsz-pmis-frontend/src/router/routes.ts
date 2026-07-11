﻿﻿﻿/**
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
    meta: { title: 'route.login', hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: 'route.notFound', hidden: true },
  },
  {
    path: '/500',
    name: 'ServerError',
    component: () => import('@/views/error/500.vue'),
    meta: { title: 'route.serverError', hidden: true },
  },
  {
    // 重定向中转路由：用于 TagsView 的"刷新当前"功能
    // 访问 /redirect/xxx 时，由 redirect/index.vue 在 onBeforeMount 中 replace 回 /xxx
    path: '/redirect/:path(.*)',
    name: 'Redirect',
    component: () => import('@/views/redirect/index.vue'),
    meta: { title: 'route.redirect', hidden: true },
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
        meta: { title: 'route.dashboard', icon: 'Odometer', affix: true },
      },
      {
        path: 'profile/security',
        name: 'ProfileSecurity',
        component: () => import('@/views/profile/security.vue'),
        meta: { title: 'route.securitySettings', icon: 'Lock', hidden: true },
      },
      {
        path: 'cockpit',
        name: 'Cockpit',
        component: () => import('@/views/cockpit/index.vue'),
        meta: { title: 'route.cockpit', icon: 'DataLine', permCode: PC.COCKPIT_OVERVIEW_VIEW },
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
    meta: { title: 'route.system', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: 'route.systemUser', icon: 'User', keepAlive: true, permCode: PC.AUTH_USER_LIST },
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: 'route.systemRole', icon: 'UserFilled', keepAlive: true, permCode: PC.AUTH_ROLE_LIST },
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: 'route.systemMenu', icon: 'Menu', keepAlive: true, permCode: PC.AUTH_PERM_CREATE },
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: 'route.systemDept', icon: 'OfficeBuilding', keepAlive: true, permCode: PC.ORG_DEPT_CREATE },
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: 'route.systemDict', icon: 'Collection', keepAlive: true, permCode: PC.SYS_CONFIG_LIST },
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: 'route.systemConfig', icon: 'Tools', keepAlive: true, permCode: PC.SYS_CONFIG_LIST },
      },
      {
        path: 'feature-flag',
        name: 'SystemFeatureFlag',
        component: () => import('@/views/system/feature-flag/index.vue'),
        meta: { title: 'route.systemFeatureFlag', icon: 'Flag', keepAlive: true, permCode: PC.SYS_FEATURE_FLAG_VIEW },
      },
      {
        path: 'session',
        name: 'SystemSession',
        component: () => import('@/views/system/session/index.vue'),
        meta: { title: 'route.systemSession', icon: 'Connection', keepAlive: true, permCode: PC.AUTH_USER_SESSION_LIST },
      },
      {
        path: 'import-export',
        name: 'SystemImportExport',
        component: () => import('@/views/system/import-export/index.vue'),
        meta: { title: 'route.systemImportExport', icon: 'Upload', keepAlive: true, permCode: PC.FILE_STORAGE_UPLOAD },
      },
      {
        path: 'chaos',
        name: 'SystemChaos',
        component: () => import('@/views/chaos/index.vue'),
        meta: { title: 'route.systemChaos', icon: 'Aim', keepAlive: true, permCode: PC.SYS_CHAOS_VIEW },
      },
    ],
  },
  {
    path: '/project',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/project/opportunity',
    meta: { title: 'route.project', icon: 'Briefcase' },
    children: [
      {
        path: 'opportunity',
        name: 'ProjectOpportunity',
        component: () => import('@/views/project/opportunity/index.vue'),
        meta: { title: 'route.projectOpportunity', icon: 'Aim', keepAlive: true, permCode: PC.PROJECT_OPPORTUNITY_LIST },
      },
      {
        path: 'opportunity-follow',
        name: 'ProjectOpportunityFollow',
        component: () => import('@/views/project/opportunity-follow/index.vue'),
        meta: { title: 'route.projectOpportunityFollow', icon: 'ChatLineRound', keepAlive: true },
      },
      {
        path: 'initiation',
        name: 'ProjectInitiation',
        component: () => import('@/views/project/initiation/index.vue'),
        meta: { title: 'route.projectInitiation', icon: 'DocumentAdd', keepAlive: true, permCode: PC.PROJECT_INITIATION_LIST },
      },
      {
        path: 'contract',
        name: 'ProjectContract',
        component: () => import('@/views/project/contract/index.vue'),
        meta: { title: 'route.projectContract', icon: 'Notebook', keepAlive: true, permCode: PC.PROJECT_CONTRACT_LIST },
      },
      {
        path: 'contract-template',
        name: 'ProjectContractTemplate',
        component: () => import('@/views/project/contract-template/index.vue'),
        meta: { title: 'route.projectContractTemplate', icon: 'Files', keepAlive: true, permCode: PC.PROJECT_CONTRACT_TEMPLATE_LIST },
      },
      {
        path: 'contract-change',
        name: 'ProjectContractChange',
        component: () => import('@/views/project/contract-change/index.vue'),
        meta: { title: 'route.projectContractChange', icon: 'Refresh', keepAlive: true, permCode: PC.PROJECT_CONTRACT_CHANGE_LIST },
      },
      {
        path: 'contract-supplement',
        name: 'ProjectContractSupplement',
        component: () => import('@/views/project/contract-supplement/index.vue'),
        meta: { title: 'route.projectContractSupplement', icon: 'Document', keepAlive: true },
      },
      {
        path: 'change',
        name: 'ProjectChange',
        component: () => import('@/views/change/index.vue'),
        meta: { title: 'route.projectChange', icon: 'EditPen', keepAlive: true, permCode: PC.PROJECT_CHANGE_LIST },
      },
    ],
  },
  {
    path: '/execution',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/execution/wbs-task',
    meta: { title: 'route.execution', icon: 'Operation' },
    children: [
      {
        path: 'wbs-task',
        name: 'ExecutionWbsTask',
        component: () => import('@/views/execution/wbs-task/index.vue'),
        meta: { title: 'route.executionWbsTask', icon: 'List', keepAlive: true, permCode: PC.EXECUTION_WBS_LIST },
      },
      {
        path: 'time-entry',
        name: 'ExecutionTimeEntry',
        component: () => import('@/views/execution/time-entry/index.vue'),
        meta: { title: 'route.executionTimeEntry', icon: 'Clock', keepAlive: true, permCode: PC.EXECUTION_TIME_LIST },
      },
      {
        path: 'purchase',
        name: 'ExecutionPurchase',
        component: () => import('@/views/execution/purchase/index.vue'),
        meta: { title: 'route.executionPurchase', icon: 'ShoppingCart', keepAlive: true, permCode: PC.EXECUTION_PURCHASE_LIST },
      },
      {
        path: 'budget',
        name: 'ExecutionBudget',
        component: () => import('@/views/execution/budget/index.vue'),
        meta: { title: 'route.executionBudget', icon: 'Money', keepAlive: true },
      },
      {
        path: 'risk',
        name: 'ExecutionRisk',
        component: () => import('@/views/execution/risk/index.vue'),
        meta: { title: 'route.executionRisk', icon: 'WarningFilled', keepAlive: true, permCode: PC.EXECUTION_RISK_LIST },
      },
      {
        path: 'evm',
        name: 'ExecutionEvm',
        component: () => import('@/views/execution/evm/index.vue'),
        meta: { title: 'route.executionEvm', icon: 'DataAnalysis', keepAlive: true, permCode: PC.EXECUTION_EVM_LIST },
      },
      {
        path: 'delivery',
        name: 'ExecutionDelivery',
        component: () => import('@/views/execution/delivery/index.vue'),
        meta: { title: 'route.executionDelivery', icon: 'Box', keepAlive: true, permCode: PC.EXECUTION_DELIVERY_LIST },
      },
      {
        path: 'alert',
        name: 'ExecutionAlert',
        component: () => import('@/views/execution/alert/index.vue'),
        meta: { title: 'route.executionAlert', icon: 'Bell', keepAlive: true, permCode: PC.COCKPIT_ALERT_VIEW },
      },
      {
        path: 'rule-engine',
        name: 'RuleEngine',
        component: () => import('@/views/execution/rule-engine/index.vue'),
        meta: { title: 'route.executionRuleEngine', icon: 'Setting', affix: false, keepAlive: true, permCode: PC.EXECUTION_RULE_ENGINE_VIEW },
      },
      {
        path: 'rule-engine/dashboard',
        name: 'RuleEngineDashboard',
        component: () => import('@/views/execution/rule-engine/dashboard.vue'),
        meta: { title: 'rule-engine.dashboard', icon: 'DataLine', activeMenu: '/execution/rule-engine', keepAlive: true, permCode: PC.EXECUTION_RULE_ENGINE_VIEW },
      },
      {
        path: 'rule-engine/designer/:ruleCode',
        name: 'RuleEngineDesigner',
        component: () => import('@/views/execution/rule-engine/designer.vue'),
        meta: { title: 'rule-engine.designer', icon: 'Connection', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_DESIGN },
      },
      {
        path: 'rule-engine/traces',
        name: 'RuleEngineTraces',
        component: () => import('@/views/execution/rule-engine/traces.vue'),
        meta: { title: 'rule-engine.traces', icon: 'DataLine', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_TRACES },
      },
      {
        path: 'rule-engine/dependency-graph',
        name: 'RuleEngineDependencyGraph',
        component: () => import('@/views/execution/rule-engine/dependency-graph.vue'),
        meta: { title: 'rule-engine.dependencyGraph', icon: 'Connection', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_VIEW },
      },
      {
        path: 'rule-engine/decision-table/:ruleCode',
        name: 'RuleEngineDecisionTable',
        component: () => import('@/views/execution/rule-engine/decision-table-editor.vue'),
        meta: { title: 'rule-engine.decisionTable', icon: 'Grid', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_DESIGN },
      },
      {
        path: 'rule-engine/decision-tree/:ruleCode',
        name: 'RuleEngineDecisionTree',
        component: () => import('@/views/execution/rule-engine/decision-tree-editor.vue'),
        meta: { title: 'rule-engine.decisionTree', icon: 'Share', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_DESIGN },
      },
      {
        path: 'rule-engine/scorecard/:ruleCode',
        name: 'RuleEngineScorecard',
        component: () => import('@/views/execution/rule-engine/scorecard-editor.vue'),
        meta: { title: 'rule-engine.scorecard', icon: 'Histogram', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_DESIGN },
      },
      {
        path: 'rule-engine/pack-market',
        component: () => import('@/views/execution/rule-engine/pack-market.vue'),
        meta: { title: 'rule-engine.packMarket', icon: 'Box', activeMenu: '/execution/rule-engine', permCode: PC.EXECUTION_RULE_ENGINE_PACK_MARKET },
      },
      {
        path: 'rule-engine/cep-patterns',
        name: 'RuleEngineCepPatterns',
        component: () => import('@/views/execution/rule-engine/cep-pattern-editor.vue'),
        meta: { title: 'rule-engine.cepPatterns', icon: 'Cpu', activeMenu: '/execution/rule-engine', hidden: true, permCode: PC.EXECUTION_RULE_ENGINE_DESIGN },
      },
      {
        path: 'rule-engine/variables',
        name: 'RuleEngineVariables',
        component: () => import('@/views/rule-engine/variables/index.vue'),
        meta: { title: 'rule-engine.variables', icon: 'Setting', activeMenu: '/execution/rule-engine', keepAlive: true, permCode: PC.EXECUTION_RULE_ENGINE_VIEW },
      },
    ],
  },
  {
    path: '/aftersales',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/aftersales/warranty',
    meta: { title: 'route.aftersales', icon: 'Service' },
    children: [
      {
        path: 'warranty',
        name: 'AftersalesWarranty',
        component: () => import('@/views/aftersales/warranty/index.vue'),
        meta: { title: 'route.aftersalesWarranty', icon: 'Lock', keepAlive: true, permCode: PC.AFTERSALES_WARRANTY_LIST },
      },
      {
        path: 'ops-ticket',
        name: 'AftersalesOpsTicket',
        component: () => import('@/views/aftersales/ops-ticket/index.vue'),
        meta: { title: 'route.aftersalesOpsTicket', icon: 'Tickets', keepAlive: true, permCode: PC.AFTERSALES_OPS_TICKET_LIST },
      },
      {
        path: 'satisfaction',
        name: 'AftersalesSatisfaction',
        component: () => import('@/views/aftersales/satisfaction/index.vue'),
        meta: { title: 'route.aftersalesSatisfaction', icon: 'Star', keepAlive: true, permCode: PC.AFTERSALES_SATISFACTION_LIST },
      },
    ],
  },
  {
    path: '/finance',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/finance/invoice',
    meta: { title: 'route.finance', icon: 'Money' },
    children: [
      {
        path: 'invoice',
        name: 'FinanceInvoice',
        component: () => import('@/views/finance/invoice/index.vue'),
        meta: { title: 'route.financeInvoice', icon: 'Tickets', keepAlive: true, permCode: PC.FINANCE_INVOICE_LIST },
      },
      {
        path: 'payment',
        name: 'FinancePayment',
        component: () => import('@/views/finance/payment/index.vue'),
        meta: { title: 'route.financePayment', icon: 'CreditCard', keepAlive: true, permCode: PC.FINANCE_PAYMENT_LIST },
      },
      {
        path: 'customer-credit',
        name: 'FinanceCustomerCredit',
        component: () => import('@/views/finance/credit/index.vue'),
        meta: { title: 'route.financeCustomerCredit', icon: 'Medal', keepAlive: true, permCode: PC.FINANCE_CREDIT_LIST },
      },
      {
        path: 'expense',
        name: 'FinanceExpense',
        component: () => import('@/views/finance/expense/index.vue'),
        meta: { title: 'route.financeExpense', icon: 'Wallet', keepAlive: true, permCode: PC.EXECUTION_EXPENSE_LIST },
      },
      {
        path: 'profit',
        name: 'FinanceProfit',
        component: () => import('@/views/finance/profit/index.vue'),
        meta: { title: 'route.financeProfit', icon: 'TrendCharts', keepAlive: true, permCode: PC.EXECUTION_PROFIT_LIST },
      },
      {
        path: 'profit-simulation',
        name: 'FinanceProfitSimulation',
        component: () => import('@/views/finance/profit-simulation/index.vue'),
        meta: { title: 'route.financeProfitSimulation', icon: 'MagicStick', keepAlive: true, permCode: PC.EXECUTION_SIMULATION_LIST },
      },
      {
        path: 'reconcile',
        name: 'FinanceReconcile',
        component: () => import('@/views/finance/reconcile/index.vue'),
        meta: { title: 'route.financeReconcile', icon: 'Document', keepAlive: true, permCode: PC.EXECUTION_RECONCILE_VIEW },
      },
      {
        path: 'revenue',
        name: 'FinanceRevenue',
        component: () => import('@/views/finance/revenue/index.vue'),
        meta: { title: 'route.financeRevenue', icon: 'Gold', keepAlive: true },
      },
    ],
  },
  {
    path: '/closure',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/closure/index',
    meta: { title: 'route.closure', icon: 'CircleCheck' },
    children: [
      {
        path: 'index',
        name: 'ProjectClosure',
        component: () => import('@/views/closure/index.vue'),
        meta: { title: 'route.closureIndex', icon: 'CircleCheck', keepAlive: true, permCode: PC.CLOSURE_LIST },
      },
    ],
  },
  {
    path: '/resource',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/resource/rank',
    meta: { title: 'route.resource', icon: 'UserFilled' },
    children: [
      {
        path: 'rank',
        name: 'ResourceRank',
        component: () => import('@/views/resource/rank/index.vue'),
        meta: { title: 'route.resourceRank', icon: 'DataLine', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'part-time-rate',
        name: 'ResourcePartTimeRate',
        component: () => import('@/views/resource/part-time-rate/index.vue'),
        meta: { title: 'route.resourcePartTimeRate', icon: 'Coin', keepAlive: true, permCode: PC.RESOURCE_PART_TIME_RATE_LIST },
      },
      {
        path: 'outsource-rate',
        name: 'ResourceOutsourceRate',
        component: () => import('@/views/resource/outsource-rate/index.vue'),
        meta: { title: 'route.resourceOutsourceRate', icon: 'Briefcase', keepAlive: true },
      },
      {
        path: 'employee',
        name: 'ResourceEmployee',
        component: () => import('@/views/resource/employee/index.vue'),
        meta: { title: 'route.resourceEmployee', icon: 'User', keepAlive: true, permCode: PC.RESOURCE_EMPLOYEE_LIST },
      },
      {
        path: 'pool',
        name: 'ResourcePool',
        component: () => import('@/views/resource/pool/index.vue'),
        meta: { title: 'route.resourcePool', icon: 'Files', keepAlive: true, permCode: PC.RESOURCE_POOL_CREATE },
      },
      {
        path: 'employee-tag',
        name: 'ResourceEmployeeTag',
        component: () => import('@/views/resource/employee-tag/index.vue'),
        meta: { title: 'route.resourceEmployeeTag', icon: 'CollectionTag', keepAlive: true, permCode: PC.RESOURCE_TAG_CREATE },
      },
      {
        path: 'assignment',
        name: 'ResourceAssignment',
        component: () => import('@/views/resource/assignment/index.vue'),
        meta: { title: 'route.resourceAssignment', icon: 'Connection', keepAlive: true, permCode: PC.RESOURCE_ASSIGN_ACT },
      },
      {
        path: 'bench',
        name: 'ResourceBench',
        component: () => import('@/views/resource/bench/index.vue'),
        meta: { title: 'route.resourceBench', icon: 'Coin', keepAlive: true, permCode: PC.RESOURCE_BENCH_LIST },
      },
      {
        path: 'rate-card',
        name: 'ResourceRateCard',
        component: () => import('@/views/resource/rate-card/index.vue'),
        meta: { title: 'route.resourceRateCard', icon: 'PriceTag', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'rate-internal',
        name: 'ResourceRateInternal',
        component: () => import('@/views/resource/rate-internal/index.vue'),
        meta: { title: 'route.resourceRateInternal', icon: 'Coin', keepAlive: true, permCode: PC.EXECUTION_RATE_LIST },
      },
      {
        path: 'utilization',
        name: 'ResourceUtilization',
        component: () => import('@/views/resource/utilization/index.vue'),
        meta: { title: 'route.resourceUtilization', icon: 'PieChart', keepAlive: true, permCode: PC.EXECUTION_UTILIZATION_VIEW },
      },
    ],
  },
  {
    path: '/attendance',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/attendance/index',
    meta: { title: 'route.attendance', icon: 'Clock' },
    children: [
      {
        path: 'index',
        name: 'Attendance',
        component: () => import('@/views/attendance/index.vue'),
        meta: { title: 'route.attendanceIndex', icon: 'Calendar', keepAlive: true, permCode: PC.ATTENDANCE_RECORD_LIST },
      },
    ],
  },
  {
    path: '/report',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/report/index',
    meta: { title: 'route.report', icon: 'DataAnalysis' },
    children: [
      {
        path: 'index',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: 'route.reportIndex', icon: 'Document', keepAlive: true, permCode: PC.REPORT_PROFIT_VIEW },
      },
      {
        path: 'executive',
        name: 'ReportExecutive',
        component: () => import('@/views/report/executive/index.vue'),
        meta: { title: 'route.reportExecutive', icon: 'TrendCharts', keepAlive: true, permCode: PC.REPORT_EXECUTIVE_VIEW },
      },
      {
        path: 'advanced',
        name: 'ReportAdvanced',
        component: () => import('@/views/report/advanced/index.vue'),
        meta: { title: 'route.reportAdvanced', icon: 'DataAnalysis', keepAlive: true },
      },
      {
        path: 'cockpit',
        name: 'ReportCockpit',
        component: () => import('@/views/report/cockpit/index.vue'),
        meta: { title: 'route.reportCockpit', icon: 'Odometer', keepAlive: true },
      },
      {
        path: 'export',
        name: 'ReportExport',
        component: () => import('@/views/report/export/index.vue'),
        meta: { title: 'route.reportExport', icon: 'Download', keepAlive: true },
      },
      {
        path: 'subscription',
        name: 'ReportSubscription',
        component: () => import('@/views/report/subscription/index.vue'),
        meta: { title: 'route.reportSubscription', icon: 'Bell', keepAlive: true },
      },
    ],
  },
  {
    path: '/audit',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/audit/index',
    meta: { title: 'route.audit', icon: 'Lock' },
    children: [
      {
        path: 'index',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: 'route.auditIndex', icon: 'Document', keepAlive: true, permCode: PC.AUDIT_LOG_VIEW },
      },
    ],
  },
  {
    path: '/agent',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/agent/orchestration',
    meta: { title: 'route.agent', icon: 'MagicStick' },
    children: [
      {
        path: 'orchestration',
        name: 'AgentOrchestration',
        component: () => import('@/views/agent/orchestration/index.vue'),
        meta: { title: 'route.agentOrchestration', icon: 'Share', keepAlive: true, permCode: PC.AGENT_ORCHESTRATION_VIEW },
      },
      {
        path: 'debug-console',
        name: 'AgentDebugConsole',
        component: () => import('@/views/agent/debug-console/index.vue'),
        meta: { title: 'route.agentDebugConsole', icon: 'Monitor', keepAlive: false, permCode: PC.AGENT_DEBUG_VIEW },
      },
      {
        path: 'dag',
        name: 'AgentDag',
        component: () => import('@/views/agent/dag/index.vue'),
        meta: { title: 'route.agentDag', icon: 'Connection', keepAlive: true, permCode: PC.AGENT_DAG_VIEW },
      },
      {
        path: 'prompt-template',
        name: 'AgentPromptTemplate',
        component: () => import('@/views/agent/prompt-template/index.vue'),
        meta: { title: 'route.agentPromptTemplate', icon: 'Document', keepAlive: true, permCode: PC.AGENT_PROMPT_VIEW },
      },
      {
        path: 'prediction',
        name: 'AgentPrediction',
        component: () => import('@/views/agent/prediction/index.vue'),
        meta: { title: 'route.agentPrediction', icon: 'DataAnalysis', keepAlive: true, permCode: PC.AGENT_PREDICTION_VIEW },
      },
      {
        path: 'hitl',
        name: 'AgentHitl',
        component: () => import('@/views/agent/hitl/index.vue'),
        meta: { title: 'route.agentHitl', icon: 'Check', keepAlive: true, permCode: PC.AGENT_HITL_LIST },
      },
      {
        path: 'knowledge-base',
        name: 'AgentKnowledgeBase',
        component: () => import('@/views/agent/knowledge-base/index.vue'),
        meta: { title: 'route.agentKnowledgeBase', icon: 'FolderOpened', keepAlive: true, permCode: PC.AGENT_KB_VIEW },
      },
      {
        path: 'token-quota',
        name: 'AgentTokenQuota',
        component: () => import('@/views/agent/token-quota/index.vue'),
        meta: { title: 'route.agentTokenQuota', icon: 'Coin', keepAlive: true, permCode: PC.AGENT_TOKEN_QUOTA_VIEW },
      },
      {
        path: 'trace',
        name: 'AgentTrace',
        component: () => import('@/views/agent/trace/index.vue'),
        meta: { title: 'route.agentTrace', icon: 'DataLine', keepAlive: true, permCode: PC.AGENT_HISTORY },
      },
      {
        path: 'evaluation',
        name: 'AgentEvaluation',
        component: () => import('@/views/agent/evaluation/index.vue'),
        meta: { title: 'route.agentEvaluation', icon: 'Histogram', keepAlive: false, permCode: PC.AGENT_HISTORY },
      },
    ],
  },
  {
    path: '/workflow',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/workflow/approval-center',
    meta: { title: 'route.workflowCenter', icon: 'Connection' },
    children: [
      {
        path: 'approval-center',
        name: 'WorkflowApprovalCenter',
        component: () => import('@/views/workflow/approval-center/index.vue'),
        meta: { title: 'route.workflowApprovalCenter', icon: 'Tickets', keepAlive: true, permCode: PC.WORKFLOW_APPROVAL_CENTER },
      },
      {
        path: 'design',
        name: 'WorkflowDesign',
        component: () => import('@/views/workflow/design/index.vue'),
        meta: { title: 'route.workflowDesign', icon: 'Edit', keepAlive: true, permCode: PC.WORKFLOW_DEFINITION_LIST },
      },
      {
        path: 'instance',
        name: 'WorkflowInstance',
        component: () => import('@/views/workflow/instance/index.vue'),
        meta: { title: 'route.workflowInstance', icon: 'View', hidden: true, permCode: PC.WORKFLOW_DIAGRAM },
      },
      {
        path: 'monitor',
        name: 'WorkflowMonitor',
        component: () => import('@/views/workflow/monitor/index.vue'),
        meta: { title: 'route.workflowMonitor', icon: 'Monitor', keepAlive: true, permCode: PC.WORKFLOW_MONITOR },
      },
      {
        path: 'form-design',
        name: 'WorkflowFormDesign',
        component: () => import('@/views/workflow/form-design/index.vue'),
        meta: { title: 'route.workflowFormDesign', icon: 'Document', keepAlive: true, permCode: PC.WORKFLOW_DEFINITION_CREATE },
      },
      {
        path: 'delegate-auth',
        name: 'WorkflowDelegateAuth',
        component: () => import('@/views/workflow/delegate-auth/index.vue'),
        meta: { title: 'route.workflowDelegateAuth', icon: 'Switch', keepAlive: true, permCode: PC.WORKFLOW_DELEGATE_AUTH_VIEW },
      },
      {
        path: 'sla',
        name: 'WorkflowSla',
        component: () => import('@/views/workflow/sla/index.vue'),
        meta: { title: 'route.workflowSla', icon: 'Timer', keepAlive: true, permCode: PC.WORKFLOW_SLA_VIEW },
      },
      {
        path: 'canary',
        name: 'WorkflowCanary',
        component: () => import('@/views/workflow/canary/index.vue'),
        meta: { title: 'route.workflowCanary', icon: 'Promotion', keepAlive: true, permCode: PC.WORKFLOW_CANARY_VIEW },
      },
      {
        path: 'history',
        name: 'WorkflowHistory',
        component: () => import('@/views/workflow/history/index.vue'),
        meta: { title: 'route.workflowHistory', icon: 'Files', keepAlive: true, permCode: PC.WORKFLOW_HISTORY_ARCHIVE_VIEW },
      },
      {
        path: 'instance-migration',
        name: 'Workflow_InstanceMigration',
        component: () => import('@/views/workflow/instance-migration/index.vue'),
        meta: { title: 'workflow.instanceMigration.title', icon: 'Switch', keepAlive: true, permCode: PC.WORKFLOW_DEFINITION_LIST },
      },
      {
        path: 'dmn',
        name: 'WorkflowDmn',
        component: () => import('@/views/workflow/dmn/index.vue'),
        meta: { title: 'route.workflowDmn', icon: 'Grid', keepAlive: true, permCode: PC.WORKFLOW_DEFINITION_LIST },
      },
    ],
  },
  {
    path: '/notification',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/notification/inbox',
    meta: { title: 'route.notificationCenter', icon: 'ChatDotRound' },
    children: [
      {
        path: 'inbox',
        name: 'NotificationInbox',
        component: () => import('@/views/notification/inbox.vue'),
        meta: { title: 'route.notificationInbox', icon: 'Message', keepAlive: true, permCode: PC.NOTIF_MESSAGE_LIST },
      },
    ],
  },
  {
    path: '/cronjob',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/cronjob/list',
    meta: { title: 'route.cronjob', icon: 'Timer' },
    children: [
      {
        path: 'list',
        name: 'CronjobList',
        component: () => import('@/views/cronjob/index.vue'),
        meta: { title: 'route.cronjobList', icon: 'List', keepAlive: true, permCode: PC.CRONJOB_JOB_LIST },
      },
      {
        path: 'log',
        name: 'CronjobLog',
        component: () => import('@/views/cronjob/log/index.vue'),
        meta: { title: 'route.cronjobLog', icon: 'Document', keepAlive: true, permCode: PC.CRONJOB_LOG_VIEW },
      },
      {
        path: 'log/content/:logId',
        name: 'CronjobLogContent',
        component: () => import('@/views/cronjob/log/content.vue'),
        meta: { title: 'route.cronjobLogContent', icon: 'Document', hidden: true, permCode: PC.CRONJOB_LOG_VIEW },
      },
      {
        path: 'monitor',
        name: 'CronjobMonitor',
        component: () => import('@/views/cronjob/monitor/index.vue'),
        meta: { title: 'route.cronjobMonitor', icon: 'Monitor', keepAlive: true, permCode: PC.CRONJOB_STATS_VIEW },
      },
    ],
  },
  {
    path: '/message',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/message/log',
    meta: { title: 'route.messageCenter', icon: 'Message' },
    children: [
      {
        path: 'log',
        name: 'MessageLog',
        component: () => import('@/views/message/log/index.vue'),
        meta: { title: 'route.messageLog', icon: 'Document', keepAlive: true, permCode: PC.MESSAGE_LOG_VIEW },
      },
      {
        path: 'template',
        name: 'MessageTemplate',
        component: () => import('@/views/message/template/index.vue'),
        meta: { title: 'route.messageTemplate', icon: 'Files', keepAlive: true, permCode: PC.MESSAGE_TEMPLATE_LIST },
      },
      {
        path: 'stats',
        name: 'MessageStats',
        component: () => import('@/views/message/stats/index.vue'),
        meta: { title: 'route.messageStats', icon: 'DataLine', keepAlive: true, permCode: PC.MESSAGE_LOG_VIEW },
      },
      {
        path: 'canary',
        name: 'MessageCanary',
        component: () => import('@/views/message/canary/index.vue'),
        meta: { title: 'route.messageCanary', icon: 'Connection', keepAlive: true, permCode: PC.MESSAGE_CANARY_VIEW },
      },
      {
        path: 'dead-letter',
        name: 'MessageDeadLetter',
        component: () => import('@/views/message/dead-letter/index.vue'),
        meta: { title: 'route.messageDeadLetter', icon: 'WarningFilled', keepAlive: true, permCode: PC.MESSAGE_DEAD_LETTER_VIEW },
      },
      {
        path: 'preference',
        name: 'MessagePreference',
        component: () => import('@/views/message/preference/index.vue'),
        meta: { title: 'route.messagePreference', icon: 'Setting', keepAlive: true, permCode: PC.MESSAGE_PREFERENCE_VIEW },
      },
    ],
  },
]
