/**
 * 统一重试策略工具 — 指数退避 + 随机抖动
 *
 * 为项目提供一致的重试逻辑，避免各处实现不一致导致的惊群效应。
 *
 * 使用方式：
 * ```ts
 * const result = await retryOperation(
 *   () => fetchData(),
 *   { maxRetries: 3, baseDelay: 1000, backoff: 'exponential', jitter: 0.25 }
 * );
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
 */

/** 重试配置选项 */
export interface RetryOptions {
  /** 最大重试次数，默认 3 */
  maxRetries?: number;
  /** 基础延迟（ms），默认 1000 */
  baseDelay?: number;
  /** 退避策略：'fixed'（固定延迟）| 'exponential'（指数退避），默认 'exponential' */
  backoff?: 'exponential' | 'fixed';
  /** 随机抖动因子（0~1），默认 0.25，用于避免惊群效应 */
  jitter?: number;
  /** 重试条件判断函数，返回 true 时触发重试 */
  retryCondition?: (error: unknown, attempt: number) => boolean;
  /** 重试前回调（可用于日志记录） */
  onRetry?: (error: unknown, attempt: number, delay: number) => void;
}

/**
 * 计算重试延迟（指数退避 + 随机抖动）
 *
 * @param attempt - 当前重试次数（从 0 开始）
 * @param options - 重试配置
 * @returns 延迟时间（ms）
 */
export function calculateRetryDelay(
  attempt: number,
  options: RetryOptions = {},
): number {
  const {
    baseDelay = 1000,
    backoff = 'exponential',
    jitter = 0.25,
  } = options;

  // 固定延迟模式
  if (backoff === 'fixed') {
    return baseDelay;
  }

  // 指数退避：baseDelay * 2^attempt
  const delay = baseDelay * Math.pow(2, attempt);

  // 添加随机抖动：±jitter 比例
  // 例如 jitter=0.25 时，延迟在 [0.75*delay, 1.25*delay] 之间
  const jitterRange = delay * jitter;
  const jitterValue = jitterRange * (Math.random() * 2 - 1);
  const finalDelay = Math.max(0, delay + jitterValue);

  return Math.round(finalDelay);
}

/**
 * 执行带重试的操作
 *
 * @param operation - 要执行的操作（返回 Promise）
 * @param options - 重试配置
 * @returns 操作结果
 * @throws 如果所有重试都失败，抛出最后一次错误
 */
export async function retryOperation<T>(
  operation: () => Promise<T>,
  options: RetryOptions = {},
): Promise<T> {
  const {
    maxRetries = 3,
    retryCondition = () => true,
    onRetry,
  } = options;

  let lastError: unknown;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;

      // 最后一次尝试或不符合重试条件，直接抛出
      if (attempt >= maxRetries || !retryCondition(error, attempt)) {
        throw error;
      }

      // 计算延迟并等待
      const delay = calculateRetryDelay(attempt, options);

      // 回调通知
      if (onRetry) {
        onRetry(error, attempt, delay);
      }

      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }

  // 理论上不会到达这里，但 TypeScript 需要
  throw lastError;
}

/**
 * 延迟执行（Promise 包装的 setTimeout）
 *
 * @param ms - 延迟时间（ms）
 * @returns Promise
 */
export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 带超时的 Promise 执行
 *
 * @param promise - 要执行的 Promise
 * @param timeoutMs - 超时时间（ms）
 * @param timeoutMessage - 超时错误消息
 * @returns Promise 结果
 * @throws 如果超时，抛出 TimeoutError
 */
export async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  timeoutMessage = 'Operation timed out',
): Promise<T> {
  const timeoutPromise = new Promise<never>((_, reject) => {
    setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
  });

  return Promise.race([promise, timeoutPromise]);
}
