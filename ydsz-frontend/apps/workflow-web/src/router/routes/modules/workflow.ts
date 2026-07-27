import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:layout-template', order: 1, title: '流程模板' },
    name: 'TemplateMgmt',
    path: '/template',
    children: [
      {
        name: 'TemplateManagement',
        path: 'list',
        component: () => import('#/views/template/index.vue'),
        meta: { icon: 'lucide:file-stack', title: '模板列表' },
      },
      {
        name: 'CategoryManagement',
        path: 'category',
        component: () => import('#/views/category/index.vue'),
        meta: { icon: 'lucide:folder-tree', title: '流程分类' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:play-circle', order: 2, title: '流程运行' },
    name: 'FlowRun',
    path: '/flow',
    children: [
      {
        name: 'InstanceManagement',
        path: 'instance',
        component: () => import('#/views/instance/index.vue'),
        meta: { icon: 'lucide:activity', title: '流程实例' },
      },
      {
        name: 'TaskManagement',
        path: 'task',
        component: () => import('#/views/task/index.vue'),
        meta: { icon: 'lucide:check-square', title: '待办任务' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:user-check', order: 3, title: '审批配置' },
    name: 'ApprovalConfig',
    path: '/approval',
    children: [
      {
        name: 'DelegateManagement',
        path: 'delegate',
        component: () => import('#/views/delegate/index.vue'),
        meta: { icon: 'lucide:user-plus', title: '委派管理' },
      },
      {
        name: 'QuickCommentManagement',
        path: 'quick-comment',
        component: () => import('#/views/quickComment/index.vue'),
        meta: { icon: 'lucide:message-square', title: '快捷评语' },
      },
    ],
  },
];

export default routes;
