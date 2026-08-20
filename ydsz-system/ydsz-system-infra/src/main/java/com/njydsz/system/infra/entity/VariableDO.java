package com.njydsz.system.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.system.domain.enums.ConfigValueType;
import com.njydsz.system.domain.enums.SystemExceptionCode;

/**
 * 系统变量实体
 *
 * <p>对应数据库表 {@code ydsz_variable}，存储系统级动态变量。 与 {@link Config} 的区别：
 *
 * <ul>
 *   <li>Variable 面向业务侧（前端/ISV 通过 Feign 调用）
 *   <li>Config 面向后端模块（按 group 消费）
 *   <li>Variable 强调「按 key 高频查询」（缓存命中优先）
 * </ul>
 *
 * <p><b>充血模型能力：</b>
 *
 * <ul>
 *   <li>{@link #isEnabled()} — 判断变量是否启用
 *   <li>{@link #validateValueType()} — 校验值类型合法性
 *   <li>{@link #getTypedValue()} — 根据值类型转换为对应 Java 类型
 *   <li>{@link #validate()} — 变量写入前自校验
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 *
 * <ul>
 *   <li>业务开关（动态启用/禁用某功能）
 *   <li>限流阈值（运行时调整 QPS 阈值）
 *   <li>运行时日期（当前会计年度、最近结算月份）
 *   <li>白名单/黑名单（IP 白名单、用户黑名单）
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_variable_key}（{@code variable_key}）， 加速按 key 查询与唯一性校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Config 系统配置实体（面向后端）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_variable")
public class VariableDO extends MpBaseEntity<String> {

  /** 状态常量：启用 */
  public static final String STATUS_ENABLED = "ENABLED";

  /** 状态常量：禁用 */
  public static final String STATUS_DISABLED = "DISABLED";

  /** 变量键（唯一标识，全局唯一） */
  private String variableKey;

  /** 变量值（按 valueType 反序列化为 String/Number/Boolean/JSON） */
  private String variableValue;

  /** 值类型（STRING/NUMBER/BOOLEAN/JSON，参见 {@link com.njydsz.system.domain.enums.ConfigValueType}） */
  private String valueType;

  /** 变量描述（业务含义说明） */
  private String description;

  // ==================== 充血领域方法 ====================

  /**
   * 判断变量是否启用。
   *
   * @return true 为启用状态
   */
  public boolean isEnabled() {
    return STATUS_ENABLED.equals(getStatus());
  }

  /**
   * 校验值类型合法性（领域自校验）。
   *
   * <p>在写入前由调用方触发，非法类型将抛出 {@link BusinessException} 阻止脏数据落库。
   *
   * @throws BusinessException 值类型非法时抛出
   */
  public void validateValueType() {
    if (valueType != null && !valueType.isBlank()) {
      try {
        ConfigValueType.validate(valueType);
      } catch (IllegalArgumentException e) {
        throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
            .data("valueType", valueType)
            .data("variableKey", variableKey);
      }
    }
  }

  /**
   * 根据值类型将变量值转换为对应 Java 类型。
   *
   * <p>转换规则：
   *
   * <ul>
   *   <li>{@code STRING} → {@link String} 原样返回
   *   <li>{@code NUMBER} → {@link Double}
   *   <li>{@code BOOLEAN} → {@link Boolean}
   *   <li>{@code JSON} → {@link String} 原样返回（调用方按需反序列化）
   * </ul>
   *
   * @return 转换后的值；变量值为 null 时返回 null
   * @throws BusinessException 值类型与声明类型不匹配时抛出
   */
  public Object getTypedValue() {
    if (variableValue == null) {
      return null;
    }
    if (valueType == null || valueType.isBlank()) {
      return variableValue;
    }
    try {
      return ConfigValueType.parseValue(valueType, variableValue);
    } catch (Exception e) {
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
          .data("variableKey", variableKey)
          .data("valueType", valueType)
          .data("variableValue", variableValue)
          .data("reason", e.getMessage());
    }
  }

  /**
   * 变量写入前自校验（领域完整性校验）。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>变量键不能为空
   *   <li>值类型合法性
   * </ul>
   *
   * @throws BusinessException 校验失败时抛出
   */
  public void validate() {
    if (variableKey == null || variableKey.isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "变量键不能为空")
          .data("variableKey", variableKey);
    }
    validateValueType();
  }
}
