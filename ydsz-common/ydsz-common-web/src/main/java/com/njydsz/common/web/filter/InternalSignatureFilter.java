package com.njydsz.common.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.constant.InternalSignatureHeaderConstants;
import com.njydsz.common.auth.security.InternalHeaderSigner;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.common.web.config.InternalSignatureProperties;

/**
 * 内部签名验签过滤器（P0-3 补建，闭环网关 X-Internal-Sig 验签链路）。
 *
 * <p>历史缺陷复盘：网关 {@code AuthGlobalFilter} 为每个请求注入 {@code X-Internal-Sig}
 * HMAC 签名，但全仓无任何下游校验代码——直连业务服务端口并伪造内部头即可绕过网关鉴权，
 * {@code /api/internal/**} 内部接口暴露风险最高。本过滤器补齐验证侧，实现"网关只签、
 * 下游必验"的闭环。
 *
 * <p><b>行为约定：</b>
 *
 * <ol>
 *   <li>仅对 {@link InternalSignatureProperties#getEnforcePaths()} 命中的路径强制验签，
 *       其余路径直接放行（公网流量已由网关鉴权）；</li>
 *   <li>验签算法：{@link InternalHeaderSigner#verify}（HMAC-SHA256 + 恒定时间比较），
 *       payload 与网关签名侧完全一致：traceId|userId|username|roles|permissions；</li>
 *   <li>fail-closed：密钥未配置时拒绝请求（503）并 ERROR 日志告警，防止"配置缺失即裸奔"；</li>
 *   <li>验签失败返回 403 + 统一 {@link YdszResponse} 错误体（错误码 C01081）。</li>
 * </ol>
 *
 * <p><b>灰度上线：</b>由 {@code ydsz.security.internal-sign.enabled} 控制，默认关闭；
 * 密钥经 Nacos 加密配置下发、网关与各服务对齐后按服务粒度开启。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class InternalSignatureFilter extends OncePerRequestFilter {

  /** 内部签名配置 */
  private final InternalSignatureProperties properties;

  /** Ant 风格路径匹配器 */
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public InternalSignatureFilter(InternalSignatureProperties properties) {
    this.properties = properties;
  }

  /**
   * 未启用或路径不强制验签时跳过过滤器。
   *
   * @param request 当前请求
   * @return true=跳过过滤器
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return true;
    }
    List<String> enforcePaths = properties.getEnforcePaths();
    if (enforcePaths == null || enforcePaths.isEmpty()) {
      return true;
    }
    String path = request.getRequestURI();
    for (String pattern : enforcePaths) {
      if (pathMatcher.match(pattern, path)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
      // fail-closed：密钥未配置时拒绝服务并告警，避免静默裸奔
      log.error("[InternalSignatureFilter] 内部签名已启用但密钥未配置"
              + " (ydsz.security.internal-sign.secret)，拒绝内部请求: {}",
          request.getRequestURI());
      writeError(response, HttpStatus.SERVICE_UNAVAILABLE, SecurityExceptionCode.SEC_ACCESS_DENIED);
      return;
    }

    String receivedSig = request.getHeader(InternalSignatureHeaderConstants.X_INTERNAL_SIG);
    boolean valid =
        InternalHeaderSigner.verify(
            secret,
            request.getHeader(HeaderConstants.TRACE_ID_HEADER),
            request.getHeader(AuthHeaderConstants.X_USER_ID),
            request.getHeader(AuthHeaderConstants.X_USERNAME),
            request.getHeader(AuthHeaderConstants.X_USER_ROLES),
            request.getHeader(AuthHeaderConstants.X_USER_PERMISSIONS),
            receivedSig);

    if (!valid) {
      log.warn(
          "[InternalSignatureFilter] 内部签名校验失败，拒绝请求: uri={}, remoteAddr={}, sigPresent={}",
          request.getRequestURI(),
          request.getRemoteAddr(),
          receivedSig != null && !receivedSig.isEmpty());
      writeError(response, HttpStatus.FORBIDDEN, SecurityExceptionCode.INTERNAL_SIGNATURE_INVALID);
      return;
    }

    filterChain.doFilter(request, response);
  }

  /**
   * 写入统一错误响应体（YdszResponse 标准格式，与 WebAccessDeniedHandler 风格一致）。
   *
   * @param response HTTP 响应
   * @param status HTTP 状态码
   * @param errorCode 异常错误码
   * @throws IOException 写出失败
   */
  private void writeError(HttpServletResponse response, HttpStatus status, ExceptionCode errorCode)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    String message = MessageUtils.getMessage(errorCode.getKey(), errorCode.getKey());
    YdszResponse<?> body = YdszResponse.error(errorCode.getCode(), message);
    // 触发 traceId 懒加载，确保序列化时包含链路追踪 ID
    body.getTraceId();
    response.getWriter().write(YdszJson.toJson(body));
  }
}
