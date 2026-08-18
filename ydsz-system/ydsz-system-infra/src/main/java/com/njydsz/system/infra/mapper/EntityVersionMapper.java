package com.njydsz.system.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.system.infra.entity.EntityVersion;

/**
 * 统一实体版本管理 Mapper
 *
 * <p>对应数据表 <code>ydsz_entity_version</code>，为 Config/Dict/Variable 提供统一的版本数据访问能力。
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see EntityVersion 实体版本实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface EntityVersionMapper extends BaseMapper<EntityVersion> {

  /**
   * 按资源类型 + 资源键查询版本历史（按生效时间倒序）
   *
   * <p>走 {@code idx_resource_type_key_version} 复合索引；返回该资源下所有有效版本（{@code deleted=0}），
   * 最新版本排首位。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @return 版本列表（按 {@code effective_date} 倒序）
   */
  @Select(
      "SELECT * FROM ydsz_entity_version WHERE resource_type = #{resourceType} "
          + "AND resource_key = #{resourceKey} AND deleted = 0 "
          + "ORDER BY effective_date DESC")
  List<EntityVersion> listByResourceTypeAndKey(
      @Param("resourceType") String resourceType, @Param("resourceKey") String resourceKey);

  /**
   * 按资源类型 + 资源键 + 版本号查询唯一版本
   *
   * @param resourceType 资源类型
   * @param resourceKey 资源唯一标识
   * @param version 版本号
   * @return 版本实体，不存在返回 null
   */
  @Select(
      "SELECT * FROM ydsz_entity_version WHERE resource_type = #{resourceType} "
          + "AND resource_key = #{resourceKey} AND version = #{version} "
          + "AND deleted = 0 LIMIT 1")
  EntityVersion selectByTypeAndKeyAndVersion(
      @Param("resourceType") String resourceType,
      @Param("resourceKey") String resourceKey,
      @Param("version") String version);
}
