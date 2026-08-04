/**
 * iframe 沙箱 — 基于 iframe contentWindow 的强隔离兜底方案
 *
 * **设计定位**：
 * 快照沙箱（防意外污染）和 Proxy 沙箱（fakeWindow 数据隔离）均运行在主窗口
 * 同一 realm，无法隔离 CSS 与 DOM 全局选择器。iframe 沙箱通过创建独立的
 * 浏览上下文（browsing context）提供 **CSS + DOM + window** 三重隔离，
 * 作为强隔离需求的兜底方案。
 *
 * **ESM 边界说明**：
 * 与 Proxy 沙箱相同，子应用通过 ESM `dynamic import()` 加载，模块代码在
 * 主 realm 执行而非 iframe 内。因此 iframe 沙箱在本项目中提供：
 * - **CSS 隔离**：将子应用挂载容器移入 iframe document，样式选择器天然隔离
 * - **DOM 隔离**：iframe 有独立 document，querySelector 等不跨域
 * - **fakeWindow**：iframe 的 contentWindow 可作为 mountProps 注入的隔离 window
 *
 * **跨 realm 通信（v3.6.0 新增）**：
 * 由于 iframe 有独立 realm，主应用直接传给子应用的 `_globalState` 对象
 * 在跨 realm 调用时可能引发异常（如 instanceof 失效、原型链不一致）。
 * 本沙箱内置 postMessage 桥接协议：
 * - 主侧通过 `postToChild(payload)` 发送 globalState 快照
 * - 子侧通过监听 `message` 事件接收，并回传 `setGlobalState` 调用
 * - 协议消息含 `__MICRO_KERNEL_BRIDGE__: true` 标记 + `type` + `payload`
 * - 主侧维护 childMessageHandler，由 kernel 注入 globalState 同步逻辑
 *
 * **适用场景**：
 * - 子应用使用全局 CSS 选择器（如 `body { ... }`）可能与主应用冲突时
 * - 需要完全独立的 document 环境的第三方子应用
 * - snapshot/proxy 沙箱隔离不足时的兜底降级
 *
 * **限制**：
 * - iframe 创建有额外开销（首次约 10-30ms）
 * - 弹窗/抽屉等 fixed 定位元素会被限制在 iframe 视口内
 *
 * **对标实现**：
 * - wujie（iframe + webcomponent 方案，本项目精简为纯 iframe 容器）
 * - micro-app（webcomponent + iframe scope，本项目仅取 iframe window 隔离）
 *
 * @path comm/effects/micro-kernel/src/iframe-sandbox.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** iframe 沙箱实例 */
export interface IframeSandboxInstance {
  /** iframe 的 contentWindow（子应用可用的隔离 window） */
  contentWindow: Window | null;
  /** iframe 的 contentDocument（子应用挂载用的隔离 document） */
  contentDocument: Document | null;
  /** 挂载容器元素（位于 iframe document 内） */
  container: HTMLElement | null;
  /** 激活沙箱 */
  activate: () => void;
  /** 停用沙箱 */
  deactivate: () => void;
  /** 清理沙箱（移除 iframe，释放资源） */
  cleanup: () => void;
  /**
   * 向子应用发送消息（主 → 子）。
   *
   * @since 3.6.0
   * @param payload - 任意可结构化克隆的消息体
   */
  postToChild: (payload: unknown) => void;
  /**
   * 注册子应用消息处理器（子 → 主）。
   *
   * @since 3.6.0
   * @param handler - 收到子应用消息时的回调
   * @returns 取消注册函数
   */
  onChildMessage: (handler: (payload: unknown) => void) => () => void;
  /**
   * 主应用调用子应用暴露的方法（RPC request-response 模式）。
   *
   * @since 3.6.1
   * @param method - 子应用通过 __MICRO_REGISTER_API__ 注册的方法名
   * @param args - 调用参数
   * @returns 子应用返回值（支持 Promise）
   */
  callRpc: (method: string, args?: unknown[]) => Promise<unknown>;
  /**
   * 注册主应用 API，供子应用通过 __MICRO_CALL_MAIN__ 调用。
   *
   * @since 3.6.1
   * @param handlers - method → 处理函数映射
   * @returns 取消注册函数
   */
  registerMainApi: (
    handlers: Record<string, (...args: any[]) => unknown>,
  ) => () => void;
}

