/**
 * 快捷键中枢（v4.0）
 *
 * 统一管理全局快捷键：注册、冲突检测、用户自定义（可选）
 *
 * 用法：
 *   registerKeyboard('cmd+k', handler)
 *   registerKeyboard('ctrl+s', handler, { preventDefault: false })
 *   const stop = registerKeyboard('escape', handler)
 *
 *   stop() —— 取消注册
 *
 * @since 4.0.0
 */

export type Modifier = 'cmd' | 'ctrl' | 'alt' | 'shift';

export interface ShortcutOptions {
  /** 是否阻止默认行为（默认 true） */
  preventDefault?: boolean;
  /** 是否在 input/textarea 内仍生效 */
  enableInInput?: boolean;
}

interface Shortcut {
  combo: string;        // 标准化组合键字符串
  handler: (e: KeyboardEvent) => void;
  options: ShortcutOptions;
}

const registry = new Map<string, Shortcut>();

/** 是否已注册全局 keydown 监听 */
let bound = false;

/**
 * 将键盘事件标准化为组合字符串（如 "cmd+shift+p"）
 */
function normalizeEvent(e: KeyboardEvent): string {
  const parts: string[] = [];
  if (e.metaKey || e.ctrlKey) parts.push('cmd');
  if (e.altKey) parts.push('alt');
  if (e.shiftKey) parts.push('shift');
  const key = e.key.toLowerCase();
  if (!['meta', 'control', 'alt', 'shift'].includes(key)) parts.push(key);
  return parts.join('+');
}

/**
 * 解析用户友好的快捷键表达式（"cmd+k" → 标准化格式）
 */
function parseShortcutExpr(expr: string): string {
  return expr
    .toLowerCase()
    .split('+')
    .map((s) => s.trim())
    .map((s) => (s === 'cmd' || s === 'command' || s === 'meta' ? 'cmd' : s))
    .map((s) => (s === 'ctrl' || s === 'control' ? 'cmd' : s))
    .sort((a, b) => {
      const o = ['cmd', 'alt', 'shift'];
      const ao = o.indexOf(a);
      const bo = o.indexOf(b);
      if (ao >= 0 && bo >= 0) return ao - bo;
      if (ao >= 0) return -1;
      if (bo >= 0) return 1;
      return a.localeCompare(b);
    })
    .join('+');
}

function bindGlobal(): void {
  if (bound) return;
  bound = true;
  document.addEventListener('keydown', handleKeyDown, true);
}

function handleKeyDown(e: KeyboardEvent): void {
  const combo = normalizeEvent(e);
  const shortcut = registry.get(combo);
  if (!shortcut) return;

  const { handler, options } = shortcut;
  const { preventDefault = true, enableInInput = false } = options;

  // input/textarea 内默认不拦截（除非显式 enableInInput）
  if (!enableInInput) {
    const target = e.target as HTMLElement | null;
    if (target && ['input', 'textarea'].includes(target.tagName.toLowerCase())) return;
  }

  if (preventDefault) e.preventDefault();
  handler(e);
}

/**
 * 注册全局快捷键。
 *
 * @param expr     快捷键表达式，如 "cmd+k"、"ctrl+shift+p"、"escape"
 * @param handler  按键处理函数
 * @param options  选项
 * @returns 取消注册函数
 *
 * @example
 * // 在 setup 内
 * onMounted(() => {
 *   const stop = registerKeyboard('cmd+k', () => searchVisible.value = !searchVisible.value);
 *   onUnmounted(stop);
 * });
 */
export function registerKeyboard(
  expr: string,
  handler: (e: KeyboardEvent) => void,
  options: ShortcutOptions = {},
): () => void {
  const combo = parseShortcutExpr(expr);
  bindGlobal();

  // 冲突检测：同 combo 已注册则 console 警告
  if (registry.has(combo)) {
    console.warn(`[shortcut] 快捷键 ${combo} 已被注册，后注册将覆盖`);
  }

  registry.set(combo, { combo, handler, options });
  return () => {
    registry.delete(combo);
    if (registry.size === 0) {
      document.removeEventListener('keydown', handleKeyDown, true);
      bound = false;
    }
  };
}

/**
 * 取消所有快捷键（测试用 / 全局重置）
 */
export function resetAllShortcuts(): void {
  registry.clear();
  if (bound) {
    document.removeEventListener('keydown', handleKeyDown, true);
    bound = false;
  }
}

/**
 * 获取当前已注册的快捷键列表（调试用）
 */
export function listRegisteredShortcuts(): string[] {
  return [...registry.keys()];
}
