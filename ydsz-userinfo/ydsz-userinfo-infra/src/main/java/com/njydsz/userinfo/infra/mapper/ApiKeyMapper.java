package com.njydsz.userinfo.infra.mapper;

import java.time.LocalDateTime;
import java.util.Collection;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.userinfo.domain.entity.ApiKey;

/**
 * API Key Mapper 接口。
 *
 * <p>对应数据表 {@code ydsz_auth_apikey}。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>{@code uk_api_key_hash} — API Key 哈希唯一索引（认证验证）</li>
 *   <li>{@code idx_user_id} — 用户 ID 索引（按用户查询 Key 列表）</li>
 *   <li>{@code idx_expire_at} — 过期时间索引（定时任务清理）</li>
 *   <li>{@code idx_enabled} — 启用状态索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

  /**
   * 更新最后使用时间。
   *
   * @param id 主键 ID
   * @param lastUsedAt 使用时间
   * @return 影响行数
   */
  @Update("UPDATE ydsz_auth_apikey SET last_used_at = #{lastUsedAt}, updated_at = NOW() "
      + "WHERE id = #{id} AND deleted = false")
  int updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);

  /**
   * 批量撤销（软删除）API Key。
   *
   * @param ids ID 集合
   * @return 影响行数
   */
  int revokeByIds(@Param("ids") Collection<Long> ids);

  /**
   * 删除已过期的 Key（物理删除，定时任务清理）。
   *
   * @param now 当前时间
   * @return 影响行数
   */
  @Update("DELETE FROM ydsz_auth_apikey WHERE expire_at IS NOT NULL AND expire_at < #{now}")
  int deleteExpired(@Param("now") LocalDateTime now);
}
