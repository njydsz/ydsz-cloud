/**
 * AlertBuilder 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\alert\AlertBuilder.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component, VNode } from 'vue';

import type { Recordable } from '@ydsz-core/typings';

import type { AlertProps, BeforeCloseScope, PromptProps } from './alert';

import { h, nextTick, ref, render } from 'vue';

import { useSimpleLocale } from '@ydsz-core/composables';
import { Input, YDSZRenderContent } from '@ydsz-core/shadcn-ui';
import { isFunction, isString } from '@ydsz-core/shared/utils';

import Alert from './alert.vue';

const alerts = ref<Array<{ container: HTMLElement; instance: Component }>>([]);

const { $t } = useSimpleLocale();

/**
 * 以完整配置对象的形式弹出提示框。
 *
 * @remarks
 * 适用于需要自定义图标、按钮文案、`beforeClose` 拦截等复杂场景。
 *
 * @param options - 完整弹窗配置，`content` 必填
 * @returns 用户点击确认后 resolve；取消或以其他方式关闭时 **reject**，详见实现签名说明
 */
export function ydszAlert(options: AlertProps): Promise<void>;
/**
 * 以「一段提示文案」的形式弹出提示框，可附带少量配置覆盖。
 *
 * @param message - 提示正文
 * @param options - 可选的配置覆盖项
 * @returns 用户点击确认后 resolve；取消时 reject
 */
export function ydszAlert(
  message: string,
  options?: Partial<AlertProps>,
): Promise<void>;
/**
 * 以「文案 + 标题」的形式弹出提示框，可附带少量配置覆盖。
 *
 * @param message - 提示正文
 * @param title - 弹窗标题；省略时回落到国际化词条 `prompt`（中文为「提示」）
 * @param options - 可选的配置覆盖项
 * @returns 用户点击确认后 resolve；取消时 reject
 */
export function ydszAlert(
  message: string,
  title?: string,
  options?: Partial<AlertProps>,
): Promise<void>;

/**
 * 命令式弹出提示框，返回 Promise 表示用户的选择结果。
 *
 * @remarks
 * **重要：取消操作会导致 Promise reject**（错误信息为 `dialog cancelled`），而非 resolve 一个 false。
 * 因此调用方必须 `try/catch` 或 `.catch()`，否则用户点取消会产生未处理的 Promise rejection。
 * 这一设计使 `await ydszAlert(...)` 之后的代码天然只在确认路径执行。
 *
 * 实现上脱离 Vue 组件树，采用「手动创建容器 + `render` 挂载」的方式：
 * - 会向 `document.body` 追加一个临时 div，关闭时自动 `render(null)` 并移除该节点，
 *   正常流程下不会残留 DOM；
 * - 因不在组件树内，**无法继承祖先组件通过 provide 提供的上下文**，
 *   弹窗内容里的自定义组件若依赖 inject 将取不到值；
 * - 所有实例登记在模块级 `alerts` 数组中，供 {@link clearAllAlerts} 统一清理。
 *
 * 三种重载通过运行时类型判断分发：第二个参数为字符串时视作标题，为对象时视作配置合并。
 * 配置的合并顺序为「基础参数 → arg1 → arg2」，**后者覆盖前者**。
 *
 * @param arg0 - 完整配置对象，或提示正文字符串
 * @param arg1 - 标题字符串或配置对象，语义由运行时类型决定
 * @param arg2 - 配置对象，优先级最高
 * @returns 确认时 resolve；取消时 reject
 *
 * @example
 * ```ts
 * try {
 *   await ydszAlert('保存成功', '提示');
 * } catch {
 *   // 用户取消
 * }
 * ```
 */
export function ydszAlert(
  arg0: AlertProps | string,
  arg1?: Partial<AlertProps> | string,
  arg2?: Partial<AlertProps>,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const options: AlertProps = isString(arg0)
      ? {
          content: arg0,
        }
      : { ...arg0 };
    if (arg1) {
      if (isString(arg1)) {
        options.title = arg1;
      } else if (!isString(arg1)) {
        // 如果第二个参数是对象，则合并到选项中
        Object.assign(options, arg1);
      }
    }

    if (arg2 && !isString(arg2)) {
      Object.assign(options, arg2);
    }
    // 创建容器元素
    const container = document.createElement('div');
    document.body.append(container);

    // 创建一个引用，用于在回调中访问实例
    const alertRef = { container, instance: null as any };

    const props: AlertProps & Recordable<any> = {
      onClosed: (isConfirm: boolean) => {
        // 移除组件实例以及创建的所有dom（恢复页面到打开前的状态）
        // 从alerts数组中移除该实例
        alerts.value = alerts.value.filter((item) => item !== alertRef);

        // 从DOM中移除容器
        render(null, container);
        if (container.parentNode) {
          container.remove();
        }

        // 解析 Promise，传递用户操作结果
        if (isConfirm) {
          resolve();
        } else {
          reject(new Error('dialog cancelled'));
        }
      },
      ...options,
      open: true,
      title: options.title ?? $t.value('prompt'),
    };

    // 创建Alert组件的VNode
    const vnode = h(Alert, props);

    // 渲染组件到容器
    render(vnode, container);

    // 保存组件实例引用
    alertRef.instance = vnode.component?.proxy as Component;

    // 将实例和容器添加到alerts数组中
    alerts.value.push(alertRef);
  });
}

