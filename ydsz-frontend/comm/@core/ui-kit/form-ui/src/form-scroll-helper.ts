import type { ComponentPublicInstance } from 'vue';

import { isRef } from 'vue';

/**
 * 表单滚动辅助类
 * @description 负责表单的 DOM 滚动操作，包括滚动到错误字段等
 */
export class FormScrollHelper {
  private componentRefMap: Map<string, unknown>;

  constructor(componentRefMap: Map<string, unknown>) {
    this.componentRefMap = componentRefMap;
  }

  /**
   * 更新组件引用映射
   * @param componentRefMap 最新的组件引用映射
   */
  updateRefMap(componentRefMap: Map<string, unknown>) {
    this.componentRefMap = componentRefMap;
  }

  /**
   * 获取字段组件实例
   * @param fieldName 字段名
   * @returns 组件实例
   */
  getFieldComponentRef<T = ComponentPublicInstance>(
    fieldName: string,
  ): T | undefined {
    let target = this.componentRefMap.has(fieldName)
      ? (this.componentRefMap.get(fieldName) as ComponentPublicInstance)
      : undefined;
    if (
      target &&
      target.$.type.name === 'AsyncComponentWrapper' &&
      target.$.subTree.ref
    ) {
      if (Array.isArray(target.$.subTree.ref)) {
        if (
          target.$.subTree.ref.length > 0 &&
          isRef(target.$.subTree.ref[0]?.r)
        ) {
          target = target.$.subTree.ref[0]?.r.value as ComponentPublicInstance;
        }
      } else if (isRef(target.$.subTree.ref.r)) {
        target = target.$.subTree.ref.r.value as ComponentPublicInstance;
      }
    }
    return target as T;
  }

  /**
   * 获取当前聚焦的字段名
   * @returns 聚焦字段名，如果没有聚焦字段则返回 undefined
   */
  getFocusedField(): string | undefined {
    for (const fieldName of this.componentRefMap.keys()) {
      const ref = this.getFieldComponentRef(fieldName);
      if (ref) {
        let el: HTMLElement | null = null;
        if (ref instanceof HTMLElement) {
          el = ref;
        } else if (ref.$el instanceof HTMLElement) {
          el = ref.$el;
        }
        if (!el) {
          continue;
        }
        if (
          el === document.activeElement ||
          el.contains(document.activeElement)
        ) {
          return fieldName;
        }
      }
    }
    return undefined;
  }

  /**
   * 滚动到第一个错误字段
   * @param errors 验证错误对象或字段名
   */
  scrollToFirstError(errors: Record<string, any> | string) {
    const firstErrorFieldName =
      typeof errors === 'string' ? errors : Object.keys(errors)[0];

    if (!firstErrorFieldName) {
      return;
    }

    let el = document.querySelector(
      `[name="${firstErrorFieldName}"]`,
    ) as HTMLElement;

    if (!el) {
      const componentRef = this.getFieldComponentRef(firstErrorFieldName);
      if (componentRef && componentRef.$el instanceof HTMLElement) {
        el = componentRef.$el;
      }
    }

    if (el) {
      el.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'nearest',
      });
    }
  }
}