/** iframe 默认样式：撑满容器、无边框 */
const IFRAME_STYLE =
  'width:100%;height:100%;border:0;display:block;margin:0;padding:0;';

/**
 * postMessage 桥接协议标记。
 *
 * 所有由本沙箱桥接发送的消息都带此标记，子侧/主侧据此区分微内核桥接消息
 * 与业务侧自己的 postMessage 通信，避免互相干扰。
 *
 * @since 3.6.0
 */
const BRIDGE_MARK = '__MICRO_KERNEL_BRIDGE__';

/**
 * 桥接消息类型。
 *
 * - `state-sync`：主 → 子，globalState 快照同步
 * - `state-set`：子 → 主，子应用调用 setGlobalState
 * - `rpc-call`：主 → 子，RPC 调用（request-response 模式）
 * - `rpc-result`：子 → 主，RPC 调用结果
 *
 * @since 3.6.0
 */
type BridgeMessageType =
  | 'state-set'
  | 'state-sync'
  | 'rpc-call'
  | 'rpc-result';

/** RPC 调用消息体 */
interface RpcCallPayload {
  /** 方法名（子应用在 iframe 中暴露） */
  method: string;
  /** 调用参数 */
  args: unknown[];
  /** 调用 ID，用于匹配响应 */
  callId: string;
}

/** RPC 结果消息体 */
interface RpcResultPayload {
  /** 调用 ID（与请求一致） */
  callId: string;
  /** 是否成功 */
  ok: boolean;
  /** 返回值（ok=true 时） */
  result?: unknown;
  /** 错误信息（ok=false 时） */
  error?: string;
}

/** 桥接消息结构 */
interface BridgeMessage<T = unknown> {
  [BRIDGE_MARK]: true;
  type: BridgeMessageType;
  payload: T;
}

/**
 * 判断消息是否为本沙箱桥接协议消息。
 *
 * @since 3.6.0
 */
function isBridgeMessage(data: unknown): data is BridgeMessage {
  return (
    typeof data === 'object' &&
    data !== null &&
    (data as Record<string, unknown>)[BRIDGE_MARK] === true
  );
}

/**
 * 在 iframe 内注入桥接监听器脚本。
 *
 * 注入的脚本在 iframe realm 中执行，监听 `message` 事件：
 * - 收到 `state-sync` 消息时，将 payload 写入 `window.__MICRO_GLOBAL_STATE__`
 * - 暴露 `window.__MICRO_SET_GLOBAL_STATE__(patch)` 供子应用调用，
 *   该方法通过 postMessage 回传 `state-set` 给主应用
 * - 暴露 `window.__MICRO_CALL_MAIN__(method, args)`：子 → 主 RPC 调用（Promise 化）
 * - 暴露 `window.__MICRO_REGISTER_API__(handlers)`：注册子应用 API，供主应用调用
 * - 监听 `rpc-call` 消息：主 → 子 RPC 调用，匹配已注册的 handler 并回传结果
 *
 * 子应用代码通过 `mountProps.iframeWindow.__MICRO_GLOBAL_STATE__` 读取同步过来的状态，
 * 通过 `mountProps.iframeWindow.__MICRO_SET_GLOBAL_STATE__({ key: value })` 回写，
 * 通过 `mountProps.iframeWindow.__MICRO_CALL_MAIN__('method', [args])` 调用主应用能力。
 *
 * @since 3.6.0
 * @param iframeWin - iframe 的 contentWindow
 */