/**
 * 以完整配置对象的形式弹出确认框（带取消按钮）。
 *
 * @param options - 完整弹窗配置；显式传入 `showCancel: false` 可退化为普通提示框
 * @returns 确认时 resolve；取消时 reject
 */
export function ydszConfirm(options: AlertProps): Promise<void>;
/**
 * 以「一段文案」的形式弹出确认框。
 *
 * @param message - 确认提示正文，建议写明操作后果（尤其是不可撤销的操作）
 * @param options - 可选的配置覆盖项
 * @returns 确认时 resolve；取消时 reject
 */
export function ydszConfirm(
  message: string,
  options?: Partial<AlertProps>,
): Promise<void>;
/**
 * 以「文案 + 标题」的形式弹出确认框。
 *
 * @param message - 确认提示正文
 * @param title - 弹窗标题，省略时使用默认的「提示」
 * @param options - 可选的配置覆盖项
 * @returns 确认时 resolve；取消时 reject
 */
export function ydszConfirm(
  message: string,
  title?: string,
  options?: Partial<AlertProps>,
): Promise<void>;

/**
 * 命令式弹出确认框，用于删除、提交等需要二次确认的操作。
 *
 * @remarks
 * 本函数是 {@link ydszAlert} 的**薄封装**，唯一差异是把 `showCancel` 默认置为 `true`，
 * 其余行为（包括「取消即 reject」的契约、脱离组件树的挂载方式）完全一致。
 *
 * 注意默认值的合并方式是 `{ ...defaultProps, ...用户配置 }`，
 * 因此用户显式传入 `showCancel: false` 会覆盖默认值，此时它与 `ydszAlert` 等价。
 *
 * @param arg0 - 完整配置对象，或确认提示正文
 * @param arg1 - 标题字符串或配置对象
 * @param arg2 - 配置对象，优先级最高
 * @returns 确认时 resolve；取消时 reject（错误信息 `dialog cancelled`）
 *
 * @example
 * ```ts
 * await ydszConfirm('删除后不可恢复，确定删除？', '危险操作');
 * await api.delete(id); // 仅在用户确认后执行
 * ```
 */
export function ydszConfirm(
  arg0: AlertProps | string,
  arg1?: Partial<AlertProps> | string,
  arg2?: Partial<AlertProps>,
): Promise<void> {
  const defaultProps: Partial<AlertProps> = {
    showCancel: true,
  };
  if (!arg1) {
    return isString(arg0)
      ? ydszAlert(arg0, defaultProps)
      : ydszAlert({ ...defaultProps, ...arg0 });
  } else if (!arg2) {
    return isString(arg1)
      ? ydszAlert(arg0 as string, arg1, defaultProps)
      : ydszAlert(arg0 as string, { ...defaultProps, ...arg1 });
  }
  return ydszAlert(arg0 as string, arg1 as string, {
    ...defaultProps,
    ...arg2,
  });
}

/**
 * 弹出带输入控件的确认框，用于获取用户的一次性输入（如填写驳回原因）。
 *
 * @remarks
 * 默认使用内置 Input，也可通过 `component` 换成任意受控组件（下拉、日期选择等），
 * 并用 `modelPropName` 指定其 v-model 的 prop 名（默认 `'modelValue'`）——
 * 该名字必须与目标组件一致，否则输入无法回写，最终恒返回默认值。
 *
 * 行为要点：
 * - 内部复用 {@link ydszConfirm}，因此**用户取消时同样会抛出异常**而不是返回 `undefined`，
 *   调用方必须捕获；返回 `undefined` 只表示「确认了但没输入内容」；
 * - 内容以函数形式传入，每次重渲染都会重新构建输入组件的 props，从而保证受控值同步；
 * - 打开后会尝试自动聚焦输入控件，聚焦策略按优先级降级：组件 `exposed.focus()`
 *   → 根元素本身可聚焦 → 在根元素内查询首个可聚焦子元素 → 相邻兄弟元素。
 *   若自定义组件的结构不满足上述任一条，则不会自动聚焦，但不影响正常使用；
 * - 强制开启 `contentMasking`，`beforeClose` 异步执行期间内容区显示 loading 遮罩，
 *   避免用户重复提交；外部传入的 `beforeClose` 会被包装，额外接收到当前输入值。
 *
 * @param options - 提示配置 + 输入组件配置，详见 `PromptProps`
 * @returns 用户确认后的输入值；未输入过则为 `defaultValue`（可能是 `undefined`）
 *
 * @example
 * ```ts
 * const reason = await ydszPrompt<string>({
 *   content: '请输入驳回原因',
 *   defaultValue: '',
 * });
 * ```
 */
