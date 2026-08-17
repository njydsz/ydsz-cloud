package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.EntityVersion;
import com.njydsz.system.infra.mapper.EntityVersionMapper;

/**
 * 统一实体版本仓储。
 *
 * <p>封装 EntityVersionMapper，提供 Config/Dict/Variable 统一的版本数据访问能力，
 * 遵循 DDD 分层架构，禁止 Service 层直接访问 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class EntityVersionRepository {

  private final EntityVersionMapper entityVersionMapper;

  /**
   * 按资源类型 + 资源键查询版本历史（按生效时间倒序）
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @return 版本列表（按生效时间倒序），无版本时返回空列表
   */
  public List<EntityVersion> findByTypeAndKey(String resourceType, String resourceKey) {
    return entityVersionMapper.listByResourceTypeAndKey(resourceType, resourceKey);
  }

  /**
   * 按资源类型 + 资源键 + 版本号查询唯一版本
   *
   * @param resourceType 资源类型
   * @param resourceKey 资源唯一标识
   * @param version 版本号
   * @return 版本实体
   */
  public Optional<EntityVersion> findByTypeAndKeyAndVersion(
      String resourceType, String resourceKey, String version) {
    return Optional.ofNullable(
        entityVersionMapper.selectByTypeAndKeyAndVersion(resourceType, resourceKey, version));
  }

  /**
   * 保存版本记录
   *
   * @param entity 版本实体
   * @return 保存后的实体（含生成的主键 ID）
   */
  public EntityVersion save(EntityVersion entity) {
    entityVersionMapper.insert(entity);
    return entity;
  }
}
