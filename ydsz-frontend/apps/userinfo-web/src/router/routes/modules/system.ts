import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:users',
      order: 1,
      title: '组织管理',
    },
    name: 'Organization',
    path: '/organization',
    children: [
      {
        name: 'UserManagement',
        path: 'user',
        component: () => import('#/views/system/user/index.vue'),
        meta: {
          icon: 'lucide:user',
          title: '用户管理',
        },
      },
      {
        name: 'DeptManagement',
        path: 'dept',
        component: () => import('#/views/system/dept/index.vue'),
        meta: {
          icon: 'lucide:building-2',
          title: '部门管理',
        },
      },
      {
        name: 'RoleManagement',
        path: 'role',
        component: () => import('#/views/system/role/index.vue'),
        meta: {
          icon: 'lucide:shield-check',
          title: '角色管理',
        },
      },
      {
        name: 'PostManagement',
        path: 'post',
        component: () => import('#/views/system/post/index.vue'),
        meta: {
          icon: 'lucide:briefcase',
          title: '岗位管理',
        },
      },
      {
        name: 'CompanyManagement',
        path: 'company',
        component: () => import('#/views/system/company/index.vue'),
        meta: {
          icon: 'lucide:factory',
          title: '公司管理',
        },
      },
    ],
  },
  {
    meta: {
      icon: 'lucide:settings-2',
      order: 2,
      title: '系统配置',
    },
    name: 'SystemConfig',
    path: '/system-config',
    children: [
      {
        name: 'MenuManagement',
        path: 'menu',
        component: () => import('#/views/system/menu/index.vue'),
        meta: {
          icon: 'lucide:menu',
          title: '菜单管理',
        },
      },
      {
        name: 'LanguageManagement',
        path: 'language',
        component: () => import('#/views/system/language/index.vue'),
        meta: {
          icon: 'lucide:languages',
          title: '语言管理',
        },
      },
    ],
  },
];

export default routes;
