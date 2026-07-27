import type { RouteRecordRaw } from 'vue-router';

const SubAppContainer = () => import('#/views/_core/subapp/index.vue');

/**
 * PMIS 子应用路由配置
 * 每个微应用通过路径前缀匹配激活，catch-all 路由确保子应用内部路由正常工作
 */
const routes: RouteRecordRaw[] = [
  // 用户中心 → ydsz-userinfo:9001
  {
    meta: {
      icon: 'lucide:users',
      order: 100,
      title: '用户中心',
    },
    name: 'UserCenterApp',
    path: '/ydsz-user',
    redirect: '/ydsz-user/users',
    children: [
      {
        name: 'UserCenterCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-user',
          title: '用户中心',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 系统管理 → ydsz-system:9002
  {
    meta: {
      icon: 'lucide:settings',
      order: 101,
      title: '系统管理',
    },
    name: 'SystemAdminApp',
    path: '/ydsz-sys',
    redirect: '/ydsz-sys/configs',
    children: [
      {
        name: 'SystemAdminCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-sys',
          title: '系统管理',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 项目管理 → ydsz-project:9003
  {
    meta: {
      icon: 'lucide:folder-kanban',
      order: 102,
      title: '项目管理',
    },
    name: 'ProjectMgmtApp',
    path: '/ydsz-proj',
    redirect: '/ydsz-proj/opportunities',
    children: [
      {
        name: 'ProjectMgmtCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-proj',
          title: '项目管理',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 消息中心 → ydsz-message:9004
  {
    meta: {
      icon: 'lucide:message-square',
      order: 103,
      title: '消息中心',
    },
    name: 'MessageCenterApp',
    path: '/ydsz-msg',
    redirect: '/ydsz-msg/messages',
    children: [
      {
        name: 'MessageCenterCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-msg',
          title: '消息中心',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 定时任务 → ydsz-cronjob:9005
  {
    meta: {
      icon: 'lucide:clock',
      order: 104,
      title: '定时任务',
    },
    name: 'CronjobAdminApp',
    path: '/ydsz-cron',
    redirect: '/ydsz-cron/jobs',
    children: [
      {
        name: 'CronjobAdminCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-cron',
          title: '定时任务',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 工作流引擎 → ydsz-workflow:9006
  {
    meta: {
      icon: 'lucide:workflow',
      order: 105,
      title: '工作流引擎',
    },
    name: 'WorkflowDesignerApp',
    path: '/ydsz-flow',
    redirect: '/ydsz-flow/templates',
    children: [
      {
        name: 'WorkflowDesignerCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-flow',
          title: '工作流引擎',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 网盘知识库 → ydsz-nextwiki:9007
  {
    meta: {
      icon: 'lucide:folder-open',
      order: 106,
      title: '网盘知识库',
    },
    name: 'WikiDriveApp',
    path: '/ydsz-wiki',
    redirect: '/ydsz-wiki/files',
    children: [
      {
        name: 'WikiDriveCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-wiki',
          title: '网盘知识库',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // 规则引擎 → ydsz-literule:9008
  {
    meta: {
      icon: 'lucide:git-branch',
      order: 107,
      title: '规则引擎',
    },
    name: 'RuleEngineApp',
    path: '/ydsz-rule',
    redirect: '/ydsz-rule/rules',
    children: [
      {
        name: 'RuleEngineCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-rule',
          title: '规则引擎',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
  // AI 助手 → ydsz-agent:9010
  {
    meta: {
      icon: 'lucide:bot',
      order: 108,
      title: 'AI 助手',
    },
    name: 'AiAssistantApp',
    path: '/ydsz-ai',
    redirect: '/ydsz-ai/chat',
    children: [
      {
        name: 'AiAssistantCatch',
        path: ':path(.*)*',
        component: SubAppContainer,
        meta: {
          activePath: '/ydsz-ai',
          title: 'AI 助手',
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
];

export default routes;
