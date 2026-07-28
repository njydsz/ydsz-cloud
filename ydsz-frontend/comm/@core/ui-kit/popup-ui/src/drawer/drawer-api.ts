/**
 * drawer-api 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\drawer\drawer-api.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DrawerApiOptions, DrawerState } from './drawer';

import { PopupApi } from '../popup-api';

const DEFAULT_DRAWER_STATE: DrawerState = {
  class: '',
  closable: true,
  closeIconPlacement: 'right',
  closeOnClickModal: true,
  closeOnPressEscape: true,
  confirmLoading: false,
  contentClass: '',
  footer: true,
  header: true,
  isOpen: false,
  loading: false,
  modal: true,
  openAutoFocus: false,
  placement: 'right',
  showCancelButton: true,
  showConfirmButton: true,
  submitting: false,
  title: '',
};

/**
 * 抽屉 API
 * @description 继承 PopupApi 基类，提供抽屉特有的状态管理
 */
export class DrawerApi extends PopupApi<DrawerState> {
  constructor(options: DrawerApiOptions = {}) {
    super(options, DEFAULT_DRAWER_STATE, 'connectedComponent');
  }
}
