package com.njydsz.system.domain.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 系统变量 VO
 *
 * <p>对应 {@code ydsz_variable} 表的展示视图，是「系统变量中心」列表 / 详情接口的返回值类型。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.Variable} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code variableKey} — 变量键，租户内唯一，业务存储 / 接口传输主键
 *   <li>{@code variableValue} — 变量值，序列化时按 {@code valueType} 转换： {@code STRING} 原样输出；{@code NUMBER /
 *       BOOLEAN / JSON} 解析为对应类型
 * </ul>
 *
 * <p><b>与 Config 的区别：</b>{@code Variable} 是<b>全局无分组</b>的扁平结构， 适合「跨模块共享的环境变量」场景；{@code ConfigVO}
 * 是<b>按分组管理</b>的结构， 适合「按业务域配置」场景。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>系统变量中心列表 / 详情 / 编辑回显
 *   <li>运行时动态配置（如外部 API endpoint、限流阈值）
 *   <li>Spring 占位符解析：{@code @Value("${ydsz.variable.xxx}")}
 * </ul>
 *
 * <p><b>缓存策略：</b>读取通过 {@code ydsz:variable:{key}} 缓存至 Redis； 写入时 {@code @CacheEvict} 主动失效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Variable 系统变量实体
 * @see com.njydsz.system.domain.dto.VariableDTO 系统变量 DTO
 * @see ConfigVO 系统配置 VO（按分组的同类结构）
 */
@Data
@Schema(description = "系统变量视图对象")
public class VariableVO {

  @Schema(description = "主键 ID")
  private String id;

  @Schema(description = "变量键")
  private String variableKey;

  @Schema(description = "变量值")
  private String variableValue;

  @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
  private String valueType;

  @Schema(description = "变量说明")
  private String description;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;
}
