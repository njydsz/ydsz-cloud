package com.njydsz.system.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.system.domain.entity.VariableVersion;

/**
 * 系统变量版本管理 Mapper
 *
 * <p>对应数据表 <code>ydsz_variable_version</code>。
 *
 * <p>变量变更（save / updateById / removeById）生成新版本，支持回滚、对比、审计。
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.VariableVersion 变量版本实体
 * @see com.njydsz.system.server.service.VariableVersionService 变量版本 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface VariableVersionMapper extends BaseMapper<VariableVersion> {

  /**
   * 按变量键查询版本历史（按生效时间倒序）
   *
   * <p>走 {@code idx_resource_key_version} 复合索引；返回该 resourceKey 下所有有效版本（{@code deleted=0}）， 最新版本排首位。
   *
   * @param resourceKey 变量键
   * @return 版本列表（按 {@code effective_date} 倒序）
   */
  @Select(
      "SELECT * FROM ydsz_variable_version WHERE resource_key = #{resourceKey} AND deleted = 0 "
          + "ORDER BY effective_date DESC")
  List<VariableVersion> listByResourceKey(@Param("resourceKey") String resourceKey);

  /**
   * 按变量键 + 版本号查询唯一版本
   *
   * @param resourceKey 变量键
   * @param version 版本号
   * @return 版本实体，不存在返回 null
   */
  @Select(
      "SELECT * FROM ydsz_variable_version WHERE resource_key = #{resourceKey} "
          + "AND version = #{version} AND deleted = 0 LIMIT 1")
  VariableVersion selectByKeyAndVersion(
      @Param("resourceKey") String resourceKey, @Param("version") String version);
}
