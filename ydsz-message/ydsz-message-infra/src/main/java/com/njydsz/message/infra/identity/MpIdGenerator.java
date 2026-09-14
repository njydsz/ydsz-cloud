package com.njydsz.message.infra.identity;

import org.springframework.stereotype.Component;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.identity.IdGenerator;

/**
 * 基于 ydsz-common-util Snowflake 的唯一 ID 生成器实现（ADR-008 整改）。
 *
 * <p>委托 common-util {@link SnowflakeIdGenerator}（含 WorkerId 分配链
 * {@code IpHashWorkerIdAllocator} / {@code PodOrdinalWorkerIdAllocator}、
 * 时钟回拨保护与 {@code SnowflakeHealthIndicator} 健康监控）。
 * 此前实现直连 MyBatis-Plus {@code IdWorker}（独立雪花实现，MAC 地址 workerId，
 * 无健康监控），与 common-util 双轨，违反 ADR-008《分布式 ID 生成场景边界》，
 * 现已收敛至平台唯一入口。
 *
 * <p>domain 层 {@link IdGenerator} 接口保留（DDD 依赖倒置合理），仅替换实现体。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.14 实现体改委托 common-util SnowflakeIdGenerator（ADR-008 整改）
 */
@Component
public class MpIdGenerator implements IdGenerator {

  /** common-util 统一雪花 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构造 ID 生成器。
   *
   * @param snowflakeIdGenerator common-util 统一雪花 ID 生成器
   */
  public MpIdGenerator(SnowflakeIdGenerator snowflakeIdGenerator) {
    this.snowflakeIdGenerator = snowflakeIdGenerator;
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 common-util 雪花生成器，返回 19 位数字字符串。
   */
  @Override
  public String nextId() {
    return String.valueOf(snowflakeIdGenerator.nextId());
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 common-util 雪花生成器，返回原始 Long 值。
   */
  @Override
  public long nextLong() {
    return snowflakeIdGenerator.nextId();
  }
}
