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
 * 系统配置实体
 *
 * <p>对应数据库表 {@code ydsz_config}，存储系统级配置项。 支持按分组分类、按配置键查找，公开/私有配置区分，多种值类型（字符串/数字/布尔/JSON）。 运行时可通过
 * {@code ConfigClient} 监听配置变更（基于 Nacos long-polling）。
 *
 * <p><b>充血模型能力：</b>
 *
 * <ul>
 *   <li>{@link #isPublicConfig()} — 判断是否公开配置
 *   <li>{@link #validateValueType()} — 校验值类型合法性
 *   <li>{@link #getTypedValue()} — 根据值类型转换为对应 Java 类型
 *   <li>{@link #ensureDefault()} — 确保配置值存在，否则使用默认值
 *   <li>{@link #validate()} — 配置写入前自校验
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 *
 * <ul>
 *   <li>功能开关（feature flag）：通过 {@code configGroup=feature} + {@code isPublic=1} 让前端感知
 *   <li>限流阈值：运行时调整接口限流参数，无需发版
 *   <li>第三方服务地址：密钥/地址变更不需重新部署
 *   <li>UI 文案：前端展示文本、错误提示等可由配置动态下发
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_config_group_key}（{@code config_group}, {@code config_key}），
 * 加速按分组+键查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 * @see com.njydsz.system.server.service.ConfigService 配置业务逻辑
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_config")
public class ConfigDO extends MpBaseEntity<String> {

  /** 配置分组（用于按业务域分类管理配置） */
  private String configGroup;

  /** 配置键（同组内唯一标识） */
  private String configKey;

  /** 配置值 */
  private String configValue;

  /** 值类型（STRING/NUMBER/BOOLEAN/JSON，参见 ConfigValueType 枚举） */
  private String valueType;

  /** 默认值（配置未设置时使用） */
  private String defaultValue;

  /** 配置描述 */
  private String description;

  /** 是否公开配置（1=公开，前端可查；0=私有，仅后端可查） */
  private Integer isPublic;

  /** 排序序号 */
  private Integer sortOrder;

  // ==================== 充血领域方法 ====================

  /**
   * 判断是否为公开配置。
   *
   * @return true 为公开配置
   */
  public boolean isPublicConfig() {
    return Integer.valueOf(1).equals(isPublic);
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
            .data("configKey", configKey);
      }
    }
  }

  /**
   * 根据值类型将配置值转换为对应 Java 类型。
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
   * @return 转换后的值；配置值为 null 时返回 null
   * @throws BusinessException 值类型与声明类型不匹配时抛出
   */
  public Object getTypedValue() {
    if (configValue == null) {
      return null;
    }
    if (valueType == null || valueType.isBlank()) {
      return configValue;
    }
    try {
      return ConfigValueType.parseValue(valueType, configValue);
    } catch (Exception e) {
      throw BusinessException.of(SystemExceptionCode.VALUE_TYPE_INVALID)
          .data("configKey", configKey)
          .data("valueType", valueType)
          .data("configValue", configValue)
          .data("reason", e.getMessage());
    }
  }

  /**
   * 确保配置值存在，否则使用默认值。
   *
   * <p>适用于「配置未填写时 fallback 到默认值」的场景。
   *
   * @return 配置值（优先）或默认值
   */
  public String ensureDefault() {
    return (configValue != null && !configValue.isBlank()) ? configValue : defaultValue;
  }

  /**
   * 配置写入前自校验（领域完整性校验）。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>配置分组不能为空
   *   <li>配置键不能为空
   *   <li>值类型合法性
   * </ul>
   *
   * @throws BusinessException 校验失败时抛出
   */
  public void validate() {
    if (configGroup == null || configGroup.isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "配置分组不能为空")
          .data("configKey", configKey);
    }
    if (configKey == null || configKey.isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "配置键不能为空")
          .data("configKey", configKey);
    }
    validateValueType();
  }

  /**
   * 校验配置值格式是否与声明类型匹配（值格式规则的权威实现，P1-5 收敛）。
   *
   * <p>委托 {@link ConfigValueType#validateFormat(String, String)}，返回错误描述而非直接抛异常，
   * 由调用方（Service）决定「严格拦截」还是「告警放行」，兼顾数据完整性与存量兼容。
   *
   * @return 错误描述；null 表示通过
   */
  public String validateValueFormat() {
    return ConfigValueType.validateFormat(valueType, configValue);
  }
}
