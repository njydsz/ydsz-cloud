package com.njydsz.userinfo.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.userinfo.domain.entity.SocialAccount;

/**
 * 社交账号绑定 Mapper 接口。
 *
 * <p>对应数据表 {@code ydsz_auth_social_account}，存储用户与第三方社交平台的绑定关系。
 * 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD 操作。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_platform_open_id} — 平台+openId 唯一索引</li>
 *   <li>{@code idx_user_id} — 用户 ID 索引</li>
 * </ul>
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有自定义 SQL 均显式追加 {@code deleted = 0} 条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.domain.entity.SocialAccount 社交账号绑定实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface SocialAccountMapper extends BaseMapper<SocialAccount> {

  /**
   * 根据平台标识和 openId 查询社交账号绑定。
   *
   * @param platform 平台标识
   * @param openId 平台用户唯一标识
   * @return 社交账号绑定实体；不存在返回 null
   */
  @Select(
      "SELECT * FROM ydsz_auth_social_account "
          + "WHERE platform = #{platform} AND open_id = #{openId} AND deleted = 0")
  SocialAccount selectByPlatformAndOpenId(
      @Param("platform") String platform, @Param("openId") String openId);

  /**
   * 根据用户 ID 和平台标识查询社交账号绑定。
   *
   * @param userId 用户 ID
   * @param platform 平台标识
   * @return 社交账号绑定实体；不存在返回 null
   */
  @Select(
      "SELECT * FROM ydsz_auth_social_account "
          + "WHERE user_id = #{userId} AND platform = #{platform} AND deleted = 0")
  SocialAccount selectByUserIdAndPlatform(
      @Param("userId") String userId, @Param("platform") String platform);

  /**
   * 查询用户的所有社交账号绑定列表。
   *
   * @param userId 用户 ID
   * @return 社交账号绑定实体列表
   */
  @Select("SELECT * FROM ydsz_auth_social_account WHERE user_id = #{userId} AND deleted = 0")
  List<SocialAccount> selectByUserId(@Param("userId") String userId);

  /**
   * 逻辑删除用户在某平台的社交账号绑定。
   *
   * @param userId 用户 ID
   * @param platform 平台标识
   * @return 影响行数
   */
  @Delete(
      "UPDATE ydsz_auth_social_account SET deleted = 1, updated_at = CURRENT_TIMESTAMP "
          + "WHERE user_id = #{userId} AND platform = #{platform} AND deleted = 0")
  int logicDeleteByUserIdAndPlatform(
      @Param("userId") String userId, @Param("platform") String platform);
}
