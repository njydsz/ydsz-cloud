/**
 * 轻量级日志工具 — 控制台噪音收敛
 *
 * 设计目标：
 *   1. 生产环境默认只输出 warn/error，开发环境输出全部
 *   2. 支持按模块名过滤（debug 风格）：`localStorage.setItem('ydsz:debug', 'MicroKernel*,ImportMap*')`
 *   3. 禁用的日志调用为零开销（编译期或运行期 no-op），不影响性能
 *   4. API 与 console 对齐，迁移成本低
 *
 * 使用方式：
 *   import { createLogger } from '@ydsz-core/shared/utils';
 *   const logger = createLogger('MicroKernel');
 *   logger.info('Started with', apps.length, 'apps');  // 开发环境输出
 *   logger.warn('Already started');                    // 始终输出
 *
 * 调试过滤：
 *   localStorage.setItem('ydsz:debug', 'MicroKernel:*')  // 仅 MicroKernel 模块
 *   localStorage.setItem('ydsz:debug', '*')                // 全部模块 debug 级别
 *   localStorage.setItem('ydsz:debug', '-MicroKernel:*')  // 排除 MicroKernel
 *
 * @path comm/@core/base/shared/src/utils/logger.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** 日志级别（数值越大，输出越详细） */
export enum LogLevel {
  /** 仅 error */
  ERROR = 0,
  /** error + warn */
  WARN = 1,
  /** error + warn + info（生产默认） */
  INFO = 2,
  /** 全部（开发默认） */
  DEBUG = 3,
}

/** 日志方法签名（与 console 对齐） */
export interface Logger {
  debug(...args: unknown[]): void;
  info(...args: unknown[]): void;
  warn(...args: unknown[]): void;
  error(...args: unknown[]): void;
}

/** 模块级配置 */
interface LoggerConfig {
  /** 全局最低日志级别（低于此级别的日志被丢弃） */
  minLevel: LogLevel;
  /** 调试过滤模式（来自 localStorage），null 表示无过滤 */
  debugFilter: Set<string> | null;
  /** 调试排除模式（来自 localStorage），null 表示无排除 */
  debugExclude: Set<string> | null;
}

/** 全局配置单例 */
let globalConfig: LoggerConfig = {
  minLevel: LogLevel.INFO,
  debugFilter: null,
  debugExclude: null,
};

/** 是否已初始化（避免重复读取 localStorage） */
let initialized = false;

/** 默认前缀（与 namespace 分隔） */
const PREFIX_BRAND = '[ydsz]';

/**
 * 初始化日志配置。
 *
 * 在应用 bootstrap 中调用一次，传入当前环境信息。
 * 库内部不直接读 import.meta.env，由应用注入保证同源。
 */
export function initLogger(options?: {
  isDev?: boolean;
  minLevel?: LogLevel;
}): void {
  if (initialized) return;
  initialized = true;

  const isDev = options?.isDev ?? false;
  globalConfig.minLevel = options?.minLevel ?? (isDev ? LogLevel.DEBUG : LogLevel.INFO);

  // 读取调试过滤（仅在浏览器环境）
  if (typeof localStorage !== 'undefined') {
    const filter = localStorage.getItem('ydsz:debug');
    if (filter) {
      const { include, exclude } = parseDebugFilter(filter);
      globalConfig.debugFilter = include;
      globalConfig.debugExclude = exclude;
    }
  }
}

/**
 * 解析 debug 过滤字符串。
 *
 * 格式：`Module1,Module2:*,-Module3:*`
 *   - `Module1` 精确匹配
 *   - `Module1:*` 前缀匹配
 *   - `-Module1:*` 排除
 */
function parseDebugFilter(filter: string): {
  include: Set<string>;
  exclude: Set<string>;
} {
  const include = new Set<string>();
  const exclude = new Set<string>();
  for (const part of filter.split(',')) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    if (trimmed.startsWith('-')) {
      exclude.add(trimmed.slice(1));
    } else {
      include.add(trimmed);
    }
  }
  return { include, exclude };
}

/** 检查模块是否在调试过滤范围内 */
function isModuleEnabled(module: string): boolean {
  const { debugFilter, debugExclude } = globalConfig;
  // 排除优先
  if (debugExclude) {
    for (const pattern of debugExclude) {
      if (matchModule(pattern, module)) return false;
    }
  }
  // 无 include 表示全部启用
  if (!debugFilter || debugFilter.size === 0) return true;
  for (const pattern of debugFilter) {
    if (matchModule(pattern, module)) return true;
  }
  return false;
}

/** 模块名匹配：支持 `*` 通配符与 `Module:*` 前缀匹配 */
function matchModule(pattern: string, module: string): boolean {
  if (pattern === '*') return true;
  if (pattern === module) return true;
  // `Module:*` 等价于 `Module` 开头
  if (pattern.endsWith(':*')) {
    return module.startsWith(pattern.slice(0, -2));
  }
  // 通用通配
  if (pattern.includes('*')) {
    const regex = new RegExp(
      `^${pattern.replace(/\./g, '\\.').replace(/\*/g, '.*')}$`,
    );
    return regex.test(module);
  }
  return false;
}

/** 创建带模块前缀的日志器 */
export function createLogger(module: string): Logger {
  const prefix = `${PREFIX_BRAND}[${module}]`;

  // 预绑定方法，避免每次调用都重新创建
  return {
    debug(...args: unknown[]): void {
      if (!initialized) initLogger();
      if (globalConfig.minLevel < LogLevel.DEBUG) return;
      if (!isModuleEnabled(module)) return;
      console.debug(prefix, ...args);
    },
    info(...args: unknown[]): void {
      if (!initialized) initLogger();
      if (globalConfig.minLevel < LogLevel.INFO) return;
      if (!isModuleEnabled(module)) return;
      console.info(prefix, ...args);
    },
    warn(...args: unknown[]): void {
      if (!initialized) initLogger();
      if (globalConfig.minLevel < LogLevel.WARN) return;
      // warn 不受 debugFilter 控制（始终输出）
      console.warn(prefix, ...args);
    },
    error(...args: unknown[]): void {
      if (!initialized) initLogger();
      // error 始终输出
      console.error(prefix, ...args);
    },
  };
}

/**
 * 设置全局日志级别（运行期动态调整）。
 *
 * 供调试面板或 URL 参数使用：
 *   `?logLevel=debug` → LogLevel.DEBUG
 */
export function setLogLevel(level: LogLevel): void {
  globalConfig.minLevel = level;
}

/** 获取当前全局日志级别 */
export function getLogLevel(): LogLevel {
  return globalConfig.minLevel;
}

/**
 * 重置日志配置（测试用）。
 */
export function resetLogger(): void {
  initialized = false;
  globalConfig = {
    minLevel: LogLevel.INFO,
    debugFilter: null,
    debugExclude: null,
  };
}
