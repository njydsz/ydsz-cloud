/**
 * use-keyboard-shortcut 组合式函数 — 键盘快捷键体系
 *
 * @path comm\effects\shared-business\src\composables\use-keyboard-shortcut.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 提供键盘快捷键注册能力，支持：
 * - 组合键（Ctrl/Shift/Alt/Meta + key）
 * - 作用域隔离（scope）：不同页面可注册同名快捷键互不冲突
 * - 冲突检测：同一作用域内重复注册会告警
 * - 自动清理：组件卸载时自动解绑
 */
import { onBeforeUnmount, onMounted } from 'vue';

/** 修饰键集合 */
type ModifierKey = 'ctrl' | 'shift' | 'alt' | 'meta';

/** 快捷键描述 */
export interface ShortcutDescriptor {
  /** 触发键（小写），如 'k'、's'、'enter' */
  key: string;
  /** 修饰键组合 */
  modifiers?: ModifierKey[];
  /** 作用域标识，默认 'global' */
  scope?: string;
  /** 是否阻止默认行为 */
  preventDefault?: boolean;
  /** 是否在输入框内也触发（默认 false：输入时不触发） */
  allowInInput?: boolean;
}

/** 快捷键回调 */
type ShortcutHandler = (event: KeyboardEvent) => void;

interface RegisteredShortcut {
  descriptor: ShortcutDescriptor;
  handler: ShortcutHandler;
}

/** 全局注册表：scope → shortcuts */
const registry = new Map<string, RegisteredShortcut[]>();

/** 已绑定的全局 keydown 监听（避免重复绑定） */
let globalListenerBound = false;

function isInputTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return (
    tag === 'input' ||
    tag === 'textarea' ||
    tag === 'select' ||
    target.isContentEditable
  );
}

function matchModifiers(event: KeyboardEvent, modifiers: ModifierKey[] = []): boolean {
  const state = {
    ctrl: event.ctrlKey,
    shift: event.shiftKey,
    alt: event.altKey,
    meta: event.metaKey,
  };
  // 要求的修饰键必须按下
  for (const mod of modifiers) {
    if (!state[mod]) return false;
  }
  // 未要求的修饰键不能按下（避免 Ctrl+Shift+K 误触 Ctrl+K）
  const required = new Set(modifiers);
  for (const [mod, pressed] of Object.entries(state)) {
    if (!required.has(mod as ModifierKey) && pressed) return false;
  }
  return true;
}

function handleKeydown(event: KeyboardEvent) {
  // 遍历所有 scope，匹配首个命中
  for (const shortcuts of registry.values()) {
    for (const { descriptor, handler } of shortcuts) {
      const { key, modifiers = [], allowInInput = false, preventDefault = true } =
        descriptor;

      if (event.key.toLowerCase() !== key) continue;
      if (!matchModifiers(event, modifiers)) continue;
      if (!allowInInput && isInputTarget(event.target)) continue;

      if (preventDefault) {
        event.preventDefault();
      }
      handler(event);
      return;
    }
  }
}

/** 解绑单个快捷键 */
function unbind(scope: string, key: string, modifiers: ModifierKey[] = []) {
  const shortcuts = registry.get(scope);
  if (!shortcuts) return;
  const idx = shortcuts.findIndex(
    (s) =>
      s.descriptor.key === key &&
      JSON.stringify(s.descriptor.modifiers) === JSON.stringify(modifiers),
  );
  if (idx >= 0) {
    shortcuts.splice(idx, 1);
  }
  if (shortcuts.length === 0) {
    registry.delete(scope);
  }
}

/**
 * 注册键盘快捷键（组件内使用，卸载自动清理）
 *
 * @param descriptor - 快捷键描述
 * @param handler - 回调
 *
 * @example
 * ```ts
 * // 页面内 Ctrl+K 触发搜索
 * useKeyboardShortcut(
 *   { key: 'k', modifiers: ['ctrl'], scope: 'list-page' },
 *   () => openSearch(),
 * );
 * ```
 */
export function useKeyboardShortcut(
  descriptor: ShortcutDescriptor,
  handler: ShortcutHandler,
) {
  const { scope = 'global', key, modifiers = [] } = descriptor;

  // 冲突检测
  const existing = registry.get(scope) || [];
  const conflict = existing.find(
    (s) =>
      s.descriptor.key === key &&
      JSON.stringify(s.descriptor.modifiers) === JSON.stringify(modifiers),
  );
  if (conflict) {
    console.warn(
      `[shortcut] 快捷键冲突: scope=${scope}, key=${key}, modifiers=${JSON.stringify(modifiers)}`,
    );
  }

  onMounted(() => {
    if (!globalListenerBound) {
      window.addEventListener('keydown', handleKeydown);
      globalListenerBound = true;
    }
    const shortcuts = registry.get(scope) || [];
    shortcuts.push({ descriptor, handler });
    registry.set(scope, shortcuts);
  });

  onBeforeUnmount(() => {
    unbind(scope, key, modifiers);
  });
}

/**
 * 注册全局快捷键（非组件场景，返回手动解绑函数）
 */
export function bindGlobalShortcut(
  descriptor: ShortcutDescriptor,
  handler: ShortcutHandler,
): () => void {
  const { scope = 'global', key, modifiers = [] } = descriptor;

  if (!globalListenerBound) {
    window.addEventListener('keydown', handleKeydown);
    globalListenerBound = true;
  }
  const shortcuts = registry.get(scope) || [];
  shortcuts.push({ descriptor, handler });
  registry.set(scope, shortcuts);

  return () => unbind(scope, key, modifiers);
}

/** 清理某作用域下全部快捷键 */
export function clearScope(scope: string) {
  registry.delete(scope);
}
