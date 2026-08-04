/**
 * 主子应用标准化 Props 下发契约（v4.0 P1-2）
 *
 * 统一子应用 mountProps 的类型定义与构造过程，
 * 消除 kernel 侧"手工拼装 props"与子应用侧"手工解构 props"的重复样板。
 *
 * 设计原则：
 * - 单一事实源：StandardMicroProps 是主子应用之间的唯一契约接口
 * - 类型安全：子应用侧通过 useMicroProps<T>() 泛型推导获得类型化访问
 * - 向后兼容：mountProps 保留 [key: string]: unknown 索引签名，旧代码无需强制迁移
 *
 * 使用方式：
 * - 主应用/kernel 侧：调用 buildStandardMountProps(config, ctx) 构造标准化 props
 * - 子应用侧：调用 useMicroProps() 获取类型化 props 访问器
 *
 * @path comm/effects/micro-runtime/src/standard-props.ts
 * @author ydsz-team
 * @since 4.0.0
 */

import type { MountProps, MicroAppConfig, SandboxType } from './types';
import type { RawGlobalStateAPI } from './global-state';
import type { NamespacedGlobalStateAPI } from './namespaced-state';

// ==================== 全局状态句柄（注入子应用） ====================

/** 增强型全局状态 API（含命名空间能力） */
export interface EnhancedGlobalStateAPI extends RawGlobalStateAPI {
  /** 获取指定命名空间的隔离 API */
  useNamespace(scope: string): NamespacedGlobalStateAPI;
}

// ==================== 点对点消息通信 ====================

/** 点对点消息结构 */
export interface MicroMessage<T = unknown> {
  from: string;
  to: string;
  action: string;
  payload: T;
  correlationId: string;
  isResponse?: boolean;
}

/** 消息处理器类型 */
export type MessageHandler<T = unknown, R = unknown> = (
  msg: { action: string; payload: T; from: string },
) => R | Promise<R>;

/** 消息总线 API（注入子应用） */
export interface MessageBusAPI {
  /** 发送消息（fire-and-forget） */
  sendMessage: (action: string, payload?: unknown) => string;
  /** 发送请求并 await 响应 */
  sendRequest: <R = unknown>(action: string, payload?: unknown, timeout?: number) => Promise<R>;
  /** 注册消息处理器 */
  registerHandler: <T = unknown, R = unknown>(handler: MessageHandler<T, R>) => () => void;
}

// ==================== 子应用上下文 ====================

/** 子应用运行时上下文 */
export interface SubAppContext {
  /** 应用名 */
  appName: string;
  /** 路由 basename */
  basename: string;
  /** 沙箱类型 */
  sandbox: SandboxType;
  /** 主应用当前主题 */
  theme?: 'light' | 'dark' | 'auto';
  /** 主应用当前语言 */
  locale?: string;
  /** 当前登录用户 ID（可选，用于子应用免请求获取） */
  userId?: string;
}

// ==================== 标准化 Mount Props ====================

/**
 * 标准化的子应用挂载参数
 *
 * 主应用通过 buildStandardMountProps() 构造此对象并传给子应用 mount()。
 * 子应用通过 useMicroProps() 类型化访问。
 */
export interface StandardMicroProps extends MountProps {
  // --- 基础信息 ---
  /** 应用唯一标识 */
  appName: string;
  /** 路由 basename（与 MountProps.basename 同义，增强可读性） */
  basename: string;
  /** 挂载容器 DOM 元素 */
  container: HTMLElement;

  // --- 沙箱 ---
  /** 沙箱类型 */
  sandbox: SandboxType;
  /** Proxy 沙箱的 fakeWindow（仅 proxy 沙箱） */
  fakeWindow?: Record<string, unknown>;
  /** iframe 沙箱的 contentWindow（仅 iframe 沙箱） */
  iframeWindow?: Window;

  // --- 跨应用通信 ---
  /** 全局状态 API（含命名空间能力） */
  globalState: EnhancedGlobalStateAPI;
  /** 点对点消息总线 */
  messageBus: MessageBusAPI;

  // --- 主应用上下文 ---
  /** 子应用运行时上下文 */
  context: SubAppContext;

  // --- 扩展索引（向后兼容） ---
  [key: string]: unknown;
}

// ==================== Props 构造器 ====================

/**
 * 构造函数的上下文参数
 */
export interface BuildPropsContext {
  /** 全局状态原始 API */
  rawGlobalState: RawGlobalStateAPI;
  /** 消息发送函数 */
  sendMessage: (action: string, payload?: unknown) => string;
  /** 消息请求函数 */
  sendRequest: <R = unknown>(action: string, payload?: unknown, timeout?: number) => Promise<R>;
  /** 消息处理器注册函数 */
  registerHandler: <T = unknown, R = unknown>(handler: MessageHandler<T, R>) => () => void;
  /** 当前主题 */
  theme?: 'light' | 'dark' | 'auto';
  /** 当前语言 */
  locale?: string;
  /** 当前用户 ID */
  userId?: string;
}

/**
 * 构造标准化的子应用挂载参数
 *
 * 在 kernel 侧的 switchToApp 中调用，替代手工拼装 props 的方式。
 *
 * @param config - 子应用注册配置
 * @param ctx - 构造上下文
 * @returns 标准化 mount props
 */
export function buildStandardMountProps(
  config: MicroAppConfig,
  ctx: BuildPropsContext,
): StandardMicroProps {
  return {
    // 基础信息
    appName: config.name,
    basename: typeof config.activeRule === 'string' ? config.activeRule : `/${config.name}`,
    container: resolveContainer(config.container),
    sandbox: config.sandbox ?? 'snapshot',

    // 跨应用通信
    globalState: {
      onGlobalStateChange: ctx.rawGlobalState.onGlobalStateChange,
      setGlobalState: ctx.rawGlobalState.setGlobalState,
      getGlobalState: ctx.rawGlobalState.getGlobalState,
      // 命名空间能力由 createNamespacedGlobalStateWrapper 在 kernel 侧注入
      useNamespace: (scope: string) => {
        // 延迟导入避免循环依赖
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const { createNamespacedState } = require('./namespaced-state');
        return createNamespacedState(ctx.rawGlobalState, scope);
      },
    },
    messageBus: {
      sendMessage: ctx.sendMessage,
      sendRequest: ctx.sendRequest,
      registerHandler: ctx.registerHandler,
    },

    // 上下文
    context: {
      appName: config.name,
      basename: typeof config.activeRule === 'string' ? config.activeRule : `/${config.name}`,
      sandbox: config.sandbox ?? 'snapshot',
      theme: ctx.theme,
      locale: ctx.locale,
      userId: ctx.userId,
    },

    // 合并 config.props 中的自定义字段（向后兼容）
    ...config.props,
  };
}

/**
 * 解析容器配置为 HTMLElement
 */
function resolveContainer(container: string | HTMLElement): HTMLElement {
  if (typeof container === 'string') {
    const el = document.querySelector(container);
    if (!el) {
      throw new Error(`[StandardProps] Container "${container}" not found`);
    }
    return el as HTMLElement;
  }
  return container;
}