function injectBridgeScript(iframeWin: Window): void {
  // 在 iframe document 中注入 <script>，确保代码在 iframe realm 执行
  const iframeDoc = iframeWin.document;
  const script = iframeDoc.createElement('script');
  script.textContent = `
    (function() {
      // 当前 globalState 快照（由主应用同步过来）
      window.__MICRO_GLOBAL_STATE__ = {};

      // 子应用调用此方法回写状态到主应用
      window.__MICRO_SET_GLOBAL_STATE__ = function(patch) {
        window.parent.postMessage({
          ${BRIDGE_MARK}: true,
          type: 'state-set',
          payload: patch
        }, '*');
      };

      // ===== RPC 协议 v3.6.1 增强 =====
      // 子应用注册给主应用调用的 API 处理器
      window.__MICRO_REGISTERED_API__ = {};
      window.__MICRO_REGISTER_API__ = function(handlers) {
        window.__MICRO_REGISTERED_API__ = handlers || {};
      };

      // 子应用 → 主应用 RPC 调用（返回 Promise）
      var __rpcSeq__ = 0;
      var __pendingCalls__ = {};
      window.__MICRO_CALL_MAIN__ = function(method, args) {
        return new Promise(function(resolve, reject) {
          var callId = 'm' + (++__rpcSeq__);
          __pendingCalls__[callId] = { resolve: resolve, reject: reject };
          window.parent.postMessage({
            ${BRIDGE_MARK}: true,
            type: 'rpc-call',
            payload: { method: method, args: args || [], callId: callId }
          }, '*');
        });
      };

      // 主 → 子 RPC 调用处理：执行后回传结果
      window.__MICRO_EXECUTE_RPC__ = function(payload) {
        var handler = window.__MICRO_REGISTERED_API__[payload.method];
        var result;
        var ok = true;
        var error;
        if (typeof handler !== 'function') {
          ok = false;
          error = 'RPC method not found: ' + payload.method;
        } else {
          try {
            result = handler.apply(null, payload.args || []);
          } catch (e) {
            ok = false;
            error = String(e && e.message || e);
          }
        }
        // 支持 Promise 返回值
        if (ok && result && typeof result.then === 'function') {
          result.then(function(value) {
            window.parent.postMessage({
              ${BRIDGE_MARK}: true,
              type: 'rpc-result',
              payload: { callId: payload.callId, ok: true, result: value }
            }, '*');
          }).catch(function(err) {
            window.parent.postMessage({
              ${BRIDGE_MARK}: true,
              type: 'rpc-result',
              payload: { callId: payload.callId, ok: false, error: String(err && err.message || err) }
            }, '*');
          });
          return;
        }
        window.parent.postMessage({
          ${BRIDGE_MARK}: true,
          type: 'rpc-result',
          payload: { callId: payload.callId, ok: ok, result: result, error: error }
        }, '*');
      };

      // 监听主应用发来的消息
      window.addEventListener('message', function(event) {
        var data = event.data;
        if (!data || data.${BRIDGE_MARK} !== true) return;
        if (data.type === 'state-sync') {
          window.__MICRO_GLOBAL_STATE__ = data.payload || {};
        } else if (data.type === 'rpc-call') {
          window.__MICRO_EXECUTE_RPC__(data.payload);
        } else if (data.type === 'rpc-result') {
          var pending = __pendingCalls__[data.payload.callId];
          if (pending) {
            delete __pendingCalls__[data.payload.callId];
            if (data.payload.ok) {
              pending.resolve(data.payload.result);
            } else {
              pending.reject(new Error(data.payload.error || 'RPC call failed'));
            }
          }
        }
      });
    })();
  `;
  iframeDoc.head.appendChild(script);
  script.remove();
}

