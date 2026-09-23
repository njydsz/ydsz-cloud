package com.njydsz.agent.server.asynctask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 异步任务执行器注册表。
 *
 * <p>管理所有 {@link AsyncTaskExecutor} 实现的路由，根据任务类型分发到对应执行器。
 * 启动时自动收集 Spring 容器中所有执行器 Bean 并建立索引。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Component
public class AsyncTaskExecutorRegistry {

  private final Map<String, AsyncTaskExecutor> executorMap;

  /**
   * 构造执行器注册表（Spring 自动注入所有 AsyncTaskExecutor 实现）。
   *
   * @param executors 所有异步任务执行器实现列表
   */
  public AsyncTaskExecutorRegistry(List<AsyncTaskExecutor> executors) {
    this.executorMap = executors.stream()
        .collect(Collectors.toMap(
            e -> e.supportedType().toUpperCase(),
            Function.identity(),
            (existing, replacement) -> {
              throw new IllegalStateException(
                  "发现重复的任务执行器类型: " + existing.supportedType()
                      + " — " + existing.getClass().getName()
                      + " 与 " + replacement.getClass().getName());
            }));
  }

  /**
   * 根据任务类型查找执行器。
   *
   * @param taskType 任务类型编码
   * @return 匹配的执行器，未找到返回 empty
   */
  public Optional<AsyncTaskExecutor> findExecutor(String taskType) {
    if (taskType == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(executorMap.get(taskType.toUpperCase()));
  }
}
