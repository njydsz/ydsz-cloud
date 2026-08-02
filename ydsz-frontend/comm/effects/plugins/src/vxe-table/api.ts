/**
 * api 模块
 *
 * @path comm\effects\plugins\src\vxe-table\api.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridInstance } from 'vxe-table';

import type { ExtendedFormApi } from '@ydsz-core/form-ui';

import type { VxeGridProps } from './types';

import { toRaw } from 'vue';

import { Store } from '@ydsz-core/shared/store';
import {
  bindMethods,
  isBoolean,
  isFunction,
  mergeWithArrayOverride,
  StateHandler,
} from '@ydsz-core/shared/utils';

function getDefaultState(): VxeGridProps {
  return {
    class: '',
    gridClass: '',
    gridOptions: {},
    gridEvents: {},
    formOptions: undefined,
    showSearchForm: true,
  };
}

/**
 * VxeGrid 表格的命令式操作句柄，用于在组件外部驱动表格与搜索表单。
 *
 * @remarks
 * 设计意图：把「配置状态」与「组件实例」解耦——业务代码只持有本 API 对象，
 * 通过 `setState` 修改配置由内部 `Store` 驱动组件更新，不必再层层传递 ref。
 *
 * 使用约定与注意事项：
 * - 构造时传入的配置会与默认配置合并（数组采用**整体覆盖**而非逐项合并），
 *   同名数组字段（如 `columns`）以传入值为准；
 * - `grid` / `formApi` 在 {@link VxeGridApi.mount} 被调用前是**空对象占位**，
 *   过早调用 `query` / `reload` 会因取不到真实实例而进入 catch 分支；
 * - `mount` 具备幂等性，重复挂载会被忽略；组件卸载时须调用 `unmount` 重置内部状态，
 *   否则下次挂载不会生效；
 * - 构造函数中执行了 `bindMethods(this)`，所有方法均已绑定 this，可安全解构后单独传递。
 *
 * @typeParam T - 表格行数据的类型
 *
 * @example
 * ```ts
 * const [Grid, gridApi] = useYDSZVxeGrid({ gridOptions });
 * await gridApi.query({ keyword: 'foo' });
 * gridApi.setLoading(true);
 * ```
 */
export class VxeGridApi<T extends Record<string, any> = any> {
  /** 搜索表单的操作句柄；挂载前为空对象占位 */
  public formApi = {} as ExtendedFormApi;

  // private prevState: null | VxeGridProps = null;
  /** vxe-grid 组件实例；挂载前为空对象占位，直接调用其方法会抛错 */
  public grid = {} as VxeGridInstance<T>;

  /** 当前配置状态的快照，随 store 更新同步刷新，供只读访问 */
  public state: null | VxeGridProps<T> = null;

  /** 配置状态仓库，组件订阅它以响应外部的 setState */
  public store: Store<VxeGridProps<T>>;

  private isMounted = false;

  private stateHandler: StateHandler;

  constructor(options: VxeGridProps = {}) {
    const storeState = { ...options };

    const defaultState = getDefaultState();
    this.store = new Store<VxeGridProps>(
      mergeWithArrayOverride(storeState, defaultState),
      {
        onUpdate: () => {
          // this.prevState = this.state;
          this.state = this.store.state;
        },
      },
    );

    this.state = this.store.state;
    this.stateHandler = new StateHandler();
    bindMethods(this);
  }

  /**
   * 绑定真实的表格与表单实例，由容器组件在 `onMounted` 时调用。
   *
   * @remarks
   * 幂等：已挂载或 `instance` 为空时直接忽略本次调用。
   * 挂载成功后会置位内部条件，唤醒此前排队等待实例的异步操作。
   *
   * @param instance - vxe-grid 组件实例，为 null 时不做任何处理
   * @param formApi - 搜索表单的操作句柄
   */
  mount(instance: null | VxeGridInstance, formApi: ExtendedFormApi) {
    if (!this.isMounted && instance) {
      this.grid = instance;
      this.formApi = formApi;
      this.stateHandler.setConditionTrue();
      this.isMounted = true;
    }
  }

