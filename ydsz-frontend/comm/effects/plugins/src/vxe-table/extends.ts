/**
 * extends 模块
 *
 * @path comm\effects\plugins\src\vxe-table\extends.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridProps, VxeUIExport } from 'vxe-table';

import type { Recordable } from '@ydsz/types';

import type { VxeGridApi } from './api';

import { formatDate, formatDateTime, isFunction } from '@ydsz/utils';

/**
 * 批量包装 vxe-grid 的代理请求钩子，使其自动携带搜索表单的当前值。
 *
 * @remarks
 * 解决的问题：vxe-table 的 `proxyConfig.ajax.*` 回调只知道分页 / 排序等表格自身参数，
 * 不感知外部搜索表单。这里对 `query`、`querySuccess`、`queryError`、
 * `queryAll`、`queryAllSuccess`、`queryAllError` 六个钩子统一做一层包装，
 * 在调用原始回调前把 `getFormValues()` 的结果合并进第二个参数。
 *
 * 边界与副作用：
 * - 只包装**调用方实际提供了**的钩子，未定义的直接跳过；
 * - 表单值的优先级高于 `customValues`，同名字段会被覆盖；
 * - 点击工具栏刷新按钮时 vxe-table 会把 `PointerEvent` 当作 `customValues` 传入，
 *   这里会识别并丢弃，避免把事件对象当成查询参数发给后端；
 * - 通过 `api.setState` 写回配置，属于对 API 状态的**原地修改**，
 *   应在表格挂载前调用一次，重复调用会造成包装嵌套。
 *
 * @param api - 目标表格的 API 句柄，包装结果写回它的 state
 * @param options - 原始 grid 配置，从中读取 `proxyConfig.ajax`
 * @param getFormValues - 取当前搜索表单值的函数，每次请求时实时求值
 */
export function extendProxyOptions(
  api: VxeGridApi,
  options: VxeGridProps,
  getFormValues: () => Recordable<any>,
) {
  [
    'query',
    'querySuccess',
    'queryError',
    'queryAll',
    'queryAllSuccess',
    'queryAllError',
  ].forEach((key) => {
    extendProxyOption(key, api, options, getFormValues);
  });
}

function extendProxyOption(
  key: string,
  api: VxeGridApi,
  options: VxeGridProps,
  getFormValues: () => Recordable<any>,
) {
  const { proxyConfig } = options;
  const configFn = (proxyConfig?.ajax as Recordable<any>)?.[key];
  if (!isFunction(configFn)) {
    return options;
  }

  const wrapperFn = async (
    params: Recordable<any>,
    customValues: Recordable<any>,
    ...args: Recordable<any>[]
  ) => {
    const formValues = getFormValues();
    const data = await configFn(
      params,
      {
        /**
         * 开启toolbarConfig.refresh功能
         * 点击刷新按钮 这里的值为PointerEvent 会携带错误参数
         */
        ...(customValues instanceof PointerEvent ? {} : customValues),
        ...formValues,
      },
      ...args,
    );
    return data;
  };
  api.setState({
    gridOptions: {
      proxyConfig: {
        ajax: {
          [key]: wrapperFn,
        },
      },
    },
  });
}

/**
 * 注册项目通用的表格单元格格式化器。
 *
 * @remarks
 * 注册后可在列配置中通过 `formatter: 'formatDate'` / `'formatDateTime'` 直接引用，
 * 分别输出日期与日期时间；具体输出格式与容错行为由 `@ydsz/utils` 的同名函数决定
 * （解析失败不抛异常）。
 *
 * 副作用：向 vxe-table 的**全局**格式化器注册表写入，同名注册会被后者覆盖，
 * 因此应在初始化阶段调用一次。
 *
 * @param vxeUI - vxe-table 的 UI 导出对象，提供 `formats` 注册入口
 */
export function extendsDefaultFormatter(vxeUI: VxeUIExport) {
  vxeUI.formats.add('formatDate', {
    tableCellFormatMethod({ cellValue }) {
      return formatDate(cellValue);
    },
  });

  vxeUI.formats.add('formatDateTime', {
    tableCellFormatMethod({ cellValue }) {
      return formatDateTime(cellValue);
    },
  });
}
