/**
 * project 路由模块
 *
 * @path apps\project-web\src\router\routes\modules\project.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:briefcase', order: 1, title: '商机管理' },
    name: 'Opportunity',
    path: '/opportunity',
    children: [
      {
        name: 'OpportunityManagement',
        path: 'list',
        component: () => import('#/views/opportunity/index.vue'),
        meta: { icon: 'lucide:trending-up', title: '商机列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:file-text', order: 2, title: '合同管理' },
    name: 'Contract',
    path: '/contract',
    children: [
      {
        name: 'ContractManagement',
        path: 'list',
        component: () => import('#/views/contract/index.vue'),
        meta: { icon: 'lucide:file-signature', title: '合同列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:rocket', order: 3, title: '项目立项' },
    name: 'Initiation',
    path: '/initiation',
    children: [
      {
        name: 'InitiationManagement',
        path: 'list',
        component: () => import('#/views/initiation/index.vue'),
        meta: { icon: 'lucide:flag', title: '立项列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:check-square', order: 4, title: '执行管理' },
    name: 'Execution',
    path: '/execution',
    children: [
      {
        name: 'ExecutionManagement',
        path: 'wbs',
        component: () => import('#/views/execution/index.vue'),
        meta: { icon: 'lucide:list-checks', title: 'WBS任务' },
      },
      {
        name: 'RiskManagement',
        path: 'risk',
        component: () => import('#/views/risk/index.vue'),
        meta: { icon: 'lucide:alert-triangle', title: '风险管理' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:dollar-sign', order: 5, title: '财务管理' },
    name: 'Finance',
    path: '/finance',
    children: [
      {
        name: 'BudgetManagement',
        path: 'budget',
        component: () => import('#/views/budget/index.vue'),
        meta: { icon: 'lucide:wallet', title: '预算管理' },
      },
      {
        name: 'ExpenseManagement',
        path: 'expense',
        component: () => import('#/views/expense/index.vue'),
        meta: { icon: 'lucide:receipt', title: '费用管理' },
      },
      {
        name: 'RevenueManagement',
        path: 'revenue',
        component: () => import('#/views/revenue/index.vue'),
        meta: { icon: 'lucide:trending-up', title: '收入管理' },
      },
      {
        name: 'InvoiceManagement',
        path: 'invoice',
        component: () => import('#/views/invoice/index.vue'),
        meta: { icon: 'lucide:file-text', title: '发票管理' },
      },
      {
        name: 'PaymentManagement',
        path: 'payment',
        component: () => import('#/views/payment/index.vue'),
        meta: { icon: 'lucide:credit-card', title: '回款管理' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:bar-chart-3', order: 6, title: 'EVM分析' },
    name: 'Evm',
    path: '/evm',
    children: [
      {
        name: 'EvmManagement',
        path: 'measure',
        component: () => import('#/views/evm/index.vue'),
        meta: { icon: 'lucide:activity', title: '挣值测量' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:users', order: 7, title: '人力管理' },
    name: 'Hr',
    path: '/hr',
    children: [
      {
        name: 'RateCardManagement',
        path: 'rate-card',
        component: () => import('#/views/rateCard/index.vue'),
        meta: { icon: 'lucide:id-card', title: '费率卡' },
      },
    ],
  },
];

export default routes;
