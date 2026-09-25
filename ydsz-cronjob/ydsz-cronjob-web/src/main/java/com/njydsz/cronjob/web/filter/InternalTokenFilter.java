package com.njydsz.cronjob.web.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.cronjob.domain.constants.CronjobConstants;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 内部通信鉴权过滤器（P0-1 安全加固）。
 *
 * <p>拦截 {@code /api/cronjob/internal/**} 节点间派发端点，校验请求头
 * {@code X-Ydsz-Internal-Token} 与配置 {@code ydsz.cronjob.remote.access-token} 是否一致。
 *
 * <h3>行为约定</h3>
 *
 * <ul>
 *   <li>配置 token 为空且 {@code ydsz.cronjob.remote.allow-empty-token=false}（默认）：fail-closed，直接拒绝返回 401
 *   <li>配置 token 为空且 allow-empty-token=true：放行（仅限可信内网开发环境显式开启）
 *   <li>配置 token 非空：强制校验，令牌不匹配或缺失返回 401
 *   <li>非 internal 路径：直接放行，不参与过滤
 * </ul>
 *
 * <p>令牌比较使用 {@link DigestUtils#constantTimeEquals(String, String)} 常量时间比较，防止时序侧信道。
 *
 * <h3>与 common 内部签名能力的边界（P1-7 评审结论，勿误用替换）</h3>
 *
 * <p>common-web 的 {@code InternalSignatureFilter} / common-auth 的 {@code InternalHeaderSigner}
 * 面向"网关注入用户上下文头"的场景，验签 payload 为 {@code traceId|userId|username|roles|permissions}；
 * 而本过滤器保护的是<b>节点间服务到服务派发</b>（无用户上下文可签），二者场景不同、并存不冲突
 * （本模块已依赖 common-web）。安全前提：静态令牌无 timestamp/nonce，不可防重放，
 * 仅限内网部署，且 {@code access-token} 必须定期轮换。
 *
 * @author ydsz-team
 * @since 26.09.01
 * 业务特异过滤器（YDIZ-ARCH-004 例外）：节点间静态令牌校验（X-Ydsz-Internal-Token），实现简单可泛化，但当前仅 cronjob 使用；建议下次迭代下沉至 common-safe。
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

  /** 鉴权失败响应体（统一 JSON 结构，便于调用方解析） */
  private static final String UNAUTHORIZED_BODY =
      "{\"code\":401,\"data\":null,\"message\":\"unauthorized: invalid internal token\"}";

  /** 401 状态码 */
  private static final int HTTP_UNAUTHORIZED = 401;

  private final CronjobProperties cronjobProperties;

  /**
   * 构造内部通信鉴权过滤器。
   *
   * @param cronjobProperties 调度配置（读取 remote.access-token）
   */
  public InternalTokenFilter(CronjobProperties cronjobProperties) {
    this.cronjobProperties = cronjobProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // 仅拦截内部派发端点，其余路径（对外 API/OpenAPI/静态资源）不参与
    String path = request.getRequestURI();
    return !path.startsWith(CronjobConstants.INTERNAL_API_PREFIX + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String expectedToken = cronjobProperties.getRemote().getAccessToken();
    // 未配置令牌：默认 fail-closed 拒绝（云顶安全规范），仅 allow-empty-token=true 时放行（可信内网开发环境）
    if (expectedToken == null || expectedToken.isBlank()) {
      if (cronjobProperties.getRemote().isAllowEmptyToken()) {
        filterChain.doFilter(request, response);
        return;
      }
      log.warn("[InternalTokenFilter] 未配置 access-token 且未开启 allow-empty-token, 拒绝内部请求: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    String providedToken = request.getHeader(CronjobConstants.INTERNAL_TOKEN_HEADER);
    if (providedToken == null || providedToken.isBlank()) {
      log.warn("[InternalTokenFilter] 内部请求缺少令牌: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    if (!DigestUtils.constantTimeEquals(providedToken, expectedToken)) {
      log.warn("[InternalTokenFilter] 内部请求令牌校验失败: uri={} from={}",
          request.getRequestURI(), request.getRemoteAddr());
      reject(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * 输出 401 拒绝响应。
   *
   * @param response HTTP 响应
   * @throws IOException 响应写出失败
   */
  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HTTP_UNAUTHORIZED);
    response.setContentType("application/json; charset=UTF-8");
    response.getWriter().write(UNAUTHORIZED_BODY);
  }
}
