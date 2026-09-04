package com.njydsz.userinfo.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.userinfo.domain.entity.UserAccount;

/**
 * 用户账号 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_acct_user}，存储用户账号基本信息。 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD
 * 操作（insert/update/selectById/selectList/deleteById 等）。
 *
 * <p><b>自定义 SQL 说明：</b>本 Mapper 仅保留两处<b>必须原子化</b>的登录安全操作 （失败计数自增、成功态重置），以消除 {@code
 * read-modify-write} 并发竞态； 其余查询通过 Service 层使用 MyBatis-Plus 的 {@code LambdaQueryWrapper} 构造，避免 XML
 * 维护成本。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_username} — 用户名唯一索引
 *   <li>{@code idx_phone} — 手机号查询索引
 *   <li>{@code idx_email} — 邮箱查询索引
 *   <li>{@code idx_dept_id} — 部门查询索引（按部门查用户）
 *   <li>{@code idx_tenant_status} — 租户+状态复合索引（多租户隔离）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有自定义 SQL 均显式追加 {@code deleted = 0} 条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.domain.entity.UserAccount 用户实体
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

  /**
   * 原子递增登录失败次数，并在达到阈值时同步设置账号锁定时间。
   *
   * <p>通过单条 SQL 完成「自增 + 条件锁定」，避免业务层先读后写的并发竞态 （并发失败时计数丢失、锁定阈值无法达成的安全漏洞）。
   *
   * <p><b>数据库兼容性：</b>锁定时间戳由 Service 层预计算后传入（{@code lockUntil}）， 避免在 SQL 中使用数据库特定的 INTERVAL 语法（如
   * PostgreSQL {@code INTERVAL '1 minute'} vs MySQL {@code DATE_ADD}），实现数据库无关。
   *
   * @param id 用户 ID
   * @param threshold 锁定阈值（登录失败次数）
   * @param lockUntil 账号锁定到期时间（由 Service 层根据 lockDurationMinutes 计算后传入）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_acct_user
            SET login_fail_count = login_fail_count + 1,
                locked_until = CASE
                    WHEN login_fail_count + 1 >= #{threshold}
                        THEN #{lockUntil}
                    ELSE locked_until
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int increaseLoginFailCount(
      @Param("id") String id,
      @Param("threshold") int threshold,
      @Param("lockUntil") LocalDateTime lockUntil);

  /**
   * 原子重置登录成功状态：清零失败计数、清除锁定时间、记录最近登录信息。
   *
   * <p>与 {@link #increaseLoginFailCount} 配套，保证成功/失败两条路径均无竞态。
   *
   * @param id 用户 ID
   * @param loginIp 最近登录 IP（可为 null）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_acct_user
            SET login_fail_count = 0,
                locked_until = NULL,
                last_login_at = CURRENT_TIMESTAMP,
                last_login_ip = #{loginIp},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int resetLoginSuccess(@Param("id") String id, @Param("loginIp") String loginIp);

  /**
   * 批量启用用户账号（P1-3：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 列表
   * @return 影响行数
   */
  @Update(
      """
            <script>
            UPDATE ydsz_acct_user
            SET status = '1', updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchEnableByIds(@Param("ids") List<String> ids);

  /**
   * 批量禁用用户账号（P1-3：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 列表
   * @return 影响行数
   */
  @Update(
      """
            <script>
            UPDATE ydsz_acct_user
            SET status = '0', updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchDisableByIds(@Param("ids") List<String> ids);

  /**
   * 批量逻辑删除用户账号（P1-3：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 列表
   * @return 影响行数
   */
  @Update(
      """
            <script>
            UPDATE ydsz_acct_user
            SET deleted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchDeleteByIds(@Param("ids") List<String> ids);

  /**
   * 原子更新用户密码并重置登录失败计数/锁定状态。
   *
   * <p>专用于密码修改场景（自助注册、找回密码、管理员重置），通过单条 SQL 完成密码更新与状态重置，避免并发竞态。
   *
   * @param id 用户 ID
   * @param newPasswordHash 新密码哈希（已加密）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_acct_user
            SET password = #{newPasswordHash},
                login_fail_count = 0,
                locked_until = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int updatePasswordAndResetFailCount(
      @Param("id") String id, @Param("newPasswordHash") String newPasswordHash);

  /**
   * 原子更新账号封禁字段。
   *
   * <p>通过单条 SQL 完成封禁类型、原因、到期时间、操作人、操作时间的更新，避免并发竞态。
   *
   * @param id 用户 ID
   * @param banType 封禁类型（TEMPORARY/PERMANENT/null）
   * @param banReason 封禁原因（null 表示清除）
   * @param banExpireAt 封禁到期时间（null 表示清除）
   * @param bannedBy 操作人标识
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_acct_user
            SET ban_type = #{banType},
                ban_reason = #{banReason},
                ban_expire_at = #{banExpireAt},
                banned_by = #{bannedBy},
                banned_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int updateBanFields(
      @Param("id") String id,
      @Param("banType") String banType,
      @Param("banReason") String banReason,
      @Param("banExpireAt") LocalDateTime banExpireAt,
      @Param("bannedBy") String bannedBy);

  /**
   * 解锁账号：清除锁定时间、清零登录失败计数。
   *
   * <p>专用于自助解锁场景（用户通过验证码验证身份后解锁），原子完成：
   * 清除锁定时间、清零失败计数、更新更新时间。
   *
   * @param id 用户 ID
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_acct_user
            SET locked_until = NULL,
                login_fail_count = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int unlockAccount(@Param("id") String id);
}
