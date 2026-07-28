/**
 * message 路由模块
 *
 * @path apps\message-web\src\router\routes\modules\message.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:mail', order: 1, title: '消息中心' },
    name: 'MessageCenter',
    path: '/message',
    children: [
      {
        name: 'MessageManagement',
        path: 'list',
        component: () => import('#/views/message/index.vue'),
        meta: { icon: 'lucide:send', title: '消息列表' },
      },
      {
        name: 'BatchManagement',
        path: 'batch',
        component: () => import('#/views/batch/index.vue'),
        meta: { icon: 'lucide:layers', title: '批量发送' },
      },
      {
        name: 'DeadLetterManagement',
        path: 'dead-letter',
        component: () => import('#/views/deadLetter/index.vue'),
        meta: { icon: 'lucide:alert-octagon', title: '死信队列' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:file-edit', order: 2, title: '模板管理' },
    name: 'TemplateMgmt',
    path: '/template',
    children: [
      {
        name: 'TemplateManagement',
        path: 'list',
        component: () => import('#/views/template/index.vue'),
        meta: { icon: 'lucide:file-text', title: '消息模板' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:bell', order: 3, title: '通知管理' },
    name: 'NotificationMgmt',
    path: '/notification',
    children: [
      {
        name: 'NotificationManagement',
        path: 'list',
        component: () => import('#/views/notification/index.vue'),
        meta: { icon: 'lucide:bell', title: '站内通知' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:route', order: 4, title: '路由配置' },
    name: 'RouteConfig',
    path: '/route',
    children: [
      {
        name: 'RouteRuleManagement',
        path: 'rules',
        component: () => import('#/views/routeRule/index.vue'),
        meta: { icon: 'lucide:git-branch', title: '路由规则' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:settings', order: 5, title: '偏好设置' },
    name: 'PreferenceMgmt',
    path: '/preference',
    children: [
      {
        name: 'PreferenceManagement',
        path: 'list',
        component: () => import('#/views/preference/index.vue'),
        meta: { icon: 'lucide:sliders', title: '消息偏好' },
      },
    ],
  },
];

export default routes;
