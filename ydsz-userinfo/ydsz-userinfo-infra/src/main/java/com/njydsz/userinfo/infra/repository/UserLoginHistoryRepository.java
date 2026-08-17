package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserLoginHistory;

/**
 * 用户登录历史 Repository 接口
 *
 * <p>封装用户登录历史表（{@code ydsz_user_login_history}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserLoginHistoryRepository {

  /**
   * 保存登录历史记录（插入）。
   *
   * @param entity 登录历史实体
   * @return 插入影响的行数
   */
  int insert(UserLoginHistory entity);

  /**
   * 统计最近指定时间窗口内的登录失败次数。
   *
   * @param userId 用户 ID
   * @param windowMinutes 时间窗口（分钟）
   * @return 失败次数
   */
  int countRecentFailures(String userId, int windowMinutes);

  /**
   * 查询用户最近的登录记录。
   *
   * @param userId 用户 ID
   * @param limit 返回记录数上限
   * @return 登录历史列表
   */
  List<UserLoginHistory> findRecentByUserId(String userId, int limit);

  /**
   * 条件查询登录历史列表。
   *
   * @param wrapper 查询条件
   * @return 登录历史列表
   */
  List<UserLoginHistory> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLoginHistory> wrapper);
}
