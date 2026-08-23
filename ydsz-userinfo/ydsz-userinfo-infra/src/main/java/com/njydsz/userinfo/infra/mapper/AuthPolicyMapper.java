package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.userinfo.infra.entity.AuthPolicyDO;

/**
 * 认证策略 Mapper 接口（P3-1）。
 *
 * <p>对应数据表 {@code ydsz_auth_policy}。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_tenant_id} — 租户 ID 唯一索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface AuthPolicyMapper extends BaseMapper<AuthPolicyDO> {

  /**
   * 根据租户 ID 查询认证策略。
   *
   * <p>传入 null 或空字符串查询全局默认策略。
   *
   * @param tenantId 租户 ID；null 表示查询全局默认
   * @return 认证策略 DO；不存在返回 null
   */
  @Select("SELECT * FROM ydsz_auth_policy "
      + "WHERE (tenant_id = #{tenantId} OR (#{tenantId} IS NULL AND tenant_id IS NULL)) "
      + "AND deleted = 0 LIMIT 1")
  AuthPolicyDO selectByTenantId(@Param("tenantId") String tenantId);
}
