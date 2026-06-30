import type { RouteRecordRaw } from 'vue-router'

/**
 * 静态路由（无需权限）
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
        path: 'cockpit',
        name: 'Cockpit',
        component: () => import('@/views/cockpit/index.vue'),
        meta: { title: '经营驾驶舱', icon: 'DataLine' },
      },
    ],
  },
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
        meta: { title: '用户管理', icon: 'User', keepAlive: true },
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled', keepAlive: true },
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'Menu', keepAlive: true },
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', keepAlive: true },
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '枚举值管理', icon: 'Collection', keepAlive: true },
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '参数配置', icon: 'Tools', keepAlive: true },
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
        meta: { title: '商机管理', icon: 'Aim', keepAlive: true },
      },
      {
        path: 'initiation',
        name: 'ProjectInitiation',
        component: () => import('@/views/project/initiation/index.vue'),
        meta: { title: '立项管理', icon: 'DocumentAdd', keepAlive: true },
      },
      {
        path: 'contract',
        name: 'ProjectContract',
        component: () => import('@/views/project/contract/index.vue'),
        meta: { title: '合同管理', icon: 'Notebook', keepAlive: true },
      },
      {
        path: 'contract-template',
        name: 'ProjectContractTemplate',
        component: () => import('@/views/project/contract-template/index.vue'),
        meta: { title: '合同模板', icon: 'Files', keepAlive: true },
      },
      {
        path: 'contract-change',
        name: 'ProjectContractChange',
        component: () => import('@/views/project/contract-change/index.vue'),
        meta: { title: '合同变更', icon: 'Refresh', keepAlive: true },
      },
      {
        path: 'change',
        name: 'ProjectChange',
        component: () => import('@/views/change/index.vue'),
        meta: { title: '项目变更', icon: 'EditPen', keepAlive: true },
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
        meta: { title: 'WBS 任务', icon: 'List', keepAlive: true },
      },
      {
        path: 'time-entry',
        name: 'ExecutionTimeEntry',
        component: () => import('@/views/execution/time-entry/index.vue'),
        meta: { title: '工时管理', icon: 'Clock', keepAlive: true },
      },
      {
        path: 'purchase',
        name: 'ExecutionPurchase',
        component: () => import('@/views/execution/purchase/index.vue'),
        meta: { title: '采购管理', icon: 'ShoppingCart', keepAlive: true },
      },
      {
        path: 'expense',
        name: 'ExecutionExpense',
        component: () => import('@/views/execution/expense/index.vue'),
        meta: { title: '费用管理', icon: 'Wallet', keepAlive: true },
      },
      {
        path: 'risk',
        name: 'ExecutionRisk',
        component: () => import('@/views/execution/risk/index.vue'),
        meta: { title: '风险管理', icon: 'WarningFilled', keepAlive: true },
      },
      {
        path: 'profit',
        name: 'ExecutionProfit',
        component: () => import('@/views/execution/profit/index.vue'),
        meta: { title: '收入/利润', icon: 'TrendCharts', keepAlive: true },
      },
      {
        path: 'delivery',
        name: 'ExecutionDelivery',
        component: () => import('@/views/execution/delivery/index.vue'),
        meta: { title: '交付物', icon: 'Box', keepAlive: true },
      },
      {
        path: 'closure',
        name: 'ExecutionClosure',
        component: () => import('@/views/execution/closure/index.vue'),
        meta: { title: '项目结项', icon: 'CircleCheck', keepAlive: true },
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
        meta: { title: '发票管理', icon: 'Tickets', keepAlive: true },
      },
      {
        path: 'payment',
        name: 'FinancePayment',
        component: () => import('@/views/execution/payment/index.vue'),
        meta: { title: '回款管理', icon: 'CreditCard', keepAlive: true },
      },
      {
        path: 'customer-credit',
        name: 'FinanceCustomerCredit',
        component: () => import('@/views/execution/customer-credit/index.vue'),
        meta: { title: '客户信用', icon: 'Medal', keepAlive: true },
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
        meta: { title: '职级费率', icon: 'DataLine', keepAlive: true },
      },
      {
        path: 'pool',
        name: 'ResourcePool',
        component: () => import('@/views/resource/pool/index.vue'),
        meta: { title: '资源池', icon: 'Files', keepAlive: true },
      },
      {
        path: 'employee-tag',
        name: 'ResourceEmployeeTag',
        component: () => import('@/views/resource/employee-tag/index.vue'),
        meta: { title: '人员标签', icon: 'CollectionTag', keepAlive: true },
      },
      {
        path: 'assignment',
        name: 'ResourceAssignment',
        component: () => import('@/views/resource/assignment/index.vue'),
        meta: { title: '资源分配', icon: 'Connection', keepAlive: true },
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
        meta: { title: '考勤中心', icon: 'Calendar', keepAlive: true },
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
        meta: { title: '报表', icon: 'Document', keepAlive: true },
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
        meta: { title: '审计日志', icon: 'Document', keepAlive: true },
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
 * 由后端返回菜单树后动态注册
 */
export const asyncRoutes: RouteRecordRaw[] = []
