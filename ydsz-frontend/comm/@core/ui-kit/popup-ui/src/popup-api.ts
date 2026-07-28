/**
 * popup-api 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\popup-api.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { MaybePromise } from '@ydsz-core/typings';

import { Store } from '@ydsz-core/shared/store';
import { bindMethods, isFunction } from '@ydsz-core/shared/utils';

/**
 * 弹窗通用回调接口
 */
export interface PopupApiCallbacks {
  onBeforeClose?: () => MaybePromise<boolean | undefined>;
  onCancel?: () => void;
  onClosed?: () => void;
  onConfirm?: () => void;
  onOpenChange?: (isOpen: boolean) => void;
  onOpened?: () => void;
}

/**
 * 弹窗通用 API 选项接口
 */
export interface PopupApiOptions<State extends Record<string, any>>
  extends PopupApiCallbacks {
  connectedComponent?: any;
  [key: string]: any;
}

/**
 * 弹窗 API 基类
 * @description 提供 Modal 和 Drawer 共享的通用方法实现，消除代码重复
 * @template State 弹窗状态类型
 */
export class PopupApi<State extends Record<string, any>> {
  public sharedData: Record<'payload', any> = {
    payload: {},
  };
  public store: Store<State>;

  protected api: PopupApiCallbacks;

  protected state!: State;

  constructor(
    options: PopupApiOptions<State>,
    defaultState: State,
    connectedComponentKey: string = 'connectedComponent',
  ) {
    const {
      [connectedComponentKey]: _,
      onBeforeClose,
      onCancel,
      onClosed,
      onConfirm,
      onOpenChange,
      onOpened,
      ...storeState
    } = options;

    this.store = new Store<State>(
      {
        ...defaultState,
        ...storeState,
      },
      {
        onUpdate: () => {
          const state = this.store.state;

          if (state?.isOpen === this.state?.isOpen) {
            this.state = state;
          } else {
            this.state = state;
            this.api.onOpenChange?.(!!state?.isOpen);
          }
        },
      },
    );

    this.state = this.store.state;

    this.api = {
      onBeforeClose,
      onCancel,
      onClosed,
      onConfirm,
      onOpenChange,
      onOpened,
    };
    bindMethods(this);
  }

  /**
   * 关闭弹窗
   * @description 关闭弹窗时会调用 onBeforeClose 钩子函数，如果 onBeforeClose 返回 false，则不关闭弹窗
   */
  async close() {
    const allowClose = (await this.api.onBeforeClose?.()) ?? true;
    if (allowClose) {
      this.store.setState((prev) => ({
        ...prev,
        isOpen: false,
        submitting: false,
      }));
    }
  }

  getData<T extends object = Record<string, any>>() {
    return (this.sharedData?.payload ?? {}) as T;
  }

  /**
   * 锁定弹窗状态（用于提交过程中的等待状态）
   * @description 锁定状态将禁用默认的取消按钮，使用spinner覆盖弹窗内容，隐藏关闭按钮，阻止手动关闭弹窗，将默认的提交按钮标记为loading状态
   * @param isLocked 是否锁定
   */
  lock(isLocked = true) {
    return this.setState({ submitting: isLocked });
  }

  /**
   * 取消操作
   */
  onCancel() {
    if (this.api.onCancel) {
      this.api.onCancel();
    } else {
      this.close();
    }
  }

  /**
   * 弹窗关闭动画播放完毕后的回调
   */
  onClosed() {
    if (!this.state.isOpen) {
      this.api.onClosed?.();
    }
  }

  /**
   * 确认操作
   */
  onConfirm() {
    this.api.onConfirm?.();
  }

  /**
   * 弹窗打开动画播放完毕后的回调
   */
  onOpened() {
    if (this.state.isOpen) {
      this.api.onOpened?.();
    }
  }

  open() {
    this.store.setState((prev) => ({ ...prev, isOpen: true }));
  }

  setData<T>(payload: T) {
    this.sharedData.payload = payload;
    return this;
  }

  setState(
    stateOrFn:
      | ((prev: State) => Partial<State>)
      | Partial<State>,
  ) {
    if (isFunction(stateOrFn)) {
      this.store.setState(stateOrFn);
    } else {
      this.store.setState((prev) => ({ ...prev, ...stateOrFn }));
    }
    return this;
  }

  /**
   * 解除弹窗的锁定状态
   * @description 解除由lock方法设置的锁定状态，是lock(false)的别名
   */
  unlock() {
    return this.lock(false);
  }
}
