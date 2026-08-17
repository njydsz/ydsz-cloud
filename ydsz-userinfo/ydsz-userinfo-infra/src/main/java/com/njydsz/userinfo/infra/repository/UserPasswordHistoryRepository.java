package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;

/**
 * 密码历史 Repository 接口
 *
 * <p>封装密码历史表（{@code ydsz_user_password_history}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPasswordHistoryRepository {

  /**
   * 保存密码历史记录（插入）。
   *
   * @param entity 密码历史实体
   * @return 插入影响的行数
   */
  int insert(UserPasswordHistoryDO entity);

  /**
   * 查询用户最近的密码历史记录。
   *
   * @param userId 用户 ID
   * @param limit 返回记录数上限
   * @return 密码历史列表
   */
  List<UserPasswordHistoryDO> findRecentByUserId(String userId, int limit);

  /**
   * 根据用户 ID 删除全部密码历史。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 统计用户的密码历史数量。
   *
   * @param userId 用户 ID
   * @return 密码历史数量
   */
  long countByUserId(String userId);

  /**
   * 条件删除密码历史。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPasswordHistoryDO> wrapper);

  /**
   * 条件查询密码历史列表。
   *
   * @param wrapper 查询条件
   * @return 密码历史列表
   */
  List<UserPasswordHistoryDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPasswordHistoryDO> wrapper);

  /**
   * 统计符合条件的密码历史数量。
   *
   * @param wrapper 查询条件
   * @return 密码历史数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPasswordHistoryDO> wrapper);

  /**
   * 批量删除指定 ID 的密码历史记录。
   *
   * @param ids 记录 ID 列表
   * @return 删除影响的行数
   */
  int deleteByIds(java.util.Collection<String> ids);
}
