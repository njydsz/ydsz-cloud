package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.query.LoginLogPageQuery;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;

/**
 * 登录日志查询控制器。
 *
 * <p>供管理员查看用户登录历史，支持按用户名、IP、状态、时间范围筛选。
 * 数据来源于 {@code ydsz_acct_login_history} 表，浏览器/操作系统字段由 User-Agent 解析得出。
 *
 * <p><b>接口路径：</b>{@code /api/userinfo/login-log}
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@RestController
@RequestMapping("/userinfo/login-log")
@RequiredArgsConstructor
@ApiVersion("26.09.13")
@Tag(name = "登录日志", description = "用户登录历史查询（审计用）")
public class LoginLogController {

  private final UserLoginHistoryRepository loginHistoryRepository;

  /**
   * 分页查询登录日志。
   *
   * <p>支持筛选条件：用户名（模糊）、登录 IP（精确）、登录结果（SUCCESS/FAILED/ALL）、时间范围。
   * 默认按登录时间降序返回。
   *
   * @param query 分页查询条件
   * @return 登录日志分页列表
   */
  @GetMapping("/page")
  @AuthApiPermission(apiCodes = "admin:security:alert")
  @Operation(summary = "分页查询登录日志", description = "按条件分页查询用户登录历史（含浏览器/操作系统解析）")
  public YdszResponse<PageResponse<List<UserLoginHistoryVO>>> page(LoginLogPageQuery query) {
    log.debug("分页查询登录日志: username={}, ip={}, status={}",
        query.getUsername(), query.getLoginIp(), query.getStatus());
    return YdszResponse.success(loginHistoryRepository.page(query));
  }
}
