/**
 * app-tour 组合式函数 — 用户操作引导
 *
 * @path comm\effects\shared-business\src\composables\use-app-tour.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 轻量自研引导系统（不依赖第三方库）：
 * - 高亮目标元素 + 蒙层 + 气泡提示
 * - 步骤推进（下一步/上一步/跳过/完成）
 * - 完成状态持久化（localStorage），已完成用户不再展示
 *
 * 用法：
 * ```ts
 * const tour = useAppTour('first-login', [
 *   { selector: '#menu', title: '菜单', content: '从这里进入各模块' },
 *   { selector: '#search', title: '全局搜索', content: '按 Ctrl+K 快速搜索' },
 * ]);
 * tour.start(); // 检查未完成才启动
 * ```
 */

/** 引导步骤定义 */
export interface TourStep {
  /** 目标元素选择器（CSS selector） */
  selector: string;
  /** 步骤标题 */
  title: string;
  /** 步骤说明 */
  content: string;
  /** 气泡位置：top/bottom/left/right，默认 bottom */
  placement?: 'top' | 'bottom' | 'left' | 'right';
}

/** 引导配置 */
export interface AppTourOptions {
  /** 唯一标识（localStorage key 后缀） */
  id: string;
  /** 步骤列表 */
  steps: TourStep[];
  /** 是否强制展示（忽略已完成状态），默认 false */
  force?: boolean;
}

interface TourState {
  /** 是否展示中 */
  active: boolean;
  /** 当前步骤索引 */
  currentIndex: number;
  /** 总步骤数 */
  total: number;
  /** 目标元素 */
  target: Element | null;
}

const STORAGE_PREFIX = 'ydsz-tour-done';

/** 获取目标元素（支持延迟等待元素渲染） */
function queryTarget(selector: string): Element | null {
  try {
    return document.querySelector(selector);
  } catch {
    return null;
  }
}

/**
 * 创建引导实例
 *
 * @param options - 引导配置
 * @returns 引导控制对象
 */
export function useAppTour(options: AppTourOptions) {
  const { id, steps, force = false } = options;

  const state: TourState = {
    active: false,
    currentIndex: 0,
    total: steps.length,
    target: null,
  };

  let overlayEl: HTMLElement | null = null;
  let popupEl: HTMLElement | null = null;

  const storageKey = `${STORAGE_PREFIX}:${id}`;

  function isCompleted(): boolean {
    try {
      return localStorage.getItem(storageKey) === '1';
    } catch {
      return false;
    }
  }

  function markCompleted() {
    try {
      localStorage.setItem(storageKey, '1');
    } catch {
      // 忽略存储失败
    }
  }

  function clearOverlay() {
    overlayEl?.remove();
    popupEl?.remove();
    overlayEl = null;
    popupEl = null;
    state.active = false;
    state.target = null;
  }

  function highlightTarget(step: TourStep) {
    clearOverlay();
    const target = queryTarget(step.selector);
    if (!target) {
      // 元素未就绪，跳过该步骤
      next();
      return;
    }
    state.target = target;

    // 遮罩层
    overlayEl = document.createElement('div');
    overlayEl.className = 'tour-overlay';
    const rect = target.getBoundingClientRect();
    overlayEl.style.cssText = `
      position: fixed; inset: 0; z-index: 10000; pointer-events: none;
      box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.5);
      border-radius: 4px;
      border: 2px solid #409eff;
      top: ${rect.top}px; left: ${rect.left}px;
      width: ${rect.width}px; height: ${rect.height}px;
    `;
    document.body.appendChild(overlayEl);

    // 气泡
    popupEl = document.createElement('div');
    popupEl.className = 'tour-popup';
    const placement = step.placement || 'bottom';
    const pad = 12;
    const popupStyle: Record<string, string> = {
      position: 'fixed',
      zIndex: '10001',
      background: '#fff',
      borderRadius: '8px',
      boxShadow: '0 6px 16px rgba(0,0,0,0.15)',
      padding: '16px',
      maxWidth: '320px',
      fontSize: '13px',
      color: '#303133',
      lineHeight: '1.6',
    };
    if (placement === 'bottom') {
      popupStyle.top = `${rect.bottom + pad}px`;
      popupStyle.left = `${rect.left}px`;
    } else if (placement === 'top') {
      popupStyle.top = `${Math.max(rect.top - 80, pad)}px`;
      popupStyle.left = `${rect.left}px`;
    } else if (placement === 'left') {
      popupStyle.top = `${rect.top}px`;
      popupStyle.left = `${Math.max(rect.left - 340, pad)}px`;
    } else {
      popupStyle.top = `${rect.top}px`;
      popupStyle.left = `${rect.right + pad}px`;
    }
    Object.assign(popupEl.style, popupStyle);
    popupEl.innerHTML = `
      <div style="font-weight:600;font-size:14px;margin-bottom:6px;">${step.title}</div>
      <div style="color:#606266;margin-bottom:12px;">${step.content}</div>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span style="font-size:12px;color:#909399;">${state.currentIndex + 1} / ${state.total}</span>
        <div>
          <button data-tour="prev" style="margin-right:6px;padding:4px 10px;border:1px solid #dcdfe6;background:#fff;border-radius:4px;cursor:pointer;font-size:12px;">上一步</button>
          <button data-tour="next" style="padding:4px 12px;border:none;background:#409eff;color:#fff;border-radius:4px;cursor:pointer;font-size:12px;">${state.currentIndex === state.total - 1 ? '完成' : '下一步'}</button>
        </div>
      </div>
      <div style="text-align:right;margin-top:8px;">
        <button data-tour="skip" style="border:none;background:none;color:#909399;cursor:pointer;font-size:12px;">跳过引导</button>
      </div>
    `;
    document.body.appendChild(popupEl);

    // 绑定按钮事件
    popupEl.querySelector('[data-tour="next"]')?.addEventListener('click', () => {
      if (state.currentIndex >= state.total - 1) {
        finish();
      } else {
        next();
      }
    });
    popupEl.querySelector('[data-tour="prev"]')?.addEventListener('click', prev);
    popupEl.querySelector('[data-tour="skip"]')?.addEventListener('click', finish);
  }

  function next() {
    if (state.currentIndex < state.total - 1) {
      state.currentIndex += 1;
      highlightTarget(steps[state.currentIndex]);
    } else {
      finish();
    }
  }

  function prev() {
    if (state.currentIndex > 0) {
      state.currentIndex -= 1;
      highlightTarget(steps[state.currentIndex]);
    }
  }

  /** 完成引导 */
  function finish() {
    markCompleted();
    clearOverlay();
  }

  /** 跳过引导（不标记完成，下次仍会展示） */
  function skip() {
    clearOverlay();
  }

  /**
   * 启动引导（默认仅对未完成的用户展示）
   *
   * @returns 是否实际启动
   */
  function start(): boolean {
    if (!force && isCompleted()) {
      return false;
    }
    if (steps.length === 0) return false;
    state.currentIndex = 0;
    state.active = true;
    highlightTarget(steps[0]);
    return true;
  }

  return {
    finish,
    isCompleted,
    skip,
    start,
    state,
  };
}
