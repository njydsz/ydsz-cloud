import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:clock', order: 1, title: '任务管理' },
    name: 'JobMgmt',
    path: '/job',
    children: [
      {
        name: 'JobManagement',
        path: 'list',
        component: () => import('#/views/job/index.vue'),
        meta: { icon: 'lucide:timer', title: '任务列表' },
      },
      {
        name: 'JobGroupManagement',
        path: 'group',
        component: () => import('#/views/jobGroup/index.vue'),
        meta: { icon: 'lucide:folder', title: '任务分组' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:git-merge', order: 2, title: 'DAG管理' },
    name: 'DagMgmt',
    path: '/dag',
    children: [
      {
        name: 'JobDagManagement',
        path: 'list',
        component: () => import('#/views/jobDag/index.vue'),
        meta: { icon: 'lucide:workflow', title: 'DAG列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:scroll-text', order: 3, title: '日志监控' },
    name: 'LogMgmt',
    path: '/log',
    children: [
      {
        name: 'JobLogManagement',
        path: 'list',
        component: () => import('#/views/jobLog/index.vue'),
        meta: { icon: 'lucide:file-text', title: '执行日志' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:bell-ring', order: 4, title: '告警管理' },
    name: 'AlertMgmt',
    path: '/alert',
    children: [
      {
        name: 'AlertManagement',
        path: 'list',
        component: () => import('#/views/alert/index.vue'),
        meta: { icon: 'lucide:siren', title: '告警规则' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:plug', order: 5, title: '连接器' },
    name: 'ConnectorMgmt',
    path: '/connector',
    children: [
      {
        name: 'ConnectorManagement',
        path: 'list',
        component: () => import('#/views/connector/index.vue'),
        meta: { icon: 'lucide:cable', title: '连接器管理' },
      },
    ],
  },
];

export default routes;
