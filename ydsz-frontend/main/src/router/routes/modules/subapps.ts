import type { RouteRecordRaw } from 'vue-router';

const SubAppContainer = () => import('#/views/_core/subapp/index.vue');

const routes: RouteRecordRaw[] = [
  // Ant Design Vue 子应用路由
  {
    meta: {
      icon: 'ant-design:appstore-outlined',
      order: 100,
      title: 'ydsz-antd-web',
    },
    name: 'AntdApp',
    path: '/ydsz-antd-web',
    redirect: '/ydsz-antd-web/dashboard/analytics',
    children: [
      {
        name: 'AntdAnalytics',
        path: '/ydsz-antd-web/dashboard/analytics',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-antd-web/dashboard/analytics',
          icon: 'lucide:area-chart',
          title: '子应用分析页',
        },
      },
      {
        name: 'AntdWorkspace',
        path: '/ydsz-antd-web/dashboard/workspace',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-antd-web/dashboard/workspace',
          icon: 'carbon:workspace',
          title: '子应用工作台',
        },
      },
      {
        name: 'AntdSubAppCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-antd-web',
          title: 'Antd Web',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // Element Plus 子应用路由
  {
    meta: {
      icon: 'ant-design:appstore-outlined',
      order: 101,
      title: 'ydsz-elem-web',
    },
    name: 'ElemApp',
    path: '/ydsz-elem-web',
    redirect: '/ydsz-elem-web/dashboard/analytics',
    children: [
      {
        name: 'ElemAnalytics',
        path: '/ydsz-elem-web/dashboard/analytics',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-elem-web/dashboard/analytics',
          icon: 'lucide:area-chart',
          title: '子应用分析页',
        },
      },
      {
        name: 'ElemWorkspace',
        path: '/ydsz-elem-web/dashboard/workspace',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-elem-web/dashboard/workspace',
          icon: 'carbon:workspace',
          title: '子应用工作台',
        },
      },
      {
        name: 'ElemSubAppCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-elem-web',
          title: 'Elem Web',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // Naive UI 子应用路由
  {
    meta: {
      icon: 'ant-design:appstore-outlined',
      order: 102,
      title: 'ydsz-naive-web',
    },
    name: 'NaiveApp',
    path: '/ydsz-naive-web',
    redirect: '/ydsz-naive-web/dashboard/analytics',
    children: [
      {
        name: 'NaiveAnalytics',
        path: '/ydsz-naive-web/dashboard/analytics',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-naive-web/dashboard/analytics',
          icon: 'lucide:area-chart',
          title: '子应用分析页',
        },
      },
      {
        name: 'NaiveWorkspace',
        path: '/ydsz-naive-web/dashboard/workspace',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-naive-web/dashboard/workspace',
          icon: 'carbon:workspace',
          title: '子应用工作台',
        },
      },
      {
        name: 'NaiveSubAppCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-naive-web',
          title: 'Naive Web',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // Play 子应用路由
  {
    meta: {
      icon: 'ant-design:appstore-outlined',
      order: 103,
      title: 'ydsz-play-web',
    },
    name: 'PlayApp',
    path: '/ydsz-play-web',
    redirect: '/ydsz-play-web/dashboard/analytics',
    children: [
      {
        name: 'PlayAnalytics',
        path: '/ydsz-play-web/dashboard/analytics',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-play-web/dashboard/analytics',
          icon: 'lucide:area-chart',
          title: '子应用分析页',
        },
      },
      {
        name: 'PlayWorkspace',
        path: '/ydsz-play-web/dashboard/workspace',
        component: () => import('#/views/_core/subapp/index.vue'),
        meta: {
          activePath: '/ydsz-play-web/dashboard/workspace',
          icon: 'carbon:workspace',
          title: '子应用工作台',
        },
      },
      {
        name: 'PlaySubAppCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-play-web',
          title: 'Play Web',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
];

export default routes;
