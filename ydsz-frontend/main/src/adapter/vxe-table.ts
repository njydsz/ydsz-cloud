/**
 * vxe-table 适配器模块
 *
 * @path main\src\adapter\vxe-table.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeTableGridOptions } from '@ydsz/plugins/vxe-table';

import { h } from 'vue';

import { useAccess } from '@ydsz/access';
import { setupYDSZVxeTable, useYDSZVxeGrid } from '@ydsz/plugins/vxe-table';

import { ElButton, ElImage } from 'element-plus';

import { useYDSZForm } from './form';

setupYDSZVxeTable({
  configVxeTable: (vxeUI) => {
    vxeUI.setConfig({
      grid: {
        align: 'center',
        border: false,
        columnConfig: {
          resizable: true,
        },
        minHeight: 180,
        formConfig: {
          // 全局禁用vxe-table的表单配置，使用formOptions
          enabled: false,
        },
        proxyConfig: {
          autoLoad: true,
          response: {
            result: 'items',
            total: 'total',
            list: 'items',
          },
          showActiveMsg: true,
          showResponseMsg: false,
        },
        round: true,
        showOverflow: true,
        size: 'small',
      } as VxeTableGridOptions,
    });

    // 表格配置项可以用 cellRender: { name: 'CellImage' },
    vxeUI.renderer.add('CellImage', {
      renderTableDefault(_renderOpts, params) {
        const { column, row } = params;
        const src = row[column.field];
        return h(ElImage, { src, previewSrcList: [src] });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellLink' },
    vxeUI.renderer.add('CellLink', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(
          ElButton,
          { size: 'small', link: true },
          { default: () => props?.text },
        );
      },
    });

    // 字段级数据权限脱敏单元格：
    // 用法 cellRender: { name: 'CellMask', props: { fieldKey: 'project.budget.amount' } }
    // 根据 useAccess().getFieldPermission(fieldKey) 决定：
    //   - 'hidden'：返回空 span（列应同时配合 visible:false 隐藏）
    //   - 'mask'：返回脱敏后的文本（保留首末字符）
    //   - 'read'：原样展示
    vxeUI.renderer.add('CellMask', {
      renderTableDefault(_renderOpts, params) {
        const { column, row } = params;
        const fieldKey = _renderOpts.props?.fieldKey ?? column.field;
        const raw = row[column.field];
        try {
          const { applyFieldMask } = useAccess();
          return h('span', null, applyFieldMask(fieldKey, raw));
        } catch {
          // Pinia 未初始化时降级为原值
          return h('span', null, raw === null || raw === undefined ? '' : String(raw));
        }
      },
    });

    // 这里可以自行扩展 vxe-table 的全局配置，比如自定义格式化
    // vxeUI.formats.add
  },
  useYDSZForm,
});

export { useYDSZVxeGrid };

export type * from '@ydsz/plugins/vxe-table';
