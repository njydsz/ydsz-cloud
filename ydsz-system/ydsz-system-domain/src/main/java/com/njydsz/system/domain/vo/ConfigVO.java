package com.njydsz.system.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统配置 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_config} 表的展示视图和写入参数，是「系统配置中心」列表 / 详情 / 创建 / 更新接口的通用载体（轻量模块约定：
 * 请求 / 响应共用同一契约对象，字段语义一致；严格三分离（Command/VO/DTO）在字段无差异时视为过度设计，不作拆分）。 由 {@link
 * com.njydsz.system.infra.converter.SystemConverter} 从 {@link
 * com.njydsz.system.infra.entity.Config} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code configValue} — 配置值，序列化时按 {@code valueType} 转换： {@code STRING} 原样输出；{@code NUMBER /
 *       BOOLEAN / JSON} 解析为对应类型
 *   <li>{@code defaultValue} — 默认值（{@code configValue} 为空时回退）
 *   <li>{@code isPublic} — 是否对前端公开：{@code 1} 公开 / {@code 0} 仅后端； 前端「公开配置」接口仅返回 {@code isPublic=1}
 *       的项
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>配置中心列表 / 详情 / 编辑回显
 *   <li>业务参数动态下发（{@code ydsz.workflow.sla-default-hours} 等）
 *   <li>前端「公开配置」接口返回值（开关、限流阈值、特性开关等）
 * </ul>
 *
 * <p><b>缓存策略：</b>读取通过 ydsz-common-cache 进程内本地缓存（键 {@code value:{tenantId}:{configKey}}）；
 * 写入时 {@code @CacheEvict} 精准失效；TTL 通过 {@code ydsz.cache.caches.system:config} 配置（默认 5 分钟）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.Config 系统配置实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ConfigVO {

  private String id;

  @NotBlank(message = "配置分组不能为空")
  @Size(max = 64, message = "配置分组长度不能超过64")
  @Xss(message = "配置分组包含非法内容")
  private String configGroup;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 128, message = "配置键长度不能超过128")
  @Xss(message = "配置键包含非法内容")
  private String configKey;

  @Xss(message = "配置值包含非法内容")
  private String configValue;

  @NotBlank(message = "值类型不能为空")
  private String valueType;

  @Xss(message = "默认值包含非法内容")
  private String defaultValue;

  @Xss(message = "配置项说明包含非法内容")
  private String description;

  private Integer isPublic;

  private Integer sortOrder;

  private String status;
}
