package com.njydsz.message.domain.identity;

/**
 * 唯一 ID 生成器（接口定义在 domain 层，实现由 infra 层提供）。
 *
 * <p>业务层通过本接口生成 Snowflake 风格唯一 ID，避免直接依赖 MyBatis-Plus {@code IdWorker} 等具体实现，
 * 符合 DDD 依赖方向（domain ↛ 第三方库）。
 *
 * <p>默认实现类 {@code MpIdGenerator} 通过 Snowflake 算法生成 64 位 Long 并以字符串形式返回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface IdGenerator {

  /**
   * 生成字符串形式唯一 ID。
   *
   * @return 雪花 ID 字符串（19 位数字）
   */
  String nextId();

  /**
   * 生成原始 Long 形式唯一 ID。
   *
   * @return 雪花 ID 值
   */
  long nextLong();
}