  /**
   * 以当前分页状态重新查询数据。
   *
   * @remarks
   * 与 {@link VxeGridApi.reload} 的区别：`query` **保留当前页码**，适合筛选条件未变的刷新；
   * 查询异常只会 `console.error`，**不会向外抛出**，调用方无法通过 catch 感知失败。
   *
   * @param params - 附加到本次请求的额外参数；会先 `toRaw` 脱去响应式代理再提交
   */
  async query(params: Record<string, any> = {}) {
    try {
      await this.grid.commitProxy('query', toRaw(params));
    } catch (error) {
      console.error('Error occurred while querying:', error);
    }
  }

  /**
   * 重置到第一页并重新查询数据。
   *
   * @remarks
   * 适合筛选条件变更后的查询，避免停留在越界页码导致空列表。
   * 同 {@link VxeGridApi.query}，异常仅打印日志不外抛。
   *
   * @param params - 附加到本次请求的额外参数
   */
  async reload(params: Record<string, any> = {}) {
    try {
      await this.grid.commitProxy('reload', toRaw(params));
    } catch (error) {
      console.error('Error occurred while reloading:', error);
    }
  }

  /**
   * 增量更新 vxe-grid 配置，典型用于动态改列、切换分页配置。
   *
   * @param options - 待合并的 grid 配置片段；对象深合并，数组整体覆盖
   */
  setGridOptions(options: Partial<VxeGridProps['gridOptions']>) {
    this.setState({
      gridOptions: options,
    });
  }

  /**
   * 手动控制表格的 loading 遮罩。
   *
   * @remarks
   * 仅在使用自定义数据加载逻辑时需要；走 `proxyConfig` 代理查询时 loading 由 vxe-table 自行管理，
   * 手动调用可能与内部状态冲突。
   *
   * @param isLoading - true 显示遮罩，false 隐藏
   */
  setLoading(isLoading: boolean) {
    this.setState({
      gridOptions: {
        loading: isLoading,
      },
    });
  }

  /**
   * 更新表格配置状态。
   *
   * @remarks
   * 合并策略为 `mergeWithArrayOverride`：普通对象递归深合并，**数组整体替换**，
   * 因此更新 `columns` 时必须传完整数组。
   * 传函数形式可基于上一次状态推导新状态，适合依赖当前值的切换类操作。
   *
   * @param stateOrFn - 待合并的状态片段，或接收上一状态并返回状态片段的函数
   */
  setState(
    stateOrFn:
      | ((prev: VxeGridProps<T>) => Partial<VxeGridProps<T>>)
      | Partial<VxeGridProps<T>>,
  ) {
    if (isFunction(stateOrFn)) {
      this.store.setState((prev) => {
        return mergeWithArrayOverride(stateOrFn(prev), prev);
      });
    } else {
      this.store.setState((prev) => mergeWithArrayOverride(stateOrFn, prev));
    }
  }

  /**
   * 显示 / 隐藏搜索表单。
   *
   * @param show - 显式指定目标状态；省略或传入非布尔值时按当前状态取反
   * @returns 切换后的显示状态
   */
  toggleSearchForm(show?: boolean) {
    this.setState({
      showSearchForm: isBoolean(show) ? show : !this.state?.showSearchForm,
    });
    // nextTick(() => {
    //   this.grid.recalculate();
    // });
    return this.state?.showSearchForm;
  }

  /**
   * 解除与组件实例的绑定，由容器组件在卸载时调用。
   *
   * @remarks
   * 只重置挂载标记与内部等待条件，**不清空 `store` 中的配置状态**，
   * 因此同一个 API 对象可在下次挂载时复用既有配置。
   */
  unmount() {
    this.isMounted = false;
    this.stateHandler.reset();
  }
}
