package com.njydsz.userinfo.infra.mapper;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.userinfo.infra.entity.UserLoginHistoryDO;

/**
 * 用户登录历史 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_user_login_history}，提供登录历史记录的 CRUD 操作。
 *
 * <p><b>主要查询场景：</b>
 *
 * <ul>
 *   <li>查询用户最近登录记录
 *   <li>查询某 IP 的登录失败次数（IP 封禁判断）
 *   <li>定期清理历史数据
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserLoginHistoryMapper extends BaseMapper<UserLoginHistoryDO> {

  /**
   * 统计指定时间范围内有登录失败记录的去重用户数。
   *
   * <p>使用 {@code COUNT(DISTINCT user_id)} 在数据库层面完成去重统计，避免全量查询到应用内存。
   *
   * @param startTime 起始时间（含）
   * @param endTime 结束时间（含）
   * @return 去重用户数
   */
  @Select(
      "SELECT COUNT(DISTINCT user_id) FROM ydsz_user_login_history "
          + "WHERE login_result = 'FAILED' AND created_at >= #{startTime} AND created_at < #{endTime}")
  long countDistinctUsersWithFailures(
      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
