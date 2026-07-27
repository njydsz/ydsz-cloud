/**
 * PMIS 子应用配置列表
 * 每个后端微服务对应一个前端微应用
 */
const isDev = import.meta.env.DEV;

// 开发环境子应用地址
const devUrls = {
  userinfo: '//localhost:5601',
  system: '//localhost:5602',
  project: '//localhost:5603',
  message: '//localhost:5604',
  cronjob: '//localhost:5605',
  workflow: '//localhost:5606',
  nextwiki: '//localhost:5607',
  literule: '//localhost:5608',
  agent: '//localhost:5610',
};

// 生产环境子应用地址（根据实际部署情况修改）
const prodUrls = {
  userinfo: '/ydsz-userinfo-web/',
  system: '/ydsz-system-web/',
  project: '/ydsz-project-web/',
  message: '/ydsz-message-web/',
  cronjob: '/ydsz-cronjob-web/',
  workflow: '/ydsz-workflow-web/',
  nextwiki: '/ydsz-nextwiki-web/',
  literule: '/ydsz-literule-web/',
  agent: '/ydsz-agent-web/',
};

const urls = isDev ? devUrls : prodUrls;

/**
 * qiankun 子应用配置
 * name: 子应用唯一标识（与子应用 vite-plugin-qiankun 注册名一致）
 * entry: 子应用入口地址
 * container: 挂载容器
 * activeRule: 路由激活规则（路径前缀匹配）
 */
export const microApps = [
  {
    name: 'userinfo-web',
    entry: urls.userinfo,
    container: '#subapp-container',
    activeRule: '/ydsz-user',
  },
  {
    name: 'system-web',
    entry: urls.system,
    container: '#subapp-container',
    activeRule: '/ydsz-sys',
  },
  {
    name: 'project-web',
    entry: urls.project,
    container: '#subapp-container',
    activeRule: '/ydsz-proj',
  },
  {
    name: 'message-web',
    entry: urls.message,
    container: '#subapp-container',
    activeRule: '/ydsz-msg',
  },
  {
    name: 'cronjob-web',
    entry: urls.cronjob,
    container: '#subapp-container',
    activeRule: '/ydsz-cron',
  },
  {
    name: 'workflow-web',
    entry: urls.workflow,
    container: '#subapp-container',
    activeRule: '/ydsz-flow',
  },
  {
    name: 'nextwiki-web',
    entry: urls.nextwiki,
    container: '#subapp-container',
    activeRule: '/ydsz-wiki',
  },
  {
    name: 'literule-web',
    entry: urls.literule,
    container: '#subapp-container',
    activeRule: '/ydsz-rule',
  },
  {
    name: 'agent-web',
    entry: urls.agent,
    container: '#subapp-container',
    activeRule: '/ydsz-ai',
  },
];
