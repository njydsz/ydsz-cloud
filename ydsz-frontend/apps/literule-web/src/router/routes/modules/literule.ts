import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:git-branch', order: 1, title: '规则管理' },
    name: 'RuleMgmt',
    path: '/rule',
    children: [
      {
        name: 'RuleManagement',
        path: 'list',
        component: () => import('#/views/rule/index.vue'),
        meta: { icon: 'lucide:git-branch', title: '规则列表' },
      },
      {
        name: 'DslManagement',
        path: 'dsl',
        component: () => import('#/views/dsl/index.vue'),
        meta: { icon: 'lucide:code', title: 'DSL管理' },
      },
      {
        name: 'VariableManagement',
        path: 'variable',
        component: () => import('#/views/variable/index.vue'),
        meta: { icon: 'lucide:variable', title: '规则变量' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:zap', order: 2, title: '高级功能' },
    name: 'Advanced',
    path: '/advanced',
    children: [
      {
        name: 'CepManagement',
        path: 'cep',
        component: () => import('#/views/cep/index.vue'),
        meta: { icon: 'lucide:zap', title: 'CEP复杂事件' },
      },
      {
        name: 'BreakpointManagement',
        path: 'breakpoint',
        component: () => import('#/views/breakpoint/index.vue'),
        meta: { icon: 'lucide:bug', title: '断点调试' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:history', order: 3, title: '审计监控' },
    name: 'AuditMgmt',
    path: '/audit',
    children: [
      {
        name: 'AuditLogManagement',
        path: 'log',
        component: () => import('#/views/auditLog/index.vue'),
        meta: { icon: 'lucide:file-text', title: '审计日志' },
      },
    ],
  },
];

export default routes;