/**
 * 创建 iframe 沙箱实例。
 *
 * 在指定的父容器内创建一个隐藏 iframe，iframe 加载空白文档后，
 * 将主应用的基础样式（CSS 变量、reset 等）注入 iframe document，
 * 并在 iframe 内创建一个挂载容器元素供子应用渲染。
 *
 * @param appName - 子应用名称（用于调试与 iframe title 属性）
 * @param parentEl - 父容器元素，iframe 将挂载到此元素内
 * @returns iframe 沙箱实例
 */
export function createIframeSandbox(
  appName: string,
  parentEl: HTMLElement,
): IframeSandboxInstance {
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-label', `sub-app-${appName}`);
  iframe.setAttribute('data-micro-sandbox', 'iframe');
  iframe.setAttribute('style', IFRAME_STYLE);
  // 使用 about:blank 避免额外网络请求，文档立即可用
  iframe.setAttribute('src', 'about:blank');

  parentEl.appendChild(iframe);

  // 同步等待 iframe document 就绪（about:blank 在同源下立即可用）
  const contentWindow = iframe.contentWindow;
  const contentDocument = iframe.contentDocument;

  if (!contentWindow || !contentDocument) {
    // 极端情况下 iframe 未就绪，移除并回退
    iframe.remove();
    throw new Error(`[IframeSandbox:${appName}] Failed to access iframe contentWindow`);
  }

  // 写入基础 HTML 结构，确保有 body 可用
  contentDocument.open();
  contentDocument.write(
    '<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body></body></html>',
  );
  contentDocument.close();

  // 复制主应用的基础样式表到 iframe（CSS 变量、设计令牌等）
  // 仅复制 <style> 和 <link> 中带 data-shared-style 标记的，避免全量复制
  try {
    const sharedStyles = document.querySelectorAll(
      'style[data-shared-style], link[data-shared-style]',
    );
    sharedStyles.forEach((node) => {
      contentDocument.head.appendChild(node.cloneNode(true));
    });
  } catch {
    // 样式复制失败不阻断沙箱创建
  }

  // 在 iframe body 内创建挂载容器
  const container = contentDocument.createElement('div');
  container.setAttribute('id', 'subapp-container');
  container.setAttribute('data-micro-app', appName);
  contentDocument.body.appendChild(container);

  // v3.6.0: 注入 postMessage 桥接脚本，建立跨 realm 通信通道
  injectBridgeScript(contentWindow);

  // v3.6.0: 主侧消息处理器集合（子 → 主）
  const childMessageHandlers = new Set<(payload: unknown) => void>();

  // v3.6.1: 主 → 子 RPC 待响应 Map（callId → resolve/reject）
  const pendingRpcs = new Map<
    string,
    { resolve: (value: unknown) => void; reject: (error: Error) => void }
  >();
  // v3.6.1: 子 → 主 RPC 处理器（子应用可调用的主应用 API）
  const mainApiHandlers: Record<string, (...args: any[]) => unknown> = {};

  // v3.6.1: RPC 调用序号
  let rpcSeq = 0;

  // 主侧监听 iframe 发来的消息（通过 postMessage 回传）
  const onMessage = (event: MessageEvent): void => {
    if (event.source !== contentWindow) return;
    const data = event.data;
    if (!isBridgeMessage(data)) return;
    if (data.type === 'state-set') {
      for (const handler of childMessageHandlers) {
        try {
          handler(data.payload);
        } catch {
          // 单个 handler 异常不影响其他 handler
        }
      }
      return;
    }
    if (data.type === 'rpc-result') {
      const payload = data.payload as RpcResultPayload;
      const pending = pendingRpcs.get(payload.callId);
      if (pending) {
        pendingRpcs.delete(payload.callId);
        if (payload.ok) {
          pending.resolve(payload.result);
        } else {
          pending.reject(new Error(payload.error || 'RPC call failed'));
        }
      }
      return;
    }
    if (data.type === 'rpc-call') {
      // 子应用调用主应用 API
      const payload = data.payload as RpcCallPayload;
      const handler = mainApiHandlers[payload.method];
      const respond = (ok: boolean, result?: unknown, error?: string) => {
        const response: BridgeMessage = {
          [BRIDGE_MARK]: true,
          type: 'rpc-result',
          payload: { callId: payload.callId, ok, result, error },
        } as BridgeMessage;
        contentWindow.postMessage(response, '*');
      };
      if (typeof handler !== 'function') {
        respond(false, undefined, `RPC method not found: ${payload.method}`);
        return;
      }
      try {
        const result = handler(...payload.args);
        if (result && typeof (result as Promise<unknown>).then === 'function') {
          (result as Promise<unknown>)
            .then((value) => respond(true, value))
            .catch((err) =>
              respond(false, undefined, String(err?.message || err)),
            );
        } else {
          respond(true, result);
        }
      } catch (error) {
        respond(false, undefined, String((error as Error)?.message || error));
      }
    }
  };
  window.addEventListener('message', onMessage);

  let isActive = false;
  let cleaned = false;

  return {
    contentWindow,
    contentDocument,
    container,

    activate() {
      if (isActive || cleaned) return;
      isActive = true;
      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Activated`);
      }
    },

    deactivate() {
      if (!isActive || cleaned) return;
      isActive = false;
      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Deactivated`);
      }
    },

    cleanup() {
      if (cleaned) return;
      cleaned = true;
      isActive = false;

      // v3.6.0: 移除主侧 message 监听器，避免内存泄漏
      window.removeEventListener('message', onMessage);
      childMessageHandlers.clear();

      // v3.6.1: 清理待响应的 RPC 请求
      for (const { reject } of pendingRpcs.values()) {
        reject(new Error(`[IframeSandbox:${appName}] Sandbox closed`));
      }
      pendingRpcs.clear();

      // 清空 iframe 内容并移除
      try {
        contentDocument.write('');
        contentDocument.close();
      } catch {
        // 忽略清理异常
      }
      iframe.remove();

      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Cleaned up`);
      }
    },

    // v3.6.0: 主 → 子 消息发送
    postToChild(payload: unknown): void {
      if (cleaned || !contentWindow) return;
      const message: BridgeMessage = {
        [BRIDGE_MARK]: true,
        type: 'state-sync',
        payload,
      } as BridgeMessage;
      contentWindow.postMessage(message, '*');
    },

    // v3.6.0: 注册子 → 主 消息处理器
    onChildMessage(handler: (payload: unknown) => void): () => void {
      childMessageHandlers.add(handler);
      return () => {
        childMessageHandlers.delete(handler);
      };
    },

    // v3.6.1: 主应用调用子应用 RPC 方法
    async callRpc(method: string, args: unknown[] = []): Promise<unknown> {
      if (cleaned || !contentWindow) {
        throw new Error(`[IframeSandbox:${appName}] Sandbox is closed`);
      }
      const callId = `p${++rpcSeq}`;
      const promise = new Promise<unknown>((resolve, reject) => {
        pendingRpcs.set(callId, { resolve, reject });
      });
      const message: BridgeMessage = {
        [BRIDGE_MARK]: true,
        type: 'rpc-call',
        payload: { method, args, callId } satisfies RpcCallPayload,
      } as BridgeMessage;
      contentWindow.postMessage(message, '*');
      // 超时保护：30s 未响应视为失败
      setTimeout(() => {
        if (pendingRpcs.delete(callId)) {
          reject(new Error(`[IframeSandbox:${appName}] RPC timeout: ${method}`));
        }
      }, 30_000);
      return promise;
    },

    // v3.6.1: 注册主应用 API 供子应用调用
    registerMainApi(
      handlers: Record<string, (...args: any[]) => unknown>,
    ): () => void {
      for (const [method, handler] of Object.entries(handlers)) {
        mainApiHandlers[method] = handler;
      }
      return () => {
        for (const method of Object.keys(handlers)) {
          delete mainApiHandlers[method];
        }
      };
    },
  };
}
