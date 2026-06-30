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
