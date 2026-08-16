package com.njydsz.common.safe.ratelimit.decorator;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;

/**
 * 限流响应装饰器
 *
 * <p>当请求被限流时，向 HTTP 响应中添加标准化的限流头部，遵循行业惯例（GitHub/Twitter API 风格）。
 *
 * <p><b>标准化响应头：</b>
 *
 * <ul>
 *   <li>{@code Retry-After} - 建议客户端等待的秒数（RFC 7231）
 *   <li>{@code X-RateLimit-Limit} - 当前规则的总阈值
 *   <li>{@code X-RateLimit-Remaining} - 剩余可用配额
 *   <li>{@code X-RateLimit-Reset} - 配额重置时间（Unix 时间戳）
 * </ul>
 *
 * <p><b>示例响应头：</b>
 *
 * <pre>{@code
 * HTTP/1.1 429 Too Many Requests
 * Retry-After: 30
 * X-RateLimit-Limit: 100
 * X-RateLimit-Remaining: 0
 * X-RateLimit-Reset: 1723710000
 * Content-Type: application/json
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class RateLimitResponseDecorator {

  private static final Logger log = LoggerFactory.getLogger(RateLimitResponseDecorator.class);

  /**
   * 为限流拒绝响应添加标准化头部
   *
   * <p>在限流被拒绝时调用，向响应对象添加 Retry-After / X-RateLimit-* 头部。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param decision 限流决策（含阈值、剩余配额、等待时间等）
   */
  public void decorateBlockedResponse(
      HttpServletRequest request, HttpServletResponse response, RateLimitDecision decision) {
    if (request == null || response == null || decision == null) {
      return;
    }

    try {
      // Retry-After：建议等待秒数
      long waitSeconds =
          decision.getWaitTimeMillis() > 0
              ? Math.max(1, Math.round(decision.getWaitTimeMillis() / 1000.0))
              : 60;
      response.setHeader("Retry-After", String.valueOf(waitSeconds));

      // X-RateLimit-Limit：总阈值
      if (decision.getThreshold() > 0) {
        response.setHeader("X-RateLimit-Limit", String.valueOf((int) decision.getThreshold()));
      }

      // X-RateLimit-Remaining：剩余配额（限流时为 0）
      response.setHeader("X-RateLimit-Remaining", "0");

      // X-RateLimit-Reset：重置时间（当前时间 + 等待秒数）
      long resetEpoch = Instant.now().getEpochSecond() + waitSeconds;
      response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpoch));

    } catch (Exception e) {
      // 头部设置失败不应影响主流程
      log.debug("设置限流响应头失败: {}", e.getMessage());
    }
  }

  /**
   * 为正常通过响应添加限流状态头部（可选增强）
   *
   * <p>在请求通过限流检查后，向响应中添加当前配额状态， 便于客户端了解自身限流使用情况。
   *
   * @param response HTTP 响应
   * @param decision 限流决策
   */
  public void decoratePassResponse(HttpServletResponse response, RateLimitDecision decision) {
    if (response == null || decision == null) {
      return;
    }

    try {
      if (decision.getThreshold() > 0) {
        response.setHeader("X-RateLimit-Limit", String.valueOf((int) decision.getThreshold()));
        response.setHeader(
            "X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.getRemaining())));
      }
    } catch (Exception e) {
      log.debug("设置限流通过响应头失败: {}", e.getMessage());
    }
  }
}
