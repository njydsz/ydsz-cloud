package com.njydsz.userinfo.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;

/**
 * 用户登录历史 Repository 接口
 *
 * <p>封装用户登录历史表（{@code ydsz_user_login_history}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserLoginHistoryRepository {

  /**
   * 保存登录历史记录（插入）。
   *
   * @param dto 登录历史 DTO
   * @return 保存后的登录历史 VO
   */
  UserLoginHistoryVO create(UserLoginHistoryDTO dto);

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
  List<UserLoginHistoryVO> findRecentByUserId(String userId, int limit);

  /**
   * 根据用户 ID 查询登录历史列表。
   *
   * @param userId 用户 ID
   * @return 登录历史列表
   */
  List<UserLoginHistoryVO> findByUserId(String userId);

  /**
   * 按登录结果统计指定时间范围内的登录次数。
   *
   * @param startTime 起始时间（含）
   * @param endTime 结束时间（含）
   * @param result 登录结果（SUCCESS/FAILED）
   * @return 登录次数
   */
  long countByResultAndTimeRange(LocalDateTime startTime, LocalDateTime endTime, String result);

  /**
   * 按失败原因统计指定时间范围内的登录失败次数。
   *
   * @param startTime 起始时间（含）
   * @param endTime 结束时间（含）
   * @param failReason 失败原因
   * @return 失败次数
   */
  int countByFailReasonAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String failReason);

  /**
   * 查询指定时间之后的最近登录失败记录。
   *
   * @param since 起始时间
   * @param limit 返回记录数上限
   * @return 登录失败记录列表
   */
  List<UserLoginHistoryVO> findRecentFailedLogins(LocalDateTime since, int limit);
}
