package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.userinfo.domain.entity.UserAccount;

/**
 * 用户账号 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_user_account}，存储用户账号基本信息。 继承 MyBatis-Plus {@code BaseMapper} 提供标准 CRUD
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
 * @since 1.0.0
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
   * @param id 用户 ID
   * @param threshold 锁定阈值（登录失败次数）
   * @param lockMinutes 锁定时长（分钟）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  @Update(
      """
            UPDATE ydsz_user_account
            SET login_fail_count = login_fail_count + 1,
                locked_until = CASE
                    WHEN login_fail_count + 1 >= #{threshold}
                        THEN CURRENT_TIMESTAMP + (#{lockMinutes} * INTERVAL '1 minute')
                    ELSE locked_until
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
  int increaseLoginFailCount(
      @Param("id") String id,
      @Param("threshold") int threshold,
      @Param("lockMinutes") int lockMinutes);

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
            UPDATE ydsz_user_account
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
            UPDATE ydsz_user_account
            SET status = '1', updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchEnableByIds(@Param("ids") java.util.List<String> ids);

  /**
   * 批量禁用用户账号（P1-3：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 列表
   * @return 影响行数
   */
  @Update(
      """
            <script>
            UPDATE ydsz_user_account
            SET status = '0', updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchDisableByIds(@Param("ids") java.util.List<String> ids);

  /**
   * 批量逻辑删除用户账号（P1-3：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 列表
   * @return 影响行数
   */
  @Update(
      """
            <script>
            UPDATE ydsz_user_account
            SET deleted = 1, updated_at = CURRENT_TIMESTAMP
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
  int batchDeleteByIds(@Param("ids") java.util.List<String> ids);
}
