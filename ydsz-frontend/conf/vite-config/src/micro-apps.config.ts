/**
 * 微应用注册表 — 单一事实源。
 *
 * 所有应用清单信息集中在此定义，包括名称、端口、路由前缀、菜单、
 * 生产入口等，消除此前 qiankun/index.ts、subapps.ts、use-tabbar-micro-sync.ts
 * 三处硬编码需要手工同步的问题。
 *
 * 基座路由、微前端注册、tabbar 映射、vite 端口配置均从此消费。
 *
 * 新增子应用时仅需在此数组追加一条记录。
 *
 * @path conf/vite-config/src/micro-apps.config.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { RouteRecordRaw } from 'vue-router';

/** 单个微应用的完整注册信息 */
export interface MicroAppEntry {
  /** 子应用唯一标识（如 'project-web'），与 pnpm workspace 包名后缀一致 */
  name: string;
  /** Monorepo 内包名（如 @ydsz/project-web） */
  packageName: string;
  /** 路由前缀（如 '/ydsz-proj'），也作为 qiankun activeRule */
  activeRule: string;
  /** 菜单默认重定向路径（如 '/ydsz-proj/opportunities'） */
  redirect: string;
  /** 菜单标题 */
  title: string;
  /** 菜单图标（lucide 图标名） */
  icon: string;
  /** 菜单排序权重（越小越靠前） */
  order: number;
  /** 开发服务器端口 */
  devPort: number;
  /** 生产环境部署子路径（null 表示与 activeRule 不一致时手动指定） */
  prodPath?: string;
}

/**
 * 微应用注册表。
 *
 * 9 个微应用分别对应后端 9 个微服务，顺序与菜单显示一致。
 * 变更流程：修改此处 → 重启基座 dev server → 验证菜单与路由。
 */
export const MICRO_APPS: readonly MicroAppEntry[] = [
  {
    name: 'userinfo-web',
    packageName: '@ydsz/userinfo-web',
    activeRule: '/ydsz-user',
    redirect: '/ydsz-user/users',
    title: '用户中心',
    icon: 'lucide:users',
    order: 100,
    devPort: 5601,
  },
  {
    name: 'system-web',
    packageName: '@ydsz/system-web',
    activeRule: '/ydsz-sys',
    redirect: '/ydsz-sys/configs',
    title: '系统管理',
    icon: 'lucide:settings',
    order: 101,
    devPort: 5602,
  },
  {
    name: 'project-web',
    packageName: '@ydsz/project-web',
    activeRule: '/ydsz-proj',
    redirect: '/ydsz-proj/opportunities',
    title: '项目管理',
    icon: 'lucide:folder-kanban',
    order: 102,
    devPort: 5603,
  },
  {
    name: 'message-web',
    packageName: '@ydsz/message-web',
    activeRule: '/ydsz-msg',
    redirect: '/ydsz-msg/messages',
    title: '消息中心',
    icon: 'lucide:message-square',
    order: 103,
    devPort: 5604,
  },
  {
    name: 'cronjob-web',
    packageName: '@ydsz/cronjob-web',
    activeRule: '/ydsz-cron',
    redirect: '/ydsz-cron/jobs',
    title: '定时任务',
    icon: 'lucide:clock',
    order: 104,
    devPort: 5605,
  },
  {
    name: 'workflow-web',
    packageName: '@ydsz/workflow-web',
    activeRule: '/ydsz-flow',
    redirect: '/ydsz-flow/templates',
    title: '工作流引擎',
    icon: 'lucide:workflow',
    order: 105,
    devPort: 5606,
  },
  {
    name: 'nextwiki-web',
    packageName: '@ydsz/nextwiki-web',
    activeRule: '/ydsz-wiki',
    redirect: '/ydsz-wiki/files',
    title: '网盘知识库',
    icon: 'lucide:folder-open',
    order: 106,
    devPort: 5607,
  },
  {
    name: 'literule-web',
    packageName: '@ydsz/literule-web',
    activeRule: '/ydsz-rule',
    redirect: '/ydsz-rule/rules',
    title: '规则引擎',
    icon: 'lucide:git-branch',
    order: 107,
    devPort: 5608,
  },
  {
    name: 'agent-web',
    packageName: '@ydsz/agent-web',
    activeRule: '/ydsz-ai',
    redirect: '/ydsz-ai/chat',
    title: 'AI 助手',
    icon: 'lucide:bot',
    order: 108,
    devPort: 5610,
  },
];

/** 路由前缀 → 子应用名 映射（供 use-tabbar-micro-sync 等场景快速查找） */
export const PATH_TO_APP_MAP: Readonly<Record<string, string>> = Object.freeze(
  Object.fromEntries(MICRO_APPS.map((app) => [app.activeRule, app.name])),
);

/** 子应用名 → 注册信息 映射 */
export const APP_BY_NAME: Readonly<Record<string, MicroAppEntry>> = Object.freeze(
  Object.fromEntries(MICRO_APPS.map((app) => [app.name, app])),
);

/**
 * 根据注册表生成基座子应用路由。
 *
 * 每条路由包含一个 catch-all 子路由（`:path(.*)*`），
 * 用于渲染微前端容器组件，被子应用内部路由接管。
 */
export function generateSubAppRoutes(): RouteRecordRaw[] {
  return MICRO_APPS.map((app) => ({
    meta: {
      icon: app.icon,
      order: app.order,
      title: app.title,
    },
    name: `${app.name.replace('-web', '')}App`,
    path: app.activeRule,
    redirect: app.redirect,
    children: [
      {
        name: `${app.name.replace(/-/g, '')}Catch`,
        path: ':path(.*)*',
        // 组件引用留空——调用方需注入实际的 SubAppContainer 组件解析函数
        component: undefined as unknown,
        meta: {
          activePath: app.activeRule,
          title: app.title,
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  })) as RouteRecordRaw[];
}
