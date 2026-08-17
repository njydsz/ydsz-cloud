package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.system.domain.entity.EntityVersion;

/**
 * 统一实体版本仓储接口（Infra 层契约）。
 *
 * <p>定义 Config/Dict/Variable 统一的版本数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link EntityVersion}），非 DTO / VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface EntityVersionRepository {

  /**
   * 按资源类型 + 资源键查询版本历史（按生效时间倒序）。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @return 版本列表（按生效时间倒序），无版本时返回空列表
   */
  List<EntityVersion> findByTypeAndKey(String resourceType, String resourceKey);

  /**
   * 按资源类型 + 资源键 + 版本号查询唯一版本。
   *
   * @param resourceType 资源类型
   * @param resourceKey 资源唯一标识
   * @param version 版本号
   * @return 版本实体；不存在返回 {@code Optional.empty()}
   */
  Optional<EntityVersion> findByTypeAndKeyAndVersion(
      String resourceType, String resourceKey, String version);

  /**
   * 保存版本记录。
   *
   * @param entity 版本实体
   * @return 保存后的实体（含生成的主键 ID）
   */
  EntityVersion save(EntityVersion entity);
}
