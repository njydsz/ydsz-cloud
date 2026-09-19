package com.njydsz.common.redis.service.ops;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis Pipeline 操作的工厂类
 *
 * <p>{@link RedisPipelineOps} 实例不能在 Spring 容器中直接单例注册（因为需依赖
 * 每次 Pipeline 回调中的 {@link RedisConnection}），因此通过本工厂在回调内部按需构造。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Resource
 * private RedisPipelineOpsFactory pipelineOpsFactory;
 *
 * List<?> results = redisTemplate.executePipelined(connection -> {
 *     RedisPipelineOps ops = pipelineOpsFactory.create(connection);
 *     ops.setString("key1", "value1");
 *     ops.setString("key2", "value2", 60);
 *     return null;
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class RedisPipelineOpsFactory {

  private final RedisTemplate<String, Object> redisTemplate;

  public RedisPipelineOpsFactory(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 为给定的 Redis 连接创建 Pipeline 操作实例
   *
   * @param connection Pipeline 回调中提供的 Redis 连接
   * @return Pipeline 操作实例
   */
  public RedisPipelineOps create(RedisConnection connection) {
    return new RedisPipelineOpsImpl(redisTemplate, connection);
  }
}
