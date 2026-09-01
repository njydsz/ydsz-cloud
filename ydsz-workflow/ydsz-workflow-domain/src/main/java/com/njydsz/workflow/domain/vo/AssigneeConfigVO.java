package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;

/**
 * 办理人配置值对象。
 *
 * <p>封装节点 ext JSON 中办理人相关的配置，包括审批人为空时的兜底策略。
 * 替代 {@link FlowNodeVO#getEmptyStrategy()}、{@link FlowNodeVO#getAdminUserId()}、
 * {@link FlowNodeVO#getSpecifiedUserId()} 等弱类型 getter。
 *
 * <p><b>兜底策略：</b>
 *
 * <ul>
 *   <li>{@link EmptyStrategy#AUTO_PASS} — 自动通过（默认）
 *   <li>{@link EmptyStrategy#TRANSFER_ADMIN} — 转交给管理员
 *   <li>{@link EmptyStrategy#ASSIGN_SPECIFIED} — 转交给指定用户
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象（所有字段 final）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class AssigneeConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认兜底策略（AUTO_PASS 自动通过） */
  public static final EmptyStrategy DEFAULT_EMPTY_STRATEGY = EmptyStrategy.AUTO_PASS;

  /** 默认管理员用户 ID */
  public static final String DEFAULT_ADMIN_USER_ID = "1";

  /** 审批人为空时的兜底策略 */
  private final EmptyStrategy emptyStrategy;

  /** 管理员用户 ID（TRANSFER_ADMIN 策略时使用） */
  private final String adminUserId;

  /** 指定用户 ID（ASSIGN_SPECIFIED 策略时使用） */
  private final String specifiedUserId;

  private AssigneeConfig(EmptyStrategy emptyStrategy, String adminUserId,
      String specifiedUserId) {
    this.emptyStrategy = emptyStrategy != null ? emptyStrategy : DEFAULT_EMPTY_STRATEGY;
    this.adminUserId = adminUserId != null ? adminUserId : DEFAULT_ADMIN_USER_ID;
    this.specifiedUserId = specifiedUserId != null ? specifiedUserId : DEFAULT_ADMIN_USER_ID;
  }

  /**
   * 从 ext JSON Map 解析办理人配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return 办理人配置值对象（不可变）
   */
  public static AssigneeConfig fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new AssigneeConfig(DEFAULT_EMPTY_STRATEGY, DEFAULT_ADMIN_USER_ID,
          DEFAULT_ADMIN_USER_ID);
    }
    EmptyStrategy strategy = parseEmptyStrategy(extMap.get("emptyStrategy"));
    String adminUser = parseStringSafe(extMap.get("adminUserId"), DEFAULT_ADMIN_USER_ID);
    String specifiedUser = parseStringSafe(extMap.get("specifiedUserId"),
        DEFAULT_ADMIN_USER_ID);
    return new AssigneeConfig(strategy, adminUser, specifiedUser);
  }

  /**
   * 从 ext JSON 字符串解析办理人配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return 办理人配置值对象（不可变）
   */
  public static AssigneeConfig fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new AssigneeConfig(DEFAULT_EMPTY_STRATEGY, DEFAULT_ADMIN_USER_ID,
          DEFAULT_ADMIN_USER_ID);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new AssigneeConfig(DEFAULT_EMPTY_STRATEGY, DEFAULT_ADMIN_USER_ID,
          DEFAULT_ADMIN_USER_ID);
    }
  }

  /**
   * 获取兜底策略对应的目标用户 ID。
   *
   * <p>根据当前兜底策略返回应转交的用户 ID：
   *
   * <ul>
   *   <li>AUTO_PASS → 返回 null（无需转交，直接通过）
   *   <li>TRANSFER_ADMIN → 返回 adminUserId
   *   <li>ASSIGN_SPECIFIED → 返回 specifiedUserId
   * </ul>
   *
   * @return 目标用户 ID，AUTO_PASS 时返回 null
   */
  public String resolveFallbackUserId() {
    return switch (emptyStrategy) {
      case AUTO_PASS -> null;
      case TRANSFER_ADMIN -> adminUserId;
      case ASSIGN_SPECIFIED -> specifiedUserId;
    };
  }

  /**
   * 审批人为空时是否需要转交他人处理。
   *
   * @return true-需要转交；false-自动通过
   */
  public boolean requiresFallback() {
    return emptyStrategy != EmptyStrategy.AUTO_PASS;
  }

  /**
   * 审批人为空时兜底策略枚举。
   *
   * <p>定义当节点审批人为空时的处理方式。
   */
  public enum EmptyStrategy {
    /** 自动通过 */
    AUTO_PASS,
    /** 转交给管理员 */
    TRANSFER_ADMIN,
    /** 转交给指定用户 */
    ASSIGN_SPECIFIED
  }

  // ==================== 内部工具方法 ====================

  private static String parseStringSafe(Object value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String str = String.valueOf(value).trim();
    return str.isEmpty() ? defaultValue : str;
  }

  private static EmptyStrategy parseEmptyStrategy(Object value) {
    if (value == null) {
      return DEFAULT_EMPTY_STRATEGY;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return EmptyStrategy.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_EMPTY_STRATEGY;
    }
  }
}
