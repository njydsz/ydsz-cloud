package com.njydsz.common.lock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.idempotent.RepeatSubmitTokenService;
import com.njydsz.common.lock.spi.CurrentUserIdResolver;

/**
 * 表单重复提交 Token 控制器
 *
 * <p>提供获取防重复提交 Token 的接口，前端在提交表单前先调用此接口获取 Token， 然后在提交时携带 Token 到请求头。
 *
 * <p><b>使用流程：</b>
 *
 * <ol>
 *   <li>前端调用 {@code GET /repeat-submit/token} 获取 Token
 *   <li>前端提交表单时在请求头携带 {@code X-Repeat-Token: {token}}
 *   <li>后端校验 Token 有效性，成功后自动删除（一次性使用）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RepeatSubmitTokenService
 */
@Slf4j
@RestController
@RequestMapping("/repeat-submit")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(RepeatSubmitTokenService.class)
@Tag(name = "防重复提交", description = "表单重复提交防护 Token 管理")
public class RepeatSubmitTokenController {

  private final RepeatSubmitTokenService tokenService;
  private final CurrentUserIdResolver userIdResolver;

  public RepeatSubmitTokenController(
      RepeatSubmitTokenService tokenService, CurrentUserIdResolver userIdResolver) {
    this.tokenService = tokenService;
    this.userIdResolver = userIdResolver;
  }

  /**
   * 获取防重复提交 Token
   *
   * <p>前端在提交表单前调用此接口获取一次性 Token，提交时携带到请求头。 Token 与当前登录用户绑定，有效期由参数指定。
   *
   * @param ttlMillis Token 有效期（毫秒），默认 60000（60 秒）
   * @return 包含 Token 的响应
   */
  @GetMapping("/token")
  @Operation(summary = "获取防重复提交 Token", description = "前端提交表单前先获取 Token，提交时携带到请求头 X-Repeat-Token")
  public YdszResponse<String> getToken(
      @Parameter(description = "Token 有效期（毫秒），默认 60000") @RequestParam(defaultValue = "60000")
          long ttlMillis) {
    try {
      String userId = userIdResolver.getCurrentUserId();
      String token = tokenService.generateToken(userId, ttlMillis);
      log.debug("[ydsz-lock] [repeat-submit] 生成 Token 成功 | userId={}, ttl={}ms", userId, ttlMillis);
      return YdszResponse.success(token);
    } catch (IllegalArgumentException e) {
      log.warn("[ydsz-lock] [repeat-submit] 生成 Token 失败 | cause={}", e.getMessage());
      return YdszResponse.error(YdszResultCode.UNAUTHORIZED, "用户未登录，无法生成 Token");
    }
  }
}
