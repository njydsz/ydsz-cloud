package com.njydsz.common.socket.acl;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import com.njydsz.common.socket.constant.WebSocketConstants;

/**
 * 默认订阅主题 ACL 策略实现（FEAT-003）。
 *
 * <p>提供以下两层校验：
 *
 * <ol>
 *   <li><b>保护区前缀</b>：{@code /topic/admin/**}、{@code /topic/internal/**} 等系统保留前缀，
 *       仅允许 Session 属性中包含 {@code ws-is-admin=true} 的管理员订阅。
 *   <li><b>用户私有前缀校验</b>：{@code /topic/user/{userId}/...} 路径段中的 userId 必须
 *       等于当前 Session 的 userId，防止水平越权（用户 A 订阅用户 B 的私有频道）。
 *   <li><b>租户隔离</b>：当携带了 tenantId 时（ARCH-005 租户隔离），{destination} 必须以
 *       {@code /topic/t/{tenantId}/} 开头，防止跨租户订阅。
 * </ol>
 *
 * <p>业务方可通过注册自定义 {@link TopicAclPolicy} 覆盖默认行为（如允许跨租户订阅特定广播）。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class DefaultTopicAclPolicy implements TopicAclPolicy {

  /** 受保护前缀（仅管理员可订阅） */
  private static final List<String> PROTECTED_PATTERNS =
      List.of("/topic/admin/**", "/topic/internal/**");

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * 检查用户是否可以订阅目标路径。
   *
   * <p>校验顺序：保护区 → 用户私有前缀 → 租户隔离；任一步返回 false 即拒绝订阅。
   *
   * @param userId 订阅用户 ID
   * @param destination 目标订阅路径
   * @param sessionAttributes Session 属性映射
   * @return true 表示允许订阅；false 表示拒绝
   */
  @Override
  public boolean allowSubscription(
      String userId, String destination, Map<String, Object> sessionAttributes) {
    if (!StringUtils.hasText(destination)) {
      return true;
    }

    // ① 保护区前缀校验
    if (isProtectedDestination(destination)) {
      boolean isAdmin = Boolean.TRUE.equals(sessionAttributes.get("ws-is-admin"));
      if (!isAdmin) {
        log.warn(
            "[WS-Acl] 订阅拒绝（保护区）: userId={}, destination={}",
            userId,
            destination);
        return false;
      }
    }

    // ② 用户私有前缀校验：/topic/user/{privateUserId}/... 时 {privateUserId} 必须等于当前用户
    if (destination.startsWith("/topic/user/")) {
      String privateUserId = extractDestinationUserId(destination);
      if (StringUtils.hasText(privateUserId) && !privateUserId.equals(userId)) {
        log.warn(
            "[WS-Acl] 订阅拒绝（越权）: userId={}, privateUserId={}, destination={}",
            userId,
            privateUserId,
            destination);
        return false;
      }
    }

    // ③ 租户隔离（ARCH-005）：destination 以 /topic/t/{tenantId}/... 开头时，tenantId 需匹配
    if (destination.startsWith("/topic/t/")) {
      Object sessionTenantId = sessionAttributes.get(WebSocketConstants.WS_ATTR_TENANT_ID);
      if (sessionTenantId instanceof String sessionTenant && StringUtils.hasText(sessionTenant)) {
        String destTenantId = extractDestinationTenantId(destination);
        if (StringUtils.hasText(destTenantId) && !destTenantId.equals(sessionTenant)) {
          log.warn(
              "[WS-Acl] 订阅拒绝（跨租户）: userId={}, sessionTenant={}, destTenant={}, destination={}",
              userId,
              sessionTenant,
              destTenantId,
              destination);
          return false;
        }
      }
    }

    return true;
  }

  /**
   * 检查目标路径是否为受保护系统前缀。
   *
   * @param destination 目标订阅路径
   * @return true 表示受保护（仅管理员可订阅）
   */
  private boolean isProtectedDestination(String destination) {
    for (String pattern : PROTECTED_PATTERNS) {
      if (pathMatcher.match(pattern, destination)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 从 {@code /topic/user/{userId}/...} 路径中提取私有用户 ID。
   *
   * <p>提取规则：取 "/topic/user/" 之后的第一个路径段；仅当路径段非空时返回有效 ID，否则返回
   * null 以允许前缀订阅（如 {@code /topic/user/**}，需管理员白名单）。
   *
   * @param destination 目标订阅路径
   * @return 私有用户 ID，格式不匹配时返回 null
   */
  private String extractDestinationUserId(String destination) {
    String prefix = "/topic/user/";
    String subPath = destination.substring(prefixLength(prefix));
    if (subPath.isEmpty()) {
      return null;
    }
    int slashIdx = subPath.indexOf('/');
    return slashIdx > 0 ? subPath.substring(0, slashIdx) : subPath;
  }

  /**
   * 从 {@code /topic/t/{tenantId}/...} 路径中提取租户 ID（ARCH-005 租户隔离）。
   *
   * @param destination 目标订阅路径
   * @return 租户 ID，格式不匹配时返回 null
   */
  private String extractDestinationTenantId(String destination) {
    String prefix = "/topic/t/";
    String subPath = destination.substring(prefixLength(prefix));
    if (subPath.isEmpty()) {
      return null;
    }
    int slashIdx = subPath.indexOf('/');
    return slashIdx > 0 ? subPath.substring(0, slashIdx) : subPath;
  }

  /**
   * 返回路径前缀长度（工具方法）。
   *
   * @param prefix 前缀字符串
   * @return 前缀长度
   */
  private int prefixLength(String prefix) {
    return prefix.length();
  }
}
