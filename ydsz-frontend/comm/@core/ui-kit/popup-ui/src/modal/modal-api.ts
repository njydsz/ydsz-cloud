import type { ModalApiOptions, ModalState } from './modal';

import { PopupApi } from '../popup-api';

const DEFAULT_MODAL_STATE: ModalState = {
  bordered: true,
  centered: false,
  class: '',
  closeOnClickModal: true,
  closeOnPressEscape: true,
  confirmDisabled: false,
  confirmLoading: false,
  contentClass: '',
  destroyOnClose: true,
  draggable: false,
  footer: true,
  footerClass: '',
  fullscreen: false,
  fullscreenButton: true,
  header: true,
  headerClass: '',
  isOpen: false,
  loading: false,
  modal: true,
  openAutoFocus: false,
  showCancelButton: true,
  showConfirmButton: true,
  title: '',
  animationType: 'slide',
};

/**
 * 弹窗 API
 * @description 继承 PopupApi 基类，提供弹窗特有的状态管理
 */
export class ModalApi extends PopupApi<ModalState> {
  constructor(options: ModalApiOptions = {}) {
    super(options, DEFAULT_MODAL_STATE, 'connectedComponent');
  }
}
