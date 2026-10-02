package com.njydsz.workflow.infra.repository;

import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.entity.FlowIdempotent;
import com.njydsz.workflow.domain.repository.FlowIdempotentRepository;
import com.njydsz.workflow.infra.mapper.FlowIdempotentMapper;

/**
 * 工作流幂等记录仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowIdempotentRepository} 接口，封装 FlowIdempotentMapper 数据访问细节。
 *
 * <p><b>分层定位：</b>依赖方向为 infra → domain（符合 DDD 依赖倒置原则）。
 *
 * @author ydsz-team
 * @since 26.10.02
 */
@Repository
@RequiredArgsConstructor
public class FlowIdempotentRepositoryImpl implements FlowIdempotentRepository {

  private final FlowIdempotentMapper idempotentMapper;

  /** {@inheritDoc} */
  @Override
  public FlowIdempotent save(FlowIdempotent entity) {
    idempotentMapper.insert(entity);
    return entity;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<FlowIdempotent> findById(String id) {
    return Optional.ofNullable(idempotentMapper.selectById(id));
  }

  /** {@inheritDoc} */
  @Override
  public Optional<FlowIdempotent> findByScopeAndKeyHash(String scope, String keyHash) {
    return Optional.ofNullable(
        idempotentMapper.selectOne(
            new LambdaQueryWrapper<FlowIdempotent>()
                .eq(FlowIdempotent::getScope, scope)
                .eq(FlowIdempotent::getKeyHash, keyHash)
        )
    );
  }

  /** {@inheritDoc} */
  @Override
  public FlowIdempotent update(FlowIdempotent entity) {
    idempotentMapper.updateById(entity);
    return entity;
  }

  /** {@inheritDoc} */
  @Override
  public void deleteById(String id) {
    idempotentMapper.deleteById(id);
  }
}
