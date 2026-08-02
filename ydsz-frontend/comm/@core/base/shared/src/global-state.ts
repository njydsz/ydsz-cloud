/**
 * 全局复用的变量、组件、配置，各个模块之间共享
 * 通过单例模式实现,单例必须注意不受请求影响，例如用户信息这些需要根据请求获取的。后续如果有ssr需求，也不会影响
 */

interface ComponentsState {
  [key: string]: any;
}

interface MessageState {
  copyPreferencesSuccess?: (title: string, content?: string) => void;
}

/**
 * 全局共享状态的结构契约。
 *
 * @remarks
 * 该接口仅描述 {@link globalShareState} 单例对外暴露的数据形状，供上层做类型标注使用；
 * 单例本身并未实现此接口，二者是「形状约定」而非继承关系，新增字段时需两边同步维护。
 *
 * 由于是进程级单例，**禁止**在此存放与单次请求/单个用户强相关的数据（如登录态、权限），
 * 否则未来接入 SSR 时会造成跨请求数据串号。
 */
export interface IGlobalSharedState {
  /** 由宿主应用注册的全局组件映射表，键为业务约定的组件名，用于框架内部按名动态渲染 */
  components: ComponentsState;
  /** 框架内部场景所需的消息提示回调集合，未注册时相关提示会被静默跳过 */
  message: MessageState;
}

class GlobalShareState {
  #components: ComponentsState = {};
  #message: MessageState = {};

  /**
   * 定义框架内部各个场景的消息提示
   */
  public defineMessage({ copyPreferencesSuccess }: MessageState) {
    this.#message = {
      copyPreferencesSuccess,
    };
  }

  public getComponents(): ComponentsState {
    return this.#components;
  }

  public getMessage(): MessageState {
    return this.#message;
  }

  public setComponents(value: ComponentsState) {
    this.#components = value;
  }
}

export const globalShareState = new GlobalShareState();
