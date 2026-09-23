package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.workflow.domain.entity.FlowIdempotent;
import com.njydsz.workflow.domain.repository.FlowIdempotentRepository;
import com.njydsz.workflow.infra.mapper.FlowIdempotentMapper;

/**
 * 工作流幂等记录仓储实现。
 *
 * <p>基于 FlowIdempotentMapper 提供幂等记录的持久化操作。唯一约束 (scope, keyHash)
 * 由数据库层保证（uk_ydsz_flow_idempotent_scope_hash 部分索引），冲突通过 DuplicateKeyException 处理。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FlowIdempotentRepositoryImpl implements FlowIdempotentRepository {

  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_PROCESSING = "PROCESSING";

  private final FlowIdempotentMapper mapper;

  @Override
  public FlowIdempotent tryInsert(FlowIdempotent record) {
    if (record == null) {
      return null;
    }
    if (record.getCreatedAt() == null) {
      record.setCreatedAt(LocalDateTime.now());
    }
    if (record.getUpdatedAt() == null) {
      record.setUpdatedAt(LocalDateTime.now());
    }
    if (record.getStatus() == null) {
      record.setStatus(STATUS_PROCESSING);
    }
    if (record.getRetryCount() == null) {
      record.setRetryCount(0);
    }
    if (record.getTtlAt() == null) {
      record.setTtlAt(LocalDateTime.now().plusDays(7));
    }
    try {
      mapper.insert(record);
      return record;
    } catch (DuplicateKeyException e) {
      // 唯一约束冲突：若已 SUCCESS 则返回 null（调用方应取缓存结果）；
      // 若 PROCESSING 也返回 null（调用方应等待或降级）
      log.debug("[FlowIdempotent] 幂等键冲突: scope={} keyHash={}", record.getScope(), record.getKeyHash());
      return null;
    }
  }

  @Override
  public void markSuccess(String scope, String keyHash, String resultData) {
    LambdaUpdateWrapper<FlowIdempotent> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(FlowIdempotent::getScope, scope)
        .eq(FlowIdempotent::getKeyHash, keyHash)
        .set(FlowIdempotent::getStatus, STATUS_SUCCESS)
        .set(FlowIdempotent::getResultData, resultData)
        .set(FlowIdempotent::getErrorMessage, null)
        .set(FlowIdempotent::getUpdatedAt, LocalDateTime.now());

    FlowIdempotent entity = new FlowIdempotent();
    mapper.update(entity, wrapper);
  }

  @Override
  public void markFailed(String scope, String keyHash, String errorMessage) {
    LambdaUpdateWrapper<FlowIdempotent> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(FlowIdempotent::getScope, scope)
        .eq(FlowIdempotent::getKeyHash, keyHash)
        .set(FlowIdempotent::getStatus, "FAILED")
        .set(FlowIdempotent::getErrorMessage, errorMessage)
        .set(FlowIdempotent::getRetryCount, Objects.requireNonNullElse(
            mapper.selectOne(
                new LambdaQueryWrapper<FlowIdempotent>()
                    .eq(FlowIdempotent::getScope, scope)
                    .eq(FlowIdempotent::getKeyHash, keyHash)
                    .select(FlowIdempotent::getRetryCount)).getRetryCount(), 0) + 1)
        .set(FlowIdempotent::getUpdatedAt, LocalDateTime.now());

    FlowIdempotent entity = new FlowIdempotent();
    mapper.update(entity, wrapper);
  }

  @Override
  public FlowIdempotent findSuccess(String scope, String keyHash) {
    return mapper.selectOne(
        new LambdaQueryWrapper<FlowIdempotent>()
            .eq(FlowIdempotent::getScope, scope)
            .eq(FlowIdempotent::getKeyHash, keyHash)
            .eq(FlowIdempotent::getStatus, STATUS_SUCCESS));
  }

  @Override
  public int purgeExpired(LocalDateTime ttlBefore) {
    return mapper.delete(
        new LambdaQueryWrapper<FlowIdempotent>()
            .eq(FlowIdempotent::getStatus, STATUS_SUCCESS)
            .le(FlowIdempotent::getTtlAt, ttlBefore));
  }
}
