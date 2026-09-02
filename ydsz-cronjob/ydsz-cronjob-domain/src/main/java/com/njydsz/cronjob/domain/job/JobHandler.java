package com.njydsz.cronjob.domain.job;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 任务处理器接口（cronjob 模块调度框架的核心契约）。
 *
 * <p>所有自定义任务处理器实现本接口的 {@link #execute(String)} 方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 由 common-domain 迁入 cronjob-domain，job 框架与 cronjob 调度引擎统一归属
 */
public interface JobHandler {

  /**
   * 执行任务。
   *
   * @param paramsJson 任务参数 JSON 字符串（可空）
   * @return 执行结果（可空，用于日志记录和回写任务日志）
   * @throws JobExecutionException 执行异常
   */
  Object execute(String paramsJson) throws JobExecutionException;

  /**
   * 执行分片任务。
   *
   * @param paramsJson 任务参数 JSON 字符串（可空）
   * @param ctx 分片上下文
   * @return 执行结果（可空）
   * @throws JobExecutionException 执行异常
   */
  default Object execute(String paramsJson, ShardingContext ctx) throws JobExecutionException {
    return execute(paramsJson);
  }

  /**
   * 幂等键（可选覆盖）。
   *
   * <p>调度框架在需要保证"同一任务不重复执行"时可基于该键做去重/加锁。 默认实现：对参数 JSON 做 SHA-256 摘要；实现方如需更精确的幂等语义
   * （如忽略无意义的参数噪声），可覆写此方法返回业务幂等键。
   *
   * @param paramsJson 任务参数 JSON 字符串（可空）
   * @return 幂等键；参数为空时返回固定常量
   * @since 26.09.01
   */
  default String idempotentKey(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return JobHandler.class.getName() + ":empty";
    }
    String hash = DigestUtils.sha256Hex(paramsJson);
    return getClass().getName() + ":" + hash;
  }

  /**
   * 最大执行尝试次数（可选覆盖）。
   *
   * <p>调度框架在任务失败时可依据该值决定重试次数。默认 1（不重试）。
   *
   * @return 最大尝试次数，应大于等于 1
   * @since 26.09.01
   */
  default int maxAttempts() {
    return 1;
  }

  /**
   * 失败重试间隔（可选覆盖）。
   *
   * <p>两次执行尝试之间的基础等待毫秒数。默认 0（立即重试）。 调度框架实现方可在该值基础上叠加指数退避或抖动。
   *
   * @return 重试间隔毫秒数，应大于等于 0
   * @since 26.09.01
   */
  default long retryDelayMillis() {
    return 0L;
  }
}
