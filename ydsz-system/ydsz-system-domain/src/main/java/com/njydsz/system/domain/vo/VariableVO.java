package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统变量 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_variable} 表的展示视图和写入参数，是「系统变量中心」列表 / 详情 / 创建 / 更新接口的通用载体。 由 {@link
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
 * @see ConfigVO 系统配置 VO（按分组的同类结构）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "系统变量视图对象")
public class VariableVO {

  @Schema(description = "主键 ID（更新时必填）")
  private String id;

  @NotBlank(message = "变量键不能为空")
  @Size(max = 128, message = "变量键长度不能超过128")
  @Xss(message = "变量键包含非法内容")
  @Schema(description = "变量键")
  private String variableKey;

  @Xss(message = "变量值包含非法内容")
  @Schema(description = "变量值")
  private String variableValue;

  @NotBlank(message = "值类型不能为空")
  @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
  private String valueType;

  @Xss(message = "变量说明包含非法内容")
  @Schema(description = "变量说明")
  private String description;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;
}
