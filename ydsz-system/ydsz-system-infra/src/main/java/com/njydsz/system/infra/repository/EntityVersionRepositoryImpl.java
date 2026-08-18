package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.entity.EntityVersion;
import com.njydsz.system.infra.mapper.EntityVersionMapper;
import com.njydsz.system.domain.repository.EntityVersionRepository;

/**
 * 统一实体版本仓储实现（Infra 层）。
 *
 * <p>实现 {@link EntityVersionRepository} 接口，封装 {@link EntityVersionMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>返回领域实体，由 Service 层负责转换为 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class EntityVersionRepositoryImpl implements EntityVersionRepository {

  private final EntityVersionMapper entityVersionMapper;

  @Override
  public List<EntityVersion> findByTypeAndKey(String resourceType, String resourceKey) {
    return entityVersionMapper.listByResourceTypeAndKey(resourceType, resourceKey);
  }

  @Override
  public Optional<EntityVersion> findByTypeAndKeyAndVersion(
      String resourceType, String resourceKey, String version) {
    return Optional.ofNullable(
        entityVersionMapper.selectByTypeAndKeyAndVersion(resourceType, resourceKey, version));
  }

  @Override
  public EntityVersion save(EntityVersion entity) {
    entityVersionMapper.insert(entity);
    return entity;
  }
}
