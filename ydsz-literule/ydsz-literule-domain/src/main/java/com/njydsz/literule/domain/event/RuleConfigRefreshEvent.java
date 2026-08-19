package com.njydsz.literule.domain.event;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.event.api.DomainEvent;

/**
 * 规则配置刷新事件（P2-1 事件体系统一）
 *
 * <p>继承 {@link DomainEvent}，统一事件元数据（eventId / occurredAt / eventType / aggregateId）。
 * 当规则配置发生变更（新增/修改/删除/启停）时发布此事件，引擎监听后重新加载规则定义并热刷新注册表。
 *
 * <p>事件类型映射：
 *
 * <ul>
 *   <li>eventType = "RuleConfigRefresh"
 *   <li>aggregateId = ruleCode（null 表示全量刷新）
 *   <li>aggregateType = "Rule"
 *   <li>metadata 包含 changeType、operator
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 2.1.0 继承 DomainEvent，统一事件元数据
 */
public class RuleConfigRefreshEvent extends DomainEvent {

  /** 元数据键：变更类型 */
  private static final String METADATA_CHANGE_TYPE = "changeType";

  /** 元数据键：操作人 */
  private static final String METADATA_OPERATOR = "operator";

  /** 事件类型常量 */
  public static final String EVENT_TYPE = "RuleConfigRefresh";

  /** 聚合根类型常量 */
  public static final String AGGREGATE_TYPE = "Rule";

  /** 变更类型枚举 */
  public enum ChangeType {
    CREATE,
    UPDATE,
    DELETE,
    TOGGLE,
    FULL_RELOAD,
    /** 规则包（RulePack）批量重载（P0-F4） */
    PACK_RELOAD
  }

  /**
   * 构造规则包批量重载事件（P0-F4）
   *
   * @param packCode 规则包编码
   * @param operator 操作人
   * @return 事件实例
   */
  public static RuleConfigRefreshEvent packReload(String packCode, String operator) {
    return new RuleConfigRefreshEvent(packCode, ChangeType.PACK_RELOAD, operator);
  }

  /**
   * 构造全量刷新事件
   *
   * @param operator 操作人
   * @return 事件实例
   */
  public static RuleConfigRefreshEvent fullReload(String operator) {
    return new RuleConfigRefreshEvent(null, ChangeType.FULL_RELOAD, operator);
  }

  /**
   * 构造单条规则变更事件
   *
   * @param ruleCode 规则编码
   * @param changeType 变更类型
   * @param operator 操作人
   * @return 事件实例
   */
  public static RuleConfigRefreshEvent of(String ruleCode, ChangeType changeType, String operator) {
    return new RuleConfigRefreshEvent(ruleCode, changeType, operator);
  }

  /**
   * 构造规则配置刷新事件
   *
   * @param ruleCode 规则编码（null 表示全量刷新）
   * @param changeType 变更类型
   * @param operator 操作人
   */
  public RuleConfigRefreshEvent(String ruleCode, ChangeType changeType, String operator) {
    super(null, null, EVENT_TYPE, ruleCode, AGGREGATE_TYPE, buildMetadata(changeType, operator));
  }

  /**
   * 从 DomainEvent 还原 RuleConfigRefreshEvent（用于反序列化场景）
   *
   * @param event 领域事件
   * @return RuleConfigRefreshEvent 实例
   */
  public static RuleConfigRefreshEvent from(DomainEvent event) {
    ChangeType changeType =
        ChangeType.valueOf(String.valueOf(event.getMetadata(METADATA_CHANGE_TYPE)));
    String operator = String.valueOf(event.getMetadata(METADATA_OPERATOR));
    return new RuleConfigRefreshEvent(event.getAggregateId(), changeType, operator);
  }

  /**
   * 获取变更类型
   *
   * @return 变更类型
   */
  public ChangeType getChangeType() {
    return ChangeType.valueOf(String.valueOf(getMetadata(METADATA_CHANGE_TYPE)));
  }

  /**
   * 获取操作人
   *
   * @return 操作人
   */
  public String getOperator() {
    return String.valueOf(getMetadata(METADATA_OPERATOR));
  }

  /**
   * 获取规则编码
   *
   * @return 规则编码（null 表示全量刷新）
   */
  public String getRuleCode() {
    return getAggregateId();
  }

  // ==================== 内部实现 ====================

  private static Map<String, Object> buildMetadata(ChangeType changeType, String operator) {
    Map<String, Object> metadata = new HashMap<>(2);
    metadata.put(METADATA_CHANGE_TYPE, changeType.name());
    metadata.put(METADATA_OPERATOR, operator);
    return metadata;
  }
}
