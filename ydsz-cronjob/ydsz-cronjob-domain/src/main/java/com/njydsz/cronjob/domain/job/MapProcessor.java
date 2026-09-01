package com.njydsz.cronjob.domain.job;

/**
 * Map 任务处理器接口
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MapProcessor {

  /**
   * 处理任务
   *
   * @param ctx 任务上下文
   * @return 处理结果
   */
  ProcessResult process(MapContext ctx);
}