export async function ydszPrompt<T = any>(
  options: PromptProps<T>,
): Promise<T | undefined> {
  const {
    component: _component,
    componentProps: _componentProps,
    componentSlots,
    content,
    defaultValue,
    modelPropName: _modelPropName,
    ...delegated
  } = options;

  const modelValue = ref<T | undefined>(defaultValue);
  const inputComponentRef = ref<null | VNode>(null);
  const staticContents: Component[] = [];

  staticContents.push(h(YDSZRenderContent, { content, renderBr: true }));

  const modelPropName = _modelPropName || 'modelValue';
  const componentProps = { ..._componentProps };

  // 每次渲染时都会重新计算的内容函数
  const contentRenderer = () => {
    const currentProps = { ...componentProps };

    // 设置当前值
    currentProps[modelPropName] = modelValue.value;

    // 设置更新处理函数
    currentProps[`onUpdate:${modelPropName}`] = (val: T) => {
      modelValue.value = val;
    };

    // 创建输入组件
    inputComponentRef.value = h(
      _component || Input,
      currentProps,
      componentSlots,
    );

    // 返回包含静态内容和输入组件的数组
    return h(
      'div',
      { class: 'flex flex-col gap-2' },
      { default: () => [...staticContents, inputComponentRef.value] },
    );
  };

  const props: AlertProps & Recordable<any> = {
    ...delegated,
    async beforeClose(scope: BeforeCloseScope) {
      if (delegated.beforeClose) {
        return await delegated.beforeClose({
          ...scope,
          value: modelValue.value,
        });
      }
    },
    // 使用函数形式，每次渲染都会重新计算内容
    content: contentRenderer,
    contentMasking: true,
    async onOpened() {
      await nextTick();
      const componentRef: null | VNode = inputComponentRef.value;
      if (componentRef) {
        if (
          componentRef.component?.exposed &&
          isFunction(componentRef.component.exposed.focus)
        ) {
          componentRef.component.exposed.focus();
        } else {
          if (componentRef.el) {
            if (
              isFunction(componentRef.el.focus) &&
              ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA'].includes(
                componentRef.el.tagName,
              )
            ) {
              componentRef.el.focus();
            } else if (isFunction(componentRef.el.querySelector)) {
              const focusableElement = componentRef.el.querySelector(
                'input, select, textarea, button',
              );
              if (focusableElement && isFunction(focusableElement.focus)) {
                focusableElement.focus();
              }
            } else if (
              componentRef.el.nextElementSibling &&
              isFunction(componentRef.el.nextElementSibling.focus)
            ) {
              componentRef.el.nextElementSibling.focus();
            }
          }
        }
      }
    },
  };

  await ydszConfirm(props);
  return modelValue.value;
}

/**
 * 强制销毁当前所有由本模块创建的弹窗。
 *
 * @remarks
 * 主要用于路由切换、用户登出等「场景整体失效」的时机——这类弹窗挂在 body 上、
 * 不随页面组件卸载而消失，不清理会残留在新页面之上。
 *
 * **关键副作用：被清理弹窗对应的 Promise 既不会 resolve 也不会 reject**，
 * 而是永久挂起。因为清理逻辑直接卸载 DOM，绕过了组件的 `onClosed` 回调。
 * 若有 `await ydszConfirm(...)` 之后的逻辑，将永远不会执行（相关闭包也无法被回收）。
 * 因此仅应在确实要丢弃这些交互结果时调用，正常关闭请让用户操作或走弹窗自身的关闭流程。
 *
 * 同理，弹窗的 `beforeClose` 拦截也会被跳过，不存在「关不掉」的情况。
 */
export function clearAllAlerts() {
  alerts.value.forEach((alert) => {
    // 从DOM中移除容器
    render(null, alert.container);
    if (alert.container.parentNode) {
      alert.container.remove();
    }
  });
  alerts.value = [];
}
