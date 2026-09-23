package com.njydsz.agent.server.asynctask;

import java.util.function.Consumer;

import com.njydsz.agent.domain.asynctask.AsyncTask;

/**
 * 异步任务执行器接口。
 *
 * <p>定义特定任务类型的执行契约。各执行器负责从任务中提取输入、驱动实际业务逻辑、
 * 并通过 progressConsumer 实时回传进度（0-100）。
 *
 * <p>执行器应为无状态实现，可安全并发调用。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public interface AsyncTaskExecutor {

  /**
   * 获取此执行器支持的任务类型。
   *
   * @return 任务类型编码（与 {@link com.njydsz.agent.domain.asynctask.AsyncTaskType} 中的 code 一致）
   */
  String supportedType();

  /**
   * 执行任务。
   *
   * <p>实现说明：
   * <ul>
   *   <li>通过 progressConsumer.accept(percent) 回传实时进度（0-100）</li>
   *   <li>执行完成后通过 task.succeed(output) / task.fail(error) 标记终态</li>
   *   <li>业务异常应捕获并调用 task.fail()，不要向上抛出</li>
   * </ul>
   *
   * @param task            已认领的异步任务
   * @param progressConsumer 进度回调（0-100），可为 null
   */
  void execute(AsyncTask task, Consumer<Integer> progressConsumer);
}
