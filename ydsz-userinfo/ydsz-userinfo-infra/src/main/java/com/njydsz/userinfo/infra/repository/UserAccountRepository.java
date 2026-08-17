package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.userinfo.infra.entity.UserAccountDO;

/**
 * 用户账号 Repository 接口
 *
 * <p>封装用户账号表（{@code ydsz_user_account}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserAccountRepository {

  /**
   * 根据 ID 查询用户账号。
   *
   * @param id 用户 ID
   * @return 用户账号实体，不存在时返回 null
   */
  UserAccountDO findById(String id);

  /**
   * 根据用户名查询用户账号。
   *
   * @param username 用户名
   * @return 用户账号实体，不存在时返回 null
   */
  UserAccountDO findByUsername(String username);

  /**
   * 保存用户账号（插入或更新）。
   *
   * <p>当 entity 的 ID 为空时执行插入，否则执行更新。
   *
   * @param entity 用户账号实体
   * @return 保存后的实体
   */
  UserAccountDO save(UserAccountDO entity);

  /**
   * 插入用户账号。
   *
   * @param entity 用户账号实体
   * @return 插入后的实体
   */
  int insert(UserAccountDO entity);

  /**
   * 更新用户账号。
   *
   * @param entity 用户账号实体
   * @return 更新影响的行数
   */
  int updateById(UserAccountDO entity);

  /**
   * 根据 ID 删除用户账号（逻辑删除）。
   *
   * @param id 用户 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 分页查询用户账号。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<UserAccountDO> page(Page<UserAccountDO> page,
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAccountDO> wrapper);

  /**
   * 条件查询用户列表。
   *
   * @param wrapper 查询条件
   * @return 用户列表
   */
  List<UserAccountDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAccountDO> wrapper);

  /**
   * 批量根据 ID 查询用户账号。
   *
   * @param ids 用户 ID 集合
   * @return 用户账号列表
   */
  List<UserAccountDO> listByIds(java.util.Collection<String> ids);

  /**
   * 统计符合条件的用户数量。
   *
   * @param wrapper 查询条件
   * @return 用户数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAccountDO> wrapper);

  /**
   * 判断用户名是否已存在。
   *
   * @param username 用户名
   * @return true 表示已存在
   */
  boolean existsByUsername(String username);

  /**
   * 按租户 ID 统计用户数量。
   *
   * @param tenantId 租户 ID
   * @return 用户数量
   */
  long countByTenantId(String tenantId);

  /**
   * 原子递增登录失败次数，并在达到阈值时同步设置账号锁定时间。
   *
   * @param id 用户 ID
   * @param threshold 锁定阈值（登录失败次数）
   * @param lockMinutes 锁定时长（分钟）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int increaseLoginFailCount(String id, int threshold, int lockMinutes);

  /**
   * 原子重置登录成功状态：清零失败计数、清除锁定时间、记录最近登录信息。
   *
   * @param id 用户 ID
   * @param loginIp 最近登录 IP（可为 null）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int resetLoginSuccess(String id, String loginIp);
}
