/**
 * Qiankun 全局状态通信
 *
 * 主应用通过 initGlobalState 创建全局状态通信通道，
 * 子应用通过 qiankunWindow.__POWERED_BY_QIANKUN__ 中的 props.onGlobalStateChange 接收。
 *
 * 对标飞书微前端方案 + Qiankun 官方推荐模式。
 */
import { initGlobalState } from 'qiankun';

/** 全局状态类型 */
export interface GlobalState {
  /** 当前用户信息 */
  user?: {
    userId?: string;
    username?: string;
    realName?: string;
    avatar?: string;
    roles?: string[];
  };
  /** 主题模式 */
  theme?: 'auto' | 'dark' | 'light';
  /** 语言 */
  locale?: 'en-US' | 'zh-CN';
  /** 未读通知数量 */
  notificationCount?: number;
  /** 当前租户 */
  tenantId?: string;
  /**
   * 注意：token 等敏感凭据不再通过 globalState 明文传递，
   * 子应用应通过 shared-auth 的 Proxy 延迟初始化机制直接从 SecureLS 读取。
   */
}

/** 全局状态初始值 */
const initialState: GlobalState = {
  locale: 'zh-CN',
  notificationCount: 0,
  theme: 'auto',
};

/** Qiankun globalState 实例 */
let actions: null | ReturnType<typeof initGlobalState> = null;

/**
 * 初始化全局状态通信
 */
export function initGlobalStateCommunication() {
  actions = initGlobalState(initialState);

  // 监听子应用触发的状态变化
  actions.onGlobalStateChange((state, prev) => {
    console.info('[GlobalState] Received from sub-app:', state, prev);
  }, true);

  console.info('[GlobalState] Communication channel initialized');
  return actions;
}

/**
 * 更新全局状态
 *
 * 主应用和子应用都可调用（子应用通过 qiankunWindow props.setGlobalState）
 */
export function setGlobalState(state: Partial<GlobalState>) {
  if (actions) {
    actions.setGlobalState(state);
  }
}

/**
 * 获取全局状态通信实例
 */
export function getGlobalStateActions() {
  return actions;
}

/**
 * 将全局状态通信方法注入子应用 props
 *
 * 在 registerMicroApps 的 props 中传递，子应用可通过 mount(props) 接收
 */
export function getSubAppProps(appName: string) {
  return {
    // 子应用可通过 onGlobalStateChange 监听全局状态
    onGlobalStateChange: actions?.onGlobalStateChange,
    // 子应用可通过 setGlobalState 更新全局状态
    setGlobalState: actions?.setGlobalState,
    // 主应用注入的全局状态（只读快照）
    globalState: initialState,
  };
}
