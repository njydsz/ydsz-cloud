package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.query.LoginLogPageQuery;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;

/**
 * 登录历史服务接口
 *
 * <p>提供登录历史记录和 IP 封禁策略功能。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>记录登录尝试（成功/失败）
 *   <li>检查 IP 是否被封禁
 *   <li>查询用户最近登录记录
 *   <li>统计 IP 登录失败次数
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface LoginHistoryService {

  /**
   * 记录登录尝试。
   *
   * @param context 登录尝试上下文（含 userId/username/loginIp）
   * @param result 登录结果（SUCCESS / FAILED）
   * @param failReason 失败原因
   * @param userAgent 用户代理
   */
  void recordLoginAttempt(
      LoginAttemptContext context, String result, String failReason, String userAgent);

  /**
   * 检查 IP 是否被封禁
   *
   * <p>如果 IP 在过去 N 分钟内失败次数超过阈值，则视为被封禁。
   *
   * @param ip IP 地址
   * @return true 表示被封禁；false 表示正常
   */
  boolean isIpBlocked(String ip);

  /**
   * 查询用户最近登录记录
   *
   * @param userId 用户 ID
   * @param limit 返回记录数上限
   * @return 登录历史列表
   */
  List<UserLoginHistoryVO> getRecentLogins(String userId, int limit);

  /**
   * 分页查询登录历史列表。
   *
   * <p>支持按用户名、IP、登录结果、时间范围筛选，默认按创建时间降序排列。
   *
   * @param query 分页查询条件
   * @return 分页结果（含总记录数与当前页数据）
   */
  PageResponse<List<UserLoginHistoryVO>> pageLoginHistory(LoginLogPageQuery query);
}
