/**
 * PMIS 子应用配置列表
 * 每个后端微服务对应一个前端微应用
 */
const isDev = import.meta.env.DEV;

// 开发环境子应用地址
const devUrls = {
  userCenter: '//localhost:5601',
  systemAdmin: '//localhost:5602',
  projectMgmt: '//localhost:5603',
  messageCenter: '//localhost:5604',
  cronjobAdmin: '//localhost:5605',
  workflowDesigner: '//localhost:5606',
  wikiDrive: '//localhost:5607',
  ruleEngine: '//localhost:5608',
  aiAssistant: '//localhost:5610',
};

// 生产环境子应用地址（根据实际部署情况修改）
const prodUrls = {
  userCenter: '/ydsz-user-center-web/',
  systemAdmin: '/ydsz-system-admin-web/',
  projectMgmt: '/ydsz-project-mgmt-web/',
  messageCenter: '/ydsz-message-center-web/',
  cronjobAdmin: '/ydsz-cronjob-admin-web/',
  workflowDesigner: '/ydsz-workflow-designer-web/',
  wikiDrive: '/ydsz-wiki-drive-web/',
  ruleEngine: '/ydsz-rule-engine-web/',
  aiAssistant: '/ydsz-ai-assistant-web/',
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
    name: 'user-center-web',
    entry: urls.userCenter,
    container: '#subapp-container',
    activeRule: '/ydsz-user',
  },
  {
    name: 'system-admin-web',
    entry: urls.systemAdmin,
    container: '#subapp-container',
    activeRule: '/ydsz-sys',
  },
  {
    name: 'project-mgmt-web',
    entry: urls.projectMgmt,
    container: '#subapp-container',
    activeRule: '/ydsz-proj',
  },
  {
    name: 'message-center-web',
    entry: urls.messageCenter,
    container: '#subapp-container',
    activeRule: '/ydsz-msg',
  },
  {
    name: 'cronjob-admin-web',
    entry: urls.cronjobAdmin,
    container: '#subapp-container',
    activeRule: '/ydsz-cron',
  },
  {
    name: 'workflow-designer-web',
    entry: urls.workflowDesigner,
    container: '#subapp-container',
    activeRule: '/ydsz-flow',
  },
  {
    name: 'wiki-drive-web',
    entry: urls.wikiDrive,
    container: '#subapp-container',
    activeRule: '/ydsz-wiki',
  },
  {
    name: 'rule-engine-web',
    entry: urls.ruleEngine,
    container: '#subapp-container',
    activeRule: '/ydsz-rule',
  },
  {
    name: 'ai-assistant-web',
    entry: urls.aiAssistant,
    container: '#subapp-container',
    activeRule: '/ydsz-ai',
  },
];
