/**
 * spinner 配置模块
 *
 * @path conf\node-utils\src\spinner.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Ora } from 'ora';

import ora from 'ora';

/** spinner 配置项 */
interface SpinnerOptions {
  /** 任务失败时终端展示的文本，缺省为 'Failed!' */
  failedText?: string;
  /** 任务成功时终端展示的文本，缺省为 'Success!' */
  successText?: string;
  /** 任务开始时展示的加载标题 */
  title: string;
}
/**
 * 用终端 loading 动画包裹异步任务执行，并统一处理成功/失败反馈。
 *
 * 在回调执行期间展示 title 的旋转动画；回调成功则标记成功文本，
 * 失败则标记失败文本并重新抛出错误，finally 中确保动画停止，避免进程挂起。
 *
 * @param options - spinner 文案配置（标题、成功/失败文本）
 * @param callback - 被包裹的异步任务
 * @returns 回调的返回值
 * @throws 回调抛出的任何错误（已标记为失败，仍向上传递）
 */
export async function spinner<T>(
  { failedText, successText, title }: SpinnerOptions,
  callback: () => Promise<T>,
): Promise<T> {
  const loading: Ora = ora(title).start();

  try {
    const result = await callback();
    loading.succeed(successText || 'Success!');
    return result;
  } catch (error) {
    loading.fail(failedText || 'Failed!');
    throw error;
  } finally {
    loading.stop();
  }
}
