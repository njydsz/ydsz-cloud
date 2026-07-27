import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 1,
      title: '系统管理',
    },
    name: 'System',
    path: '/system',
    children: [
      {
        name: 'ConfigManagement',
        path: 'config',
        component: () => import('#/views/config/index.vue'),
        meta: { icon: 'lucide:sliders-horizontal', title: '系统配置' },
      },
      {
        name: 'DictTypeManagement',
        path: 'dict-type',
        component: () => import('#/views/dictType/index.vue'),
        meta: { icon: 'lucide:book-open', title: '字典类型' },
      },
      {
        name: 'DictItemManagement',
        path: 'dict-item',
        component: () => import('#/views/dictItem/index.vue'),
        meta: { icon: 'lucide:list', title: '字典项' },
      },
      {
        name: 'VariableManagement',
        path: 'variable',
        component: () => import('#/views/variable/index.vue'),
        meta: { icon: 'lucide:variable', title: '系统变量' },
      },
      {
        name: 'AppManagement',
        path: 'app',
        component: () => import('#/views/app/index.vue'),
        meta: { icon: 'lucide:app-window', title: '应用注册' },
      },
    ],
  },
];

export default routes;
